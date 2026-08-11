"""
Tests unitaires pour app.rag_store
===================================

Couvre :
  - index_document() : indexation, idempotence par hash, ré-indexation si contenu change
  - retrieve_context() : récupération top-K, exclusion du chunk source, seuil de distance
  - retrieve_evidence() : chunk unique le plus proche
  - delete_document_index() : suppression de collection
  - RAG propagé dans le prompt CoT (chain_of_thought.build_cot_prompt)
  - RAG propagé dans le score qualité (quality_scorer.score_questions)

Les tests utilisent un client ChromaDB in-memory (pas de fichiers sur disque)
pour ne pas dépendre du système de fichiers en CI.
"""
import hashlib
import types
from unittest.mock import MagicMock, patch

import pytest

# ── Helpers ───────────────────────────────────────────────────────────────────

def _make_chunk(text: str, idx: int = 0):
    """Crée un SemanticChunk minimal sans importer le module complet."""
    chunk = types.SimpleNamespace()
    chunk.text = text
    chunk.word_count = len(text.split())
    chunk.coherence_score = 0.8
    chunk.topic_keywords = ["concept", "clé"]
    return chunk


def _content_hash(chunks) -> str:
    return hashlib.md5(
        "".join(c.text for c in chunks).encode("utf-8", errors="replace")
    ).hexdigest()


def _make_embedding_model(dim: int = 8):
    """Modèle d'embeddings bouchonné retournant des vecteurs aléatoires normalisés."""
    import numpy as np

    model = MagicMock()

    def encode(texts, **kwargs):
        rng = np.random.default_rng(sum(ord(c) for c in "".join(texts)))
        vecs = rng.standard_normal((len(texts), dim)).astype("float32")
        norms = np.linalg.norm(vecs, axis=1, keepdims=True)
        return vecs / np.maximum(norms, 1e-8)

    model.encode = encode
    return model


# ── Fixtures ──────────────────────────────────────────────────────────────────

@pytest.fixture
def chroma_client(tmp_path):
    """Client ChromaDB persistant dans un répertoire temporaire."""
    pytest.importorskip("chromadb")
    import chromadb
    return chromadb.PersistentClient(path=str(tmp_path / "chroma"))


@pytest.fixture
def rag_module(chroma_client):
    """
    Importe app.rag_store avec le client patché sur le client de test.
    Réinitialise le singleton entre les tests.
    """
    import app.rag_store as rs
    # Injecter le client de test + activer RAG
    rs._chroma_client = chroma_client
    rs.settings.rag_enabled = True
    rs.settings.rag_top_k = 3
    yield rs
    # Réinitialiser le singleton pour ne pas polluer les autres tests
    rs._chroma_client = None


@pytest.fixture
def embed_model():
    return _make_embedding_model()


@pytest.fixture
def sample_chunks():
    return [
        _make_chunk("Les réseaux de neurones sont des modèles inspirés du cerveau.", 0),
        _make_chunk("La descente de gradient optimise les paramètres du modèle.", 1),
        _make_chunk("Le sur-apprentissage survient lorsque le modèle mémorise les données.", 2),
        _make_chunk("La régularisation L2 pénalise les poids élevés pour éviter le sur-apprentissage.", 3),
        _make_chunk("Le taux d'apprentissage contrôle la vitesse de convergence.", 4),
    ]


# ── Tests : index_document ────────────────────────────────────────────────────

