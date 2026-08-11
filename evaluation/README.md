# Évaluation Qualité IA — QuizGen

Ce dossier contient le script d'évaluation et le rapport de qualité des
questions générées par le pipeline NLP QuizGen.

## Structure

```
evaluation/
  evaluate.py              Script principal d'évaluation
  rapport_qualite_ia.md   Rapport généré automatiquement
  corpus/                  Corpus annoté (non versionné — voir .gitignore)
    document_01/
      document.pdf
      annotations.json
    document_02/
      ...
```

## Format des annotations

Chaque `annotations.json` suit ce schéma :

```json
{
  "source": "Cours Informatique L3 — Réseaux",
  "language": "fr",
  "questions": [
    {
      "id": "q1",
      "type": "QCM",
      "question": "Quelle couche du modèle OSI gère le routage IP ?",
      "correct_answers": ["La couche réseau (couche 3)"],
      "options": [
        "Couche physique",
        "Couche liaison",
        "Couche réseau",
        "Couche transport"
      ]
    },
    {
      "id": "q2",
      "type": "OUVERTE",
      "question": "Expliquez le principe du protocole TCP.",
      "correct_answers": [
        "TCP est un protocole orienté connexion qui garantit la livraison fiable des données via un mécanisme d'acquittement et de retransmission."
      ]
    }
  ]
}
```

## Utilisation

### Mode démonstration (sans stack Docker)
```bash
cd /path/to/quizgen
pip install sacrebleu rouge-score bert-score requests
python evaluation/evaluate.py --dry-run
```

### Mode complet (avec stack en cours d'exécution)
```bash
# 1. Obtenir un token Keycloak
export QUIZGEN_API_TOKEN=$(curl -s \
  http://localhost:8180/realms/quizgen/protocol/openid-connect/token \
  -d "grant_type=password&client_id=quizgen-frontend&username=admin@quizgen.ma&password=Admin1234!" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# 2. Lancer l'évaluation
python evaluation/evaluate.py \
  --corpus   evaluation/corpus \
  --api      http://localhost:8080/api/v1 \
  --token    "$QUIZGEN_API_TOKEN" \
  --n-questions 10 \
  --output   evaluation/rapport_qualite_ia.md

# 3. Consulter le rapport
cat evaluation/rapport_qualite_ia.md
```

### Via GitHub Actions
L'évaluation est déclenchée manuellement via `workflow_dispatch` dans le
job `ai-quality` du workflow `.github/workflows/e2e.yml`.

## Métriques et seuils

| Métrique      | Description                        | Seuil minimal |
|---------------|------------------------------------|---------------|
| BLEU-4        | Précision n-gram jusqu'à 4-gram   | ≥ 0.10        |
| ROUGE-L       | Rappel séquence la plus longue    | ≥ 0.20        |
| BERTScore F1  | Similarité sémantique             | ≥ 0.60        |
| Temps P95     | Temps de génération               | ≤ 15s         |

> **Note :** Les seuils sont délibérément conservateurs pour la génération
> automatique de questions (tâche difficile). Le BERTScore est la métrique
> principale car il capture mieux la qualité sémantique que les métriques
> basées sur le chevauchement lexical.
