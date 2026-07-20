-- Public short stays in origin_label / destination_label.
-- Full address + private labels (owner-only in API responses).
ALTER TABLE rides
    ADD COLUMN IF NOT EXISTS origin_full_address TEXT,
    ADD COLUMN IF NOT EXISTS destination_full_address TEXT,
    ADD COLUMN IF NOT EXISTS origin_private_label TEXT,
    ADD COLUMN IF NOT EXISTS destination_private_label TEXT;

ALTER TABLE ride_requests
    ADD COLUMN IF NOT EXISTS origin_full_address TEXT,
    ADD COLUMN IF NOT EXISTS destination_full_address TEXT,
    ADD COLUMN IF NOT EXISTS origin_private_label TEXT,
    ADD COLUMN IF NOT EXISTS destination_private_label TEXT;
