-- flyway:transactional=true

-- Indicações deixam de exigir consentimento do morador indicado: toda indicação fica ACTIVE.
-- Remove o status PENDING_RESIDENT_CONSENT, a coluna de consentimento e o índice de lookup pendente.

-- 1) Promove indicações que estavam aguardando consentimento para ACTIVE.
UPDATE recommendation SET status = 'ACTIVE' WHERE status = 'PENDING_RESIDENT_CONSENT';

-- 2) Restringe o CHECK de status (sem PENDING_RESIDENT_CONSENT).
ALTER TABLE recommendation DROP CONSTRAINT chk_reco_status;
ALTER TABLE recommendation ADD CONSTRAINT chk_reco_status CHECK (status IN ('ACTIVE', 'HIDDEN'));

-- 3) Remove artefatos do fluxo de consentimento.
DROP INDEX IF EXISTS idx_reco_resident_pending;
ALTER TABLE recommendation DROP COLUMN resident_consent_at;
