# Backlog de evolução — gestor-condominio

> Ideias de evolução pausadas para retomar **após** validar/liberar o que já existe em produção
> (decisão de 2026-06-06). Não está em desenvolvimento ativo.

## Estado atual (2026-06-06)
Entregue e na `main`: Fundação + 2A/2B/2C (auth, cadastro, LGPD, WhatsApp), **3A Classificados**,
**3B Indicações**, **4 Mural de avisos (MVP)**. Cobertura de testes ampla (backend ~204, frontend ~65),
incluindo contrato web de todos os controllers.

## Pendências conhecidas
- **Validação e2e do 3B em HML**: falta ligar a flag no Dokploy backend-hml
  (`APP_FEATURE_RECOMMENDATIONS_ENABLED=true` + `APP_RECOMMENDATION_CONSENT_BASE_URL=https://hml.app.helbor.paulobof.com.br/indicacoes/pendentes`),
  redeploy e rodar o checklist (Plano 3B Task 12 Step 3). Gateway WhatsApp **Bot-Robo já conectado**.
- **Descoberta/navegação**: o home (`App.tsx`) não linka as features (classificados/indicações/avisos
  são acessados por URL direta). Falta um menu/cards de navegação.

## Próximas features (roadmap, spec §71)
1. **Mural 4b — broadcast WhatsApp**: ao publicar aviso, notificar moradores opt-in (telefone
   verificado), via outbox/retry do 2C, com rate-limit e cuidado de não disparar para todos sem
   consentimento. (Ficou de fora do MVP do Mural de propósito.)
2. **Reservas de áreas comuns**: churrasqueira/salão; conflito de horário, antecedência, limites por
   unidade; calendário. (Concorrência é o ponto difícil.)
3. **Ocorrências/chamados**: morador abre chamado; síndico responde/encaminha/resolve; status e histórico.
4. **Boletos/financeiro**: emissão/consulta, status de pagamento. Mais sensível (integração financeira).
5. **App mobile**.

## Notas técnicas para retomar
- Padrão de feature já consolidado: package-by-feature, soft delete `@SQLDelete`/`@SQLRestriction`,
  feature flag `@ConditionalOnProperty(app.feature.<nome>.enabled)`, `@PreAuthorize` por permission,
  controller fino + service `@Transactional`, DTOs records, testes de contrato web com `MockAuth`.
- Migrations: próxima livre = **V19** (V18 = announcements). Próxima permission id livre = **16**.
