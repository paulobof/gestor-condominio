-- flyway:transactional=true
--
-- Corrige o termo 1.0.0: o cadastro não pede mais comprovante de residência (removido em V39),
-- então o termo não pode dizer que o coleta. Menos dados coletados — não exige novo aceite.

UPDATE consent_document
SET body = replace(replace(replace(body,
    E'- **Comprovante de residência** (PDF, JPG ou PNG), usado só para validar que você mora no condomínio.\n', ''),
    E'- O comprovante de residência **nunca** é exibido a outros moradores.\n', ''),
    E'- **Comprovante de residência:** descartado 180 dias após a aprovação do cadastro.\n', '')
WHERE version = '1.0.0';
