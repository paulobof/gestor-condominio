-- flyway:transactional=true

-- Aluguel de vagas (anúncios entre moradores). Soft delete; estados ACTIVE/RENTED/ARCHIVED.
-- Sem fotos, sem título/descrição. Contato (nome/telefone) é resolvido do perfil do autor
-- na camada de aplicação, não armazenado aqui.
CREATE TABLE parking_rental (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint NOT NULL DEFAULT 0,
    tower           varchar(40) NOT NULL,
    floor           varchar(20) NOT NULL,
    spot_number     varchar(40) NOT NULL,
    monthly_price   numeric(12,2) NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',
    author_user_id  uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    CONSTRAINT chk_parking_rental_status CHECK (status IN ('ACTIVE','RENTED','ARCHIVED'))
);

CREATE INDEX idx_parking_rental_status ON parking_rental (status, created_at)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_parking_rental_author ON parking_rental (author_user_id)
    WHERE deleted_at IS NULL;
