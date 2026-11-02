-- Keep seller IDs and historical order references stable while changing display names.
UPDATE sellers SET name = CASE id
    WHEN 'main-canteen' THEN 'M.S.R.I.T Canteen'
    WHEN 'campus-pantry' THEN 'M.S.R.I.T Condiments'
    WHEN 'coffee-corner' THEN 'Cafe Da Costa'
    WHEN 'north-counter' THEN 'Ramaiah''s Cafeteria'
END, updated_at = now(), version = version + 1
WHERE id IN ('main-canteen', 'campus-pantry', 'coffee-corner', 'north-counter');

-- The three secondary sellers retain bulk-only behavior until an authorized seller changes policy.
ALTER TABLE seller_ordering_policies
    ADD COLUMN individual_enabled BOOLEAN NOT NULL DEFAULT FALSE;
INSERT INTO seller_ordering_policies (seller_id, individual_enabled)
SELECT id, TRUE FROM sellers WHERE id = 'main-canteen'
ON CONFLICT (seller_id) DO UPDATE SET individual_enabled = TRUE;

ALTER TABLE menu_items ADD COLUMN order_eligibility TEXT NOT NULL DEFAULT 'BULK_ONLY'
    CONSTRAINT menu_items_order_eligibility_check
    CHECK (order_eligibility IN ('INDIVIDUAL_ONLY', 'BULK_ONLY', 'BOTH'));
UPDATE menu_items SET order_eligibility = 'BOTH'
WHERE seller_id = 'main-canteen' AND kind = 'PREPARED';

CREATE INDEX menu_items_individual_eligible_idx ON menu_items (seller_id, id)
    WHERE order_eligibility IN ('INDIVIDUAL_ONLY', 'BOTH') AND published
      AND availability = 'AVAILABLE' AND archived_at IS NULL;
