-- Reverses migration/V16__worksheet_search.sql.
--
-- idx_sequences_original_recent is dropped, not recreated: V17 replaced it with
-- idx_sequences_listed_recent and undo/V17 (run before this one, in the normal
-- reverse order) already put it back for exactly that step. Recreating it again
-- here would only be right if this ran without V17 ever having applied, which
-- is not how undo scripts are meant to be used -- one step at a time, from the
-- highest version actually applied.

DROP INDEX IF EXISTS idx_sequences_original_recent;
DROP INDEX IF EXISTS idx_worksheets_search_vector;

ALTER TABLE public.worksheets DROP COLUMN IF EXISTS search_vector;

DELETE FROM flyway_schema_history WHERE version='16';
