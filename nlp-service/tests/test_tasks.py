"""Tests unitaires pour les helpers du pipeline Celery."""

from app.bloom_classifier import BloomClassification, BloomLevel
from app.models import Difficulty, QuestionType, TextSection
from app.semantic_chunker import SemanticChunk
from app.tasks import _plan_question_distribution, _plan_top_up, _sections_to_chunks


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

class TestGetEmbeddingModel:
    def test_returns_sentence_transformer_not_keybert_backend(self, monkeypatch):
        # KeyBERT expose un wrapper (.model) sans .encode() ; le pipeline a
        # besoin du SentenceTransformer sous-jacent (.model.embedding_model).
        from types import SimpleNamespace

        import app.nlp_pipeline as nlp_pipeline
        from app.tasks import _get_embedding_model

        sentence_transformer = SimpleNamespace(encode=lambda texts, **_: texts)
        backend = SimpleNamespace(embedding_model=sentence_transformer)
        monkeypatch.setattr(nlp_pipeline, "_load_keybert_model", lambda: SimpleNamespace(model=backend))

        assert _get_embedding_model() is sentence_transformer


class TestPlanTopUp:
    QCM = [QuestionType.QCM]

    def _candidates(self, n: int):
        return [(i, _chunk(), _bloom(QuestionType.QCM)) for i in range(n)]

    def _plan(self, candidates, tried=frozenset(), missing=1, types=None, round_idx=1):
        return _plan_top_up(candidates, set(tried), missing, types or self.QCM, round_idx, Difficulty.MOYEN)

    def test_one_question_per_call_covering_the_missing_count(self):
        plan = self._plan(self._candidates(6), missing=3)

        assert [step[4] for step in plan] == [1, 1, 1]
        assert len({step[0] for step in plan}) == 3

    def test_prefers_chunks_never_tried(self):
        plan = self._plan(self._candidates(6), tried={0, 1, 2, 3}, missing=2)

        assert {step[0] for step in plan} == {4, 5}

    def test_reuses_tried_chunks_rotated_by_round_when_all_tried(self):
        cands = self._candidates(4)
        first = self._plan(cands, tried={0, 1, 2, 3}, round_idx=1)[0][0]
        second = self._plan(cands, tried={0, 1, 2, 3}, round_idx=2)[0][0]

        assert first != second

    def test_rotates_requested_types(self):
        types = [QuestionType.QCM, QuestionType.OUVERTE]
        plan = self._plan(self._candidates(4), missing=4, types=types)

        assert {step[3] for step in plan} == set(types)

    def test_nothing_to_plan_without_chunks_or_missing(self):
        assert self._plan([], missing=3) == []
        assert self._plan(self._candidates(2), missing=0) == []
