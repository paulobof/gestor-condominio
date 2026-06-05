# Plano 3 — Classifieds + Indicações — Design

> Spec de design. Base: `docs/superpowers/specs/2026-05-24-gestor-condominio-design.md` (§3.2 entidades `classified*`, `recommendation*`, `tag`, `recommendation_opening_hours`; §4.3 endpoints; §5.7 componentes; §6.3 tela de indicação). Continuação natural do Plano 2C (out-of-scope dele: "templates de notificação para classifieds/recommendations/moderação vêm no plano 3").

**Data:** 2026-06-05.

---

## 1. Visão geral & escopo

Entregar as duas features de produto que faltam da spec mestra antes das telas institucionais (contacts/links/FAQ):

- **Classifieds** (anúncios entre moradores): CRUD, até 5 fotos, estados ACTIVE/SOLD/ARCHIVED, moderação.
- **Indicações** (recomendações de profissionais): CRUD, tags livres, até 5 fotos, horários de funcionamento, **fluxo de consentimento** quando o indicado é morador do condomínio, moderação.

**Fora de escopo** (planos futuros):
- `contact`, `link`, `faq` (telas institucionais) — plano 4.
- WhatsApp **inbound** (receber respostas) — continua só outbound.
- Busca full-text avançada / ranking por relevância — listagem usa filtros simples + sort determinístico.
- Notificações de classifieds (ex.: "seu anúncio expira") — só o consentimento de indicação dispara WhatsApp nesta fase.

**Decisões de produto desta fase** (tomadas no brainstorming 2026-06-05):
1. Entrega faseada: **um spec, dois planos sequenciais** — `3A Classifieds` → `3B Indicações`. Classifieds primeiro: mais simples e estabelece o padrão foto+CRUD+moderação que indicações reusa.
2. Consentimento do morador indicado: **in-app + nudge por WhatsApp** (template novo via outbox do 2C).
3. Tags: **criação livre** por qualquer morador (slug `citext`); `TAG_MANAGE` modera/deleta.

---

## 2. Arquitetura & princípios

`package-by-feature` (mantém o padrão das fases anteriores). Novos pacotes:

```
feature/classified/      (3A)
feature/tag/             (3B)
feature/recommendation/  (3B)
```

**Reuso (nada de reinventar):**

| Já existe | Uso no Plano 3 |
| --- | --- |
| `storage/FileStorage` (`upload`/`presignedGetUrl`/`delete`) + buckets `classifieds`/`recommendations` já provisionados (`MinioBootstrap`) | Upload/leitura de fotos |
| `storage/MagicBytesValidator.isAcceptedForPhoto` (jpg/png/webp) — **já implementado** | Validação magic-bytes server-side das fotos |
| `feature/whatsapp` — outbox + `WhatsAppOutboxService` + `WhatsAppRetryScheduler` + `WhatsAppMessageRenderer` + `PhoneNumberNormalizer` | Nudge de consentimento (template novo) |
| `@SQLDelete`/`@SQLRestriction` (Hibernate 6) | Soft delete |
| `@PreAuthorize("hasAuthority(...)")` + `PermissionCode` (`CLASSIFIED_MODERATE`, `RECOMMENDATION_MODERATE`, `TAG_MANAGE` já seedados) | Autorização |
| `LogSanitizer`, `MdcFilter` | Logs sem PII |

**Princípios obrigatórios** (CLAUDE.md): SOLID/KISS/STRIDE, domínio rico (transições de estado em métodos da entidade, não setter solto), TDD, soft delete, `@Transactional` só em service, upload pro MinIO **fora** da transação, eventos via `@TransactionalEventListener(AFTER_COMMIT) + @Async`, PT-BR na UI / inglês no código.

### 2.1 Decisão: upload de foto

Seguir o padrão já estabelecido no comprovante de residência: **multipart → backend**, que (1) valida magic-bytes + content-type + tamanho server-side, (2) sobe pro MinIO **fora da transação**, (3) persiste o `object_key`. Leitura sempre via **presigned GET** de TTL curto.

Rejeitada a alternativa "presigned PUT" (upload direto do browser pro MinIO): furaria o magic-bytes check server-side, que é **obrigatório** por CLAUDE.md. O frontend ainda comprime antes do envio (`browser-image-compression`) para respeitar o teto de 1MB.

Adicionar `presignedTtlPhotosSeconds` (default 300) em `MinioProperties` — hoje só existe TTL para comprovantes.

### 2.2 Feature flags

Cada feature atrás de flag (CLAUDE.md — WIP atrás de flag, default prod `false`):
- `app.feature.classifieds.enabled:false`
- `app.feature.recommendations.enabled:false`

Controllers checam a flag (ou registro condicional) para retornar 404 quando desligada. Liga-se em HML para validar e2e; em prod só após decisão registrada em issue.

