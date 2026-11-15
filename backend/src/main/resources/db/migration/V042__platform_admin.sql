-- Backend 3 (Engagement/Admin). Audit metadata must never contain ticket bodies, tokens or passwords.

CREATE TABLE admin_audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor_role TEXT NOT NULL,
    action TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    seller_id TEXT REFERENCES sellers(id) ON DELETE RESTRICT,
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT audit_actor_role_valid CHECK (actor_role IN ('ADMIN', 'SELLER', 'CUSTOMER')),
    CONSTRAINT audit_action_shape CHECK (action ~ '^[a-z]+(\.[a-z_]+)+$'),
    CONSTRAINT audit_target_present CHECK (length(target_type) BETWEEN 1 AND 40 AND length(target_id) BETWEEN 1 AND 128),
    CONSTRAINT audit_detail_object CHECK (jsonb_typeof(detail) = 'object')
);

CREATE INDEX admin_audit_occurred_idx ON admin_audit_events (occurred_at DESC, id);
CREATE INDEX admin_audit_actor_idx ON admin_audit_events (actor_user_id, occurred_at DESC);

-- A draft is a planning record only: no seller row, membership, invitation or email is created from it.
CREATE TABLE admin_setup_drafts (
    id UUID PRIMARY KEY,
    proposed_seller_id TEXT NOT NULL,
    seller_name TEXT NOT NULL,
    notes TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT setup_drafts_seller_id_slug CHECK (proposed_seller_id ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
    CONSTRAINT setup_drafts_name_present CHECK (seller_name = btrim(seller_name) AND length(seller_name) BETWEEN 1 AND 200),
    CONSTRAINT setup_drafts_notes_length CHECK (notes IS NULL OR length(notes) <= 1000),
    CONSTRAINT setup_drafts_status_valid CHECK (status IN ('DRAFT', 'ARCHIVED'))
);

CREATE UNIQUE INDEX setup_drafts_open_seller_idx ON admin_setup_drafts (proposed_seller_id) WHERE status = 'DRAFT';

-- Saved report configuration only; no export file is generated from it.
CREATE TABLE report_selections (
    id UUID PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name TEXT NOT NULL,
    metrics JSONB NOT NULL,
    from_date DATE,
    to_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT report_selections_name_present CHECK (name = btrim(name) AND length(name) BETWEEN 1 AND 80),
    CONSTRAINT report_selections_metrics_shape CHECK (jsonb_typeof(metrics) = 'array' AND jsonb_array_length(metrics) BETWEEN 1 AND 10),
    CONSTRAINT report_selections_range CHECK (from_date IS NULL OR to_date IS NULL OR from_date <= to_date),
    CONSTRAINT report_selections_name_unique UNIQUE (seller_id, name)
);
