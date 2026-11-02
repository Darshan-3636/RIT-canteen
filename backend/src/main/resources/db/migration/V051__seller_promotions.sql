-- Seller-owned offers and immutable discount snapshots on individual demo orders.
CREATE TABLE promotions (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    title TEXT NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 120),
    description TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 1000),
    kind TEXT NOT NULL CHECK (kind IN ('FEATURED','TODAYS_SPECIAL','NEW_ARRIVAL','BANNER','PERCENT_DISCOUNT','FIXED_DISCOUNT')),
    audience TEXT NOT NULL DEFAULT 'GENERAL' CHECK (audience IN ('GENERAL','BEHAVIORAL')),
    discount_percent INTEGER CHECK (discount_percent BETWEEN 1 AND 100),
    discount_paise BIGINT CHECK (discount_paise BETWEEN 1 AND 1000000000),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (seller_id,id),
    CHECK (ends_at > starts_at),
    CHECK ((kind='PERCENT_DISCOUNT' AND discount_percent IS NOT NULL AND discount_paise IS NULL) OR
           (kind='FIXED_DISCOUNT' AND discount_paise IS NOT NULL AND discount_percent IS NULL) OR
           (kind NOT IN ('PERCENT_DISCOUNT','FIXED_DISCOUNT') AND discount_percent IS NULL AND discount_paise IS NULL))
);
CREATE INDEX promotions_current_idx ON promotions (seller_id, starts_at, ends_at) WHERE published;

CREATE TABLE promotion_items (
    seller_id TEXT NOT NULL,
    promotion_id UUID NOT NULL,
    item_id TEXT NOT NULL,
    PRIMARY KEY (promotion_id,item_id),
    FOREIGN KEY (seller_id,promotion_id) REFERENCES promotions(seller_id,id) ON DELETE CASCADE,
    FOREIGN KEY (seller_id,item_id) REFERENCES menu_items(seller_id,id) ON DELETE RESTRICT
);
CREATE TABLE promotion_categories (
    seller_id TEXT NOT NULL,
    promotion_id UUID NOT NULL,
    category_id TEXT NOT NULL,
    PRIMARY KEY (promotion_id,category_id),
    FOREIGN KEY (seller_id,promotion_id) REFERENCES promotions(seller_id,id) ON DELETE CASCADE,
    FOREIGN KEY (seller_id,category_id) REFERENCES seller_categories(seller_id,category_id) ON DELETE RESTRICT
);

ALTER TABLE individual_demo_orders ADD COLUMN promotion_id UUID REFERENCES promotions(id) ON DELETE RESTRICT;
ALTER TABLE individual_demo_orders ADD COLUMN discount_paise BIGINT NOT NULL DEFAULT 0
    CHECK (discount_paise >= 0 AND discount_paise <= subtotal_paise);
