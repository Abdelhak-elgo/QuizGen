"""
Client Ollama/Mistral — Génération CoT de questions pédagogiques.

Architecture :
  build_cot_prompt (chain_of_thought.py)
    → Ollama /api/generate (Mistral)
      → _extract_json_from_text (parsing robuste)
        → _parse_questions (validation + strip du champ "reasoning")
          → List[GeneratedQuestion]

Le champ "reasoning" produit par le CoT est loggué en DEBUG pour traçabilité
mais n'est pas exposé dans la réponse finale à l'utilisateur.
"""
import json
import logging
import re
import time
from typing import Any, Dict, List, Optional

import httpx

from app.chain_of_thought import SYSTEM_PROMPT_COT, build_cot_prompt
from app.config import get_settings
from app.models import Difficulty, GeneratedQuestion, QuestionType

logger = logging.getLogger(__name__)
settings = get_settings()


# ── Extraction JSON robuste ───────────────────────────────────────────────────

def _extract_json_from_text(text: str) -> Optional[Any]:
    """
    Extrait un JSON valide du texte retourné par Mistral.

    Stratégies tentées dans l'ordre :
      1. Parse direct du texte (cas idéal)
      2. Extraction du premier tableau [...] par regex + DOTALL
      3. Extraction du premier objet {...} par regex + DOTALL
      4. Recherche par accolade/crochet équilibré (cas JSON imbriqué avec texte parasite)
    """
    text = text.strip()

    # Stratégie 1 : parse direct
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # Stratégie 2 : extraire un tableau JSON
    match = re.search(r"\[.*\]", text, re.DOTALL)
    if match:
        try:
            return json.loads(match.group())
        except json.JSONDecodeError:
            pass

    # Stratégie 3 : extraire un objet JSON
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if match:
        try:
            result = json.loads(match.group())
            return [result] if isinstance(result, dict) else result
        except json.JSONDecodeError:
            pass

    # Stratégie 4 : trouver le JSON par équilibrage de délimiteurs
    for start_char, end_char in [("[", "]"), ("{", "}")]:
        start_idx = text.find(start_char)
        if start_idx == -1:
            continue
        depth = 0
        for i, ch in enumerate(text[start_idx:], start=start_idx):
            if ch == start_char:
                depth += 1
            elif ch == end_char:
                depth -= 1
                if depth == 0:
                    candidate = text[start_idx:i + 1]
                    try:
                        result = json.loads(candidate)
                        return [result] if isinstance(result, dict) else result
                    except json.JSONDecodeError:
                        break

    logger.debug("Impossible d'extraire du JSON depuis : %.200s", text)
    return None


def _parse_questions(
    raw: Any,
    expected_type: QuestionType,
    difficulty: Difficulty,
) -> List[GeneratedQuestion]:
    """
    Convertit la sortie brute du LLM en liste de GeneratedQuestion validées.

    Le champ "reasoning" (Chain-of-Thought) est extrait et loggué
    mais pas conservé dans le modèle final.
    """
    questions: List[GeneratedQuestion] = []

    if isinstance(raw, dict):
        raw = [raw]
    if not isinstance(raw, list):
        logger.warning("Format LLM inattendu : %s", type(raw))
        return []

    for item in raw:
        if not isinstance(item, dict):
            continue

        # Extraire et logger le raisonnement CoT (pour traçabilité)
        reasoning = item.pop("reasoning", None)
        if reasoning:
            logger.debug("CoT reasoning : %.300s", reasoning)

        try:
            q = GeneratedQuestion(
                type=item.get("type", expected_type.value),
                content=(item.get("content") or "").strip(),
                options=item.get("options"),
                correct_answer=(item.get("correct_answer") or "").strip(),
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
    bloom_level: str = "COMPREHENSION",
    bloom_directive: str = "Demander d'expliquer le concept dans ses propres mots.",
    domain: str = "general",
    domain_suffix: str = "",
    max_retries: int | None = None,
) -> List[GeneratedQuestion]:
    """
    Génère des questions via Mistral/Ollama avec prompt Chain-of-Thought.

    Args:
        context:         Chunk de texte sémantiquement cohérent.
        keywords:        Concepts clés extraits par KeyBERT + SpaCy.
        question_type:   Type de question (QCM, OUVERTE, EXERCICE).
        nb:              Nombre de questions à générer (recommandé 1-2 par chunk).
        difficulty:      Niveau de difficulté.
        bloom_level:     Niveau Bloom détecté pour ce chunk (ex: "ANALYSE").
        bloom_directive: Instruction pédagogique injectée dans le prompt.
        domain:          Domaine académique détecté (informatique, droit, etc.)
        domain_suffix:   Suffixe de prompt spécifique au domaine.
        max_retries:     Tentatives max (défaut depuis Settings).

    Returns:
        Liste de GeneratedQuestion (peut être vide si le LLM échoue).
    """
    max_retries = max_retries or settings.llm_max_retries

    # Construire le prompt CoT structuré
    prompt = build_cot_prompt(
        question_type=question_type,
        context=context,
        keywords=keywords,
        nb=nb,
        difficulty=difficulty,
        bloom_level=bloom_level,
        bloom_directive=bloom_directive,
        domain=domain,
        domain_suffix=domain_suffix,
    )

    payload: Dict[str, Any] = {
        "model": settings.ollama_model,
        "prompt": prompt,
        "system": SYSTEM_PROMPT_COT,
        "stream": False,
        "options": {
            "temperature": settings.llm_temperature,
            "num_predict": 2048,
            # Pénaliser la répétition — aide à diversifier les distracteurs QCM
            "repeat_penalty": 1.1,
        },
    }

    for attempt in range(1, max_retries + 1):
        try:
            logger.info(
                "Ollama CoT — tentative %d/%d (type=%s, bloom=%s, nb=%d, domain=%s)",
                attempt, max_retries,
                question_type.value, bloom_level, nb, domain,
            )
            with httpx.Client(timeout=settings.ollama_timeout) as client:
                resp = client.post(
                    f"{settings.ollama_url}/api/generate",
                    json=payload,
                )
                resp.raise_for_status()

            response_text: str = resp.json().get("response", "")
            logger.debug("Réponse LLM brute (trunc.) : %.300s", response_text)

            parsed = _extract_json_from_text(response_text)
            if parsed is None:
                logger.warning("JSON introuvable (tentative %d) — réponse : %.200s", attempt, response_text)
                time.sleep(1)
                continue

            questions = _parse_questions(parsed, question_type, difficulty)
            if questions:
                logger.info(
                    "✅ %d question(s) générée(s) [type=%s, bloom=%s, tentative=%d]",
                    len(questions), question_type.value, bloom_level, attempt,
                )
                return questions

            logger.warning("Aucune question valide extraite (tentative %d)", attempt)
            time.sleep(1)

        except httpx.TimeoutException:
            logger.error("Timeout Ollama (tentative %d/%d)", attempt, max_retries)
            time.sleep(2)
        except httpx.HTTPStatusError as exc:
            logger.error("Erreur HTTP Ollama %s (tentative %d)", exc.response.status_code, attempt)
            time.sleep(1)
        except Exception as exc:
            logger.error("Erreur inattendue Ollama : %s", exc, exc_info=True)
            time.sleep(1)

    logger.error(
        "Génération échouée après %d tentatives [type=%s, bloom=%s].",
        max_retries, question_type.value, bloom_level,
    )
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
