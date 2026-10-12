-- Worksheet marketplace: visibility, an honest creation time, and provenance.
--
-- Visibility is three-valued rather than a published flag. Every worksheet is
-- already readable by anyone holding its link -- that is how a teacher hands one
-- to a colleague and how a student opens one without an account -- so calling
-- that state "not published" and defaulting new rows to hidden would break every
-- link already in circulation. UNLISTED keeps that behaviour and stays the
-- default; PUBLIC is the opt-in that adds being *findable*; PRIVATE is new.
--
-- Re-running is safe: every ADD is guarded, and the constraint and index are
-- created only when absent.

ALTER TABLE public.worksheets
    ADD COLUMN IF NOT EXISTS visibility  varchar(16) NOT NULL DEFAULT 'UNLISTED',
    ADD COLUMN IF NOT EXISTS created_at  timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS copied_from uuid,
    ADD COLUMN IF NOT EXISTS copied_at   timestamptz;

-- Provenance on the sequence too. Nothing displays it: it exists so the library
-- search can tell an original from one of its clones, because a marketplace that
-- makes copying routine would otherwise fill the sequence library with
-- duplicates of everything anyone ever copied.
ALTER TABLE public.tiered_task_sequences
    ADD COLUMN IF NOT EXISTS copied_from uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_worksheets_visibility'
    ) THEN
        ALTER TABLE public.worksheets
            ADD CONSTRAINT chk_worksheets_visibility
            CHECK (visibility IN ('PRIVATE', 'UNLISTED', 'PUBLIC'));
    END IF;

    -- ON DELETE SET NULL, not CASCADE: deleting an original must not take the
    -- copies with it. Whoever copied it owns their copy outright; all that is
    -- lost is the record of where it came from.
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_worksheets_copied_from'
    ) THEN
        ALTER TABLE public.worksheets
            ADD CONSTRAINT fk_worksheets_copied_from
            FOREIGN KEY (copied_from) REFERENCES public.worksheets(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_sequences_copied_from'
    ) THEN
        ALTER TABLE public.tiered_task_sequences
            ADD CONSTRAINT fk_sequences_copied_from
            FOREIGN KEY (copied_from) REFERENCES public.tiered_task_sequences(id) ON DELETE SET NULL;
    END IF;
END
$$;

-- The marketplace's default listing is "public, newest first", which is this
-- index exactly.
CREATE INDEX IF NOT EXISTS idx_worksheets_visibility_created
    ON public.worksheets (visibility, created_at DESC);

-- Existing rows all took the DEFAULT now(), which would make every worksheet
-- ever written look like it appeared the moment this migration ran, and "newest
-- first" meaningless on day one. The sequences a worksheet holds do carry
-- created_at, so the earliest of those is a far better guess. Worksheets with no
-- sequences keep the default -- there is nothing better to infer from.
UPDATE public.worksheets w
   SET created_at = src.earliest
  FROM (
        SELECT ws.worksheet_id, MIN(s.created_at) AS earliest
          FROM public.worksheets_sequence ws
          JOIN public.tiered_task_sequences s ON s.id = ws.sequence_id
         WHERE s.created_at IS NOT NULL
         GROUP BY ws.worksheet_id
       ) src
 WHERE w.id = src.worksheet_id
   AND src.earliest < w.created_at;
