-- Whether a worksheet assignment has been published to the class.
--
-- A newly assigned worksheet is hidden until the teacher publishes it, so
-- students cannot stumble across one that is still being put together. That is
-- the opposite of how assignments behaved before, which is why the default is
-- applied in two steps:
--
--   ADD COLUMN ... DEFAULT true   gives every existing assignment `true`, so
--                                 nothing that students can see today disappears
--   ALTER COLUMN ... DEFAULT false makes every future assignment start hidden
--
-- Re-running is safe: the ADD is skipped once the column exists, and setting the
-- default again changes nothing. Notably it does not touch existing values, so a
-- worksheet a teacher has deliberately left unpublished stays that way.
--
-- This does not replace release_at. Publishing is the manual switch; release_at
-- still opens an assignment automatically when that moment arrives, so a teacher
-- can either publish by hand or schedule it, and either is enough.

ALTER TABLE public.class_worksheets
    ADD COLUMN IF NOT EXISTS published boolean NOT NULL DEFAULT true;

ALTER TABLE public.class_worksheets
    ALTER COLUMN published SET DEFAULT false;
