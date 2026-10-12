-- Reverses migration/V19__unaccent_search.sql: restores each generated
-- search_vector column to exactly the expression it had before V19 -- the one
-- migration/V1 (tasks, tiered_task_sequences) and migration/V16 (worksheets)
-- originally defined -- then drops the normalization functions and the
-- unaccent extension.
--
-- Order matters: the search_vector columns reference public.search_normalize
-- in their generated expression, so they have to be redefined first -- dropping
-- that dependency -- before the function itself can be dropped.

ALTER TABLE public.tasks DROP COLUMN IF EXISTS search_vector;
ALTER TABLE public.tasks ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', COALESCE(statement, '')), 'A') ||
    setweight(to_tsvector('simple', COALESCE(solution, '')), 'B')
) STORED;
CREATE INDEX IF NOT EXISTS idx_tasks_fts ON public.tasks USING gin (search_vector);

ALTER TABLE public.tiered_task_sequences DROP COLUMN IF EXISTS search_vector;
ALTER TABLE public.tiered_task_sequences ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', COALESCE(task_description, '')), 'A') ||
    setweight(to_tsvector('simple', (COALESCE(semantic_summary, ''::character varying))::text), 'B')
) STORED;
CREATE INDEX IF NOT EXISTS idx_sequences_fts ON public.tiered_task_sequences USING gin (search_vector);

ALTER TABLE public.worksheets DROP COLUMN IF EXISTS search_vector;
ALTER TABLE public.worksheets ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', COALESCE(name, '')), 'A') ||
    setweight(to_tsvector('simple', COALESCE(description, '')), 'B')
) STORED;
CREATE INDEX IF NOT EXISTS idx_worksheets_search_vector ON public.worksheets USING GIN (search_vector);

DROP FUNCTION IF EXISTS public.search_normalize(text);
DROP FUNCTION IF EXISTS public.fold_umlauts(text);
DROP FUNCTION IF EXISTS public.immutable_unaccent(text);

-- Nothing else in this schema uses it once the three functions above are gone.
DROP EXTENSION IF EXISTS unaccent;

DELETE FROM flyway_schema_history WHERE version='19';
