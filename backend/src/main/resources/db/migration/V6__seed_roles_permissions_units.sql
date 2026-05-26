-- flyway:transactional=true

INSERT INTO role (id, name, label, max_holders) VALUES
    (1, 'MANAGER',  'Síndico',       1),
    (2, 'COUNCIL',  'Conselheiro',   3),
    (3, 'STAFF',    'Administração', 5),
    (4, 'RESIDENT', 'Morador',       NULL),
    (5, 'DOORMAN',  'Porteiro',      NULL);

INSERT INTO permission (id, code, label) VALUES
    (1,  'USER_VIEW',                  'Visualizar usuários'),
    (2,  'USER_MANAGE',                'Gerenciar usuários'),
    (3,  'REGISTRATION_VIEW',          'Visualizar cadastros pendentes'),
    (4,  'REGISTRATION_APPROVE',       'Aprovar/rejeitar cadastros'),
    (5,  'RESIDENCE_PROOF_VIEW',       'Visualizar comprovantes de residência'),
    (6,  'CONTACT_MANAGE',             'Gerenciar telefones úteis'),
    (7,  'LINK_MANAGE',                'Gerenciar links úteis'),
    (8,  'FAQ_MANAGE',                 'Gerenciar FAQ'),
    (9,  'TAG_MANAGE',                 'Gerenciar tags'),
    (10, 'RECOMMENDATION_MODERATE',    'Moderar indicações'),
    (11, 'CLASSIFIED_MODERATE',        'Moderar classificados'),
    (12, 'ROLE_ASSIGN',                'Atribuir/remover roles'),
    (13, 'PERMISSION_GRANT',           'Conceder/revogar permissões'),
    (14, 'AUDIT_VIEW',                 'Visualizar trilhas de auditoria');

INSERT INTO role_permission (role_id, permission_id) SELECT 1, id FROM permission;

INSERT INTO role_permission (role_id, permission_id) VALUES
    (2, 1), (2, 3), (2, 4), (2, 5), (2, 8), (2, 10), (2, 11);

INSERT INTO role_permission (role_id, permission_id) VALUES (5, 1);

INSERT INTO unit (tower, floor, "position", code)
SELECT
    t.tower, f.floor, p.pos,
    f.floor || lpad(p.pos::text, 2, '0') || t.tower
FROM (VALUES ('A'), ('B'), ('C')) AS t(tower)
CROSS JOIN generate_series(4, 32) AS f(floor)
CROSS JOIN generate_series(1, 6) AS p(pos);
