-- flyway:transactional=true

-- Telefones úteis (CONTACT_MANAGE, só síndico) com horário por dia. Leitura por qualquer autenticado.
-- CONTACT_MANAGE (id 6) já existe + grant MANAGER (V6) — nada de permissão aqui.

CREATE TABLE contact (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version     bigint NOT NULL DEFAULT 0,
    name        varchar(120) NOT NULL,
    category    varchar(60) NOT NULL,
    phone       varchar(20) NOT NULL,
    notes       text,
    is_24h      boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);
CREATE INDEX idx_contact_listing ON contact (category, name) WHERE deleted_at IS NULL;

CREATE TABLE contact_opening_hours (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     uuid NOT NULL REFERENCES contact (id) ON DELETE CASCADE,
    day_of_week  smallint NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    opens_at     time,
    closes_at    time,
    notes        varchar(120)
);
CREATE INDEX idx_contact_oh_owner ON contact_opening_hours (owner_id, day_of_week);
