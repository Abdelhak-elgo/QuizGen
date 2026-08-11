"""
QuizGen — Microservice FastAPI NLP
Endpoint /health opérationnel. La Phase 2 implémentera /generate.
"""
from fastapi import FastAPI
from pydantic import BaseModel
import httpx
import os

app = FastAPI(
    title="QuizGen NLP Service",
    description="Microservice de traitement NLP et génération de questions via Mistral/Ollama",
    version="1.0.0",
)

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://ollama:11434")


class HealthResponse(BaseModel):
    status: str
    spacy_model: str
    ollama_available: bool
    redis_available: bool


@app.get("/health", response_model=HealthResponse)
async def health():
    """Vérifie l'état du service et de ses dépendances."""
    ollama_ok = False
    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            resp = await client.get(f"{OLLAMA_URL}/api/tags")
            ollama_ok = resp.status_code == 200
    except Exception:
        pass

    # Redis check sera ajouté en Phase 2 avec Celery
    return HealthResponse(
        status="ok",
        spacy_model="fr_core_news_lg",
        ollama_available=ollama_ok,
        redis_available=False,  # mis à jour Phase 2
    )
