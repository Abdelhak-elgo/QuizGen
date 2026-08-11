"""Fixtures pytest partagées entre tous les tests."""
import io
import struct
import zlib
import pytest


# ── Helpers PDF minimalistes ──────────────────────────────────────────────────

def _make_minimal_pdf(text: str = "Contenu de test pour QuizGen.") -> bytes:
    """
    Génère un PDF minimaliste valide contenant `text` en plain-text.
    Utilise uniquement la bibliothèque standard Python (struct + zlib).
    """
    # On construit un PDF sans images ni polices complexes
    content = (
        f"BT /F1 12 Tf 50 750 Td ({text}) Tj ET"
    )
    content_bytes = content.encode("latin-1")

    objects = []

    # Objet 1 : Catalogue
    objects.append(b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
    # Objet 2 : Pages
    objects.append(b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
    # Objet 3 : Page
    objects.append(
        b"3 0 obj\n<< /Type /Page /Parent 2 0 R "
        b"/MediaBox [0 0 612 792] /Contents 4 0 R "
        b"/Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>\nendobj\n"
    )
    # Objet 4 : Contenu
    content_obj = (
        f"4 0 obj\n<< /Length {len(content_bytes)} >>\nstream\n"
        + content
        + "\nendstream\nendobj\n"
    ).encode("latin-1")
    objects.append(content_obj)

    header = b"%PDF-1.4\n"
    body = b"".join(objects)
    xref_offset = len(header) + len(body)

    xref = (
        f"xref\n0 {len(objects) + 1}\n"
        f"0000000000 65535 f \n"
    )
    offset = len(header)
    for obj in objects:
        xref += f"{offset:010d} 00000 n \n"
        offset += len(obj)

    trailer = (
        f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\n"
        f"startxref\n{xref_offset}\n%%EOF"
    )

    return header + body + xref.encode() + trailer.encode()


@pytest.fixture
def sample_pdf_stream():
    """Flux BytesIO d'un PDF de test valide avec du texte extractible."""
    text = (
        "L'intelligence artificielle est une discipline de l'informatique. "
        "Elle vise à reproduire des comportements intelligents par des machines. "
        "Les réseaux de neurones artificiels sont inspirés du cerveau humain. "
        "Le machine learning permet aux systèmes d'apprendre à partir de données. "
        "Le deep learning utilise des couches multiples de neurones pour l'apprentissage. "
        "Les algorithmes supervisés nécessitent des données étiquetées pour l'entraînement."
    )
    return io.BytesIO(_make_minimal_pdf(text))


@pytest.fixture
def empty_pdf_stream():
    """Flux BytesIO d'un PDF sans texte extractible (contenu vide)."""
    return io.BytesIO(_make_minimal_pdf(""))


@pytest.fixture
def sample_text():
    return (
        "L'intelligence artificielle est une discipline de l'informatique qui vise "
        "à reproduire des comportements intelligents par des machines. "
        "Les réseaux de neurones artificiels sont inspirés du cerveau humain. "
        "Le machine learning permet aux systèmes d'apprendre automatiquement à partir de données. "
        "La classification et la régression sont deux tâches fondamentales du machine learning."
    )


@pytest.fixture
def sample_questions_raw():
    """Questions brutes simulant une réponse du LLM."""
    from app.models import Difficulty, GeneratedQuestion, QuestionType
    return [
        GeneratedQuestion(
            type=QuestionType.QCM,
            content="Qu'est-ce que le machine learning ?",
            options=[
                "Un type de base de données",
                "Une méthode d'apprentissage automatique à partir de données",
                "Un langage de programmation",
                "Un système d'exploitation",
            ],
            correct_answer="Une méthode d'apprentissage automatique à partir de données",
            explanation="Le machine learning permet aux systèmes d'apprendre sans être explicitement programmés.",
            difficulty=Difficulty.MOYEN,
            keywords=["machine learning", "apprentissage"],
        ),
        GeneratedQuestion(
            type=QuestionType.QCM,
            content="Qu'est-ce que le machine learning ?",  # Doublon intentionnel
            options=[
                "Une technique d'apprentissage automatique",
                "Un algorithme de tri",
                "Un protocole réseau",
                "Un type de processeur",
            ],
            correct_answer="Une technique d'apprentissage automatique",
            difficulty=Difficulty.MOYEN,
            keywords=["machine learning"],
        ),
        GeneratedQuestion(
            type=QuestionType.OUVERTE,
            content="Expliquez la différence entre le machine learning supervisé et non supervisé.",
            options=None,
            correct_answer="Le ML supervisé utilise des données étiquetées, le non supervisé découvre des patterns sans étiquettes.",
            difficulty=Difficulty.DIFFICILE,
            keywords=["supervisé", "non supervisé"],
        ),
    ]
