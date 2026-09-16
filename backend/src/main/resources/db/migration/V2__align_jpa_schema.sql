-- Align databases initialized by earlier init-db scripts with the current JPA
-- mappings. Every operation is safe to run on the already-aligned base schema.

DROP VIEW IF EXISTS v_attempt_detail;
DROP VIEW IF EXISTS v_student_dashboard;
DROP VIEW IF EXISTS v_quiz_summary;

-- Rename legacy relationship and status columns.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'quizzes' AND column_name = 'user_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'quizzes' AND column_name = 'teacher_id'
    ) THEN
        ALTER TABLE quizzes RENAME COLUMN user_id TO teacher_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sessions' AND column_name = 'user_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sessions' AND column_name = 'teacher_id'
    ) THEN
        ALTER TABLE sessions RENAME COLUMN user_id TO teacher_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sessions' AND column_name = 'status'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sessions' AND column_name = 'session_status'
    ) THEN
        ALTER TABLE sessions RENAME COLUMN status TO session_status;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'attempts' AND column_name = 'user_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'attempts' AND column_name = 'student_id'
    ) THEN
        ALTER TABLE attempts RENAME COLUMN user_id TO student_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'attempts' AND column_name = 'status'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'attempts' AND column_name = 'attempt_status'
    ) THEN
        ALTER TABLE attempts RENAME COLUMN status TO attempt_status;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'attempts' AND column_name = 'submitted_at'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'attempts' AND column_name = 'completed_at'
    ) THEN
        ALTER TABLE attempts RENAME COLUMN submitted_at TO completed_at;
    END IF;
END
$$;

-- Add fields introduced by the current domain model.
ALTER TABLE quizzes
    ADD COLUMN IF NOT EXISTS quiz_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS nlp_task_id VARCHAR(100);

ALTER TABLE questions
    ADD COLUMN IF NOT EXISTS keywords JSONB;

ALTER TABLE attempts
    ADD COLUMN IF NOT EXISTS max_score INTEGER,
    ADD COLUMN IF NOT EXISTS bert_score_details JSONB;

-- Hibernate maps EnumType.STRING as VARCHAR. Convert legacy PostgreSQL named
-- enums and translate the old French session/attempt values.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ALTER COLUMN role DROP DEFAULT;
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(20) USING role::text;
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'ETUDIANT';

ALTER TABLE quizzes DROP CONSTRAINT IF EXISTS chk_quizzes_difficulty;
ALTER TABLE quizzes DROP CONSTRAINT IF EXISTS chk_quizzes_status;
ALTER TABLE quizzes ALTER COLUMN difficulty DROP DEFAULT;
ALTER TABLE quizzes ALTER COLUMN difficulty TYPE VARCHAR(20) USING difficulty::text;
ALTER TABLE quizzes ALTER COLUMN difficulty SET DEFAULT 'MOYEN';
ALTER TABLE quizzes ALTER COLUMN quiz_status DROP DEFAULT;
ALTER TABLE quizzes ALTER COLUMN quiz_status TYPE VARCHAR(20) USING quiz_status::text;
ALTER TABLE quizzes ALTER COLUMN quiz_status SET DEFAULT 'DRAFT';

ALTER TABLE questions DROP CONSTRAINT IF EXISTS qcm_has_4_options;
ALTER TABLE questions DROP CONSTRAINT IF EXISTS chk_questions_type;
ALTER TABLE questions DROP CONSTRAINT IF EXISTS chk_questions_difficulty;
ALTER TABLE questions ALTER COLUMN type TYPE VARCHAR(20) USING type::text;
ALTER TABLE questions ALTER COLUMN difficulty DROP DEFAULT;
ALTER TABLE questions ALTER COLUMN difficulty TYPE VARCHAR(20) USING difficulty::text;
ALTER TABLE questions ALTER COLUMN difficulty SET DEFAULT 'MOYEN';

ALTER TABLE sessions DROP CONSTRAINT IF EXISTS chk_sessions_status;
ALTER TABLE sessions ALTER COLUMN session_status DROP DEFAULT;
ALTER TABLE sessions ALTER COLUMN session_status TYPE VARCHAR(20)
    USING CASE session_status::text
        WHEN 'PLANIFIEE' THEN 'SCHEDULED'
        WHEN 'ACTIVE' THEN 'OPEN'
        WHEN 'TERMINEE' THEN 'CLOSED'
        WHEN 'ANNULEE' THEN 'CANCELLED'
        ELSE session_status::text
    END;
ALTER TABLE sessions ALTER COLUMN session_status SET DEFAULT 'SCHEDULED';

ALTER TABLE attempts DROP CONSTRAINT IF EXISTS chk_attempts_status;
ALTER TABLE attempts ALTER COLUMN attempt_status DROP DEFAULT;
ALTER TABLE attempts ALTER COLUMN attempt_status TYPE VARCHAR(20)
    USING CASE attempt_status::text
        WHEN 'EN_COURS' THEN 'IN_PROGRESS'
        WHEN 'SOUMIS' THEN 'SUBMITTED'
        WHEN 'CORRIGE' THEN 'SUBMITTED'
        ELSE attempt_status::text
    END;
