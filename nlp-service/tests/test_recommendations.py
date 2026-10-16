"""Tests des recommandations pédagogiques (prompt, parsing, endpoint)."""
import json
from unittest import mock

import pytest
from fastapi.testclient import TestClient

from app import recommendations
from app.llm_providers import LlmConfigError
from app.models import AnswerOutcome, QuestionType, RecommendationItem, RecommendationRequest
from app.recommendations import MAX_ITEMS_PER_LIST, build_prompt, parse_recommendation

LLM_JSON = {
    "summary": "Bases solides, confusion sur la gouvernance.",
    "strengths": ["Définition du SI (Q1)"],
    "weaknesses": ["Confond pilotage stratégique et opérationnel (Q2)"],
    "focus_areas": ["Niveaux de pilotage du SI"],
    "actions": ["Refaire l'exercice de cartographie du cours 2"],
}


def _request(**overrides) -> RecommendationRequest:
    data = dict(
        quiz_title="Urbanisation des SI",
        score=1,
        max_score=2,
        items=[
            RecommendationItem(question="Qu'est-ce qu'un SI ?", type=QuestionType.QCM,
                               correct_answer="Un système d'information", student_answer="Un système d'information",
                               outcome=AnswerOutcome.CORRECT),
            RecommendationItem(question="Pilotage stratégique vs opérationnel ?", type=QuestionType.OUVERTE,
                               correct_answer="Horizon long vs court", student_answer=None,
                               outcome=AnswerOutcome.UNANSWERED, explanation="Voir chapitre 2"),
        ],
    )
    data.update(overrides)
    return RecommendationRequest(**data)


class TestBuildPrompt:
    def test_contains_every_question_verdict_and_score(self):
        prompt = build_prompt(_request())

        assert "Score : 1 / 2" in prompt
        assert "réponse juste" in prompt and "réponse sans réponse" in prompt
        assert "(aucune réponse)" in prompt
        assert "Voir chapitre 2" in prompt

    def test_truncates_very_long_answers(self):
        long_item = RecommendationItem(question="Q", type=QuestionType.OUVERTE, correct_answer="R",
                                       student_answer="x" * 5000, outcome=AnswerOutcome.INCORRECT)
        prompt = build_prompt(_request(items=[long_item]))

        assert "x" * (recommendations.MAX_ANSWER_CHARS + 1) not in prompt


class TestParseRecommendation:
    def test_parses_plain_json(self):
        rec = parse_recommendation(json.dumps(LLM_JSON))

        assert rec.summary.startswith("Bases solides")
        assert rec.focus_areas == ["Niveaux de pilotage du SI"]

    def test_takes_the_object_not_an_inner_list_when_text_surrounds_it(self):
        rec = parse_recommendation("Voici l'analyse :\n" + json.dumps(LLM_JSON) + "\nBon courage.")

        assert rec.summary.startswith("Bases solides")
        assert rec.weaknesses

    def test_caps_list_length_and_drops_blank_entries(self):
        payload = {**LLM_JSON, "actions": ["", "a", "b", "c", "d", "e", "f"]}
        rec = parse_recommendation(json.dumps(payload))

        assert rec.actions == ["a", "b", "c", "d", "e"][:MAX_ITEMS_PER_LIST]

    def test_rejects_response_without_summary(self):
        with pytest.raises(ValueError):
            parse_recommendation('{"strengths": []}')
        with pytest.raises(ValueError):
            parse_recommendation("pas de JSON du tout")


class TestRecommendationEndpoint:
    @pytest.fixture
    def client(self):
        from app.main import app
        return TestClient(app)

    def _body(self):
        return json.loads(_request().model_dump_json())

    def test_returns_recommendation_with_model(self, client):
        with mock.patch("app.recommendations.complete", return_value=json.dumps(LLM_JSON)):
            resp = client.post("/recommendations", json=self._body())

        assert resp.status_code == 200
        assert resp.json()["summary"].startswith("Bases solides")
        assert resp.json()["model"]

    def test_llm_config_error_is_503(self, client):
        with mock.patch("app.recommendations.complete", side_effect=LlmConfigError("clé manquante")):
            resp = client.post("/recommendations", json=self._body())

        assert resp.status_code == 503

    def test_unusable_llm_answer_is_502(self, client):
        with mock.patch("app.recommendations.complete", return_value="désolé, je ne peux pas"):
            resp = client.post("/recommendations", json=self._body())

        assert resp.status_code == 502

    def test_empty_copy_is_rejected(self, client):
        body = {**self._body(), "items": []}
        resp = client.post("/recommendations", json=body)

        assert resp.status_code == 422
