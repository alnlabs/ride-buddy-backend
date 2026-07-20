-- Multiple saved homes / offices per user (private labels for owner only).
CREATE TABLE IF NOT EXISTS user_places (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind            TEXT NOT NULL CHECK (kind IN ('home', 'office')),
    private_label   TEXT NOT NULL,
    public_short    TEXT NOT NULL,
    full_address    TEXT,
    lat             DOUBLE PRECISION NOT NULL,
    lng             DOUBLE PRECISION NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_places_user ON user_places (user_id);
CREATE INDEX IF NOT EXISTS idx_user_places_user_kind ON user_places (user_id, kind);

-- At most one primary per kind per user.
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_places_primary_kind
    ON user_places (user_id, kind)
    WHERE is_primary = TRUE;

-- Seed from legacy profile home / office columns.
INSERT INTO user_places (user_id, kind, private_label, public_short, full_address, lat, lng, is_primary)
SELECT
    p.user_id,
    'home',
    COALESCE(NULLIF(TRIM(p.home_label), ''), 'Home'),
    COALESCE(NULLIF(TRIM(p.home_label), ''), 'Home'),
    p.home_label,
    p.home_lat,
    p.home_lng,
    TRUE
FROM profiles p
WHERE p.home_lat IS NOT NULL AND p.home_lng IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM user_places up WHERE up.user_id = p.user_id AND up.kind = 'home'
  );

INSERT INTO user_places (user_id, kind, private_label, public_short, full_address, lat, lng, is_primary)
SELECT
    p.user_id,
    'office',
    COALESCE(NULLIF(TRIM(p.office_label), ''), 'Office'),
    COALESCE(NULLIF(TRIM(p.office_label), ''), 'Office'),
    p.office_label,
    p.office_lat,
    p.office_lng,
    TRUE
FROM profiles p
WHERE p.office_lat IS NOT NULL AND p.office_lng IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM user_places up WHERE up.user_id = p.user_id AND up.kind = 'office'
  );
