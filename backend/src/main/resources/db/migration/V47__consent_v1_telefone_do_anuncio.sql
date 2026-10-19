-- flyway:transactional=true
--
-- Corrige o termo 1.0.0: o anúncio mostra o telefone que o autor digitar nele (V46), nunca o
-- telefone da conta. Esclarecimento a favor do titular — não exige novo aceite.

UPDATE consent_document
SET body = replace(body,
    E'conta, pode vê-los. Num anúncio aparecem o **seu nome e o seu telefone** como contato do vendedor. ',
    E'conta, pode vê-los. Num anúncio aparecem o **seu nome** e, se você informar, um **telefone de ' ||
    E'contato** digitado no próprio anúncio. O telefone da sua conta **nunca** é exibido. ')
WHERE version = '1.0.0';
