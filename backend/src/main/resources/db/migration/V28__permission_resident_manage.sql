-- flyway:transactional=true

-- Permissão do proprietário: gerir moradores das suas unidades.
INSERT INTO permission (id, code, label) VALUES
    (17, 'RESIDENT_MANAGE', 'Gerir moradores das minhas unidades');

-- Admin (MANAGER) recebe via role_permission (consistente com as demais permissions globais).
INSERT INTO role_permission (role_id, permission_id)
SELECT 1, id FROM permission WHERE code = 'RESIDENT_MANAGE';

-- Backfill: todo master ACTIVE atual ganha o grant (granted_by NULL = concessão do sistema).
INSERT INTO user_permission_grant (user_id, permission_id, granted_by_user_id)
SELECT u.id, p.id, NULL
FROM "user" u
CROSS JOIN permission p
WHERE p.code = 'RESIDENT_MANAGE'
  AND u.is_unit_master = true
  AND u.status = 'ACTIVE'
  AND u.deleted_at IS NULL;