---

## 3. Modelo de dados (migrations Flyway — expand, backward-compatible)

Cabeçalho `-- flyway:transactional=true`; PKs `gen_random_uuid()`; sem rename/remove no mesmo migration que adiciona; soft delete com `deleted_at timestamptz` exceto onde indicado.

### V15 — Classifieds (Plano 3A)

**`classified`** (soft delete):

| Coluna | Tipo | Notas |
| --- | --- | --- |
| id | uuid PK | `gen_random_uuid()` |
| title | varchar(120) | NOT NULL |
| description | text | |
| price | numeric(12,2) | nullable |
| status | varchar(20) | CHECK ('ACTIVE','SOLD','ARCHIVED') default 'ACTIVE' |
| author_user_id | uuid FK user | `ON DELETE RESTRICT` |
| created_at/updated_at/deleted_at/version | — | padrão das entidades |

**`classified_photo`** (soft delete):

| Coluna | Tipo | Notas |
| --- | --- | --- |
| id | uuid PK | |
| classified_id | uuid FK classified | `ON DELETE RESTRICT` (soft delete; cascade real proibido) |
| object_key | varchar(255) | bucket `classifieds` |
| content_type | varchar(80) | |
| ordering | int | |

`UNIQUE (classified_id, ordering) WHERE deleted_at IS NULL`. Index `(author_user_id)`, `(status) WHERE deleted_at IS NULL`. Limite **5 fotos** validado em service.

### V16 — Indicações + Tags (Plano 3B)

**`tag`** (soft delete): `id uuid PK`, `slug citext UNIQUE` (case-insensitive — extensão `citext` já criada na V1), `label varchar(80)`, `color varchar(20)`.

**`recommendation`** (soft delete) — conforme §3.2 da spec mestra:

| Coluna | Tipo | Notas |
| --- | --- | --- |
| id | uuid PK | |
| service_name | varchar(120) | NOT NULL |
| professional_name | varchar(120) | |
| phone | varchar(20) | |
| is_resident | boolean | default false |
| resident_user_id | uuid FK user | NOT NULL quando `is_resident` (CHECK) |
| address_line | varchar(255) | externo, ou unidade quando interno |
| price_range | varchar(40) | livre |
| rating | smallint | CHECK 1..5 |
| comment | text | |
| recommended_by_user_id | uuid FK user | autor |
| status | varchar(30) | CHECK ('ACTIVE','PENDING_RESIDENT_CONSENT','HIDDEN') default 'ACTIVE' |
| resident_consent_at | timestamptz | |

CHECK: `is_resident = false OR resident_user_id IS NOT NULL`.

**`recommendation_photo`** (soft delete): igual ao `classified_photo`, bucket `recommendations`, `UNIQUE (recommendation_id, ordering) WHERE deleted_at IS NULL`, limite 5 fotos / 1MB.

**`recommendation_tag`** (M:N puro, **sem soft delete**): PK `(recommendation_id, tag_id)`, `ON DELETE CASCADE`.

**`recommendation_opening_hours`** (**sem soft delete** — CASCADE; FK real, sem polimorfismo): `id`, `owner_id uuid FK recommendation ON DELETE CASCADE`, `day_of_week smallint CHECK 0..6`, `opens_at time` (null=fechado), `closes_at time`, `notes varchar(120)`. Index `(owner_id, day_of_week)`.

Ordenação default da listagem de indicações: `ORDER BY is_resident DESC, rating DESC NULLS LAST, created_at DESC`.

> Nota: a migration pode ser fatiada em V16/V17 na fase de plano se ficar grande; logicamente é um bloco aditivo.

---

## 4. Fase 3A — Classifieds

### 4.1 Domínio
- `Classified` **rico**: transições só por métodos — `markSold()`, `archive()`, `reactivate()` (valida estado origem; lança `ClassifiedException` em transição inválida). Lombok `@Getter`/`@Setter(PROTECTED)`/`@EqualsAndHashCode(of="id")`/`@ToString(onlyExplicitlyIncluded=true)` (proibido `@Data`).
- `ClassifiedPhoto` com `ordering`.

### 4.2 Service (TDD)
`ClassifiedService`:
- `create(authorId, dto)` — autor = self.
- `update(id, actor, dto)` / `delete(id, actor)` — permitido se `actor == author` **ou** `actor` tem `CLASSIFIED_MODERATE`. Regra autor-ou-moderador no service (não via `hasRole`).
- `addPhoto(id, actor, file)` — valida: ≤5 fotos ativas, `isAcceptedForPhoto`, magic-bytes batem com content-type declarado, ≤1MB; **upload fora da tx**.
- `removePhoto(id, photoId, actor)`.
- `photoUrl(id, photoId)` — presigned GET TTL curto.
- `list(filter, pageable)` / `getById(id)`.

