-- Recommandation pédagogique générée par LLM pour une copie soumise
-- (diagnostic destiné à l'enseignant), conservée pour ne pas la régénérer.
ALTER TABLE attempts ADD COLUMN IF NOT EXISTS recommendation              JSONB;
ALTER TABLE attempts ADD COLUMN IF NOT EXISTS recommendation_generated_at TIMESTAMP;
