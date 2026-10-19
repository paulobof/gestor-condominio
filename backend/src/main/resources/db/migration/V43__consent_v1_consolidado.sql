-- flyway:transactional=true
--
-- Termo de privacidade consolidado em 1.0.0: o aplicativo ainda não foi lançado, então não faz
-- sentido existir uma "v2". O texto vigente (controladora WIZOR, app independente) passa a ser a
-- versão 1.0.0, reescrito em Markdown bem formado e cobrindo o que o app faz hoje (classificados e
-- indicações abertos a visitantes, reclamações com fotos). A v2.0.0 é removida e quem a aceitou
-- passa a constar como aceite da 1.0.0 — mesmo conteúdo de controladora e finalidades.

UPDATE consent_document
SET published_at = now(),
    body =
    E'# Política de Privacidade\n\n' ||
    E'**Aplicativo de Moradores — HELBOR TRILOGY HOME**\n\n' ||
    E'Versão 1.0.0\n\n' ||
    E'## 1. Quem somos\n\n' ||
    E'Este é um aplicativo **independente**, desenvolvido e operado pela **WIZOR TECNOLOGIA LTDA** ' ||
    E'(nome fantasia **WIZOR TECH**). Ele **não possui vínculo** com a gestão do condomínio, com a ' ||
    E'administradora ou com o síndico, e **não é um canal oficial** do Condomínio Edifício HELBOR ' ||
    E'TRILOGY HOME. O uso é voluntário.\n\n' ||
    E'**Controladora dos dados:** WIZOR TECNOLOGIA LTDA — CNPJ 47.228.018/0001-09\n\n' ||
    E'**Contato:** contato@wizortech.com.br — https://wizortech.com.br\n\n' ||
    E'## 2. Dados que coletamos\n\n' ||
    E'- **Cadastro:** nome completo, e-mail, telefone e apartamento.\n' ||
    E'- **Comprovante de residência** (PDF, JPG ou PNG), usado só para validar que você mora no condomínio.\n' ||
    E'- **Opcionais:** data de nascimento e gênero.\n' ||
    E'- **Conteúdo que você publica:** anúncios de classificados (com fotos), indicações e avaliações de ' ||
    E'profissionais, e reclamações sobre o condomínio (com fotos).\n' ||
    E'- **Registros de acesso:** IP, data e hora e ações realizadas, para segurança.\n\n' ||
    E'## 3. Para que usamos\n\n' ||
    E'- Criar e manter sua conta e confirmar que você é morador.\n' ||
    E'- Exibir o conteúdo que você publica para os demais usuários.\n' ||
    E'- Enviar mensagens operacionais pelo **WhatsApp**, como recuperação de senha e avisos — somente ' ||
    E'com o seu consentimento, que pode ser revogado a qualquer momento.\n' ||
    E'- Garantir a segurança do aplicativo e apurar uso indevido.\n\n' ||
    E'Não vendemos seus dados e não os usamos para publicidade.\n\n' ||
    E'## 4. O que fica visível para outras pessoas\n\n' ||
    E'- **Classificados e indicações são públicos:** qualquer pessoa que acesse o aplicativo, mesmo sem ' ||
    E'conta, pode vê-los. Num anúncio aparecem o **seu nome e o seu telefone** como contato do vendedor. ' ||
    E'Numa indicação aparecem os dados do profissional que você informar.\n' ||
    E'- **Reclamações** são vistas apenas por moradores da sua unidade e pelos responsáveis por ' ||
    E'tratá-las no aplicativo.\n' ||
    E'- O comprovante de residência **nunca** é exibido a outros moradores.\n\n' ||
    E'## 5. Com quem compartilhamos\n\n' ||
    E'Apenas com os fornecedores necessários para o aplicativo funcionar, que tratam os dados em nosso nome:\n\n' ||
    E'- **Hospedagem:** servidor gerenciado via Dokploy, com banco de dados PostgreSQL e armazenamento de arquivos MinIO.\n' ||
    E'- **WhatsApp:** gateway de envio de mensagens.\n\n' ||
    E'## 6. Por quanto tempo guardamos\n\n' ||
    E'- **Comprovante de residência:** descartado 180 dias após a aprovação do cadastro.\n' ||
    E'- **Registros de acesso:** 6 meses.\n' ||
    E'- **Conta sem uso por 12 meses:** anonimizada.\n' ||
    E'- **Demais dados:** enquanto sua conta existir.\n\n' ||
    E'## 7. Seus direitos (LGPD)\n\n' ||
    E'Você pode pedir **acesso**, **correção**, **portabilidade**, **anonimização** ou **eliminação** dos ' ||
    E'seus dados, e revogar consentimentos. Faça isso na página **Privacidade** do aplicativo ou pelo ' ||
    E'e-mail contato@wizortech.com.br.\n\n' ||
    E'## 8. Alterações\n\n' ||
    E'Se esta política mudar, a nova versão será publicada no aplicativo e o seu aceite será pedido novamente.'
WHERE version = '1.0.0';

UPDATE "user" SET consent_document_version = '1.0.0' WHERE consent_document_version = '2.0.0';

DELETE FROM consent_document WHERE version = '2.0.0';
