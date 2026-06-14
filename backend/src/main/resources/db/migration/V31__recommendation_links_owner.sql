-- flyway:transactional=true
-- Expand-only: add optional social/link columns and owner-unit columns to recommendation.
-- No constraint changes; existing rows stay untouched (all columns nullable).

ALTER TABLE recommendation
    ADD COLUMN IF NOT EXISTS instagram_url   varchar(255),
    ADD COLUMN IF NOT EXISTS facebook_url    varchar(255),
    ADD COLUMN IF NOT EXISTS whatsapp_url    varchar(255),
    ADD COLUMN IF NOT EXISTS catalog_url     varchar(500),
    ADD COLUMN IF NOT EXISTS owner_unit_id   uuid REFERENCES unit(id),
    ADD COLUMN IF NOT EXISTS owner_unit_code varchar(10);

CREATE INDEX IF NOT EXISTS idx_recommendation_owner_unit
    ON recommendation(owner_unit_id)
    WHERE owner_unit_id IS NOT NULL;
