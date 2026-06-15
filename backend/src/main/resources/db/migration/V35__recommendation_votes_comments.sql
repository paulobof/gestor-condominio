-- flyway:transactional=true

-- Contadores denormalizados de votos (a listagem ordena por like_count).
ALTER TABLE recommendation ADD COLUMN like_count    integer NOT NULL DEFAULT 0;
ALTER TABLE recommendation ADD COLUMN dislike_count integer NOT NULL DEFAULT 0;

-- Voto (like/dislike): uma linha por (indicação, usuário). É reação → sem soft delete.
CREATE TABLE recommendation_vote (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id uuid NOT NULL REFERENCES recommendation (id) ON DELETE CASCADE,
    user_id           uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    value             varchar(10) NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_recommendation_vote_value CHECK (value IN ('LIKE','DISLIKE')),
    CONSTRAINT ux_recommendation_vote_user UNIQUE (recommendation_id, user_id)
);
CREATE INDEX idx_recommendation_vote_reco ON recommendation_vote (recommendation_id);

-- Comentário: conteúdo → soft delete (padrão do projeto).
CREATE TABLE recommendation_comment (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version           bigint NOT NULL DEFAULT 0,
    recommendation_id uuid NOT NULL REFERENCES recommendation (id) ON DELETE CASCADE,
    author_user_id    uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    text              text NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at        timestamptz
);
CREATE INDEX idx_recommendation_comment_reco
    ON recommendation_comment (recommendation_id, created_at)
    WHERE deleted_at IS NULL;

-- Índice para a nova ordenação (morador desc, likes desc, recência) na lista ACTIVE.
CREATE INDEX idx_recommendation_likes ON recommendation (like_count DESC, created_at DESC)
    WHERE deleted_at IS NULL;
