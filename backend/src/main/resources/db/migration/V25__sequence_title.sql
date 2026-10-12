-- Let the owner of a sequence (or an admin) give it a name of its own.
--
-- Null means "no custom name": the sequence page then shows its generic heading and a worksheet
-- its numbered default ("Sequenz 2" / "Sequence 2"), so existing rows need no backfill.
--
-- The name is searchable: search_vector is regenerated with it at the same weight as the task
-- description, keeping V19's normalization (unaccent + umlaut folding).
--
-- Re-running is safe: the ADD is guarded and the vector is dropped before it is recreated.

ALTER TABLE public.tiered_task_sequences
    ADD COLUMN IF NOT EXISTS title text;

ALTER TABLE public.tiered_task_sequences DROP COLUMN IF EXISTS search_vector;
ALTER TABLE public.tiered_task_sequences ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', public.search_normalize(COALESCE(title, ''))), 'A') ||
    setweight(to_tsvector('simple', public.search_normalize(COALESCE(task_description, ''))), 'A') ||
    setweight(to_tsvector('simple', public.search_normalize((COALESCE(semantic_summary, ''::character varying))::text)), 'B')
) STORED;
CREATE INDEX IF NOT EXISTS idx_sequences_fts ON public.tiered_task_sequences USING gin (search_vector);
