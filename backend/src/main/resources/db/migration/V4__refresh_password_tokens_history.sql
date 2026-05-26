-- flyway:transactional=true

CREATE TABLE refresh_token (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint NOT NULL DEFAULT 0,
    user_id             uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    token_hash          varchar(255) NOT NULL,
    token_family        uuid NOT NULL,
    expires_at          timestamptz NOT NULL,
    revoked             boolean NOT NULL DEFAULT false,
    revoked_at          timestamptz,
    revoked_reason      varchar(80),
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by_user_id  uuid,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    updated_by_user_id  uuid,
    deleted_at          timestamptz,
    deleted_by_user_id  uuid
);
CREATE UNIQUE INDEX ux_refresh_token_hash ON refresh_token (token_hash);
CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_refresh_token_family ON refresh_token (token_family) WHERE revoked = false;
CREATE INDEX idx_refresh_token_active_expires ON refresh_token (expires_at) WHERE revoked = false AND deleted_at IS NULL;

CREATE TABLE password_history (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    password_hash               varchar(255) NOT NULL,
    password_pepper_version     smallint NOT NULL,
    created_at                  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_history_user_created ON password_history (user_id, created_at DESC);

CREATE TABLE password_reset_token (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    token_hash          varchar(255) NOT NULL,
    expires_at          timestamptz NOT NULL,
    used_at             timestamptz,
    created_ip          inet,
    delivered_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_password_reset_token_hash ON password_reset_token (token_hash);
CREATE INDEX idx_password_reset_token_user ON password_reset_token (user_id, used_at);
CREATE INDEX idx_password_reset_token_expires ON password_reset_token (expires_at) WHERE used_at IS NULL;
