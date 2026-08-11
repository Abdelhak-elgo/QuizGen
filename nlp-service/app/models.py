"""Modèles Pydantic partagés entre les modules."""
from enum import Enum
from typing import List, Optional
from pydantic import BaseModel, Field


class QuestionType(str, Enum):
    QCM = "QCM"
    OUVERTE = "OUVERTE"
    EXERCICE = "EXERCICE"


class Difficulty(str, Enum):
    FACILE = "FACILE"
    MOYEN = "MOYEN"
    DIFFICILE = "DIFFICILE"


class GeneratedQuestion(BaseModel):
    type: QuestionType
    content: str
    options: Optional[List[str]] = None  # Exactement 4 éléments pour QCM
    correct_answer: str
    explanation: Optional[str] = None
    difficulty: Difficulty = Difficulty.MOYEN
    keywords: List[str] = Field(default_factory=list)
    position: int = 0


class GenerateRequest(BaseModel):
    document_id: str
    bucket_name: str = "documents"
    object_key: str
    nb_questions: int = Field(ge=3, le=25, default=10)
    question_types: List[QuestionType] = Field(
        default=[QuestionType.QCM, QuestionType.OUVERTE]
    )
    difficulty: Difficulty = Difficulty.MOYEN


class GenerateResponse(BaseModel):
    task_id: str
    status: str
    message: str


class TaskStatus(BaseModel):
    task_id: str
    status: str  # PENDING | STARTED | PROGRESS | SUCCESS | FAILURE
    progress: Optional[int] = None  # 0–100
    result: Optional[List[GeneratedQuestion]] = None
    error: Optional[str] = None


class TextSection(BaseModel):
    """Section de texte extraite d'un PDF."""
    page: int
    text: str
    tokens: int


# ── BERTScore ─────────────────────────────────────────────────────────────────

class ScoreRequest(BaseModel):
    """Requête de correction sémantique d'une réponse ouverte."""
    candidate: str = Field(..., description="Réponse de l'étudiant")
    reference: str = Field(..., description="Réponse de référence (correctAnswer)")
    question_id: Optional[str] = Field(None, description="ID de la question (pour logging)")


class ScoreResponse(BaseModel):
    """Résultat BERTScore pour une réponse ouverte."""
    question_id: Optional[str] = None
    f1: float = Field(..., ge=0.0, le=1.0, description="Score F1 BERTScore")
    precision: float = Field(..., ge=0.0, le=1.0)
    recall: float = Field(..., ge=0.0, le=1.0)
    partial_score: float = Field(
        ..., ge=0.0, le=1.0,
        description="Score normalisé [0,1] selon seuils (≥0.70=1.0, [0.50,0.70[=partiel, <0.50=0)"
    )
    model: str
    label: str = Field(
        ...,
        description="Étiquette de correction : CORRECT | PARTIEL | INCORRECT"
    )


class BatchScoreRequest(BaseModel):
    """Correction de plusieurs réponses ouvertes en une seule requête."""
    items: List[ScoreRequest]


class BatchScoreResponse(BaseModel):
    results: List[ScoreResponse]
    total_items: int
    available: bool = Field(..., description="True si bert-score est installé")
