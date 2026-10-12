-- Reverses migration/V8__task_submissions.sql. The index is defined on the
-- table and goes with it.

DROP TABLE IF EXISTS public.task_submissions;

DELETE FROM flyway_schema_history WHERE version='8';
