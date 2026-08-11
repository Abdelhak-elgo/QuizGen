# QuizGen 🎓

**Génération automatique de quiz pédagogiques par IA**  
PFE Master MIAGE 2024/2025 — Université Mohammed V

[![CI](https://github.com/Abdelhak-elgo/QuizGen/actions/workflows/ci.yml/badge.svg)](https://github.com/Abdelhak-elgo/QuizGen/actions/workflows/ci.yml)
[![Quality Gate](https://img.shields.io/badge/SonarQube-Quality%20Gate-brightgreen)](https://github.com/Abdelhak-elgo/QuizGen)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)](https://spring.io/projects/spring-boot)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688)](https://fastapi.tiangolo.com)

---

## 🏗️ Architecture

| Couche | Technologie | Port |
|--------|-------------|------|
| Frontend | React 18 + TypeScript + TailwindCSS | 3000 |
| Backend API | Spring Boot 3.3 + Java 21 | 8080 |
| Microservice NLP | FastAPI + Python 3.11 | 8000 |
| IAM | Keycloak 25.0 | 8180 |
| Base de données | PostgreSQL 16 | 5432 |
| Cache / Broker | Redis 7 | 6379 |
| Stockage PDF | MinIO | 9000 / 9001 |
| LLM | Ollama + Mistral | 11434 |

---

## 🚀 Démarrage rapide

### Prérequis
- Docker Desktop ≥ 24 + Docker Compose v2
- 8 Go RAM minimum (Mistral nécessite ~4 Go)
- Git

### 1. Cloner et configurer

```bash
git clone https://github.com/Abdelhak-elgo/QuizGen.git
cd QuizGen
cp .env.example .env
# Éditez .env et remplacez tous les CHANGE_ME_* par des mots de passe forts
```

### 2. Lancer tous les services

```bash
docker-compose up -d
```

Vérifier que tout est sain :

```bash
docker-compose ps
# Tous les services doivent afficher "healthy" ou "running"
```

### 3. Télécharger le modèle Mistral (~4 Go)

```bash
chmod +x scripts/setup-ollama.sh
./scripts/setup-ollama.sh
```

### 4. Accès aux services

| Service | URL | Credentials |
|---------|-----|-------------|
| Spring Boot API | http://localhost:8080/api/v1 | JWT Keycloak |
| Swagger UI | http://localhost:8080/api/v1/swagger-ui.html | — |
| Keycloak Console | http://localhost:8180 | admin / (KC_ADMIN_PASS dans .env) |
| MinIO Console | http://localhost:9001 | minioadmin / (MINIO_SECRET_KEY dans .env) |
| FastAPI NLP docs | http://localhost:8000/docs | — |

---

## 🔐 Authentification (Keycloak)

**Realm** : `quizgen`  
**Utilisateurs de test** :

| Email | Mot de passe | Rôle |
|-------|--------------|------|
| admin@quizgen.ma | admin123 | ADMIN |
| prof.ahmed@univ.ma | prof123 | ENSEIGNANT |
| sara.elamrani@univ.ma | etud123 | ETUDIANT |

### Obtenir un token JWT (curl)

```bash
curl -s -X POST http://localhost:8180/realms/quizgen/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=quizgen-frontend" \
  -d "username=prof.ahmed@univ.ma" \
  -d "password=prof123" | jq -r .access_token
```

---

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

```bash
# Via Newman (CLI Postman)
npm install -g newman
newman run postman/quizgen.postman_collection.json \
  --environment postman/quizgen.postman_environment.json \
  --reporters cli,json \
  --reporter-json-export postman/results.json
```

---

## 📋 API Endpoints

### Users

```
POST   /api/v1/users/sync      # Synchroniser utilisateur Keycloak → BDD
GET    /api/v1/users/me        # Mon profil
GET    /api/v1/users           # Liste paginée (ADMIN)
GET    /api/v1/users/{id}      # Détail (ADMIN)
```

### Documents

```
POST   /api/v1/documents/upload             # Upload PDF (max 50 Mo, ENSEIGNANT/ADMIN)
GET    /api/v1/documents                    # Mes documents (paginé)
GET    /api/v1/documents/{id}               # Détail
GET    /api/v1/documents/{id}/download-url  # URL présignée MinIO (1h)
DELETE /api/v1/documents/{id}               # Supprimer
```

---

## 🔄 CI/CD

### GitHub Actions

Le pipeline CI s'exécute automatiquement sur chaque push vers `main` ou `develop` :

| Job | Description | Déclencheur |
|-----|-------------|-------------|
| `build` | Compile + tests unitaires Maven + rapport Surefire | push / PR |
| `sonar` | Analyse SonarQube (Quality Gate) | push main/develop |
| `docker-backend` | Build + push image Spring Boot → GHCR | push |
| `docker-nlp` | Build + push image FastAPI → GHCR | push |

### Secrets GitHub requis

Configurer dans **Settings > Secrets and variables > Actions** :

| Secret | Description |
|--------|-------------|
| `SONAR_TOKEN` | Token d'authentification SonarQube |
| `SONAR_HOST_URL` | URL du serveur SonarQube (ex: `https://sonarqube.mondomaine.ma`) |

> Les secrets `GITHUB_TOKEN` et accès GHCR sont fournis automatiquement par GitHub Actions.

---

## 🧪 Tests

```bash
# Tests unitaires Spring Boot (JUnit 5 + Mockito)
cd backend && mvn test

# Tests NLP (Python — Phase 2)
cd nlp-service && pytest tests/ -v --cov=app

# Tests Postman via Newman
newman run postman/quizgen.postman_collection.json \
  -e postman/quizgen.postman_environment.json
```

---

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
├── nlp-service/                # FastAPI NLP (Python 3.11) — Phase 2
│   ├── Dockerfile
│   ├── requirements.txt
│   └── app/main.py
└── frontend/                   # React 18 — Phase 4
```

---

## 🗺️ Plan d'implémentation

| Phase | Contenu | Statut |
|-------|---------|--------|
| Phase 1 | Spring Boot CRUD Users + Documents + MinIO | ✅ Fait |
| Phase 2 | CI/CD + Postman | ✅ Fait |
| Phase 3 | FastAPI NLP : SpaCy + KeyBERT + Mistral | ⏳ À faire |
| Phase 4 | Spring Boot Quiz, Sessions, Attempts, Analytics | ⏳ À faire |
| Phase 5 | React 18 Frontend multi-rôles | ⏳ À faire |
| Phase 6 | BERTScore + Exports SCORM/Moodle | ⏳ À faire |
| Phase 7 | Qualité + Kubernetes + Monitoring | ⏳ À faire |
