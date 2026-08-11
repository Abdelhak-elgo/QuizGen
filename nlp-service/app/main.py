"""
QuizGen — Microservice FastAPI NLP
Point d'entrée principal de l'application.
"""
import logging
from typing import Any, Dict, Optional

import httpx
import redis
from celery.result import AsyncResult
from fastapi import FastAPI, HTTPException, status
from fastapi.responses import JSONResponse

from pydantic import BaseModel

from app.bert_scorer import BertScorer
from app.celery_app import celery_app
from app.config import get_settings
from app.metrics import record_bertscore, record_evidence_retrieved, setup_prometheus
from app.llm_client import ping_ollama
from app.models import (
    BatchScoreRequest,
    BatchScoreResponse,
    GenerateRequest,
    GenerateResponse,
    GeneratedQuestion,
    ScoreRequest,
    ScoreResponse,
    TaskStatus,
)
from app.rag_store import retrieve_evidence
from app.tasks import generate_quiz_task

logger = logging.getLogger(__name__)
settings = get_settings()

app = FastAPI(
    title="QuizGen NLP Service",
    description=(
        "Microservice de traitement NLP et génération de questions pédagogiques "
        "via SpaCy, KeyBERT et Mistral/Ollama. "
        "La génération est asynchrone (Celery + Redis) — "
        "soumettre via POST /generate, suivre via GET /status/{task_id}.\n\n"
        "**BERTScore** : POST /score pour corriger les réponses ouvertes (RoBERTa-large)."
    ),
    version="3.0.0",
)

