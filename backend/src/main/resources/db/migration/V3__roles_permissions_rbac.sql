-- flyway:transactional=true

CREATE TABLE role (
    id              smallint PRIMARY KEY,
    name            varchar(20) NOT NULL UNIQUE,
    label           varchar(40) NOT NULL,
    max_holders     smallint,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE permission (
    id              smallint PRIMARY KEY,
    code            varchar(60) NOT NULL UNIQUE,
    label           varchar(80) NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE user_role (
    user_id             uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    role_id             smallint NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    assigned_at         timestamptz NOT NULL DEFAULT now(),
    assigned_by_user_id uuid REFERENCES "user" (id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_role_role_id ON user_role (role_id);

CREATE TABLE role_permission (
    role_id         smallint NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission_id   smallint NOT NULL REFERENCES permission (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX idx_role_permission_permission_id ON role_permission (permission_id);

CREATE TABLE user_permission_grant (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    permission_id       smallint NOT NULL REFERENCES permission (id) ON DELETE RESTRICT,
    granted_by_user_id  uuid REFERENCES "user" (id) ON DELETE SET NULL,
    granted_at          timestamptz NOT NULL DEFAULT now(),
    revoked_at          timestamptz,
    revoked_by_user_id  uuid REFERENCES "user" (id) ON DELETE SET NULL,
    CONSTRAINT chk_grant_self CHECK (user_id <> granted_by_user_id OR granted_by_user_id IS NULL)
);
CREATE UNIQUE INDEX ux_user_permission_grant_active
    ON user_permission_grant (user_id, permission_id)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_user_permission_grant_user ON user_permission_grant (user_id);
