-- Reverses migration/V21__task_submission_progress.sql: task_submissions goes back
-- to holding solved arrangements only, the way V8 defined it.
--
-- Destructive, and not symmetrically so. V21 added a state the table could not hold
-- before -- a row for a task a student has started but not solved -- and there is no
-- pre-V21 shape for those rows to fold back into. They are deleted, so a student with
-- unfinished work on a task loses it and reopening reshuffles the puzzle, exactly as
-- it did before V21. Solved arrangements are untouched.
--
-- Application code reads and writes `solved` (PostgreTaskSubmissionDao selects it,
-- upserts it and relies on solved_at being nullable), so a backend at V21 or later
-- will fail against a database this has run against. Roll the code back with the
-- schema.
--
-- Idempotent -- safe to run against a database that never had the column.

-- Before the column goes, while the in-progress rows can still be identified.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public'
                 AND table_name   = 'task_submissions'
                 AND column_name  = 'solved') THEN
        DELETE FROM public.task_submissions WHERE solved = false;
    END IF;
END $$;

-- The delete above clears every NULL solved_at the DAO can produce: it writes NULL
-- only for an in-progress row, and COALESCEs a real timestamp in the moment such a
-- row is first solved. This covers a row that reached that state some other way --
-- SET NOT NULL fails outright on a single NULL, and updated_at is the closest thing
-- to a solve time still on the row.
UPDATE public.task_submissions SET solved_at = updated_at WHERE solved_at IS NULL;

ALTER TABLE public.task_submissions
    ALTER COLUMN solved_at SET NOT NULL;

ALTER TABLE public.task_submissions
    DROP COLUMN IF EXISTS solved;

DELETE FROM flyway_schema_history WHERE version='21';
