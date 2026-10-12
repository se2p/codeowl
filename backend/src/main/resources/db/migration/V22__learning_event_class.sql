-- Give a learning event the class it happened in.
--
-- learning_events has carried user_id, task_id and sequence_id since V1 and nothing else
-- that locates the work. That is fine for "what happened on this task" and useless for
-- "what did class 7a do", which is the question the admin research page exists to answer.
-- Neither available join answers it:
--
--   via user_id -> class_students : dead for exactly the rows that matter, because
--     LearningEventService nulls user_id for a student whose classes all have
--     tracking_enabled = false. Those events would simply vanish from a per-class view.
--   via task -> sequence -> worksheet -> class : survives anonymisation, but a sequence
--     can sit in several worksheets and a worksheet can be assigned to several classes,
--     so one event fans out to N classes and every count doubles.
--
-- feedback_logs solved this in V1 by carrying class_id resolved at write time, kept even
-- when user_id is not -- which is what makes the class progress views possible at all.
-- This is the same move for learning_events: identity is dropped for an untracked class,
-- class context is not.
--
-- The privacy cost is real and worth stating: an anonymised row now says "someone in this
-- class, at this moment" rather than "someone". In a class of twenty that is a smaller
-- anonymity set than before. It is the tradeoff feedback_logs already makes.
--
-- ON DELETE SET NULL, not CASCADE: deleting a class must not delete the research record of
-- the work done in it. It only loses the label.
--
-- Idempotent -- safe on a database that already has the column.

ALTER TABLE public.learning_events
    ADD COLUMN IF NOT EXISTS class_id uuid REFERENCES public.classes(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_learning_events_class
    ON public.learning_events (class_id);

-- findBySequenceId has been the hottest read path since V1 and has never had an index;
-- the research page filters on it as well, over the whole table rather than one sequence.
CREATE INDEX IF NOT EXISTS idx_learning_events_sequence
    ON public.learning_events (sequence_id);

-- Backfill, best effort, for rows written before this column existed.
--
-- Only rows that still have a user_id can be placed: the class is the one that both enrols
-- the student and has this task assigned. Where that matches nothing (a task opened by
-- direct link, a worksheet since unassigned) or is ambiguous, the row keeps NULL rather
-- than being given a guess -- an unlabelled event is recoverable, a wrongly labelled one
-- is not.
--
-- Rows anonymised at write time stay NULL permanently. Their class is not recoverable from
-- anything left in the database, and pretending otherwise would attribute one student's
-- work to a class they may not be in.
UPDATE public.learning_events e
SET class_id = c.class_id
FROM (
    SELECT DISTINCT ON (cs.student_id, t.id)
           cs.student_id,
           t.id AS task_id,
           cw.class_id
    FROM class_students cs
    JOIN class_worksheets cw    ON cw.class_id     = cs.class_id
    JOIN worksheets_sequence ws ON ws.worksheet_id = cw.worksheet_id
    JOIN tasks t                ON t.sequence_id   = ws.sequence_id
    ORDER BY cs.student_id, t.id, cw.assigned_at
) c
WHERE e.class_id IS NULL
  AND e.user_id IS NOT NULL
  AND e.user_id = c.student_id
  AND e.task_id = c.task_id;
