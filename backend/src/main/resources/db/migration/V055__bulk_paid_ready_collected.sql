-- Bulk orders continue after payment like individual orders:
-- AGREED_AWAITING_PAYMENT -> PAID (PhonePe confirmed, pickup PIN issued) -> READY -> COMPLETED (handed over).
-- The quote revision keeps its agreed status; only the request moves on.

ALTER TABLE bulk_requests DROP CONSTRAINT bulk_requests_status_check;
ALTER TABLE bulk_requests ADD CONSTRAINT bulk_requests_status_check CHECK (status IN (
    'SUBMITTED', 'COUNTEROFFER', 'AGREED_AWAITING_PAYMENT', 'EXPIRED', 'REJECTED', 'WITHDRAWN',
    'PAID', 'READY', 'COMPLETED'));

ALTER TABLE bulk_requests
    ADD COLUMN pickup_code TEXT,
    ADD COLUMN paid_at TIMESTAMPTZ,
    ADD COLUMN ready_at TIMESTAMPTZ,
    ADD COLUMN handed_over_at TIMESTAMPTZ;

-- Every paid order has a 4-digit PIN the customer shows at pickup.
ALTER TABLE bulk_requests ADD CONSTRAINT bulk_requests_pickup_code_check CHECK (
    status NOT IN ('PAID', 'READY', 'COMPLETED') OR pickup_code ~ '^[0-9]{4}$');

ALTER TABLE bulk_request_events DROP CONSTRAINT bulk_request_events_event_type_check;
ALTER TABLE bulk_request_events ADD CONSTRAINT bulk_request_events_event_type_check CHECK (event_type IN (
    'SUBMITTED', 'COUNTEROFFER', 'CUSTOMER_APPROVED', 'SELLER_APPROVED', 'SPECIAL_PICKUP_DECIDED', 'EXPIRED',
    'REJECTED', 'WITHDRAWN', 'AGREED', 'PAID', 'READY', 'COMPLETED'));
