-- flyway:transactional=true
--
-- Telefone de contato do anúncio, informado por quem anuncia. O telefone da CONTA do morador nunca
-- é exposto: até aqui classificados e vagas devolviam o telefone do autor. Anúncios existentes
-- ficam sem telefone (NÃO copiar o da conta, seria o mesmo vazamento) até o autor editar.

ALTER TABLE classified ADD COLUMN contact_phone varchar(20);
ALTER TABLE parking_rental ADD COLUMN contact_phone varchar(20);
