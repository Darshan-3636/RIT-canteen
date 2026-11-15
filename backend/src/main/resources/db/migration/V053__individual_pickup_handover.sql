-- Human-readable reference and customer-held handover code. Existing order IDs remain unchanged.
CREATE SEQUENCE individual_pickup_reference_seq;

ALTER TABLE individual_demo_orders
    ADD COLUMN pickup_reference TEXT,
    ADD COLUMN pickup_code TEXT,
    ADD COLUMN handed_over_at TIMESTAMPTZ,
    ADD COLUMN handed_over_by UUID REFERENCES users(id) ON DELETE RESTRICT;

UPDATE individual_demo_orders
SET pickup_reference='RIT-' || lpad(nextval('individual_pickup_reference_seq')::text,8,'0'),
    pickup_code=upper(substr(replace(gen_random_uuid()::text,'-',''),1,12));

ALTER TABLE individual_demo_orders
    ALTER COLUMN pickup_reference SET DEFAULT 'RIT-' || lpad(nextval('individual_pickup_reference_seq')::text,8,'0'),
    ALTER COLUMN pickup_reference SET NOT NULL,
    ALTER COLUMN pickup_code SET DEFAULT upper(substr(replace(gen_random_uuid()::text,'-',''),1,12)),
    ALTER COLUMN pickup_code SET NOT NULL;

CREATE UNIQUE INDEX individual_demo_orders_pickup_reference_key ON individual_demo_orders(pickup_reference);
CREATE UNIQUE INDEX individual_demo_orders_pickup_code_key ON individual_demo_orders(pickup_code);
ALTER TABLE individual_demo_orders ADD CONSTRAINT individual_demo_orders_handover_pair_check
    CHECK ((handed_over_at IS NULL)=(handed_over_by IS NULL));
