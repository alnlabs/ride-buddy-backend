CREATE TABLE IF NOT EXISTS profile_interests (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tag        TEXT NOT NULL,
    category   TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, tag)
);

CREATE INDEX IF NOT EXISTS idx_profile_interests_user ON profile_interests (user_id);
