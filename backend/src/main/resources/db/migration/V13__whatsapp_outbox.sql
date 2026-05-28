-- flyway:transactional=true

-- Outbox de notificações WhatsApp outbound. Toda mensagem enviada pelo bot do
-- Paulo passa por aqui (PENDING -> SENT/FAILED). Job @Scheduled reprocessa
-- entradas FAILED < max_attempts.

CREATE TABLE whatsapp_outbox (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    to_phone            varchar(20) NOT NULL,
    template            varchar(60) NOT NULL,
    payload             jsonb NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'PENDING',
    attempts            smallint NOT NULL DEFAULT 0,
    last_attempt_at     timestamptz,
    error_message       text,
    sent_at             timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz,
    deleted_by_user_id  uuid REFERENCES "user" (id),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','SENT','FAILED'))
);

-- Index do job de reprocessamento: pega rapidamente o que precisa retry.
CREATE INDEX idx_outbox_retryable ON whatsapp_outbox (created_at)
    WHERE status IN ('PENDING','FAILED') AND deleted_at IS NULL;

-- Index geral por status para dashboards/observabilidade.
CREATE INDEX idx_outbox_status ON whatsapp_outbox (status, created_at)
    WHERE deleted_at IS NULL;
