-- ════════════════════════════════════════════════════════════════════
-- 14_migration_lowercase_concept_names.sql
-- One spelling rule for concept names: lowercase
-- ════════════════════════════════════════════════════════════════════
--
-- 13_migration_merge_duplicate_concepts.sql folded the spellings that were
-- genuinely the same concept. What it deliberately left alone were three
-- coarser ones with no single kebab-case counterpart — "Loop", "Collections",
-- "Sequence" — which are still legitimately distinct concepts, just written in
-- the other house style.
--
-- This finishes the job on style alone: the names go lowercase, no rows are
-- merged, and no meaning changes. src/main/resources/concepts/concept-catalog.json
-- has been lowercase all along, so this makes the database agree with it.
--
-- Lowercasing cannot collide here: 13 removed every pair of names differing
-- only by case, and the constraint added below stops them coming back. The
-- INSERT paths in PostgreConceptDAO normalise before writing, so the check is
-- a safety net rather than the mechanism.
--
-- HOW TO RUN (existing / server DB — db/init only runs on a fresh volume):
--   docker exec -i codeowl-clean-db psql -U postgres -d codeowl < db/init/14_migration_lowercase_concept_names.sql
--   psql "postgresql://USER:PASS@HOST:5432/codeowl" -f db/init/14_migration_lowercase_concept_names.sql
--
-- VERIFY afterwards (must return 0 rows):
--   SELECT name FROM concepts WHERE name <> lower(name);
--
-- IDEMPOTENT: the UPDATE matches nothing on a second run and the constraint is
-- guarded. Safe to re-run.
-- ════════════════════════════════════════════════════════════════════

SET client_encoding TO 'UTF8';

BEGIN;

-- Guard rather than assume: if some later data did reintroduce a case-only
-- pair, fail loudly here instead of hitting the unique index halfway through.
DO $$
DECLARE
    clashing text;
BEGIN
    SELECT string_agg(lower(name), ', ') INTO clashing
    FROM (SELECT lower(name) AS name FROM concepts GROUP BY lower(name) HAVING count(*) > 1) dupes;

    IF clashing IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot lowercase concept names: these would collide (%). '
                        'Run 13_migration_merge_duplicate_concepts.sql first, or merge them by hand.',
                        clashing;
    END IF;
END
$$;

UPDATE concepts SET name = lower(name) WHERE name <> lower(name);

-- ADD CONSTRAINT has no IF NOT EXISTS, so guard it explicitly.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'concepts_name_lowercase_check'
    ) THEN
        ALTER TABLE concepts
            ADD CONSTRAINT concepts_name_lowercase_check CHECK (name = lower(name));
    END IF;
END
$$;

COMMENT ON CONSTRAINT concepts_name_lowercase_check ON concepts IS
    'Concept names are lowercase. PostgreConceptDAO normalises on insert; this catches anything that does not.';

COMMIT;
