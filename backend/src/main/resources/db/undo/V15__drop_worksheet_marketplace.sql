-- Reverses migration/V15__worksheet_marketplace.sql. `created_at` on
-- `worksheets` is itself new in V15 (there was no prior column of that name to
-- preserve), so dropping it loses nothing that existed before this migration --
-- including the value the created_at backfill UPDATE computed, which only ever
-- existed inside this new column.
--
-- Run after undo/V16 and undo/V17 (or before either has run): V16 indexed
-- tiered_task_sequences on `copied_from IS NULL`, and V17 replaced that index
-- with one keyed on `diverged_at` too -- both need to be gone before this drops
-- the column underneath them, and undo/V16 and undo/V17 already take care of
-- that when run in the normal reverse order.

DROP INDEX IF EXISTS idx_worksheets_visibility_created;

ALTER TABLE public.tiered_task_sequences DROP CONSTRAINT IF EXISTS fk_sequences_copied_from;
ALTER TABLE public.worksheets DROP CONSTRAINT IF EXISTS fk_worksheets_copied_from;
ALTER TABLE public.worksheets DROP CONSTRAINT IF EXISTS chk_worksheets_visibility;

ALTER TABLE public.tiered_task_sequences DROP COLUMN IF EXISTS copied_from;

ALTER TABLE public.worksheets
    DROP COLUMN IF EXISTS visibility,
    DROP COLUMN IF EXISTS created_at,
    DROP COLUMN IF EXISTS copied_from,
    DROP COLUMN IF EXISTS copied_at;

DELETE FROM flyway_schema_history WHERE version='15';
