"""
Post-traitement des questions générées par le LLM.

Fonctionnalités :
- Validation du schéma (QCM = exactement 4 options, 1 bonne réponse valide)
- Déduplication par similarité ROUGE-L (seuil configurable)
- Normalisation des métadonnées
- Attribution des positions
"""
import logging
from typing import List, Sequence

from app.config import get_settings
from app.models import Difficulty, GeneratedQuestion, QuestionType

logger = logging.getLogger(__name__)
settings = get_settings()


# ── ROUGE-L ───────────────────────────────────────────────────────────────────

def _lcs_length(a: str, b: str) -> int:
    """Longueur de la plus longue sous-séquence commune (LCS) entre a et b."""
    tokens_a = a.lower().split()
    tokens_b = b.lower().split()
    m, n = len(tokens_a), len(tokens_b)
    if m == 0 or n == 0:
        return 0

    # Optimisation mémoire : 2 lignes seulement
    prev = [0] * (n + 1)
    curr = [0] * (n + 1)
    for i in range(1, m + 1):
        for j in range(1, n + 1):
            if tokens_a[i - 1] == tokens_b[j - 1]:
                curr[j] = prev[j - 1] + 1
            else:
                curr[j] = max(curr[j - 1], prev[j])
        prev, curr = curr, [0] * (n + 1)
    return prev[n]


def _rouge_l(ref: str, hyp: str) -> float:
    """
    Calcule le score ROUGE-L F1 entre deux chaînes.
    Retourne 0.0 si l'une des chaînes est vide.
    """
    ref_tokens = ref.lower().split()
    hyp_tokens = hyp.lower().split()
    if not ref_tokens or not hyp_tokens:
        return 0.0

    lcs = _lcs_length(ref, hyp)
    precision = lcs / len(hyp_tokens)
    recall = lcs / len(ref_tokens)
    if precision + recall == 0:
        return 0.0
    return 2 * precision * recall / (precision + recall)


# ── Validation ────────────────────────────────────────────────────────────────

def _validate_question(q: GeneratedQuestion) -> bool:
    """
    Valide les contraintes métier sur une question.

    Règles :
    - QCM : exactement 4 options ET correct_answer présent dans les options
    - OUVERTE / EXERCICE : options = None ou liste vide
    - content et correct_answer doivent être non-vides
    """
    if not q.content.strip() or not q.correct_answer.strip():
        logger.debug("Question rejetée : contenu ou réponse vide.")
        return False

    if q.type == QuestionType.QCM:
        if not q.options or len(q.options) != 4:
            logger.debug(
                "QCM rejeté : %d option(s) au lieu de 4.", len(q.options) if q.options else 0
            )
            return False
        # Vérifier que la bonne réponse est l'une des options (tolérance casse/espaces)
        options_normalized = [o.strip().lower() for o in q.options]
        answer_normalized = q.correct_answer.strip().lower()
        if answer_normalized not in options_normalized:
            # Accepter aussi une correspondance partielle (commence par)
            if not any(answer_normalized.startswith(o[:15]) for o in options_normalized):
                logger.debug(
                    "QCM rejeté : correct_answer '%s' absent des options.", q.correct_answer[:40]
                )
                return False
    else:
        # Pour OUVERTE et EXERCICE, les options doivent être absentes
        if q.options and len(q.options) > 0:
            q.options = None  # Correction silencieuse

    return True


# ── Déduplication ─────────────────────────────────────────────────────────────

def _deduplicate(
    questions: List[GeneratedQuestion],
    threshold: float | None = None,
) -> List[GeneratedQuestion]:
    """
    Supprime les questions trop similaires (ROUGE-L > threshold).
    Conserve la première occurrence (présumée meilleure qualité car générée en premier).
    """
    threshold = threshold or settings.rouge_dedup_threshold
    kept: List[GeneratedQuestion] = []

    for candidate in questions:
        is_duplicate = False
        for existing in kept:
            score = _rouge_l(existing.content, candidate.content)
            if score > threshold:
                logger.debug(
                    "Question dupliquée supprimée (ROUGE-L=%.2f) : %.60s...",
                    score, candidate.content,
                )
                is_duplicate = True
                break
        if not is_duplicate:
            kept.append(candidate)

    return kept


# ── Point d'entrée principal ──────────────────────────────────────────────────

def process_questions(
    questions: Sequence[GeneratedQuestion],
    target_difficulty: Difficulty = Difficulty.MOYEN,
    dedup_threshold: float | None = None,
) -> List[GeneratedQuestion]:
    """
    Valide, déduplique et normalise une liste de questions générées.

    Args:
        questions:          Liste brute de questions (depuis le LLM).
        target_difficulty:  Difficulté cible (pour normaliser les questions sans difficulté).
        dedup_threshold:    Seuil ROUGE-L pour la déduplication.

    Returns:
        Liste nettoyée et ordonnée de GeneratedQuestion.
    """
    if not questions:
        return []

    # 1. Validation
    valid = [q for q in questions if _validate_question(q)]
    logger.info(
        "Validation : %d/%d questions valides.", len(valid), len(questions)
    )

    # 2. Normalisation de la difficulté (si absente ou invalide)
    for q in valid:
        try:
            Difficulty(q.difficulty)
        except ValueError:
            q.difficulty = target_difficulty

    # 3. Déduplication ROUGE-L
    deduped = _deduplicate(valid, dedup_threshold)
    logger.info(
        "Déduplication : %d question(s) après suppression des doublons.", len(deduped)
    )

    # 4. Attribution des positions
    for i, q in enumerate(deduped):
        q.position = i

    return deduped
