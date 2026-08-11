"""
Tâches Celery — Pipeline de génération de quiz amélioré.

Pipeline complet (v2 — avec chunking sémantique + Bloom + CoT + quality scoring) :

  MinIO (PDF)
    ↓
  pdf_extractor  → sections brutes (par page)
    ↓
  semantic_chunker → chunks sémantiquement cohérents (TextTiling sémantique)
    ↓
  bloom_classifier → niveau Bloom + type de question recommandé par chunk
  domain_detector  → domaine académique (informatique, droit, biologie…)
    ↓
  llm_client (CoT) → 1-2 questions par chunk avec raisonnement intermédiaire
    ↓
  quality_scorer   → answerability check + score linguistique + distinctiveness
    ↓
  post_processor   → déduplication ROUGE-L + validation schéma + normalisation
    ↓
  List[GeneratedQuestion] (N questions filtrées et ordonnées par score)

Avantage vs v1 :
  - Le LLM reçoit des chunks thématiquement cohérents (pas des pages aléatoires)
  - Le prompt CoT adapté au niveau Bloom et au domaine → questions ciblées
  - L'answerability check élimine les hallucinations avant stockage
"""
import logging
from typing import Any, Dict, List, Optional, Tuple

from celery import Task

from app.bloom_classifier import (
    BloomClassification,
    classify_bloom_level,
    detect_domain,
    get_domain_prompt_suffix,
)
from app.celery_app import celery_app
from app.llm_client import generate_questions
from app.minio_client import download_pdf
from app.models import Difficulty, GenerateRequest, GeneratedQuestion, QuestionType
from app.pdf_extractor import extract_sections
from app.post_processor import process_questions
from app.quality_scorer import filter_quality_questions, score_questions
from app.rag_store import index_document, retrieve_context
from app.semantic_chunker import SemanticChunk, chunk_text_semantically

logger = logging.getLogger(__name__)


class QuizGenerationTask(Task):
    """Tâche de base avec gestion d'état pour le suivi de progression."""

    abstract = True

    def update_progress(self, current: int, total: int, message: str = "") -> None:
        progress = min(100, int(current / total * 100)) if total > 0 else 0
        self.update_state(
            state="PROGRESS",
            meta={"progress": progress, "message": message, "current": current, "total": total},
        )


