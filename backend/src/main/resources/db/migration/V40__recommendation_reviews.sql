-- flyway:transactional=true

-- Avaliações das indicações: uma por apartamento, com nota, sentimento e comentário obrigatório.
-- Substitui recommendation_vote e recommendation_comment (V35), ambas vazias em produção.

CREATE TABLE recommendation_review (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version           bigint NOT NULL DEFAULT 0,
    recommendation_id uuid NOT NULL REFERENCES recommendation (id) ON DELETE CASCADE,
    unit_id           uuid NOT NULL REFERENCES unit (id) ON DELETE RESTRICT,
    author_user_id    uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    sentiment         varchar(10) NOT NULL,
    rating            smallint NOT NULL,
    text              text NOT NULL,
    status            varchar(10) NOT NULL DEFAULT 'ACTIVE',
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at        timestamptz,
    CONSTRAINT chk_review_sentiment CHECK (sentiment IN ('LIKE', 'DISLIKE')),
    CONSTRAINT chk_review_rating    CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_review_status    CHECK (status IN ('ACTIVE', 'HIDDEN')),
    CONSTRAINT chk_review_text      CHECK (length(btrim(text)) >= 10)
);

-- Cota do apartamento. PARCIAL de propósito: apagar a própria avaliação libera a vaga;
-- avaliação apenas OCULTA (moderada) continua ocupando, para o abuso não ser reescrito.
CREATE UNIQUE INDEX ux_review_unit ON recommendation_review (recommendation_id, unit_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_review_reco ON recommendation_review (recommendation_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- Contadores desnormalizados. Guarda a SOMA das notas, não a média: soma é inteiro exato,
-- média em coluna acumula erro de arredondamento a cada recálculo.
ALTER TABLE recommendation ADD COLUMN review_count integer NOT NULL DEFAULT 0;
ALTER TABLE recommendation ADD COLUMN rating_sum   integer NOT NULL DEFAULT 0;

-- A nota e o comentário do autor viram a primeira avaliação; as colunas saem da indicação.
ALTER TABLE recommendation DROP COLUMN rating;
ALTER TABLE recommendation DROP COLUMN comment;

DROP TABLE recommendation_vote;
DROP TABLE recommendation_comment;
