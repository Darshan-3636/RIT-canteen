-- Seller-scoped inventory. Existing commerce and identity rows are untouched.
CREATE TABLE inventory_units (
    code TEXT PRIMARY KEY CHECK (code ~ '^[a-z][a-z0-9-]{0,15}$'),
    name TEXT NOT NULL,
    dimension TEXT NOT NULL CHECK (dimension IN ('MASS','VOLUME','COUNT')),
    base_factor NUMERIC(24,12) NOT NULL CHECK (base_factor > 0)
);
INSERT INTO inventory_units(code,name,dimension,base_factor) VALUES
    ('g','Gram','MASS',1),('kg','Kilogram','MASS',1000),
    ('ml','Millilitre','VOLUME',1),('l','Litre','VOLUME',1000),
    ('each','Each','COUNT',1);

CREATE TABLE inventory_material_categories (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    name TEXT NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 80),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (seller_id,id), UNIQUE (seller_id,name)
);
CREATE TABLE inventory_materials (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    category_id UUID NOT NULL,
    name TEXT NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    base_unit_code TEXT NOT NULL REFERENCES inventory_units(code),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    on_hand_base NUMERIC(20,6) NOT NULL DEFAULT 0 CHECK (on_hand_base >= 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (seller_id,id), UNIQUE (seller_id,name),
    FOREIGN KEY (seller_id,category_id) REFERENCES inventory_material_categories(seller_id,id) ON DELETE RESTRICT
);
CREATE INDEX inventory_materials_seller_category_idx ON inventory_materials(seller_id,category_id,name);

CREATE TABLE inventory_suppliers (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    name TEXT NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    phone TEXT CHECK (phone IS NULL OR length(phone) BETWEEN 5 AND 30),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (seller_id,id), UNIQUE (seller_id,name)
);
CREATE TABLE inventory_purchase_orders (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    supplier_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PLACED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    note TEXT NOT NULL DEFAULT '' CHECK (length(note) <= 500),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (seller_id,id),
    FOREIGN KEY (seller_id,supplier_id) REFERENCES inventory_suppliers(seller_id,id) ON DELETE RESTRICT
);
CREATE INDEX inventory_purchase_orders_seller_idx ON inventory_purchase_orders(seller_id,created_at DESC);
CREATE TABLE inventory_purchase_order_items (
    seller_id TEXT NOT NULL,
    purchase_order_id UUID NOT NULL,
    line_no INTEGER NOT NULL CHECK (line_no BETWEEN 1 AND 200),
    material_id UUID NOT NULL,
    unit_code TEXT NOT NULL REFERENCES inventory_units(code),
    quantity NUMERIC(20,6) NOT NULL CHECK (quantity > 0),
    unit_price_paise BIGINT NOT NULL CHECK (unit_price_paise >= 0),
    received_base NUMERIC(20,6) NOT NULL DEFAULT 0 CHECK (received_base >= 0),
    PRIMARY KEY (purchase_order_id,line_no),
    UNIQUE (seller_id,purchase_order_id,line_no),
    UNIQUE (purchase_order_id,material_id),
    FOREIGN KEY (seller_id,purchase_order_id) REFERENCES inventory_purchase_orders(seller_id,id) ON DELETE RESTRICT,
    FOREIGN KEY (seller_id,material_id) REFERENCES inventory_materials(seller_id,id) ON DELETE RESTRICT
);

CREATE TABLE inventory_stock_receipts (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    purchase_order_id UUID,
    supplier_id UUID,
    supplier_reference TEXT CHECK (supplier_reference IS NULL OR length(supplier_reference) BETWEEN 1 AND 100),
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    request_hash BYTEA NOT NULL CHECK (octet_length(request_hash)=32),
    UNIQUE (seller_id,id),
    FOREIGN KEY (seller_id,purchase_order_id) REFERENCES inventory_purchase_orders(seller_id,id) ON DELETE RESTRICT,
    FOREIGN KEY (seller_id,supplier_id) REFERENCES inventory_suppliers(seller_id,id) ON DELETE RESTRICT
);
CREATE TABLE inventory_stock_batches (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    material_id UUID NOT NULL,
    receipt_id UUID NOT NULL,
    batch_code TEXT NOT NULL CHECK (length(btrim(batch_code)) BETWEEN 1 AND 80),
    expires_on DATE,
    initial_base NUMERIC(20,6) NOT NULL CHECK (initial_base > 0),
    remaining_base NUMERIC(20,6) NOT NULL CHECK (remaining_base >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (seller_id,id), UNIQUE (seller_id,material_id,id),
    UNIQUE (seller_id,material_id,batch_code),
    FOREIGN KEY (seller_id,material_id) REFERENCES inventory_materials(seller_id,id) ON DELETE RESTRICT,
    FOREIGN KEY (seller_id,receipt_id) REFERENCES inventory_stock_receipts(seller_id,id) ON DELETE RESTRICT
);
CREATE INDEX inventory_batches_expiry_idx ON inventory_stock_batches(seller_id,expires_on)
    WHERE remaining_base > 0 AND expires_on IS NOT NULL;
CREATE TABLE inventory_stock_receipt_items (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL,
    receipt_id UUID NOT NULL,
    material_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    unit_code TEXT NOT NULL REFERENCES inventory_units(code),
    quantity NUMERIC(20,6) NOT NULL CHECK (quantity > 0),
    base_quantity NUMERIC(20,6) NOT NULL CHECK (base_quantity > 0),
    purchase_order_id UUID,
    purchase_order_line_no INTEGER,
    UNIQUE (batch_id),
    CHECK ((purchase_order_id IS NULL) = (purchase_order_line_no IS NULL)),
    FOREIGN KEY (seller_id,receipt_id) REFERENCES inventory_stock_receipts(seller_id,id) ON DELETE RESTRICT,
    FOREIGN KEY (seller_id,material_id,batch_id) REFERENCES inventory_stock_batches(seller_id,material_id,id) ON DELETE RESTRICT,
    FOREIGN KEY (seller_id,purchase_order_id,purchase_order_line_no)
        REFERENCES inventory_purchase_order_items(seller_id,purchase_order_id,line_no) ON DELETE RESTRICT
);

CREATE TABLE inventory_stock_transactions (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    material_id UUID NOT NULL,
    batch_id UUID,
    source_kind TEXT NOT NULL CHECK (source_kind IN ('RECEIPT','WASTAGE','ADJUSTMENT','RECIPE_CONSUMPTION')),
    source_id UUID NOT NULL,
    delta_base NUMERIC(20,6) NOT NULL CHECK (delta_base <> 0),
    material_balance_after NUMERIC(20,6) NOT NULL CHECK (material_balance_after >= 0),
    reason TEXT NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 300),
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (seller_id,material_id) REFERENCES inventory_materials(seller_id,id) ON DELETE RESTRICT,
    FOREIGN KEY (seller_id,material_id,batch_id) REFERENCES inventory_stock_batches(seller_id,material_id,id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX inventory_stock_transaction_source_idx ON inventory_stock_transactions
    (seller_id,source_kind,source_id,material_id,batch_id) NULLS NOT DISTINCT;
CREATE INDEX inventory_stock_transactions_material_idx ON inventory_stock_transactions(seller_id,material_id,occurred_at DESC);

CREATE TABLE inventory_wastage (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    material_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    quantity_base NUMERIC(20,6) NOT NULL CHECK (quantity_base > 0),
    reason TEXT NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 300),
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    request_hash BYTEA NOT NULL CHECK (octet_length(request_hash)=32),
    UNIQUE (seller_id,id),
    FOREIGN KEY (seller_id,material_id,batch_id) REFERENCES inventory_stock_batches(seller_id,material_id,id) ON DELETE RESTRICT
);
CREATE TABLE inventory_stock_adjustments (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    material_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    delta_base NUMERIC(20,6) NOT NULL CHECK (delta_base <> 0),
    reason TEXT NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 300),
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    request_hash BYTEA NOT NULL CHECK (octet_length(request_hash)=32),
    UNIQUE (seller_id,id),
    FOREIGN KEY (seller_id,material_id,batch_id) REFERENCES inventory_stock_batches(seller_id,material_id,id) ON DELETE RESTRICT
);

CREATE TABLE inventory_reorder_rules (
    seller_id TEXT NOT NULL,
    material_id UUID NOT NULL,
    threshold_base NUMERIC(20,6) NOT NULL CHECK (threshold_base >= 0),
    target_base NUMERIC(20,6) NOT NULL CHECK (target_base > threshold_base),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (seller_id,material_id),
    FOREIGN KEY (seller_id,material_id) REFERENCES inventory_materials(seller_id,id) ON DELETE RESTRICT
);
CREATE TABLE inventory_alerts (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL,
    material_id UUID NOT NULL,
    batch_id UUID,
    kind TEXT NOT NULL CHECK (kind IN ('LOW_STOCK','EXPIRY')),
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','ACKNOWLEDGED','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (seller_id,material_id) REFERENCES inventory_materials(seller_id,id) ON DELETE RESTRICT,
    FOREIGN KEY (seller_id,material_id,batch_id) REFERENCES inventory_stock_batches(seller_id,material_id,id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX inventory_alerts_scope_idx ON inventory_alerts(seller_id,material_id,kind,batch_id) NULLS NOT DISTINCT;

CREATE TABLE inventory_recipes (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (seller_id,id), UNIQUE (seller_id,item_id),
    FOREIGN KEY (seller_id,item_id) REFERENCES menu_items(seller_id,id) ON DELETE RESTRICT
);
CREATE TABLE inventory_recipe_ingredients (
    seller_id TEXT NOT NULL,
    recipe_id UUID NOT NULL,
    material_id UUID NOT NULL,
    quantity_base NUMERIC(20,6) NOT NULL CHECK (quantity_base > 0),
    PRIMARY KEY (recipe_id,material_id),
    FOREIGN KEY (seller_id,recipe_id) REFERENCES inventory_recipes(seller_id,id) ON DELETE CASCADE,
    FOREIGN KEY (seller_id,material_id) REFERENCES inventory_materials(seller_id,id) ON DELETE RESTRICT
);
