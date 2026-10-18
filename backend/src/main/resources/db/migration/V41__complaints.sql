-- flyway:transactional=true

-- Chamados: moradores reportam problemas do prédio; o admin analisa e encaminha em lote semanal
-- à síndica (o app só gera o texto; envio manual). Catálogo tipo/subtipo/local gerido pelo admin.

CREATE TABLE complaint_type (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version     bigint NOT NULL DEFAULT 0,
    name        varchar(80) NOT NULL,
    active      boolean NOT NULL DEFAULT true,
    sort_order  int NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);
CREATE UNIQUE INDEX ux_complaint_type_name ON complaint_type (lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE complaint_subtype (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version     bigint NOT NULL DEFAULT 0,
    type_id     uuid NOT NULL REFERENCES complaint_type (id) ON DELETE RESTRICT,
    name        varchar(80) NOT NULL,
    active      boolean NOT NULL DEFAULT true,
    sort_order  int NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);
CREATE UNIQUE INDEX ux_complaint_subtype_name ON complaint_subtype (type_id, lower(name))
    WHERE deleted_at IS NULL;

CREATE TABLE complaint_location (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version     bigint NOT NULL DEFAULT 0,
    name        varchar(80) NOT NULL,
    active      boolean NOT NULL DEFAULT true,
    sort_order  int NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);
CREATE UNIQUE INDEX ux_complaint_location_name ON complaint_location (lower(name))
    WHERE deleted_at IS NULL;

CREATE TABLE complaint_batch (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sent_by_user_id  uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    sent_at          timestamptz NOT NULL,
    rendered_text    text NOT NULL,
    complaint_count  int NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz
);
CREATE INDEX idx_complaint_batch_sent ON complaint_batch (sent_at DESC) WHERE deleted_at IS NULL;

CREATE SEQUENCE complaint_code_seq;

CREATE TABLE complaint (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version           bigint NOT NULL DEFAULT 0,
    code              bigint NOT NULL DEFAULT nextval('complaint_code_seq'),
    author_user_id    uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    author_unit_id    uuid NOT NULL REFERENCES unit (id) ON DELETE RESTRICT,
    type_id           uuid NOT NULL REFERENCES complaint_type (id) ON DELETE RESTRICT,
    subtype_id        uuid NOT NULL REFERENCES complaint_subtype (id) ON DELETE RESTRICT,
    location_id       uuid NOT NULL REFERENCES complaint_location (id) ON DELETE RESTRICT,
    location_detail   varchar(200),
    description       varchar(2000) NOT NULL,
    status            varchar(20) NOT NULL DEFAULT 'OPEN',
    due_date          date,
    rejection_reason  varchar(500),
    resolution_note   varchar(500),
    syndic_response   varchar(2000),
    syndic_response_at timestamptz,
    batch_id          uuid REFERENCES complaint_batch (id) ON DELETE RESTRICT,
    forwarded_at      timestamptz,
    resolved_at       timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at        timestamptz,
    CONSTRAINT ux_complaint_code UNIQUE (code),
    CONSTRAINT chk_complaint_status CHECK (status IN
        ('OPEN','IN_REVIEW','READY','FORWARDED','RESOLVED','REJECTED')),
    CONSTRAINT chk_complaint_description CHECK (length(btrim(description)) >= 10)
);
ALTER SEQUENCE complaint_code_seq OWNED BY complaint.code;
CREATE INDEX idx_complaint_status ON complaint (status, created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_complaint_author ON complaint (author_user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE complaint_photo (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id  uuid NOT NULL REFERENCES complaint (id) ON DELETE RESTRICT,
    object_key    varchar(255) NOT NULL,
    content_type  varchar(80) NOT NULL,
    size_bytes    bigint NOT NULL,
    ordering      int NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    deleted_at    timestamptz
);
CREATE UNIQUE INDEX uq_complaint_photo_ordering ON complaint_photo (complaint_id, ordering)
    WHERE deleted_at IS NULL;

-- Log imutável (sem soft delete, como os demais logs). Nunca guardar PII em from/to.
CREATE TABLE complaint_event (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id   uuid NOT NULL REFERENCES complaint (id) ON DELETE RESTRICT,
    actor_user_id  uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    kind           varchar(30) NOT NULL,
    from_value     varchar(500),
    to_value       varchar(500),
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_complaint_event_complaint ON complaint_event (complaint_id, created_at);

-- Permissões (22/23 = próximas livres; máx atual = 21) e role atribuível pela tela de acessos
-- (10 = próximo livre; máx atual = 9). Síndico (role 1) também recebe as duas.
INSERT INTO permission (id, code, label) VALUES
    (22, 'COMPLAINT_MANAGE',         'Gerenciar chamados'),
    (23, 'COMPLAINT_CATALOG_MANAGE', 'Gerenciar catálogo de chamados');

INSERT INTO role (id, name, label, max_holders, assignable)
VALUES (10, 'COMPLAINT_MANAGER', 'Gestor de Chamados', NULL, true);

INSERT INTO role_permission (role_id, permission_id) VALUES
    (10, 22), (10, 23), (1, 22), (1, 23);

-- Catálogo inicial (editável depois pela tela de catálogo).
WITH t AS (
    INSERT INTO complaint_type (name, sort_order) VALUES
        ('Manutenção', 1), ('Limpeza', 2), ('Segurança', 3), ('Convivência', 4), ('Outros', 5)
    RETURNING id, name
)
INSERT INTO complaint_subtype (type_id, name, sort_order)
SELECT t.id, s.name, s.ord
FROM t JOIN (VALUES
    ('Manutenção', 'Pintura', 1), ('Manutenção', 'Elétrica', 2), ('Manutenção', 'Hidráulica', 3),
    ('Manutenção', 'Alvenaria', 4), ('Manutenção', 'Elevador', 5), ('Manutenção', 'Portões', 6),
    ('Limpeza', 'Áreas comuns', 1), ('Limpeza', 'Garagem', 2),
    ('Segurança', 'Portaria', 1), ('Segurança', 'Câmeras', 2), ('Segurança', 'Iluminação', 3),
    ('Convivência', 'Barulho', 1), ('Convivência', 'Uso indevido', 2),
    ('Outros', 'Outros', 1)
) AS s(type_name, name, ord) ON s.type_name = t.name;

INSERT INTO complaint_location (name, sort_order) VALUES
    ('Torre 1', 1), ('Torre 2', 2), ('Torre 3', 3), ('Garagem', 4), ('Área de lazer', 5),
    ('Piscina', 6), ('Academia', 7), ('Salão de festas', 8), ('Portaria', 9), ('Fachada', 10),
    ('Jardim', 11);
