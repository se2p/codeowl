-- Reverses migration/V9__worksheet_schedule.sql.

ALTER TABLE public.class_worksheets DROP CONSTRAINT IF EXISTS class_worksheets_deadline_after_release;
ALTER TABLE public.class_worksheets DROP CONSTRAINT IF EXISTS class_worksheets_grace_non_negative;

ALTER TABLE public.class_worksheets
    DROP COLUMN IF EXISTS release_at,
    DROP COLUMN IF EXISTS deadline_at,
    DROP COLUMN IF EXISTS grace_minutes;

DELETE FROM flyway_schema_history WHERE version='9';
