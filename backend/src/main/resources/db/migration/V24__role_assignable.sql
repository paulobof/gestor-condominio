-- flyway:transactional=true

-- Marca quais roles podem ser atribuídas/removidas pela tela "Gerenciar acessos".
-- Síndico (MANAGER, cap 1) e Morador (RESIDENT, automática no cadastro) ficam fora (assignable=false).
ALTER TABLE role ADD COLUMN assignable boolean NOT NULL DEFAULT false;

UPDATE role SET assignable = true WHERE name IN ('COUNCIL', 'STAFF', 'DOORMAN');