class TestIndexDocument:
    def test_indexes_chunks_successfully(self, rag_module, sample_chunks, embed_model):
        result = rag_module.index_document("doc-1", sample_chunks, embed_model)
        assert result is True

    def test_creates_collection_with_correct_count(self, rag_module, sample_chunks, embed_model, chroma_client):
        rag_module.index_document("doc-2", sample_chunks, embed_model)
        col = chroma_client.get_collection(rag_module._collection_name("doc-2"))
        assert col.count() == len(sample_chunks)

    def test_idempotent_same_content(self, rag_module, sample_chunks, embed_model):
        """Même contenu → même hash → skip sans erreur."""
        r1 = rag_module.index_document("doc-3", sample_chunks, embed_model)
        r2 = rag_module.index_document("doc-3", sample_chunks, embed_model)
        assert r1 is True
        assert r2 is True  # skipped, pas d'erreur

    def test_reindexes_when_content_changes(self, rag_module, sample_chunks, embed_model, chroma_client):
        """Contenu différent → hash différent → ré-indexation."""
        rag_module.index_document("doc-4", sample_chunks, embed_model)
        # Modifier le contenu d'un chunk
        modified = list(sample_chunks)
        modified[0] = _make_chunk("Texte entièrement différent qui change le hash.")
        r2 = rag_module.index_document("doc-4", modified, embed_model)
        assert r2 is True
        col = chroma_client.get_collection(rag_module._collection_name("doc-4"))
        # La collection a été recréée avec le nouveau contenu
        assert col.count() == len(modified)

    def test_content_hash_stored_in_metadata(self, rag_module, sample_chunks, embed_model, chroma_client):
        """Le hash de contenu est persisté dans les métadonnées de la collection."""
        rag_module.index_document("doc-5", sample_chunks, embed_model)
        col = chroma_client.get_collection(rag_module._collection_name("doc-5"))
        expected_hash = _content_hash(sample_chunks)
        assert col.metadata.get("content_hash") == expected_hash

    def test_returns_false_when_rag_disabled(self, rag_module, sample_chunks, embed_model):
        rag_module.settings.rag_enabled = False
        result = rag_module.index_document("doc-6", sample_chunks, embed_model)
        assert result is False
        rag_module.settings.rag_enabled = True  # restaurer

    def test_returns_false_for_empty_chunks(self, rag_module, embed_model):
        result = rag_module.index_document("doc-7", [], embed_model)
        assert result is False


# ── Tests : retrieve_context ──────────────────────────────────────────────────

class TestRetrieveContext:
    def test_returns_list_of_strings(self, rag_module, sample_chunks, embed_model):
        rag_module.index_document("ret-1", sample_chunks, embed_model)
        results = rag_module.retrieve_context(
            "ret-1", sample_chunks[0].text, embedding_model=embed_model
        )
        assert isinstance(results, list)
        assert all(isinstance(r, str) for r in results)

    def test_returns_at_most_top_k(self, rag_module, sample_chunks, embed_model):
        rag_module.index_document("ret-2", sample_chunks, embed_model)
        results = rag_module.retrieve_context(
            "ret-2", sample_chunks[0].text, top_k=2, embedding_model=embed_model
        )
        assert len(results) <= 2

    def test_excludes_source_chunk(self, rag_module, sample_chunks, embed_model):
        """Le chunk source lui-même ne doit pas apparaître dans les résultats RAG."""
        rag_module.index_document("ret-3", sample_chunks, embed_model)
        source_text = sample_chunks[0].text
        results = rag_module.retrieve_context(
            "ret-3", source_text, exclude_text=source_text, embedding_model=embed_model
        )
        assert source_text not in results

    def test_returns_empty_when_collection_absent(self, rag_module, embed_model):
        results = rag_module.retrieve_context(
            "nonexistent-doc", "quelque chose", embedding_model=embed_model
        )
        assert results == []

    def test_returns_empty_when_rag_disabled(self, rag_module, sample_chunks, embed_model):
        rag_module.index_document("ret-4", sample_chunks, embed_model)
        rag_module.settings.rag_enabled = False
        results = rag_module.retrieve_context(
            "ret-4", sample_chunks[0].text, embedding_model=embed_model
        )
        assert results == []
        rag_module.settings.rag_enabled = True


# ── Tests : retrieve_evidence ─────────────────────────────────────────────────

class TestRetrieveEvidence:
    def test_returns_dict_with_text_and_similarity(self, rag_module, sample_chunks, embed_model):
        rag_module.index_document("ev-1", sample_chunks, embed_model)
        evidence = rag_module.retrieve_evidence(
            "ev-1", sample_chunks[0].text, embedding_model=embed_model
        )
        assert evidence is not None
        assert "text" in evidence
        assert "similarity" in evidence
        assert isinstance(evidence["text"], str)
        assert 0.0 <= evidence["similarity"] <= 1.0

    def test_returns_none_when_collection_absent(self, rag_module, embed_model):
        evidence = rag_module.retrieve_evidence(
            "nonexistent-ev", "texte quelconque", embedding_model=embed_model
        )
        assert evidence is None


