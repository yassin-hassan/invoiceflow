-- V2__add_clients.sql

CREATE TABLE clients (
    id                 UUID         PRIMARY KEY,
    user_id            UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name               VARCHAR(255) NOT NULL,
    email              VARCHAR(255) NOT NULL,
    phone              VARCHAR(50),
    vat_number         VARCHAR(50),
    billing_address_id UUID         REFERENCES addresses(id),
    notes              TEXT,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_client_email_per_user UNIQUE (user_id, email)
);
