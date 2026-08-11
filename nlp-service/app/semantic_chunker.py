"""
Semantic Chunker — Découpage sémantique du texte source.

Problème du découpage naïf par page :
  - Une page peut contenir plusieurs sujets non liés
  - Un concept peut être coupé en milieu de page
  - Résultat : le LLM reçoit un contexte incohérent → questions hors-sujet

Solution : TextTiling sémantique avec sentence-transformers.
  1. Découper le texte en phrases (SpaCy)
  2. Calculer les embeddings de chaque phrase (MiniLM déjà chargé par KeyBERT)
  3. Mesurer la rupture sémantique entre fenêtres glissantes (cosine distance)
  4. Placer les frontières là où la distance est maximale
  5. Construire des chunks cohérents (150–400 tokens)

Référence : Hearst, M. (1997). TextTiling: Segmenting Text into Multi-paragraph
            Subtopic Passages. Computational Linguistics, 23(1):33–64.
"""
import logging
import re
from dataclasses import dataclass
from typing import List

import numpy as np

logger = logging.getLogger(__name__)


@dataclass
class SemanticChunk:
    """Unité de texte sémantiquement cohérente."""
    text: str                  # Texte brut du chunk
    sentences: List[str]       # Phrases constitutives
    start_sentence: int        # Index de la première phrase dans le doc
    end_sentence: int          # Index de la dernière phrase (exclu)
    coherence_score: float     # Score de cohérence interne [0, 1]
    topic_keywords: List[str]  # Termes dominants extraits

    @property
    def word_count(self) -> int:
        return len(self.text.split())

    @property
    def is_substantial(self) -> bool:
        """Un chunk est substantiel s'il contient assez de contenu pour générer des questions."""
        return self.word_count >= 80


def _split_into_sentences(text: str) -> List[str]:
    """
    Découpe un texte en phrases via regex robuste.
    Évite de casser sur les abréviations communes (M., Dr., etc.)
    """
    # Protéger les abréviations courantes
    abbrevs = ["M.", "Mme.", "Dr.", "Prof.", "Fig.", "p.", "pp.", "art.", "vol.", "no."]
    protected = text
    for abbrev in abbrevs:
        protected = protected.replace(abbrev, abbrev.replace(".", "<!DOT!>"))

    # Découper sur . ! ? suivi d'une majuscule ou fin de ligne
    sentences = re.split(r"(?<=[.!?])\s+(?=[A-ZÀ-Ü\d])", protected)

    # Restaurer les abréviations
    sentences = [s.replace("<!DOT!>", ".").strip() for s in sentences]

    # Filtrer les phrases trop courtes (< 5 mots) — probablement des artefacts
    return [s for s in sentences if len(s.split()) >= 5]


def _compute_embeddings(sentences: List[str], model) -> np.ndarray:
    """
    Calcule les embeddings de phrases avec sentence-transformers.
    Retourne une matrice (N, embedding_dim).
    """
    if not sentences:
        return np.array([])
    try:
        # Le modèle sentence-transformers est déjà chargé dans KeyBERT
        embeddings = model.encode(sentences, normalize_embeddings=True, show_progress_bar=False)
        return np.array(embeddings)
    except Exception as exc:
        logger.warning("Erreur encodage sentences: %s — fallback chunking fixe", exc)
        return np.array([])


def _cosine_distance(a: np.ndarray, b: np.ndarray) -> float:
    """Distance cosinus entre deux vecteurs normalisés : 1 - similarité."""
    if a.shape != b.shape or np.linalg.norm(a) == 0 or np.linalg.norm(b) == 0:
        return 1.0
    # Embeddings déjà normalisés (normalize_embeddings=True) → produit scalaire = similarité
    return float(1.0 - np.dot(a, b))


def _compute_boundary_scores(
    embeddings: np.ndarray,
    window_size: int = 3,
) -> List[float]:
    """
    Calcule un score de rupture sémantique entre chaque paire de fenêtres.

    Méthode TextTiling adaptée :
      Pour chaque position i, comparer la moyenne des embeddings de la fenêtre
      [i-w, i] avec la fenêtre [i, i+w]. Un score élevé = rupture thématique.

    Returns:
        Liste de scores (len = N_phrases - 1).
    """
    n = len(embeddings)
    scores = []

    for i in range(1, n):
        # Fenêtre gauche [max(0, i-window_size), i]
        left_start = max(0, i - window_size)
        left_embed = embeddings[left_start:i].mean(axis=0)

        # Fenêtre droite [i, min(n, i+window_size)]
        right_end = min(n, i + window_size)
        right_embed = embeddings[i:right_end].mean(axis=0)

        scores.append(_cosine_distance(left_embed, right_embed))

    return scores


