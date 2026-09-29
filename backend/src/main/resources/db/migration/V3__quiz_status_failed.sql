-- Statut FAILED : génération NLP échouée ou abandonnée (délai dépassé).
ALTER TABLE quizzes DROP CONSTRAINT IF EXISTS chk_quizzes_status;
ALTER TABLE quizzes
    ADD CONSTRAINT chk_quizzes_status
        CHECK (quiz_status IN ('DRAFT', 'REVIEWING', 'PUBLISHED', 'ARCHIVED', 'FAILED'));
