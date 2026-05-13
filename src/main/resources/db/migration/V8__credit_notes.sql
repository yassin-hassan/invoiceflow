-- V8__credit_notes.sql

CREATE TABLE credit_notes (
    id                    UUID         PRIMARY KEY,
    user_id               UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_invoice_id   UUID         NOT NULL REFERENCES invoices(id),
    number                VARCHAR(20),
    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    issue_date            DATE         NOT NULL,
    reason                TEXT         NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    issued_at             TIMESTAMP,

    CONSTRAINT uq_credit_note_original_invoice UNIQUE (original_invoice_id),
    CONSTRAINT uq_credit_note_number_per_user UNIQUE (user_id, number)
);

CREATE TABLE credit_note_lines (
    id                UUID           PRIMARY KEY,
    credit_note_id    UUID           NOT NULL REFERENCES credit_notes(id) ON DELETE CASCADE,
    invoice_line_id   UUID           NOT NULL REFERENCES invoice_lines(id) ON DELETE RESTRICT,
    quantity          NUMERIC(10, 2) NOT NULL,
    sort_order        INT            NOT NULL DEFAULT 0
);

CREATE INDEX idx_credit_notes_user ON credit_notes(user_id);
CREATE INDEX idx_credit_note_lines_credit_note ON credit_note_lines(credit_note_id);
