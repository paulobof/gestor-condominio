-- flyway:transactional=true

CREATE TABLE user_permission_grant_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    action          varchar(20) NOT NULL,
    target_user_id  uuid NOT NULL,
    permission_id   smallint NOT NULL,
    actor_user_id   uuid,
    acted_at        timestamptz NOT NULL DEFAULT now(),
    request_id      varchar(40),
    CONSTRAINT chk_grant_log_action CHECK (action IN ('GRANT','REVOKE'))
);
CREATE INDEX idx_grant_log_target_user ON user_permission_grant_log (target_user_id, acted_at);

CREATE TABLE proof_access_log (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id               uuid NOT NULL,
    target_user_id              uuid NOT NULL,
    accessed_at                 timestamptz NOT NULL DEFAULT now(),
    ip                          inet,
    user_agent                  varchar(255),
    presigned_url_ttl_seconds   integer,
    request_id                  varchar(40)
);
CREATE INDEX idx_proof_access_log_admin ON proof_access_log (admin_user_id, accessed_at);
CREATE INDEX idx_proof_access_log_target ON proof_access_log (target_user_id, accessed_at);

CREATE TABLE sensitive_access_log (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id       uuid NOT NULL,
    target_user_id      uuid,
    action              varchar(40) NOT NULL,
    acted_at            timestamptz NOT NULL DEFAULT now(),
    client_ip           inet,
    user_agent          varchar(255),
    request_id          varchar(40)
);
CREATE INDEX idx_sensitive_access_log_actor ON sensitive_access_log (actor_user_id, acted_at);
CREATE INDEX idx_sensitive_access_log_target ON sensitive_access_log (target_user_id, acted_at) WHERE target_user_id IS NOT NULL;
