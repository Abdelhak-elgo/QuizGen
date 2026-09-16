"""Tests unitaires pour le module llm_client (mock Ollama)."""
import json
import unittest.mock as mock

import pytest

from app.models import Difficulty, GeneratedQuestion, QuestionType
from app.llm_client import (
    _build_prompt,
    _extract_json_from_text,
    _parse_questions,
    generate_questions,
    ping_ollama,
)


# ── Extraction JSON ───────────────────────────────────────────────────────────

class TestExtractJsonFromText:
    def test_clean_json_array(self):
        text = '[{"type": "QCM", "content": "Question ?"}]'
        result = _extract_json_from_text(text)
        assert result == [{"type": "QCM", "content": "Question ?"}]

    def test_json_with_preamble(self):
        text = "Voici les questions générées :\n[{\"type\": \"QCM\", \"content\": \"Test ?\"}]"
        result = _extract_json_from_text(text)
        assert result is not None
        assert isinstance(result, list)

    def test_invalid_json_returns_none(self):
        text = "Ceci n'est pas du JSON valide {broken"
        result = _extract_json_from_text(text)
        assert result is None

    def test_empty_string_returns_none(self):
        assert _extract_json_from_text("") is None

    def test_json_object_extracted(self):
        text = '{"type": "OUVERTE", "content": "Question ouverte ?"}'
        result = _extract_json_from_text(text)
        assert result is not None


# ── Build prompt ──────────────────────────────────────────────────────────────

class TestBuildPrompt:
    def test_qcm_prompt_contains_keywords(self):
        prompt = _build_prompt(
            QuestionType.QCM,
            context="Texte sur l'IA",
            keywords=["machine learning", "réseau de neurones"],
            nb=3,
            difficulty=Difficulty.MOYEN,
        )
        assert "machine learning" in prompt
        assert "QCM" in prompt
        assert "3" in prompt

    def test_ouverte_prompt_structure(self):
        prompt = _build_prompt(
            QuestionType.OUVERTE,
            context="Texte source",
            keywords=["concept clé"],
            nb=2,
            difficulty=Difficulty.DIFFICILE,
        )
        assert "OUVERTE" in prompt
        assert "DIFFICILE" in prompt

    def test_context_truncated_to_3000_chars(self):
        long_context = "x" * 5000
        prompt = _build_prompt(
            QuestionType.QCM,
            context=long_context,
            keywords=[],
            nb=1,
            difficulty=Difficulty.FACILE,
        )
        # Le contexte dans le prompt ne doit pas dépasser 3000 chars
        assert "x" * 3001 not in prompt


# ── Parse questions ───────────────────────────────────────────────────────────

class TestParseQuestions:
    def test_valid_qcm_parsed(self):
        raw = [
            {
                "type": "QCM",
                "content": "Qu'est-ce que l'IA ?",
                "options": ["Option A", "Option B", "Option C", "Option D"],
                "correct_answer": "Option A",
                "explanation": "Explication.",
                "difficulty": "MOYEN",
                "keywords": ["IA"],
            }
        ]
        questions = _parse_questions(raw, QuestionType.QCM, Difficulty.MOYEN)
        assert len(questions) == 1
        assert questions[0].type == QuestionType.QCM
        assert questions[0].content == "Qu'est-ce que l'IA ?"

    def test_skips_invalid_items(self):
        raw = [
            {"type": "QCM", "content": "", "correct_answer": "A"},  # content vide
            {
                "type": "QCM",
                "content": "Question valide ?",
                "correct_answer": "Réponse",
            },
        ]
        questions = _parse_questions(raw, QuestionType.QCM, Difficulty.MOYEN)
        # La question avec content vide est ignorée (correct_answer vide ou content vide)
        assert len(questions) == 1

    def test_dict_input_wrapped_in_list(self):
        raw = {
            "type": "OUVERTE",
            "content": "Question ouverte ?",
            "correct_answer": "Réponse modèle.",
        }
        questions = _parse_questions(raw, QuestionType.OUVERTE, Difficulty.MOYEN)
        assert len(questions) == 1

    def test_empty_list(self):
        assert _parse_questions([], QuestionType.QCM, Difficulty.MOYEN) == []


# ── generate_questions (mock Ollama) ──────────────────────────────────────────

