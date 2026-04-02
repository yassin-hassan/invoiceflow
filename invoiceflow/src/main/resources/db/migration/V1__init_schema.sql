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
    id                            UUID            PRIMARY KEY,
    email                         VARCHAR(255)    NOT NULL UNIQUE,
    password_hash                 VARCHAR(255)    NOT NULL,
    role                          VARCHAR(20)     NOT NULL DEFAULT 'USER',
    first_name                    VARCHAR(100),
    last_name                     VARCHAR(100),
    company_name                  VARCHAR(255),
    phone                         VARCHAR(50),
    vat_number                    VARCHAR(50),
    logo_url                      VARCHAR(500),
    preferred_language            VARCHAR(5)      NOT NULL DEFAULT 'FR',
    billing_address_id            UUID            REFERENCES addresses(id),
    is_active                     BOOLEAN         NOT NULL DEFAULT TRUE,
    is_email_verified             BOOLEAN         NOT NULL DEFAULT FALSE,
    email_verification_token      VARCHAR(255),
    email_verification_expires_at TIMESTAMP,
    password_reset_token          VARCHAR(255),
    password_reset_expires_at     TIMESTAMP,
    is_2fa_enabled                BOOLEAN         NOT NULL DEFAULT FALSE,
    two_fa_phone                  VARCHAR(50),
    created_at                    TIMESTAMP       NOT NULL DEFAULT NOW(),
    last_login_at                 TIMESTAMP
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