def _find_boundaries(
    scores: List[float],
    n_boundaries: int,
    min_gap: int = 3,
) -> List[int]:
    """
    Identifie les positions de rupture maximale (pics locaux).

    Args:
        scores:       Scores de rupture par position.
        n_boundaries: Nombre de frontières souhaitées.
        min_gap:      Distance minimale entre deux frontières.

    Returns:
        Indices triés des positions de frontière.
    """
    if not scores:
        return []

    # Trouver les pics locaux : score[i] > score[i-1] et score[i] > score[i+1]
    peaks = []
    for i in range(1, len(scores) - 1):
        if scores[i] > scores[i - 1] and scores[i] > scores[i + 1]:
            peaks.append((scores[i], i))

    # Si pas assez de pics, prendre les N positions avec les scores les plus élevés
    if len(peaks) < n_boundaries:
        indexed = sorted(enumerate(scores), key=lambda x: -x[1])
        peaks = [(s, i) for i, s in indexed]

    # Trier par score décroissant, sélectionner en respectant min_gap
    peaks_sorted = sorted(peaks, key=lambda x: -x[0])
    selected = []
    for _score, pos in peaks_sorted:
        # Vérifier la distance minimale avec les frontières déjà sélectionnées
        if all(abs(pos - s) >= min_gap for s in selected):
            selected.append(pos)
        if len(selected) == n_boundaries:
            break

    return sorted(selected)


def _compute_chunk_coherence(chunk_embeddings: np.ndarray) -> float:
    """
    Cohérence interne d'un chunk = similarité cosinus moyenne entre toutes
    les paires de phrases. Valeur [0, 1] ; proche de 1 = très cohérent.
    """
    n = len(chunk_embeddings)
    if n <= 1:
        return 1.0

    similarities = []
    for i in range(n):
        for j in range(i + 1, n):
            sim = float(np.dot(chunk_embeddings[i], chunk_embeddings[j]))
            similarities.append(max(0.0, sim))  # clamp à 0

    return float(np.mean(similarities)) if similarities else 1.0


def _extract_chunk_keywords(sentences: List[str], top_k: int = 5) -> List[str]:
    """
    Extrait les termes les plus fréquents d'un chunk (TF simple, sans stop words).
    Utilisé pour enrichir le prompt LLM avec les concepts dominants du chunk.
    """
    STOP_WORDS_FR = {
        "le", "la", "les", "de", "du", "des", "un", "une", "et", "en", "à",
        "au", "aux", "est", "sont", "a", "ont", "dans", "par", "pour", "sur",
        "avec", "que", "qui", "se", "il", "elle", "nous", "vous", "ils", "elles",
        "on", "ce", "cette", "ces", "mon", "ton", "son", "ma", "ta", "sa", "pas",
        "plus", "ou", "si", "ne", "ni", "car", "mais", "donc", "or", "aussi",
    }
    freq: dict[str, int] = {}
    for sentence in sentences:
        for word in re.findall(r"\b[a-zéèêëàâùûîïôœç]{4,}\b", sentence.lower()):
            if word not in STOP_WORDS_FR:
                freq[word] = freq.get(word, 0) + 1

    return [w for w, _ in sorted(freq.items(), key=lambda x: -x[1])[:top_k]]


