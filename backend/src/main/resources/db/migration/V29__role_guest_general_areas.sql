-- flyway:transactional=true

-- Role nova "Convidado": sem unidade, ACTIVE direto, não atribuível pela tela de acessos.
INSERT INTO role (id, name, label, max_holders, assignable)
VALUES (7, 'GUEST', 'Convidado', NULL, false);

-- Permission para ver áreas gerais (Avisos/Informações/FAQ).
-- Permission para criar conteúdo (indicações/classificados).
INSERT INTO permission (id, code, label) VALUES
    (18, 'GENERAL_AREAS_VIEW', 'Ver avisos, informações e FAQ'),
    (19, 'CONTENT_CREATE',     'Criar indicações e classificados');

-- Concede AMBAS a todas as roles EXCETO GUEST (aditivo: ninguém perde acesso).
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE p.code IN ('GENERAL_AREAS_VIEW', 'CONTENT_CREATE')
  AND r.name <> 'GUEST';
