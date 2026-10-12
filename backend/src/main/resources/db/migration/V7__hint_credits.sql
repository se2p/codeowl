-- Per-worksheet hint credit budget.
--
-- NULL means unlimited. That is deliberately the default: every worksheet was
-- effectively unlimited before this column existed, so existing rows keep the
-- behaviour their teachers already saw instead of being capped silently
-- mid-term. Teachers opt into a budget explicitly.
--
-- Spending itself is not stored here. It is derived from the hint_usage rows
-- that were already being written, which is what makes a budget survive a task
-- reset: resetting the puzzle does not delete the usage history.

ALTER TABLE public.worksheets
    ADD COLUMN IF NOT EXISTS hint_credits_per_task INTEGER DEFAULT NULL;

-- ADD CONSTRAINT has no IF NOT EXISTS, so guard it for re-runs.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'worksheets_hint_credits_positive'
    ) THEN
        ALTER TABLE public.worksheets
            ADD CONSTRAINT worksheets_hint_credits_positive
            CHECK (hint_credits_per_task IS NULL OR hint_credits_per_task > 0);
    END IF;
END
$$;

-- The credit check runs on every hint request and counts a student's rows for
-- one task, so make that lookup cheap.
CREATE INDEX IF NOT EXISTS idx_hint_usage_user_task
    ON public.hint_usage (user_id, task_id);
