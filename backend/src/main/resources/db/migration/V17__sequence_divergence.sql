-- A copy that has been edited is no longer a duplicate.
--
-- Migration 16 keeps copies out of the sequence library with `copied_from IS NULL`, so
-- that a marketplace which makes copying routine does not bury every original under
-- clones of itself. That rule cannot tell an untouched clone from a copy its new owner
-- has reworked into different material: copied_from is written once at copy time and
-- never cleared, so an edited copy stayed hidden for good, and the teacher who wrote it
-- could not find their own work in the library.
--
-- diverged_at records the first edit to a copy. Listing then asks
-- "copied_from IS NULL OR diverged_at IS NOT NULL" -- originals, plus copies that have
-- become something else. Provenance survives either way: copied_from still points at the
-- source, which is what the worksheet marketplace's "already copied" badge reads, so
-- recording divergence costs nothing that the copy chain needs.
--
-- Null for every existing row, copies edited before this migration included. Nothing
-- recorded when those edits happened and inventing a timestamp would be a fiction; they
-- reappear on their next edit, which is the first moment there is a true answer.
--
-- Re-running is safe: the ADD is guarded, and the indexes are created and dropped by name.

ALTER TABLE public.tiered_task_sequences
    ADD COLUMN IF NOT EXISTS diverged_at timestamptz;

-- The listing predicate has changed, so migration 16's partial index no longer matches it
-- and would simply go unused. Same shape, widened predicate.
CREATE INDEX IF NOT EXISTS idx_sequences_listed_recent
    ON public.tiered_task_sequences (created_at DESC)
    WHERE copied_from IS NULL OR diverged_at IS NOT NULL;

DROP INDEX IF EXISTS public.idx_sequences_original_recent;
