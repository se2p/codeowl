-- Manual ordering for sequences on a worksheet.
--
-- Until now `worksheets_sequence` carried no ordering column at all: every read ordered by
-- `ws.sequence_id` (see 01_codeowl_backup.sql and PostgreWorksheetDao) -- that is, by the
-- sequence's random UUID. Adding a sequence never appended it to the end of the worksheet, it
-- landed wherever its UUID happened to sort, and there was no way for a teacher to reorder
-- sequences on a worksheet at all.
--
-- `position` is a plain integer per (worksheet_id, sequence_id) row. Adding a sequence now sets
-- it to one past the worksheet's current maximum (0 for the first sequence), so "add" always
-- appends. Reordering rewrites every row's position for that worksheet.
--
-- No uniqueness constraint on (worksheet_id, position): a reorder rewrites every row for a
-- worksheet in one statement, and enforcing uniqueness mid-statement buys nothing here -- ties
-- are broken by sequence_id, which keeps ordering deterministic without needing the write to be
-- transactionally careful about passing through a colliding intermediate state.
--
-- Backfill keeps the previous, UUID-sorted order so existing worksheets do not visually reshuffle
-- the moment this migration runs.
--
-- Re-running is safe: the ADD is guarded, the backfill only touches rows still missing a
-- position, and SET NOT NULL is a no-op once every row already satisfies it.

ALTER TABLE public.worksheets_sequence
    ADD COLUMN IF NOT EXISTS position integer;

UPDATE public.worksheets_sequence ws
SET position = ordered.rn - 1
FROM (
    SELECT worksheet_id, sequence_id,
           ROW_NUMBER() OVER (PARTITION BY worksheet_id ORDER BY sequence_id) AS rn
    FROM public.worksheets_sequence
) ordered
WHERE ws.worksheet_id = ordered.worksheet_id
  AND ws.sequence_id = ordered.sequence_id
  AND ws.position IS NULL;

ALTER TABLE public.worksheets_sequence
    ALTER COLUMN position SET NOT NULL;
