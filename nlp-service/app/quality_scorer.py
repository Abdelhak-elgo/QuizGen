"""
Quality Scorer — Évaluation et filtrage des questions générées.

Deux problèmes que la génération LLM ne résout pas seule :

1. HALLUCINATION : La réponse correcte n'est pas dans le texte source.
   → Answerability Check par similarité cosinus embedding(réponse) ∥ embedding(chunk)

2. QUALITÉ LINGUISTIQUE : Question trop courte, trop vague, ou syntaxiquement bancale.
   → Heuristiques linguistiques (longueur, structure interrogative, etc.)

Le score final est une combinaison pondérée :
   score = 0.50 × answerability + 0.30 × linguistic + 0.20 × distinctiveness

Les questions avec score < QUALITY_THRESHOLD sont filtrées avant persistence.

Référence :
  Deutsch et al. (2021). Towards Question-Answering as an Automatic Metric
  for Evaluating the Content Quality of a Summary. TACL, 9:774–789.
"""
import logging
import re
from dataclasses import dataclass
from typing import List, Optional

import numpy as np

from app.models import GeneratedQuestion, QuestionType

logger = logging.getLogger(__name__)

# Seuil de qualité minimal [0, 1] — les questions en dessous sont rejetées
QUALITY_THRESHOLD = 0.40
# Seuil d'answerability — la réponse doit être sémantiquement ancrée dans le chunk
ANSWERABILITY_THRESHOLD = 0.30


@dataclass
class ScoredQuestion:
    question: GeneratedQuestion
    answerability: float   # [0,1] — similarité embedding réponse / chunk source
    linguistic:    float   # [0,1] — qualité linguistique heuristique
    distinctiveness: float # [0,1] — différence avec les autres questions du batch
    total_score:   float   # [0,1] — score composite final
    rejected:      bool
    rejection_reason: Optional[str]


# ── Answerability Check ───────────────────────────────────────────────────────

