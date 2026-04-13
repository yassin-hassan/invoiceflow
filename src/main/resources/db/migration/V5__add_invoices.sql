-- V5__add_invoices.sql

CREATE TABLE invoices (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id      UUID         NOT NULL REFERENCES clients(id),
    quote_id       UUID         REFERENCES quotes(id),
    number         VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    issue_date     DATE         NOT NULL,
    due_date       DATE         NOT NULL,
    payment_terms  TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_invoice_number_per_user UNIQUE (user_id, number)
);

CREATE TABLE invoice_lines (
    id          UUID           PRIMARY KEY,
    invoice_id  UUID           NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    product_id  UUID           REFERENCES products(id),
    description VARCHAR(500)   NOT NULL,
    quantity    NUMERIC(10, 2) NOT NULL,
    unit_price  NUMERIC(10, 2) NOT NULL,
    vat_rate    NUMERIC(5, 2)  NOT NULL,
    sort_order  INT            NOT NULL DEFAULT 0
);

CREATE TABLE payments (
    id         UUID           PRIMARY KEY,
    invoice_id UUID           NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    amount     NUMERIC(10, 2) NOT NULL,
    method     VARCHAR(50)    NOT NULL,
    paid_at    DATE           NOT NULL,
    notes      TEXT,
    created_at TIMESTAMP      NOT NULL DEFAULT NOW()
);
