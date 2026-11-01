-- Additive management metadata. Existing identities, orders and payment attempts are untouched.
ALTER TABLE menu_items ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE menu_items ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE seller_categories ADD COLUMN display_name TEXT CHECK (length(trim(display_name)) BETWEEN 1 AND 80);
ALTER TABLE seller_categories ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE seller_categories ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE seller_categories ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE seller_memberships ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE seller_ordering_policies ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE sellers ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE admin_setup_drafts ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE admin_setup_drafts ADD COLUMN decision TEXT CHECK (decision IN ('APPROVED','REJECTED'));
ALTER TABLE admin_setup_drafts ADD COLUMN owner_user_id UUID REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE admin_setup_drafts ADD COLUMN created_seller_id TEXT UNIQUE REFERENCES sellers(id) ON DELETE RESTRICT;
ALTER TABLE admin_setup_drafts ADD COLUMN reviewed_by UUID REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE admin_setup_drafts ADD COLUMN reviewed_at TIMESTAMPTZ;
ALTER TABLE admin_setup_drafts ADD COLUMN decision_reason TEXT CHECK (length(trim(decision_reason)) BETWEEN 1 AND 500);
ALTER TABLE admin_setup_drafts ADD CONSTRAINT setup_draft_decision_shape CHECK (
    (decision IS NULL AND created_seller_id IS NULL AND reviewed_by IS NULL AND reviewed_at IS NULL) OR
    (decision IS NOT NULL AND status='ARCHIVED' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL
     AND decision_reason IS NOT NULL AND
     ((decision='APPROVED' AND owner_user_id IS NOT NULL AND created_seller_id IS NOT NULL AND created_seller_id=proposed_seller_id)
      OR (decision='REJECTED' AND created_seller_id IS NULL)))
);
CREATE INDEX admin_audit_seller_time_idx ON admin_audit_events(seller_id,occurred_at DESC,id);

-- Preserve the existing child guard; additionally reject parent edits that invalidate children.
CREATE FUNCTION validate_seller_supervisor_children() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM seller_memberships child JOIN seller_memberships parent
               ON parent.id=child.supervisor_membership_id
               WHERE parent.id=NEW.id AND
                 (child.seller_id<>parent.seller_id OR
                  (child.role='MANAGER' AND parent.role<>'OWNER') OR
                  (child.role='STAFF' AND parent.role<>'MANAGER'))) THEN
        RAISE EXCEPTION 'Reassign dependent memberships before changing supervisor role' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER seller_supervisor_children_guard AFTER UPDATE ON seller_memberships
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_seller_supervisor_children();

-- Item IDs already permit 1-96 characters; seller targets keep their original slug rule.
ALTER TABLE favorites DROP CONSTRAINT favorites_target_id_slug;
ALTER TABLE favorites ADD CONSTRAINT favorites_target_id_slug CHECK (
    (target_type='SELLER' AND target_id ~ '^[a-z0-9][a-z0-9-]{1,62}$') OR
    (target_type='ITEM' AND target_id ~ '^[a-z0-9][a-z0-9-]{0,95}$')
);
