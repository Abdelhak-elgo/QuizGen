# QuizGen 🎓

**Génération automatique de quiz pédagogiques par IA**  
PFE Master MIAGE 2024/2025 — Université Mohammed V

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

### 1. Cloner et configurer

```bash
git clone https://github.com/Abdelhak-elgo/QuizGen.git
cd QuizGen
cp .env.example .env
# Éditez .env et changez tous les CHANGE_ME_*
```

### 2. Lancer tous les services

```bash
docker-compose up -d
```

### 3. Télécharger le modèle Mistral (~4 Go)

```bash
chmod +x scripts/setup-ollama.sh
./scripts/setup-ollama.sh
```

### 4. Accès aux services

| Service | URL | Credentials |
|---------|-----|-------------|
| Spring Boot API | http://localhost:8080/api/v1 | — |
| Swagger UI | http://localhost:8080/api/v1/swagger-ui.html | — |
| Keycloak Console | http://localhost:8180 | admin / (voir .env KC_ADMIN_PASS) |
| MinIO Console | http://localhost:9001 | minioadmin / (voir .env MINIO_SECRET_KEY) |
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

### Obtenir un token JWT

```bash
curl -X POST http://localhost:8180/realms/quizgen/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=quizgen-frontend" \
  -d "username=prof.ahmed@univ.ma" \
  -d "password=prof123"
```

---

## 📋 API Endpoints principaux

### Users

```
POST   /api/v1/users/sync      # Synchroniser l'utilisateur Keycloak → BDD
GET    /api/v1/users/me        # Mon profil
GET    /api/v1/users           # Liste (ADMIN)
GET    /api/v1/users/{id}      # Détail (ADMIN)
```

### Documents

```
POST   /api/v1/documents/upload          # Uploader un PDF (max 50 Mo)
GET    /api/v1/documents                 # Mes documents
GET    /api/v1/documents/{id}            # Détail
GET    /api/v1/documents/{id}/download-url  # URL de téléchargement (1h)
DELETE /api/v1/documents/{id}            # Supprimer
```

---

## 🧪 Tests

```bash
# Tests unitaires Spring Boot
cd backend && mvn test

# Tests NLP (Python)
cd nlp-service && pytest --cov=app tests/
```

---

## 📁 Structure du projet

```
QuizGen/
├── docker-compose.yml
├── .env.example
├── init-db/
│   └── 01-schema.sql          # Schéma PostgreSQL complet
├── keycloak/
│   └── realm-export.json      # Realm pré-configuré
├── scripts/
│   └── setup-ollama.sh        # Pull du modèle Mistral
├── backend/                   # Spring Boot API
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/ma/quizgen/
│       ├── config/            # MinIO, OpenAPI
│       ├── security/          # Keycloak JWT, rôles
│       ├── entity/            # JPA entities + enums
│       ├── repository/        # Spring Data repositories
│       ├── dto/               # Records Java
│       ├── service/           # Interfaces + implémentations
│       ├── controller/        # REST controllers
│       └── exception/         # GlobalExceptionHandler
├── nlp-service/               # FastAPI NLP (Phase 2)
│   ├── Dockerfile
│   ├── requirements.txt
│   └── app/
└── frontend/                  # React 18 (Phase 4)
```

---

## 🗺️ Plan d'implémentation

| Phase | Contenu | Statut |
|-------|---------|--------|
| Phase 1 | Spring Boot CRUD Users + Documents + MinIO | ✅ |
| Phase 2 | FastAPI NLP : SpaCy + KeyBERT + Mistral | ⏳ |
| Phase 3 | Quiz, Sessions, Attempts, Analytics | ⏳ |
| Phase 4 | React 18 Frontend multi-rôles | ⏳ |
| Phase 5 | BERTScore + Exports SCORM/Moodle | ⏳ |
| Phase 6 | Qualité + Kubernetes + Monitoring | ⏳ |
