-- V9__credit_notes_drop_unique_invoice.sql

ALTER TABLE credit_notes
    DROP CONSTRAINT uq_credit_note_original_invoice;

CREATE INDEX idx_credit_notes_original_invoice ON credit_notes(original_invoice_id);
