-- V12__audit_logs.sql
-- Persistent audit trail for security-sensitive and state-changing actions.
-- See _private/plans/admin-and-audit.md for the schema rationale (single table,
-- JSONB details, three indexes covering the expected admin-UI query shapes).

CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY,
    occurred_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    actor_user_id UUID         REFERENCES users(id) ON DELETE SET NULL,
    actor_email   VARCHAR(255),
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64),
    resource_id   VARCHAR(64),
    ip_address    VARCHAR(64),
    user_agent    VARCHAR(512),
    details       JSONB
);

CREATE INDEX idx_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_logs_actor       ON audit_logs (actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_action      ON audit_logs (action, occurred_at DESC);
