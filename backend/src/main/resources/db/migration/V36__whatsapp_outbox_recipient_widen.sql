-- flyway:transactional=true

-- Avisos de atividade vão pro JID de um grupo (ex.: 120363409829888116@g.us, 24 chars),
-- que não cabia em varchar(20). Alargar é expand/contract-safe.
ALTER TABLE whatsapp_outbox ALTER COLUMN to_phone TYPE varchar(64);
