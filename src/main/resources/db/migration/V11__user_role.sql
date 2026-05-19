-- V11__user_role.sql
-- Adds a single role per user backing the Role enum (USER, ADMIN).
-- The existing roles / user_roles / permissions tables from V1 were never wired up; we use a
-- single VARCHAR column instead per _private/plans/admin-and-audit.md.

ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
