CREATE TABLE IF NOT EXISTS rides (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id                   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    vehicle_id                 UUID NOT NULL REFERENCES vehicles (id),
    ride_type                  TEXT NOT NULL DEFAULT 'scheduled'
                                   CHECK (ride_type IN ('scheduled', 'on_demand')),
    status                     TEXT NOT NULL DEFAULT 'open'
                                   CHECK (status IN ('open', 'full', 'in_progress', 'completed', 'cancelled')),
    is_comfort_ride            BOOLEAN NOT NULL DEFAULT FALSE,
    max_back_seat_passengers   INT,
    origin_lat                 DOUBLE PRECISION NOT NULL,
    origin_lng                 DOUBLE PRECISION NOT NULL,
    origin_label               TEXT NOT NULL,
    destination_lat            DOUBLE PRECISION NOT NULL,
    destination_lng            DOUBLE PRECISION NOT NULL,
    destination_label          TEXT NOT NULL,
    depart_at                  TIMESTAMPTZ NOT NULL,
    trip_started_at            TIMESTAMPTZ,
    available_seats            INT NOT NULL CHECK (available_seats >= 0),
    price_per_seat             NUMERIC(10, 2) NOT NULL DEFAULT 0,
    is_recurring               BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rides_owner ON rides (owner_id);
CREATE INDEX IF NOT EXISTS idx_rides_status_depart ON rides (status, depart_at);
CREATE INDEX IF NOT EXISTS idx_rides_origin ON rides (origin_lat, origin_lng);
CREATE INDEX IF NOT EXISTS idx_rides_destination ON rides (destination_lat, destination_lng);
