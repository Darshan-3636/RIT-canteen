CREATE TABLE bulk_requests (
    id UUID PRIMARY KEY,
    customer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    current_version INTEGER NOT NULL CHECK (current_version >= 1),
    status TEXT NOT NULL CHECK (status IN ('SUBMITTED', 'COUNTEROFFER', 'AGREED_AWAITING_PAYMENT', 'EXPIRED', 'REJECTED', 'WITHDRAWN')),
    idempotency_key TEXT NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 128),
    idempotency_hash BYTEA NOT NULL CHECK (octet_length(idempotency_hash) = 32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (customer_user_id, idempotency_key)
);
CREATE INDEX bulk_requests_seller_status_idx ON bulk_requests (seller_id, status, created_at DESC);
CREATE INDEX bulk_requests_customer_idx ON bulk_requests (customer_user_id, created_at DESC);

CREATE TABLE bulk_quote_revisions (
    request_id UUID NOT NULL REFERENCES bulk_requests(id) ON DELETE RESTRICT,
    version INTEGER NOT NULL CHECK (version >= 1),
    status TEXT NOT NULL CHECK (status IN ('SUBMITTED', 'COUNTEROFFER', 'AGREED_AWAITING_PAYMENT', 'EXPIRED', 'REJECTED', 'WITHDRAWN')),
    pickup_at TIMESTAMPTZ NOT NULL,
    pickup_kind TEXT NOT NULL CHECK (pickup_kind IN ('REGULAR', 'SPECIAL')),
    special_pickup_reason TEXT NULL CHECK (special_pickup_reason IS NULL OR length(trim(special_pickup_reason)) BETWEEN 1 AND 500),
    subtotal_paise BIGINT NOT NULL CHECK (subtotal_paise >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (request_id, version),
    CHECK ((pickup_kind = 'SPECIAL' AND special_pickup_reason IS NOT NULL) OR (pickup_kind = 'REGULAR' AND special_pickup_reason IS NULL)),
    CHECK (expires_at > created_at)
);

CREATE TABLE bulk_quote_lines (
    request_id UUID NOT NULL,
    version INTEGER NOT NULL,
    seller_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    item_name_snapshot TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    unit_price_paise BIGINT NOT NULL CHECK (unit_price_paise >= 0),
    PRIMARY KEY (request_id, version, item_id),
    FOREIGN KEY (request_id, version) REFERENCES bulk_quote_revisions(request_id, version) ON DELETE RESTRICT,
    FOREIGN KEY (seller_id, item_id) REFERENCES menu_items(seller_id, id) ON DELETE RESTRICT
);

CREATE TABLE bulk_quote_approvals (
    request_id UUID NOT NULL,
    version INTEGER NOT NULL,
    actor_kind TEXT NOT NULL CHECK (actor_kind IN ('CUSTOMER', 'SELLER')),
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    approved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (request_id, version, actor_kind),
    FOREIGN KEY (request_id, version) REFERENCES bulk_quote_revisions(request_id, version) ON DELETE RESTRICT
);

CREATE TABLE bulk_special_pickup_decisions (
    request_id UUID NOT NULL,
    version INTEGER NOT NULL,
    seller_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    approved BOOLEAN NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (request_id, version),
    FOREIGN KEY (request_id, version) REFERENCES bulk_quote_revisions(request_id, version) ON DELETE RESTRICT
);

CREATE TABLE bulk_request_events (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES bulk_requests(id) ON DELETE RESTRICT,
    version INTEGER NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    event_type TEXT NOT NULL CHECK (event_type IN ('SUBMITTED', 'COUNTEROFFER', 'CUSTOMER_APPROVED', 'SELLER_APPROVED', 'SPECIAL_PICKUP_DECIDED', 'EXPIRED', 'REJECTED', 'WITHDRAWN', 'AGREED')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    details JSONB NOT NULL DEFAULT '{}'::jsonb
);
