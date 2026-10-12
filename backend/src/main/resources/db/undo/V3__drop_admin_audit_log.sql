-- Reverses migration/V3__admin_audit_log.sql. Both indexes are defined on the
-- table and go with it.

DROP TABLE IF EXISTS admin_audit_log;

DELETE FROM flyway_schema_history WHERE version='3';
