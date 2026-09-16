"""
Tests unitaires pour le module BERTScore.

Les tests ne nécessitent PAS bert-score installé :
- En CI/CD, bert-score peut être absent (modèle roberta-large ~1.4 Go).
- Le scorer gère gracieusement l'absence via _check_available().
- Les tests vérifient la logique métier (seuils, partial_score, labels)
  en mockant l'appel interne.
"""
import importlib
import sys
import types
from unittest.mock import MagicMock, patch

import pytest


# ── Fixtures ───────────────────────────────────────────────────────────────────

@pytest.fixture()
def scorer():
    """Retourne un BertScorer isolé (instance fraîche, hors singleton)."""
    from app.bert_scorer import BertScorer

    s = BertScorer.__new__(BertScorer)
    s._model_type = "roberta-large"
    s._lang = "fr"
    s._available = None
    return s


# ── Tests : _check_available ───────────────────────────────────────────────────

def test_check_available_when_bert_score_installed(scorer):
    """Doit retourner True si bert_score est importable."""
    # Injecter un faux module bert_score dans sys.modules
    fake_module = types.ModuleType("bert_score")
    with patch.dict(sys.modules, {"bert_score": fake_module}):
        result = scorer._check_available()
    assert result is True


def test_check_available_when_bert_score_missing(scorer):
    """Doit retourner False si bert_score n'est pas installé."""
    # Simuler un ImportError
    with patch.dict(sys.modules, {"bert_score": None}):
        scorer._available = None
        result = scorer._check_available()
    assert result is False


# ── Tests : partial_score ──────────────────────────────────────────────────────

@pytest.mark.parametrize("f1, expected", [
    (1.00, 1.0),
    (0.80, 1.0),     # Au-dessus du seuil CORRECT (0.70)
    (0.70, 1.0),     # Exactement au seuil CORRECT
    (0.60, 0.5),     # Milieu de la zone PARTIEL [0.50, 0.70[
    (0.50, 0.0),     # Exactement au seuil PARTIEL (inclus → 0)
    (0.49, 0.0),     # En-dessous du seuil PARTIEL
    (0.00, 0.0),     # Score nul
])
def test_partial_score_thresholds(scorer, f1, expected):
    """Vérifie les seuils de conversion F1 → score partiel."""
    result = scorer.partial_score(f1)
    assert abs(result - expected) < 0.001, (
        f"f1={f1}: attendu {expected}, obtenu {result}"
    )


def test_partial_score_interpolation(scorer):
    """Vérifie l'interpolation linéaire dans la zone partielle."""
    # f1=0.60 est au milieu de [0.50, 0.70] → score = 0.5
    assert abs(scorer.partial_score(0.60) - 0.5) < 0.01

    # f1=0.65 → score ≈ 0.75
    assert abs(scorer.partial_score(0.65) - 0.75) < 0.01


# ── Tests : score — fallback si unavailable ────────────────────────────────────

def test_score_returns_zeros_when_unavailable(scorer):
    """score() doit retourner des zéros si bert-score n'est pas disponible."""
    scorer._available = False

    result = scorer.score("une réponse", "la réponse attendue")
    assert result.f1 == 0.0
    assert result.precision == 0.0
    assert result.recall == 0.0
    assert result.model == "unavailable"


def test_score_returns_zeros_for_empty_candidate(scorer):
    """score() doit retourner des zéros si le candidat est vide."""
    scorer._available = True

    result = scorer.score("", "réponse de référence")
    assert result.f1 == 0.0


# ── Tests : score — comportement avec bert_score mocké ────────────────────────

def test_score_calls_bert_score_correctly(scorer):
    """score() doit appeler bert_score.score avec les bons arguments."""
    scorer._available = True

    mock_tensor_p = MagicMock()
    mock_tensor_p.__getitem__ = lambda self, i: MagicMock(__float__=lambda s: 0.85)
    mock_tensor_r = MagicMock()
    mock_tensor_r.__getitem__ = lambda self, i: MagicMock(__float__=lambda s: 0.80)
    mock_tensor_f1 = MagicMock()
    mock_tensor_f1.__getitem__ = lambda self, i: MagicMock(__float__=lambda s: 0.82)

    # Simuler import bert_score + retour de la fonction score()
    import torch
    t_p  = torch.tensor([0.85])
    t_r  = torch.tensor([0.80])
    t_f1 = torch.tensor([0.82])

    fake_bert_score = types.ModuleType("bert_score")
    fake_bert_score.score = MagicMock(return_value=(t_p, t_r, t_f1))

    with patch.dict(sys.modules, {"bert_score": fake_bert_score}):
        result = scorer.score("ma réponse", "réponse attendue")

    fake_bert_score.score.assert_called_once_with(
        cands=["ma réponse"],
        refs=["réponse attendue"],
        model_type="roberta-large",
        lang="fr",
        verbose=False,
    )
    assert abs(result.f1 - 0.82) < 0.001
    assert abs(result.precision - 0.85) < 0.001
    assert abs(result.recall - 0.80) < 0.001


