"""Tests unitaires pour le module pdf_extractor."""
import io
import pytest

from app.pdf_extractor import (
    _clean_text,
    _count_tokens,
    _detect_recurring_lines,
    _is_page_number,
    _split_into_sections,
    extract_sections,
)


class TestCountTokens:
    def test_empty_string(self):
        assert _count_tokens("") == 0

    def test_single_word(self):
        assert _count_tokens("bonjour") == 1

    def test_estimate(self):
        # 10 mots → ~13 tokens (10 / 0.75)
        text = " ".join(["word"] * 10)
        assert _count_tokens(text) > 10


class TestIsPageNumber:
    def test_simple_number(self):
        assert _is_page_number("42") is True

    def test_dashed_number(self):
        assert _is_page_number("- 5 -") is True

    def test_not_a_page_number(self):
        assert _is_page_number("Introduction à l'IA") is False

    def test_empty_string(self):
        assert _is_page_number("") is True  # fullmatch sur espaces → True

    def test_number_with_text(self):
        assert _is_page_number("Page 3 sur 10") is False


class TestDetectRecurringLines:
    def test_detects_header(self):
        pages = [
            "UNIVERSITÉ MOHAMMED V\nContenu de la page 1.",
            "UNIVERSITÉ MOHAMMED V\nContenu de la page 2.",
            "UNIVERSITÉ MOHAMMED V\nContenu de la page 3.",
        ]
        recurring = _detect_recurring_lines(pages, threshold=0.5)
        assert "UNIVERSITÉ MOHAMMED V" in recurring

    def test_unique_lines_not_detected(self):
        pages = [
            "Ligne unique page 1.",
            "Autre ligne page 2.",
            "Encore différent page 3.",
        ]
        recurring = _detect_recurring_lines(pages, threshold=0.5)
        assert len(recurring) == 0

    def test_empty_pages(self):
        assert _detect_recurring_lines([]) == set()


class TestCleanText:
    def test_removes_recurring_lines(self):
        text = "Université Mohammed V\nContenu important de la page."
        recurring = {"Université Mohammed V"}
        cleaned = _clean_text(text, recurring)
        assert "Université Mohammed V" not in cleaned
        assert "Contenu important" in cleaned

    def test_removes_page_numbers(self):
        text = "Texte principal.\n42\nSuite du texte."
        cleaned = _clean_text(text, set())
        assert "42" not in cleaned
        assert "Texte principal" in cleaned

    def test_collapses_whitespace(self):
        text = "Mot1  Mot2   Mot3"
        cleaned = _clean_text(text, set())
        assert "  " not in cleaned


class TestSplitIntoSections:
    def test_short_text_single_section(self):
        text = "Phrase courte. Encore une phrase. Et une dernière phrase complète."
        sections = _split_into_sections(text, max_tokens=200)
        assert len(sections) >= 1

    def test_long_text_multiple_sections(self):
        # Générer un texte long
        sentence = "L'intelligence artificielle révolutionne de nombreux secteurs industriels. "
        text = sentence * 50
        sections = _split_into_sections(text, max_tokens=50)
        assert len(sections) > 1

    def test_empty_text_returns_empty(self):
        assert _split_into_sections("") == []

    def test_ignores_too_short_sections(self):
        text = "Court."
        sections = _split_into_sections(text, max_tokens=500)
        # Une phrase de 1 mot ne constitue pas une section valide (< 10 mots)
        assert len(sections) == 0


class TestExtractSections:
    def test_valid_pdf_returns_sections(self, sample_pdf_stream):
        sections = extract_sections(sample_pdf_stream)
        assert len(sections) >= 1
        for section in sections:
            assert isinstance(section.text, str)
            assert len(section.text) > 0
            assert section.page >= 1
            assert section.tokens > 0

    def test_invalid_bytes_raises_value_error(self):
        bad_stream = io.BytesIO(b"not a pdf at all")
        with pytest.raises(ValueError, match="Impossible d'ouvrir"):
            extract_sections(bad_stream)

    def test_encrypted_pdf_raises_value_error(self):
        # Simuler un PDF chiffré (header avec /Encrypt)
        # Un vrai PDF chiffré est difficile à générer ici — on teste via mock
        import unittest.mock as mock
        with mock.patch("fitz.open") as mock_fitz:
            mock_doc = mock.MagicMock()
            mock_doc.needs_pass = True
            mock_fitz.return_value = mock_doc
            with pytest.raises(ValueError, match="protégé"):
                extract_sections(io.BytesIO(b"%PDF-1.4"))
