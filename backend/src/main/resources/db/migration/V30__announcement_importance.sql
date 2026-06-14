-- flyway:transactional=true

-- Níveis de importância para avisos do mural.
-- 3 valores: HIGH (Urgente), MEDIUM (Importante), LOW (Informativo).
-- Default = 'MEDIUM' (nível do meio); backward-compatible (expand-only, sem ALTER TYPE).

ALTER TABLE announcement
    ADD COLUMN importance varchar(10)
        NOT NULL DEFAULT 'MEDIUM'
        CONSTRAINT chk_announcement_importance CHECK (importance IN ('HIGH', 'MEDIUM', 'LOW'));
