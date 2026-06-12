-- flyway:transactional=true

-- Posse de unidade (proprietário ↔ unidade). Move comprovante + estado de aprovação
-- (hoje na linha do "user") para o par (usuário, unidade), habilitando multi-unidade.
CREATE TABLE unit_ownership (
    id                            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                       bigint NOT NULL DEFAULT 0,
    user_id                       uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    unit_id                       uuid NOT NULL REFERENCES unit (id) ON DELETE RESTRICT,
    status                        text NOT NULL,
    residence_proof_object_key    text,
    residence_proof_filename      text,
    residence_proof_content_type  varchar(80),
    residence_proof_uploaded_at   timestamptz,
    proof_verified_at             timestamptz,
    approved_by_user_id           uuid,
    approved_at                   timestamptz,
    rejection_reason              text,
    created_at                    timestamptz NOT NULL DEFAULT now(),
    updated_at                    timestamptz,
    created_by_user_id            uuid,
    updated_by_user_id            uuid,
    deleted_at                    timestamptz,
    deleted_by_user_id            uuid,
    CONSTRAINT chk_unit_ownership_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- 1 master por unidade (posse APPROVED única por unidade).
CREATE UNIQUE INDEX ux_unit_ownership_unit_approved
    ON unit_ownership (unit_id)
    WHERE status = 'APPROVED' AND deleted_at IS NULL;

-- Sem claim duplicado do mesmo usuário na mesma unidade.
CREATE UNIQUE INDEX ux_unit_ownership_user_unit_open
    ON unit_ownership (user_id, unit_id)
    WHERE status IN ('PENDING', 'APPROVED') AND deleted_at IS NULL;

-- Lista de pendências (admin).
CREATE INDEX idx_unit_ownership_pending
    ON unit_ownership (created_at)
    WHERE status = 'PENDING' AND deleted_at IS NULL;

-- "Minhas unidades" (posses aprovadas do usuário).
CREATE INDEX idx_unit_ownership_user_approved
    ON unit_ownership (user_id)
    WHERE status = 'APPROVED' AND deleted_at IS NULL;

-- Backfill: cada master atual vira 1 posse, copiando o comprovante da linha do user.
INSERT INTO unit_ownership (
    user_id, unit_id, status,
    residence_proof_object_key, residence_proof_filename, residence_proof_content_type,
    residence_proof_uploaded_at, proof_verified_at, approved_by_user_id, approved_at,
    rejection_reason, created_at)
SELECT
    u.id, u.unit_id,
    CASE u.status
        WHEN 'ACTIVE' THEN 'APPROVED'
        WHEN 'PENDING_APPROVAL' THEN 'PENDING'
        WHEN 'REJECTED' THEN 'REJECTED'
        ELSE 'APPROVED'
    END,
    u.residence_proof_object_key, u.residence_proof_filename, u.residence_proof_content_type,
    u.residence_proof_uploaded_at, u.proof_verified_at, u.approved_by_user_id, u.approved_at,
    u.rejection_reason, u.created_at
FROM "user" u
WHERE u.is_unit_master = true AND u.unit_id IS NOT NULL AND u.deleted_at IS NULL;

-- Libera um usuário ser master de N unidades (removendo a unicidade por master_user_id).
DROP INDEX IF EXISTS ux_unit_master_user_active;
