-- flyway:transactional=true

-- As entidades ProofAccessLog / SensitiveAccessLog mapeiam o IP como String.
-- Com ddl-auto=validate + globally_quoted_identifiers, o Hibernate espera VARCHAR
-- e rejeita o tipo nativo `inet` (Types#OTHER), impedindo o startup da aplicacao.
-- Convertemos para `text` (mesmo padrao ja usado em rejection_reason), que o JDBC
-- reporta como VARCHAR e valida corretamente contra o campo String.
ALTER TABLE proof_access_log ALTER COLUMN ip TYPE text USING ip::text;
ALTER TABLE sensitive_access_log ALTER COLUMN client_ip TYPE text USING client_ip::text;
