-- Expiry + recurring schedule templates for rides and needs.

-- rides: allow expired; add expires_at + schedule link
ALTER TABLE rides DROP CONSTRAINT IF EXISTS rides_status_check;
ALTER TABLE rides ADD CONSTRAINT rides_status_check
    CHECK (status IN ('open', 'full', 'in_progress', 'completed', 'cancelled', 'expired'));

ALTER TABLE rides ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS schedule_id UUID;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS occurrence_date DATE;

UPDATE rides
SET expires_at = depart_at + INTERVAL '2 hours'
WHERE expires_at IS NULL;

-- ride_requests: expiry + schedule link
ALTER TABLE ride_requests ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
ALTER TABLE ride_requests ADD COLUMN IF NOT EXISTS schedule_id UUID;
ALTER TABLE ride_requests ADD COLUMN IF NOT EXISTS occurrence_date DATE;
ALTER TABLE ride_requests ADD COLUMN IF NOT EXISTS is_recurring BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE ride_requests
SET expires_at = depart_at + INTERVAL '2 hours'
WHERE expires_at IS NULL;

CREATE TABLE IF NOT EXISTS ride_schedules (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id                   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind                       TEXT NOT NULL CHECK (kind IN ('ride', 'need')),
    frequency                  TEXT NOT NULL
                                   CHECK (frequency IN (
                                       'daily', 'weekdays', 'weekends', 'weekly', 'monthly', 'custom_days'
                                   )),
    -- ISO weekdays 1=Mon … 7=Sun, comma-separated e.g. '1,2,3,4,5'
    days_of_week               TEXT,
    day_of_month               INT CHECK (day_of_month IS NULL OR (day_of_month >= 1 AND day_of_month <= 31)),
    depart_local_time          TIME NOT NULL,
    timezone                   TEXT NOT NULL DEFAULT 'Asia/Kolkata',
    active                     BOOLEAN NOT NULL DEFAULT TRUE,
    vehicle_id                 UUID REFERENCES vehicles (id),
    available_seats            INT,
    price_per_seat             NUMERIC(10, 2) DEFAULT 0,
    is_comfort_ride            BOOLEAN NOT NULL DEFAULT FALSE,
    seats_needed               INT CHECK (seats_needed IS NULL OR (seats_needed >= 1 AND seats_needed <= 3)),
    comfort_preferred          BOOLEAN NOT NULL DEFAULT FALSE,
    origin_lat                 DOUBLE PRECISION NOT NULL,
    origin_lng                 DOUBLE PRECISION NOT NULL,
    origin_label               TEXT NOT NULL,
    origin_full_address        TEXT,
    origin_private_label       TEXT,
    destination_lat            DOUBLE PRECISION NOT NULL,
    destination_lng            DOUBLE PRECISION NOT NULL,
    destination_label          TEXT NOT NULL,
    destination_full_address   TEXT,
    destination_private_label  TEXT,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ride_schedules_owner ON ride_schedules (owner_id);
CREATE INDEX IF NOT EXISTS idx_ride_schedules_active ON ride_schedules (active) WHERE active = TRUE;

ALTER TABLE rides
    DROP CONSTRAINT IF EXISTS rides_schedule_id_fkey;
ALTER TABLE rides
    ADD CONSTRAINT rides_schedule_id_fkey
    FOREIGN KEY (schedule_id) REFERENCES ride_schedules (id) ON DELETE SET NULL;

ALTER TABLE ride_requests
    DROP CONSTRAINT IF EXISTS ride_requests_schedule_id_fkey;
ALTER TABLE ride_requests
    ADD CONSTRAINT ride_requests_schedule_id_fkey
    FOREIGN KEY (schedule_id) REFERENCES ride_schedules (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_rides_schedule_occurrence
    ON rides (schedule_id, occurrence_date)
    WHERE schedule_id IS NOT NULL AND occurrence_date IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_ride_requests_schedule_occurrence
    ON ride_requests (schedule_id, occurrence_date)
    WHERE schedule_id IS NOT NULL AND occurrence_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rides_expires
    ON rides (expires_at)
    WHERE status IN ('open', 'full');

CREATE INDEX IF NOT EXISTS idx_ride_requests_expires
    ON ride_requests (expires_at)
    WHERE status = 'open';