def test_score_handles_exception_gracefully(scorer):
    """score() doit retourner des zéros si bert_score.score lève une exception."""
    scorer._available = True

    fake_bert_score = types.ModuleType("bert_score")
    fake_bert_score.score = MagicMock(side_effect=RuntimeError("CUDA out of memory"))

    with patch.dict(sys.modules, {"bert_score": fake_bert_score}):
        result = scorer.score("réponse étudiant", "réponse référence")

    assert result.f1 == 0.0
    assert result.model == "error"


# ── Tests : endpoint FastAPI /score ───────────────────────────────────────────

@pytest.fixture()
def client():
    """Client de test FastAPI."""
    from fastapi.testclient import TestClient
    from app.main import app
    return TestClient(app)


def test_score_endpoint_returns_200_when_unavailable(client):
    """
    Le endpoint /score doit retourner 200 même si bert-score n'est pas installé.
    Vérifie la résilience (fallback gracieux).
    """
    with patch("app.main.BertScorer.get_instance") as mock_get:
        mock_scorer = MagicMock()
        mock_scorer.score.return_value = MagicMock(
            f1=0.0, precision=0.0, recall=0.0, model="unavailable"
        )
        mock_scorer.partial_score.return_value = 0.0
        mock_get.return_value = mock_scorer

        response = client.post("/score", json={
            "candidate": "photosynthèse",
            "reference": "La photosynthèse est la synthèse de matière organique par les plantes.",
        })

    assert response.status_code == 200
    data = response.json()
    assert "f1" in data
    assert "label" in data
    assert data["label"] == "INCORRECT"


def test_score_endpoint_returns_correct_label_for_high_f1(client):
    """Un F1 élevé (≥0.70) doit produire le label CORRECT."""
    from app.bert_scorer import BertScoreResult

    with patch("app.main.BertScorer.get_instance") as mock_get:
        mock_scorer = MagicMock()
        mock_scorer.score.return_value = BertScoreResult(
            f1=0.85, precision=0.87, recall=0.83, model="roberta-large"
        )
        mock_scorer.partial_score.return_value = 1.0
        mock_get.return_value = mock_scorer

        response = client.post("/score", json={
            "candidate": "La photosynthèse produit du glucose.",
            "reference": "La photosynthèse est la synthèse de matière organique.",
            "question_id": "q-001",
        })

    assert response.status_code == 200
    data = response.json()
    assert data["label"] == "CORRECT"
    assert data["partial_score"] == 1.0
    assert data["question_id"] == "q-001"


def test_score_endpoint_returns_partiel_label_for_mid_f1(client):
    """Un F1 entre 0.50 et 0.70 doit produire le label PARTIEL."""
    from app.bert_scorer import BertScoreResult

    with patch("app.main.BertScorer.get_instance") as mock_get:
        mock_scorer = MagicMock()
        mock_scorer.score.return_value = BertScoreResult(
            f1=0.60, precision=0.62, recall=0.58, model="roberta-large"
        )
        mock_scorer.partial_score.return_value = 0.5
        mock_get.return_value = mock_scorer

        response = client.post("/score", json={
            "candidate": "photosynthèse = lumière + CO2",
            "reference": "La photosynthèse transforme la lumière en énergie chimique.",
        })

    assert response.status_code == 200
    data = response.json()
    assert data["label"] == "PARTIEL"
    assert 0.0 < data["partial_score"] < 1.0


def test_batch_score_endpoint(client):
    """Le endpoint /score/batch doit traiter plusieurs items."""
    from app.bert_scorer import BertScoreResult

    with patch("app.main.BertScorer.get_instance") as mock_get:
        mock_scorer = MagicMock()
        mock_scorer._check_available.return_value = True
        mock_scorer.score.return_value = BertScoreResult(
            f1=0.75, precision=0.78, recall=0.72, model="roberta-large"
        )
        mock_scorer.partial_score.return_value = 1.0
        mock_get.return_value = mock_scorer

        response = client.post("/score/batch", json={
            "items": [
                {
                    "candidate": "réponse 1",
                    "reference": "référence 1",
                    "question_id": "q1",
                },
                {
                    "candidate": "réponse 2",
                    "reference": "référence 2",
                    "question_id": "q2",
                },
            ]
        })

    assert response.status_code == 200
    data = response.json()
    assert data["total_items"] == 2
    assert len(data["results"]) == 2
    assert data["results"][0]["question_id"] == "q1"
    assert data["results"][1]["question_id"] == "q2"


def test_batch_score_empty_items(client):
    """Un batch vide doit retourner une liste vide sans erreur."""
    with patch("app.main.BertScorer.get_instance") as mock_get:
        mock_scorer = MagicMock()
        mock_scorer._check_available.return_value = True
        mock_get.return_value = mock_scorer

        response = client.post("/score/batch", json={"items": []})

    assert response.status_code == 200
    data = response.json()
    assert data["total_items"] == 0
    assert data["results"] == []
