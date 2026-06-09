-- flyway:transactional=true

-- Log imutável de atribuição/remoção de roles (auditoria do "Gerenciar acessos").
-- Sem deleted_at: é log append-only, como sensitive_access_log. Hard delete permitido por exceção.
CREATE TABLE role_assignment_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    action          varchar(10) NOT NULL,   -- 'ASSIGN' | 'REMOVE'
    target_user_id  uuid NOT NULL,
    role_id         smallint NOT NULL,
    actor_user_id   uuid NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_role_assignment_log_target ON role_assignment_log (target_user_id);
