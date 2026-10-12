-- ════════════════════════════════════════════════════════════
-- Migration: add fim_uid column to users
-- Links a CodeOwl account to the FIM-Account (LDAP uid) it logs in with.
-- NULL for accounts that log in with email + password only.
-- Idempotent — safe to run on existing DBs and fresh installs.
-- ════════════════════════════════════════════════════════════
SET client_encoding = 'UTF8';

ALTER TABLE users ADD COLUMN IF NOT EXISTS fim_uid VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS users_fim_uid_key ON users (fim_uid);
