-- ============================================================
-- QuizGen — Migration Phase 5 : BERTScore + Exports
-- À appliquer sur une base existante (Phase 4 → Phase 5)
-- ============================================================

-- ── Colonne bert_score_details sur attempts ───────────────────────────────────
-- Stocke les détails de correction BERTScore par question ouverte.
-- Format JSONB : { "question_uuid": { f1, precision, recall, partial_score, label, model, ... } }

ALTER TABLE attempts
    ADD COLUMN IF NOT EXISTS bert_score_details JSONB;

COMMENT ON COLUMN attempts.bert_score_details IS
    'Détails BERTScore par question ouverte — Map<question_id, { f1, precision, recall, partial_score, label, model }>. Null si aucune question ouverte.';

-- Index GIN pour rechercher dans les résultats BERTScore (optionnel, performance)
CREATE INDEX IF NOT EXISTS idx_attempts_bert_score ON attempts USING GIN (bert_score_details);

-- ── Vue enrichie pour les résultats étudiant ─────────────────────────────────
-- Inclut le score BERTScore moyen des questions ouvertes
CREATE OR REPLACE VIEW v_attempt_detail AS
SELECT
    at.id                AS attempt_id,
    at.session_id,
    at.student_id,
    u.email              AS student_email,
    u.first_name || ' ' || u.last_name AS student_name,
    q.id                 AS quiz_id,
    q.title              AS quiz_title,
    at.score,
    at.max_score,
    CASE
        WHEN at.max_score > 0
        THEN ROUND((at.score::numeric / at.max_score) * 100, 1)
        ELSE 0
    END                  AS score_percent,
    at.attempt_status,
    at.bert_score_details,
    at.started_at,
    at.completed_at
FROM attempts at
JOIN sessions s ON s.id = at.session_id
JOIN quizzes  q ON q.id = s.quiz_id
JOIN users    u ON u.id = at.student_id;
