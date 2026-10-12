-- Let the owner of a sequence (or an admin) give each of its tasks a name of their own.
--
-- Null means "no custom name": the UI then falls back to the numbered default ("Aufgabe 2" /
-- "Task 2"), derived from tier_level, so existing rows need no backfill and the default follows
-- the viewer's language instead of being frozen into the row.
--
-- Re-running is safe: the ADD is guarded.

ALTER TABLE public.tasks
    ADD COLUMN IF NOT EXISTS title text;
