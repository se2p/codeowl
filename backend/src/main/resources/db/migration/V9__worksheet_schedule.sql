-- Release date, submission deadline and grace period for a worksheet as it is
-- assigned to one class.
--
-- These live on the assignment, not on the worksheet: the same worksheet can be
-- given to several classes on different schedules.
--
-- Every column is nullable, and null means "no constraint" in each case:
--   release_at    null -> available as soon as it is assigned
--   deadline_at   null -> stays open indefinitely
--   grace_minutes null -> the deadline is sharp
--
-- timestamptz rather than the plain timestamp used elsewhere in this schema: a
-- deadline is a real instant that a class shares, and storing it without a zone
-- would make it mean different things to a server and a browser in different
-- offsets. The older columns are left as they are.

ALTER TABLE public.class_worksheets
    ADD COLUMN IF NOT EXISTS release_at    timestamptz,
    ADD COLUMN IF NOT EXISTS deadline_at   timestamptz,
    ADD COLUMN IF NOT EXISTS grace_minutes integer;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'class_worksheets_grace_non_negative'
    ) THEN
        ALTER TABLE public.class_worksheets
            ADD CONSTRAINT class_worksheets_grace_non_negative
            CHECK (grace_minutes IS NULL OR grace_minutes >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'class_worksheets_deadline_after_release'
    ) THEN
        -- A deadline before the release would make the worksheet unreachable:
        -- closed by the time anyone could open it.
        ALTER TABLE public.class_worksheets
            ADD CONSTRAINT class_worksheets_deadline_after_release
            CHECK (release_at IS NULL OR deadline_at IS NULL OR deadline_at > release_at);
    END IF;
END
$$;
