"""
Bloom Classifier — Taxonomie de Bloom pour la génération pédagogique.

La Taxonomie de Bloom (1956, révisée Anderson & Krathwohl 2001) organise
les objectifs d'apprentissage en 6 niveaux cognitifs croissants :

  L1 — MÉMORISATION   : reconnaître, rappeler des faits
  L2 — COMPRÉHENSION  : expliquer, interpréter, résumer
  L3 — APPLICATION    : utiliser, appliquer, résoudre
  L4 — ANALYSE        : différencier, organiser, décomposer
  L5 — ÉVALUATION     : juger, critiquer, défendre
  L6 — CRÉATION       : concevoir, produire, planifier

Ce module :
  1. Détecte le niveau Bloom dominant d'un passage de texte
     (via marqueurs linguistiques + verbes d'action)
  2. Mappe le niveau → type de question adapté (QCM/OUVERTE/EXERCICE)
  3. Suggère le niveau de difficulté et les directives de génération
  4. Détecte le domaine du document pour adapter les prompts
"""
import logging
import re
from dataclasses import dataclass
from enum import Enum
from typing import Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)


# ── Taxonomie ──────────────────────────────────────────────────────────────────

class BloomLevel(Enum):
    MEMORISATION  = 1   # Remember
    COMPREHENSION = 2   # Understand
    APPLICATION   = 3   # Apply
    ANALYSE       = 4   # Analyze
    EVALUATION    = 5   # Evaluate
    CREATION      = 6   # Create


@dataclass
class BloomClassification:
    level: BloomLevel
    confidence: float           # [0, 1]
    detected_markers: List[str] # Marqueurs qui ont déclenché la classification
    question_type_hint: str     # "QCM" | "OUVERTE" | "EXERCICE"
    difficulty_hint: str        # "FACILE" | "MOYEN" | "DIFFICILE"
    prompt_directive: str       # Instruction à injecter dans le prompt LLM


# ── Marqueurs linguistiques par niveau ────────────────────────────────────────

# Verbes et marqueurs caractéristiques de chaque niveau Bloom
# (formes lemmatisées + formes fléchies fréquentes en français)
_BLOOM_MARKERS: Dict[BloomLevel, List[str]] = {
    BloomLevel.MEMORISATION: [
        # Verbes : définir, nommer, lister, identifier, rappeler, citer
        "définir", "définit", "définition", "défini",
        "nommer", "liste", "lister", "identifier", "identifier",
        "rappeler", "citer", "cité", "énumérer", "reconnaître",
        "mémoriser", "retenir", "se souvenir", "répéter",
        # Marqueurs structurels (textes factuels)
        "est défini comme", "se définit par", "on appelle",
        "par définition", "désigne", "désigné par",
        "est caractérisé par", "correspond à", "signifie",
    ],
    BloomLevel.COMPREHENSION: [
        # Verbes : expliquer, résumer, interpréter, paraphraser, classifier
        "expliquer", "explique", "explication", "comprendre",
        "résumer", "résumé", "synthétiser", "synthèse",
        "interpréter", "interprétation", "paraphraser",
        "illustrer", "exemple", "démontrer", "comparer",
        "contraster", "distinguer", "différence entre",
        "en d'autres termes", "c'est-à-dire", "autrement dit",
        "ce qui signifie", "en résumé", "ainsi", "donc",
    ],
    BloomLevel.APPLICATION: [
        # Verbes : appliquer, utiliser, calculer, résoudre, implémenter
        "appliquer", "applique", "application", "utiliser",
        "calculer", "calcul", "résoudre", "résolution",
        "implémenter", "implémentation", "réaliser", "exécuter",
        "employer", "mettre en œuvre", "procédure", "algorithme",
        "étapes", "démarche", "méthode pour", "comment faire",
        "à partir de", "en utilisant", "grâce à",
    ],
    BloomLevel.ANALYSE: [
        # Verbes : analyser, décomposer, distinguer, comparer, catégoriser
        "analyser", "analyse", "décomposer", "décomposition",
        "distinguer", "différencier", "identifier les composantes",
        "relation entre", "lien entre", "cause", "effet",
        "conséquence", "impact", "influence", "facteur",
        "structure de", "organisation de", "hiérarchie",
        "d'une part", "d'autre part", "en revanche", "or",
        "parce que", "car", "en raison de", "conduit à",
    ],
    BloomLevel.EVALUATION: [
        # Verbes : évaluer, juger, critiquer, justifier, défendre
        "évaluer", "évaluation", "juger", "jugement",
        "critiquer", "critique", "avantage", "inconvénient",
        "justifier", "justification", "défendre", "argumenter",
        "choisir", "sélectionner", "recommander", "privilégier",
        "meilleur", "moins bon", "optimal", "pertinent",
        "valide", "invalide", "approprié", "inapproprié",
        "qualité", "limite", "biais", "fiabilité",
    ],
    BloomLevel.CREATION: [
        # Verbes : concevoir, créer, produire, planifier, concevoir
        "concevoir", "conception", "créer", "créatif",
        "produire", "production", "planifier", "plan",
        "proposer", "proposition", "élaborer", "développer",
        "concevoir", "inventer", "imaginer", "construire",
        "modéliser", "formuler", "synthétiser", "intégrer",
        "nouveau", "original", "innovant", "solution",
    ],
}

