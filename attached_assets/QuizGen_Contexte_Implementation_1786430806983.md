# QuizGen — Contexte & Plan d'implémentation

**Projet** : QuizGen — Génération automatique de quiz pédagogiques par IA
**Type** : PFE Master MIAGE 2024/2025
**Repository** : https://github.com/Abdelhak-elgo/QuizGen.git

---

## 1. Vision du projet

QuizGen est une plateforme web qui permet aux enseignants d'importer un document PDF, de configurer un quiz (difficulté, types de questions, nombre), et de générer automatiquement des questions pédagogiques grâce à un pipeline NLP + LLM. Les étudiants passent les examens en mode chronométré, avec correction automatique (directe pour les QCM, sémantique via BERTScore pour les questions ouvertes). Un tableau de bord analytique offre aux enseignants des indicateurs de performance par quiz et par question.

---

## 2. Stack technique finale

| Couche | Technologie | Rôle |
|---|---|---|
| Frontend | React 18 + TypeScript + TailwindCSS | SPA multi-rôles |
| Backend API | Spring Boot 3 (Java 21) | REST API, orchestration métier |
| Microservice NLP | FastAPI (Python 3.11) | Extraction PDF, génération questions, correction |
| LLM | Mistral fine-tuné via Ollama (local) | Génération de questions structurées |
| NLP | SpaCy (fr_core_news_lg) + KeyBERT | POS tagging, NER, extraction de concepts |
| Correction | BERTScore (RoBERTa) | Évaluation sémantique des réponses ouvertes |
| IAM | Keycloak 25.0 (OAuth2 / OpenID Connect) | Authentification, gestion des rôles, SSO |
| BDD | PostgreSQL 16 | Données métier (7 tables) |
| Cache / Broker | Redis 7 | Cache, sessions, Celery broker |
| Stockage objets | MinIO (S3-compatible) | PDFs uploadés, exports SCORM/Moodle |
| CI/CD | GitHub Actions + SonarQube | Build, tests, qualité de code |
| Conteneurisation | Docker Compose (dev) / Kubernetes (prod) | Orchestration des services |
| Monitoring | Prometheus + Grafana | Métriques et dashboards |
| Évaluation IA | BLEU-4, ROUGE-L, BERTScore | Qualité des questions générées |
| Export | SCORM 2004, Moodle XML | Intégration LMS |

---

## 3. Architecture des services Docker

```
┌─────────────────────────────────────────────────────────────┐
│                Docker Compose / Kubernetes                   │
│                                                             │
│  ┌──────────┐   ┌──────────────┐   ┌───────────────────┐  │
│  │ Frontend │   │  Keycloak    │   │  Keycloak DB      │  │
│  │ React    │   │  :8180       │──▶│  PostgreSQL       │  │
│  │ :3000    │   │  OAuth2/OIDC │   │  (séparée)        │  │
│  └────┬─────┘   └──────┬───────┘   └───────────────────┘  │
│       │                │ JWKS                               │
│       ▼                ▼                                    │
│  ┌──────────────────────────┐   ┌────────────────────────┐ │
│  │ Spring Boot API  :8080   │──▶│ FastAPI NLP  :8000     │ │
│  │ Java 21 + OAuth2 RS      │   │ Python 3.11 + SpaCy    │ │
│  │ Valide JWT Keycloak      │   │ KeyBERT + Mistral      │ │
│  └──┬────┬────┬─────────────┘   └──┬────┬────┬───────────┘ │
│     │    │    │                     │    │    │              │
│     ▼    ▼    ▼                     ▼    ▼    ▼              │
│  ┌─────┐┌─────┐┌──────┐     ┌─────┐┌──────┐┌───────┐      │
│  │Postgre││Redis││MinIO │     │Redis││MinIO ││Ollama │      │
│  │:5432 ││:6379││:9000 │     │     ││      ││:11434 │      │
│  │      ││     ││:9001 │     │     ││      ││Mistral│      │
│  └──────┘└─────┘└──────┘     └─────┘└──────┘└───────┘      │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Modèle de données (7 entités)

**5 Enums** : `role_enum` (ENSEIGNANT, ETUDIANT, ADMIN), `question_type_enum` (QCM, OUVERTE, EXERCICE), `difficulty_enum` (FACILE, MOYEN, DIFFICILE), `session_status_enum` (PLANIFIEE, ACTIVE, TERMINEE, ANNULEE), `attempt_status_enum` (EN_COURS, SOUMIS, CORRIGE)

**7 Tables avec relations** :

```
USERS ──1:N──▶ DOCUMENTS ──1:N──▶ QUIZZES ──1:N──▶ QUESTIONS
  │                                  │
  │                                  ├──1:N──▶ SESSIONS ──1:N──▶ ATTEMPTS
  │                                  │
  │                                  └──1:1──▶ ANALYTICS
  │
  └──1:N──▶ ATTEMPTS
