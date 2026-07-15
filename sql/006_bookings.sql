CREATE TABLE IF NOT EXISTS bookings (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id           UUID NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    passenger_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status            TEXT NOT NULL DEFAULT 'requested'
                          CHECK (status IN ('requested', 'accepted', 'rejected', 'cancelled', 'completed')),
    seats_requested   INT NOT NULL DEFAULT 1 CHECK (seats_requested >= 1),
    amount            NUMERIC(10, 2) NOT NULL DEFAULT 0,
    payment_method    TEXT NOT NULL DEFAULT 'cash'
                          CHECK (payment_method IN ('cash', 'razorpay')),
    pickup_lat        DOUBLE PRECISION,
    pickup_lng        DOUBLE PRECISION,
    pickup_label      TEXT,
    drop_lat          DOUBLE PRECISION,
    drop_lng          DOUBLE PRECISION,
    drop_label        TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (ride_id, passenger_id)
);

CREATE INDEX IF NOT EXISTS idx_bookings_passenger ON bookings (passenger_id);
CREATE INDEX IF NOT EXISTS idx_bookings_ride ON bookings (ride_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings (status);
