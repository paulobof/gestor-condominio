-- flyway:transactional=true

-- Tags livres das indicações (ex.: "encanador"). Slug citext = case-insensitive nativo
-- (extensão citext criada na V1). Criação livre por morador; TAG_MANAGE modera/deleta.
CREATE TABLE tag (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        citext NOT NULL,
    label       varchar(80) NOT NULL,
    color       varchar(20),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);
CREATE UNIQUE INDEX uq_tag_slug ON tag (slug) WHERE deleted_at IS NULL;
