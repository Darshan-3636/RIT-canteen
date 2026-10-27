-- Backend 3 (Engagement/Admin). Support tickets never reference or alter payment state.

CREATE TABLE support_tickets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    seller_id TEXT REFERENCES sellers(id) ON DELETE RESTRICT,
    category TEXT NOT NULL,
    subject TEXT NOT NULL,
    request_reference TEXT,
    status TEXT NOT NULL DEFAULT 'OPEN',
    client_request_id TEXT,
    escalated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT support_tickets_category_valid CHECK (category IN ('FOOD', 'SCHEDULE', 'PAYMENT', 'ACCOUNT', 'APP')),
    CONSTRAINT support_tickets_status_valid CHECK (status IN ('OPEN', 'ESCALATED', 'RESOLVED', 'CLOSED')),
    CONSTRAINT support_tickets_subject_present CHECK (subject = btrim(subject) AND length(subject) BETWEEN 3 AND 120),
    CONSTRAINT support_tickets_reference_shape CHECK (request_reference IS NULL OR request_reference ~ '^[A-Za-z0-9._:-]{1,64}$'),
    CONSTRAINT support_tickets_client_request_shape CHECK (client_request_id IS NULL OR client_request_id ~ '^[A-Za-z0-9_-]{8,64}$'),
    CONSTRAINT support_tickets_escalation_time CHECK (status <> 'ESCALATED' OR escalated_at IS NOT NULL)
);

CREATE UNIQUE INDEX support_tickets_client_request_idx ON support_tickets (user_id, client_request_id)
    WHERE client_request_id IS NOT NULL;
CREATE INDEX support_tickets_user_created_idx ON support_tickets (user_id, created_at DESC);
CREATE INDEX support_tickets_seller_status_idx ON support_tickets (seller_id, status, updated_at DESC);
CREATE INDEX support_tickets_status_updated_idx ON support_tickets (status, updated_at DESC);

CREATE TABLE support_ticket_messages (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    author_role TEXT NOT NULL,
    body TEXT NOT NULL,
    client_message_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT support_messages_role_valid CHECK (author_role IN ('CUSTOMER', 'SELLER', 'ADMIN')),
    CONSTRAINT support_messages_body_present CHECK (length(btrim(body)) BETWEEN 1 AND 4000),
    CONSTRAINT support_messages_client_id_shape CHECK (client_message_id IS NULL OR client_message_id ~ '^[A-Za-z0-9_-]{8,64}$')
);

CREATE UNIQUE INDEX support_messages_client_id_idx ON support_ticket_messages (ticket_id, author_user_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
CREATE INDEX support_messages_ticket_created_idx ON support_ticket_messages (ticket_id, created_at);

CREATE TABLE support_ticket_status_history (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    from_status TEXT,
    to_status TEXT NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor_role TEXT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT support_history_status_valid CHECK (
        (from_status IS NULL OR from_status IN ('OPEN', 'ESCALATED', 'RESOLVED', 'CLOSED')) AND
        to_status IN ('OPEN', 'ESCALATED', 'RESOLVED', 'CLOSED')),
    CONSTRAINT support_history_role_valid CHECK (actor_role IN ('CUSTOMER', 'SELLER', 'ADMIN'))
);

CREATE INDEX support_history_ticket_idx ON support_ticket_status_history (ticket_id, changed_at);

-- In-app inbox only. Rows are never evidence that push, SMS or email was delivered.
CREATE TABLE notification_inbox (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    ticket_id UUID REFERENCES support_tickets(id) ON DELETE CASCADE,
    delivery_channel TEXT NOT NULL DEFAULT 'IN_APP_ONLY',
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT notification_event_type_valid CHECK (event_type IN ('SUPPORT_REPLY', 'SUPPORT_STATUS_CHANGED')),
    CONSTRAINT notification_channel_in_app_only CHECK (delivery_channel = 'IN_APP_ONLY'),
    CONSTRAINT notification_text_present CHECK (length(title) BETWEEN 1 AND 120 AND length(body) BETWEEN 1 AND 500)
);

CREATE INDEX notification_inbox_user_created_idx ON notification_inbox (user_id, created_at DESC);
CREATE INDEX notification_inbox_user_unread_idx ON notification_inbox (user_id) WHERE read_at IS NULL;
