-- One account-owned, single-seller draft. A version protects edits from another device.
CREATE TABLE bulk_customer_drafts (
    customer_user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    seller_id TEXT REFERENCES sellers(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    pickup_at TIMESTAMPTZ,
    special_pickup BOOLEAN NOT NULL DEFAULT FALSE,
    special_pickup_reason TEXT NOT NULL DEFAULT '' CHECK (length(special_pickup_reason) <= 500),
    instructions TEXT NOT NULL DEFAULT '' CHECK (length(instructions) <= 500),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (customer_user_id, seller_id),
    CHECK (seller_id IS NOT NULL OR pickup_at IS NULL AND NOT special_pickup AND
           special_pickup_reason = '' AND instructions = ''),
    CHECK (special_pickup OR special_pickup_reason = '')
);

CREATE TABLE bulk_customer_draft_lines (
    customer_user_id UUID NOT NULL,
    seller_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    PRIMARY KEY (customer_user_id, item_id),
    FOREIGN KEY (customer_user_id, seller_id)
        REFERENCES bulk_customer_drafts(customer_user_id, seller_id) ON DELETE CASCADE,
    FOREIGN KEY (seller_id, item_id) REFERENCES menu_items(seller_id, id) ON DELETE RESTRICT
);
