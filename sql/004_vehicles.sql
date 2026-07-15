CREATE TABLE IF NOT EXISTS vehicles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    nickname    TEXT,
    make_model  TEXT NOT NULL,
    plate_number TEXT NOT NULL,
    seats       INT NOT NULL CHECK (seats >= 1 AND seats <= 8),
    color       TEXT,
    is_primary  BOOLEAN NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vehicles_owner ON vehicles (owner_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_vehicles_one_primary_per_owner
    ON vehicles (owner_id)
    WHERE is_primary = TRUE AND is_active = TRUE;
