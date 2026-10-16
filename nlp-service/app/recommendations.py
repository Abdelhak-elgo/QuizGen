"""
Recommandations pédagogiques par copie d'étudiant.

À partir d'une copie corrigée (questions, réponses attendues, réponses de
l'étudiant, verdict), le LLM configuré (Ollama ou API en ligne) produit un
diagnostic pour l'enseignant : points forts, lacunes, notions à retravailler
et actions concrètes. La copie est anonymisée par le backend.
"""
import json
import logging
from typing import Any, List

from app.llm_providers import complete, resolve_target
from app.models import AnswerOutcome, Recommendation, RecommendationRequest

logger = logging.getLogger(__name__)

# Nombre maximal d'éléments par liste — garde le diagnostic lisible
MAX_ITEMS_PER_LIST = 5
# Réponses d'étudiant longues tronquées dans le prompt (coût / contexte)
MAX_ANSWER_CHARS = 600

SYSTEM_PROMPT = """Tu es un enseignant-tuteur universitaire francophone bienveillant et précis.
Tu analyses la copie corrigée d'un étudiant pour conseiller son enseignant.

RÈGLES ABSOLUES :
1. Réponds UNIQUEMENT avec un objet JSON valide, sans texte avant ni après.
2. Appuie-toi exclusivement sur les réponses fournies : ne suppose rien d'autre sur l'étudiant.
3. Cite les notions précises (pas de généralités comme « réviser le cours »).
4. Rédige en français, phrases courtes, ton professionnel et constructif."""

_OUTCOME_LABELS = {
    AnswerOutcome.CORRECT: "juste",
    AnswerOutcome.PARTIAL: "partiellement juste",
    AnswerOutcome.INCORRECT: "fausse",
    AnswerOutcome.UNANSWERED: "sans réponse",
    AnswerOutcome.UNGRADED: "non corrigée automatiquement (à apprécier)",
}


def build_prompt(request: RecommendationRequest) -> str:
    """Construit le prompt d'analyse à partir de la copie corrigée."""
    lines = [
        f'Quiz : « {request.quiz_title} »',
        f"Score : {request.score:g} / {request.max_score}",
        "",
        "COPIE CORRIGÉE :",
    ]
    for i, item in enumerate(request.items, start=1):
        answer = (item.student_answer or "").strip()[:MAX_ANSWER_CHARS] or "(aucune réponse)"
        lines += [
            f"Q{i} [{item.type.value}] — réponse {_OUTCOME_LABELS[item.outcome]}",
            f"  Question : {item.question}",
            f"  Réponse attendue : {item.correct_answer}",
            f"  Réponse de l'étudiant : {answer}",
        ]
        if item.explanation:
            lines.append(f"  Explication du cours : {item.explanation}")
    lines += [
        "",
        "Réponds avec cet objet JSON :",
        "{",
        '  "summary": "2 phrases : niveau global et principal enjeu",',
        '  "strengths": ["notion maîtrisée, appuyée sur une question précise"],',
        '  "weaknesses": ["lacune ou confusion observée, avec la question concernée"],',
        '  "focus_areas": ["notion du cours à retravailler en priorité"],',
        '  "actions": ["action concrète que l\'enseignant peut proposer à l\'étudiant"]',
        "}",
        f"Au plus {MAX_ITEMS_PER_LIST} éléments par liste. Listes vides si rien à signaler.",
    ]
    return "\n".join(lines)


def _clean_list(value: Any) -> List[str]:
    if not isinstance(value, list):
        return []
    items = [str(v).strip() for v in value if str(v).strip()]
    return items[:MAX_ITEMS_PER_LIST]


def _extract_object(text: str) -> Any:
    """
    Objet JSON de la réponse. On cherche l'objet englobant ({ … }) et non un
    tableau : l'extracteur générique des questions prendrait sinon une des
    listes internes (strengths, actions…) quand le LLM ajoute du texte autour.
    """
    text = text.strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    start, end = text.find("{"), text.rfind("}")
    if start == -1 or end <= start:
        return None
    try:
        return json.loads(text[start:end + 1])
    except json.JSONDecodeError:
        return None


def parse_recommendation(text: str) -> Recommendation:
    """Extrait le diagnostic JSON de la réponse du LLM ; ValueError si inexploitable."""
    parsed = _extract_object(text)
    if not isinstance(parsed, dict) or not str(parsed.get("summary", "")).strip():
        raise ValueError("Réponse du LLM sans diagnostic exploitable")
    return Recommendation(
        summary=str(parsed["summary"]).strip(),
        strengths=_clean_list(parsed.get("strengths")),
        weaknesses=_clean_list(parsed.get("weaknesses")),
        focus_areas=_clean_list(parsed.get("focus_areas")),
        actions=_clean_list(parsed.get("actions")),
    )


def generate_recommendation(request: RecommendationRequest) -> Recommendation:
    """
    Appelle le LLM actif et renvoie le diagnostic.

    Lève LlmConfigError (configuration), httpx.HTTPError (réseau / fournisseur)
    ou ValueError (réponse inexploitable) — traduits en codes HTTP par l'API.
    """
    target = resolve_target()
    logger.info(
        "Recommandation — %s:%s, %d question(s), score %s/%d",
        target.protocol, target.model, len(request.items), request.score, request.max_score,
    )
    text = complete(build_prompt(request), SYSTEM_PROMPT)
    recommendation = parse_recommendation(text)
    return recommendation.model_copy(update={"model": target.model})
