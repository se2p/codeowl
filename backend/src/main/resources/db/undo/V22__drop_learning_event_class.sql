-- Reverses migration/V22__learning_event_class.sql.
--
-- class_id was derived at write time and lives only in this column, so dropping it loses
-- the class label on every event -- including the backfilled ones, which cannot be
-- reconstructed a second time once a worksheet has been unassigned from a class. The
-- events themselves are untouched; per-class views simply stop being answerable, as they
-- were before V22.
--
-- Application code writes and reads class_id (LearningEventService resolves it,
-- PostgreLearningEventDao inserts and maps it, the admin research page filters on it), so
-- a backend at V22 or later will fail against a database this has run against. Roll the
-- code back with the schema.
--
-- idx_learning_events_sequence goes too. V22 added it, so undoing V22 removes it -- but it
-- was a plain performance fix for a read path that predates this feature, and keeping it
-- costs nothing. Drop the DROP INDEX line below if the rollback is about class_id alone.
--
-- Idempotent -- safe to run against a database that never had the column.

DROP INDEX IF EXISTS idx_learning_events_sequence;

-- Dropping the column would take its index with it; dropping it first keeps this script
-- explicit about everything V22 created.
DROP INDEX IF EXISTS idx_learning_events_class;

ALTER TABLE public.learning_events DROP COLUMN IF EXISTS class_id;

DELETE FROM flyway_schema_history WHERE version='22';
