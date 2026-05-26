-- flyway:transactional=true

-- Index covering para listagem rápida de PENDING_APPROVAL
CREATE INDEX IF NOT EXISTS idx_user_status_pending
    ON "user" (created_at DESC)
    WHERE status = 'PENDING_APPROVAL' AND deleted_at IS NULL;

-- Index para lookup de unidade por master
CREATE INDEX IF NOT EXISTS idx_unit_master_user
    ON unit (master_user_id)
    WHERE deleted_at IS NULL;
