CREATE TABLE individual_demo_orders (
    id UUID PRIMARY KEY,
    customer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    status TEXT NOT NULL DEFAULT 'DEMO_SAVED' CHECK (status = 'DEMO_SAVED'),
    demo_only BOOLEAN NOT NULL DEFAULT TRUE CHECK (demo_only),
    payment_verified BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT payment_verified),
    subtotal_paise BIGINT NOT NULL CHECK (subtotal_paise >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX individual_demo_orders_customer_idx ON individual_demo_orders (customer_user_id, created_at DESC);

CREATE TABLE individual_demo_order_lines (
    order_id UUID NOT NULL REFERENCES individual_demo_orders(id) ON DELETE RESTRICT,
    seller_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    item_name_snapshot TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    unit_price_paise BIGINT NOT NULL CHECK (unit_price_paise >= 0),
    PRIMARY KEY (order_id, item_id),
    FOREIGN KEY (seller_id, item_id) REFERENCES menu_items(seller_id, id) ON DELETE RESTRICT
);
