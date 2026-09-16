"""
QuizGen NLP Service — Métriques Prometheus
==========================================

Expose des métriques métier via prometheus-client :
  - Requêtes HTTP (count, latence, status)
  - BERTScore (F1 distribution, labels CORRECT/PARTIEL/INCORRECT)
  - Génération Ollama (durée, tokens)
  - Cache Redis PDF (hits/misses)
  - Workers Celery actifs

Usage (dans main.py) :
  from app.metrics import setup_prometheus, HTTP_REQUESTS, BERTSCORE_F1
  # L'endpoint /metrics est ajouté automatiquement par setup_prometheus(app)
"""
import time
from contextlib import contextmanager
from typing import Generator

try:
    from prometheus_client import (
        CONTENT_TYPE_LATEST,
        Counter,
        Gauge,
        Histogram,
        Summary,
        generate_latest,
        CollectorRegistry,
        REGISTRY,
    )
    from fastapi import FastAPI, Response
    HAS_PROMETHEUS = True
except ImportError:
    HAS_PROMETHEUS = False


# ── Registre de métriques ─────────────────────────────────────────────────────

if HAS_PROMETHEUS:
    # Requêtes HTTP
    HTTP_REQUESTS = Counter(
        "quizgen_nlp_http_requests_total",
        "Nombre total de requêtes HTTP",
        ["method", "endpoint", "http_status"],
    )

    HTTP_LATENCY = Histogram(
        "quizgen_nlp_http_request_duration_seconds",
        "Latence des requêtes HTTP en secondes",
        ["method", "endpoint"],
        buckets=[0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0],
    )

    # BERTScore
    BERTSCORE_F1 = Summary(
        "quizgen_nlp_bertscore_f1",
        "Distribution du score F1 BERTScore (moyenne glissante)",
    )

    BERTSCORE_LABELS = Counter(
        "quizgen_nlp_bertscore_labels_total",
        "Nombre de labels BERTScore attribués",
        ["label"],   # CORRECT | PARTIEL | INCORRECT
    )

    BERTSCORE_DURATION = Histogram(
        "quizgen_nlp_bertscore_duration_seconds",
        "Durée d'un appel BERTScore (modèle chargé)",
        buckets=[0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0],
    )

    # Génération Ollama
    OLLAMA_GENERATION_DURATION = Histogram(
        "quizgen_nlp_ollama_generation_seconds",
        "Durée de génération par Ollama (appel LLM)",
        ["model"],
        buckets=[1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0],
    )

    OLLAMA_GENERATION_ERRORS = Counter(
        "quizgen_nlp_ollama_generation_errors_total",
        "Nombre d'erreurs lors de la génération Ollama",
        ["model", "error_type"],
    )

    QUESTIONS_GENERATED = Counter(
        "quizgen_nlp_questions_generated_total",
        "Nombre total de questions générées",
        ["question_type", "difficulty"],
    )

    # Cache Redis PDF
    PDF_CACHE_HITS = Counter(
        "quizgen_nlp_pdf_cache_hits_total",
        "Nombre de hits du cache Redis pour les extractions PDF",
    )

    PDF_CACHE_MISSES = Counter(
        "quizgen_nlp_pdf_cache_misses_total",
        "Nombre de misses du cache Redis pour les extractions PDF",
    )

    PDF_EXTRACTION_DURATION = Histogram(
        "quizgen_nlp_pdf_extraction_seconds",
        "Durée d'extraction de texte depuis un PDF",
        buckets=[0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0],
    )

    # Workers Celery
    CELERY_TASKS_ACTIVE = Gauge(
        "quizgen_nlp_celery_tasks_active",
        "Nombre de tâches Celery actives (en cours de traitement)",
    )

    CELERY_TASKS_COMPLETED = Counter(
        "quizgen_nlp_celery_tasks_completed_total",
        "Nombre total de tâches Celery terminées",
        ["status"],   # success | failure
    )

    # Evidence RAG (BERTScore explicable)
    EVIDENCE_SIMILARITY_SCORE = Histogram(
        "quizgen_nlp_evidence_similarity_score",
        "Score de similarité cosinus entre la réponse étudiant et le passage source (Evidence RAG)",
        buckets=[0.05, 0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 0.95, 1.0],
    )

    EVIDENCE_RETRIEVAL_TOTAL = Counter(
        "quizgen_nlp_evidence_retrieval_total",
        "Nombre de récupérations d'evidence RAG pour la correction BERTScore",
        ["result"],   # found | not_found
    )