class TestGenerateQuestions:
    def _make_ollama_response(self, questions_json: list) -> dict:
        return {"response": json.dumps(questions_json)}

    @mock.patch("app.llm_client.httpx.Client")
    def test_successful_generation(self, mock_client_class):
        mock_response_data = [
            {
                "type": "QCM",
                "content": "Qu'est-ce que le deep learning ?",
                "options": ["Réseau de neurones profonds", "Langage de prog", "Base de données", "OS"],
                "correct_answer": "Réseau de neurones profonds",
                "difficulty": "MOYEN",
                "keywords": ["deep learning"],
            }
        ]
        mock_resp = mock.MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {"response": json.dumps(mock_response_data)}
        mock_resp.raise_for_status = mock.MagicMock()

        mock_client = mock.MagicMock()
        mock_client.__enter__ = mock.MagicMock(return_value=mock_client)
        mock_client.__exit__ = mock.MagicMock(return_value=False)
        mock_client.post.return_value = mock_resp
        mock_client_class.return_value = mock_client

        questions = generate_questions(
            context="Texte sur le deep learning.",
            keywords=["deep learning", "réseau de neurones"],
            question_type=QuestionType.QCM,
            nb=1,
            difficulty=Difficulty.MOYEN,
            max_retries=1,
        )

        assert len(questions) == 1
        assert questions[0].type == QuestionType.QCM

    @mock.patch("app.llm_client.httpx.Client")
    def test_malformed_json_triggers_retry(self, mock_client_class):
        mock_resp = mock.MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {"response": "Texte sans JSON valide."}
        mock_resp.raise_for_status = mock.MagicMock()

        mock_client = mock.MagicMock()
        mock_client.__enter__ = mock.MagicMock(return_value=mock_client)
        mock_client.__exit__ = mock.MagicMock(return_value=False)
        mock_client.post.return_value = mock_resp
        mock_client_class.return_value = mock_client

        questions = generate_questions(
            context="Texte.",
            keywords=[],
            question_type=QuestionType.QCM,
            nb=1,
            difficulty=Difficulty.MOYEN,
            max_retries=2,  # 2 tentatives, toutes échouent → liste vide
        )

        assert questions == []
        assert mock_client.post.call_count == 2  # 2 tentatives

    @mock.patch("app.llm_client.httpx.Client")
    def test_timeout_returns_empty(self, mock_client_class):
        import httpx

        mock_client = mock.MagicMock()
        mock_client.__enter__ = mock.MagicMock(return_value=mock_client)
        mock_client.__exit__ = mock.MagicMock(return_value=False)
        mock_client.post.side_effect = httpx.TimeoutException("timeout")
        mock_client_class.return_value = mock_client

        questions = generate_questions(
            context="Texte.",
            keywords=[],
            question_type=QuestionType.QCM,
            nb=1,
            difficulty=Difficulty.MOYEN,
            max_retries=1,
        )
        assert questions == []


# ── ping_ollama ───────────────────────────────────────────────────────────────

class TestPingOllama:
    @mock.patch("app.llm_client.httpx.Client")
    def test_ping_success(self, mock_client_class):
        mock_resp = mock.MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {"models": [{"name": "mistral:latest"}]}

        mock_client = mock.MagicMock()
        mock_client.__enter__ = mock.MagicMock(return_value=mock_client)
        mock_client.__exit__ = mock.MagicMock(return_value=False)
        mock_client.get.return_value = mock_resp
        mock_client_class.return_value = mock_client

        assert ping_ollama() is True

    @mock.patch("app.llm_client.httpx.Client")
    def test_ping_model_not_found(self, mock_client_class):
        mock_resp = mock.MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {"models": [{"name": "llama2:latest"}]}  # Pas mistral

        mock_client = mock.MagicMock()
        mock_client.__enter__ = mock.MagicMock(return_value=mock_client)
        mock_client.__exit__ = mock.MagicMock(return_value=False)
        mock_client.get.return_value = mock_resp
        mock_client_class.return_value = mock_client

        assert ping_ollama() is False

    @mock.patch("app.llm_client.httpx.Client")
    def test_ping_connection_error(self, mock_client_class):
        mock_client = mock.MagicMock()
        mock_client.__enter__ = mock.MagicMock(return_value=mock_client)
        mock_client.__exit__ = mock.MagicMock(return_value=False)
        mock_client.get.side_effect = Exception("Connection refused")
        mock_client_class.return_value = mock_client

        assert ping_ollama() is False
