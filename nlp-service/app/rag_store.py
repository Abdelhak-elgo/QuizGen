"""
RAG Store — Vector store ChromaDB pour la Retrieval Augmented Generation.

Architecture :
  À l'upload d'un PDF → index_document() embedde et stocke tous les chunks
  À la génération     → retrieve_context() récupère les top-K chunks pertinents

Design :
  - Une collection ChromaDB par document_id → isolation totale
  - Le modèle d'embeddings réutilise le singleton KeyBERT/MiniLM (zéro overhead)
  - Idempotent : si la collection existe déjà (même document_id + même nb de chunks),
    l'indexation est sautée pour ne pas ré-indexer à chaque requête
  - Graceful degradation : si ChromaDB est indisponible, le pipeline continue
    sans RAG (rag_context_used=False dans les stats)

Référence :
  Lewis et al. (2020). Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks.
  NeurIPS 2020. https://arxiv.org/abs/2005.11401
"""
import hashlib
import logging
from typing import List, Optional

from app.config import get_settings
from app.semantic_chunker import SemanticChunk

logger = logging.getLogger(__name__)
settings = get_settings()

# Client ChromaDB singleton (initialisé au premier appel)
_chroma_client = None


def _get_chroma_client():
    """Initialise et retourne le client ChromaDB (singleton thread-safe via GIL)."""
    global _chroma_client
    if _chroma_client is not None:
        return _chroma_client
    try:
        import chromadb

        _chroma_client = chromadb.PersistentClient(path=settings.chromadb_path)
        logger.info("ChromaDB initialisé : %s", settings.chromadb_path)
        return _chroma_client
    except Exception as exc:
        logger.warning("ChromaDB indisponible : %s — RAG désactivé", exc)
        return None


def _collection_name(document_id: str) -> str:
    """
    Génère un nom de collection ChromaDB valide depuis le document_id.
    ChromaDB requiert : 3-63 chars, alphanum + tirets, commence/finit par alphanum.
    """
    safe = "".join(c if c.isalnum() else "-" for c in str(document_id))
    safe = safe.strip("-")[:50]
    if len(safe) < 3:
        safe = f"doc-{safe}"
    return f"qg-{safe}"


def _embed_texts(texts: List[str], embedding_model) -> List[List[float]]:
    """Encode une liste de textes avec sentence-transformers → liste de vecteurs."""
    try:
        embeddings = embedding_model.encode(
            texts,
            normalize_embeddings=True,
            show_progress_bar=False,
            batch_size=32,
        )
        return [e.tolist() for e in embeddings]
    except Exception as exc:
        logger.error("Erreur encodage RAG : %s", exc)
        return []


def index_document(
    document_id: str,
    chunks: List[SemanticChunk],
    embedding_model=None,
) -> bool:
    """
    Indexe tous les chunks d'un document dans ChromaDB.

    Idempotent : si la collection existe déjà avec le même nombre de chunks,
    l'indexation est sautée (le document n'a pas changé).

    Args:
        document_id:     Identifiant unique du document (UUID depuis Spring Boot).
        chunks:          Chunks sémantiques produits par chunk_text_semantically().
        embedding_model: Modèle sentence-transformers (KeyBERT singleton).

    Returns:
        True si indexé (ou déjà indexé), False si erreur.
    """
    if not settings.rag_enabled:
        return False
    if not chunks:
        logger.warning("RAG index_document : aucun chunk pour %s", document_id)
        return False

    client = _get_chroma_client()
    if client is None:
        return False

    collection_name = _collection_name(document_id)

    try:
        # Vérifier si la collection existe déjà avec le même contenu
        try:
            existing = client.get_collection(collection_name)
            existing_count = existing.count()
            if existing_count == len(chunks):
                logger.info(
                    "RAG : collection %s déjà indexée (%d chunks) — skip",
                    collection_name, existing_count,
                )
                return True
            else:
                # Contenu différent (document ré-uploadé) → supprimer et ré-indexer
                logger.info(
                    "RAG : collection %s existante (%d chunks) ≠ nouveau (%d chunks) — ré-indexation",
                    collection_name, existing_count, len(chunks),
                )
                client.delete_collection(collection_name)
        except Exception:
            pass  # Collection n'existe pas encore

        # Créer la collection avec métrique cosinus
        collection = client.create_collection(
            name=collection_name,
            metadata={"hnsw:space": "cosine"},
        )

        # Préparer les données à indexer
        texts = [chunk.text for chunk in chunks]
        ids = [f"chunk-{i}-{hashlib.md5(t[:100].encode()).hexdigest()[:8]}" for i, t in enumerate(texts)]
        metadatas = [
            {
                "chunk_index": i,
                "word_count": chunk.word_count,
                "coherence_score": chunk.coherence_score,
                "keywords": ", ".join(chunk.topic_keywords[:5]),
            }
            for i, chunk in enumerate(chunks)
        ]

        # Encoder avec le modèle d'embeddings
        if embedding_model is not None:
            embeddings = _embed_texts(texts, embedding_model)
            if len(embeddings) != len(texts):
                logger.error("RAG : mismatch embeddings/chunks (%d vs %d)", len(embeddings), len(texts))
                return False

            collection.add(
                ids=ids,
                documents=texts,
                embeddings=embeddings,
                metadatas=metadatas,
            )
        else:
            # Sans modèle d'embeddings : ChromaDB utilise son embedding par défaut
            collection.add(
                ids=ids,
                documents=texts,
                metadatas=metadatas,
            )

        logger.info(
            "RAG : %d chunks indexés dans la collection %s",
            len(chunks), collection_name,
        )
        return True

    except Exception as exc:
        logger.error("RAG index_document erreur : %s", exc, exc_info=True)
        return False


