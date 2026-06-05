-- flyway:transactional=true

-- Mesmo motivo da V11/V12: PasswordResetToken.createdIp e mapeado como String
-- (@Column(columnDefinition="text")), mas a coluna foi criada como `inet` na V4.
-- No startup o Hibernate (ddl-auto=validate) confia no columnDefinition e nao checa o
-- tipo real, entao a app sobe; porem o INSERT em runtime falha porque o Postgres nao
-- faz cast implicito varchar->inet ("column created_ip is of type inet but expression
-- is of type character varying"). Convertemos para text, alinhado a convencao do projeto
-- (CLAUDE.md: tipos nativos como inet/citext mapeados como String -> usar text).
ALTER TABLE password_reset_token ALTER COLUMN created_ip TYPE text USING created_ip::text;
