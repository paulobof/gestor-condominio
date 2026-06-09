# Design: indicações sem aprovação do morador

**Data:** 2026-06-09
**Status:** aprovado
**Reverte:** decisão LGPD de consentimento do residente do spec `2026-05-24-gestor-condominio-design.md` (linha ~1140).

## Contexto e decisão

O design original previa, por precaução LGPD, que indicar um **morador** como prestador (`is_resident=true`) deixasse a indicação em `PENDING_RESIDENT_CONSENT` até esse morador aprovar (via link enviado por WhatsApp). Indicações de negócios externos já entravam `ACTIVE` direto.

**Decisão do produto (Paulo, 2026-06-09):** a indicação **não precisa de aprovação**. Toda indicação — externa ou de morador — entra `ACTIVE` imediatamente. O conceito de "indicar morador" (`is_resident`) e o filtro "só moradores" **permanecem** como informação/navegação; some apenas o gate de consentimento.

## Escopo da remoção

### Backend
- `RecommendationStatus`: remove `PENDING_RESIDENT_CONSENT` (restam `ACTIVE`, `HIDDEN`).
- `RecommendationService.create`: sempre `ACTIVE`; remove publicação de evento de consentimento. Remove `pendingConsentFor()` e `residentConsent()`. Remove a guarda de visibilidade específica de `PENDING` (mantém a de `HIDDEN`). Remove `ApplicationEventPublisher` se ficar sem uso.
- `RecommendationController`: remove `GET /pending-consent` e `POST /{id}/resident-consent`.
- Deleta `event/RecommendationConsentRequestedEvent` e `whatsapp/RecommendationConsentEventListener`. Remove o template de consentimento do `WhatsAppMessageRenderer`.
- `Recommendation` entity: remove `resident_consent_at`, `isPendingConsent()`, `consentByResident()`. Mantém `is_resident`/`resident_user_id`.
- DTOs: mantém `is_resident`; remove campos de consentimento de `RecommendationView`.
- Config: remove `app.recommendation.consent-base-url` (application.yml + deploy/env-example).

### Banco — migration `V21` (contract puro, sem add)
1. `UPDATE recommendation SET status='ACTIVE' WHERE status='PENDING_RESIDENT_CONSENT';`
2. Recria o CHECK de status para `IN ('ACTIVE','HIDDEN')`.
3. `ALTER TABLE recommendation DROP COLUMN resident_consent_at;`

Flyway roda no boot antes de servir, então não sobra linha `PENDING` para o enum novo ler.

### Frontend
- Deleta `features/recommendations/pages/PendingConsentPage.tsx` (+ teste).
- `router.tsx`: remove rota `/indicacoes/pendentes`.
- `Sidebar.tsx`: remove item "Consentimentos" (+ ajustar `Sidebar.test`).
- `recommendationsApi`: remove `listPendingConsent`/`respondConsent` (+ teste).
- `App.tsx`/`App.test`: remove referências.
- Form de indicação: mantém o seletor "é morador?" (informativo); remove texto de "enviado para aprovação".

## Testes
TDD: ajustar `RecommendationServiceTest`, `RecommendationControllerWebTest`, `WhatsAppMessageRendererRecommendationTest`, `Sidebar.test`, `App.test`, `recommendationsApi.test`. Cobrir explicitamente: **indicação de morador entra `ACTIVE` direto** e os endpoints de consentimento deixam de existir.

## Fora de escopo
Moderação (`HIDDEN`, `RECOMMENDATION_MODERATE`) permanece intacta.
