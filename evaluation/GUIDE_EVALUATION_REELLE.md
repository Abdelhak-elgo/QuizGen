# Guide — réaliser l'évaluation réelle (section 6.3 du rapport)

Ce guide décrit comment produire des résultats **mesurés** pour la section 6.3,
à la place des valeurs simulées du mode `--dry-run`.

> **Pourquoi :** le fichier `rapport_qualite_ia.md` actuel a été produit avec
> `--dry-run`, qui tire les scores au hasard (`random.seed(42)`). Les tableaux 16
> et 17 et les figures 29 et 30 du rapport reprennent exactement ces valeurs.

***

## Étape 1 — Constituer le corpus (≈ 30 min)

Choisir **5 documents PDF** de cours :

| Critère   | Règle                                                                                       |
| --------- | ------------------------------------------------------------------------------------------- |
| Texte     | Sélectionnable (pas de scan sans OCR)                                                       |
| Taille    | 5 à 30 pages                                                                                |
| Diversité | Au moins 2 matières différentes (ex. processus métier, urbanisation du SI, base de données) |
| Langue    | Français                                                                                    |

Arborescence attendue (le dossier `corpus/*/` n'est pas versionné) :

```
evaluation/corpus/
  document_01/
    document.pdf          ← le cours, renommé
    annotations.json      ← copie remplie de annotations.modele.json
  document_02/
  ...
  document_05/
```

## Étape 2 — Rédiger les questions de référence (≈ 1 h par document)

Copier `annotations.modele.json` dans chaque dossier et le remplir.

**Règles indispensables pour que l'évaluation soit valable :**

1. **Rédiger les références AVANT d'avoir vu les questions générées** pour ce
   document. Sinon les références imitent la machine et les scores sont gonflés.
2. Les questions sont rédigées par un humain : vous, idéalement relues par
   l'enseignant du cours. Ne pas les faire écrire par une IA.
3. Une question = une notion du document. Indiquer la page (`page`).
4. Énoncé complet, compréhensible sans le document, sans « selon le texte ».
5. Même répartition que la génération : **5 QCM + 5 questions ouvertes**.
6. Répartition Bloom conseillée par document : 3 MEMORISATION,
   3 COMPREHENSION, 2 APPLICATION, 2 ANALYSE.
7. QCM : 4 options, une seule correcte, recopiée **à l'identique** dans
   `correct_answers`. Distracteurs plausibles, pas absurdes.
8. Question ouverte : réponse modèle de 1 à 3 phrases, tirée du document.

Valeurs autorisées pour `bloom_level` : `MEMORISATION`, `COMPREHENSION`,
`APPLICATION`, `ANALYSE`, `EVALUATION`, `CREATION`.

Contrôle rapide d'un fichier :

```Shell
python -c "import json,sys; d=json.load(open(sys.argv[1],encoding='utf-8')); \
qs=d['questions']; print(len(qs),'questions'); \
print([q['id'] for q in qs if not q['question'].strip()] or 'aucune question vide')" \
evaluation/corpus/document_01/annotations.json
```

## Étape 3 — Script corrigé ✅

Les problèmes ci-dessous sont **corrigés** dans `evaluate.py`. Le script ne
bascule plus jamais en simulation en silence (corpus vide = erreur), répète
la mesure (`--runs`, défaut 3), note le commit, le modèle et la machine, et
écrit aussi `resultats_evaluation.json` (paires appariées incluses, pour
vérifier chaque appariement à la main). Problèmes corrigés :

| Problème                                                                      | Correction                                                                               |
| ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Appelle `POST /quizzes/generate` (n'existe plus)                              | `POST /quizzes` avec `documentId`, `title`, `nbQuestions`, `questionTypes`, `difficulty` |
| Considère `DRAFT` comme terminé (c'est l'état « génération en cours »)        | Attendre `REVIEWING` (ou `FAILED`), délai max ≈ 15 min                                   |
| Compare la question générée n°i à la référence n°i (ordre arbitraire)         | Apparier chaque question générée à la référence la plus proche (BERTScore)               |
| BERTScore avec `roberta-large` (modèle anglais)                               | Modèle multilingue : `xlm-roberta-large`                                                 |
| Jeton par mot de passe refusé (direct grant désactivé sur `quizgen-frontend`) | Activer le direct grant en local **ou** passer un jeton copié depuis le navigateur       |
| Temps mesuré upload compris                                                   | Mesurer de la soumission à `REVIEWING`                                                   |

## Étape 4 — Lancer l'évaluation

```Shell
docker compose up -d                      # stack complète
pip install sacrebleu rouge-score bert-score requests

pip install scipy                         # appariement optimal (sinon glouton)

python evaluation/evaluate.py \
  --corpus      evaluation/corpus \
  --api         http://localhost:8080/api/v1 \
  --token       "$QUIZGEN_API_TOKEN" \
  --n-questions 10 \
  --runs        3 \
  --llm         "Mistral 7B via Ollama (CPU)" \
  --output      evaluation/rapport_qualite_ia.md \
  --json        evaluation/resultats_evaluation.json
```

Le jeton : se connecter sur l'application avec le compte enseignant, ouvrir les
outils développeur (F12) → onglet Réseau → une requête `/api/v1/...` → en-tête
`Authorization: Bearer …` → copier la partie après `Bearer` dans
`QUIZGEN_API_TOKEN`. Il expire au bout de quelques minutes : le copier juste
avant de lancer (ou augmenter la durée de vie du jeton dans Keycloak en local).

**Mesurer aussi le taux de rejet réel du contrôle qualité** (slide « contrôle
de la sortie ») : pendant l'exécution, le worker journalise ses statistiques.

```Shell
docker compose logs nlp-worker | grep "Pipeline v2 terminé"
# → questions_generated_raw, questions_accepted, questions_rejected, questions_final
```

Taux de rejet = rejected / generated_raw, à reporter par document.

**À noter pendant l'exécution** (à reporter dans le rapport) : date, commit Git
(`git rev-parse --short HEAD`), fournisseur et modèle LLM utilisés (Ollama/Mistral
ou fournisseur en ligne), machine (CPU/GPU, RAM), nombre de questions réellement
produites par document.

Conseil : lancer **2 ou 3 fois** et reporter la moyenne et l'écart, car la
génération par LLM n'est pas déterministe.

## Étape 5 — Interpréter

| Métrique     | Ce qu'elle mesure                         | Seuil fixé a priori |
| ------------ | ----------------------------------------- | ------------------- |
| BLEU-4       | Recouvrement de n-grammes (forme)         | ≥ 0,10              |
| ROUGE-L      | Plus longue sous-séquence commune (forme) | ≥ 0,20              |
| BERTScore F1 | Proximité de sens                         | ≥ 0,60              |
| Temps P95    | Durée de génération (95e centile)         | ≤ 15 s              |

Avec 5 documents, le « P95 » est en pratique le maximum : le dire dans le texte.
Si un seuil n'est pas atteint, l'écrire tel quel et l'analyser : c'est accepté,
une valeur inventée ne l'est pas.

***

## Modèle de rédaction pour la section 6.3

Remplacer chaque `[…]` par la valeur mesurée.

> L'évaluation a porté sur un corpus de \[5] documents pédagogiques de \[matières],
> comptant de \[N] à \[M] pages. Pour chacun, \[dix] questions de référence ont été
> rédigées manuellement par \[l'auteur / l'auteur et l'enseignant du cours] avant
> toute génération, selon une répartition fixée à l'avance (\[5] QCM, \[5] questions
> ouvertes ; niveaux de Bloom de la mémorisation à l'analyse). Le pipeline a
> ensuite généré \[dix] questions par document, soit \[X] questions produites sur
> \[50] demandées. L'expérience a été conduite le \[date], sur la version \[commit]
> du dépôt, avec le modèle \[Mistral 7B via Ollama / …], sur \[matériel] ;
> elle a été répétée \[n] fois.
>
> Chaque question générée a été appariée à la question de référence la plus proche
> sémantiquement, puis trois métriques ont été calculées. \[Garder ici votre
> paragraphe actuel sur BLEU-4, ROUGE-L et BERTScore : il est juste.] Le BERTScore
> est calculé avec le modèle multilingue \[xlm-roberta-large], adapté au français.
>
> Le tableau 16 confronte les moyennes aux seuils fixés a priori. BLEU-4 vaut
> \[x,xxx], ROUGE-L \[x,xxx] et BERTScore F1 \[x,xxx]. \[Seuils atteints / non
> atteints : …]. Le temps de génération moyen est de \[xx] s ; sa valeur maximale,
> qui tient lieu de 95e centile sur \[5] documents, est de \[xx] s, \[en deçà / au-delà]
> de l'exigence NF-01. \[Analyse : quel document est le moins bon et pourquoi —
> longueur, structure, contenu formel…]
>
> Ces résultats doivent être lus avec prudence : la taille du corpus est réduite,
> les références reflètent les choix d'un seul annotateur, et les métriques
> automatiques ne jugent ni la justesse pédagogique ni la qualité des distracteurs ;
> c'est l'objet du protocole d'évaluation humaine décrit en 6.4.

## Liste de contrôle avant de reporter les chiffres

* [ ] `rapport_qualite_ia.md` ne contient pas « dry-run » et date du jour de l'expérience
* [ ] Les 5 `annotations.json` existent et ont été rédigés avant la génération
* [ ] Les chiffres du tableau 16, du tableau 17 et des figures 29-30 sont identiques à ceux du rapport généré
* [ ] Le modèle LLM cité dans le texte est celui réellement utilisé
* [ ] Les sections 6.5 (limites) et la conclusion sont mises à jour si les conclusions changent

