-- Four-digit pickup PIN shown to the customer (and encoded in their pickup QR).
-- A PIN is unique only among one seller's uncollected orders, so collected or
-- rejected orders free their PIN for reuse. Collected orders keep their old codes.

DROP INDEX individual_demo_orders_pickup_code_key;
ALTER TABLE individual_demo_orders ALTER COLUMN pickup_code DROP DEFAULT;

CREATE FUNCTION next_free_pickup_pin(p_seller_id TEXT, p_order_id UUID) RETURNS TEXT
LANGUAGE plpgsql AS $$
DECLARE
    candidate TEXT;
BEGIN
    -- Serialise PIN choice per seller so two concurrent orders cannot pick the same PIN.
    PERFORM pg_advisory_xact_lock(hashtext('individual_pickup_pin:' || p_seller_id));
    FOR attempt IN 1..50 LOOP
        candidate := lpad(floor(random() * 10000)::INT::TEXT, 4, '0');
        IF NOT EXISTS (SELECT 1 FROM individual_demo_orders
                       WHERE seller_id = p_seller_id AND pickup_code = candidate AND id <> p_order_id
                         AND status NOT IN ('DEMO_COMPLETED', 'DEMO_REJECTED')) THEN
            RETURN candidate;
        END IF;
    END LOOP;
    -- Nearly full: take the first free PIN instead of guessing.
    SELECT lpad(n::TEXT, 4, '0') INTO candidate FROM generate_series(0, 9999) AS n
    WHERE NOT EXISTS (SELECT 1 FROM individual_demo_orders
                      WHERE seller_id = p_seller_id AND pickup_code = lpad(n::TEXT, 4, '0') AND id <> p_order_id
                        AND status NOT IN ('DEMO_COMPLETED', 'DEMO_REJECTED'))
    LIMIT 1;
    IF candidate IS NULL THEN
        RAISE EXCEPTION 'No free pickup PIN for seller %', p_seller_id;
    END IF;
    RETURN candidate;
END $$;

CREATE FUNCTION assign_individual_pickup_pin() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.pickup_code := next_free_pickup_pin(NEW.seller_id, NEW.id);
    RETURN NEW;
END $$;

CREATE TRIGGER individual_demo_orders_pickup_pin
    BEFORE INSERT ON individual_demo_orders
    FOR EACH ROW EXECUTE FUNCTION assign_individual_pickup_pin();

-- Existing uncollected orders get a PIN too, one at a time so each sees the previous ones.
DO $$
DECLARE
    pending RECORD;
BEGIN
    FOR pending IN SELECT id, seller_id FROM individual_demo_orders
                   WHERE status NOT IN ('DEMO_COMPLETED', 'DEMO_REJECTED') ORDER BY created_at, id LOOP
        UPDATE individual_demo_orders SET pickup_code = next_free_pickup_pin(pending.seller_id, pending.id)
        WHERE id = pending.id;
    END LOOP;
END $$;

CREATE UNIQUE INDEX individual_demo_orders_active_pickup_pin_key
    ON individual_demo_orders (seller_id, pickup_code)
    WHERE status NOT IN ('DEMO_COMPLETED', 'DEMO_REJECTED');
