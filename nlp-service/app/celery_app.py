"""Configuration et création de l'instance Celery."""
import logging

from celery import Celery
from celery.signals import worker_init

from app.config import get_settings

logger = logging.getLogger(__name__)
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
    # Acquittement à la réception : avec acks_late, une tâche longue interrompue
    # (OOM, redémarrage du conteneur) était remise en file et rejouée à chaque
    # redémarrage du worker, sans plus aucun quiz pour en consommer le résultat.
    # Une génération perdue est détectée côté backend (délai max) et relancée
    # par l'enseignant.
    task_acks_late=False,
)


@worker_init.connect
def preload_nlp_models(**kwargs) -> None:
    """
    Charge les modèles SpaCy et KeyBERT dans le processus parent, avant que
    le pool prefork ne forke les processus worker.

    Sans ça, chaque modèle est chargé paresseusement (lru_cache) au moment
    du premier appel — c'est-à-dire à l'intérieur d'une tâche Celery, dans un
    processus déjà forké. Conséquences :
      - la première tâche traitée par chaque worker paie le coût de
        chargement du modèle (plusieurs dizaines de secondes) ;
      - avec concurrency > 1, chaque processus enfant charge sa propre copie
        du modèle en mémoire (~450 Mo pour KeyBERT), sans partage possible.

    En chargeant ici, dans le processus parent, les enfants héritent la
    mémoire par copy-on-write au lieu de dupliquer le modèle par worker.
    """
    from app.nlp_pipeline import _load_keybert_model, _load_spacy_model

    logger.info("Préchargement des modèles NLP avant fork des workers...")
    _load_spacy_model()
    _load_keybert_model()
    logger.info("Modèles NLP prêts — fork des workers.")
