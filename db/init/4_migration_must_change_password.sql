-- ════════════════════════════════════════════════════════════
-- Migration: add must_change_password column to users
-- Idempotent — safe to run on existing DBs and fresh installs.
-- ════════════════════════════════════════════════════════════
SET client_encoding = 'UTF8';

DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'must_change_password'
    ) THEN
        ALTER TABLE users
            ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;