@celery_app.task(
    bind=True,
    base=QuizGenerationTask,
    name="quizgen.generate_quiz",
    max_retries=0,
    soft_time_limit=600,
    time_limit=660,
)
def generate_quiz_task(self: QuizGenerationTask, request_dict: Dict[str, Any]) -> Dict[str, Any]:
    """
    Génère un quiz complet à partir d'un document PDF stocké dans MinIO.

    Progression :
       0%  Démarrage
      10%  PDF téléchargé
      20%  Texte extrait + chunking sémantique
      30%  Domaine détecté + distribution Bloom calculée
      30–85% Génération CoT par chunk + quality scoring
      90%  Post-traitement (déduplication, validation)
     100%  Terminé

    Returns:
        {"questions": [...], "nb_generated": N, "nb_requested": N,
         "domain": str, "pipeline_stats": {...}}
    """
    request = GenerateRequest(**request_dict)
    self.update_progress(0, 100, "Démarrage de la génération...")

    # ── Étape 1 : Téléchargement MinIO ───────────────────────────────────────
    logger.info(
        "Pipeline v2 — document_id=%s bucket=%s key=%s nb=%d types=%s",
        request.document_id, request.bucket_name, request.object_key,
        request.nb_questions, [t.value for t in request.question_types],
    )
    try:
        pdf_stream = download_pdf(request.bucket_name, request.object_key)
    except FileNotFoundError as exc:
        raise ValueError(str(exc)) from exc

    self.update_progress(10, 100, "PDF téléchargé, extraction du texte...")

    # ── Étape 2 : Extraction du texte + chunking sémantique ──────────────────
    sections = extract_sections(pdf_stream)

    # Assembler le texte complet du document pour le chunking
    full_text = "\n\n".join(s.text for s in sections if len(s.text.split()) >= 20)

    if not full_text.strip():
        logger.error("Aucun texte extractible du document %s", request.document_id)
        return {"questions": [], "nb_generated": 0, "nb_requested": request.nb_questions}

    # Charger le modèle d'embeddings (singleton KeyBERT — déjà en mémoire)
    embedding_model = _get_embedding_model()

    # Chunking sémantique (TextTiling sémantique avec sentence-transformers)
    chunks = chunk_text_semantically(
        full_text,
        embedding_model=embedding_model,
        target_chunk_words=250,
        max_chunk_words=420,
        min_chunk_words=80,
    )

    # Filtrer les chunks trop courts pour générer des questions
    substantial_chunks = [c for c in chunks if c.is_substantial]

    if not substantial_chunks:
        logger.warning("Aucun chunk substantiel — fallback sur sections brutes")
        substantial_chunks = _sections_to_chunks(sections)

    logger.info(
        "Chunking : %d chunks substantiels extraits du document (avg %.0f mots)",
        len(substantial_chunks),
        sum(c.word_count for c in substantial_chunks) / max(1, len(substantial_chunks)),
    )

    self.update_progress(20, 100, f"{len(substantial_chunks)} segments analysés, indexation RAG...")

    # ── Étape 2b : Indexation ChromaDB (RAG) ─────────────────────────────────
    # Idempotent : si la collection existe déjà avec le même nb de chunks → skip
    rag_indexed = index_document(
        document_id=str(request.document_id),
        chunks=substantial_chunks,
        embedding_model=embedding_model,
    )
    logger.info("RAG indexation : %s (%d chunks)", "OK" if rag_indexed else "skipped/error", len(substantial_chunks))

    self.update_progress(25, 100, f"RAG indexé | détection du domaine...")

    # ── Étape 3 : Détection domaine + classification Bloom ───────────────────
    domain = detect_domain(full_text[:5000])  # Les 5000 premiers mots suffisent
    domain_suffix = get_domain_prompt_suffix(domain)
    logger.info("Domaine détecté : %s", domain)

    # Classifier chaque chunk selon Bloom
    bloom_by_chunk: List[BloomClassification] = [
        classify_bloom_level(chunk.text) for chunk in substantial_chunks
    ]

    # Calculer la distribution optimale des questions par chunk + type
    distribution = _plan_question_distribution(
        chunks=substantial_chunks,
        blooms=bloom_by_chunk,
        nb_total=request.nb_questions,
        requested_types=request.question_types,
        difficulty=request.difficulty,
    )

    self.update_progress(30, 100, f"Domaine : {domain} | Génération CoT par segment...")

    # ── Étape 4 : Génération CoT par chunk + quality scoring ─────────────────
    all_questions: List[GeneratedQuestion] = []
    total_steps = len(distribution)
    pipeline_stats = {
        "domain": domain,
        "n_chunks": len(substantial_chunks),
        "n_generation_calls": total_steps,
        "rag_indexed": rag_indexed,
        "rag_retrievals": 0,
        "questions_generated_raw": 0,
        "questions_accepted": 0,
        "questions_rejected": 0,
    }

    for step_idx, (chunk_idx, chunk, bloom, q_type, nb, diff) in enumerate(distribution):
        if nb == 0:
            continue

        # Progression 30% → 85% répartie sur les étapes de génération
        progress = 30 + int((step_idx / max(total_steps, 1)) * 55)
        self.update_progress(
            progress, 100,
            f"Segment {step_idx + 1}/{total_steps} — {q_type.value} (Bloom: {bloom.level.name})"
        )

        # Combiner les keywords du chunk avec l'analyse NLP globale
        keywords = chunk.topic_keywords

        # ── RAG : récupérer les chunks connexes cross-document ────────────────
        rag_chunks = retrieve_context(
            document_id=str(request.document_id),
            query_text=chunk.text,
            top_k=3,
            exclude_text=chunk.text,
            embedding_model=embedding_model,
        )
        if rag_chunks:
            pipeline_stats["rag_retrievals"] += 1

        logger.info(
            "Chunk %d/%d [%s, bloom=%s, diff=%s, domain=%s, rag=%d] — génération %d question(s)",
            step_idx + 1, total_steps,
            q_type.value, bloom.level.name, diff.value, domain, len(rag_chunks), nb,
        )

        # Génération avec prompt CoT adapté + contexte RAG cross-document
        generated = generate_questions(
            context=chunk.text,
            keywords=keywords,
            question_type=q_type,
            nb=nb,
            difficulty=diff,
            bloom_level=bloom.level.name,
            bloom_directive=bloom.prompt_directive,
            domain=domain,
            domain_suffix=domain_suffix,
            rag_context=rag_chunks if rag_chunks else None,
        )

        pipeline_stats["questions_generated_raw"] += len(generated)

        if not generated:
            logger.warning("Chunk %d : aucune question générée — chunk ignoré.", chunk_idx)
            continue

        # Quality scoring (answerability sur source étendue si RAG disponible)
        scored = score_questions(
            questions=generated,
            source_chunk=chunk.text,
            embedding_model=embedding_model,
            rag_context=rag_chunks if rag_chunks else None,
        )

        accepted = filter_quality_questions(scored)
        pipeline_stats["questions_accepted"] += len(accepted)
        pipeline_stats["questions_rejected"] += len(scored) - len(accepted)

        all_questions.extend(accepted)
        logger.info(
            "Chunk %d : %d générées → %d acceptées après quality scoring.",
            chunk_idx, len(generated), len(accepted),
        )

    self.update_progress(90, 100, f"{len(all_questions)} questions acceptées, post-traitement...")

    # ── Étape 5 : Post-traitement ─────────────────────────────────────────────
    # Déduplication ROUGE-L + validation schéma + normalisation
    final_questions = process_questions(
        all_questions,
        target_difficulty=request.difficulty,
    )

    # Respecter le nombre demandé
    final_questions = final_questions[:request.nb_questions]

    # Si on n'a pas assez de questions après filtrage, signaler (ne pas halluci)
    if len(final_questions) < request.nb_questions:
        logger.warning(
            "Seulement %d/%d questions produites après quality filtering. "
            "Le document source est peut-être trop court ou trop homogène.",
            len(final_questions), request.nb_questions,
        )

    self.update_progress(100, 100, f"Terminé — {len(final_questions)} question(s) générée(s).")

    pipeline_stats["questions_final"] = len(final_questions)
    logger.info("Pipeline v2 terminé : %s", pipeline_stats)

    return {
        "questions": [q.model_dump() for q in final_questions],
        "nb_generated": len(final_questions),
        "nb_requested": request.nb_questions,
        "domain": domain,
        "rag_context_used": rag_indexed,
        "rag_chunks_retrieved": pipeline_stats["rag_retrievals"],
        "pipeline_stats": pipeline_stats,
    }


