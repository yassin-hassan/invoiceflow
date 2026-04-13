-- V4__add_quotes.sql

CREATE TABLE quotes (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id   UUID         NOT NULL REFERENCES clients(id),
    number      VARCHAR(20)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    issue_date  DATE         NOT NULL,
    expiry_date DATE         NOT NULL,
    notes       TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_quote_number_per_user UNIQUE (user_id, number)
);

CREATE TABLE quote_lines (
    id          UUID           PRIMARY KEY,
    quote_id    UUID           NOT NULL REFERENCES quotes(id) ON DELETE CASCADE,
    product_id  UUID           REFERENCES products(id),
    description VARCHAR(500)   NOT NULL,
    quantity    NUMERIC(10, 2) NOT NULL,
    unit_price  NUMERIC(10, 2) NOT NULL,
    vat_rate    NUMERIC(5, 2)  NOT NULL,
    sort_order  INT            NOT NULL DEFAULT 0
);
