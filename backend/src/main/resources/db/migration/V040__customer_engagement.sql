-- Backend 3 (Engagement/Admin). Per-customer extras; no foreign keys to unmerged Commerce tables.

CREATE TABLE customer_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    operational_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    transactional_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    promotional_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    personalization_consent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Item targets are validated through the injected Commerce port at write time; seller_id records the
-- owning seller reported by that port (or the seller itself for SELLER favorites).
CREATE TABLE favorites (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, target_type, target_id),
    CONSTRAINT favorites_target_type_valid CHECK (target_type IN ('SELLER', 'ITEM')),
    CONSTRAINT favorites_target_id_slug CHECK (target_id ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
    CONSTRAINT favorites_seller_target_consistent CHECK (target_type <> 'SELLER' OR seller_id = target_id)
);

CREATE INDEX favorites_user_created_idx ON favorites (user_id, created_at DESC);

-- Saved line mixes. Lines are item references for later catalogue revalidation by Commerce on submission;
-- no bulk minimum applies to a saved template.
CREATE TABLE bulk_templates (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    name TEXT NOT NULL,
    lines JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT bulk_templates_name_present CHECK (name = btrim(name) AND length(name) BETWEEN 1 AND 80),
    CONSTRAINT bulk_templates_lines_shape CHECK (jsonb_typeof(lines) = 'array' AND jsonb_array_length(lines) BETWEEN 1 AND 50),
    CONSTRAINT bulk_templates_name_unique UNIQUE (user_id, seller_id, name)
);

CREATE INDEX bulk_templates_user_updated_idx ON bulk_templates (user_id, updated_at DESC);
