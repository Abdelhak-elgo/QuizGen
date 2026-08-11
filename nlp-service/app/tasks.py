"""
Tâches Celery pour la génération asynchrone de quiz.

La tâche principale `generate_quiz_task` orchestre tout le pipeline :
MinIO → PDF Extractor → NLP Pipeline → LLM Client → Post Processor
"""
import logging
import math
from typing import Any, Dict, List

from celery import Task

from app.celery_app import celery_app
from app.minio_client import download_pdf
from app.models import Difficulty, GenerateRequest, GeneratedQuestion, QuestionType
from app.pdf_extractor import extract_sections
from app.nlp_pipeline import analyze_sections
from app.llm_client import generate_questions
from app.post_processor import process_questions

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
    max_retries=0,  # On ne retente pas — la génération LLM est longue
    soft_time_limit=600,   # 10 min max
    time_limit=660,
)
def generate_quiz_task(self: QuizGenerationTask, request_dict: Dict[str, Any]) -> Dict[str, Any]:
    """
    Génère un quiz complet à partir d'un document PDF stocké dans MinIO.

    Progression reportée via Celery :
      0%  → Démarrage
     10%  → PDF téléchargé
     20%  → Sections extraites
     40%  → Analyse NLP terminée
     40–90% → Génération LLM par lot
     95%  → Post-traitement
    100%  → Terminé

    Returns:
        Dictionnaire {"questions": [...], "nb_generated": N}
    """
    request = GenerateRequest(**request_dict)
    self.update_progress(0, 100, "Démarrage de la génération...")

    # ── Étape 1 : Téléchargement depuis MinIO ─────────────────────────────────
    logger.info(
        "Génération quiz — document_id=%s, bucket=%s, key=%s",
        request.document_id, request.bucket_name, request.object_key,
    )
    try:
        pdf_stream = download_pdf(request.bucket_name, request.object_key)
    except FileNotFoundError as exc:
        raise ValueError(str(exc)) from exc

    self.update_progress(10, 100, "PDF téléchargé, extraction du texte...")

    # ── Étape 2 : Extraction du texte ─────────────────────────────────────────
    sections = extract_sections(pdf_stream)
    self.update_progress(20, 100, f"{len(sections)} sections extraites, analyse NLP...")

    # ── Étape 3 : Analyse NLP (SpaCy + KeyBERT) ───────────────────────────────
    concepts_by_section = analyze_sections(sections)
    self.update_progress(40, 100, "Analyse NLP terminée, génération des questions...")

    # ── Étape 4 : Génération LLM par type de question ─────────────────────────
    nb_total = request.nb_questions
    types = request.question_types
    difficulty = request.difficulty

    # Répartir les questions entre les types demandés
    nb_per_type = _distribute_questions(nb_total, types)

    # Sélectionner les meilleures sections (celles avec le plus de concepts)
    best_sections = _select_best_sections(sections, concepts_by_section, n=5)

    all_questions: List[GeneratedQuestion] = []
    llm_steps = len(types)
    step_size = 50 // max(llm_steps, 1)  # 40% → 90%

    for i, question_type in enumerate(types):
        nb = nb_per_type[question_type]
        if nb == 0:
            continue

        # Combiner le texte des meilleures sections
        combined_context = "\n\n".join(s.text for s in best_sections)
        combined_keywords = _merge_concepts(concepts_by_section, list(concepts_by_section.keys()))

        progress = 40 + (i * step_size)
        self.update_progress(
            progress, 100,
            f"Génération {question_type.value} ({nb} questions)..."
        )

        generated = generate_questions(
            context=combined_context,
            keywords=combined_keywords,
            question_type=question_type,
            nb=nb,
            difficulty=difficulty,
        )
        all_questions.extend(generated)
        logger.info(
            "Type %s : %d/%d questions générées.", question_type.value, len(generated), nb
        )

    self.update_progress(90, 100, "Post-traitement des questions...")

    # ── Étape 5 : Post-traitement ──────────────────────────────────────────────
    final_questions = process_questions(all_questions, target_difficulty=difficulty)

    # Limiter au nombre demandé
    final_questions = final_questions[:nb_total]

    self.update_progress(100, 100, f"Terminé — {len(final_questions)} question(s) générée(s).")

    return {
        "questions": [q.model_dump() for q in final_questions],
        "nb_generated": len(final_questions),
        "nb_requested": nb_total,
    }


# ── Helpers ───────────────────────────────────────────────────────────────────

def _distribute_questions(total: int, types: List[QuestionType]) -> Dict[QuestionType, int]:
    """Répartit `total` questions équitablement entre les types demandés."""
    if not types:
        return {}
    n = len(types)
    base = total // n
    remainder = total % n
    result = {t: base for t in types}
    # Distribuer le reste aux premiers types
    for t in types[:remainder]:
        result[t] += 1
    return result


def _select_best_sections(sections, concepts_by_section, n: int = 5):
    """Sélectionne les N sections avec le plus de concepts identifiés."""
    scored = [
        (len(concepts_by_section.get(i, [])), sec)
        for i, sec in enumerate(sections)
    ]
    scored.sort(key=lambda x: x[0], reverse=True)
    return [sec for _, sec in scored[:n]]


def _merge_concepts(concepts_by_section: Dict[int, List[str]], indices: List[int]) -> List[str]:
    """Fusionne les concepts de plusieurs sections en supprimant les doublons."""
    seen = set()
    merged = []
    for idx in indices:
        for concept in concepts_by_section.get(idx, []):
            if concept not in seen:
                seen.add(concept)
                merged.append(concept)
    return merged[:20]
