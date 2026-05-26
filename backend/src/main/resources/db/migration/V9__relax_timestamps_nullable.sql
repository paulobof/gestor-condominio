-- flyway:transactional=true
-- Permitir NULL em updated_at quando inserido pela primeira vez (default now() ainda cobre via INSERT que omite a coluna).
-- O motivo: Hibernate envia explicitamente NULL durante o INSERT mesmo com @DynamicInsert/insertable=false,
-- e PostgreSQL recusa NULL mesmo com DEFAULT now() já que NULL foi enviado explicitamente.

ALTER TABLE refresh_token ALTER COLUMN updated_at DROP NOT NULL;
ALTER TABLE refresh_token ALTER COLUMN created_at DROP NOT NULL;
