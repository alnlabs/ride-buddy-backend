-- Office email verification (employee badge) + optional personal/social contact email.
-- Real mail delivery comes later — verification codes are mockable for now.

ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS office_email TEXT,
    ADD COLUMN IF NOT EXISTS office_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS office_email_pending_code TEXT,
    ADD COLUMN IF NOT EXISTS office_email_code_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS contact_email TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_profiles_office_email_verified
    ON profiles (lower(office_email))
    WHERE office_email IS NOT NULL AND office_email_verified = TRUE;