### 4.3 Controller — `/api/classifieds` (spec §4.3)
```
GET    /api/classifieds                 any logado   (paginado; ?status= default ACTIVE)
GET    /api/classifieds/{id}            any logado
POST   /api/classifieds                 any (autor=self)
PUT    /api/classifieds/{id}            author | CLASSIFIED_MODERATE
DELETE /api/classifieds/{id}            author | CLASSIFIED_MODERATE
POST   /api/classifieds/{id}/photos     author | CLASSIFIED_MODERATE   (≤5, 1MB)
DELETE /api/classifieds/{id}/photos/{photoId}   author | CLASSIFIED_MODERATE
GET    /api/classifieds/{id}/photos/{photoId}/url   any logado
```
Endpoints "self-or-permission" usam `@PreAuthorize("isAuthenticated()")` + checagem no service; paths exclusivos de moderação não existem aqui (autor cobre o caso comum).

### 4.4 Frontend
`ClassifiedsListPage` (grid + filtro status), `ClassifiedDetailPage` (carrossel de fotos via presigned URL), `ClassifiedFormPage` (criar/editar, upload com `browser-image-compression`), `classifiedsApi.ts`, rotas `/classificados`, `/classificados/:id`, `/classificados/novo`, `/classificados/:id/editar`. Autor e moderador veem ações de editar/excluir/mudar status; mobile-first, touch ≥44px, WCAG AA.

---

## 5. Fase 3B — Indicações

### 5.1 Domínio
- `Recommendation` **rico**: `consentByResident()` (PENDING_RESIDENT_CONSENT → ACTIVE, set `resident_consent_at`), `declineByResident()` (soft delete própria), `hide()` (→ HIDDEN), invariante `is_resident ⇒ resident_user_id`.
- `Tag`, `RecommendationPhoto`, `RecommendationOpeningHours`.

### 5.2 Tags (`feature/tag`)
`TagService.getOrCreate(slug, label?)` — normaliza slug (`citext` resolve case-insensitive), reusa existente ou cria. `findForAutocomplete(query)`.
```
GET    /api/tags        any logado   (autocomplete; ?q=)
POST   /api/tags        any logado   (criação livre)
DELETE /api/tags/{id}   TAG_MANAGE
```
Na criação de indicação, `tagSlugs[]` resolve via `getOrCreate` dentro da transação do `RecommendationService`.

### 5.3 Fluxo de consentimento do morador indicado (`is_resident = true`)
1. `POST /api/recommendations` com `isResident=true`, `residentUserId`: cria com `status=PENDING_RESIDENT_CONSENT`.
2. `RecommendationService` publica `RecommendationConsentRequestedEvent`; `@TransactionalEventListener(AFTER_COMMIT) + @Async` enfileira WhatsApp **template `RECOMMENDATION_CONSENT`** (via `WhatsAppOutboxService`) pro telefone do morador indicado. Reusa `PhoneNumberNormalizer`. **Nudge** — a indicação também já aparece in-app.
3. **`GET /api/recommendations/pending-consent` (self)** — lista indicações onde `resident_user_id = me AND status = PENDING_RESIDENT_CONSENT`. *Endpoint além da spec original, aprovado no brainstorming — surface in-app.*
4. **`POST /api/recommendations/{id}/resident-consent`** body `{ approved: boolean }`, autorizado para o próprio morador indicado (self = `resident_user_id`) **ou** `RECOMMENDATION_MODERATE`:
   - `approved=true` → `consentByResident()` → ACTIVE + `resident_consent_at`.
   - `approved=false` → `declineByResident()` → soft delete. *Recusar = direito do titular de derrubar a própria indicação; além da spec (que só previa aprovar), aprovado no brainstorming.*

**Template `RECOMMENDATION_CONSENT`** (`WhatsAppTemplate` enum + `WhatsAppMessageRenderer`) — espera `{ greetingName, recommenderName, serviceName, link }` (link p/ página de pendências); campo ausente → `WhatsAppSendException` (outbox FAILED rastreável, não envia `{var}` cru), igual aos templates do 2C. Copy PT-BR a aprovar no plano.

### 5.4 Controller — `/api/recommendations` (spec §4.3)
```
GET    /api/recommendations                 any logado   (?tag=&residentOnly=&search=&page=; sort residents-first, rating desc)
GET    /api/recommendations/pending-consent self
GET    /api/recommendations/{id}            any logado
POST   /api/recommendations                 any (autor=self)   (isResident, residentUserId, addressLine, priceRange, tagSlugs[])
PUT    /api/recommendations/{id}            author | RECOMMENDATION_MODERATE
DELETE /api/recommendations/{id}            author | RECOMMENDATION_MODERATE
POST   /api/recommendations/{id}/photos     author | RECOMMENDATION_MODERATE   (≤5, 1MB)
DELETE /api/recommendations/{id}/photos/{photoId}   author | RECOMMENDATION_MODERATE
GET    /api/recommendations/{id}/photos/{photoId}/url   any logado
POST   /api/recommendations/{id}/resident-consent   residente indicado (self) | RECOMMENDATION_MODERATE
POST   /api/recommendations/{id}/hide       RECOMMENDATION_MODERATE
```