# Mapping niveau Bloom → type de question et difficulté
_BLOOM_TO_QUESTION: Dict[BloomLevel, Tuple[str, str, str]] = {
    # niveau → (question_type, difficulty, directive_courte)
    BloomLevel.MEMORISATION:  ("QCM",      "FACILE",   "Tester la mémorisation de faits, définitions ou dates clés."),
    BloomLevel.COMPREHENSION: ("OUVERTE",  "MOYEN",    "Demander d'expliquer ou de reformuler le concept dans ses propres mots."),
    BloomLevel.APPLICATION:   ("EXERCICE", "MOYEN",    "Proposer un problème concret à résoudre en appliquant la méthode décrite."),
    BloomLevel.ANALYSE:       ("OUVERTE",  "DIFFICILE","Demander d'analyser les relations de cause-effet ou de comparer deux éléments."),
    BloomLevel.EVALUATION:    ("OUVERTE",  "DIFFICILE","Demander de justifier un choix ou d'évaluer les limites d'une approche."),
    BloomLevel.CREATION:      ("EXERCICE", "DIFFICILE","Demander de concevoir une solution, un modèle ou un plan d'action."),
}


# ── Détection du domaine ──────────────────────────────────────────────────────

_DOMAIN_VOCABULARIES: Dict[str, List[str]] = {
    "informatique": [
        "algorithme", "programme", "code", "langage", "compilateur",
        "fonction", "objet", "classe", "héritage", "polymorphisme",
        "base de données", "sql", "réseau", "protocole", "api",
        "complexité", "récursivité", "tri", "graphe", "arbre",
        "machine learning", "réseau de neurones", "gradient",
    ],
    "mathematiques": [
        "théorème", "démonstration", "preuve", "lemme", "corollaire",
        "intégrale", "dérivée", "limite", "continuité", "convergence",
        "matrice", "vecteur", "espace vectoriel", "groupe", "anneau",
        "probabilité", "variable aléatoire", "espérance", "variance",
    ],
    "biologie": [
        "cellule", "adn", "arn", "protéine", "enzyme", "organelle",
        "métabolisme", "photosynthèse", "respiration", "mitose", "méiose",
        "écosystème", "espèce", "gène", "chromosome", "évolution",
        "membrane", "noyau", "ribosomes", "mitochondrie",
    ],
    "chimie": [
        "molécule", "atome", "liaison", "réaction", "équation",
        "oxydation", "réduction", "acide", "base", "ph",
        "électrolyse", "catalyseur", "enthalpie", "entropie",
        "orbital", "valence", "isomère", "polymère",
    ],
    "droit": [
        "loi", "article", "contrat", "obligation", "responsabilité",
        "jurisprudence", "tribunal", "sanction", "peine", "infraction",
        "liberté", "droit fondamental", "constitution", "décret",
        "code civil", "code pénal", "juridiction", "appel", "recours",
    ],
    "economie": [
        "marché", "offre", "demande", "prix", "équilibre",
        "inflation", "chômage", "pib", "croissance", "monnaie",
        "banque", "taux", "crédit", "investissement", "productivité",
        "monopole", "concurrence", "fiscalité", "budget",
    ],
    "histoire": [
        "révolution", "guerre", "traité", "empire", "siècle",
        "mouvement", "réforme", "colonisation", "indépendance",
        "civilisation", "monarchie", "république", "démocratie",
        "industrialisation", "mondialisation",
    ],
    "physique": [
        "force", "énergie", "vitesse", "accélération", "masse",
        "champ électrique", "champ magnétique", "onde", "fréquence",
        "photon", "quantum", "relativité", "thermodynamique",
        "entropie", "travail", "puissance", "circuit",
    ],
}

