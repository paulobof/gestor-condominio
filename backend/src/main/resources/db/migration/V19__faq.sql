-- flyway:transactional=true

-- FAQ: o síndico/conselho (FAQ_MANAGE) publica perguntas frequentes; moradores autenticados leem
-- os publicados. Categoria é texto livre; agrupamento e ordenação na listagem. Soft delete.
-- FAQ_MANAGE (id 8) já existe e tem grants (V6) — nada de permissão aqui.

CREATE TABLE faq (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version     bigint NOT NULL DEFAULT 0,
    question    varchar(200) NOT NULL,
    answer      text NOT NULL,
    category    varchar(80) NOT NULL,
    published   boolean NOT NULL DEFAULT false,
    ordering    integer NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);

CREATE INDEX idx_faq_listing ON faq (category, ordering)
    WHERE deleted_at IS NULL;
