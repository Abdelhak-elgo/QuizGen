"""
Module BERTScore — correction sémantique des réponses ouvertes.

Utilise bert_score (RoBERTa-large en production, distilbert en test/CI)
pour calculer la similarité sémantique entre la réponse d'un étudiant
et la réponse de référence.

Le modèle est chargé en singleton au premier appel (lazy init) pour ne pas
bloquer le démarrage du service.
"""
import logging
import os
from dataclasses import dataclass
from threading import Lock
from typing import Optional

logger = logging.getLogger(__name__)

# Modèle configurable via env var pour permettre un modèle plus léger en CI
_DEFAULT_MODEL = os.environ.get("BERT_SCORE_MODEL", "roberta-large")
_LANG = os.environ.get("BERT_SCORE_LANG", "fr")


@dataclass
class BertScoreResult:
    f1: float
    precision: float
    recall: float
    model: str


class BertScorer:
    """Singleton thread-safe pour le calcul BERTScore."""

    _instance: Optional["BertScorer"] = None
    _lock: Lock = Lock()

    def __init__(self, model_type: str = _DEFAULT_MODEL, lang: str = _LANG):
        self._model_type = model_type
        self._lang = lang
        self._available: Optional[bool] = None  # None = pas encore initialisé

    @classmethod
    def get_instance(cls) -> "BertScorer":
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = cls()
        return cls._instance

    def _check_available(self) -> bool:
        if self._available is None:
            try:
                import bert_score  # noqa: F401
                self._available = True
                logger.info("bert-score disponible — modèle=%s", self._model_type)
            except ImportError:
                self._available = False
                logger.warning(
                    "bert-score non installé — correction sémantique désactivée. "
                    "Installer avec : pip install bert-score"
                )
        return self._available

    def score(self, candidate: str, reference: str) -> BertScoreResult:
        """
        Calcule le BERTScore entre un candidat et une référence.

        Retourne un BertScoreResult avec F1, précision et rappel.
        En cas d'indisponibilité de bert-score, retourne des zéros
        pour ne pas bloquer la soumission de l'étudiant.

        Seuils de correction :
          F1 ≥ 0.70 → correct (score plein)
          F1 ∈ [0.50, 0.70[ → partiel (score proportionnel)
          F1 < 0.50 → incorrect (score 0)
        """
        if not candidate or not candidate.strip():
            return BertScoreResult(f1=0.0, precision=0.0, recall=0.0, model=self._model_type)

        if not self._check_available():
            # Fallback gracieux : score 0 si bert-score non disponible
            return BertScoreResult(f1=0.0, precision=0.0, recall=0.0, model="unavailable")

        try:
            from bert_score import score as _score

            P, R, F1 = _score(
                cands=[candidate.strip()],
                refs=[reference.strip()],
                model_type=self._model_type,
                lang=self._lang,
                verbose=False,
            )
            return BertScoreResult(
                f1=round(float(F1[0]), 4),
                precision=round(float(P[0]), 4),
                recall=round(float(R[0]), 4),
                model=self._model_type,
            )
        except Exception as exc:
            logger.error("Erreur BERTScore : %s", exc, exc_info=True)
            return BertScoreResult(f1=0.0, precision=0.0, recall=0.0, model="error")

    def partial_score(self, f1: float) -> float:
        """
        Convertit un F1 BERTScore en score normalisé [0, 1] selon les seuils.

        - F1 ≥ 0.70 → 1.0 (correct)
        - F1 ∈ [0.50, 0.70[ → interpolation linéaire dans [0, 1]
        - F1 < 0.50 → 0.0 (incorrect)
        """
        THRESHOLD_CORRECT = float(os.environ.get("BERT_SCORE_THRESHOLD_CORRECT", "0.70"))
        THRESHOLD_PARTIAL = float(os.environ.get("BERT_SCORE_THRESHOLD_PARTIAL", "0.50"))

        if f1 >= THRESHOLD_CORRECT:
            return 1.0
        if f1 >= THRESHOLD_PARTIAL:
            return (f1 - THRESHOLD_PARTIAL) / (THRESHOLD_CORRECT - THRESHOLD_PARTIAL)
        return 0.0
