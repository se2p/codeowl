-- Reverses migration/V6__split_plan.sql.

ALTER TABLE tasks DROP COLUMN IF EXISTS split_plan;

DELETE FROM flyway_schema_history WHERE version='6';
