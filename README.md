# QuizGen 🎓

**Génération automatique de quiz pédagogiques par IA**\
PFE Master MIAGE 2024/2025 — Université Mohammed V

[![CI](https://github.com/Abdelhak-elgo/QuizGen/actions/workflows/ci.yml/badge.svg)](https://github.com/Abdelhak-elgo/QuizGen/actions/workflows/ci.yml)
[![Quality Gate](https://img.shields.io/badge/SonarQube-Quality%20Gate-brightgreen)](https://github.com/Abdelhak-elgo/QuizGen)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)](https://spring.io/projects/spring-boot)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688)](https://fastapi.tiangolo.com)

***

## 🏗️ Architecture

| Couche           | Technologie                                | Port        |
| ---------------- | ------------------------------------------ | ----------- |
| Frontend         | React 18 + TypeScript + TailwindCSS        | 3000        |
| Backend API      | Spring Boot 3.3 + Java 21                  | 8080        |
| Microservice NLP | FastAPI + Python 3.11                      | 8000        |
| IAM              | Keycloak 25.0                              | 8180        |
| Base de données  | PostgreSQL 16                              | 5432        |
| Cache / Broker   | Redis 7                                    | 6379        |
| Stockage PDF     | MinIO                                      | 9000 / 9001 |
| LLM (hors ligne) | Ollama (Mistral, Qwen…)                    | 11434       |
| LLM (en ligne)   | API Anthropic / OpenAI / Mistral / NVIDIA… | —           |

Le LLM est configurable : **hors ligne** (Ollama local, aucune donnée ne quitte
la machine) ou **en ligne** (API distante, génération en quelques secondes).
Voir [Choix du LLM](#-choix-du-llm--hors-ligne-ou-en-ligne).

***

## 🚀 Démarrage rapide

### Prérequis

* Docker Desktop ≥ 24 + Docker Compose ≥ 2.20 (profils + `depends_on.required`)
* 8 Go RAM minimum — en mode hors ligne, Mistral nécessite \~4 Go en plus ;
  le mode en ligne est recommandé sur une machine modeste
* Git

### 1. Cloner et configurer

```Shell
git clone https://github.com/Abdelhak-elgo/QuizGen.git
cd QuizGen
cp .env.example .env
# Éditez .env et remplacez tous les CHANGE_ME_* par des mots de passe forts
```

### 2. Lancer tous les services

```Shell
docker compose up -d --build
```

Vérifier que tout est sain :

```Shell
docker compose ps
# Tous les services doivent afficher "healthy" ou "running"
```

### 3. (Mode hors ligne uniquement) Modèle Ollama

Avec `COMPOSE_PROFILES=offline`, le conteneur `ollama-init` télécharge
automatiquement `OLLAMA_MODEL` au premier démarrage (\~4 Go pour Mistral).
En mode en ligne, rien à télécharger.

### 4. Accès aux services

| Service          | URL                                            | Credentials                                 |
| ---------------- | ---------------------------------------------- | ------------------------------------------- |
| Frontend QuizGen | <http://localhost:3000>                        | Comptes de test ci-dessous                  |
| Spring Boot API  | <http://localhost:8080/api/v1>                 | JWT Keycloak                                |
| Swagger UI       | <http://localhost:8080/api/v1/swagger-ui.html> | —                                           |
| Keycloak Console | <http://localhost:8180>                        | admin / (KC\_ADMIN\_PASS dans .env)         |
| MinIO Console    | <http://localhost:9001>                        | minioadmin / (MINIO\_SECRET\_KEY dans .env) |
| FastAPI NLP docs | <http://localhost:8000/docs>                   | —                                           |

***

## 🤖 Choix du LLM : hors ligne ou en ligne

Tout se règle dans `.env` (voir `.env.example`), puis `docker compose up -d` :

| Variable           | Rôle                                                                                                               |
| ------------------ | ------------------------------------------------------------------------------------------------------------------ |
| `LLM_MODE`         | `offline` (Ollama) ou `online` (API distante)                                                                      |
| `LLM_MODEL`        | Modèle en ligne, ex. `claude-haiku-4-5-20251001`, `gpt-4o-mini`                                                    |
| `LLM_API_KEY`      | Clé de l'API en ligne (jamais commitée : `.env` est ignoré par git)                                                |
| `LLM_API_BASE_URL` | Point d'accès : détermine le protocole utilisé (tableau ci-dessous)                                                |
| `COMPOSE_PROFILES` | `offline` démarre les conteneurs Ollama ; toute autre valeur (ex. `online`) les laisse arrêtés et économise la RAM |

| Fournisseur               | `LLM_API_BASE_URL`                    | Protocole                    |
| ------------------------- | ------------------------------------- | ---------------------------- |
| Anthropic                 | `https://api.anthropic.com`           | API Messages Anthropic       |
| OpenAI                    | `https://api.openai.com/v1`           | `/chat/completions` (OpenAI) |
| Mistral                   | `https://api.mistral.ai/v1`           | `/chat/completions` (OpenAI) |
| NVIDIA NIM                | `https://integrate.api.nvidia.com/v1` | `/chat/completions` (OpenAI) |
| Autre (Groq, OpenRouter…) | point d'accès compatible OpenAI       | `/chat/completions` (OpenAI) |

Exemple en ligne (Claude Haiku 4.5) :

```Shell
LLM_MODE=online
LLM_MODEL=claude-haiku-4-5-20251001
LLM_API_KEY=sk-ant-...
LLM_API_BASE_URL=https://api.anthropic.com
COMPOSE_PROFILES=online
```

> En mode en ligne, le texte des documents est envoyé au fournisseur choisi.
> Une clé invalide ou un modèle inconnu fait échouer la génération immédiatement
> (le quiz passe en « Échec »), sans nouvel essai.

**Ollama natif (GPU).** Ollama installé directement sur Windows/macOS utilise le
GPU et évite la mémoire limitée de la VM Docker : `LLM_MODE=offline`,
`OLLAMA_URL=http://host.docker.internal:11434` et `COMPOSE_PROFILES=` (vide) pour
ne pas démarrer le conteneur Ollama.

`GET http://localhost:8000/health` indique le fournisseur actif (`llm_mode`,
`llm_protocol`, `llm_model`) — jamais la clé.

***

## 📝 Cycle de vie d'un quiz

| Statut      | Signification                                                                |
| ----------- | ---------------------------------------------------------------------------- |
| `DRAFT`     | Génération en cours (asynchrone : l'enseignant peut quitter la page)         |
| `REVIEWING` | Questions générées, à relire / modifier avant publication                    |
| `PUBLISHED` | Publié : utilisable dans des sessions, exports SCORM / Moodle                |
| `FAILED`    | Génération échouée, sans question exploitable, ou sans résultat après 40 min |

* Si le LLM rend moins de questions que demandé (filtre qualité, doublons), le
  pipeline relance jusqu'à 2 tours d'appels complémentaires sur d'autres segments
  du document.
* Un quiz `FAILED` peut être **relancé** (mêmes paramètres) ou complété **à la
  main** (« Ajouter une question ») depuis l'éditeur.
* Tout quiz peut être **supprimé** depuis l'éditeur, y compris pendant sa
  génération — sauf s'il est déjà utilisé par une session (les copies des
  étudiants seraient perdues).
* Taille maximale d'un PDF : **20 Mo**.

***

## 👥 Sessions et étudiants assignés

À la création d'une session (ou ensuite depuis son détail), l'enseignant peut
**assigner des étudiants** :

* ils voient la session dans « Mes sessions » sur leur tableau de bord et la
  commencent en un clic, sans identifiant ni code ;
* une session avec des étudiants assignés leur est **réservée** ;
* sans étudiant assigné, la session reste ouverte à tout étudiant disposant de
  son identifiant (et du code d'accès éventuel).

Un étudiant n'apparaît dans la liste qu'après sa première connexion à QuizGen.

***

## 🔐 Authentification (Keycloak)

**Realm** : `quizgen`\
**Utilisateurs de test** :

| Email                                                                                                                          | Mot de passe | Rôle       |
| ------------------------------------------------------------------------------------------------------------------------------ | ------------ | ---------- |
| <admin@quizgen.ma>                                                                                                             | admin123     | ADMIN      |
| [teacher@quizgen.ma](http://localhost:8180/admin/master/console/#/quizgen/users/b470952a-0a46-4bcc-b9a6-9d877dc70941/settings) | teacher123   | ENSEIGNANT |
| [student@quizgen.ma](http://localhost:8180/admin/master/console/#/quizgen/users/c7b2661b-1b87-4005-946e-cef76171a481/settings) | student123   | ETUDIANT   |

Après connexion avec le compte administrateur, l'interface d'administration
est disponible sur `http://localhost:3000/admin`. Elle permet actuellement de
consulter et paginer l'annuaire des utilisateurs. La modification des rôles,
l'activation et la suppression nécessitent encore des endpoints backend.

### Obtenir un token JWT

Le client public `quizgen-frontend` n'autorise pas le grant `password`
(« Direct access grants » désactivé) : connectez-vous via l'application ou
Swagger UI, ou activez temporairement ce grant sur un client de test dans la
console Keycloak.

***

## 🧪 Tests avec Postman

### Import de la collection

1. Ouvrir **Postman**
2. Cliquer **Import** → sélectionner `postman/quizgen.postman_collection.json`
3. Importer aussi l'environnement : `postman/quizgen.postman_environment.json`
4. Sélectionner l'environnement **QuizGen Local** en haut à droite

### Scénario de test bout-en-bout

Exécuter les requêtes dans cet ordre :

```
1. Auth > Get Token (Enseignant)
   → Le token JWT est automatiquement stocké dans access_token

2. Users > POST /users/sync
   → Synchronise le profil Keycloak en BDD
   → Stocke current_user_id

3. Users > GET /users/me
   → Vérifie le profil

4. Documents > POST /documents/upload
   → Sélectionner un PDF dans Body > form-data > file
   → Stocke document_id

5. Documents > GET /documents
   → Liste les documents uploadés

6. Documents > GET /documents/{id}
   → Détail du document

7. Documents > GET /documents/{id}/download-url
   → URL présignée MinIO (valable 1h)

8. Documents > DELETE /documents/{id}
   → Supprime le document

9. Documents > GET /documents/{id} [après suppression]
   → Vérifie le 404
```

### Tests automatisés (Collection Runner)

```Shell
# Via Newman (CLI Postman)
npm install -g newman
newman run postman/quizgen.postman_collection.json \
  --environment postman/quizgen.postman_environment.json \
  --reporters cli,json \
  --reporter-json-export postman/results.json
```

***

## 📋 API Endpoints

### Users

```
POST   /api/v1/users/sync      # Synchroniser utilisateur Keycloak → BDD
GET    /api/v1/users/me        # Mon profil
GET    /api/v1/users           # Liste paginée (ADMIN)
GET    /api/v1/users/students  # Étudiants actifs (ENSEIGNANT/ADMIN — assignation aux sessions)
GET    /api/v1/users/{id}      # Détail (ADMIN)
```

### Documents

```
POST   /api/v1/documents/upload             # Upload PDF (max 20 Mo, ENSEIGNANT/ADMIN)
GET    /api/v1/documents                    # Mes documents (paginé)
GET    /api/v1/documents/{id}               # Détail
GET    /api/v1/documents/{id}/download-url  # URL présignée MinIO (1h)
DELETE /api/v1/documents/{id}               # Supprimer
```

### Quiz

```
POST   /api/v1/quizzes                    # Créer un quiz → génération NLP asynchrone (DRAFT)
GET    /api/v1/quizzes                    # Mes quiz (rafraîchit les générations en cours)
GET    /api/v1/quizzes/{id}               # Détail — propriétaire/admin ; étudiant : réponses masquées avant soumission
POST   /api/v1/quizzes/{id}/regenerate    # Relancer la génération d'un quiz FAILED
PUT    /api/v1/quizzes/{id}/questions     # Éditer / ajouter des questions, publier (publish=true)
DELETE /api/v1/quizzes/{id}               # Supprimer (409 si utilisé par une session)
GET    /api/v1/quizzes/{id}/export/scorm  # Export SCORM 2004 (quiz publié)
GET    /api/v1/quizzes/{id}/export/moodle # Export Moodle XML (quiz publié)
```

### Sessions

```
POST   /api/v1/sessions                    # Créer une session (studentIds optionnel)
GET    /api/v1/sessions                    # Mes sessions (enseignant)
GET    /api/v1/sessions/assigned           # Sessions assignées à l'étudiant connecté
GET    /api/v1/sessions/{id}               # Détail (avec étudiants assignés)
PUT    /api/v1/sessions/{id}/participants  # Assigner des étudiants (liste vide = session ouverte)
POST   /api/v1/sessions/{id}/join          # Rejoindre (étudiant) → tentative
DELETE /api/v1/sessions/{id}/cancel        # Annuler
```

***

## 🔄 CI/CD

### GitHub Actions

Le pipeline CI s'exécute automatiquement sur chaque push vers `main` ou `develop` :

| Job              | Description                                        | Déclencheur       |
| ---------------- | -------------------------------------------------- | ----------------- |
| `build`          | Compile + tests unitaires Maven + rapport Surefire | push / PR         |
| `sonar`          | Analyse SonarQube (Quality Gate)                   | push main/develop |
| `docker-backend` | Build + push image Spring Boot → GHCR              | push              |
| `docker-nlp`     | Build + push image FastAPI → GHCR                  | push              |

### Secrets GitHub requis

Configurer dans **Settings > Secrets and variables > Actions** :

| Secret           | Description                                                      |
| ---------------- | ---------------------------------------------------------------- |
| `SONAR_TOKEN`    | Token d'authentification SonarQube                               |
| `SONAR_HOST_URL` | URL du serveur SonarQube (ex: `https://sonarqube.mondomaine.ma`) |

> Les secrets `GITHUB_TOKEN` et accès GHCR sont fournis automatiquement par GitHub Actions.

***

## 🧪 Tests

```Shell
# Tests unitaires Spring Boot (JUnit 5 + Mockito)
cd backend && mvn test

# Tests NLP (Python)
cd nlp-service && pytest tests/ -v --cov=app

# Régénérer les clients API (React Query + Zod) après modification de lib/api-spec/openapi.yaml
pnpm --filter @workspace/api-spec run codegen

# Tests Postman via Newman
newman run postman/quizgen.postman_collection.json \
  -e postman/quizgen.postman_environment.json
```

***

## 📁 Structure du projet

```
QuizGen/
├── .github/
│   └── workflows/
│       ├── ci.yml              # Pipeline CI principal (build + sonar + docker)
│       └── pr-checks.yml       # Vérifications rapides sur les PR
├── postman/
│   ├── quizgen.postman_collection.json    # Collection Postman complète
│   └── quizgen.postman_environment.json  # Environnement "QuizGen Local"
├── docker-compose.yml
├── .env.example
├── init-db/
│   └── 01-schema.sql           # Schéma PostgreSQL complet (7 tables, vues, index)
├── keycloak/
│   └── realm-export.json       # Realm Keycloak pré-configuré
├── lib/
│   ├── api-spec/openapi.yaml   # Contrat API — source des clients générés (orval)
│   └── api-client-react/       # Hooks React Query générés
├── scripts/
│   └── setup-ollama.sh         # Pull du modèle Mistral
├── backend/                    # Spring Boot API (Java 21)
│   ├── pom.xml
│   ├── Dockerfile
│   ├── sonar-project.properties
│   └── src/
│       ├── main/java/ma/quizgen/
│       │   ├── config/         # MinioConfig, OpenApiConfig
│       │   ├── security/       # KeycloakRoleConverter, SecurityConfig, CurrentUser
│       │   ├── entity/         # User, Document + enums
│       │   ├── repository/     # Spring Data JPA
│       │   ├── dto/            # Records Java (UserDto, DocumentDto…)
│       │   ├── service/        # Interfaces + impl (UserService, DocumentService, MinioService)
│       │   ├── controller/     # UserController, DocumentController
│       │   └── exception/      # GlobalExceptionHandler
│       └── test/               # JUnit 5 + Mockito
├── nlp-service/                # FastAPI + Celery NLP (Python 3.11)
│   ├── Dockerfile
│   ├── requirements.txt
│   └── app/
│       ├── main.py             # API FastAPI (/generate, /status, /health, scoring)
│       ├── tasks.py            # Pipeline Celery : chunking, RAG, Bloom, CoT, compléments
│       ├── llm_client.py       # Prompts CoT + parsing des questions
│       └── llm_providers.py    # Ollama (offline) / Anthropic / API compatibles OpenAI (online)
└── artifacts/frontend/         # React + Vite — application web multi-rôles
```

***

## 🗺️ Plan d'implémentation

| Phase   | Contenu                                         | Statut    |
| ------- | ----------------------------------------------- | --------- |
| Phase 1 | Spring Boot CRUD Users + Documents + MinIO      | ✅ Fait    |
| Phase 2 | CI/CD + Postman                                 | ✅ Fait    |
| Phase 3 | FastAPI NLP : SpaCy + KeyBERT + Mistral         | ✅ Fait    |
| Phase 4 | Spring Boot Quiz, Sessions, Attempts, Analytics | ✅ Fait    |
| Phase 5 | React Frontend multi-rôles                      | ✅ Fait    |
| Phase 6 | BERTScore + Exports SCORM/Moodle                | ✅ Fait    |
| Phase 7 | Qualité + Kubernetes + Monitoring               | ⏳ À faire |

