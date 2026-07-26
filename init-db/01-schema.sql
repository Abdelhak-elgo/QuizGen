-- ============================================================
-- QuizGen — Modèle BDD PostgreSQL
-- PFE Master MIAGE 2024/2025
-- Version : 1.0
-- ============================================================

-- ===================== EXTENSIONS =====================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ===================== ENUMS ==========================

CREATE TYPE role_enum AS ENUM ('ENSEIGNANT', 'ETUDIANT', 'ADMIN');
CREATE TYPE question_type_enum AS ENUM ('QCM', 'OUVERTE', 'EXERCICE');
CREATE TYPE difficulty_enum AS ENUM ('FACILE', 'MOYEN', 'DIFFICILE');
CREATE TYPE session_status_enum AS ENUM ('PLANIFIEE', 'ACTIVE', 'TERMINEE', 'ANNULEE');
CREATE TYPE attempt_status_enum AS ENUM ('EN_COURS', 'SOUMIS', 'CORRIGE');

-- ===================== TABLES =========================

-- 1. USERS
CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100)    NOT NULL,
    role            role_enum       NOT NULL DEFAULT 'ETUDIANT',
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

COMMENT ON TABLE users IS 'Utilisateurs de la plateforme (enseignants, étudiants, admins)';

-- 2. DOCUMENTS
CREATE TABLE documents (
    id              BIGSERIAL       PRIMARY KEY,
    filename        VARCHAR(255)    NOT NULL,
    original_name   VARCHAR(255)    NOT NULL,
    content_text    TEXT,
    file_size       BIGINT          NOT NULL,
    mime_type       VARCHAR(100)    NOT NULL DEFAULT 'application/pdf',
    bucket_name     VARCHAR(100)    NOT NULL DEFAULT 'quizgen-documents',
    object_key      VARCHAR(500)    NOT NULL UNIQUE,
    etag            VARCHAR(255),
    page_count      INTEGER,
    keywords        JSONB,
    uploaded_by     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    uploaded_at     TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_file_size CHECK (file_size > 0 AND file_size <= 52428800) -- max 50 Mo
);

COMMENT ON TABLE documents IS 'Documents PDF importés par les enseignants, stockés dans MinIO (S3)';
COMMENT ON COLUMN documents.bucket_name IS 'Nom du bucket MinIO (ex: quizgen-documents)';
COMMENT ON COLUMN documents.object_key IS 'Clé objet S3 : documents/{userId}/{uuid}.pdf';
COMMENT ON COLUMN documents.etag IS 'ETag MinIO pour vérification d''intégrité';

-- 3. QUIZZES
CREATE TABLE quizzes (
    id              BIGSERIAL       PRIMARY KEY,
    title           VARCHAR(255)    NOT NULL,
    description     TEXT,
    difficulty      difficulty_enum NOT NULL DEFAULT 'MOYEN',
    nb_questions    INTEGER         NOT NULL DEFAULT 10,
    config          JSONB           NOT NULL DEFAULT '{}',
    status          VARCHAR(50)     NOT NULL DEFAULT 'BROUILLON',
    document_id     BIGINT          NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    created_by      BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_nb_questions CHECK (nb_questions BETWEEN 3 AND 25)
);

COMMENT ON TABLE quizzes IS 'Quiz générés à partir des documents';
COMMENT ON COLUMN quizzes.config IS 'Configuration JSON : types de questions, niveau taxonomique, langue, etc.';

-- 4. QUESTIONS
CREATE TABLE questions (
    id              BIGSERIAL           PRIMARY KEY,
    quiz_id         BIGINT              NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    type            question_type_enum  NOT NULL,
    text            TEXT                NOT NULL,
    options         JSONB,
    correct_answer  TEXT                NOT NULL,
    explanation     TEXT,
    hint            TEXT,
    order_index     INTEGER             NOT NULL DEFAULT 0,
    points          DOUBLE PRECISION    NOT NULL DEFAULT 1.0,
    created_at      TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP           NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_points_positive CHECK (points > 0),
    CONSTRAINT chk_qcm_has_options CHECK (
        (type != 'QCM') OR (options IS NOT NULL AND jsonb_array_length(options) = 4)
    )
);

