-- Reverses migration/V12__curriculum_education_level.sql. Every existing row
-- took the DEFAULT ('SCHOOL'), so there is no prior value to restore -- dropping
-- the column removes everything V12 added.

DROP INDEX IF EXISTS idx_curriculum_topics_education_level;
ALTER TABLE curriculum_topics DROP CONSTRAINT IF EXISTS curriculum_topics_education_level_check;
ALTER TABLE curriculum_topics DROP COLUMN IF EXISTS education_level;

DELETE FROM flyway_schema_history WHERE version='12';
