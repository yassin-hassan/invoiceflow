-- V3__add_products.sql

CREATE TABLE products (
    id          UUID           PRIMARY KEY,
    user_id     UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255)   NOT NULL,
    description TEXT,
    reference   VARCHAR(100)   NOT NULL,
    unit_price  NUMERIC(10, 2) NOT NULL,
    vat_rate    NUMERIC(5, 2)  NOT NULL,
    unit        VARCHAR(50)    NOT NULL,
    is_active   BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_product_reference_per_user UNIQUE (user_id, reference)
);
