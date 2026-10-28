ALTER TABLE individual_demo_orders DROP CONSTRAINT individual_demo_orders_status_check;
ALTER TABLE individual_demo_orders ADD CONSTRAINT individual_demo_orders_status_check CHECK (status IN (
    'DEMO_SAVED', 'DEMO_PAYMENT_PENDING', 'DEMO_PAYMENT_FAILED', 'DEMO_PAYMENT_SIMULATED',
    'DEMO_ACCEPTED', 'DEMO_REJECTED', 'DEMO_READY', 'DEMO_COMPLETED'));
ALTER TABLE individual_demo_orders ADD COLUMN demo_payment_outcome TEXT NOT NULL DEFAULT 'NOT_ATTEMPTED'
    CHECK (demo_payment_outcome IN ('NOT_ATTEMPTED', 'PENDING', 'FAILURE', 'SUCCESS'));
ALTER TABLE individual_demo_orders ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX individual_demo_orders_seller_idx ON individual_demo_orders (seller_id, created_at DESC);
