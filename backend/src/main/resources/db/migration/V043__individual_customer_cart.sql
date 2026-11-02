-- A customer-owned draft. Version checks prevent stale devices from silently overwriting one another.
CREATE TABLE individual_customer_carts (
    customer_user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE individual_customer_cart_lines (
    customer_user_id UUID NOT NULL REFERENCES individual_customer_carts(customer_user_id) ON DELETE CASCADE,
    seller_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    PRIMARY KEY (customer_user_id, item_id),
    FOREIGN KEY (seller_id, item_id) REFERENCES menu_items(seller_id, id) ON DELETE RESTRICT
);

ALTER TABLE individual_demo_orders
    ADD COLUMN idempotency_key TEXT,
    ADD COLUMN idempotency_hash BYTEA;
CREATE UNIQUE INDEX individual_demo_orders_idempotency_idx
    ON individual_demo_orders(customer_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
