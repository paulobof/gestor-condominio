-- flyway:transactional=true

-- =========================================================================
-- CONSENT_DOCUMENT
-- =========================================================================
CREATE TABLE consent_document (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 varchar(20) NOT NULL,
    body                    text NOT NULL,
    published_at            timestamptz NOT NULL DEFAULT now(),
    created_at              timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_consent_document_version ON consent_document (version);

-- =========================================================================
-- USER
-- =========================================================================
CREATE TABLE "user" (
    id                              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                         bigint NOT NULL DEFAULT 0,
    unit_id                         uuid REFERENCES unit (id) ON DELETE RESTRICT,
    is_unit_master                  boolean NOT NULL DEFAULT false,
    full_name                       varchar(180) NOT NULL,
    greeting_name                   varchar(60),
    phone                           varchar(20),
    phone_verified_at               timestamptz,
    gender                          varchar(20),
    birth_date                      date,
    password_hash                   varchar(255) NOT NULL,
    password_pepper_version         smallint NOT NULL DEFAULT 1,
    must_change_password            boolean NOT NULL DEFAULT false,
    status                          varchar(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    residence_proof_object_key      varchar(255),
    residence_proof_filename        varchar(255),
    residence_proof_content_type    varchar(80),
    residence_proof_uploaded_at     timestamptz,
    proof_verified_at               timestamptz,
    approved_by_user_id             uuid,
    approved_at                     timestamptz,
    rejection_reason                text,
    anonymized_at                   timestamptz,
    consent_document_version        varchar(20),
    consent_accepted_at             timestamptz,
    consent_accepted_ip             inet,
    whatsapp_opt_in                 boolean NOT NULL DEFAULT false,
    whatsapp_opt_in_at              timestamptz,
    created_at                      timestamptz NOT NULL DEFAULT now(),
    updated_at                      timestamptz NOT NULL DEFAULT now(),
    created_by_user_id              uuid,
    updated_by_user_id              uuid,
    deleted_at                      timestamptz,
    deleted_by_user_id              uuid,
    CONSTRAINT chk_user_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER','NOT_INFORMED')),
    CONSTRAINT chk_user_status CHECK (status IN ('PENDING_APPROVAL','ACTIVE','REJECTED','DISABLED','ANONYMIZED')),
    CONSTRAINT chk_user_master_needs_unit CHECK (is_unit_master = false OR unit_id IS NOT NULL)
);

CREATE INDEX idx_user_status ON "user" (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_unit_id ON "user" (unit_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_full_name_trgm ON "user" USING gin (full_name gin_trgm_ops);

ALTER TABLE unit
    ADD CONSTRAINT fk_unit_master_user
    FOREIGN KEY (master_user_id) REFERENCES "user" (id) ON DELETE RESTRICT;

-- =========================================================================
-- USER_EMAIL
-- =========================================================================
CREATE TABLE user_email (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint NOT NULL DEFAULT 0,
    user_id                 uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    email                   citext NOT NULL,
    is_primary              boolean NOT NULL DEFAULT false,
    verified_at             timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by_user_id      uuid,
    updated_by_user_id      uuid,
    deleted_at              timestamptz,
    deleted_by_user_id      uuid
);

CREATE UNIQUE INDEX ux_user_email_email_active ON user_email (email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_user_email_primary_per_user_active ON user_email (user_id) WHERE is_primary = true AND deleted_at IS NULL;
CREATE INDEX idx_user_email_user_id ON user_email (user_id) WHERE deleted_at IS NULL;
