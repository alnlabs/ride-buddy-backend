-- Work identity shown on ride / need posts + ranked interests for "top 5 on posts".

ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS job_role TEXT,
    ADD COLUMN IF NOT EXISTS company TEXT;

ALTER TABLE profile_interests
    ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 100;

-- Lower sort_order = higher priority on posts (0–4 are the featured top 5).
CREATE INDEX IF NOT EXISTS idx_profile_interests_user_sort
    ON profile_interests (user_id, sort_order ASC, created_at ASC);
