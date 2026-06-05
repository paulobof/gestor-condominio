-- flyway:transactional=true

-- Classifieds (anúncios entre moradores). Soft delete; estados ACTIVE/SOLD/ARCHIVED.
-- Fotos no bucket MinIO `classifieds`, leitura via presigned GET. Limite 5 fotos/anúncio
-- validado no service.

CREATE TABLE classified (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint NOT NULL DEFAULT 0,
    title           varchar(120) NOT NULL,
    description     text,
    price           numeric(12,2),
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',
    author_user_id  uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    CONSTRAINT chk_classified_status CHECK (status IN ('ACTIVE','SOLD','ARCHIVED'))
);

CREATE INDEX idx_classified_status ON classified (status, created_at)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_classified_author ON classified (author_user_id)
    WHERE deleted_at IS NULL;

CREATE TABLE classified_photo (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    classified_id   uuid NOT NULL REFERENCES classified (id) ON DELETE RESTRICT,
    object_key      varchar(255) NOT NULL,
    content_type    varchar(80) NOT NULL,
    ordering        int NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz
);

CREATE UNIQUE INDEX uq_classified_photo_ordering
    ON classified_photo (classified_id, ordering)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_classified_photo_owner ON classified_photo (classified_id)
    WHERE deleted_at IS NULL;
