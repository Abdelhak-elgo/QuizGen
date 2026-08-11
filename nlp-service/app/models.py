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
