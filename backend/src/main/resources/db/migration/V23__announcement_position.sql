-- flyway:transactional=true

-- Reordenação manual do mural: substitui "pinned" por "position" (ordem definida pelo editor).
-- Topo = menor position. Decisão registrada no spec: tudo num migration (app single-instance).

ALTER TABLE announcement ADD COLUMN position integer;

-- Backfill preservando a ordem atual: fixados primeiro, depois mais recentes.
WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY pinned DESC, published_at DESC) AS rn
    FROM announcement
)
UPDATE announcement a SET position = ordered.rn
FROM ordered WHERE ordered.id = a.id;

ALTER TABLE announcement ALTER COLUMN position SET NOT NULL;

-- Troca o índice do feed: antes (pinned DESC, published_at DESC); agora (position).
DROP INDEX IF EXISTS idx_announcement_feed;
CREATE INDEX idx_announcement_position ON announcement (position) WHERE deleted_at IS NULL;

ALTER TABLE announcement DROP COLUMN pinned;
