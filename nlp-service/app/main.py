"""
QuizGen — Microservice FastAPI NLP
Point d'entrée principal de l'application.
"""
import logging
from typing import Any, Dict

import httpx
import redis
from celery.result import AsyncResult
from fastapi import FastAPI, HTTPException, status
from fastapi.responses import JSONResponse

from app.celery_app import celery_app
from app.config import get_settings
from app.llm_client import ping_ollama
from app.models import (
    GenerateRequest,
    GenerateResponse,
    GeneratedQuestion,
    TaskStatus,
)
from app.tasks import generate_quiz_task

logger = logging.getLogger(__name__)
settings = get_settings()

app = FastAPI(
    title="QuizGen NLP Service",
    description=(
        "Microservice de traitement NLP et génération de questions pédagogiques "
        "via SpaCy, KeyBERT et Mistral/Ollama. "
        "La génération est asynchrone (Celery + Redis) — "
        "soumettre via POST /generate, suivre via GET /status/{task_id}."
    ),
    version="2.0.0",
)


# ── Health ────────────────────────────────────────────────────────────────────

@app.get("/health", tags=["Monitoring"])
async def health() -> Dict[str, Any]:
    """
    Vérifie l'état du service et de ses dépendances.

    Retourne :
    - status : "ok" ou "degraded"
    - spacy_model : nom du modèle SpaCy configuré
    - ollama_available : True si Ollama répond et que le modèle est chargé
    - redis_available : True si Redis est accessible
    - ollama_model : nom du modèle LLM utilisé
    """
    # Ping Ollama
    ollama_ok = ping_ollama()

    # Ping Redis
    redis_ok = False
    try:
        r = redis.from_url(settings.redis_url, socket_timeout=2)
        r.ping()
        redis_ok = True
    except Exception:
        pass

    overall = "ok" if ollama_ok and redis_ok else "degraded"

    return {
        "status": overall,
        "spacy_model": settings.spacy_model,
        "ollama_available": ollama_ok,
        "ollama_model": settings.ollama_model,
        "redis_available": redis_ok,
    }


# ── Generate ──────────────────────────────────────────────────────────────────

@app.post(
    "/generate",
    response_model=GenerateResponse,
    status_code=status.HTTP_202_ACCEPTED,
    tags=["Quiz Generation"],
)
async def generate(request: GenerateRequest) -> GenerateResponse:
    """
    Soumet une tâche de génération de quiz.

    La génération est asynchrone. Utiliser **GET /status/{task_id}** pour
    suivre l'avancement et récupérer les questions générées une fois la tâche terminée.

    ### Corps de la requête
    - **document_id** : ID du document (pour référence)
    - **bucket_name** : Bucket MinIO (défaut : `documents`)
    - **object_key** : Clé de l'objet PDF dans MinIO
    - **nb_questions** : Nombre de questions (3–25, défaut : 10)
    - **question_types** : Types souhaités (`QCM`, `OUVERTE`, `EXERCICE`)
    - **difficulty** : `FACILE`, `MOYEN` ou `DIFFICILE`
    """
    task = generate_quiz_task.apply_async(kwargs={"request_dict": request.model_dump()})
    logger.info(
        "Tâche soumise : task_id=%s, document=%s, nb=%d",
        task.id, request.document_id, request.nb_questions,
    )
    return GenerateResponse(
        task_id=task.id,
        status="PENDING",
        message=f"Génération en cours — suivre via GET /status/{task.id}",
    )


# ── Status ────────────────────────────────────────────────────────────────────

@app.get(
    "/status/{task_id}",
    response_model=TaskStatus,
    tags=["Quiz Generation"],
)
async def get_status(task_id: str) -> TaskStatus:
    """
    Retourne l'état d'une tâche de génération.

    ### États possibles
    - **PENDING** : En attente dans la file Redis
    - **STARTED** : Le worker a pris en charge la tâche
    - **PROGRESS** : En cours (champ `progress` de 0 à 100)
    - **SUCCESS** : Terminé — champ `result` contient la liste de questions
    - **FAILURE** : Échec — champ `error` contient le message d'erreur
    """
    result: AsyncResult = AsyncResult(task_id, app=celery_app)

    state = result.state

    if state == "PENDING":
        return TaskStatus(task_id=task_id, status="PENDING", progress=0)

    if state == "STARTED":
        return TaskStatus(task_id=task_id, status="STARTED", progress=5)

    if state == "PROGRESS":
        meta = result.info or {}
        return TaskStatus(
            task_id=task_id,
            status="PROGRESS",
            progress=meta.get("progress", 0),
        )

    if state == "SUCCESS":
        data = result.result or {}
        questions_raw = data.get("questions", [])
        questions = [GeneratedQuestion(**q) for q in questions_raw]
        return TaskStatus(
            task_id=task_id,
            status="SUCCESS",
            progress=100,
            result=questions,
        )

    if state == "FAILURE":
        error_info = str(result.info) if result.info else "Erreur inconnue"
        return TaskStatus(
            task_id=task_id,
            status="FAILURE",
            error=error_info,
        )

    # États Celery non gérés (ex: REVOKED, RETRY)
    return TaskStatus(task_id=task_id, status=state)


# ── Gestionnaire d'erreurs global ─────────────────────────────────────────────

@app.exception_handler(ValueError)
async def value_error_handler(_request, exc: ValueError):
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={"detail": str(exc)},
    )
