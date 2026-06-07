-- flyway:transactional=true

-- Mural de avisos: o síndico (ANNOUNCEMENT_MANAGE) publica avisos; moradores autenticados leem.
-- Soft delete; avisos fixados (pinned) aparecem primeiro, depois por published_at desc.
-- Sem fotos/anexos no MVP. Notificação WhatsApp broadcast fica para fase 4b.

CREATE TABLE announcement (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint NOT NULL DEFAULT 0,
    title           varchar(140) NOT NULL,
    body            text NOT NULL,
    pinned          boolean NOT NULL DEFAULT false,
    published_at    timestamptz NOT NULL DEFAULT now(),
    author_user_id  uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz
);

-- Feed do mural: fixados primeiro, depois mais recentes. Cobre o sort padrão da listagem.
CREATE INDEX idx_announcement_feed ON announcement (pinned DESC, published_at DESC)
    WHERE deleted_at IS NULL;

-- Permissão de gestão do mural. MANAGER (síndico) e COUNCIL (conselho) recebem.
INSERT INTO permission (id, code, label) VALUES
    (15, 'ANNOUNCEMENT_MANAGE', 'Gerenciar mural de avisos');

INSERT INTO role_permission (role_id, permission_id) VALUES (1, 15), (2, 15);
