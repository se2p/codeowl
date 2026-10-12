-- Let an owner delete a sequence without breaking the worksheets it sits in.
--
-- worksheets_sequence.sequence_id is ON DELETE CASCADE (V1), so a real DELETE would pull the
-- sequence out of every worksheet that uses it -- including one students are halfway through,
-- and it would take the tasks, and with them submissions, down too. Deleting must only remove
-- the sequence from where it is *offered*: the library, search, similar-sequence suggestions
-- and the owner's own list. A worksheet that already holds it keeps it.
--
-- deleted_at marks that. Every listing predicate asks "deleted_at IS NULL"; loading a sequence
-- by id, which is what a worksheet does, does not. It also keeps learning events and research
-- data attached to a row that still exists.
--
-- Null for every existing row. Re-running is safe: the ADD is guarded.

ALTER TABLE public.tiered_task_sequences
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;
