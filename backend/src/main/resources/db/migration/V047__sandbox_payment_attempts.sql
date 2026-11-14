-- PhonePe UAT attempts are separate from fictional click-through payments and from real verified revenue.
-- source_id refers to an individual_demo_orders.id or bulk_requests.id; ownership is checked transactionally.
CREATE TABLE sandbox_payment_attempts (
    id UUID PRIMARY KEY,
    customer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    source_type TEXT NOT NULL CHECK (source_type IN ('INDIVIDUAL', 'BULK')),
    source_id UUID NOT NULL,
    quote_version INTEGER,
    amount_paise BIGINT NOT NULL CHECK (amount_paise >= 100),
    state TEXT NOT NULL CHECK (state IN ('INITIATED', 'PENDING', 'SANDBOX_SUCCESS', 'SANDBOX_FAILED', 'CANCELLED', 'EXPIRED')),
    merchant_order_id TEXT NOT NULL UNIQUE CHECK (length(merchant_order_id) BETWEEN 1 AND 63),
    provider_order_id TEXT,
    transaction_reference TEXT,
    sdk_token TEXT,
    idempotency_key TEXT NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 128),
    provider_state TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (customer_user_id, source_type, source_id, idempotency_key)
);

CREATE INDEX sandbox_payment_attempts_customer_idx
    ON sandbox_payment_attempts (customer_user_id, created_at DESC);
CREATE INDEX sandbox_payment_attempts_seller_idx
    ON sandbox_payment_attempts (seller_id, created_at DESC);
CREATE INDEX sandbox_payment_attempts_source_idx
    ON sandbox_payment_attempts (source_type, source_id, created_at DESC);
-- One in-flight or successful UAT payment per order; failed/expired attempts may be retried with a new key.
CREATE UNIQUE INDEX sandbox_payment_attempts_one_active_idx
    ON sandbox_payment_attempts (source_type, source_id)
    WHERE state IN ('INITIATED', 'PENDING', 'CANCELLED', 'SANDBOX_SUCCESS');