# ── Tests : delete_document_index ─────────────────────────────────────────────

class TestDeleteDocumentIndex:
    def test_deletes_existing_collection(self, rag_module, sample_chunks, embed_model, chroma_client):
        rag_module.index_document("del-1", sample_chunks, embed_model)
        result = rag_module.delete_document_index("del-1")
        assert result is True
        with pytest.raises(Exception):
            chroma_client.get_collection(rag_module._collection_name("del-1"))

    def test_returns_false_for_nonexistent(self, rag_module):
        result = rag_module.delete_document_index("never-indexed")
        assert result is False


# ── Tests : propagation RAG → prompt CoT ─────────────────────────────────────

class TestRagInCoTPrompt:
    def test_rag_section_present_when_context_provided(self):
        from app.chain_of_thought import build_cot_prompt
        from app.models import Difficulty, QuestionType

        rag_chunks = ["Texte RAG chunk A.", "Texte RAG chunk B."]
        prompt = build_cot_prompt(
            question_type=QuestionType.QCM,
            context="Contexte principal du cours.",
            keywords=["concept"],
            nb=1,
            difficulty=Difficulty.MOYEN,
            rag_context=rag_chunks,
        )
        assert "CONTEXTE COMPLÉMENTAIRE DU DOCUMENT" in prompt
        assert "Texte RAG chunk A." in prompt

    def test_rag_section_absent_when_no_context(self):
        from app.chain_of_thought import build_cot_prompt
        from app.models import Difficulty, QuestionType

        prompt = build_cot_prompt(
            question_type=QuestionType.QCM,
            context="Contexte principal du cours.",
            keywords=["concept"],
            nb=1,
            difficulty=Difficulty.MOYEN,
            rag_context=None,
        )
        assert "CONTEXTE COMPLÉMENTAIRE DU DOCUMENT" not in prompt

    def test_rag_section_absent_for_empty_list(self):
        from app.chain_of_thought import build_cot_prompt
        from app.models import Difficulty, QuestionType

        prompt = build_cot_prompt(
            question_type=QuestionType.OUVERTE,
            context="Contexte principal.",
            keywords=[],
            nb=1,
            difficulty=Difficulty.FACILE,
            rag_context=[],
        )
        assert "CONTEXTE COMPLÉMENTAIRE DU DOCUMENT" not in prompt


# ── Tests : propagation RAG → quality scorer ──────────────────────────────────

class TestRagInQualityScorer:
    def _make_question(self, content: str = "Qu'est-ce que le gradient ?", answer: str = "Le gradient est un vecteur de dérivées partielles."):
        from app.models import Difficulty, GeneratedQuestion, QuestionType

        return GeneratedQuestion(
            type=QuestionType.OUVERTE,
            content=content,
            correct_answer=answer,
            difficulty=Difficulty.MOYEN,
            keywords=["gradient"],
        )

    def test_score_questions_accepts_rag_context(self):
        from app.quality_scorer import score_questions

        q = self._make_question()
        source = "Un réseau de neurones est composé de couches."
        # La réponse est dans le contexte RAG, pas dans le chunk source
        rag_ctx = ["Le gradient est un vecteur de dérivées partielles."]
        scored = score_questions(
            questions=[q],
            source_chunk=source,
            rag_context=rag_ctx,
        )
        assert len(scored) == 1
        # L'answerability est calculée sur source + rag_ctx → score >= 0
        assert scored[0].answerability_score >= 0.0

    def test_score_questions_works_without_rag(self):
        from app.quality_scorer import score_questions

        q = self._make_question(
            answer="La descente de gradient optimise les paramètres."
        )
        source = "La descente de gradient optimise les paramètres du modèle."
        scored = score_questions(questions=[q], source_chunk=source, rag_context=None)
        assert len(scored) == 1