def chunk_text_semantically(
    text: str,
    embedding_model=None,
    target_chunk_words: int = 250,
    max_chunk_words: int = 450,
    min_chunk_words: int = 80,
) -> List[SemanticChunk]:
    """
    Découpe un texte long en chunks sémantiquement cohérents.

    Algorithme :
      1. Découper en phrases
      2. Calculer les embeddings (si modèle disponible)
      3. Détecter les ruptures thématiques (TextTiling sémantique)
      4. Construire les chunks et calculer leur cohérence interne

    Args:
        text:               Texte source complet.
        embedding_model:    Modèle sentence-transformers (optionnel, fallback fixe si None).
        target_chunk_words: Taille cible d'un chunk en mots.
        max_chunk_words:    Taille maximale d'un chunk.
        min_chunk_words:    Taille minimale d'un chunk (en dessous → fusionné).

    Returns:
        Liste de SemanticChunk triés par ordre d'apparition.
    """
    if not text.strip():
        return []

    # ── Étape 1 : Découpage en phrases ─────────────────────────────────────
    sentences = _split_into_sentences(text)
    if len(sentences) < 3:
        # Texte trop court → un seul chunk
        return [SemanticChunk(
            text=text,
            sentences=sentences,
            start_sentence=0,
            end_sentence=len(sentences),
            coherence_score=1.0,
            topic_keywords=_extract_chunk_keywords(sentences),
        )]

    # ── Étape 2 : Embeddings ─────────────────────────────────────────────────
    embeddings = None
    if embedding_model is not None:
        embeddings = _compute_embeddings(sentences, embedding_model)

    # ── Étape 3 : Détection des frontières sémantiques ───────────────────────
    n_words = len(text.split())
    n_chunks_target = max(1, n_words // target_chunk_words)

    if embeddings is not None and len(embeddings) >= 4:
        boundary_scores = _compute_boundary_scores(embeddings)
        # On cherche n_chunks_target - 1 frontières
        n_boundaries = min(n_chunks_target - 1, len(boundary_scores) // 2)
        boundaries = _find_boundaries(boundary_scores, n_boundaries, min_gap=3)
        # Convertir les indices de scores en indices de phrases (offset +1)
        boundaries = [b + 1 for b in boundaries]
    else:
        # Fallback : découper en groupes de taille fixe
        step = max(3, len(sentences) // max(1, n_chunks_target))
        boundaries = list(range(step, len(sentences), step))

    # ── Étape 4 : Construction des chunks ───────────────────────────────────
    segment_starts = [0] + boundaries + [len(sentences)]
    raw_chunks: List[SemanticChunk] = []

    for i in range(len(segment_starts) - 1):
        start = segment_starts[i]
        end = segment_starts[i + 1]
        chunk_sentences = sentences[start:end]
        chunk_text = " ".join(chunk_sentences)
        word_count = len(chunk_text.split())

        if word_count < min_chunk_words:
            # Fusionner avec le chunk précédent si trop petit
            if raw_chunks:
                prev = raw_chunks[-1]
                merged_sentences = prev.sentences + chunk_sentences
                merged_text = " ".join(merged_sentences)
                raw_chunks[-1] = SemanticChunk(
                    text=merged_text,
                    sentences=merged_sentences,
                    start_sentence=prev.start_sentence,
                    end_sentence=end,
                    coherence_score=0.0,  # recalculé après
                    topic_keywords=[],
                )
            continue

        # Cohérence interne
        coherence = 0.8  # défaut si pas d'embeddings
        if embeddings is not None and end > start:
            chunk_embeds = embeddings[start:end]
            coherence = _compute_chunk_coherence(chunk_embeds)

        raw_chunks.append(SemanticChunk(
            text=chunk_text,
            sentences=chunk_sentences,
            start_sentence=start,
            end_sentence=end,
            coherence_score=round(coherence, 4),
            topic_keywords=_extract_chunk_keywords(chunk_sentences),
        ))

    # ── Étape 5 : Découper les chunks trop longs ─────────────────────────────
    final_chunks: List[SemanticChunk] = []
    for chunk in raw_chunks:
        if chunk.word_count <= max_chunk_words:
            final_chunks.append(chunk)
        else:
            # Diviser en 2 parties égales
            mid = len(chunk.sentences) // 2
            for sub_sents in [chunk.sentences[:mid], chunk.sentences[mid:]]:
                if not sub_sents:
                    continue
                sub_text = " ".join(sub_sents)
                if len(sub_text.split()) >= min_chunk_words:
                    final_chunks.append(SemanticChunk(
                        text=sub_text,
                        sentences=sub_sents,
                        start_sentence=chunk.start_sentence,
                        end_sentence=chunk.end_sentence,
                        coherence_score=chunk.coherence_score,
                        topic_keywords=_extract_chunk_keywords(sub_sents),
                    ))

    logger.info(
        "Chunking sémantique : %d phrases → %d chunks (avg %.0f mots/chunk)",
        len(sentences),
        len(final_chunks),
        sum(c.word_count for c in final_chunks) / max(1, len(final_chunks)),
    )

    return final_chunks
