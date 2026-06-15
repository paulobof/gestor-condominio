-- flyway:transactional=true

-- Documentos do condomínio (RI, AGE, atas, ...). O arquivo (PDF) fica no MinIO; aqui só metadados.
-- Soft delete; tipo restrito por CHECK.
CREATE TABLE document (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version              bigint NOT NULL DEFAULT 0,
    title                varchar(180) NOT NULL,
    type                 varchar(20) NOT NULL,
    object_key           text NOT NULL,
    filename             varchar(255) NOT NULL,
    content_type         varchar(80) NOT NULL,
    size_bytes           bigint NOT NULL,
    uploaded_by_user_id  uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    deleted_at           timestamptz,
    CONSTRAINT chk_document_type
        CHECK (type IN ('RI','AGE','AGO','ATA','CONVENCAO','EDITAL','OUTRO'))
);

CREATE INDEX idx_document_created ON document (created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_document_type ON document (type, created_at) WHERE deleted_at IS NULL;

-- Permission de gerenciar documentos (id 21 = próximo livre; máx atual = 20).
INSERT INTO permission (id, code, label) VALUES
    (21, 'DOCUMENT_MANAGE', 'Gerenciar documentos do condomínio');

-- Role nova "Editor de Documentos" (id 8 = próximo livre; máx atual = 7), gerível pela tela de acessos.
INSERT INTO role (id, name, label, max_holders, assignable)
VALUES (8, 'DOCUMENT_EDITOR', 'Editor de Documentos', NULL, true);

-- Concede DOCUMENT_MANAGE à role nova e ao Síndico (MANAGER, role 1).
INSERT INTO role_permission (role_id, permission_id) VALUES
    (8, 21),
    (1, 21);
