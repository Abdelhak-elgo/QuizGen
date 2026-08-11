"""Tests unitaires pour les helpers de la tâche Celery (sans exécuter Celery réellement)."""
import pytest

from app.models import QuestionType
from app.tasks import _distribute_questions, _merge_concepts, _select_best_sections
from app.models import TextSection


# ── _distribute_questions ─────────────────────────────────────────────────────

class TestDistributeQuestions:
    def test_equal_distribution(self):
        types = [QuestionType.QCM, QuestionType.OUVERTE]
        result = _distribute_questions(10, types)
        assert result[QuestionType.QCM] == 5
        assert result[QuestionType.OUVERTE] == 5

    def test_remainder_distributed(self):
        types = [QuestionType.QCM, QuestionType.OUVERTE, QuestionType.EXERCICE]
        result = _distribute_questions(10, types)
        total = sum(result.values())
        assert total == 10

    def test_single_type(self):
        result = _distribute_questions(7, [QuestionType.QCM])
        assert result[QuestionType.QCM] == 7

    def test_empty_types(self):
        assert _distribute_questions(10, []) == {}

    def test_more_types_than_questions(self):
        types = [QuestionType.QCM, QuestionType.OUVERTE, QuestionType.EXERCICE]
        result = _distribute_questions(2, types)
        total = sum(result.values())
        assert total == 2


# ── _select_best_sections ─────────────────────────────────────────────────────

class TestSelectBestSections:
    def _make_section(self, text: str, page: int = 1) -> TextSection:
        return TextSection(page=page, text=text, tokens=len(text.split()))

    def test_returns_n_sections(self):
        sections = [self._make_section(f"Section {i}") for i in range(10)]
        concepts = {i: ["concept"] * i for i in range(10)}  # Section 9 a le plus de concepts
        result = _select_best_sections(sections, concepts, n=5)
        assert len(result) == 5

    def test_selects_by_concept_count(self):
        sections = [
            self._make_section("Section pauvre en concepts."),
            self._make_section("Section riche en concepts et en contenu informatif."),
        ]
        concepts = {
            0: ["un"],           # 1 concept
            1: ["a", "b", "c", "d", "e"],  # 5 concepts
        }
        result = _select_best_sections(sections, concepts, n=1)
        assert result[0] == sections[1]  # La section la plus riche doit être sélectionnée

    def test_fewer_sections_than_n(self):
        sections = [self._make_section("Section unique.")]
        concepts = {0: ["concept"]}
        result = _select_best_sections(sections, concepts, n=5)
        assert len(result) == 1


# ── _merge_concepts ───────────────────────────────────────────────────────────

class TestMergeConcepts:
    def test_merges_without_duplicates(self):
        concepts = {
            0: ["machine learning", "réseau", "données"],
            1: ["réseau", "deep learning", "données"],  # "réseau" et "données" en commun
        }
        result = _merge_concepts(concepts, [0, 1])
        assert result.count("réseau") == 1
        assert result.count("données") == 1
        assert "machine learning" in result
        assert "deep learning" in result

    def test_empty_concepts(self):
        assert _merge_concepts({}, []) == []

    def test_limit_to_20(self):
        concepts = {0: [f"concept_{i}" for i in range(30)]}
        result = _merge_concepts(concepts, [0])
        assert len(result) == 20
