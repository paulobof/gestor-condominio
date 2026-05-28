-- flyway:transactional=true

-- Mesmo motivo da V11: User.consentAcceptedIp e mapeado como String e o Hibernate
-- (ddl-auto=validate) rejeita o tipo nativo `inet`. Convertemos para text.
-- "user" e palavra reservada -> sempre com aspas.
ALTER TABLE "user" ALTER COLUMN consent_accepted_ip TYPE text USING consent_accepted_ip::text;
