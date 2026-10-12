-- A student's solved arrangement of a task, so reopening it shows their work
-- instead of a freshly shuffled puzzle.
--
-- One row per student per task: the arrangement is replaced when they solve the
-- task again, rather than accumulating a history. The research record of how
-- they got there already lives in learning_events and is untouched by this —
-- this table is UI state, deliberately separate, because learning_events nulls
-- user_id when a class has tracking disabled and so cannot say whose work it is.
--
-- Only solved arrangements are stored. Work in progress is not autosaved.

CREATE TABLE IF NOT EXISTS public.task_submissions (
    user_id       uuid NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    task_id       uuid NOT NULL REFERENCES public.tasks(id) ON DELETE CASCADE,

    -- The solution area as the student left it: the visible text of each block,
    -- top to bottom, and the indent level of each. Restoring matches these back
    -- onto the widget's blocks by text.
    block_order   jsonb NOT NULL DEFAULT '[]'::jsonb,
    indent_levels jsonb NOT NULL DEFAULT '[]'::jsonb,

    solved_at     timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, task_id)
);

-- Reopening a task looks the row up by exactly this pair, which the primary key
-- already covers; this index is for the reverse direction, e.g. a teacher
-- asking who has solved a given task.
CREATE INDEX IF NOT EXISTS idx_task_submissions_task
    ON public.task_submissions (task_id);
