-- flyway:transactional=true

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =========================================================================
-- UNIT — 522 unidades pré-cadastradas (seed em V6)
-- =========================================================================
CREATE TABLE unit (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint NOT NULL DEFAULT 0,
    tower                   varchar(1) NOT NULL,
    floor                   smallint NOT NULL,
    "position"              smallint NOT NULL,
    code                    varchar(8) NOT NULL,
    master_user_id          uuid,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by_user_id      uuid,
    updated_by_user_id      uuid,
    deleted_at              timestamptz,
    deleted_by_user_id      uuid,
    CONSTRAINT chk_unit_tower CHECK (tower IN ('A','B','C')),
    CONSTRAINT chk_unit_floor CHECK (floor BETWEEN 4 AND 32),
    CONSTRAINT chk_unit_position CHECK ("position" BETWEEN 1 AND 6)
);

CREATE UNIQUE INDEX ux_unit_tower_floor_position_active
    ON unit (tower, floor, "position")
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_unit_code_active
    ON unit (code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_unit_master_user_active
    ON unit (master_user_id)
    WHERE deleted_at IS NULL AND master_user_id IS NOT NULL;

-- FK master_user_id → user(id) será adicionada na V2 (após "user" existir).
