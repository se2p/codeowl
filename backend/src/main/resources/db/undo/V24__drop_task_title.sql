-- Reverses migration/V24__task_title.sql.
--
-- Every custom task name is lost; tasks show their numbered default again. Application code
-- selects the column, so a backend at V24 or later will fail against a database this has run
-- against. Roll the code back with the schema.
--
-- Idempotent -- safe to run against a database that never had the column.

ALTER TABLE public.tasks DROP COLUMN IF EXISTS title;

DELETE FROM flyway_schema_history WHERE version='24';
