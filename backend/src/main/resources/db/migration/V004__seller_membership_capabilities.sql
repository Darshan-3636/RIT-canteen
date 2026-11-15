CREATE TABLE seller_membership_capabilities (
    membership_id UUID NOT NULL REFERENCES seller_memberships(id) ON DELETE CASCADE,
    capability TEXT NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (membership_id, capability),
    CONSTRAINT seller_capability_wire_valid CHECK (capability ~ '^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$')
);

CREATE INDEX seller_membership_capabilities_wire_idx ON seller_membership_capabilities (capability);
