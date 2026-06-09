-- flyway:transactional=true

-- Informações Gerais: seções livres (título + corpo rich text sanitizado), ordem manual.
-- Leitura por qualquer autenticado; escrita só com INFO_MANAGE (síndico).

CREATE TABLE info_section (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version     bigint NOT NULL DEFAULT 0,
    title       varchar(120) NOT NULL,
    body        text NOT NULL,
    position    integer NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);
CREATE INDEX idx_info_section_position ON info_section (position) WHERE deleted_at IS NULL;

-- Nova permissão INFO_MANAGE (próximo id livre = 16; id 15 já é ANNOUNCEMENT_MANAGE na V18)
-- + grant ao síndico (role 1). O grant em massa de MANAGER na V6 (SELECT 1, id FROM permission)
-- já rodou antes desta permissão existir, então é necessário o grant explícito aqui.
INSERT INTO permission (id, code, label) VALUES
    (16, 'INFO_MANAGE', 'Gerenciar informações gerais');
INSERT INTO role_permission (role_id, permission_id) VALUES (1, 16);

-- Remove o módulo de Contatos (feature recém-criada, flag off em prod; HML usa seed sintético).
DROP TABLE IF EXISTS contact_opening_hours;
DROP TABLE IF EXISTS contact;

-- Remove a permissão CONTACT_MANAGE (id 6) e seus grants — sai junto com o módulo.
DELETE FROM role_permission WHERE permission_id = 6;
DELETE FROM permission WHERE id = 6;
