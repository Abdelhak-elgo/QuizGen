"""
Chain-of-Thought Prompts — Génération structurée de questions pédagogiques.

Pourquoi CoT améliore la qualité :
  Wei et al. (2022) "Chain-of-Thought Prompting Elicits Reasoning in LLMs"
  démontrent que forcer le LLM à raisonner étape par étape avant de répondre
  améliore significativement la qualité sur les tâches complexes.

  Sans CoT : Mistral prend le chemin le plus court → questions sur les premiers
             mots du contexte, souvent superficielles.

  Avec CoT : Mistral identifie d'abord les concepts importants, évalue leur
             pertinence pédagogique, PUIS génère une question ciblée.

Structure du prompt CoT pour QuizGen :
  ÉTAPE 1 — ANALYSE CONCEPTUELLE
    "Quels sont les 3 concepts les plus importants de ce texte ?"
    → Force Mistral à lire tout le contexte avant d'agir

  ÉTAPE 2 — CIBLAGE PÉDAGOGIQUE
    "Quel concept mérite d'être testé à ce niveau (Bloom L{n}) ?"
    → Aligne la question sur l'objectif pédagogique

  ÉTAPE 3 — GÉNÉRATION DE LA QUESTION
    "Génère une question {type} qui teste ce concept"
    → La question est maintenant fondée sur un raisonnement explicite

  ÉTAPE 4 — AUTO-VÉRIFICATION
    "Vérifie que la réponse est dans le texte source"
    → Réduit les hallucinations avant même le quality scorer

Le JSON de sortie inclut le raisonnement intermédiaire (champ "reasoning")
qui est loggué pour traçabilité mais pas exposé à l'utilisateur final.
"""
from typing import List

from app.models import Difficulty, QuestionType

# ── Prompt système commun ─────────────────────────────────────────────────────

SYSTEM_PROMPT_COT = """Tu es un expert en ingénierie pédagogique universitaire francophone.
Tu génères des questions d'évaluation de haute qualité pour des étudiants de niveau Master.

RÈGLES ABSOLUES :
1. Réponds UNIQUEMENT en JSON valide. Aucun texte avant ni après le JSON.
2. Chaque question doit être directement répondable depuis le texte source fourni.
3. N'invente jamais d'information absente du texte.
4. Les distracteurs QCM doivent être plausibles mais clairement incorrects pour qui maîtrise le sujet.
5. Le champ "reasoning" doit montrer ta réflexion AVANT de formuler la question."""


# ── Templates Chain-of-Thought par type ──────────────────────────────────────

_COT_QCM_TEMPLATE = """Tu dois générer {nb} question(s) QCM de niveau Bloom "{bloom_level}" ({bloom_directive})

DOMAINE : {domain}
{domain_suffix}

TEXTE SOURCE :
\"\"\"
{context}
\"\"\"

CONCEPTS CLÉS IDENTIFIÉS PAR ANALYSE NLP : {keywords}

Pour CHAQUE question, suis ces étapes dans le champ "reasoning" :
  1. Identifie LE concept central du texte qui mérite d'être évalué
  2. Formule une question claire qui ne peut être répondue que si on comprend ce concept
  3. Crée 4 options : 1 correcte + 3 distracteurs basés sur des confusions réelles typiques
  4. Vérifie que la bonne réponse est EXPLICITEMENT dans le texte source

Réponds avec un tableau JSON :
[
  {{
    "reasoning": "Le concept central est X. Une confusion fréquente est Y vs Z. La bonne réponse est dans le texte à la phrase 'quote'. Les distracteurs exploitent les confusions A, B, C.",
    "type": "QCM",
    "content": "Question précise et sans ambiguïté ?",
    "options": [
      "Option correcte (dans le texte)",
      "Distracteur plausible basé sur confusion fréquente A",
      "Distracteur plausible basé sur confusion fréquente B",
      "Distracteur plausible basé sur confusion fréquente C"
    ],
    "correct_answer": "Option correcte (dans le texte)",
    "explanation": "Explication pédagogique de pourquoi c'est correct et pourquoi les autres ne le sont pas.",
    "difficulty": "{difficulty}",
    "keywords": ["concept1", "concept2"]
  }}
]"""

_COT_OUVERTE_TEMPLATE = """Tu dois générer {nb} question(s) ouverte(s) de niveau Bloom "{bloom_level}" ({bloom_directive})

DOMAINE : {domain}
{domain_suffix}

TEXTE SOURCE :
\"\"\"
{context}
\"\"\"

CONCEPTS CLÉS IDENTIFIÉS PAR ANALYSE NLP : {keywords}

Pour CHAQUE question, suis ces étapes dans le champ "reasoning" :
  1. Identifie quelle compétence cognitive le texte permet de tester (expliquer ? analyser ? évaluer ?)
  2. Formule une question ouverte qui encourage la réflexion au-delà du copier-coller
  3. Rédige une réponse modèle complète qui couvre les points essentiels attendus
  4. Vérifie que tous les éléments de la réponse modèle sont dans le texte source

Réponds avec un tableau JSON :
[
  {{
    "reasoning": "Le texte permet de tester la compétence X. Une question de niveau {bloom_level} demanderait Y. La réponse modèle doit couvrir les points P1, P2, P3 car ils sont dans le texte.",
    "type": "OUVERTE",
    "content": "Question ouverte engageant la réflexion critique ?",
    "options": null,
    "correct_answer": "Réponse modèle complète couvrant les points clés attendus. Les éléments de réponse doivent être tirés du texte source.",
    "explanation": "Points clés que la réponse doit aborder : (1)... (2)... (3)... Critères de correction : ...",
    "difficulty": "{difficulty}",
    "keywords": ["concept1", "concept2"]
  }}
]"""

