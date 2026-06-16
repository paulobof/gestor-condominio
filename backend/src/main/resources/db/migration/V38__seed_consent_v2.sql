-- flyway:transactional=true
--
-- Termo de privacidade v2.0.0: corrige a CONTROLADORA dos dados. A v1.0.0 (V7)
-- apresentava o condomínio como controlador, dando a (falsa) impressão de app
-- oficial. Este aplicativo é INDEPENDENTE, operado pela WIZOR TECNOLOGIA LTDA,
-- sem vínculo com a atual gestão do condomínio. `findLatest()` usa published_at
-- DESC, então a v2.0.0 (inserida depois) passa a ser o termo vigente.

INSERT INTO consent_document (version, body) VALUES (
    '2.0.0',
    E'# Política de Privacidade — Aplicativo de Moradores (HELBOR TRILOGY HOME)\n\n' ||
    E'**Aviso:** Este é um aplicativo **independente**, desenvolvido e operado pela ' ||
    E'**WIZOR TECH** (WIZOR TECNOLOGIA LTDA). **Não possui vínculo com a atual gestão do ' ||
    E'condomínio**, com a administradora ou com o síndico, e **não é um canal oficial** do ' ||
    E'Condomínio Edifício HELBOR TRILOGY HOME. O uso é voluntário.\n\n' ||
    E'**Controladora dos dados:** WIZOR TECNOLOGIA LTDA (nome fantasia WIZOR TECH) — ' ||
    E'CNPJ 47.228.018/0001-09 — contato@wizortech.com.br — https://wizortech.com.br\n\n' ||
    E'## Dados coletados\n' ||
    E'- Nome completo, e-mail, telefone, apartamento\n' ||
    E'- Comprovante de residência (PDF/JPG/PNG)\n' ||
    E'- Data de nascimento e gênero (opcionais)\n' ||
    E'- Histórico de acesso\n\n' ||
    E'## Finalidades\n' ||
    E'- Autenticação e gestão de moradores no aplicativo\n' ||
    E'- Comunicação operacional via WhatsApp (com seu consentimento)\n\n' ||
    E'## Direitos do titular (LGPD)\n' ||
    E'- Acesso, retificação, eliminação, portabilidade, anonimização\n' ||
    E'- Contato com a controladora em /privacidade ou contato@wizortech.com.br\n\n' ||
    E'## Operadores\n' ||
    E'- Dokploy (hospedagem), PostgreSQL, MinIO (storage), Bot WhatsApp\n\n' ||
    E'## Retenção\n' ||
    E'- Comprovante de residência: descartado 180 dias após aprovação\n' ||
    E'- Logs de acesso: 6 meses\n' ||
    E'- Conta inativa por 12 meses: anonimizada automaticamente'
);