def retrieve_context(
    document_id: str,
    query_text: str,
    top_k: int | None = None,
    exclude_text: str | None = None,
    embedding_model=None,
) -> List[str]:
    """
    Récupère les top-K chunks les plus proches de query_text dans ChromaDB.

    Args:
        document_id:     Identifiant du document (même que pour index_document).
        query_text:      Texte du chunk principal utilisé comme requête de recherche.
        top_k:           Nombre de chunks à récupérer (défaut depuis Settings).
        exclude_text:    Texte à exclure des résultats (évite de retourner le chunk source lui-même).
        embedding_model: Modèle sentence-transformers pour encoder la requête.

    Returns:
        Liste de textes des chunks pertinents (peut être vide si erreur ou collection absente).
    """
    if not settings.rag_enabled:
        return []

    top_k = top_k or settings.rag_top_k
    client = _get_chroma_client()
    if client is None:
        return []

    collection_name = _collection_name(document_id)

    try:
        collection = client.get_collection(collection_name)
    except Exception:
        logger.debug("RAG : collection %s introuvable — pas de contexte RAG", collection_name)
        return []

    try:
        # Encoder la requête
        if embedding_model is not None:
            query_embeds = _embed_texts([query_text], embedding_model)
            if not query_embeds:
                return []
            results = collection.query(
                query_embeddings=query_embeds,
                n_results=min(top_k + 1, collection.count()),  # +1 pour compenser l'exclusion
                include=["documents", "distances"],
            )
        else:
            results = collection.query(
                query_texts=[query_text],
                n_results=min(top_k + 1, collection.count()),
                include=["documents", "distances"],
            )

        retrieved_docs: List[str] = results.get("documents", [[]])[0]
        distances: List[float] = results.get("distances", [[]])[0]

        # Filtrer le chunk source lui-même (overlap > 80%)
        filtered = []
        for doc, dist in zip(retrieved_docs, distances):
            if exclude_text:
                # Overlap simple : si > 80% des tokens du doc sont dans exclude_text → skip
                doc_tokens = set(doc.lower().split())
                excl_tokens = set(exclude_text.lower().split())
                if doc_tokens and len(doc_tokens & excl_tokens) / len(doc_tokens) > 0.80:
                    continue
            # Seuil de similarité : distance cosinus < 0.6 (similarité > 0.4)
            if dist < 0.60:
                filtered.append(doc)
            if len(filtered) >= top_k:
                break

        logger.debug(
            "RAG retrieve : %d chunks récupérés (query=%.40s...)",
            len(filtered), query_text,
        )
        return filtered

    except Exception as exc:
        logger.warning("RAG retrieve_context erreur : %s", exc)
        return []


def retrieve_evidence(
    document_id: str,
    query_text: str,
    embedding_model=None,
) -> Optional[dict]:
    """
    Récupère le chunk le plus proche d'un texte (utilisé pour l'evidence BERTScore).

    Retourne le chunk le plus similaire avec son score de similarité,
    ou None si la collection n'existe pas ou si l'erreur survient.

    Utilisé par la tâche #14 (Evidence RAG pour correction BERTScore).

    Returns:
        {"text": str, "similarity": float} ou None
    """
    if not settings.rag_enabled:
        return None

    client = _get_chroma_client()
    if client is None:
        return None

    collection_name = _collection_name(document_id)

    try:
        collection = client.get_collection(collection_name)
        if collection.count() == 0:
            return None

        if embedding_model is not None:
            query_embeds = _embed_texts([query_text], embedding_model)
            if not query_embeds:
                return None
            results = collection.query(
                query_embeddings=query_embeds,
                n_results=1,
                include=["documents", "distances"],
            )
        else:
            results = collection.query(
                query_texts=[query_text],
                n_results=1,
                include=["documents", "distances"],
            )

        docs = results.get("documents", [[]])[0]
        distances = results.get("distances", [[]])[0]

        if not docs:
            return None

        similarity = round(1.0 - float(distances[0]), 4)
        return {"text": docs[0], "similarity": similarity}

    except Exception as exc:
        logger.warning("RAG retrieve_evidence erreur : %s", exc)
        return None


def delete_document_index(document_id: str) -> bool:
    """
    Supprime la collection ChromaDB d'un document (utile lors de la suppression du PDF).

    Returns:
        True si supprimé, False si introuvable ou erreur.
    """
    client = _get_chroma_client()
    if client is None:
        return False

    collection_name = _collection_name(document_id)
    try:
        client.delete_collection(collection_name)
        logger.info("RAG : collection %s supprimée", collection_name)
        return True
    except Exception as exc:
        logger.debug("RAG delete_document_index : %s", exc)
        return False
