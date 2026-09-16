-- ============================================================
-- QuizGen — Schéma PostgreSQL 16
-- Généré pour le PFE Master MIAGE 2024/2025
-- ============================================================

-- ── Extensions ───────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- Recherche full-text

-- ── Table : users ─────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    keycloak_id   VARCHAR(36) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    role          VARCHAR(20) NOT NULL DEFAULT 'ETUDIANT',
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_role
        CHECK (role IN ('ENSEIGNANT', 'ETUDIANT', 'ADMIN'))
);

-- ── Table : documents ─────────────────────────────────────────────────────────
CREATE TABLE documents (
    id                UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    bucket_name       VARCHAR(100) NOT NULL,
    object_key        VARCHAR(500) NOT NULL UNIQUE,
    etag              VARCHAR(100),
    file_size         BIGINT      CHECK (file_size <= 52428800),   -- max 50 Mo
    page_count        INTEGER,
    is_processed      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- ── Table : quizzes ───────────────────────────────────────────────────────────
CREATE TABLE quizzes (
    id            UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id   UUID            NOT NULL REFERENCES documents(id) ON DELETE RESTRICT,
    teacher_id    UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title         VARCHAR(255)    NOT NULL,
    description   TEXT,
    difficulty    VARCHAR(20)     NOT NULL DEFAULT 'MOYEN',
    nb_questions  INTEGER         NOT NULL CHECK (nb_questions BETWEEN 3 AND 25),
    quiz_status   VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    nlp_task_id   VARCHAR(100),
    is_published  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_quizzes_difficulty
        CHECK (difficulty IN ('FACILE', 'MOYEN', 'DIFFICILE')),
    CONSTRAINT chk_quizzes_status
        CHECK (quiz_status IN ('DRAFT', 'REVIEWING', 'PUBLISHED', 'ARCHIVED'))
);

-- ── Table : questions ─────────────────────────────────────────────────────────
CREATE TABLE questions (
    id               UUID               PRIMARY KEY DEFAULT uuid_generate_v4(),
    quiz_id          UUID               NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    type             VARCHAR(20)        NOT NULL,
    content          TEXT               NOT NULL,
    options          JSONB,             -- Pour QCM : ["opt1", "opt2", "opt3", "opt4"]
    correct_answer   TEXT               NOT NULL,
    explanation      TEXT,
    difficulty       VARCHAR(20)        NOT NULL DEFAULT 'MOYEN',
    keywords         JSONB,
    position         INTEGER            NOT NULL DEFAULT 0,
    created_at       TIMESTAMP          NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP          NOT NULL DEFAULT NOW(),
    -- Contrainte : QCM doit avoir exactement 4 options
    CONSTRAINT chk_questions_type
        CHECK (type IN ('QCM', 'OUVERTE', 'EXERCICE')),
    CONSTRAINT chk_questions_difficulty
        CHECK (difficulty IN ('FACILE', 'MOYEN', 'DIFFICILE')),
    CONSTRAINT qcm_has_4_options CHECK (
        type != 'QCM' OR jsonb_array_length(options) = 4
    )
);

-- ── Table : sessions ──────────────────────────────────────────────────────────
CREATE TABLE sessions (
    id           UUID                 PRIMARY KEY DEFAULT uuid_generate_v4(),
    quiz_id      UUID                 NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    teacher_id   UUID                 NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title        VARCHAR(255),
    access_code  VARCHAR(20),
    session_status VARCHAR(20)          NOT NULL DEFAULT 'SCHEDULED',
    start_time   TIMESTAMP            NOT NULL,
    end_time     TIMESTAMP            NOT NULL,
    max_attempts INTEGER              NOT NULL DEFAULT 1,
    created_at   TIMESTAMP            NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP            NOT NULL DEFAULT NOW(),
    CONSTRAINT end_after_start CHECK (end_time > start_time),
    CONSTRAINT chk_sessions_status
        CHECK (session_status IN ('SCHEDULED', 'OPEN', 'CLOSED', 'CANCELLED'))
);

-- ── Table : attempts ──────────────────────────────────────────────────────────
CREATE TABLE attempts (
    id             UUID                PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id     UUID                NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    student_id     UUID                NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attempt_status VARCHAR(20)         NOT NULL DEFAULT 'IN_PROGRESS',
    answers        JSONB,              -- {"question_id": "réponse_étudiant", ...}
    score          INTEGER             NOT NULL DEFAULT 0,
    max_score      INTEGER,
    started_at     TIMESTAMP           NOT NULL DEFAULT NOW(),
    completed_at   TIMESTAMP,
    created_at     TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP           NOT NULL DEFAULT NOW(),
    -- Un étudiant = une seule tentative par session
    CONSTRAINT uq_attempt_session_student UNIQUE (session_id, student_id),
    CONSTRAINT chk_attempts_status
        CHECK (attempt_status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED'))
);

-- ── Table : analytics ─────────────────────────────────────────────────────────
CREATE TABLE analytics (
    id              UUID      PRIMARY KEY DEFAULT uuid_generate_v4(),
    quiz_id         UUID      NOT NULL UNIQUE REFERENCES quizzes(id) ON DELETE CASCADE,
    total_attempts  INTEGER   NOT NULL DEFAULT 0,
    avg_score       NUMERIC(5,2),
    pass_rate       NUMERIC(5,2),
    avg_duration_s  INTEGER,
    last_computed   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Index ─────────────────────────────────────────────────────────────────────
CREATE INDEX idx_users_email        ON users(email);
CREATE INDEX idx_users_keycloak_id  ON users(keycloak_id);
CREATE INDEX idx_users_role         ON users(role);

CREATE INDEX idx_documents_user_id  ON documents(user_id);
CREATE INDEX idx_documents_created  ON documents(created_at DESC);

CREATE INDEX idx_quizzes_teacher    ON quizzes(teacher_id);
CREATE INDEX idx_quizzes_document   ON quizzes(document_id);
CREATE INDEX idx_quizzes_published  ON quizzes(is_published);

CREATE INDEX idx_questions_quiz_id  ON questions(quiz_id);
CREATE INDEX idx_questions_type     ON questions(type);

CREATE INDEX idx_sessions_quiz_id   ON sessions(quiz_id);
CREATE INDEX idx_sessions_status    ON sessions(session_status);
CREATE INDEX idx_sessions_times     ON sessions(start_time, end_time);

CREATE INDEX idx_attempts_session   ON attempts(session_id);
CREATE INDEX idx_attempts_student   ON attempts(student_id);
CREATE INDEX idx_attempts_status    ON attempts(attempt_status);

CREATE INDEX idx_analytics_quiz     ON analytics(quiz_id);

-- ── Triggers updated_at ───────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_quizzes_updated_at
    BEFORE UPDATE ON quizzes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_questions_updated_at
    BEFORE UPDATE ON questions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_sessions_updated_at
    BEFORE UPDATE ON sessions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_attempts_updated_at
    BEFORE UPDATE ON attempts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── Vues ─────────────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW v_quiz_summary AS
SELECT
    q.id            AS quiz_id,
    q.title,
    q.difficulty,
    q.nb_questions,
    q.quiz_status,
    u.email         AS teacher_email,
    d.original_filename AS source_document,
    a.total_attempts,
    a.avg_score,
    a.pass_rate,
    q.created_at
FROM quizzes q
JOIN users     u ON u.id = q.teacher_id
JOIN documents d ON d.id = q.document_id
LEFT JOIN analytics a ON a.quiz_id = q.id;

CREATE OR REPLACE VIEW v_student_dashboard AS
SELECT
    at.student_id,
    u.email,
    s.quiz_id,
    q.title        AS quiz_title,
    at.score,
    at.max_score,
    at.attempt_status,
    at.completed_at,
    s.start_time,
    s.end_time
FROM attempts at
JOIN sessions s ON s.id = at.session_id
JOIN quizzes  q ON q.id = s.quiz_id
JOIN users    u ON u.id = at.student_id;

-- ── Données de test ───────────────────────────────────────────────────────────
INSERT INTO users (keycloak_id, email, first_name, last_name, role) VALUES
    ('admin-kc-uuid-001',   'admin@quizgen.ma',        'Admin',  'QuizGen',   'ADMIN'),
    ('teacher-kc-uuid-002', 'prof.ahmed@univ.ma',      'Ahmed',  'Benali',    'ENSEIGNANT'),
    ('student-kc-uuid-003', 'sara.elamrani@univ.ma',   'Sara',   'El Amrani', 'ETUDIANT');
