-- Reverses migration/V23__sequence_soft_delete.sql.
--
-- Every sequence a user deleted becomes visible again in the library: the marker is the only
-- record that they were deleted. Application code filters on deleted_at, so a backend at V23
-- or later will fail against a database this has run against. Roll the code back with the
-- schema.
--
-- Idempotent -- safe to run against a database that never had the column.

ALTER TABLE public.tiered_task_sequences DROP COLUMN IF EXISTS deleted_at;

DELETE FROM flyway_schema_history WHERE version='23';