# ── Context managers pour mesures ────────────────────────────────────────────

@contextmanager
def measure_bertscore_duration() -> Generator[None, None, None]:
    """Mesure la durée d'un appel BERTScore."""
    if not HAS_PROMETHEUS:
        yield
        return
    with BERTSCORE_DURATION.time():
        yield


@contextmanager
def measure_ollama_duration(model: str = "mistral") -> Generator[None, None, None]:
    """Mesure la durée d'un appel Ollama."""
    if not HAS_PROMETHEUS:
        yield
        return
    with OLLAMA_GENERATION_DURATION.labels(model=model).time():
        yield


@contextmanager
def measure_pdf_extraction() -> Generator[None, None, None]:
    """Mesure la durée d'extraction PDF."""
    if not HAS_PROMETHEUS:
        yield
        return
    with PDF_EXTRACTION_DURATION.time():
        yield


# ── Helpers ────────────────────────────────────────────────────────────────────

def record_bertscore(f1: float, label: str) -> None:
    """Enregistre un score BERTScore."""
    if not HAS_PROMETHEUS:
        return
    BERTSCORE_F1.observe(f1)
    BERTSCORE_LABELS.labels(label=label).inc()


def record_ollama_error(model: str, error_type: str) -> None:
    """Enregistre une erreur de génération Ollama."""
    if not HAS_PROMETHEUS:
        return
    OLLAMA_GENERATION_ERRORS.labels(model=model, error_type=error_type).inc()


def record_pdf_cache_hit() -> None:
    if HAS_PROMETHEUS:
        PDF_CACHE_HITS.inc()


def record_pdf_cache_miss() -> None:
    if HAS_PROMETHEUS:
        PDF_CACHE_MISSES.inc()


def record_question_generated(question_type: str, difficulty: str = "MOYEN") -> None:
    if HAS_PROMETHEUS:
        QUESTIONS_GENERATED.labels(
            question_type=question_type,
            difficulty=difficulty,
        ).inc()


def set_celery_active_tasks(count: int) -> None:
    if HAS_PROMETHEUS:
        CELERY_TASKS_ACTIVE.set(count)


def record_celery_task_completed(success: bool) -> None:
    if HAS_PROMETHEUS:
        CELERY_TASKS_COMPLETED.labels(status="success" if success else "failure").inc()


def record_evidence_retrieved(similarity: float, found: bool) -> None:
    """
    Enregistre une récupération d'evidence RAG.

    Args:
        similarity: Score de similarité cosinus [0, 1] (ignoré si found=False).
        found:      True si un passage a été trouvé, False sinon.
    """
    if not HAS_PROMETHEUS:
        return
    EVIDENCE_RETRIEVAL_TOTAL.labels(result="found" if found else "not_found").inc()
    if found and similarity > 0:
        EVIDENCE_SIMILARITY_SCORE.observe(similarity)


# ── Middleware FastAPI ──────────────────────────────────────────────────────────

def setup_prometheus(app: "FastAPI") -> None:
    """
    Ajoute l'endpoint /metrics Prometheus à l'application FastAPI.
    Enregistre aussi un middleware pour collecter les métriques HTTP.
    """
    if not HAS_PROMETHEUS:
        return

    from starlette.middleware.base import BaseHTTPMiddleware
    from starlette.requests import Request

    class PrometheusMiddleware(BaseHTTPMiddleware):
        async def dispatch(self, request: Request, call_next):
            start = time.perf_counter()
            endpoint = request.url.path
            method   = request.method

            response = await call_next(request)

            duration = time.perf_counter() - start
            status   = str(response.status_code)

            HTTP_REQUESTS.labels(
                method=method, endpoint=endpoint, http_status=status
            ).inc()
            HTTP_LATENCY.labels(method=method, endpoint=endpoint).observe(duration)

            return response

    app.add_middleware(PrometheusMiddleware)

    @app.get("/metrics", include_in_schema=False, tags=["Monitoring"])
    async def prometheus_metrics():
        """Endpoint Prometheus — scrappé par Prometheus server."""
        return Response(
            generate_latest(REGISTRY),
            media_type=CONTENT_TYPE_LATEST,
        )
