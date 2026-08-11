# Rapport Qualité IA — QuizGen

> Généré automatiquement le 2026-08-11 08:22

## Résumé global

| Métrique                         | Valeur         |
|----------------------------------|----------------|
| Documents évalués                | 5              |
| Questions générées (total)       | 50          |
| **BLEU-4 moyen**                 | **0.1495**       |
| **ROUGE-L moyen**                | **0.2469**     |
| **BERTScore F1 moyen**           | **0.7252**     |
| Temps génération moyen           | 12.2s   |
| Temps génération P95             | 15.9s   |

## Seuils d'acceptation

| Métrique       | Seuil acceptable | Résultat | Statut |
|----------------|-----------------|---------|--------|
| BLEU-4         | ≥ 0.10          | 0.1495  | ✅ OK |
| ROUGE-L        | ≥ 0.20          | 0.2469 | ✅ OK |
| BERTScore F1   | ≥ 0.60          | 0.7252 | ✅ OK |
| Temps ≤ 15s    | P95 ≤ 15s       | 15.9s   | ❌ KO |

## Résultats par document

| Document                | Qref | Qgen | BLEU-4 | ROUGE-L | BERTScore F1 | Temps (s) |
|-------------------------|------|------|--------|---------|--------------|-----------|
| document_01.pdf         |   10 |   10 | 0.1887 | 0.1855  | 0.6605       |      11.5 |
| document_02.pdf         |   10 |   10 | 0.2052 | 0.3289  | 0.7963       |       9.7 |
| document_03.pdf         |   10 |   10 | 0.1517 | 0.1866  | 0.6481       |      15.3 |
| document_04.pdf         |   10 |   10 | 0.0845 | 0.2237  | 0.7430       |      15.9 |
| document_05.pdf         |   10 |   10 | 0.1175 | 0.3096  | 0.7781       |       8.6 |

## Analyse et recommandations

### Points forts
- Le pipeline NLP génère des questions pertinentes sur les documents techniques.
- Le BERTScore F1 reflète une bonne capture sémantique du contenu source.

### Points d'amélioration
- Augmenter la diversité des types de questions (exercices pratiques).
- Affiner les prompts Ollama pour améliorer la précision BLEU-4.
- Mettre en cache les embeddings de documents fréquemment utilisés.

### Performance
- Temps P95 de 15.9s (KO - depasse objectif de 15s - optimisation requise).

---
_Rapport généré par `evaluation/evaluate.py` — QuizGen PFE MIAGE 2024/2025_