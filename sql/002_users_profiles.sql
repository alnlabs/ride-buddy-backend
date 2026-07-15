CREATE TABLE IF NOT EXISTS users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone      TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS otp_challenges (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone      TEXT NOT NULL,
    code       TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_otp_challenges_phone ON otp_challenges (phone);

CREATE TABLE IF NOT EXISTS profiles (
    user_id              UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    display_name         TEXT,
    avatar_url           TEXT,
    home_lat             DOUBLE PRECISION,
    home_lng             DOUBLE PRECISION,
    home_label           TEXT,
    home_area_slug       TEXT,
    office_lat           DOUBLE PRECISION,
    office_lng           DOUBLE PRECISION,
    office_label         TEXT,
    office_area_slug     TEXT,
    experience_bio       TEXT,
    years_experience     INT,
    can_offer_rides      BOOLEAN NOT NULL DEFAULT FALSE,
    profile_strength     INT NOT NULL DEFAULT 0,
    emergency_contact_name  TEXT,
    emergency_contact_phone TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
