-- Transactional in-app notices. Existing support inbox entries remain intact.
ALTER TABLE notification_inbox DROP CONSTRAINT notification_event_type_valid;
ALTER TABLE notification_inbox ADD CONSTRAINT notification_event_type_valid CHECK (
    event_type IN ('SUPPORT_REPLY', 'SUPPORT_STATUS_CHANGED',
                   'BULK_REQUEST_UPDATE', 'INDIVIDUAL_ORDER_UPDATE'));
ALTER TABLE notification_inbox ADD COLUMN event_key TEXT;
ALTER TABLE notification_inbox ADD CONSTRAINT notification_event_key_shape CHECK (
    event_key IS NULL OR length(event_key) BETWEEN 1 AND 160);
CREATE UNIQUE INDEX notification_inbox_user_event_key_idx
    ON notification_inbox (user_id, event_key) WHERE event_key IS NOT NULL;