def _compute_answerability(
    answer: str,
    source_chunk: str,
    embedding_model,
) -> float:
    """
    Mesure dans quelle mesure la réponse est sémantiquement ancrée dans
    le texte source en calculant la similarité cosinus entre leurs embeddings.

    Un score élevé (> 0.50) indique que la réponse est bien dérivée du texte.
    Un score faible (< 0.30) indique une hallucination probable.

    Args:
        answer:          Réponse correcte générée par le LLM.
        source_chunk:    Texte source ayant servi de contexte.
        embedding_model: Modèle sentence-transformers déjà chargé.

    Returns:
        Score [0, 1] — 1.0 si la réponse est quasi-identique au texte source.
    """
    if not answer.strip() or not source_chunk.strip():
        return 0.0

    if embedding_model is None:
        # Fallback heuristique : chevauchement de tokens normalisé
        answer_tokens = set(answer.lower().split())
        source_tokens = set(source_chunk.lower().split())
        if not answer_tokens:
            return 0.0
        overlap = len(answer_tokens & source_tokens) / len(answer_tokens)
        return min(1.0, overlap * 1.5)  # Normaliser (overlap partiel est ok)

    try:
        embeddings = embedding_model.encode(
            [answer, source_chunk],
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        similarity = float(np.dot(embeddings[0], embeddings[1]))
        return max(0.0, similarity)
    except Exception as exc:
        logger.warning("Answerability embedding error: %s", exc)
        return 0.5  # Valeur neutre en cas d'erreur


# ── Quality Linguistique ──────────────────────────────────────────────────────

# Marqueurs d'une bonne question pédagogique
_QUESTION_STARTERS_FR = [
    "qu'est-ce", "quel", "quelle", "quels", "quelles",
    "comment", "pourquoi", "dans quel", "lequel", "laquelle",
    "expliquez", "définissez", "décrivez", "analysez",
    "comparez", "justifiez", "démontrez", "calculez",
    "quelle est", "quels sont", "comment expliquer",
]

_VAGUE_PATTERNS = [
    r"\bchose\b", r"\btruc\b", r"\bmachin\b",  # termes vagues
    r"\bétc\b", r"\b\.\.\.\b",                  # etc incomplet
    r"^.{0,15}\?$",                              # question trop courte (<15 chars)
]


def _score_linguistic_quality(question: GeneratedQuestion) -> float:
    """
    Évalue la qualité linguistique d'une question sur plusieurs dimensions.

    Critères :
    - Structure interrogative valide (commence par un mot question)
    - Longueur appropriée (20-200 mots pour le content)
    - Absence de termes vagues ou de formulations tronquées
    - Cohérence type/format (QCM = 4 options, OUVERTE = réponse substantielle)
    - La réponse correcte ne répète pas mot-pour-mot la question

    Returns:
        Score [0, 1].
    """
    score = 1.0
    content = question.content.strip()
    answer = (question.correct_answer or "").strip()
    words = content.split()

    # ── Longueur du contenu ────────────────────────────────────────────────
    n_words = len(words)
    if n_words < 8:
        score -= 0.4   # Trop courte
    elif n_words < 15:
        score -= 0.2   # Un peu courte
    elif n_words > 150:
        score -= 0.1   # Peut-être trop longue / ambiguë

    # ── Structure interrogative ────────────────────────────────────────────
    content_lower = content.lower()
    has_question_marker = (
        content.endswith("?")
        or any(content_lower.startswith(s) for s in _QUESTION_STARTERS_FR)
        or any(f" {s} " in content_lower for s in _QUESTION_STARTERS_FR[:8])
    )
    if not has_question_marker:
        score -= 0.2

    # ── Absence de termes vagues ───────────────────────────────────────────
    for pattern in _VAGUE_PATTERNS:
        if re.search(pattern, content_lower):
            score -= 0.15
            break

    # ── Qualité de la réponse ──────────────────────────────────────────────
    if not answer:
        score -= 0.3
    else:
        answer_words = len(answer.split())
        if answer_words < 3:
            score -= 0.3  # Réponse trop courte
        elif answer_words > 500:
            score -= 0.1  # Réponse trop longue

        # Vérifier que la réponse ne copie pas la question (> 70% overlap)
        q_tokens = set(content_lower.split())
        a_tokens = set(answer.lower().split())
        if q_tokens and len(q_tokens & a_tokens) / len(q_tokens) > 0.70:
            score -= 0.25

    # ── Validation spécifique QCM ──────────────────────────────────────────
    if question.type == QuestionType.QCM:
        if not question.options or len(question.options) != 4:
            score -= 0.4
        else:
            # Vérifier que les distracteurs ne sont pas identiques
            unique_options = set(o.strip().lower() for o in question.options)
            if len(unique_options) < 4:
                score -= 0.2  # Options dupliquées

            # Les options doivent être de longueur comparable (pas 1 mot vs 3 lignes)
            lengths = [len(o.split()) for o in question.options]
            if max(lengths) > 5 * (min(lengths) + 1):
                score -= 0.1  # Déséquilibre trop fort

    return max(0.0, min(1.0, score))


# ── Distinctiveness ───────────────────────────────────────────────────────────

def _score_distinctiveness(
    question: GeneratedQuestion,
    all_questions: List[GeneratedQuestion],
    embedding_model,
) -> float:
    """
    Mesure à quel point cette question est distincte des autres questions
    du même batch (éviter les questions sémantiquement redondantes).

    Returns:
        Score [0, 1] — 1.0 si complètement unique, 0.0 si identique à une autre.
    """
    if len(all_questions) <= 1:
        return 1.0

    others = [q for q in all_questions if q is not question]
    if not others:
        return 1.0

    if embedding_model is not None:
        try:
            texts = [question.content] + [q.content for q in others]
            embeddings = embedding_model.encode(
                texts,
                normalize_embeddings=True,
                show_progress_bar=False,
            )
            q_embed = embeddings[0]
            other_embeds = embeddings[1:]
            # Similarité maximale avec n'importe quelle autre question
            max_sim = float(max(np.dot(q_embed, o) for o in other_embeds))
            # Inverser : haute similarité = faible distinctiveness
            return max(0.0, 1.0 - max_sim)
        except Exception as exc:
            logger.warning("Distinctiveness embedding error: %s", exc)

    # Fallback ROUGE-L simple
    def rouge_l_overlap(s1: str, s2: str) -> float:
        t1, t2 = s1.lower().split(), s2.lower().split()
        common = len(set(t1) & set(t2))
        return common / max(len(t1), 1)

    max_overlap = max(
        rouge_l_overlap(question.content, q.content)
        for q in others
    )
    return max(0.0, 1.0 - max_overlap)


# ── Scorer principal ──────────────────────────────────────────────────────────

def score_questions(
    questions: List[GeneratedQuestion],
    source_chunk: str,
    embedding_model=None,
    quality_threshold: float = QUALITY_THRESHOLD,
    answerability_threshold: float = ANSWERABILITY_THRESHOLD,
) -> List[ScoredQuestion]:
    """
    Score et filtre une liste de questions générées.

    Pipeline :
      1. Answerability : la réponse est-elle ancrée dans le texte source ?
      2. Linguistic    : la question est-elle bien formée ?
      3. Distinctiveness : est-elle différente des autres ?
      4. Score composite : 0.50×A + 0.30×L + 0.20×D
      5. Filtre : rejeter si score < quality_threshold ou answerability trop faible

    Args:
        questions:             Questions générées par le LLM.
        source_chunk:          Texte source ayant servi de contexte.
        embedding_model:       Modèle sentence-transformers (None = fallback heuristique).
        quality_threshold:     Seuil de score composite minimal.
        answerability_threshold: Seuil d'answerability minimal.

    Returns:
        Liste de ScoredQuestion (toutes, acceptées ET rejetées) pour traçabilité.
    """
    if not questions:
        return []

    scored: List[ScoredQuestion] = []

    for q in questions:
        # 1. Answerability
        ans_score = _compute_answerability(
            q.correct_answer or "",
            source_chunk,
            embedding_model,
        )

        # 2. Qualité linguistique
        ling_score = _score_linguistic_quality(q)

        # 3. Distinctiveness (par rapport aux autres questions du batch)
        dist_score = _score_distinctiveness(q, questions, embedding_model)

        # 4. Score composite pondéré
        total = 0.50 * ans_score + 0.30 * ling_score + 0.20 * dist_score

        # 5. Décision de rejet
        rejected = False
        reason = None
        if total < quality_threshold:
            rejected = True
            reason = f"score trop bas ({total:.2f} < {quality_threshold})"
        elif ans_score < answerability_threshold:
            rejected = True
            reason = f"réponse non ancrée dans le texte (answerability={ans_score:.2f})"

        scored.append(ScoredQuestion(
            question=q,
            answerability=round(ans_score, 3),
            linguistic=round(ling_score, 3),
            distinctiveness=round(dist_score, 3),
            total_score=round(total, 3),
            rejected=rejected,
            rejection_reason=reason,
        ))

    # Statistiques de log
    n_rejected = sum(1 for s in scored if s.rejected)
    logger.info(
        "Quality scoring : %d/%d questions acceptées (seuil=%.2f)",
        len(scored) - n_rejected, len(scored), quality_threshold,
    )
    for s in scored:
        if s.rejected:
            logger.debug(
                "  ✗ Rejetée [ans=%.2f lin=%.2f dist=%.2f tot=%.2f] : %.60s — %s",
                s.answerability, s.linguistic, s.distinctiveness,
                s.total_score, s.question.content, s.rejection_reason,
            )
        else:
            logger.debug(
                "  ✓ Acceptée [ans=%.2f lin=%.2f dist=%.2f tot=%.2f] : %.60s",
                s.answerability, s.linguistic, s.distinctiveness,
                s.total_score, s.question.content,
            )

    return scored


def filter_quality_questions(scored: List[ScoredQuestion]) -> List[GeneratedQuestion]:
    """Retourne uniquement les questions acceptées, triées par score décroissant."""
    accepted = [s for s in scored if not s.rejected]
    accepted.sort(key=lambda s: -s.total_score)
    return [s.question for s in accepted]
