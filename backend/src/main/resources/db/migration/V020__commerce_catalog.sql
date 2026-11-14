CREATE TABLE menu_categories (
    id TEXT PRIMARY KEY CHECK (id ~ '^[a-z0-9][a-z0-9-]{0,63}$'),
    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 80),
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE seller_categories (
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    category_id TEXT NOT NULL REFERENCES menu_categories(id) ON DELETE RESTRICT,
    PRIMARY KEY (seller_id, category_id)
);

CREATE TABLE menu_items (
    id TEXT PRIMARY KEY CHECK (id ~ '^[a-z0-9][a-z0-9-]{0,95}$'),
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    category_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    description TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 1200),
    price_paise BIGINT NOT NULL CHECK (price_paise >= 0),
    kind TEXT NOT NULL CHECK (kind IN ('PREPARED', 'PACKAGED')),
    dietary TEXT NOT NULL CHECK (dietary IN ('VEGETARIAN', 'NON_VEGETARIAN', 'NOT_SPECIFIED')),
    availability TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (availability IN ('AVAILABLE', 'UNAVAILABLE')),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    unavailable_visibility TEXT NOT NULL DEFAULT 'SHOW' CHECK (unavailable_visibility IN ('SHOW', 'HIDE')),
    image_key TEXT NOT NULL CHECK (length(trim(image_key)) BETWEEN 1 AND 120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (seller_id, id),
    FOREIGN KEY (seller_id, category_id) REFERENCES seller_categories(seller_id, category_id) ON DELETE RESTRICT
);
CREATE INDEX menu_items_seller_visible_idx ON menu_items (seller_id, category_id, name)
    WHERE published AND (availability = 'AVAILABLE' OR unavailable_visibility = 'SHOW');

CREATE TABLE seller_ordering_policies (
    seller_id TEXT PRIMARY KEY REFERENCES sellers(id) ON DELETE RESTRICT,
    bulk_minimum_units INTEGER NOT NULL DEFAULT 8 CHECK (bulk_minimum_units BETWEEN 1 AND 999),
    timezone TEXT NOT NULL DEFAULT 'Asia/Kolkata' CHECK (timezone = 'Asia/Kolkata'),
    special_pickup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE seller_hours (
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    PRIMARY KEY (seller_id, day_of_week),
    CHECK (opens_at < closes_at)
);

CREATE TABLE seller_closures (
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    local_date DATE NOT NULL,
    reason TEXT NOT NULL DEFAULT '' CHECK (length(reason) <= 300),
    PRIMARY KEY (seller_id, local_date)
);