COMMENT ON TABLE questions IS 'Questions individuelles appartenant à un quiz';
COMMENT ON COLUMN questions.options IS 'Tableau JSON des 4 options QCM : [{"label":"A","text":"...","correct":true}, ...]';

-- 5. SESSIONS
CREATE TABLE sessions (
    id              BIGSERIAL           PRIMARY KEY,
    quiz_id         BIGINT              NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    access_code     VARCHAR(10)         NOT NULL UNIQUE,
    status          session_status_enum NOT NULL DEFAULT 'PLANIFIEE',
    duration        INTEGER             NOT NULL,
    start_time      TIMESTAMP           NOT NULL,
    end_time        TIMESTAMP           NOT NULL,
    max_attempts    INTEGER             NOT NULL DEFAULT 1,
    shuffle_questions BOOLEAN           NOT NULL DEFAULT FALSE,
    created_by      BIGINT              NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMP           NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_duration_positive CHECK (duration > 0),
    CONSTRAINT chk_time_coherence CHECK (end_time > start_time)
);

COMMENT ON TABLE sessions IS 'Sessions d''examen avec chronomètre et code d''accès';

-- 6. ATTEMPTS
CREATE TABLE attempts (
    id              BIGSERIAL           PRIMARY KEY,
    session_id      BIGINT              NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    user_id         BIGINT              NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          attempt_status_enum NOT NULL DEFAULT 'EN_COURS',
    score           DOUBLE PRECISION,
    total_points    DOUBLE PRECISION,
    duration        INTEGER,
    answers         JSONB               NOT NULL DEFAULT '[]',
    started_at      TIMESTAMP           NOT NULL DEFAULT NOW(),
    submitted_at    TIMESTAMP,

    CONSTRAINT chk_score_range CHECK (score IS NULL OR (score >= 0 AND score <= total_points)),
    CONSTRAINT uq_session_user_attempt UNIQUE (session_id, user_id)
);

COMMENT ON TABLE attempts IS 'Tentatives de réponse des étudiants';
COMMENT ON COLUMN attempts.answers IS 'Réponses JSON : [{"questionId":1,"answer":"...","score":0.8,"correct":true}, ...]';

-- 7. ANALYTICS
CREATE TABLE analytics (
    id              BIGSERIAL       PRIMARY KEY,
    quiz_id         BIGINT          NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE UNIQUE,
    total_attempts  INTEGER         NOT NULL DEFAULT 0,
    avg_score       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    avg_duration    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    pass_rate       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    question_stats  JSONB           NOT NULL DEFAULT '[]',
    score_distribution JSONB        NOT NULL DEFAULT '[]',
    computed_at     TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_avg_score CHECK (avg_score >= 0),
    CONSTRAINT chk_pass_rate CHECK (pass_rate BETWEEN 0 AND 100)
);

COMMENT ON TABLE analytics IS 'Données analytiques agrégées par quiz';
COMMENT ON COLUMN analytics.question_stats IS 'Stats par question : [{"questionId":1,"avgScore":0.75,"avgTime":45}, ...]';
COMMENT ON COLUMN analytics.score_distribution IS 'Distribution : [{"range":"0-20","count":5}, {"range":"20-40","count":12}, ...]';

-- ===================== INDEX ==========================

-- Users
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Documents
CREATE INDEX idx_documents_uploaded_by ON documents(uploaded_by);
CREATE INDEX idx_documents_uploaded_at ON documents(uploaded_at DESC);
CREATE INDEX idx_documents_object_key ON documents(object_key);

