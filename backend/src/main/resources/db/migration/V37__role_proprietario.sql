-- flyway:transactional=true

-- Papel Proprietário (read-only). Concedido na aprovação da posse (UnitOwnership), não pela
-- tela de acessos (assignable=false). Permissão: apenas GENERAL_AREAS_VIEW (ver portal).
INSERT INTO role (id, name, label, max_holders, assignable)
VALUES (9, 'PROPRIETARIO', 'Proprietário', NULL, false);

INSERT INTO role_permission (role_id, permission_id)
SELECT 9, id FROM permission WHERE code = 'GENERAL_AREAS_VIEW';
