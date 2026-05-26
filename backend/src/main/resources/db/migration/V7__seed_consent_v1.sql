-- flyway:transactional=true

INSERT INTO consent_document (version, body) VALUES (
    '1.0.0',
    E'# Política de Privacidade — HELBOR TRILOGY HOME\n\n' ||
    E'**Controlador:** Condomínio Edifício HELBOR TRILOGY HOME.\n\n' ||
    E'## Dados coletados\n' ||
    E'- Nome completo, e-mail, telefone, apartamento\n' ||
    E'- Comprovante de residência (PDF/JPG/PNG)\n' ||
    E'- Data de nascimento e gênero (opcionais)\n' ||
    E'- Histórico de acesso\n\n' ||
    E'## Finalidades\n' ||
    E'- Autenticação e gestão de moradores\n' ||
    E'- Comunicação operacional via WhatsApp (com seu consentimento)\n' ||
    E'- Cumprimento de obrigações da convenção condominial (Lei 4.591/64)\n\n' ||
    E'## Direitos do titular (LGPD)\n' ||
    E'- Acesso, retificação, eliminação, portabilidade, anonimização\n' ||
    E'- Contato com o Encarregado em /privacidade\n\n' ||
    E'## Operadores\n' ||
    E'- Dokploy (hospedagem), PostgreSQL, MinIO (storage), Bot WhatsApp\n\n' ||
    E'## Retenção\n' ||
    E'- Comprovante de residência: descartado 180 dias após aprovação\n' ||
    E'- Logs de acesso: 6 meses\n' ||
    E'- Conta inativa por 12 meses: anonimizada automaticamente'
);
