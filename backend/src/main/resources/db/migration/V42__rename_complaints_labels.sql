-- flyway:transactional=true

-- A feature "Chamados" passou a se chamar "Reclamações" na interface. Só os rótulos exibidos
-- mudam: role.name (COMPLAINT_MANAGER) e permission.code continuam iguais (usados pelo código).
UPDATE role
   SET label = 'Gestor de Reclamações'
 WHERE name = 'COMPLAINT_MANAGER';

UPDATE permission
   SET label = 'Gerenciar reclamações'
 WHERE code = 'COMPLAINT_MANAGE';

UPDATE permission
   SET label = 'Gerenciar catálogo de reclamações'
 WHERE code = 'COMPLAINT_CATALOG_MANAGE';