ALTER TABLE attempts ALTER COLUMN attempt_status SET DEFAULT 'IN_PROGRESS';

-- The original schema used NUMERIC for score; the entity uses Integer.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'attempts'
          AND column_name = 'score'
          AND data_type <> 'integer'
    ) THEN
        ALTER TABLE attempts
            ALTER COLUMN score TYPE INTEGER USING ROUND(score)::INTEGER;
    END IF;
END
$$;

-- Normalize legacy values before enforcing the current domain constraints.
UPDATE quizzes SET quiz_status = 'DRAFT'
WHERE quiz_status NOT IN ('DRAFT', 'REVIEWING', 'PUBLISHED', 'ARCHIVED');

UPDATE sessions SET session_status = 'SCHEDULED'
WHERE session_status NOT IN ('SCHEDULED', 'OPEN', 'CLOSED', 'CANCELLED');

UPDATE attempts SET attempt_status = 'IN_PROGRESS'
WHERE attempt_status NOT IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED');

ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('ENSEIGNANT', 'ETUDIANT', 'ADMIN'));

ALTER TABLE quizzes
    ADD CONSTRAINT chk_quizzes_difficulty
        CHECK (difficulty IN ('FACILE', 'MOYEN', 'DIFFICILE')),
    ADD CONSTRAINT chk_quizzes_status
        CHECK (quiz_status IN ('DRAFT', 'REVIEWING', 'PUBLISHED', 'ARCHIVED'));

ALTER TABLE questions
    ADD CONSTRAINT chk_questions_type
        CHECK (type IN ('QCM', 'OUVERTE', 'EXERCICE')),
    ADD CONSTRAINT chk_questions_difficulty
        CHECK (difficulty IN ('FACILE', 'MOYEN', 'DIFFICILE')),
    ADD CONSTRAINT qcm_has_4_options
        CHECK (type <> 'QCM' OR jsonb_array_length(options) = 4);

ALTER TABLE sessions
    ADD CONSTRAINT chk_sessions_status
        CHECK (session_status IN ('SCHEDULED', 'OPEN', 'CLOSED', 'CANCELLED'));

ALTER TABLE attempts
    ADD CONSTRAINT chk_attempts_status
        CHECK (attempt_status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED'));

ALTER TABLE attempts DROP CONSTRAINT IF EXISTS unique_attempt_per_student_session;
ALTER TABLE attempts DROP CONSTRAINT IF EXISTS uq_attempt_session_student;
ALTER TABLE attempts
    ADD CONSTRAINT uq_attempt_session_student UNIQUE (session_id, student_id);

-- Recreate indexes whose columns were renamed.
DROP INDEX IF EXISTS idx_quizzes_user_id;
DROP INDEX IF EXISTS idx_sessions_status;
DROP INDEX IF EXISTS idx_attempts_user;
DROP INDEX IF EXISTS idx_attempts_status;

CREATE INDEX IF NOT EXISTS idx_quizzes_teacher ON quizzes(teacher_id);
CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(session_status);
CREATE INDEX IF NOT EXISTS idx_attempts_student ON attempts(student_id);
CREATE INDEX IF NOT EXISTS idx_attempts_status ON attempts(attempt_status);
CREATE INDEX IF NOT EXISTS idx_attempts_bert_score
    ON attempts USING GIN (bert_score_details);

-- Restore reporting views using the current names.
CREATE OR REPLACE VIEW v_quiz_summary AS
SELECT
    q.id AS quiz_id,
    q.title,
    q.difficulty,
    q.nb_questions,
    q.quiz_status,
    u.email AS teacher_email,
    d.original_filename AS source_document,
    a.total_attempts,
    a.avg_score,
    a.pass_rate,
    q.created_at
FROM quizzes q
JOIN users u ON u.id = q.teacher_id
JOIN documents d ON d.id = q.document_id
LEFT JOIN analytics a ON a.quiz_id = q.id;

CREATE OR REPLACE VIEW v_student_dashboard AS
SELECT
    at.student_id,
    u.email,
    s.quiz_id,
    q.title AS quiz_title,
    at.score,
    at.max_score,
    at.attempt_status,
    at.completed_at,
    s.start_time,
    s.end_time
FROM attempts at
JOIN sessions s ON s.id = at.session_id
JOIN quizzes q ON q.id = s.quiz_id
JOIN users u ON u.id = at.student_id;

CREATE OR REPLACE VIEW v_attempt_detail AS
SELECT
    at.id AS attempt_id,
    at.session_id,
    at.student_id,
    u.email AS student_email,
    u.first_name || ' ' || u.last_name AS student_name,
    q.id AS quiz_id,
    q.title AS quiz_title,
    at.score,
    at.max_score,
    CASE
        WHEN at.max_score > 0
        THEN ROUND((at.score::numeric / at.max_score) * 100, 1)
        ELSE 0
    END AS score_percent,
    at.attempt_status,
    at.bert_score_details,
    at.started_at,
    at.completed_at
FROM attempts at
JOIN sessions s ON s.id = at.session_id
JOIN quizzes q ON q.id = s.quiz_id
JOIN users u ON u.id = at.student_id;