### 5.5 Frontend
`RecommendationsListPage` (filtros: tag, só moradores, busca), `RecommendationDetailPage` (fotos, horários, badge "mora aqui"), `RecommendationFormPage` (autocomplete de tags, toggle "é morador" → seletor de morador, upload de fotos, editor de horários com `date-fns-tz`/`America/Sao_Paulo`), banner/página de **pendências de consentimento**, `recommendationsApi.ts`, `tagsApi.ts`, rotas correspondentes. Mobile-first, WCAG AA.

---

## 6. Transversais

- **Soft delete** em `classified`, `classified_photo`, `tag`, `recommendation`, `recommendation_photo`. **Sem** soft delete em `recommendation_tag` (M:N puro) e `recommendation_opening_hours` (CASCADE) — per CLAUDE.md. Nunca `CascadeType.REMOVE`.
- **Upload:** `MagicBytesValidator.isAcceptedForPhoto` + detecção real de magic-bytes + teto 1MB + máx 5; upload fora da tx; presigned GET TTL curto (`presignedTtlPhotosSeconds`).
- **Segurança (STRIDE):** `@PreAuthorize` por permission; autor-ou-moderador no service; FKs `RESTRICT`; `@Version`; nunca PII em log (telefone do indicado, nomes) — `LogSanitizer`; MDC (`requestId`, `userId`, `unitId`).
- **Rate limit (opcional, a decidir no plano):** Bucket4j modesto no upload de fotos (ex. 20/min/user) — defesa contra abuso de storage.

---

## 7. Testes

- **TDD nos services** (`ClassifiedServiceTest`, `RecommendationServiceTest`, `TagServiceTest`) — incluindo a **máquina de estados do consentimento** (PENDING→ACTIVE / PENDING→declined-soft-deleted) e regras autor-ou-moderador.
- **Slices MockMvc** dos controllers (autorização: autor vs moderador vs terceiro).
- **Upload:** teste de `MagicBytesValidator` para imagem (já existe `isAcceptedForPhoto`; cobrir rejeição de mismatch e >1MB e >5 fotos).
- **Renderer:** teste do template `RECOMMENDATION_CONSENT` (substituição + campo ausente → exceção), no padrão dos testes do 2C.
- **e2e em HML antes de marcar qualquer task `[x]`** (per regra do projeto: validar e2e em HML, não só verde local).

---

## 8. Entrega (dois planos)

| Plano | Conteúdo | Migrations | PRs estimados |
| --- | --- | --- | --- |
| **3A — Classifieds** | `feature/classified` (entidades, service TDD, controller, fotos), frontend, e2e HML | V15 | ~3–4 (≤400 linhas cada) |
| **3B — Indicações** | `feature/tag` + `feature/recommendation` (entidades, service TDD, consentimento + WhatsApp template, horários, fotos, controller), frontend, e2e HML | V16 (±V17) | ~4–5 |

Cada plano: branch ≤2 dias, PR ≤400 linhas, trunk-based, feature flag para WIP. `writing-plans` detalha as tasks task-by-task.

---

## 9. Riscos & mitigações

| Risco | Mitigação |
| --- | --- |
| Foto com content-type forjado | Magic-bytes server-side (`detect` + `isAcceptedForPhoto`); rejeita mismatch antes do upload. |
| Indicação cria spam/abuso de morador alheio | Status PENDING_RESIDENT_CONSENT até o indicado aprovar; até lá não aparece como ACTIVE na listagem pública; recusa = soft delete pelo titular. |
| Telefone do indicado vaza em log | Nunca logar telefone/nome; `LogSanitizer` + `redactPhone` no client WhatsApp (padrão 2C). |
| Tag-bombing (criação livre) | `TAG_MANAGE` deleta/funde; slug `citext` evita duplicata por caixa. |
| Migration grande (3B) | Fatiar V16/V17; sempre aditivo, backward-compatible. |
| Órfãos por FK virtual | `recommendation_opening_hours` usa FK real `ON DELETE CASCADE` (decisão herdada do database-reviewer na spec mestra). |

---

## 10. Próximo passo

`superpowers:writing-plans` → plano de implementação do **3A (Classifieds)** primeiro; 3B depois, após 3A validado e2e em HML.
