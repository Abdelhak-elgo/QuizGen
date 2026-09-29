"""
Fournisseurs LLM — mode hors ligne (Ollama local) ou en ligne (API distante).

Sélection via .env :
  LLM_MODE          offline | online
  LLM_MODEL         identifiant du modèle en ligne (ex. claude-haiku-4-5-20251001, gpt-4o-mini,
                    mistral-small-latest, meta/llama-3.1-70b-instruct)
  LLM_API_KEY       clé de l'API en ligne
  LLM_API_BASE_URL  point d'accès de l'API :
                      https://api.anthropic.com         → API Messages Anthropic
                      https://api.openai.com/v1         → OpenAI
                      https://api.mistral.ai/v1         → Mistral
                      https://integrate.api.nvidia.com/v1 → NVIDIA NIM
                      (tout autre point d'accès compatible OpenAI : Groq, OpenRouter…)

Le protocole est déduit du point d'accès : Anthropic a sa propre API
(/v1/messages) ; tous les autres exposent /chat/completions au format OpenAI.
En mode offline, rien ne sort de la machine : Ollama (OLLAMA_URL / OLLAMA_MODEL).
"""
import logging
from dataclasses import dataclass

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()

ANTHROPIC_VERSION = "2023-06-01"


class LlmConfigError(ValueError):
    """Configuration LLM invalide ou incomplète (erreur non récupérable par un nouvel essai)."""


@dataclass(frozen=True)
class LlmTarget:
    """Décrit à quel modèle et par quel protocole les appels sont envoyés."""

    mode: str      # offline | online
    protocol: str  # ollama | anthropic | openai
    model: str
    timeout: int


def resolve_target() -> LlmTarget:
    """Détermine le fournisseur actif depuis la configuration, en validant les variables requises."""
    mode = settings.llm_mode.strip().lower()
    if mode == "offline":
        return LlmTarget("offline", "ollama", settings.ollama_model, settings.ollama_timeout)
    if mode != "online":
        raise LlmConfigError(f"LLM_MODE invalide : {settings.llm_mode!r} (attendu : offline ou online)")

    missing = [
        name for name, value in (
            ("LLM_MODEL", settings.llm_model),
            ("LLM_API_KEY", settings.llm_api_key),
            ("LLM_API_BASE_URL", settings.llm_api_base_url),
        ) if not value.strip()
    ]
    if missing:
        raise LlmConfigError(f"LLM_MODE=online mais variable(s) manquante(s) : {', '.join(missing)}")

    protocol = "anthropic" if "anthropic.com" in settings.llm_api_base_url.lower() else "openai"
    return LlmTarget("online", protocol, settings.llm_model.strip(), settings.llm_api_timeout)


def complete(prompt: str, system: str) -> str:
    """
    Envoie un prompt au fournisseur actif et retourne le texte généré.

    Lève httpx.TimeoutException / httpx.HTTPStatusError en cas d'échec réseau
    ou HTTP (gérés par l'appelant), et LlmConfigError si la configuration est invalide.
    """
    target = resolve_target()
    if target.protocol == "ollama":
        return _complete_ollama(prompt, system, target)
    if target.protocol == "anthropic":
        return _complete_anthropic(prompt, system, target)
    return _complete_openai_compatible(prompt, system, target)


def describe_target() -> dict:
    """Résumé non sensible (jamais la clé) du fournisseur actif, pour /health et les logs."""
    try:
        target = resolve_target()
    except LlmConfigError as exc:
        return {"llm_mode": settings.llm_mode, "llm_error": str(exc)}
    return {"llm_mode": target.mode, "llm_protocol": target.protocol, "llm_model": target.model}


# ── Implémentations par protocole ─────────────────────────────────────────────

def _complete_ollama(prompt: str, system: str, target: LlmTarget) -> str:
    payload = {
        "model": target.model,
        "prompt": prompt,
        "system": system,
        "stream": False,
        "keep_alive": settings.ollama_keep_alive,
        "options": {
            "temperature": settings.llm_temperature,
            "num_predict": settings.llm_max_tokens,
            "num_ctx": settings.ollama_num_ctx,
            # Pénaliser la répétition — aide à diversifier les distracteurs QCM
            "repeat_penalty": 1.1,
        },
    }
    data = _post_json(f"{settings.ollama_url.rstrip('/')}/api/generate", payload, {}, target.timeout)
    return data.get("response", "")


def _complete_anthropic(prompt: str, system: str, target: LlmTarget) -> str:
    base = settings.llm_api_base_url.strip().rstrip("/")
    if base.endswith("/v1"):
        base = base[: -len("/v1")]
    payload = {
        "model": target.model,
        "max_tokens": settings.llm_max_tokens,
        "temperature": settings.llm_temperature,
        "system": system,
        "messages": [{"role": "user", "content": prompt}],
    }
    headers = {"x-api-key": settings.llm_api_key, "anthropic-version": ANTHROPIC_VERSION}
    data = _post_json(f"{base}/v1/messages", payload, headers, target.timeout)
    return "".join(block.get("text", "") for block in data.get("content", []) if block.get("type") == "text")


def _complete_openai_compatible(prompt: str, system: str, target: LlmTarget) -> str:
    base = settings.llm_api_base_url.strip().rstrip("/")
    payload = {
        "model": target.model,
        "max_tokens": settings.llm_max_tokens,
        "temperature": settings.llm_temperature,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ],
    }
    headers = {"Authorization": f"Bearer {settings.llm_api_key}"}
    data = _post_json(f"{base}/chat/completions", payload, headers, target.timeout)
    choices = data.get("choices") or [{}]
    return (choices[0].get("message") or {}).get("content") or ""


def _post_json(url: str, payload: dict, headers: dict, timeout: int) -> dict:
    with httpx.Client(timeout=timeout) as client:
        resp = client.post(url, json=payload, headers=headers)
        resp.raise_for_status()
    return resp.json()
