-- flyway:transactional=true
--
-- Último acesso do usuário (login ou renovação de sessão). Base da anonimização automática de
-- conta sem uso por 12 meses prometida no termo de privacidade. Contas que já existem começam a
-- contar a partir de agora, para ninguém ser anonimizado por falta de histórico.

ALTER TABLE "user" ADD COLUMN last_login_at timestamptz;

UPDATE "user" SET last_login_at = now() WHERE last_login_at IS NULL;
