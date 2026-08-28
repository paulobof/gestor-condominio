-- flyway:transactional=true

-- Convidado e Proprietário saíram do produto (decisão do controlador dos dados, 2026-08-27).
-- Sai junto tudo que existia só para servi-los: posse de unidade e comprovante.
-- Ordem: vínculos → permissions → roles → contas órfãs → tabelas → colunas.

-- 1. Vínculos das roles removidas (user_role é M:N puro: hard delete permitido).
DELETE FROM user_role
WHERE role_id IN (SELECT id FROM role WHERE name IN ('GUEST', 'PROPRIETARIO'));

-- 2. Grants das roles e das permissions removidas (role_permission é M:N puro).
DELETE FROM role_permission
WHERE role_id IN (SELECT id FROM role WHERE name IN ('GUEST', 'PROPRIETARIO'))
   OR permission_id IN (SELECT id FROM permission
                        WHERE code IN ('GENERAL_AREAS_VIEW', 'CONTENT_CREATE',
                                       'RESIDENCE_PROOF_VIEW'));

-- 3. Grants individuais das permissions removidas (log/M:N: hard delete permitido).
DELETE FROM user_permission_grant
WHERE permission_id IN (SELECT id FROM permission
                        WHERE code IN ('GENERAL_AREAS_VIEW', 'CONTENT_CREATE',
                                       'RESIDENCE_PROOF_VIEW'));

DELETE FROM permission
WHERE code IN ('GENERAL_AREAS_VIEW', 'CONTENT_CREATE', 'RESIDENCE_PROOF_VIEW');

DELETE FROM role WHERE name IN ('GUEST', 'PROPRIETARIO');

-- 4. Proprietário puro ficou sem papel nenhum: desativa (NÃO apaga) para o síndico tratar.
--    Quem também é morador manteve RESIDENT e não é tocado aqui.
UPDATE "user"
SET status = 'DISABLED', updated_at = now()
WHERE status = 'ACTIVE'
  AND deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id = "user".id);

-- 5. Posse de unidade e log de acesso a comprovante.
DROP TABLE IF EXISTS unit_ownership;
DROP TABLE IF EXISTS proof_access_log;

-- 6. Colunas de comprovante no usuário.
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_object_key;
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_filename;
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_content_type;
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_uploaded_at;
ALTER TABLE "user" DROP COLUMN IF EXISTS proof_verified_at;
