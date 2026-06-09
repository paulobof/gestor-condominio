-- flyway:transactional=true

-- Nova role "Editor do Mural": só ANNOUNCEMENT_MANAGE, gerível pela tela de acessos.
INSERT INTO role (id, name, label, max_holders, assignable)
VALUES (6, 'MURAL_EDITOR', 'Editor do Mural', NULL, true);

INSERT INTO role_permission (role_id, permission_id)
SELECT 6, id FROM permission WHERE code = 'ANNOUNCEMENT_MANAGE';
