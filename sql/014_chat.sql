-- Host ↔ co-rider 1:1 chat (opened on booking request or seat offer)

CREATE TABLE IF NOT EXISTS chat_conversations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id              UUID NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    host_id              UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    co_rider_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    booking_id           UUID REFERENCES bookings (id) ON DELETE SET NULL,
    offer_id             UUID REFERENCES ride_offers (id) ON DELETE SET NULL,
    last_message_at      TIMESTAMPTZ,
    last_message_preview TEXT,
    host_last_read_at    TIMESTAMPTZ,
    co_rider_last_read_at TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (ride_id, co_rider_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_host
    ON chat_conversations (host_id, last_message_at DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_chat_conversations_co_rider
    ON chat_conversations (co_rider_id, last_message_at DESC NULLS LAST);

CREATE TABLE IF NOT EXISTS chat_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES chat_conversations (id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body            TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation_created
    ON chat_messages (conversation_id, created_at DESC);
