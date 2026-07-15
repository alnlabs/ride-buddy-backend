-- Co-rider "need a ride" posts + owner seat offers (two-way matching).

CREATE TABLE IF NOT EXISTS ride_requests (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    origin_lat           DOUBLE PRECISION NOT NULL,
    origin_lng           DOUBLE PRECISION NOT NULL,
    origin_label         TEXT NOT NULL,
    destination_lat      DOUBLE PRECISION NOT NULL,
    destination_lng      DOUBLE PRECISION NOT NULL,
    destination_label    TEXT NOT NULL,
    depart_at            TIMESTAMPTZ NOT NULL,
    seats_needed         INT NOT NULL DEFAULT 1 CHECK (seats_needed >= 1 AND seats_needed <= 3),
    comfort_preferred    BOOLEAN NOT NULL DEFAULT FALSE,
    status               TEXT NOT NULL DEFAULT 'open'
                             CHECK (status IN ('open', 'matched', 'cancelled', 'expired')),
    matched_ride_id      UUID REFERENCES rides (id) ON DELETE SET NULL,
    matched_booking_id   UUID REFERENCES bookings (id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ride_requests_requester ON ride_requests (requester_id);
CREATE INDEX IF NOT EXISTS idx_ride_requests_status_depart ON ride_requests (status, depart_at);
CREATE INDEX IF NOT EXISTS idx_ride_requests_origin ON ride_requests (origin_lat, origin_lng);
CREATE INDEX IF NOT EXISTS idx_ride_requests_destination ON ride_requests (destination_lat, destination_lng);

CREATE TABLE IF NOT EXISTS ride_offers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id    UUID NOT NULL REFERENCES ride_requests (id) ON DELETE CASCADE,
    ride_id       UUID NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    owner_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status        TEXT NOT NULL DEFAULT 'offered'
                      CHECK (status IN ('offered', 'accepted', 'rejected', 'cancelled')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (request_id, ride_id)
);

CREATE INDEX IF NOT EXISTS idx_ride_offers_request ON ride_offers (request_id);
CREATE INDEX IF NOT EXISTS idx_ride_offers_owner ON ride_offers (owner_id);
CREATE INDEX IF NOT EXISTS idx_ride_offers_ride ON ride_offers (ride_id);
CREATE INDEX IF NOT EXISTS idx_ride_offers_status ON ride_offers (status);
