-- Full-text search over worksheets.
--
-- The vector covers only the worksheet's own two text fields, because a
-- generated column cannot reach into another table and a worksheet's actual
-- subject matter lives in the sequences it holds. Those are matched at query
-- time instead, with an EXISTS over worksheets_sequence -- the same shape the
-- sequence search already uses to reach a sequence's tasks.
--
-- Weights follow the sequence search: A for the thing a teacher names it, B for
-- the longer description, so a worksheet called "Rekursion" outranks one that
-- merely mentions recursion in passing.
--
-- Re-running is safe: both statements are guarded.

ALTER TABLE public.worksheets
    ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', COALESCE(name, '')), 'A') ||
        setweight(to_tsvector('simple', COALESCE(description, '')), 'B')
    ) STORED;

CREATE INDEX IF NOT EXISTS idx_worksheets_search_vector
    ON public.worksheets USING GIN (search_vector);

-- Copying a worksheet duplicates its sequences, and the library lists every
-- sequence there is. Without somewhere to hang "this one is a duplicate", a
-- marketplace that makes copying routine would bury the originals under clones
-- of themselves. The column arrived with the marketplace migration; this index
-- is what makes filtering on it cheap, since the library's default listing
-- orders by created_at.
CREATE INDEX IF NOT EXISTS idx_sequences_original_recent
    ON public.tiered_task_sequences (created_at DESC)
    WHERE copied_from IS NULL;
