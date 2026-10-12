-- Reverses migration/V4__must_change_password.sql.

ALTER TABLE users DROP COLUMN IF EXISTS must_change_password;

DELETE FROM flyway_schema_history WHERE version='4';
