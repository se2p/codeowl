-- Reverses migration/V17__sequence_divergence.sql, including recreating the
-- index V17 itself dropped (migration/V16's idx_sequences_original_recent),
-- so the schema afterwards matches what V16 alone left behind.

DROP INDEX IF EXISTS idx_sequences_listed_recent;

ALTER TABLE public.tiered_task_sequences DROP COLUMN IF EXISTS diverged_at;

CREATE INDEX IF NOT EXISTS idx_sequences_original_recent
    ON public.tiered_task_sequences (created_at DESC)
    WHERE copied_from IS NULL;

DELETE FROM flyway_schema_history WHERE version='17';
