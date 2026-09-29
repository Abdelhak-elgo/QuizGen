-- Relance de génération : mémoriser les types demandés et l'instant de la
-- dernière soumission NLP (le délai d'abandon part de là, pas de created_at).
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS question_types        VARCHAR(100);
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS generation_started_at TIMESTAMP;

-- Étudiants assignés à une session : ils la voient sur leur tableau de bord
-- et y accèdent sans code. Une session avec participants est réservée à eux.
CREATE TABLE IF NOT EXISTS session_participants (
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    PRIMARY KEY (session_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_session_participants_student ON session_participants(student_id);