-- Quizzes
CREATE INDEX idx_quizzes_document_id ON quizzes(document_id);
CREATE INDEX idx_quizzes_created_by ON quizzes(created_by);
CREATE INDEX idx_quizzes_created_at ON quizzes(created_at DESC);

-- Questions
CREATE INDEX idx_questions_quiz_id ON questions(quiz_id);
CREATE INDEX idx_questions_type ON questions(type);
CREATE INDEX idx_questions_order ON questions(quiz_id, order_index);

-- Sessions
CREATE INDEX idx_sessions_quiz_id ON sessions(quiz_id);
CREATE INDEX idx_sessions_access_code ON sessions(access_code);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_sessions_time_range ON sessions(start_time, end_time);

-- Attempts
CREATE INDEX idx_attempts_session_id ON attempts(session_id);
CREATE INDEX idx_attempts_user_id ON attempts(user_id);
CREATE INDEX idx_attempts_status ON attempts(status);

-- Analytics
CREATE INDEX idx_analytics_quiz_id ON analytics(quiz_id);

-- ===================== TRIGGERS =======================

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_quizzes_updated_at
    BEFORE UPDATE ON quizzes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_questions_updated_at
    BEFORE UPDATE ON questions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ===================== SEED DATA ======================

-- Mot de passe hashé : "password123" (bcrypt)
INSERT INTO users (email, password_hash, first_name, last_name, role) VALUES
    ('admin@quizgen.ma', '$2a$10$N9qo8uLOickgx2ZMRZoHK.Zz1X3Y1X3Y1X3Y1X3Y1X3Y1X3Y1X3Y', 'Admin', 'QuizGen', 'ADMIN'),
    ('prof.ahmed@univ.ma', '$2a$10$N9qo8uLOickgx2ZMRZoHK.Zz1X3Y1X3Y1X3Y1X3Y1X3Y1X3Y1X3Y', 'Ahmed', 'Bennani', 'ENSEIGNANT'),
    ('etudiant.sara@univ.ma', '$2a$10$N9qo8uLOickgx2ZMRZoHK.Zz1X3Y1X3Y1X3Y1X3Y1X3Y1X3Y1X3Y', 'Sara', 'El Amrani', 'ETUDIANT');

-- ===================== VUES ===========================

-- Vue : résumé quiz avec stats
CREATE OR REPLACE VIEW v_quiz_summary AS
SELECT
    q.id,
    q.title,
    q.difficulty,
    q.nb_questions,
    q.created_at,
    d.original_name AS document_name,
    u.email AS created_by_email,
    CONCAT(u.first_name, ' ', u.last_name) AS created_by_name,
    COUNT(DISTINCT s.id) AS total_sessions,
    COUNT(DISTINCT a.id) AS total_attempts,
    COALESCE(an.avg_score, 0) AS avg_score
FROM quizzes q
JOIN documents d ON q.document_id = d.id
JOIN users u ON q.created_by = u.id
LEFT JOIN sessions s ON s.quiz_id = q.id
LEFT JOIN attempts a ON a.session_id = s.id
LEFT JOIN analytics an ON an.quiz_id = q.id
GROUP BY q.id, q.title, q.difficulty, q.nb_questions, q.created_at,
         d.original_name, u.email, u.first_name, u.last_name, an.avg_score;

-- Vue : tableau de bord étudiant
CREATE OR REPLACE VIEW v_student_dashboard AS
SELECT
    u.id AS student_id,
    u.email,
    CONCAT(u.first_name, ' ', u.last_name) AS student_name,
    q.title AS quiz_title,
    a.score,
    a.total_points,
    ROUND((a.score / NULLIF(a.total_points, 0)) * 100, 1) AS percentage,
    a.duration AS time_spent,
    a.submitted_at
FROM attempts a
JOIN users u ON a.user_id = u.id
JOIN sessions s ON a.session_id = s.id
JOIN quizzes q ON s.quiz_id = q.id
WHERE a.status = 'CORRIGE'
ORDER BY a.submitted_at DESC;
