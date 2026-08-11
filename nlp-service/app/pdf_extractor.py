"""
Extraction de texte depuis des fichiers PDF via PyMuPDF.

Fonctionnalités :
- Extraction page par page
- Nettoyage des headers/footers récurrents et numéros de page
- Découpage en sections cohérentes (~500 tokens)
"""
import re
import logging
from collections import Counter
from typing import IO, List

import fitz  # PyMuPDF

from app.models import TextSection

logger = logging.getLogger(__name__)

# Nombre de mots approximatif par token (estimation heuristique)
_WORDS_PER_TOKEN = 0.75


def _count_tokens(text: str) -> int:
    """Estimation rapide du nombre de tokens (pas besoin d'un tokenizer exact ici)."""
    return int(len(text.split()) / _WORDS_PER_TOKEN)


def _is_page_number(line: str) -> bool:
    """Retourne True si la ligne ne contient qu'un numéro de page."""
    return bool(re.fullmatch(r"\s*-?\s*\d+\s*-?\s*", line.strip()))


def _detect_recurring_lines(pages_text: List[str], threshold: float = 0.5) -> set:
    """
    Détecte les lignes qui apparaissent dans plus de `threshold` des pages
    (probablement des headers/footers).
    """
    if not pages_text:
        return set()

    all_lines: List[str] = []
    for page_text in pages_text:
        all_lines.extend(line.strip() for line in page_text.split("\n") if line.strip())

    line_counts = Counter(all_lines)
    min_occurrences = max(2, int(len(pages_text) * threshold))
    return {line for line, count in line_counts.items() if count >= min_occurrences}


def _clean_text(text: str, recurring_lines: set) -> str:
    """Supprime les lignes récurrentes, numéros de page et espaces excessifs."""
    cleaned_lines = []
    for line in text.split("\n"):
        stripped = line.strip()
        if not stripped:
            continue
        if _is_page_number(stripped):
            continue
        if stripped in recurring_lines:
            continue
        cleaned_lines.append(stripped)

    cleaned = " ".join(cleaned_lines)
    # Réduction des espaces multiples
    cleaned = re.sub(r"\s{2,}", " ", cleaned)
    return cleaned.strip()


def _split_into_sections(text: str, max_tokens: int = 500) -> List[str]:
    """
    Découpe un texte en sections d'environ `max_tokens` tokens.
    Essaie de couper aux limites de phrases (. ! ?).
    """
    if not text:
        return []

    sentences = re.split(r"(?<=[.!?])\s+", text)
    sections: List[str] = []
    current_section: List[str] = []
    current_tokens = 0

    for sentence in sentences:
        sentence_tokens = _count_tokens(sentence)
        if current_tokens + sentence_tokens > max_tokens and current_section:
            sections.append(" ".join(current_section))
            current_section = [sentence]
            current_tokens = sentence_tokens
        else:
            current_section.append(sentence)
            current_tokens += sentence_tokens

    if current_section:
        sections.append(" ".join(current_section))

    return [s for s in sections if len(s.split()) >= 10]  # ignorer les sections trop courtes


def extract_sections(
    pdf_stream: IO[bytes],
    max_tokens_per_section: int = 500,
) -> List[TextSection]:
    """
    Extrait et découpe le contenu d'un PDF en sections de texte.

    Args:
        pdf_stream: Flux binaire du fichier PDF.
        max_tokens_per_section: Taille maximale d'une section en tokens.

    Returns:
        Liste de TextSection triées par page.

    Raises:
        ValueError: Si le PDF est vide, chiffré ou ne contient pas de texte extractible.
    """
    pdf_stream.seek(0)
    raw_data = pdf_stream.read()

    try:
        doc = fitz.open(stream=raw_data, filetype="pdf")
    except Exception as exc:
        raise ValueError(f"Impossible d'ouvrir le fichier PDF : {exc}") from exc

    if doc.needs_pass:
        raise ValueError("Le PDF est protégé par un mot de passe.")

    nb_pages = len(doc)
    if nb_pages == 0:
        raise ValueError("Le PDF ne contient aucune page.")

    logger.info("Extraction PDF : %d pages", nb_pages)

    # Extraction brute par page
    pages_text: List[str] = []
    for page_num in range(nb_pages):
        page = doc[page_num]
        text = page.get_text("text")  # type: ignore[attr-defined]
        pages_text.append(text)

    doc.close()

    # Détection des headers/footers récurrents
    recurring = _detect_recurring_lines(pages_text)
    logger.debug("Lignes récurrentes détectées : %d", len(recurring))

    # Nettoyage + découpage en sections
    sections: List[TextSection] = []
    for page_num, raw_text in enumerate(pages_text):
        cleaned = _clean_text(raw_text, recurring)
        if not cleaned:
            continue
        page_sections = _split_into_sections(cleaned, max_tokens_per_section)
        for sec_text in page_sections:
            sections.append(
                TextSection(
                    page=page_num + 1,
                    text=sec_text,
                    tokens=_count_tokens(sec_text),
                )
            )

    if not sections:
        raise ValueError(
            "Aucun texte extractible trouvé. Le PDF est peut-être scanné (images uniquement)."
        )

    logger.info("Sections extraites : %d", len(sections))
    return sections
