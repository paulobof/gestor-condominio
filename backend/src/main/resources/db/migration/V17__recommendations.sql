-- flyway:transactional=true

-- Indicações de profissionais. Soft delete. Quando o profissional é morador
-- (is_resident), entra como PENDING_RESIDENT_CONSENT até o morador aprovar.
CREATE TABLE recommendation (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint NOT NULL DEFAULT 0,
    service_name            varchar(120) NOT NULL,
    professional_name       varchar(120),
    phone                   varchar(20),
    is_resident             boolean NOT NULL DEFAULT false,
    resident_user_id        uuid REFERENCES "user" (id) ON DELETE RESTRICT,
    address_line            varchar(255),
    price_range             varchar(40),
    rating                  smallint,
    comment                 text,
    recommended_by_user_id  uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    status                  varchar(30) NOT NULL DEFAULT 'ACTIVE',
    resident_consent_at     timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    deleted_at              timestamptz,
    CONSTRAINT chk_reco_status CHECK (status IN ('ACTIVE','PENDING_RESIDENT_CONSENT','HIDDEN')),
    CONSTRAINT chk_reco_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reco_resident CHECK (is_resident = false OR resident_user_id IS NOT NULL)
);
CREATE INDEX idx_reco_status ON recommendation (status, created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_reco_resident_pending ON recommendation (resident_user_id, status)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_reco_author ON recommendation (recommended_by_user_id) WHERE deleted_at IS NULL;

CREATE TABLE recommendation_photo (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id uuid NOT NULL REFERENCES recommendation (id) ON DELETE RESTRICT,
    object_key        varchar(255) NOT NULL,
    content_type      varchar(80) NOT NULL,
    ordering          int NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at        timestamptz
);
-- ordering pode ter gaps após soft-delete; service normaliza (max+1). Índice único também
-- serve lookups por recommendation_id (prefixo à esquerda).
CREATE UNIQUE INDEX uq_reco_photo_ordering ON recommendation_photo (recommendation_id, ordering)
    WHERE deleted_at IS NULL;

-- M:N puro recommendation<->tag. Sem soft delete, CASCADE.
CREATE TABLE recommendation_tag (
    recommendation_id uuid NOT NULL REFERENCES recommendation (id) ON DELETE CASCADE,
    tag_id            uuid NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (recommendation_id, tag_id)
);

-- Horários de funcionamento. FK real, sem polimorfismo, sem soft delete (CASCADE).
CREATE TABLE recommendation_opening_hours (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    uuid NOT NULL REFERENCES recommendation (id) ON DELETE CASCADE,
    day_of_week smallint NOT NULL,
    opens_at    time,
    closes_at   time,
    notes       varchar(120),
    CONSTRAINT chk_reco_oh_dow CHECK (day_of_week BETWEEN 0 AND 6)
);
CREATE INDEX idx_reco_oh_owner ON recommendation_opening_hours (owner_id, day_of_week);
