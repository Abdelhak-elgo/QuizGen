"""Tests unitaires pour les helpers du pipeline Celery."""

from app.bloom_classifier import BloomClassification, BloomLevel
from app.models import Difficulty, QuestionType, TextSection
from app.semantic_chunker import SemanticChunk
from app.tasks import _plan_question_distribution, _sections_to_chunks


def _chunk(words: int = 100, coherence: float = 0.8) -> SemanticChunk:
    text = " ".join(f"mot{i}" for i in range(words))
    return SemanticChunk(
        text=text,
        sentences=[text],
        start_sentence=0,
        end_sentence=1,
        coherence_score=coherence,
        topic_keywords=["concept"],
    )


def _bloom(question_type: QuestionType, confidence: float = 0.9) -> BloomClassification:
    return BloomClassification(
        level=BloomLevel.COMPREHENSION,
        confidence=confidence,
        detected_markers=[],
        question_type_hint=question_type.value,
        difficulty_hint=Difficulty.MOYEN.value,
        prompt_directive="Expliquer le concept.",
    )


class TestSectionsToChunks:
    def test_converts_only_substantial_sections(self):
        long_text = " ".join(["contenu"] * 90)
        sections = [
            TextSection(page=1, text=long_text, tokens=90),
            TextSection(page=2, text="trop court", tokens=2),
        ]

        result = _sections_to_chunks(sections)

        assert len(result) == 1
        assert result[0].text == long_text
        assert result[0].is_substantial is True

    def test_empty_sections(self):
        assert _sections_to_chunks([]) == []


class TestPlanQuestionDistribution:
    def test_empty_inputs_return_no_plan(self):
        assert _plan_question_distribution(
            chunks=[],
            blooms=[],
            nb_total=5,
            requested_types=[QuestionType.QCM],
            difficulty=Difficulty.MOYEN,
        ) == []

    def test_allocates_requested_total_with_max_two_per_chunk(self):
        chunks = [_chunk(100), _chunk(120), _chunk(140)]
        blooms = [_bloom(QuestionType.QCM) for _ in chunks]

        plan = _plan_question_distribution(
            chunks=chunks,
            blooms=blooms,
            nb_total=5,
            requested_types=[QuestionType.QCM],
            difficulty=Difficulty.MOYEN,
        )

        assert sum(item[4] for item in plan) == 5
        assert all(item[4] <= 2 for item in plan)

    def test_prefers_bloom_question_type_when_requested(self):
        plan = _plan_question_distribution(
            chunks=[_chunk()],
            blooms=[_bloom(QuestionType.OUVERTE)],
            nb_total=1,
            requested_types=[QuestionType.QCM, QuestionType.OUVERTE],
            difficulty=Difficulty.DIFFICILE,
        )

        assert plan[0][3] is QuestionType.OUVERTE
        assert plan[0][5] is Difficulty.DIFFICILE

    def test_falls_back_to_requested_type(self):
        plan = _plan_question_distribution(
            chunks=[_chunk()],
            blooms=[_bloom(QuestionType.EXERCICE)],
            nb_total=1,
            requested_types=[QuestionType.QCM],
            difficulty=Difficulty.FACILE,
        )

        assert plan[0][3] is QuestionType.QCM