# Activer les métriques Prometheus (endpoint /metrics)
setup_prometheus(app)


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
    - bert_score_available : True si bert-score est installé
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

    # Vérifier bert-score
    scorer = BertScorer.get_instance()
    bert_ok = scorer._check_available()

    overall = "ok" if ollama_ok and redis_ok else "degraded"

    return {
        "status": overall,
        "spacy_model": settings.spacy_model,
        "ollama_available": ollama_ok,
        "ollama_model": settings.ollama_model,
        "redis_available": redis_ok,
        "bert_score_available": bert_ok,
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


# ── BERTScore ─────────────────────────────────────────────────────────────────

def _build_score_response(req: ScoreRequest) -> ScoreResponse:
    """Calcule le BERTScore pour un seul item et construit la réponse."""
    scorer = BertScorer.get_instance()
    result = scorer.score(req.candidate, req.reference)
    partial = scorer.partial_score(result.f1)

    if partial >= 1.0:
        label = "CORRECT"
    elif partial > 0.0:
        label = "PARTIEL"
    else:
        label = "INCORRECT"

    return ScoreResponse(
        question_id=req.question_id,
        f1=result.f1,
        precision=result.precision,
        recall=result.recall,
        partial_score=round(partial, 4),
        model=result.model,
        label=label,
    )


@app.post(
    "/score",
    response_model=ScoreResponse,
    tags=["BERTScore"],
    summary="Corriger une réponse ouverte (BERTScore RoBERTa)",
)
async def score_answer(request: ScoreRequest) -> ScoreResponse:
    """
    Calcule le BERTScore entre la réponse d'un étudiant et la réponse de référence.

    ### Seuils de correction
    | F1 BERTScore | Label      | Score partiel |
    |---|---|---|
    | ≥ 0.70       | CORRECT    | 1.0           |
    | [0.50, 0.70[ | PARTIEL    | interpolé     |
    | < 0.50       | INCORRECT  | 0.0           |

    ### Retour
    - **f1** : Score F1 BERTScore brut
    - **partial_score** : Score normalisé [0, 1] selon les seuils
    - **label** : CORRECT | PARTIEL | INCORRECT
    - **model** : Modèle utilisé (roberta-large par défaut)
    """
    try:
        resp = _build_score_response(request)
        record_bertscore(resp.f1, resp.label)
        return resp
    except Exception as exc:
        logger.error("Erreur /score : %s", exc, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Erreur lors du calcul BERTScore : {exc}",
        )


@app.post(
    "/score/batch",
    response_model=BatchScoreResponse,
    tags=["BERTScore"],
    summary="Corriger plusieurs réponses ouvertes en une seule requête",
)
async def score_batch(request: BatchScoreRequest) -> BatchScoreResponse:
    """
    Calcule le BERTScore pour plusieurs paires (candidat, référence) en une seule requête.
    Utilisé par Spring Boot pour traiter toutes les réponses ouvertes d'un Attempt.
    """
    if not request.items:
        return BatchScoreResponse(results=[], total_items=0, available=True)

    scorer = BertScorer.get_instance()
    available = scorer._check_available()

    results = []
    for item in request.items:
        try:
            resp = _build_score_response(item)
            record_bertscore(resp.f1, resp.label)
            results.append(resp)
        except Exception as exc:
            logger.error("Erreur item %s : %s", item.question_id, exc)
            results.append(ScoreResponse(
                question_id=item.question_id,
                f1=0.0, precision=0.0, recall=0.0,
                partial_score=0.0, model="error", label="INCORRECT",
            ))

    return BatchScoreResponse(results=results, total_items=len(results), available=available)


# ── Evidence RAG ──────────────────────────────────────────────────────────────

class EvidenceRequest(BaseModel):
    """Corps de la requête POST /evidence."""
    document_id: str
    student_answer: str


class EvidenceResponse(BaseModel):
    """
    Réponse de l'endpoint /evidence.

    - evidence_text:    Passage du document le plus similaire à la réponse étudiant.
                        Null si ChromaDB est indisponible ou si le document n'est pas indexé.
    - similarity_score: Score de similarité cosinus [0, 1] entre la réponse et le passage.
                        Null si evidence_text est null.
    """
    evidence_text: Optional[str] = None
    similarity_score: Optional[float] = None


@app.post(
    "/evidence",
    response_model=EvidenceResponse,
    tags=["Evidence RAG"],
    summary="Retrouver le passage source correspondant à une réponse étudiant",
)
async def get_evidence(request: EvidenceRequest) -> EvidenceResponse:
    """
    Utilise ChromaDB (indexé lors de la génération de quiz) pour retrouver
    le passage du document source le plus proche de la réponse d'un étudiant.

    Ce "passage-evidence" est retourné avec le score de correction BERTScore
    pour permettre une correction explicable (XAI — Explainable AI) :
    l'étudiant voit quel passage du cours sa réponse est censée couvrir.

    ### Comportement de fallback
    Si ChromaDB est indisponible ou si le document n'a pas encore été indexé
    (cas de régression), retourne `{evidence_text: null, similarity_score: null}`
    sans lever d'erreur — la correction BERTScore continue normalement.

    ### Corps de la requête
    - **document_id** : UUID du document (même identifiant que lors de la génération)
    - **student_answer** : Réponse de l'étudiant (utilisée comme requête de similarité)
    """
    if not request.student_answer or not request.student_answer.strip():
        return EvidenceResponse()

    try:
        result = retrieve_evidence(
            document_id=request.document_id,
            query_text=request.student_answer.strip(),
            embedding_model=None,  # ChromaDB gère l'embedding en mode API
        )
        if result and result.get("text"):
            similarity = float(result.get("similarity", 0.0))
            record_evidence_retrieved(similarity, found=True)
            logger.info(
                "[evidence] doc_id=%s similarity=%.2f chunk_preview=%.60s...",
                request.document_id, similarity, result["text"],
            )
            return EvidenceResponse(
                evidence_text=result["text"],
                similarity_score=round(similarity, 4),
            )
        else:
            record_evidence_retrieved(0.0, found=False)
            logger.debug("[evidence] doc_id=%s — aucun chunk trouvé", request.document_id)
            return EvidenceResponse()

    except Exception as exc:
        logger.warning("[evidence] erreur pour doc_id=%s : %s", request.document_id, exc)
        record_evidence_retrieved(0.0, found=False)
        return EvidenceResponse()


# ── Gestionnaire d'erreurs global ─────────────────────────────────────────────

@app.exception_handler(ValueError)
async def value_error_handler(_request, exc: ValueError):
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={"detail": str(exc)},
    )
