-- ════════════════════════════════════════════════════════════════════
-- 12_migration_curriculum_education_level.sql
-- Tell school and university curriculum entries apart
-- ════════════════════════════════════════════════════════════════════
--
-- The curriculum tree was built for the Bavarian LehrplanPLUS, where every
-- top-level entry is a Jahrgangsstufe (grade_level + track). University use
-- needs entries that are modules instead — "Programmieren 1" — with no grade
-- number at all, and a way to keep the two apart in the topic list.
--
-- education_level marks which world a topic belongs to. Everything that
-- exists today is school curriculum, so the column defaults to 'SCHOOL' and
-- the backfill is the default itself.
--
-- Sub-topics carry their parent's value (the backend copies it on insert),
-- the same way they already carry grade_level and track. That keeps a row
-- self-describing, so filtering never needs a join to the parent.
--
-- A module is a top-level topic with grade_level NULL: the curriculum page
-- already falls back to the name column for those, so "Programmieren 1"
-- displays as itself rather than as a derived "Jgst. N — track" label.
--
-- HOW TO RUN (existing / server DB — db/init only runs on a fresh volume):
--   docker exec -i codeowl-clean-db psql -U postgres -d codeowl < db/init/12_migration_curriculum_education_level.sql
--   psql "postgresql://USER:PASS@HOST:5432/codeowl" -f db/init/12_migration_curriculum_education_level.sql
--
-- VERIFY afterwards (every row classified, none outside the two values):
--   SELECT education_level, count(*) FROM curriculum_topics GROUP BY 1;
--
-- IDEMPOTENT: ADD COLUMN IF NOT EXISTS + a guarded constraint — safe to re-run.
-- ════════════════════════════════════════════════════════════════════

SET client_encoding TO 'UTF8';

BEGIN;

ALTER TABLE curriculum_topics
    ADD COLUMN IF NOT EXISTS education_level varchar(20) NOT NULL DEFAULT 'SCHOOL';

COMMENT ON COLUMN curriculum_topics.education_level IS
    'SCHOOL (Jahrgangsstufen, LehrplanPLUS) or UNIVERSITY (modules). Sub-topics repeat their parent''s value.';

-- ADD CONSTRAINT has no IF NOT EXISTS, so guard it explicitly.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'curriculum_topics_education_level_check'
    ) THEN
        ALTER TABLE curriculum_topics
            ADD CONSTRAINT curriculum_topics_education_level_check
            CHECK (education_level IN ('SCHOOL', 'UNIVERSITY'));
    END IF;
END
$$;

-- Rows that predate the column: the whole seeded curriculum is LehrplanPLUS.
UPDATE curriculum_topics SET education_level = 'SCHOOL' WHERE education_level IS NULL;

CREATE INDEX IF NOT EXISTS idx_curriculum_topics_education_level
    ON curriculum_topics (education_level);

COMMIT;
