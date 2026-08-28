-- flyway:transactional=true

-- Convidado e Proprietário saíram do produto (decisão do controlador dos dados, 2026-08-27).
-- Sai junto tudo que existia só para servi-los: posse de unidade e comprovante.
-- Ordem: contas órfãs → vínculos → permissions → roles → tabelas → colunas.

-- 1. Proprietário puro (só tem os papéis que estão saindo) fica sem papel nenhum: desativa
--    (NÃO apaga) para o síndico tratar caso a caso. Roda ANTES de qualquer DELETE, enquanto
--    user_role ainda descreve a verdade — e exige que a pessoa TENHA um dos papéis removidos,
--    para nunca alcançar quem está sem papel por outro motivo (ex.: síndico no meio de uma
--    troca de papéis pela tela de acessos). Quem também é morador mantém RESIDENT e não é tocado.
UPDATE "user" u
SET status = 'DISABLED', updated_at = now()
WHERE u.status = 'ACTIVE'
  AND u.deleted_at IS NULL
  AND EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id
              WHERE ur.user_id = u.id AND r.name IN ('GUEST', 'PROPRIETARIO'))
  AND NOT EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id
                  WHERE ur.user_id = u.id AND r.name NOT IN ('GUEST', 'PROPRIETARIO'));

-- 2. Vínculos das roles removidas (user_role é M:N puro: hard delete permitido).
DELETE FROM user_role
WHERE role_id IN (SELECT id FROM role WHERE name IN ('GUEST', 'PROPRIETARIO'));

-- 3. Grants das roles e das permissions removidas (role_permission é M:N puro).
DELETE FROM role_permission
WHERE role_id IN (SELECT id FROM role WHERE name IN ('GUEST', 'PROPRIETARIO'))
   OR permission_id IN (SELECT id FROM permission
                        WHERE code IN ('GENERAL_AREAS_VIEW', 'CONTENT_CREATE',
                                       'RESIDENCE_PROOF_VIEW'));

-- 4. Grants individuais das permissions removidas (log/M:N: hard delete permitido).
DELETE FROM user_permission_grant
WHERE permission_id IN (SELECT id FROM permission
                        WHERE code IN ('GENERAL_AREAS_VIEW', 'CONTENT_CREATE',
                                       'RESIDENCE_PROOF_VIEW'));

DELETE FROM permission
WHERE code IN ('GENERAL_AREAS_VIEW', 'CONTENT_CREATE', 'RESIDENCE_PROOF_VIEW');

DELETE FROM role WHERE name IN ('GUEST', 'PROPRIETARIO');

-- 5. Posse de unidade e log de acesso a comprovante.
DROP TABLE IF EXISTS unit_ownership;
DROP TABLE IF EXISTS proof_access_log;

-- 6. Colunas de comprovante no usuário.
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_object_key;
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_filename;
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_content_type;
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_uploaded_at;
ALTER TABLE "user" DROP COLUMN IF EXISTS proof_verified_at;
