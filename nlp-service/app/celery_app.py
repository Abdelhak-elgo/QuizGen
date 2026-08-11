"""Configuration et création de l'instance Celery."""
from celery import Celery
from app.config import get_settings

settings = get_settings()

celery_app = Celery(
    "quizgen_nlp",
    broker=settings.redis_url,
    backend=settings.get_celery_backend(),
    include=["app.tasks"],
)

celery_app.conf.update(
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    timezone="Africa/Casablanca",
    enable_utc=True,
    task_track_started=True,
    result_expires=3600,  # Les résultats expirent après 1h
    worker_prefetch_multiplier=1,  # 1 tâche par worker (génération lourde)
    task_acks_late=True,           # Acquittement après complétion (fiabilité)
)