# ── Helpers ───────────────────────────────────────────────────────────────────

def _get_embedding_model():
    """
    Récupère le modèle sentence-transformers déjà chargé par KeyBERT.
    Évite de charger un modèle en double — KeyBERT utilise déjà MiniLM.
    Retourne None si KeyBERT n'est pas encore initialisé (CI/tests).
    """
    try:
        from app.nlp_pipeline import _load_keybert_model
        kw_model = _load_keybert_model()
        # L'objet KeyBERT expose le modèle via .model
        return kw_model.model
    except Exception as exc:
        logger.warning("Impossible de récupérer le modèle d'embeddings : %s", exc)
        return None


def _sections_to_chunks(sections) -> List[SemanticChunk]:
    """
    Fallback : convertit des sections brutes en SemanticChunk simples.
    Utilisé quand le chunking sémantique ne produit rien (document trop court).
    """
    return [
        SemanticChunk(
            text=s.text,
            sentences=s.text.split(". "),
            start_sentence=0,
            end_sentence=1,
            coherence_score=0.8,
            topic_keywords=s.text.split()[:5],
        )
        for s in sections
        if len(s.text.split()) >= 80
    ]


def _plan_question_distribution(
    chunks: List[SemanticChunk],
    blooms: List[BloomClassification],
    nb_total: int,
    requested_types: List[QuestionType],
    difficulty: Difficulty,
) -> List[Tuple[int, SemanticChunk, BloomClassification, QuestionType, int, Difficulty]]:
    """
    Planifie quelle question générer depuis quel chunk.

    Stratégie :
      1. Trier les chunks par cohérence × taille décroissante (meilleurs d'abord)
      2. Répartir nb_total questions proportionnellement aux poids des chunks
      3. Pour chaque chunk, choisir le type de question :
         - Priorité au type suggéré par Bloom si dans les types demandés
         - Sinon round-robin sur les types demandés
      4. Retourner max 2 questions par chunk (évite la répétition thématique)

    Returns:
        Liste de (chunk_idx, chunk, bloom, question_type, nb, difficulty)
    """
    if not chunks or nb_total == 0 or not requested_types:
        return []

    # Pondérer par cohérence × taille (clamped à 400 mots)
    weights = [
        bloom.confidence * min(chunk.word_count, 400)
        for chunk, bloom in zip(chunks, blooms)
    ]
    total_weight = sum(weights) or 1.0

    # Allouer les questions proportionnellement, max 2 par chunk
    raw_alloc = [
        min(2, max(0, round(w / total_weight * nb_total)))
        for w in weights
    ]

    # Ajuster pour atteindre exactement nb_total
    allocated = sum(raw_alloc)
    diff = nb_total - allocated
    if diff != 0:
        # Ajouter/retirer aux chunks les plus pondérés
        sorted_idx = sorted(range(len(weights)), key=lambda i: -weights[i])
        for i in sorted_idx:
            if diff == 0:
                break
            if diff > 0 and raw_alloc[i] < 2:
                raw_alloc[i] += 1
                diff -= 1
            elif diff < 0 and raw_alloc[i] > 0:
                raw_alloc[i] -= 1
                diff += 1

    # Construire le plan
    plan = []
    for i, (chunk, bloom, nb) in enumerate(zip(chunks, blooms, raw_alloc)):
        if nb == 0:
            continue

        # Choisir le type de question
        if bloom.question_type_hint in [t.value for t in requested_types]:
            q_type = QuestionType(bloom.question_type_hint)
        else:
            q_type = requested_types[i % len(requested_types)]

        # La difficulté suit la suggestion Bloom SAUF si l'enseignant a forcé une difficulté
        # (on garde la difficulté demandée — le Bloom informe le prompt, pas le filtre)
        plan.append((i, chunk, bloom, q_type, nb, difficulty))

    logger.info(
        "Plan de génération : %d appels LLM pour %d questions (%d chunks actifs)",
        len(plan), sum(p[4] for p in plan), len(plan),
    )
    return plan