_DOMAIN_PROMPT_SUFFIXES: Dict[str, str] = {
    "informatique": (
        "Le document traite d'informatique. "
        "Utilise une terminologie technique précise. "
        "Pour les QCM, inclure des distracteurs plausibles liés à des confusions courantes en programmation. "
        "Pour les exercices, préférer des problèmes algorithmiques ou de conception."
    ),
    "mathematiques": (
        "Le document traite de mathématiques. "
        "Les énoncés doivent être rigoureux et précis. "
        "Pour les exercices, inclure un énoncé numérique ou symbolique concret à résoudre. "
        "Éviter les QCM sur des démonstrations — préférer OUVERTE ou EXERCICE."
    ),
    "biologie": (
        "Le document traite de biologie. "
        "Ancrer les questions dans des processus biologiques réels et mesurables. "
        "Pour les QCM, les distracteurs doivent être des termes biologiques proches mais distincts."
    ),
    "chimie": (
        "Le document traite de chimie. "
        "Les questions doivent référencer des réactions, molécules ou principes concrets. "
        "Pour les exercices, préférer des calculs de stœchiométrie ou de concentration."
    ),
    "droit": (
        "Le document traite de droit. "
        "Les questions doivent citer les textes de loi pertinents quand c'est possible. "
        "Préférer les questions OUVERTE demandant la qualification juridique d'une situation."
    ),
    "economie": (
        "Le document traite d'économie. "
        "Ancrer les questions dans des concepts économiques précis (offre/demande, équilibre, etc.). "
        "Pour les exercices, inclure des données chiffrées fictives réalistes à analyser."
    ),
    "histoire": (
        "Le document traite d'histoire. "
        "Les questions doivent référencer des dates, acteurs ou événements précis. "
        "Préférer les questions OUVERTE qui demandent de contextualiser ou d'analyser les causes."
    ),
    "physique": (
        "Le document traite de physique. "
        "Les exercices doivent inclure des données numériques et demander un calcul. "
        "Pour les QCM, les distracteurs doivent être des ordres de grandeur plausibles."
    ),
    "general": (
        "Génère des questions claires et factuellement ancrées dans le texte source. "
        "Les distracteurs des QCM doivent être plausibles mais clairement incorrects."
    ),
}


# ── Fonctions principales ─────────────────────────────────────────────────────

def detect_domain(text: str) -> str:
    """
    Détecte le domaine académique du texte par analyse de vocabulaire TF pondéré.

    Retourne le domaine avec le plus de termes détectés, ou "general" si ambigu.
    """
    text_lower = text.lower()
    scores: Dict[str, int] = {}

    for domain, vocab in _DOMAIN_VOCABULARIES.items():
        score = sum(1 for term in vocab if term in text_lower)
        if score > 0:
            scores[domain] = score

    if not scores:
        return "general"

    # Domaine dominant
    best_domain = max(scores, key=lambda d: scores[d])
    best_score = scores[best_domain]

    # Vérifier que c'est significatif (>= 3 termes) et dominant (2× le suivant)
    sorted_scores = sorted(scores.values(), reverse=True)
    if best_score < 3:
        return "general"
    if len(sorted_scores) > 1 and best_score < 2 * sorted_scores[1]:
        # Ambiguïté — retourner "general" pour un prompt neutre
        return "general"

    logger.debug("Domaine détecté : %s (score=%d)", best_domain, best_score)
    return best_domain