```

Table DOCUMENTS : stockage MinIO via champs `bucket_name`, `object_key`, `etag` (pas de file_path local).

**Contraintes métier** : nb_questions entre 3–25, file_size max 50 Mo, QCM doit avoir exactement 4 options, end_time > start_time sur les sessions, un étudiant = une tentative par session (UNIQUE).

**16 index**, **3 triggers** (auto updated_at), **2 vues** (v_quiz_summary, v_student_dashboard), **3 users seed**.

---

## 5. Sécurité — Keycloak OAuth2

**Realm** : `quizgen`
**Clients** :
- `quizgen-api` (bearer-only) — validé par Spring Boot Resource Server
- `quizgen-frontend` (public SPA) — login OAuth2 depuis React

**Rôles Keycloak** : ENSEIGNANT, ETUDIANT, ADMIN
**Protocol mapper** : `realm_roles` injecté dans le JWT access token

**Spring Boot** : OAuth2 Resource Server, `KeycloakRoleConverter` extrait `realm_access.roles` du JWT et les convertit en `ROLE_*` pour `@PreAuthorize` / `hasRole()`. Helper `CurrentUser` pour accéder à l'email, prénom, nom depuis le token.

**Utilisateurs de test** :
- admin@quizgen.ma / admin123 (ADMIN)
- prof.ahmed@univ.ma / prof123 (ENSEIGNANT)
- sara.elamrani@univ.ma / etud123 (ETUDIANT)

---

## 6. Réalisations accomplies

### 6.1 Diagrammes UML (4/4) ✅

| Diagramme | Contenu |
|---|---|
| Classes | 7 entités + 3 enums + cardinalités + méthodes métier |
| Cas d'utilisation | 3 acteurs, 12 UC (7 Enseignant, 3 Étudiant, 2 Admin) |
| Séquence #1 | Upload PDF → NLP pipeline → Mistral → Quiz généré |
| Séquence #2 | Étudiant : accès examen → réponses → correction BERTScore → résultats |
| Déploiement | 8 services Docker + Keycloak + MinIO + monitoring |

### 6.2 Modèle BDD PostgreSQL ✅

Fichier `quizgen_init.sql` complet : 5 enums, 7 tables, 16 index, 3 triggers, 2 vues, seed data. Prêt pour `docker-entrypoint-initdb.d/`.

### 6.3 Docker Compose ✅

8 services orchestrés :
- PostgreSQL (app) + PostgreSQL (Keycloak) — BDD séparées
- Keycloak 25.0 avec import automatique du realm
- Redis 7 avec authentification
- MinIO avec création automatique des buckets via `minio-init`
- Ollama + script `setup-ollama.sh` pour pull Mistral
- Spring Boot API (dépend de PostgreSQL + Redis + MinIO + Keycloak)
- FastAPI NLP (dépend de Redis + MinIO + Ollama)

Tous les services ont des health checks, volumes persistants, et un réseau bridge partagé.

### 6.4 Realm Keycloak ✅

`keycloak/realm-export.json` : realm quizgen, 3 rôles, 2 clients configurés, protocol mapper pour les rôles dans le JWT, 3 utilisateurs de test. Import automatique au démarrage.

### 6.5 Backend Spring Boot (structure initiale) ✅

| Composant | Fichiers |
|---|---|
| Config Maven | `pom.xml` (Spring Boot 3.3, OAuth2 RS, JPA, MinIO SDK, SpringDoc) |
| Application | `QuizGenApplication.java`, `application.yml` |
| Sécurité Keycloak | `SecurityConfig.java`, `KeycloakRoleConverter.java`, `CurrentUser.java` |
| Entités JPA | `User.java`, `Document.java` + 3 enums |
| Repositories | `UserRepository.java`, `DocumentRepository.java` |
| Config | `MinioConfig.java` (bean MinioClient) |
| Exceptions | `GlobalExceptionHandler.java` |

### 6.6 Microservice FastAPI (structure initiale) ✅

`Dockerfile` (Python 3.11 + SpaCy fr), `requirements.txt` (14 dépendances), endpoint `/health` fonctionnel.

---

## 7. Plan d'implémentation complet

### PHASE 1 — FONDATIONS (Sem 1–2)

| Tâche | Durée | Statut |
|---|---|---|
| Diagrammes UML (classes, UC, séquence, déploiement) | 3 jours | ✅ Fait |
| Setup GitHub repo + Docker Compose | 2 jours | ✅ Fait |
| Modèle BDD PostgreSQL (7 entités) | 1 jour | ✅ Fait |
| CDC v2 (mise à jour Mistral + Keycloak) | 1 jour | ⏳ À faire |
| Spring Boot + Keycloak OAuth2 (structure) | 3 jours | ✅ Structure faite |
| CRUD Users (synchro Keycloak Admin API) | 2 jours | ⏳ À faire |
| CRUD Documents + upload MinIO | 2 jours | ⏳ À faire |
| CI/CD GitHub Actions (build + tests) | 1 jour | ⏳ À faire |
| Validation : docker-compose up + tests Postman | 1 jour | ⏳ À faire |

### PHASE 2 — PIPELINE IA (Sem 3–4)

| Tâche | Durée | Statut |
|---|---|---|
| Extraction texte PDF (PyMuPDF) | 2 jours | ⏳ |
| Pipeline NLP : SpaCy POS/NER + KeyBERT concepts | 3 jours | ⏳ |
| Intégration Mistral via Ollama (prompt engineering) | 3 jours | ⏳ |
| Post-traitement : déduplication ROUGE, validation JSON | 2 jours | ⏳ |
| Endpoint /generate fonctionnel (10 questions/PDF) | 1 jour | ⏳ |
| Tests unitaires NLP (coverage 80%+) | 2 jours | ⏳ |

### PHASE 3 — BACKEND COMPLET (Sem 5–6)

| Tâche | Durée | Statut |
|---|---|---|
| CRUD Quiz + Questions (Spring Boot) | 3 jours | ⏳ |
| Gestion Sessions + Attempts | 2 jours | ⏳ |
| Intégration microservice NLP ↔ Spring Boot | 2 jours | ⏳ |
| API REST complète + documentation Swagger | 2 jours | ⏳ |
| Tests d'intégration backend | 2 jours | ⏳ |

### PHASE 4 — FRONTEND (Sem 7–8)

| Tâche | Durée | Statut |
|---|---|---|
| Setup React + routing + Keycloak JS adapter | 2 jours | ⏳ |
| Interface upload PDF + configuration quiz | 3 jours | ⏳ |
| Affichage et édition des questions générées | 2 jours | ⏳ |
| Mode examen chronométré (étudiant) | 3 jours | ⏳ |
| Dashboard analytique (Chart.js / Recharts) | 2 jours | ⏳ |

### PHASE 5 — CORRECTION & EXPORT (Sem 9–10)

| Tâche | Durée | Statut |
|---|---|---|
| Correction automatique BERTScore (réponses ouvertes) | 3 jours | ⏳ |
| Dashboard analytics complet (graphiques) | 2 jours | ⏳ |
| Export SCORM 2004 | 3 jours | ⏳ |
| Export Moodle XML | 2 jours | ⏳ |
| Import test dans Moodle | 1 jour | ⏳ |

### PHASE 6 — QUALITÉ & DÉPLOIEMENT (Sem 11–12)

| Tâche | Durée | Statut |
|---|---|---|
| Tests d'intégration E2E | 3 jours | ⏳ |
| Évaluation qualité IA (BLEU-4, BERTScore, n=20) | 3 jours | ⏳ |
| Déploiement Kubernetes | 2 jours | ⏳ |
| Optimisations performance (< 15s génération) | 2 jours | ⏳ |
| SonarQube : correction dette technique | 1 jour | ⏳ |

### PHASE 7 — FINALISATION (Sem 13–14)

| Tâche | Durée | Statut |
|---|---|---|
| Rédaction mémoire PFE (60–80 pages) | 5 jours | ⏳ |
| Préparation slides soutenance | 2 jours | ⏳ |
| Correction bugs remontés | 2 jours | ⏳ |
| Démo live prête + documentation technique | 2 jours | ⏳ |

---

## 8. Structure du repository GitHub

```
QuizGen/
├── docker-compose.yml
├── .env.example
├── .gitignore
├── init-db/
│   └── 01-schema.sql
├── keycloak/
│   └── realm-export.json
├── scripts/
│   └── setup-ollama.sh
├── backend/                        ← Spring Boot API
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/ma/quizgen/
│       ├── QuizGenApplication.java
│       ├── config/MinioConfig.java
│       ├── security/
│       │   ├── SecurityConfig.java
│       │   ├── KeycloakRoleConverter.java
│       │   └── CurrentUser.java
│       ├── entity/
│       │   ├── User.java
│       │   ├── Document.java
│       │   └── enums/ (Role, QuestionType, Difficulty)
│       ├── repository/
│       ├── dto/
│       ├── service/
│       ├── controller/
│       └── exception/GlobalExceptionHandler.java
├── nlp-service/                    ← FastAPI NLP
│   ├── Dockerfile
│   ├── requirements.txt
│   └── app/main.py
└── frontend/                       ← React (Phase 4)
```

---

## 9. Commandes de lancement

```bash
# 1. Cloner et configurer
git clone https://github.com/Abdelhak-elgo/QuizGen.git
cd QuizGen
cp .env.example .env               # adapter les mots de passe

# 2. Lancer tous les services
docker-compose up -d

# 3. Télécharger le modèle Mistral (~4 Go)
chmod +x scripts/setup-ollama.sh && ./scripts/setup-ollama.sh

# 4. Accès aux services
# Spring Boot API    → http://localhost:8080/api/v1
# Swagger UI         → http://localhost:8080/api/v1/swagger-ui.html
# Keycloak Console   → http://localhost:8180 (admin/admin)
# MinIO Console      → http://localhost:9001 (minioadmin/minio_secret_2025)
# FastAPI NLP        → http://localhost:8000/docs
```

---

## 10. Prochaines étapes immédiates

1. **Intégrer les fichiers** dans le repo GitHub et valider `docker-compose up`
2. **CRUD Users** avec synchronisation Keycloak Admin API
3. **CRUD Documents** avec upload/download vers MinIO
4. **CI/CD GitHub Actions** (build Maven + tests + Docker build)
5. **Collection Postman** pour valider tous les endpoints avec flux OAuth2

---

*Document généré le 11 août 2026 — Discussion Claude × Abdelhak*
