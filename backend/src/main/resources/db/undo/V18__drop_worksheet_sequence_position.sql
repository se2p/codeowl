-- Reverses migration/V18__worksheet_sequence_position.sql. `position` -- backfill
-- and the later SET NOT NULL alike -- lives only in this column, so dropping it
-- loses nothing that existed before V18; reads simply go back to ordering by
-- `sequence_id`, as PostgreWorksheetDao did beforehand.

ALTER TABLE public.worksheets_sequence DROP COLUMN IF EXISTS position;

DELETE FROM flyway_schema_history WHERE version='18';