def classify_bloom_level(text: str) -> BloomClassification:
    """
    Classe un passage de texte selon la Taxonomie de Bloom.

    Stratégie de scoring :
      - Chaque marqueur détecté dans le texte ajoute +1 au score du niveau
      - Les marqueurs en début de phrase (position syntaxique forte) valent +0.5
      - Le niveau avec le score le plus élevé gagne

    En cas d'égalité, préférer le niveau le plus bas (L1 < L2 < ... < L6)
    car les textes académiques commencent généralement par définir avant d'analyser.
    """
    text_lower = text.lower()
    scores: Dict[BloomLevel, float] = {level: 0.0 for level in BloomLevel}
    detected_markers: List[str] = []

    for level, markers in _BLOOM_MARKERS.items():
        for marker in markers:
            if marker in text_lower:
                scores[level] += 1.0
                detected_markers.append(marker)
                # Bonus si le marqueur est en début de phrase
                if re.search(r"(?:^|\.\s+)" + re.escape(marker), text_lower):
                    scores[level] += 0.5

    # Niveau dominant
    max_score = max(scores.values())
    if max_score == 0.0:
        # Aucun marqueur → défaut L2 (compréhension) car c'est le plus courant
        dominant = BloomLevel.COMPREHENSION
        confidence = 0.3
    else:
        # En cas d'égalité, prendre le niveau le plus bas
        candidates = [l for l, s in scores.items() if s == max_score]
        dominant = min(candidates, key=lambda l: l.value)

        # Confiance = score dominant / somme des scores (si > 0)
        total = sum(scores.values())
        confidence = min(1.0, max_score / total) if total > 0 else 0.5

    q_type, difficulty, directive = _BLOOM_TO_QUESTION[dominant]

    return BloomClassification(
        level=dominant,
        confidence=round(confidence, 3),
        detected_markers=list(set(detected_markers))[:8],  # top 8 uniques
        question_type_hint=q_type,
        difficulty_hint=difficulty,
        prompt_directive=directive,
    )


def get_domain_prompt_suffix(domain: str) -> str:
    """Retourne le suffixe de prompt spécifique au domaine détecté."""
    return _DOMAIN_PROMPT_SUFFIXES.get(domain, _DOMAIN_PROMPT_SUFFIXES["general"])


def suggest_question_distribution(
    chunks: List,
    total_questions: int,
    requested_types: List[str],
) -> List[Tuple[int, str, str]]:
    """
    Suggère une distribution optimale des questions par chunk.

    Pour chaque chunk, détermine : (n_questions, type, difficulty)
    en respectant les types demandés et en pondérant par
    la taille et la cohérence du chunk.

    Returns:
        Liste de (n_questions, question_type, difficulty) par chunk.
    """
    if not chunks or total_questions == 0:
        return []

    # Pondérer les chunks par cohérence × taille (chunks plus denses et cohérents = plus de questions)
    weights = []
    for chunk in chunks:
        coherence = getattr(chunk, "coherence_score", 0.8)
        words = getattr(chunk, "word_count", 200)
        weights.append(coherence * min(words, 400))

    total_weight = sum(weights) or 1.0
    normalized = [w / total_weight for w in weights]

    # Distribuer les questions proportionnellement
    raw_counts = [max(0, round(n * total_questions)) for n in normalized]

    # Ajuster pour atteindre exactement total_questions
    diff = total_questions - sum(raw_counts)
    # Ajouter/retirer les questions manquantes aux chunks les plus pondérés
    sorted_idx = sorted(range(len(weights)), key=lambda i: -weights[i])
    for i in range(abs(diff)):
        idx = sorted_idx[i % len(sorted_idx)]
        raw_counts[idx] += 1 if diff > 0 else -1
        raw_counts[idx] = max(0, raw_counts[idx])

    # Assigner les types en suivant la classification Bloom + les types demandés
    distribution = []
    type_cycle = requested_types if requested_types else ["QCM", "OUVERTE"]

    for i, (chunk, n) in enumerate(zip(chunks, raw_counts)):
        if n == 0:
            continue
        bloom = classify_bloom_level(chunk.text)
        # Priorité : type Bloom suggéré si dans les types demandés, sinon round-robin
        if bloom.question_type_hint in type_cycle:
            q_type = bloom.question_type_hint
        else:
            q_type = type_cycle[i % len(type_cycle)]

        distribution.append((n, q_type, bloom.difficulty_hint))

    return distribution
