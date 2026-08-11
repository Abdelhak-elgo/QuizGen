"""
Client Ollama/Mistral pour la génération de questions pédagogiques.

Gestion des templates de prompts par type de question (QCM, OUVERTE, EXERCICE),
parsing JSON avec retry automatique (jusqu'à LLM_MAX_RETRIES tentatives).
"""
import json
import logging
import re
import time
from typing import Any, Dict, List, Optional

import httpx

from app.config import get_settings
from app.models import Difficulty, GeneratedQuestion, QuestionType

logger = logging.getLogger(__name__)
settings = get_settings()

# ── Templates de prompts ──────────────────────────────────────────────────────

_SYSTEM_PROMPT = """Tu es un expert en pédagogie universitaire francophone.
Tu génères des questions pédagogiques précises, claires et adaptées au niveau Master.
Tu réponds UNIQUEMENT en JSON valide, sans texte avant ni après le JSON.
Ne génère JAMAIS de commentaires, d'explications ou de texte en dehors du JSON."""

_QCM_TEMPLATE = """À partir du texte suivant, génère {nb} question(s) QCM de niveau {difficulty}.
Chaque QCM doit avoir EXACTEMENT 4 options et UNE seule bonne réponse.

Texte source :
\"\"\"
{context}
\"\"\"

Concepts clés à couvrir : {keywords}

Réponds avec un tableau JSON de la forme :
[
  {{
    "type": "QCM",
    "content": "Question claire et précise ?",
    "options": ["Option A", "Option B", "Option C", "Option D"],
    "correct_answer": "Option A",
    "explanation": "Explication courte de la bonne réponse.",
    "difficulty": "{difficulty}",
    "keywords": ["concept1", "concept2"]
  }}
]"""

_OUVERTE_TEMPLATE = """À partir du texte suivant, génère {nb} question(s) ouverte(s) de niveau {difficulty}.
Les questions doivent encourager la réflexion et la synthèse.

Texte source :
\"\"\"
{context}
\"\"\"

Concepts clés à couvrir : {keywords}

Réponds avec un tableau JSON de la forme :
[
  {{
    "type": "OUVERTE",
    "content": "Question ouverte invitant à la réflexion ?",
    "options": null,
    "correct_answer": "Réponse modèle complète attendue de l'étudiant.",
    "explanation": "Points clés que la réponse doit aborder.",
    "difficulty": "{difficulty}",
    "keywords": ["concept1", "concept2"]
  }}
]"""

_EXERCICE_TEMPLATE = """À partir du texte suivant, génère {nb} exercice(s) pratique(s) de niveau {difficulty}.
Les exercices doivent être concrets et applicables.

Texte source :
\"\"\"
{context}
\"\"\"

Concepts clés à couvrir : {keywords}

Réponds avec un tableau JSON de la forme :
[
  {{
    "type": "EXERCICE",
    "content": "Énoncé de l'exercice pratique.",
    "options": null,
    "correct_answer": "Solution complète étape par étape.",
    "explanation": "Démarche et concepts mobilisés.",
    "difficulty": "{difficulty}",
    "keywords": ["concept1", "concept2"]
  }}
]"""

_TEMPLATES = {
    QuestionType.QCM: _QCM_TEMPLATE,
    QuestionType.OUVERTE: _OUVERTE_TEMPLATE,
    QuestionType.EXERCICE: _EXERCICE_TEMPLATE,
}

# ── Extraction JSON robuste ───────────────────────────────────────────────────

def _extract_json_from_text(text: str) -> Optional[Any]:
    """
    Tente d'extraire un JSON valide du texte retourné par Mistral,
    même si le modèle ajoute du texte parasite avant/après.
    """
    # Chercher un tableau JSON [ ... ] ou objet { ... }
    for pattern in (r"\[.*\]", r"\{.*\}"):
        match = re.search(pattern, text, re.DOTALL)
        if match:
            try:
                return json.loads(match.group())
            except json.JSONDecodeError:
                continue
    return None


def _build_prompt(
    question_type: QuestionType,
    context: str,
    keywords: List[str],
    nb: int,
    difficulty: Difficulty,
) -> str:
    template = _TEMPLATES[question_type]
    return template.format(
        nb=nb,
        difficulty=difficulty.value,
        context=context[:3000],  # Limiter le contexte pour éviter de dépasser la fenêtre du LLM
        keywords=", ".join(keywords[:10]) if keywords else "les concepts principaux du texte",
    )