_COT_EXERCICE_TEMPLATE = """Tu dois générer {nb} exercice(s) pratique(s) de niveau Bloom "{bloom_level}" ({bloom_directive})

DOMAINE : {domain}
{domain_suffix}

TEXTE SOURCE :
\"\"\"
{context}
\"\"\"

CONCEPTS CLÉS IDENTIFIÉS PAR ANALYSE NLP : {keywords}

Pour CHAQUE exercice, suis ces étapes dans le champ "reasoning" :
  1. Identifie quelle procédure, méthode ou compétence pratique le texte décrit
  2. Conçois un scénario concret et réaliste qui oblige à APPLIQUER cette méthode
  3. Rédige la solution complète étape par étape
  4. Vérifie que toutes les étapes de la solution s'appuient sur la méthode décrite dans le texte

Réponds avec un tableau JSON :
[
  {{
    "reasoning": "La méthode décrite dans le texte est X en Y étapes. Un exercice réaliste serait de demander Z. La solution utilise les étapes E1, E2, E3 du texte.",
    "type": "EXERCICE",
    "content": "Énoncé concret et précis de l'exercice. Données : ... Demande : ...",
    "options": null,
    "correct_answer": "Solution complète étape par étape : Étape 1 : ... Étape 2 : ... Résultat : ...",
    "explanation": "Concepts mobilisés : ... Points de vigilance : ... Erreurs fréquentes à éviter : ...",
    "difficulty": "{difficulty}",
    "keywords": ["concept1", "concept2"]
  }}
]"""

_COT_TEMPLATES = {
    QuestionType.QCM:      _COT_QCM_TEMPLATE,
    QuestionType.OUVERTE:  _COT_OUVERTE_TEMPLATE,
    QuestionType.EXERCICE: _COT_EXERCICE_TEMPLATE,
}

# Labels lisibles pour les niveaux Bloom dans les prompts
_BLOOM_LABELS = {
    "MEMORISATION":  "Mémorisation — rappel de faits et définitions",
    "COMPREHENSION": "Compréhension — expliquer et interpréter",
    "APPLICATION":   "Application — utiliser dans un cas concret",
    "ANALYSE":       "Analyse — décomposer et trouver des relations",
    "EVALUATION":    "Évaluation — juger et justifier",
    "CREATION":      "Création — concevoir et synthétiser",
}


def build_cot_prompt(
    question_type: QuestionType,
    context: str,
    keywords: List[str],
    nb: int,
    difficulty: Difficulty,
    bloom_level: str = "COMPREHENSION",
    bloom_directive: str = "Demander d'expliquer le concept dans ses propres mots.",
    domain: str = "general",
    domain_suffix: str = "",
) -> str:
    """
    Construit un prompt Chain-of-Thought structuré pour Mistral.

    Args:
        question_type:   Type de question (QCM/OUVERTE/EXERCICE).
        context:         Chunk de texte sémantiquement cohérent (~200-400 mots).
        keywords:        Concepts clés extraits par KeyBERT + SpaCy.
        nb:              Nombre de questions à générer (recommandé : 1-2 par chunk).
        difficulty:      Niveau de difficulté.
        bloom_level:     Niveau Bloom détecté (ex: "ANALYSE").
        bloom_directive: Instruction pédagogique spécifique au niveau Bloom.
        domain:          Domaine détecté du document.
        domain_suffix:   Instructions supplémentaires spécifiques au domaine.

    Returns:
        Prompt formaté prêt à envoyer à Ollama.
    """
    template = _COT_TEMPLATES.get(question_type, _COT_OUVERTE_TEMPLATE)
    bloom_label = _BLOOM_LABELS.get(bloom_level, bloom_level)

    # Limiter le contexte : ~2500 tokens pour laisser de la place au raisonnement CoT
    context_truncated = _smart_truncate(context, max_words=350)

    keywords_str = (
        ", ".join(f'"{k}"' for k in keywords[:8])
        if keywords
        else '"les concepts principaux du texte"'
    )

    return template.format(
        nb=nb,
        bloom_level=bloom_label,
        bloom_directive=bloom_directive,
        domain=domain.upper() if domain != "general" else "GÉNÉRAL",
        domain_suffix=domain_suffix,
        context=context_truncated,
        keywords=keywords_str,
        difficulty=difficulty.value,
    )


def _smart_truncate(text: str, max_words: int) -> str:
    """
    Tronque le texte à max_words mots en coupant à une frontière de phrase.
    Préserve la cohérence du contexte (pas de coupure en milieu de phrase).
    """
    words = text.split()
    if len(words) <= max_words:
        return text

    # Prendre les premiers max_words mots
    truncated = " ".join(words[:max_words])

    # Reculer jusqu'à la dernière ponctuation forte
    last_punct = max(
        truncated.rfind("."),
        truncated.rfind("!"),
        truncated.rfind("?"),
    )
    if last_punct > max_words * 3:  # Au moins 75% du texte conservé
        return truncated[:last_punct + 1]

    return truncated + "..."
