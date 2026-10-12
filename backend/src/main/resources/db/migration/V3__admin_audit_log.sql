-- ════════════════════════════════════════════════════════════
-- Migration: admin_audit_log table
-- Tracks all admin-initiated database access operations
-- (full backups, research exports, future high-sensitivity ops)
-- Idempotent — safe to run on existing DBs and fresh installs.
-- ════════════════════════════════════════════════════════════
SET client_encoding = 'UTF8';

DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'admin_audit_log'
    ) THEN
        CREATE TABLE admin_audit_log (
            id          BIGSERIAL PRIMARY KEY,
            admin_id    UUID NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
            action      VARCHAR(64) NOT NULL,
            ip_address  VARCHAR(45),
            user_agent  TEXT,
            bytes_sent  BIGINT,
            rows_sent   BIGINT,
            success     BOOLEAN NOT NULL,
            error_msg   TEXT,
            created_at  TIMESTAMP NOT NULL DEFAULT NOW()
        );
        CREATE INDEX idx_admin_audit_admin_time
            ON admin_audit_log(admin_id, created_at DESC);
        CREATE INDEX idx_admin_audit_action_time
            ON admin_audit_log(action, created_at DESC);
    END IF;
END $$;
