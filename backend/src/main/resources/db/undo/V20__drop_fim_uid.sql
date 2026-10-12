-- ════════════════════════════════════════════════════════════
-- Undo V20: remove the fim_uid column from users
-- Reverses the schema change only. The FIM-Account links themselves are data,
-- and dropping the column destroys them -- there is no way to recover which
-- CodeOwl account belonged to which LDAP uid afterwards. Dump users first if
-- those links still matter.
-- Application code reads fim_uid (AuthService, PostgreUserDao, UserRowMapper),
-- so a backend at V20 or later will not start against a database this has run
-- against. Roll the code back together with the schema.
-- Idempotent -- safe to run against a database that never had the column.
-- ════════════════════════════════════════════════════════════
SET client_encoding = 'UTF8';

-- Dropping the column would take the index with it; dropping it first keeps the
-- undo explicit about everything V20 created.
DROP INDEX IF EXISTS users_fim_uid_key;

ALTER TABLE users DROP COLUMN IF EXISTS fim_uid;
