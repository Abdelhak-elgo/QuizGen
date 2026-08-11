"""Tests unitaires pour le module post_processor."""
import pytest

from app.models import Difficulty, GeneratedQuestion, QuestionType
from app.post_processor import (
    _deduplicate,
    _lcs_length,
    _rouge_l,
    _validate_question,
    process_questions,
)


# ── Helpers ───────────────────────────────────────────────────────────────────

def _make_qcm(content: str = "Question test ?", correct: str = "Option A") -> GeneratedQuestion:
    return GeneratedQuestion(
        type=QuestionType.QCM,
        content=content,
        options=["Option A", "Option B", "Option C", "Option D"],
        correct_answer=correct,
        difficulty=Difficulty.MOYEN,
    )


def _make_ouverte(content: str = "Expliquez le machine learning.") -> GeneratedQuestion:
    return GeneratedQuestion(
        type=QuestionType.OUVERTE,
        content=content,
        options=None,
        correct_answer="Réponse modèle complète.",
        difficulty=Difficulty.MOYEN,
    )


# ── ROUGE-L ───────────────────────────────────────────────────────────────────

class TestLcsLength:
    def test_identical_strings(self):
        assert _lcs_length("a b c", "a b c") == 3

    def test_empty_string(self):
        assert _lcs_length("", "a b c") == 0

    def test_no_common_tokens(self):
        assert _lcs_length("foo bar", "baz qux") == 0

    def test_partial_match(self):
        lcs = _lcs_length("a b c d", "a x c y")
        assert lcs == 2  # "a" et "c"


class TestRougeL:
    def test_identical_sentences(self):
        score = _rouge_l("le machine learning", "le machine learning")
        assert score == pytest.approx(1.0)

    def test_completely_different(self):
        score = _rouge_l("machine learning intelligence", "photosynthèse chlorophylle")
        assert score == pytest.approx(0.0)

    def test_partial_overlap(self):
        score = _rouge_l("machine learning est utile", "machine learning")
        assert 0.0 < score < 1.0

    def test_empty_strings(self):
        assert _rouge_l("", "") == 0.0
        assert _rouge_l("texte", "") == 0.0
        assert _rouge_l("", "texte") == 0.0


# ── Validation ────────────────────────────────────────────────────────────────

class TestValidateQuestion:
    def test_valid_qcm(self):
        q = _make_qcm()
        assert _validate_question(q) is True

    def test_qcm_wrong_number_of_options(self):
        q = GeneratedQuestion(
            type=QuestionType.QCM,
            content="Question ?",
            options=["A", "B", "C"],  # Seulement 3 options
            correct_answer="A",
            difficulty=Difficulty.MOYEN,
        )
        assert _validate_question(q) is False

    def test_qcm_correct_answer_not_in_options(self):
        q = GeneratedQuestion(
            type=QuestionType.QCM,
            content="Question ?",
            options=["A", "B", "C", "D"],
            correct_answer="E",  # Pas dans les options
            difficulty=Difficulty.MOYEN,
        )
        assert _validate_question(q) is False

    def test_valid_ouverte(self):
        q = _make_ouverte()
        assert _validate_question(q) is True

    def test_ouverte_empty_content(self):
        q = _make_ouverte(content="")
        assert _validate_question(q) is False

    def test_ouverte_empty_answer(self):
        q = GeneratedQuestion(
            type=QuestionType.OUVERTE,
            content="Question ?",
            options=None,
            correct_answer="",
            difficulty=Difficulty.MOYEN,
        )
        assert _validate_question(q) is False

    def test_ouverte_with_options_gets_cleaned(self):
        """Les options sur une OUVERTE doivent être supprimées silencieusement."""
        q = GeneratedQuestion(
            type=QuestionType.OUVERTE,
            content="Expliquez le deep learning.",
            options=["A", "B"],  # Erreur LLM — doit être nettoyé
            correct_answer="Réponse modèle.",
            difficulty=Difficulty.MOYEN,
        )
        result = _validate_question(q)
        assert result is True
        assert q.options is None  # Nettoyage silencieux


# ── Déduplication ─────────────────────────────────────────────────────────────

class TestDeduplicate:
    def test_removes_near_duplicates(self):
        q1 = _make_qcm("Qu'est-ce que le machine learning dans l'IA ?")
        q2 = _make_qcm("Qu'est-ce que le machine learning dans l'IA ?")  # Identique
        result = _deduplicate([q1, q2], threshold=0.8)
        assert len(result) == 1

    def test_keeps_different_questions(self):
        q1 = _make_qcm("Qu'est-ce que le machine learning ?")
        q2 = _make_ouverte("Décrivez les réseaux de neurones profonds et leur fonctionnement.")
        result = _deduplicate([q1, q2], threshold=0.8)
        assert len(result) == 2

    def test_empty_list(self):
        assert _deduplicate([], threshold=0.8) == []

    def test_single_question(self):
        q = _make_qcm()
        result = _deduplicate([q], threshold=0.8)
        assert len(result) == 1

    def test_threshold_sensitivity(self):
        """Un seuil bas (0.3) devrait filtrer même des questions partiellement similaires."""
        q1 = _make_qcm("Qu'est-ce que le machine learning ?")
        q2 = _make_qcm("Qu'est-ce que le deep learning ?")
        strict = _deduplicate([q1, q2], threshold=0.3)
        loose = _deduplicate([q1, q2], threshold=0.95)
        # Avec un seuil strict, les deux peuvent être filtrées
        assert len(loose) >= len(strict)


# ── Pipeline complet ──────────────────────────────────────────────────────────

class TestProcessQuestions:
    def test_valid_questions_returned(self, sample_questions_raw):
        result = process_questions(sample_questions_raw)
        assert len(result) >= 1
        # Les positions doivent être attribuées
        for i, q in enumerate(result):
            assert q.position == i

    def test_duplicates_removed(self, sample_questions_raw):
        """sample_questions_raw contient 1 QCM en doublon → doit être filtré."""
        result = process_questions(sample_questions_raw)
        contents = [q.content for q in result]
        # Le contenu "Qu'est-ce que le machine learning ?" ne doit apparaître qu'une fois
        ml_questions = [c for c in contents if "machine learning" in c.lower()]
        assert len(ml_questions) == 1

    def test_invalid_questions_filtered(self):
        invalid = GeneratedQuestion(
            type=QuestionType.QCM,
            content="",  # Contenu vide → invalide
            options=["A", "B", "C", "D"],
            correct_answer="A",
            difficulty=Difficulty.MOYEN,
        )
        result = process_questions([invalid])
        assert result == []

    def test_empty_input(self):
        assert process_questions([]) == []

    def test_difficulty_normalization(self):
        """Une question avec une difficulté invalide doit être normalisée."""
        q = GeneratedQuestion(
            type=QuestionType.OUVERTE,
            content="Question valide pour tester la normalisation de la difficulté ?",
            options=None,
            correct_answer="Réponse modèle valide.",
            difficulty="INVALIDE",  # type: ignore
        )
        result = process_questions([q], target_difficulty=Difficulty.FACILE)
        assert len(result) == 1
        assert result[0].difficulty == Difficulty.FACILE
