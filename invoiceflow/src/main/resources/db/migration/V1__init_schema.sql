-- V1__init_schema.sql
-- Initial schema for InvoiceFlow

CREATE TABLE addresses (
    id          UUID            PRIMARY KEY,
    street      VARCHAR(255),
    postal_code VARCHAR(20),
    city        VARCHAR(100),
    country     VARCHAR(100)
);

CREATE TABLE users (
    id                            UUID         PRIMARY KEY,
    email                         VARCHAR(255) NOT NULL UNIQUE,
    password_hash                 VARCHAR(255) NOT NULL,
    first_name                    VARCHAR(100),
    last_name                     VARCHAR(100),
    company_name                  VARCHAR(255),
    phone                         VARCHAR(50),
    vat_number                    VARCHAR(50),
    logo_url                      VARCHAR(500),
    preferred_language            VARCHAR(5)   NOT NULL DEFAULT 'FR',
    billing_address_id            UUID         REFERENCES addresses(id),
    is_active                     BOOLEAN      NOT NULL DEFAULT TRUE,
    is_email_verified             BOOLEAN      NOT NULL DEFAULT FALSE,
    is_2fa_enabled                BOOLEAN      NOT NULL DEFAULT FALSE,
    two_fa_phone                  VARCHAR(50),
    failed_attempts               INT          NOT NULL DEFAULT 0,
    locked_until                  TIMESTAMP,
    created_at                    TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_login_at                 TIMESTAMP
);

-- ─── Verification tables ─────────────────────────────────────────────────────

CREATE TABLE account_verifications (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE reset_password_verifications (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE two_factor_verifications (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    code       VARCHAR(10) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- ─── RBAC ────────────────────────────────────────────────────────────────────

CREATE TABLE roles (
    id   UUID        PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE user_roles (
    user_id     UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     UUID      NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    assigned_by UUID      REFERENCES users(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE permissions (
    id   UUID         PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ─── Events ──────────────────────────────────────────────────────────────────

CREATE TABLE events (
    id          UUID         PRIMARY KEY,
    type        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE user_events (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id   UUID         NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    device     VARCHAR(255),
    browser    VARCHAR(255),
    ip_address VARCHAR(100),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
