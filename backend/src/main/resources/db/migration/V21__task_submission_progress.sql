-- Autosave a student's work in progress, not just their solved arrangement.
--
-- 08_migration_task_submissions.sql stored only solved arrangements: "Work in progress is
-- not autosaved." That left a student who closed the tab, navigated to another page or
-- refreshed with nothing to come back to -- reopening reshuffled the puzzle and their work
-- was gone. The same row now carries an in-progress arrangement too.
--
-- `solved` is what keeps the two apart. It defaults to true because every row that exists
-- when this runs is a solved arrangement, and because "who has solved this task" -- the
-- question idx_task_submissions_task exists to answer -- must not start counting students
-- who merely opened it. Once true it stays true: a student who solves a task and then pulls
-- it apart has still solved it.
--
-- solved_at becomes nullable, since an in-progress row has no solve time. updated_at is the
-- column that moves on every autosave.
--
-- Research is deliberately not stored here. How the student got to this arrangement goes to
-- learning_events, which anonymises user_id for classes with tracking disabled and so cannot
-- answer "whose work is this" -- the reason this table exists separately in the first place.
--
-- Not covered, because the arrangement is recorded as the visible text of each block: values
-- typed into gap inputs (AP30) and the placement of inline-split chips (AP31) contribute no
-- textContent and are neither saved nor restored.
--
-- Idempotent -- safe on a database that already has the column.

ALTER TABLE public.task_submissions
    ADD COLUMN IF NOT EXISTS solved boolean NOT NULL DEFAULT true;

ALTER TABLE public.task_submissions
    ALTER COLUMN solved_at DROP NOT NULL;
