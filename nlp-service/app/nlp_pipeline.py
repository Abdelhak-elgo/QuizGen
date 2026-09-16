"""
Pipeline NLP : SpaCy (POS tagging + NER) + KeyBERT (extraction de concepts).

Le modèle SpaCy est chargé une seule fois (singleton) au démarrage.
"""
import logging
from functools import lru_cache
from typing import Dict, List

from app.config import get_settings
from app.models import TextSection

logger = logging.getLogger(__name__)

settings = get_settings()


@lru_cache(maxsize=1)
def _load_spacy_model():
    """Charge le modèle SpaCy en mémoire (singleton thread-safe)."""
    import spacy  # import tardif pour ne pas bloquer le démarrage en CI

    logger.info("Chargement du modèle SpaCy : %s", settings.spacy_model)
    nlp = spacy.load(settings.spacy_model)
    logger.info("Modèle SpaCy chargé.")
    return nlp


@lru_cache(maxsize=1)
def _load_keybert_model():
    """Charge le modèle KeyBERT (singleton)."""
    from keybert import KeyBERT  # type: ignore

    logger.info("Chargement de KeyBERT...")
    model = KeyBERT(model="paraphrase-multilingual-MiniLM-L12-v2")
    logger.info("KeyBERT prêt.")
    return model


def _extract_spacy_terms(text: str) -> List[str]:
    """
    Extrait les entités nommées et les noms propres/communs importants via SpaCy.
    Retourne une liste de termes uniques (lowercase).
    """
    nlp = _load_spacy_model()
    doc = nlp(text[:10000])  # limite pour éviter les timeouts sur textes très longs

    terms: List[str] = []

    # Entités nommées (NER)
    for ent in doc.ents:
        if ent.label_ not in {"CARDINAL", "ORDINAL", "DATE", "TIME", "PERCENT", "MONEY", "QUANTITY"}:
            terms.append(ent.text.lower().strip())

    # Noms et adjectifs importants (POS tagging) — termes de longueur ≥ 4 chars
    for token in doc:
        if (
            token.pos_ in {"NOUN", "PROPN", "ADJ"}
            and not token.is_stop
            and not token.is_punct
            and len(token.lemma_) >= 4
        ):
            terms.append(token.lemma_.lower())

    # Dédupliquer en conservant l'ordre d'apparition
    seen = set()
    unique_terms = []
    for t in terms:
        if t not in seen and t:
            seen.add(t)
            unique_terms.append(t)

    return unique_terms[:50]  # top 50 max


def _extract_keybert_concepts(text: str, top_k: int = 10) -> List[str]:
    """Extrait les K concepts les plus importants via KeyBERT."""
    kw_model = _load_keybert_model()
    try:
        keywords = kw_model.extract_keywords(
            text,
            keyphrase_ngram_range=(1, 3),
            stop_words=None,  # KeyBERT gère le multilingual
            top_n=top_k,
            use_mmr=True,       # Diversité maximale
            diversity=0.5,
        )
        return [kw for kw, _score in keywords]
    except Exception as exc:
        logger.warning("KeyBERT erreur sur ce texte : %s", exc)
        return []


def analyze_sections(
    sections: List[TextSection],
    top_k: int | None = None,
) -> Dict[int, List[str]]:
    """
    Analyse les sections de texte et retourne un dict {section_index: [concepts]}.

    Args:
        sections: Sections extraites par pdf_extractor.
        top_k: Nombre de concepts par section (défaut depuis Settings).

    Returns:
        Dictionnaire indexé par position de section, valeur = liste de concepts clés.
    """
    top_k = top_k or settings.keybert_top_k
    result: Dict[int, List[str]] = {}

    for idx, section in enumerate(sections):
        text = section.text
        if len(text.split()) < 20:
            result[idx] = []
            continue

        # Combiner SpaCy (termes linguistiques) et KeyBERT (concepts sémantiques)
        spacy_terms = _extract_spacy_terms(text)
        keybert_concepts = _extract_keybert_concepts(text, top_k)

        # Fusionner, KeyBERT en premier (plus sémantiques)
        combined = list(dict.fromkeys(keybert_concepts + spacy_terms))
        result[idx] = combined[:top_k]

        logger.debug(
            "Section %d (page %d) → %d concepts : %s",
            idx, section.page, len(result[idx]), result[idx][:5],
        )

    return result