def _parse_questions(raw: Any, expected_type: QuestionType, difficulty: Difficulty) -> List[GeneratedQuestion]:
    """Convertit la sortie brute du LLM en liste de GeneratedQuestion validées."""
    questions: List[GeneratedQuestion] = []

    if isinstance(raw, dict):
        raw = [raw]
    if not isinstance(raw, list):
        logger.warning("Format LLM inattendu : %s", type(raw))
        return []

    for item in raw:
        if not isinstance(item, dict):
            continue
        try:
            q = GeneratedQuestion(
                type=item.get("type", expected_type.value),
                content=item.get("content", "").strip(),
                options=item.get("options"),
                correct_answer=item.get("correct_answer", "").strip(),
                explanation=item.get("explanation"),
                difficulty=item.get("difficulty", difficulty.value),
                keywords=item.get("keywords", []),
            )
            if q.content and q.correct_answer:
                questions.append(q)
        except Exception as exc:
            logger.debug("Question invalide ignorée : %s", exc)

    return questions


# ── Client Ollama ─────────────────────────────────────────────────────────────

def generate_questions(
    context: str,
    keywords: List[str],
    question_type: QuestionType,
    nb: int,
    difficulty: Difficulty,
    *,
    max_retries: int | None = None,
) -> List[GeneratedQuestion]:
    """
    Génère des questions via Mistral/Ollama avec retry automatique.

    Args:
        context:       Texte source extrait du PDF.
        keywords:      Concepts clés identifiés par le pipeline NLP.
        question_type: Type de question (QCM, OUVERTE, EXERCICE).
        nb:            Nombre de questions à générer.
        difficulty:    Niveau de difficulté.
        max_retries:   Nombre maximal de tentatives (défaut depuis Settings).

    Returns:
        Liste de GeneratedQuestion validées (peut être vide si le LLM échoue).
    """
    max_retries = max_retries or settings.llm_max_retries
    prompt = _build_prompt(question_type, context, keywords, nb, difficulty)

    payload = {
        "model": settings.ollama_model,
        "prompt": prompt,
        "system": _SYSTEM_PROMPT,
        "stream": False,
        "options": {
            "temperature": settings.llm_temperature,
            "num_predict": 2048,
        },
    }

    for attempt in range(1, max_retries + 1):
        try:
            logger.info(
                "Ollama — tentative %d/%d (type=%s, nb=%d)",
                attempt, max_retries, question_type.value, nb,
            )
            with httpx.Client(timeout=settings.ollama_timeout) as client:
                resp = client.post(
                    f"{settings.ollama_url}/api/generate",
                    json=payload,
                )
                resp.raise_for_status()

            response_text: str = resp.json().get("response", "")
            logger.debug("Réponse brute LLM (trunc.) : %s...", response_text[:300])

            parsed = _extract_json_from_text(response_text)
            if parsed is None:
                logger.warning("JSON introuvable dans la réponse — tentative %d", attempt)
                time.sleep(1)
                continue

            questions = _parse_questions(parsed, question_type, difficulty)
            if questions:
                logger.info(
                    "✅ %d question(s) générée(s) (type=%s, tentative %d)",
                    len(questions), question_type.value, attempt,
                )
                return questions

            logger.warning("Aucune question valide extraite — tentative %d", attempt)
            time.sleep(1)

        except httpx.TimeoutException:
            logger.error("Timeout Ollama — tentative %d/%d", attempt, max_retries)
            time.sleep(2)
        except httpx.HTTPStatusError as exc:
            logger.error("Erreur HTTP Ollama %s — tentative %d", exc.response.status_code, attempt)
            time.sleep(1)
        except Exception as exc:
            logger.error("Erreur inattendue Ollama : %s", exc, exc_info=True)
            time.sleep(1)

    logger.error("Génération échouée après %d tentatives (type=%s).", max_retries, question_type.value)
    return []


def ping_ollama() -> bool:
    """Vérifie qu'Ollama est accessible et que le modèle est disponible."""
    try:
        with httpx.Client(timeout=5.0) as client:
            resp = client.get(f"{settings.ollama_url}/api/tags")
            if resp.status_code != 200:
                return False
            models = [m.get("name", "") for m in resp.json().get("models", [])]
            return any(settings.ollama_model in m for m in models)
    except Exception:
        return False
