-- Reverses migration/V25__sequence_title.sql.
--
-- Every custom sequence name is lost. search_vector is first restored to V19's expression,
-- which no longer references the title, so the column can then be dropped. Application code
-- selects the column, so a backend at V25 or later will fail against a database this has run
-- against. Roll the code back with the schema.
--
-- Idempotent -- safe to run against a database that never had the column.

ALTER TABLE public.tiered_task_sequences DROP COLUMN IF EXISTS search_vector;
ALTER TABLE public.tiered_task_sequences ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', public.search_normalize(COALESCE(task_description, ''))), 'A') ||
    setweight(to_tsvector('simple', public.search_normalize((COALESCE(semantic_summary, ''::character varying))::text)), 'B')
) STORED;
CREATE INDEX IF NOT EXISTS idx_sequences_fts ON public.tiered_task_sequences USING gin (search_vector);

ALTER TABLE public.tiered_task_sequences DROP COLUMN IF EXISTS title;

DELETE FROM flyway_schema_history WHERE version='25';
