CREATE TABLE seller_memberships (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    seller_id TEXT NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    role TEXT NOT NULL,
    supervisor_membership_id UUID REFERENCES seller_memberships(id) ON DELETE RESTRICT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT seller_membership_one_per_user_seller UNIQUE (user_id, seller_id),
    CONSTRAINT seller_membership_role_valid CHECK (role IN ('OWNER', 'MANAGER', 'STAFF')),
    CONSTRAINT seller_membership_supervision_shape CHECK (
        (role = 'OWNER' AND supervisor_membership_id IS NULL) OR
        (role IN ('MANAGER', 'STAFF') AND supervisor_membership_id IS NOT NULL)
    ),
    CONSTRAINT seller_membership_not_self_supervised CHECK (supervisor_membership_id IS NULL OR supervisor_membership_id <> id)
);

CREATE INDEX seller_memberships_seller_active_idx ON seller_memberships (seller_id, active);
CREATE INDEX seller_memberships_supervisor_idx ON seller_memberships (supervisor_membership_id);

CREATE FUNCTION validate_seller_supervisor() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_seller TEXT;
DECLARE parent_role TEXT;
BEGIN
    IF NEW.role = 'OWNER' THEN RETURN NEW; END IF;
    SELECT seller_id, role INTO parent_seller, parent_role
    FROM seller_memberships WHERE id = NEW.supervisor_membership_id;
    IF parent_seller IS NULL OR parent_seller <> NEW.seller_id OR
       (NEW.role = 'MANAGER' AND parent_role <> 'OWNER') OR
       (NEW.role = 'STAFF' AND parent_role <> 'MANAGER') THEN
        RAISE EXCEPTION 'Invalid seller supervisor chain';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER seller_supervisor_guard
    BEFORE INSERT OR UPDATE OF seller_id, role, supervisor_membership_id
    ON seller_memberships FOR EACH ROW EXECUTE FUNCTION validate_seller_supervisor();
