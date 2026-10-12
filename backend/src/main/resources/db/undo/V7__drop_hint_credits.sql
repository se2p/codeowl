-- Reverses migration/V7__hint_credits.sql.

DROP INDEX IF EXISTS idx_hint_usage_user_task;

ALTER TABLE public.worksheets DROP CONSTRAINT IF EXISTS worksheets_hint_credits_positive;
ALTER TABLE public.worksheets DROP COLUMN IF EXISTS hint_credits_per_task;

DELETE FROM flyway_schema_history WHERE version='7';
