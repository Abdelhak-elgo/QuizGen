"""Tests du choix de fournisseur LLM (offline Ollama / online API) et des protocoles."""
import json
from unittest import mock

import httpx
import pytest

from app import llm_providers
from app.llm_client import generate_questions
from app.llm_providers import LlmConfigError, complete, describe_target, resolve_target
from app.models import Difficulty, QuestionType

QUESTION_JSON = json.dumps([{
    "type": "QCM",
    "content": "Qu'est-ce qu'un SI ?",
    "options": ["Un système d'information", "Un serveur", "Un langage", "Un réseau"],
    "correct_answer": "Un système d'information",
    "explanation": "Défini dans le cours.",
    "difficulty": "MOYEN",
    "keywords": ["SI"],
}])


@pytest.fixture
def online(monkeypatch):
    """Configure le mode en ligne ; chaque test fixe le point d'accès voulu."""
    def configure(base_url: str, model: str = "test-model", key: str = "sk-secret"):
        s = llm_providers.settings
        monkeypatch.setattr(s, "llm_mode", "online")
        monkeypatch.setattr(s, "llm_model", model)
        monkeypatch.setattr(s, "llm_api_key", key)
        monkeypatch.setattr(s, "llm_api_base_url", base_url)
    return configure


def _mock_http(json_body: dict, status_code: int = 200):
    """Remplace httpx.Client ; retourne le mock du client pour inspecter l'appel."""
    client = mock.MagicMock()
    client.__enter__.return_value = client
    response = mock.MagicMock(status_code=status_code, text="error body")
    response.json.return_value = json_body
    if status_code >= 400:
        response.raise_for_status.side_effect = httpx.HTTPStatusError(
            "error", request=mock.MagicMock(), response=response)
    client.post.return_value = response
    return mock.patch("app.llm_providers.httpx.Client", return_value=client), client


class TestResolveTarget:
    def test_offline_by_default_uses_ollama(self, monkeypatch):
        monkeypatch.setattr(llm_providers.settings, "llm_mode", "offline")
        target = resolve_target()
        assert (target.mode, target.protocol) == ("offline", "ollama")

    def test_online_requires_model_key_and_endpoint(self, online):
        online(base_url="https://api.openai.com/v1", key="")
        with pytest.raises(LlmConfigError, match="LLM_API_KEY"):
            resolve_target()

    def test_invalid_mode_is_rejected(self, monkeypatch):
        monkeypatch.setattr(llm_providers.settings, "llm_mode", "cloud")
        with pytest.raises(LlmConfigError, match="LLM_MODE"):
            resolve_target()

    @pytest.mark.parametrize("base_url, protocol", [
        ("https://api.anthropic.com", "anthropic"),
        ("https://api.openai.com/v1", "openai"),
        ("https://api.mistral.ai/v1", "openai"),
        ("https://integrate.api.nvidia.com/v1", "openai"),
    ])
    def test_protocol_detected_from_endpoint(self, online, base_url, protocol):
        online(base_url=base_url)
        assert resolve_target().protocol == protocol

    def test_describe_target_never_exposes_the_key(self, online):
        online(base_url="https://api.anthropic.com", key="sk-very-secret")
        assert "sk-very-secret" not in json.dumps(describe_target())


class TestComplete:
    @pytest.mark.parametrize("base_url", ["https://api.anthropic.com", "https://api.anthropic.com/v1/"])
    def test_anthropic_messages_api(self, online, base_url):
        online(base_url=base_url, model="claude-haiku-4-5-20251001")
        patcher, client = _mock_http({"content": [{"type": "text", "text": "réponse"}]})

        with patcher:
            assert complete("prompt", "system") == "réponse"

        url = client.post.call_args.args[0]
        kwargs = client.post.call_args.kwargs
        assert url == "https://api.anthropic.com/v1/messages"
        assert kwargs["headers"]["x-api-key"] == "sk-secret"
        assert kwargs["json"]["system"] == "system"
        assert kwargs["json"]["model"] == "claude-haiku-4-5-20251001"

    def test_openai_compatible_chat_completions(self, online):
        online(base_url="https://integrate.api.nvidia.com/v1/")
        patcher, client = _mock_http({"choices": [{"message": {"content": "réponse"}}]})

        with patcher:
            assert complete("prompt", "system") == "réponse"

        url = client.post.call_args.args[0]
        kwargs = client.post.call_args.kwargs
        assert url == "https://integrate.api.nvidia.com/v1/chat/completions"
        assert kwargs["headers"]["Authorization"] == "Bearer sk-secret"
        assert kwargs["json"]["messages"][0] == {"role": "system", "content": "system"}


class TestGenerateQuestionsOnline:
    def test_generates_questions_through_online_provider(self, online):
        online(base_url="https://api.openai.com/v1")
        patcher, _ = _mock_http({"choices": [{"message": {"content": QUESTION_JSON}}]})

        with patcher:
            questions = generate_questions(
                context="Un SI est un système d'information.", keywords=["SI"],
                question_type=QuestionType.QCM, nb=1, difficulty=Difficulty.MOYEN,
            )

        assert len(questions) == 1

    def test_invalid_key_fails_fast_without_retry(self, online):
        online(base_url="https://api.anthropic.com")
        patcher, client = _mock_http({}, status_code=401)

        with patcher, pytest.raises(LlmConfigError, match="HTTP 401"):
            generate_questions(
                context="Texte.", keywords=[], question_type=QuestionType.QCM,
                nb=1, difficulty=Difficulty.MOYEN, max_retries=3,
            )
        assert client.post.call_count == 1
