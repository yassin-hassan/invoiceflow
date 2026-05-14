-- V10__invoice_stripe_fields.sql

ALTER TABLE invoices
    ADD COLUMN stripe_payment_link_id VARCHAR(255),
    ADD COLUMN stripe_payment_link_url TEXT,
    ADD COLUMN stripe_payment_link_created_at TIMESTAMP;

ALTER TABLE payments
    ADD COLUMN stripe_payment_intent_id VARCHAR(255),
    ADD COLUMN stripe_checkout_session_id VARCHAR(255);

CREATE UNIQUE INDEX uq_payments_stripe_payment_intent
    ON payments(stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;
