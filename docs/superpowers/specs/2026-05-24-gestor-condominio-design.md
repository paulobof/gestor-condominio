# Gestor de Condomínio — HELBOR TRILOGY HOME — Design

**Data:** 2026-05-24 (rev. 2026-05-25 — review por agentes especializados + identidade visual + WhatsApp + FAQ + horários + indicações expandidas)
**Status:** Design completo aguardando aprovação final do usuário.
**Repositório:** `git@github.com:paulobof/gestor-condominio.git`
**Condomínio:** HELBOR TRILOGY HOME — 3 torres (A/B/C), andares 4-32, 6 unidades/andar (522 unidades).

---

## 1. Visão geral

Sistema web responsivo (mobile-first) para gestão interna do condomínio HELBOR TRILOGY HOME.

**Estrutura física:**
- 3 torres A, B, C; andares 4-32 (29); 6 posições por andar.
- Código de unidade `{andar}{posição:02}{torre}`. Ex.: `702C`, `1503A`.
- 522 unidades pré-cadastradas via Flyway seed.

**Acesso por unidade:**
- Cada unidade tem 1 **usuário master**. Ele faz auto-cadastro público, anexa **comprovante de residência** e aguarda aprovação humana. Aprovado → ACTIVE.
- Sub-usuários (cônjuge, filhos, dependentes) **não se auto-cadastram**. O master logado cria via endpoint autenticado, sem comprovante (atestado pelo master). Ficam ACTIVE direto.
- **Roles** disponíveis:
  - `MANAGER` (síndico) — 1 pessoa, controle total.
  - `COUNCIL` (conselheiro) — 3 pessoas, **são moradores** (acumulam `COUNCIL` + `RESIDENT`); poderes ampliados (ver/aprovar cadastros, moderar conteúdo).
  - `STAFF` (administração) — até 5 pessoas, **podem não ser moradores**; cada uma com permissões granulares específicas (financeiro, secretaria, manutenção etc.) — o conjunto de permissões é atribuído individualmente.
  - `RESIDENT` (morador) — papel padrão; cobre master e sub-usuários de unidade.
  - `DOORMAN` (porteiro) — funcional, sem unidade.
- Roles são acumuláveis (um usuário pode ter várias).
- Autorização efetiva é **por permission** (granular), não por role. Permissions são derivadas das roles do usuário + concessões individuais (`user_permission_grant`). Ver seção 4.5.

**Comunicação outbound — WhatsApp only:**
- Toda comunicação externa (recuperação de senha, futuros avisos) sai via **WhatsApp** através de bot do próprio condomínio.
- Sistema **nunca** envia e-mails. E-mail é apenas identificador de login.

**Stack:**
- Backend: Java 21 + Spring Boot 3 + Lombok + Spring Security (JWT) + Spring Data JPA + Flyway + Bucket4j + Micrometer + Resilience4j (calls ao bot WhatsApp).
- Banco: PostgreSQL 16+.
- Storage de arquivos: MinIO (S3-compatible).
- Frontend: Vite + React 18 + TypeScript + Tailwind CSS + shadcn/ui + Fontes Outfit + Work Sans.
- Compressão de imagens: `browser-image-compression` no frontend antes do upload (≤1MB).
- Deploy: Dokploy (panel.paulobof.com.br).
- Build: Maven (backend), npm (frontend).
- Pre-commit: Husky + lint-staged + commitlint + ESLint/Prettier + Spotless.

**Princípios obrigatórios:** SOLID, KISS, STRIDE, POO (domínio rico).

**Modelo de desenvolvimento: Trunk-Based Development (TBD).**
- Branch única de longa vida: `main`.
- Feature branches são curtas (≤1-2 dias); merges frequentes em `main` via PR.
- `main` deve estar sempre deployável (CI verde obrigatório).
- Work-in-progress é protegido por **feature flags** (env vars binárias) — código fica no `main`, comportamento desligado em prod até estar pronto.
- Migrations são **backward-compatible** (padrão expand/contract): adicione coluna → backfill → mude o código → remova coluna obsoleta em release posterior. Nunca quebra a versão anterior do app rodando.
- Pre-push hook é a primeira linha; CI no PR é segunda; HML auto-deploy é terceira.

**Soft delete obrigatório** em todas as tabelas (NUNCA hard delete), exceto `user_role`, `opening_hours`, `recommendation_tag` (M:N e value-objects técnicos com CASCADE).

**Idioma:** código em inglês, UI em português brasileiro.

**Escopo MVP:**
1. Auto-cadastro de master com aprovação + comprovante.
2. Cadastro de membros da unidade pelo master.
3. Autenticação JWT + refresh em cookie HttpOnly; senha bcrypt+pepper; **reset via WhatsApp**.
4. Gestão de usuários, unidades, roles.
5. Classificados com fotos.
6. Telefones úteis **com horário de funcionamento**.
7. Links úteis **com horário de funcionamento** (quando aplicável).
8. Recomendações/indicações: morador-vs-externo, até 5 fotos, endereço, tags, ordenação privilegiando moradores.
9. FAQ — perguntas frequentes.
10. Conformidade LGPD baseline.

**Roadmap futuro (out-of-scope):** sorteio de vagas, reservas de áreas comuns, ocorrências/chamados, boletos, mural de avisos, app mobile.

---

## 2. Arquitetura

### 2.1 Estrutura de pastas (monorepo)

```
gestor-condominio/
├── backend/                              Spring Boot 3 + Java 21
│   └── src/main/java/br/com/condominio/
│       ├── GestorCondominioApplication.java
│       ├── config/                       Security, MinIO, OpenAPI, CORS, Bucket4j,
│       │                                 JpaAuditing, Observability, WhatsAppClient
│       ├── shared/                       GlobalExceptionHandler, AuditorAwareImpl,
│       │                                 MdcFilter, base DTOs, utils
│       ├── storage/                      FileStorage (interface) + MinioFileStorage
│       ├── whatsapp/                     WhatsAppNotificationClient (Resilience4j)
│       └── feature/
│           ├── auth/                     login, refresh, register-master,
│           │                             password-reset (via WhatsApp)
│           ├── user/                     CRUD usuários, gestão de roles
│           ├── unit/                     unidades, membros da unidade
│           ├── registration/             moderação de cadastros
│           ├── privacy/                  consent, export, anonymize (LGPD)
│           ├── classified/               classificados + fotos
│           ├── contact/                  telefones úteis + horários
│           ├── link/                     links úteis + horários (opcional)
│           ├── recommendation/           indicações + fotos + tags + endereço
│           ├── tag/                      tags reutilizáveis
│           └── faq/                      perguntas frequentes
├── frontend/                             Vite + React + TS
│   └── src/
│       ├── main.tsx, App.tsx, router.tsx
│       ├── lib/                          api, auth, image-compression, utils
│       ├── design-system/                tokens (cores, spacing, type), theme
│       ├── components/
│       │   ├── ui/                       shadcn (button, input, dialog, etc.)
│       │   ├── Layout.tsx, ProtectedRoute.tsx, RoleGate.tsx
│       │   ├── OpeningHoursDisplay.tsx, OpeningHoursEditor.tsx, TagPicker.tsx
│       └── features/<name>/              pages + components + hooks + api
├── deploy/                               configs Dokploy
├── docs/superpowers/specs/               este arquivo
├── .husky/                               pre-commit, commit-msg, pre-push
├── package.json, commitlint.config.cjs, .lintstagedrc.json, .editorconfig
├── docker-compose.dev.yml                postgres + minio local
├── CLAUDE.md                             convenções
└── README.md
```

### 2.2 Fluxo de requisição

```
Browser → Nginx (SPA) → /api/* → Backend Spring Boot
                                    ├── Postgres (JPA)
                                    ├── MinIO (S3 SDK)
                                    └── WhatsApp bot webhook (outbound, async)
```

### 2.3 Padrões transversais (obrigatórios, replicados no `CLAUDE.md`)

- **Transações**: `@Transactional` apenas em service. Upload MinIO **fora** do bloco transacional.
- **Auditoria automática**: `@EntityListeners(AuditingEntityListener.class)` + `AuditorAware<UUID>` que retorna `Optional.empty()` em contextos públicos (cadastro do master); evita NPE em `@CreatedBy`.
- **Optimistic locking**: `@Version Long version` em todas entidades mutáveis.
- **Soft delete (Hibernate 6 / Spring Boot 3.x)**: usar `@SQLDelete` + `@SQLRestriction` (substitui `@Where` deprecado). Limitações documentadas no `CLAUDE.md`:
  - `@SQLRestriction` **não** filtra queries nativas (`@Query(nativeQuery=true)`) nem `JdbcTemplate` — incluir `WHERE deleted_at IS NULL` manualmente.
  - **Nunca** usar `CascadeType.REMOVE` — bypass-a `@SQLDelete` e executa DELETE real. Soft delete em cascata é manual no service.
- **Eventos de domínio**: `@TransactionalEventListener(phase=AFTER_COMMIT)` + `@Async` para side effects que devem rodar após sucesso do commit. Listener publicado por método de domínio retornando lista de eventos coletada pelo service.
- **Lombok em entidades JPA** (regra obrigatória):
  - **Proibido `@Data`** — gera `@EqualsAndHashCode` com todos campos, causando `LazyInitializationException` e loops em relações bidirecionais.
  - Usar `@Getter @Setter(AccessLevel.PROTECTED)` + `@EqualsAndHashCode(of="id")` + `@ToString(onlyExplicitlyIncluded=true)`.
  - Setters protegidos: mutação só via métodos de domínio.
- **Spring Security**: `JwtAuthenticationConverter` customizado lê claim `authorities` (array de strings) e mapeia para `SimpleGrantedAuthority` — sem hit no DB por request.
- **Observabilidade** (ver seção 13).

---

## 3. Modelo de dados

### 3.1 Convenções comuns

Toda tabela (exceto `user_role`) tem:

| Coluna                | Tipo         |
| --------------------- | ------------ |
| id                    | uuid PK      |
| version               | bigint       |
| created_at            | timestamptz  |
| updated_at            | timestamptz  |
| created_by_user_id    | uuid FK user |
| updated_by_user_id    | uuid FK user |
| deleted_at            | timestamptz  |
| deleted_by_user_id    | uuid FK user |

### 3.2 Entidades

#### unit (522 pré-seedadas)

| Coluna          | Tipo        | Notas                                             |
| --------------- | ----------- | ------------------------------------------------- |
| id              | uuid PK     |                                                   |
| tower           | varchar(1)  | CHECK ('A','B','C')                               |
| floor           | smallint    | CHECK BETWEEN 4 AND 32                            |
| position        | smallint    | CHECK BETWEEN 1 AND 6                             |
| code            | varchar(8)  | `{floor}{lpad(position,2,'0')}{tower}`            |
| master_user_id  | uuid FK user| UNIQUE parcial; null até master ser aprovado      |

Constraints: `UNIQUE (tower, floor, position) WHERE deleted_at IS NULL`, `UNIQUE (code) WHERE deleted_at IS NULL`.

#### user

| Coluna                          | Tipo          | Notas                                                            |
| ------------------------------- | ------------- | ---------------------------------------------------------------- |
| id                              | uuid PK       |                                                                  |
| unit_id                         | uuid FK unit  | obrigatório para RESIDENT                                        |
| is_unit_master                  | boolean       | 1 por unidade ativa                                              |
| full_name                       | varchar(180)  |                                                                  |
| greeting_name                   | varchar(60)   |                                                                  |
| phone                           | varchar(20)   | E.164 — usado para WhatsApp                                      |
| phone_verified_at               | timestamptz   |                                                                  |
| gender                          | varchar(20)   | CHECK ('MALE','FEMALE','OTHER','NOT_INFORMED')                   |
| birth_date                      | date          |                                                                  |
| password_hash                   | varchar(255)  | bcrypt(HMAC-SHA256(senha, pepper))                               |
| password_pepper_version         | smallint      |                                                                  |
| must_change_password            | boolean       |                                                                  |
| status                          | varchar(30)   | CHECK ('PENDING_APPROVAL','ACTIVE','REJECTED','DISABLED','ANONYMIZED') |
| residence_proof_object_key      | varchar(255)  | só para `is_unit_master = true`                                  |
| residence_proof_filename        | varchar(255)  |                                                                  |
| residence_proof_content_type    | varchar(80)   |                                                                  |
| residence_proof_uploaded_at     | timestamptz   |                                                                  |
| proof_verified_at               | timestamptz   |                                                                  |
| approved_by_user_id             | uuid FK user  |                                                                  |
| approved_at                     | timestamptz   |                                                                  |
| rejection_reason                | text          |                                                                  |
| anonymized_at                   | timestamptz   |                                                                  |
| consent_document_version        | varchar(20)   |                                                                  |
| consent_accepted_at             | timestamptz   |                                                                  |
| consent_accepted_ip             | inet          |                                                                  |

#### user_email (1:N)

| Coluna       | Tipo          |
| ------------ | ------------- |
| user_id      | uuid FK       |
| email        | varchar(180)  |
| is_primary   | boolean       |
| verified_at  | timestamptz   |

`UNIQUE (email) WHERE deleted_at IS NULL`, `UNIQUE (user_id) WHERE is_primary AND deleted_at IS NULL`.

#### role, permission, user_role, role_permission, user_permission_grant

**role** (tabela seed, 5 valores fixos):

| Coluna       | Tipo         | Notas                                                    |
| ------------ | ------------ | -------------------------------------------------------- |
| id           | smallint PK  |                                                          |
| name         | varchar(20)  | UNIQUE; `MANAGER`, `COUNCIL`, `STAFF`, `RESIDENT`, `DOORMAN` |
| label        | varchar(40)  | "Síndico", "Conselheiro", "Administração", "Morador", "Porteiro" |
| max_holders  | smallint     | null = ilimitado; MANAGER=1, COUNCIL=3, STAFF=5          |

**permission** (tabela seed expansível):

| Coluna  | Tipo         | Notas                                                                  |
| ------- | ------------ | ---------------------------------------------------------------------- |
| id      | smallint PK  |                                                                        |
| code    | varchar(60)  | UNIQUE; e.g. `USER_MANAGE`, `REGISTRATION_APPROVE`, `RESIDENCE_PROOF_VIEW`, `CONTACT_MANAGE`, `LINK_MANAGE`, `FAQ_MANAGE`, `TAG_MANAGE`, `RECOMMENDATION_MODERATE`, `CLASSIFIED_MODERATE`, `ROLE_ASSIGN`, `PERMISSION_GRANT`, `AUDIT_VIEW` |
| label   | varchar(80)  |                                                                        |

**user_role** — M:N, PK `(user_id, role_id)`, sem soft delete, `ON DELETE CASCADE`. Enforce `role.max_holders` no service (`UserService.assignRole`).

**role_permission** — M:N, PK `(role_id, permission_id)`. Seed default:
- MANAGER → todas.
- COUNCIL → `USER_VIEW`, `REGISTRATION_VIEW`, `REGISTRATION_APPROVE`, `RESIDENCE_PROOF_VIEW`, `FAQ_MANAGE`, `CLASSIFIED_MODERATE`, `RECOMMENDATION_MODERATE`.
- STAFF → nenhuma por default; permissões concedidas individualmente via `user_permission_grant`.
- RESIDENT → nenhuma (acesso a recursos próprios não exige permission).
- DOORMAN → `USER_VIEW`.

**user_permission_grant** — concessão individual extra a um usuário. M:N `(user_id, permission_id)` + `granted_by_user_id` + `granted_at` + `revoked_at`. Útil para customizar STAFF.

**Regras de segurança da concessão (anti-escalada):**
- **Ceiling check**: grantor só pode conceder permissions que ele próprio possui (`grantedPermissions ⊆ grantor.effectivePermissions`). Verificado no `PermissionGrantService`.
- **Sem auto-concessão**: `grantor.id != target.id`.
- **Sem self-grant da meta-permission**: ninguém pode conceder a si mesmo `PERMISSION_GRANT` ou `ROLE_ASSIGN`.
- Auditoria via tabela `user_permission_grant_log` (hard delete + log) — `(action, target_user_id, permission_id, actor_id, timestamp, request_id)`.

**user_permission_grant_log** (auditoria, sem soft delete):

| Coluna             | Tipo         |
| ------------------ | ------------ |
| id                 | uuid PK      |
| action             | varchar(20)  | `GRANT` ou `REVOKE`                          |
| target_user_id     | uuid FK      |                                              |
| permission_id      | smallint FK  |                                              |
| actor_user_id      | uuid FK      |                                              |
| acted_at           | timestamptz  |                                              |
| request_id         | varchar(40)  | do MDC, para correlacionar com log           |

**Authorities efetivas no JWT** (cálculo no login):
```
authorities = (∪ role.permissions for role in user.roles)
            ∪ (user.individual_grants WHERE revoked_at IS NULL)
            + ROLE_<NAME> tokens para cada role (compatibilidade descritiva)
```

O JWT carrega claim `authorities: ["REGISTRATION_APPROVE", "USER_VIEW", ...]`. Um `JwtAuthenticationConverter` customizado registrado em `HttpSecurity.oauth2ResourceServer(...)` lê o claim e gera `SimpleGrantedAuthority` por item — **zero hit no DB por request**.

`@PreAuthorize("hasAuthority('REGISTRATION_APPROVE')")` em endpoints.

**Proibido**: `@PreAuthorize("hasRole('STAFF')")` ou similar — STAFF não tem permissions por default; usar role como proxy de acesso vaza para usuários recém-criados sem grants. **Sempre** usar `hasAuthority` com a permission específica.

**Enforce `role.max_holders` com lock pessimista**: no `UserService.assignRole`, dentro da transação:
```sql
SELECT COUNT(*) FROM user_role ur
  JOIN role r ON r.id = ur.role_id
 WHERE r.id = ? FOR UPDATE;
```
Mais defesa em profundidade: trigger Postgres que rejeita INSERT se `count >= max_holders` para roles com limite (MANAGER=1, COUNCIL=3, STAFF=5).

#### refresh_token

| Coluna         | Tipo         |
| -------------- | ------------ |
| id             | uuid PK      |
| user_id        | uuid FK      |
| token_hash     | varchar(255) |
| token_family   | uuid         |
| expires_at     | timestamptz  |
| revoked        | boolean      |
| revoked_at     | timestamptz  |
| revoked_reason | varchar(80)  |

#### password_history (NOVO — anti-reuso das últimas 5)

| Coluna                  | Tipo         | Notas                                                  |
| ----------------------- | ------------ | ------------------------------------------------------ |
| id                      | uuid PK      |                                                        |
| user_id                 | uuid FK user |                                                        |
| password_hash           | varchar(255) | mesmo formato do `user.password_hash`                  |
| password_pepper_version | smallint     | versão do pepper usado quando o hash foi gerado        |

Indexes: `(user_id, created_at DESC)`. Sem soft delete (poda automática mantém só 5 mais recentes por usuário via service após cada troca).

#### password_reset_token (fluxo WhatsApp)

| Coluna       | Tipo         | Notas                                                  |
| ------------ | ------------ | ------------------------------------------------------ |
| id           | uuid PK      |                                                        |
| user_id      | uuid FK      |                                                        |
| token_hash   | varchar(255) | SHA-256 do token; URL contém token claro de uso único  |
| expires_at   | timestamptz  | TTL 30 min                                             |
| used_at      | timestamptz  | null = ainda válido; preenche no consumo               |
| created_ip   | inet         |                                                        |
| delivered_at | timestamptz  | quando o bot WhatsApp confirmou envio                  |

Indexes: `UNIQUE(token_hash)`, `(user_id, used_at)`, `(expires_at)`.

#### classified

| Coluna             | Tipo          | Notas                                  |
| ------------------ | ------------- | -------------------------------------- |
| id                 | uuid PK       |                                        |
| title              | varchar(120)  |                                        |
| description        | text          |                                        |
| price              | numeric(12,2) | nullable                               |
| status             | varchar(20)   | CHECK ('ACTIVE','SOLD','ARCHIVED')     |
| author_user_id     | uuid FK user  | ON DELETE RESTRICT                     |

#### classified_photo

| Coluna           | Tipo            |
| ---------------- | --------------- |
| classified_id    | uuid FK         |
| object_key       | varchar(255)    |
| content_type     | varchar(80)     |
| ordering         | int             |

`UNIQUE (classified_id, ordering) WHERE deleted_at IS NULL`. Limite **5 fotos** por classificado (validado em service).

#### contact (telefones úteis) + horário

| Coluna       | Tipo          |
| ------------ | ------------- |
| name         | varchar(120)  |
| category     | varchar(60)   |
| phone        | varchar(20)   |
| notes        | text          |
| is_24h       | boolean       | atalho para "24/7"; quando true, dispensa opening_hours |

#### opening_hours (3 tabelas — FK real por owner; sem polimorfismo)

Após review do database-reviewer (FK virtual = orphans silenciosos), abrimos em 3 tabelas separadas — DDL idêntica trocando o owner. **Sem `@Inheritance` no JPA**, carregadas explicitamente pelo service (`OpeningHoursRepository.findByContactId(...)` etc.).

`contact_opening_hours`, `link_opening_hours`, `recommendation_opening_hours`:

| Coluna        | Tipo          | Notas                                                 |
| ------------- | ------------- | ----------------------------------------------------- |
| id            | uuid PK       |                                                       |
| owner_id      | uuid FK       | FK real `ON DELETE CASCADE` para a tabela owner       |
| day_of_week   | smallint      | CHECK 0..6 (0=domingo)                                |
| opens_at      | time          | null = fechado                                        |
| closes_at     | time          | null = fechado                                        |
| notes         | varchar(120)  |                                                       |

Index `(owner_id, day_of_week)`. Sem soft delete (CASCADE).

#### link

| Coluna        | Tipo          |
| ------------- | ------------- |
| title         | varchar(120)  |
| url           | varchar(500)  |
| description   | text          |
| category      | varchar(60)   |
| is_24h        | boolean       |

#### recommendation (indicações — EXPANDIDA)

| Coluna                    | Tipo            | Notas                                                                       |
| ------------------------- | --------------- | --------------------------------------------------------------------------- |
| service_name              | varchar(120)    | "Pintor", "Eletricista", etc.                                              |
| professional_name         | varchar(120)    |                                                                             |
| phone                     | varchar(20)     |                                                                             |
| **is_resident**           | boolean         | TRUE quando o profissional indicado mora no condomínio (pode ser autoindicação) |
| **resident_user_id**      | uuid FK user    | quando `is_resident=true`, referencia o morador profissional               |
| **address_line**          | varchar(255)    | endereço externo, OU número da unidade quando interno                       |
| **price_range**           | varchar(40)     | livre — "R$80-150/h", "sob consulta"                                       |
| rating                    | smallint        | CHECK 1..5                                                                  |
| comment                   | text            |                                                                             |
| recommended_by_user_id    | uuid FK user    | autor da indicação                                                          |
| **status**                | varchar(30)     | CHECK ('ACTIVE','PENDING_RESIDENT_CONSENT','HIDDEN'); default ACTIVE — para indicação não-morador, ou PENDING_RESIDENT_CONSENT quando `is_resident=true` aguardando aprovação do residente |
| resident_consent_at       | timestamptz     | preenchido quando residente aprovou                                          |

Constraint: `is_resident=true` exige `resident_user_id IS NOT NULL`.

**Ordenação default na listagem**: `ORDER BY is_resident DESC, rating DESC, created_at DESC` — moradores primeiro, depois melhor avaliados.

#### recommendation_photo

| Coluna             | Tipo          |
| ------------------ | ------------- |
| recommendation_id  | uuid FK       |
| object_key         | varchar(255)  | bucket `recommendations`                  |
| content_type       | varchar(80)   |
| ordering           | int           |

Limite **5 fotos**, **1MB cada** (validado em service e comprimido no frontend antes do upload). `UNIQUE (recommendation_id, ordering) WHERE deleted_at IS NULL`.

#### tag

| Coluna   | Tipo         | Notas                                       |
| -------- | ------------ | ------------------------------------------- |
| slug     | citext       | UNIQUE — case-insensitive nativo (`CREATE EXTENSION citext` em V1); ex. "encanador" |
| label    | varchar(80)  | exibição                                    |
| color    | varchar(20)  | classe Tailwind ou hex para badge           |

#### recommendation_tag (M:N)

| Coluna             | Tipo |
| ------------------ | ---- |
| recommendation_id  | uuid |
| tag_id             | uuid |

PK composta, sem soft delete (CASCADE).

#### faq (NOVO)

| Coluna       | Tipo          | Notas                                       |
| ------------ | ------------- | ------------------------------------------- |
| question     | varchar(300)  |                                             |
| answer       | text          | suporta markdown leve renderizado no front  |
| category     | varchar(60)   | "Geral", "Pagamentos", "Áreas comuns"...    |
| ordering     | int           | dentro da categoria; default 100            |
| published    | boolean       | rascunho não aparece para morador           |

Index `(category, ordering) WHERE deleted_at IS NULL AND published = true`.

#### whatsapp_outbox

| Coluna          | Tipo          | Notas                                                  |
| --------------- | ------------- | ------------------------------------------------------ |
| id              | uuid PK       |                                                        |
| to_phone        | varchar(20)   | E.164                                                  |
| template        | varchar(60)   | `password_reset`, `password_changed`, ...              |
| payload         | jsonb         | body completo enviado                                  |
| status          | varchar(20)   | CHECK ('PENDING','SENT','FAILED')                      |
| attempts        | smallint      | default 0                                              |
| last_attempt_at | timestamptz   |                                                        |
| error_message   | text          |                                                        |
| sent_at         | timestamptz   |                                                        |
| created_at      | timestamptz   |                                                        |

Index `(status, created_at) WHERE status IN ('PENDING','FAILED')` — indispensável para o job de reprocessamento.

#### sensitive_access_log

Audita acessos a dados pessoais de outros titulares (não-self) por COUNCIL/STAFF/MANAGER. Disparado por interceptor após endpoints com permission `USER_VIEW`, `REGISTRATION_VIEW`, `RESIDENCE_PROOF_VIEW`, export.

| Coluna          | Tipo          |
| --------------- | ------------- |
| id              | uuid PK       |
| actor_user_id   | uuid FK       |
| target_user_id  | uuid FK       |
| action          | varchar(40)   | `USER_VIEW`, `REGISTRATION_VIEW`, `PROOF_VIEW`, `EXPORT_REQUESTED`... |
| acted_at        | timestamptz   |
| client_ip       | inet          |
| user_agent      | varchar(255)  |
| request_id      | varchar(40)   |

Sem soft delete (auditoria legal). Retenção 24 meses então purge.

#### consent_document, proof_access_log — inalterados (seção LGPD)

### 3.3 STRIDE no modelo

- **Spoofing**: bcrypt+pepper; JWT iss+aud validados; aprovação humana do master; sub-usuário atestado.
- **Tampering**: `@Version`; soft delete preserva tudo; `approved_by_user_id`; FKs RESTRICT.
- **Repudiation**: auditoria automática; `proof_access_log`; logs JSON com MDC.
- **Information disclosure**: senha nunca em response; bucket isolado de comprovantes; presigned URLs TTL curto; `Referrer-Policy: no-referrer`; magic-bytes check em todos uploads.
- **DoS**: limites de campo; upload máx 5MB (comprovante) / 1MB (fotos); Bucket4j em login (5/min/IP) + register (3/h/IP) + refresh (10/min/IP) + password-reset request (3/h/IP) + password-reset consume (10/h/IP) + proof-url (10/min/admin); lockout por userId após 10 falhas.
- **Elevation of privilege**: auto-registro fixado em RESIDENT+master; promoção de roles só com permission `ROLE_ASSIGN`; concessão de permissão individual exige `PERMISSION_GRANT`; `@PreAuthorize` em endpoints; transição de estado guardada em métodos de domínio.

### 3.4 Migrações Flyway

- `V1__init.sql` — `pgcrypto`; tabelas core (`unit`, `user`, `user_email`, `role`, `user_role`, `refresh_token`, `password_reset_token`, `consent_document`, `proof_access_log`); todos indexes (FKs, status, email parcial, token_hash); CHECK constraints.
- `V2__seed_roles_permissions_units.sql` — 5 roles (MANAGER/COUNCIL/STAFF/RESIDENT/DOORMAN) com `max_holders`; tabela `permission` com todos os codes; `role_permission` com defaults (MANAGER=todas; COUNCIL=USER_VIEW+REGISTRATION_*+RESIDENCE_PROOF_VIEW+FAQ_MANAGE+*_MODERATE; STAFF=∅; RESIDENT=∅; DOORMAN=USER_VIEW); gera 522 unidades.
- `V3__seed_consent_v1.sql`.
- `V4__seed_admin.sql` — admin com `__PENDING__` (encoder rejeita explicitamente).
- `V5__create_classified.sql` — `classified`, `classified_photo`.
- `V6__create_contact_link_opening_hours.sql` — `contact`, `link`, `opening_hours` polimórfica.
- `V7__create_recommendation_tag.sql` — `recommendation`, `recommendation_photo`, `tag`, `recommendation_tag`.
- `V8__create_faq.sql`.
- `V9__seed_tags.sql` — tags iniciais comuns: "encanador", "eletricista", "limpeza", "pintura", "marcenaria", "babá", "petsitter", "manutenção", "estética", "professor particular".

`AdminBootstrap` por UPDATE condicional atômico (idempotente entre replicas):
```sql
UPDATE "user" SET password_hash = ?, password_pepper_version = ?
 WHERE password_hash = '__PENDING__';
```

---

## 4. API e autenticação

### 4.1 Autenticação JWT

- **Access**: JWT HS256, TTL 15 min, claims `iss`, `aud`, `sub`, `roles`, `unitId`, `isUnitMaster`, `exp`, `iat`, `jti`. Header `kid` para rotação de secret.
- **Refresh**: opaque random 32 bytes em **cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`**, TTL 7 dias. Hash SHA-256 no DB. Rotação atômica + detecção de replay revoga toda `token_family`.
- **Senha**: `bcrypt(HMAC-SHA256(senha, pepper), salt)` cost 12. Pepper rotacionável; histórico re-hasheado no mesmo job.
- **Política de senha** (validador Bean Validation `@StrongPassword`, custom usando Passay):
  - Mínimo 8 caracteres.
  - Pelo menos 1 letra **maiúscula**, 1 letra **minúscula**, 1 **número**, 1 **caractere especial** (`!@#$%^&*()_+-=[]{};':",./<>?`).
  - **Não pode ser igual às últimas 5 senhas do usuário** (compara via bcrypt+pepper contra hashes em `password_history`).
  - Não pode conter `full_name`, `greeting_name`, ou parte local do e-mail do usuário (≥4 chars contíguos, case-insensitive).
  - **Verificação de histórico timing-safe**: o validator itera todos os 5 hashes em `password_history` sem short-circuit. Se a primeira comparação bcrypt der match, ainda compara os demais antes de retornar `PASSWORD_REUSED`. Evita oráculo de tempo.
  - **Rotação de pepper**: o job de rotação atualiza `user.password_hash` E re-hasheia toda `password_history` daquele usuário com o novo pepper, mantendo `password_pepper_version` consistente. Sem isso, hashes históricos com pepper antigo ficam não-verificáveis e a regra de reuso silenciosamente falha.
  - Erros validados retornam 400 com `fields[].code` específico (`PASSWORD_TOO_SHORT`, `PASSWORD_MISSING_UPPER`, `PASSWORD_REUSED`, etc.) para o frontend mostrar mensagens granulares em pt-BR.

### 4.2 Endpoints públicos

```
POST /api/auth/login                  { emailOrLogin, password }
                                      → 200 { accessToken, user }; refresh em cookie
POST /api/auth/register-master        multipart: campos + residenceProof + consentVersion
                                      → 202 { id, status:PENDING_APPROVAL }
POST /api/auth/refresh                cookie refresh_token
POST /api/auth/password/request-reset { emailOrLogin }
                                      → 202 sempre (não vaza se e-mail existe);
                                        backend dispara mensagem WhatsApp p/ phone do usuário
POST /api/auth/password/consume-reset { token, newPassword }
                                      → 204; consome token, invalida sessions ativas
GET  /api/units/lookup?code=702C      → { id, code, hasActiveMaster }
GET  /api/privacy/document/current
```

Rate limits Bucket4j:
- login 5/min/IP + lockout 10 falhas consecutivas/userId (reset em sucesso ou 30 min)
- register-master 3/h/IP
- refresh 10/min/IP
- request-reset 3/h/IP **e** 3/h/userId
- consume-reset 10/h/IP
- units/lookup 30/min/IP

### 4.3 Endpoints autenticados (resumo)

```
GET    /api/auth/me                                           self
POST   /api/auth/logout                                       self
PUT    /api/users/me                                          self
PUT    /api/users/me/password                                 self (5/h)
POST   /api/users/me/emails                                   self
DELETE /api/users/me/emails/{id}                              self
PUT    /api/users/me/emails/{id}/primary                      self

POST   /api/units/me/members                                  RESIDENT (master)
GET    /api/units/me/members                                  RESIDENT (master) | self
PUT    /api/units/me/members/{id}/disable                     RESIDENT (master)

POST   /api/users                                             USER_MANAGE
GET    /api/users                                             USER_VIEW
GET    /api/users/{id}                                        USER_VIEW | self
PUT    /api/users/{id}                                        USER_MANAGE
DELETE /api/users/{id}                                        USER_MANAGE
PUT    /api/users/{id}/roles                                  ROLE_ASSIGN
PUT    /api/users/{id}/permissions                            PERMISSION_GRANT

GET    /api/registrations?status=PENDING_APPROVAL             REGISTRATION_VIEW
GET    /api/registrations/{id}                                REGISTRATION_VIEW
GET    /api/registrations/{id}/proof-url                      RESIDENCE_PROOF_VIEW
POST   /api/registrations/{id}/approve                        REGISTRATION_APPROVE
POST   /api/registrations/{id}/reject     { reason }          REGISTRATION_APPROVE

GET    /api/privacy/me/export                                 self
POST   /api/privacy/me/anonymize                              self

GET    /api/classifieds                                       any logado
GET    /api/classifieds/{id}                                  any logado
POST   /api/classifieds                                       any (autor=self)
PUT    /api/classifieds/{id}                                  author | CLASSIFIED_MODERATE
DELETE /api/classifieds/{id}                                  author | CLASSIFIED_MODERATE
POST   /api/classifieds/{id}/photos      (≤5, 1MB cada)       author | CLASSIFIED_MODERATE
DELETE /api/classifieds/{id}/photos/{photoId}                 author | CLASSIFIED_MODERATE
GET    /api/classifieds/{id}/photos/{photoId}/url             any logado

GET    /api/contacts                                          any logado
POST   /api/contacts                     incl. openingHours[] CONTACT_MANAGE
PUT    /api/contacts/{id}                                     CONTACT_MANAGE
DELETE /api/contacts/{id}                                     CONTACT_MANAGE

GET    /api/links                                             any logado
POST   /api/links                        incl. openingHours[] LINK_MANAGE
PUT    /api/links/{id}                                        LINK_MANAGE
DELETE /api/links/{id}                                        LINK_MANAGE

GET    /api/recommendations                                   any logado
                                          query: ?tag=...&residentOnly=&search=&page=
                                          default sort: residents first, rating desc
GET    /api/recommendations/{id}                              any logado
POST   /api/recommendations                                   any (autor=self)
                                          body inclui isResident, residentUserId (se true),
                                          addressLine, priceRange, tagSlugs[]
PUT    /api/recommendations/{id}                              author | RECOMMENDATION_MODERATE
DELETE /api/recommendations/{id}                              author | RECOMMENDATION_MODERATE
POST   /api/recommendations/{id}/photos  (≤5, 1MB cada)       author | RECOMMENDATION_MODERATE
DELETE /api/recommendations/{id}/photos/{photoId}             author | RECOMMENDATION_MODERATE
POST   /api/recommendations/{id}/resident-consent             residente indicado (self) | RECOMMENDATION_MODERATE
POST   /api/recommendations/{id}/hide                         RECOMMENDATION_MODERATE
GET    /api/recommendations/{id}/photos/{photoId}/url         any logado

GET    /api/tags                          listar para autocomplete  any logado
POST   /api/tags                          (criação livre)            any logado
                                          (autor cria tag se não existir; TAG_MANAGE modera)
DELETE /api/tags/{id}                                                TAG_MANAGE

GET    /api/faq                           ?category=...               any logado
GET    /api/faq/{id}                                                  any logado
POST   /api/faq                                                       FAQ_MANAGE
PUT    /api/faq/{id}                                                  FAQ_MANAGE
PUT    /api/faq/{id}/publish              { published: true|false }   FAQ_MANAGE
DELETE /api/faq/{id}                                                  FAQ_MANAGE
PUT    /api/faq/reorder                   { items: [{id, ordering}] } FAQ_MANAGE
```

### 4.4 Reset de senha via WhatsApp

Fluxo:
1. Usuário acessa "Esqueci minha senha" no front, informa e-mail ou login.
2. Frontend → `POST /api/auth/password/request-reset`. Backend responde 202 sempre (não vaza existência).
3. Se usuário existe e está ACTIVE com `phone_verified_at != null`:
   - Gera token aleatório 32 bytes URL-safe; salva hash SHA-256 em `password_reset_token`.
   - Invalida tokens anteriores não usados do mesmo usuário.
   - Publica `PasswordResetRequestedEvent`. Listener async chama `WhatsAppNotificationClient.send(phone, link)`.
4. Bot do condomínio (serviço externo do Paulo) recebe webhook `POST /webhook/wpp/send-message` autenticado com HMAC compartilhado, envia ao número.
5. Usuário clica no link `https://app.helbor.../reset?token=xxx`.
6. Frontend → `POST /api/auth/password/consume-reset`. Backend valida token (hash, expiração, não usado), atualiza senha, marca `used_at`, revoga todos refresh tokens do usuário.
7. Backend dispara `PasswordResetCompletedEvent` → WhatsApp informativo "Sua senha foi alterada."

**WhatsAppNotificationClient:**

- HTTP client (Spring WebClient) para webhook do bot.
- Endpoint configurável `APP_WHATSAPP_WEBHOOK_URL`.
- **Segredo HMAC rotacionável** — `APP_WHATSAPP_HMAC_KEYS=v1:base64-32-bytes,v2:...`, `APP_WHATSAPP_HMAC_ACTIVE_KID=v1`. Header `X-Hmac-Kid` para o bot saber qual chave usar.
- **Payload com anti-replay**: corpo inclui `timestamp` (ISO-8601), `jti` (UUID único), `to`, `template`, `data`. Bot rejeita `|now - timestamp| > 5s` e armazena `jti` por 5min para detectar replay.
- Signature: HMAC-SHA256 do corpo serializado em header `X-Signature: sha256=<hex>`.
- Resilience4j (config completo em `application.yml`):
  ```yaml
  resilience4j:
    timelimiter.instances.whatsapp.timeoutDuration: 5s
    retry.instances.whatsapp:
      maxAttempts: 3
      waitDuration: 1s
      enableExponentialBackoff: true
      exponentialBackoffMultiplier: 2
    circuitbreaker.instances.whatsapp:
      slidingWindowSize: 10
      failureRateThreshold: 50
      waitDurationInOpenState: 30s
  ```
- Fallback (`@CircuitBreaker(fallbackMethod="fallback")`): log WARN, marca outbox `FAILED`, **nunca propaga exceção** ao chamador HTTP.
- `whatsapp_outbox` armazena cada tentativa; job `@Scheduled` reprocessa `status IN (PENDING, FAILED) AND attempts < 5`.

**Listener async correto**:
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("whatsappTaskExecutor")
public void on(PasswordResetRequestedEvent e) {
    if (passwordResetTokenRepo.findById(e.tokenId())
            .filter(t -> t.getDeliveredAt() == null)
            .isEmpty()) return;       // idempotência
    whatsAppClient.sendPasswordReset(...);
}
```
`AFTER_COMMIT` evita disparo se a transação fizer rollback. `delivered_at` em `password_reset_token` evita duplo envio em caso de retry de mensageria interna.

### 4.5 Domínio rico

Métodos por agregado:
- `User.approveAsMaster(approver)`, `User.reject(approver, reason)`, `User.disable(actor)`, `User.anonymize()`.
- `User.changePassword(newRawPassword, encoder, recentHistory)` — valida política (regex composto), checa contra `recentHistory` (últimos 5 hashes), gera novo hash, retorna evento `PasswordChangedEvent`.
- `User.rehashPassword(newHash, newPepperVersion)` — rotação transparente.
- `Unit.assignMaster(user)`.
- `Classified.markSold(actor)`, `Classified.archive(actor)`, `Classified.addPhoto(key, contentType, ordering)` (enforce ≤5).
- `Recommendation.changeRating(actor, value)`, `Recommendation.addPhoto(...)` (≤5), `Recommendation.markAsResident(residentUser)` (transição com validação), `Recommendation.consent(residentActor)` (aprovação do residente indicado), `Recommendation.hide(actor)`.
- `RefreshToken.rotate(newHash)`, `RefreshToken.revoke(reason)`.
- `PasswordResetToken.consume()` (idempotência).
- `Faq.publish()`, `Faq.unpublish()`, `Faq.reorder(newOrdering)`.

### 4.6 Tratamento de erros

`GlobalExceptionHandler` retorna envelope uniforme com `requestId` para suporte. Optimistic lock → 409 `CONCURRENT_MODIFICATION`. Validation → 400 `VALIDATION_FAILED` + `fields[]`. Mensagem genérica de login (não enumera).

### 4.7 OpenAPI

`springdoc-openapi`. Em **prod** requer permission `AUDIT_VIEW` (basicamente MANAGER+COUNCIL); em dev/test livre.

### 4.8 Segurança de upload

- Tamanhos: comprovante ≤5MB; foto classified/recommendation ≤1MB (já comprimida no frontend).
- Tipos aceitos: `application/pdf`, `image/jpeg`, `image/png`, `image/webp`.
- **Magic bytes check** server-side (`%PDF`, `\xFF\xD8\xFF`, `\x89PNG`, `RIFF...WEBP`).
- Object key UUID; nome original guardado para exibição.

---

## 5. Design System UX/UI (responsivo, mobile-first)

### 5.1 Estilo geral

**Flat Design moderno** com toques de profundidade discreta (Soft UI Evolution). Bordas arredondadas médias (12-16px). Sem skeumorfismo. Sem emojis como ícones (usar **lucide-react**). Animações 150-300ms ease-out.

### 5.2 Tipografia

Pareamento **Geometric Modern**:
- **Heading**: `Outfit` (300, 400, 500, 600, 700).
- **Body**: `Work Sans` (300, 400, 500, 600).
- Importadas em `index.html` via Google Fonts com `display=swap`.

Escala (Tailwind):
- `text-xs` 12px (apenas captions/badges)
- `text-sm` 14px (helper, secondary)
- `text-base` 16px (body padrão; mínimo no mobile)
- `text-lg` 18px (subhead)
- `text-xl` 20px (h3)
- `text-2xl` 24px (h2)
- `text-3xl` 30px (h1 mobile)
- `text-4xl` 36px (h1 desktop)

Line-height: body 1.6; heading 1.2-1.3.

### 5.3 Paleta funcional (mapeando o tangram)

Tokens CSS expostos como variáveis Tailwind/shadcn (via `tailwind.config.ts` + `globals.css`):

| Token Tailwind        | Valor (light)        | Valor (dark)         | Uso                                  |
| --------------------- | -------------------- | -------------------- | ------------------------------------ |
| `--background`        | `0 0% 100%`          | `0 0% 6%`            | fundo principal                      |
| `--foreground`        | `222 47% 11%`        | `0 0% 96%`           | texto                                |
| `--card`              | `210 20% 98%`        | `0 0% 10%`           | cards                                |
| `--card-foreground`   | `222 47% 11%`        | `0 0% 96%`           |                                      |
| `--primary`           | `210 78% 47%` (#1976D2) | `210 78% 60%`     | botões principais, links, ativo nav |
| `--primary-foreground`| `0 0% 100%`          | `0 0% 100%`          |                                      |
| `--accent`            | `42 90% 54%` (#F1B829) | mesma             | CTA destaque, "publicar", "aprovar" |
| `--accent-foreground` | `222 47% 11%`        | `222 47% 11%`        |                                      |
| `--info`              | `188 75% 45%` (#1FB4C8) | mesma             | dicas, links, badges info           |
| `--success`           | `82 56% 49%` (#84CC16) | mesma             | confirmação, "aprovado"             |
| `--warning`           | `42 90% 54%`         | mesma                | atenção                              |
| `--destructive`       | `354 75% 56%` (#E63946) | mesma             | rejeição, delete, erro              |
| `--muted`             | `210 16% 93%`        | `0 0% 16%`           | superfícies neutras                  |
| `--muted-foreground`  | `215 16% 47%`        | `0 0% 65%`           | texto secundário                     |
| `--border`            | `214 32% 91%`        | `0 0% 18%`           | divisores, inputs                    |
| `--ring`              | `210 78% 47%`        | `210 78% 60%`        | foco                                 |

**Regra**: vermelho coral é exclusivo para destructive — nunca em CTA neutro. Amarelo é o "acento de chamada" do brand, usado em CTAs principais que não destroem (ex.: "Aprovar cadastro", "Publicar classificado").

Brand color strip (decorativa, no header/footer e em estados festivos): faixas tangram em ordem `red → yellow → cyan → green → blue` com altura 4-6px.

### 5.4 Escala de spacing/radius

- Spacing: múltiplos de 4px (Tailwind padrão); seções 16/24/32/48.
- Radius: `rounded-md` (8px) padrão; `rounded-lg` (12px) em cards; `rounded-xl` (16px) em modais; `rounded-full` em avatares/badges.
- Shadow: `shadow-sm` em cards, `shadow-md` em popovers, `shadow-lg` em modais. Sem shadows agressivas.

### 5.5 Componentes shadcn essenciais

Instalar via CLI: `button`, `input`, `label`, `form`, `card`, `dialog`, `sheet`, `dropdown-menu`, `select`, `checkbox`, `radio-group`, `switch`, `tabs`, `table`, `badge`, `avatar`, `alert`, `alert-dialog`, `tooltip`, `accordion` (FAQ), `command` + `popover` (combobox pattern para TagPicker), `calendar` (date-picker), `separator`, `skeleton`, `progress`, `breadcrumb`, `pagination`, `sonner` (toasts modernos integráveis com TanStack Query).

**Patterns/Compostos (não componentes diretos):**
- **DataTable**: `@tanstack/react-table` + shadcn `Table` para `UsersPage`, `PendingRegistrationsPage`, `TagsPage`, `FaqAdminPage`.
- **Combobox/TagPicker**: `Command` + `Popover`.
- **PhotoUploader**: `@dnd-kit/core` + `@dnd-kit/sortable` para reordenar; `browser-image-compression` (`{ maxSizeMB: 0.9, maxWidthOrHeight: 2000, useWebWorker: true }`) antes do upload.
- **UnitSelector simplificado**: `Input` com máscara via `react-imask` (`702C` validado client-side) + debounce de `/api/units/lookup`. Os 3-selects encadeados só ficam no painel admin (`UnitsPage`).
- **OpeningHoursDisplay/Editor**: usa `date-fns-tz` com fuso fixo `America/Sao_Paulo` para calcular "Aberto agora" — evita divergência se o browser estiver em outro fuso.

### 5.6 Padrões de layout responsivo

Breakpoints Tailwind: `sm` 640, `md` 768, `lg` 1024, `xl` 1280.

- **Mobile (<768px)**: bottom navbar com 5 ícones+label (Home, Classificados, Telefones, Indicações, Eu). Topbar com logo+menu. Cards 1 coluna full-width.
- **Tablet (768-1024px)**: topbar + sidebar collapsable. Grid 2 colunas.
- **Desktop (≥1024px)**: sidebar fixa esquerda (240px), topbar com user menu e notificações, content max-w-6xl centralizado.

Touch targets ≥44px em mobile. Spacing ≥8px entre elementos clicáveis. `min-h-dvh` em containers raiz.

### 5.7 Componentes-chave do produto

- **OpeningHoursDisplay**: chip por dia da semana, hoje em destaque com indicador "Aberto agora" / "Fechado".
- **OpeningHoursEditor**: 7 linhas (dia → opens/closes ou "Fechado"), checkbox "24h" global.
- **TagPicker**: combobox + criação inline.
- **PhotoUploader**: grid de até 5 thumbnails, drag-to-reorder, compressão client-side antes de enviar.
- **ResidentBadge**: badge azul "Morador" exibido em recommendations.is_resident.
- **UnitSelector**: 3 selects encadeados Torre/Andar/Posição com `useQuery` para `/units/lookup`.

### 5.8 Acessibilidade

WCAG AA mínimo (AAA quando viável):
- Contrast ≥4.5:1 em texto normal, ≥3:1 em large/UI.
- Focus ring visível 2-3px no `--ring`.
- `aria-label` em ícone-only.
- `prefers-reduced-motion` respeitado.
- Inputs com `<label>` associado; erros com `role="alert"`/`aria-live="polite"`.
- Tab order natural; skip link "Pular para conteúdo".

**Ferramentas de validação (dev + CI):**
- **`@axe-core/react`** em modo dev — loga violações no console durante uso.
- **`eslint-plugin-jsx-a11y`** no lint-staged.
- **`@lhci/cli`** (Lighthouse CI) no GitHub Actions com budgets para `accessibility ≥ 95`, `performance ≥ 80`, `best-practices ≥ 90`.

### 5.9 Loading states e Skeletons

Cada página com fetch via TanStack Query define um componente skeleton dedicado, evitando CLS:
- `PendingRegistrationsPage` → `SkeletonTable` (5 linhas).
- `RecommendationsList`, `ClassifiedsList` → `SkeletonCard` (4 cards com placeholder de foto).
- `FaqPage` → `SkeletonAccordion` (3 itens).
- `ContactsList` → `SkeletonContactCard`.
- Renderiza enquanto `isLoading === true`.

---

## 6. Frontend (estrutura)

```
frontend/src/
├── main.tsx, App.tsx, router.tsx
├── lib/                 api.ts, auth.ts, image-compression.ts, utils.ts, whatsapp-link.ts
├── design-system/       tokens.css, theme.ts
├── components/
│   ├── ui/              shadcn (button, input, dialog, accordion, command, etc.)
│   ├── Layout.tsx, BottomNav.tsx, Sidebar.tsx, TopBar.tsx
│   ├── ProtectedRoute.tsx, RoleGate.tsx
│   ├── OpeningHoursDisplay.tsx, OpeningHoursEditor.tsx
│   ├── TagPicker.tsx, ResidentBadge.tsx
│   ├── PhotoUploader.tsx (compressão browser-image-compression)
│   └── UnitSelector.tsx
└── features/
    ├── auth/            LoginPage, RegisterMasterPage (wizard), PendingApprovalPage,
    │                    PasswordResetRequestPage, PasswordResetConsumePage,
    │                    ChangePasswordPage
    ├── unit/            UnitMembersPage (master cria/edita)
    ├── privacy/         PrivacyPolicyPage, ExportMyDataPage, AnonymizePage
    ├── classifieds/     List, Detail, Form (até 5 fotos comprimidas)
    ├── contacts/        List (com OpeningHoursDisplay), Form
    ├── links/           List, Form
    ├── recommendations/ List (filtros: tag, residentOnly, search; ordenação default),
    │                    Detail, Form (isResident, TagPicker, PhotoUploader, AddressLine)
    ├── faq/             FaqPage (accordion por categoria)
    └── admin/           PendingRegistrationsPage, UsersPage, UnitsPage,
                         TagsPage, FaqAdminPage
```

### 6.0 AuthProvider — estado de loading

`AuthProvider` inicia com `status: "loading"` e dispara `POST /api/auth/refresh` (cookie HttpOnly enviado automaticamente). Enquanto `status === "loading"`, exibe `<FullPageSpinner>` (evita flash de tela de login antes do refresh resolver). `ProtectedRoute` aguarda `status !== "loading"`. Após F5, a transição loading → authenticated|unauthenticated é transparente.

### 6.0.1 i18n

Strings pt-BR em `src/constants/messages.<feature>.ts` por feature, importadas onde forem usadas. Sem `react-i18next` no MVP — migração futura é direta sem refator de chaves.

### 6.1 Fluxo Master register (wizard)

1. Termo de privacidade (versão vigente; checkbox).
2. UnitSelector (Torre → Andar → Posição) com check `hasActiveMaster`.
3. Dados pessoais (full_name, greeting_name, gender opcional, birth_date, phone E.164, email primário, senha+confirmar).
4. Comprovante (upload + preview).
5. Submit → 202 → PendingApprovalPage.

### 6.2 Reset por WhatsApp (UI)

- `PasswordResetRequestPage`: input "e-mail ou login" → submit → mensagem "Se a conta existir, você receberá um WhatsApp em alguns instantes."
- `PasswordResetConsumePage`: lê `?token=...`, mostra form "Nova senha + confirmar", submete e redireciona pro login.

### 6.3 Tela de Indicação

Form com: service_name, professional_name, phone, **toggle "É morador do condomínio?"** (mostra UnitSelector + busca de usuário quando true), addressLine (auto-preenchido se morador), priceRange, rating (estrelas), comment, tags (TagPicker), até 5 fotos (PhotoUploader). Antes do upload, cada imagem é comprimida client-side com `browser-image-compression` para ≤1MB e ≤2000px lado maior.

Listagem com filtros: tag chip, switch "Apenas moradores", busca textual. Ordenação default: moradores primeiro, rating desc.

### 6.4 FAQ

`FaqPage` com Accordion do shadcn agrupado por categoria. Admin tem `FaqAdminPage` com listagem ordenável (drag-and-drop simples) e form WYSIWYG-ish (textarea com preview markdown).

### 6.5 Telefones/Links com horário

Card mostra nome, categoria, telefone (botão clicável `tel:` no mobile), e `OpeningHoursDisplay` colapsado por padrão (expande). Indicador "Aberto agora" calculado no client com `date-fns`.

---

## 7. Deploy (Dokploy)

### 7.1 Serviços

Provisionados no painel `panel.paulobof.com.br`:
- Backend, Frontend, Postgres, MinIO (compose).

### 7.2 Backend

Multi-stage Dockerfile (Maven → JRE 21 slim). Healthchecks `/actuator/health/{liveness,readiness}`. Graceful shutdown + HikariCP pool. Security headers Spring Security. Swagger restrito a permission `AUDIT_VIEW` em prod.

### 7.3 Frontend

Multi-stage (Node 22 → Nginx). CSP estrito. Build arg `VITE_API_BASE_URL`. Fontes Google self-hosted via `@fontsource/outfit` e `@fontsource/work-sans` (evita CSP issue + offline).

### 7.4 MinIO

Buckets: `residence-proofs`, `classifieds`, `recommendations`. Criação idempotente no startup do backend.

### 7.5 Ordem de deploy

Postgres → MinIO → Backend → Frontend → (Bot WhatsApp externo configurado fora do Dokploy).

### 7.6 SSH

`C:\Users\paulo\Downloads\pc_paulo_private_id_rsa.txt` — NUNCA commitar, ler ou imprimir.

---

## 8. Testes

JUnit 5 + Mockito + AssertJ. Sem `@SpringBootTest` no MVP.

```
backend/src/test/java/br/com/condominio/
├── feature/auth/AuthServiceTest.java
├── feature/auth/RefreshTokenServiceTest.java
├── feature/auth/PasswordResetServiceTest.java
├── feature/registration/RegistrationServiceTest.java
├── feature/unit/UnitMemberServiceTest.java
├── feature/user/UserServiceTest.java
├── feature/classified/ClassifiedServiceTest.java
├── feature/contact/ContactServiceTest.java
├── feature/link/LinkServiceTest.java
├── feature/recommendation/RecommendationServiceTest.java
├── feature/faq/FaqServiceTest.java
├── feature/tag/TagServiceTest.java
├── feature/privacy/PrivacyServiceTest.java
├── whatsapp/WhatsAppNotificationClientTest.java
└── shared/security/PepperedBCryptPasswordEncoderTest.java   (real, sem mocks)
```

Frontend: Vitest configurado, sem testes no MVP. `vitest run --passWithNoTests`.

---

## 9. Estratégia de branches, CI/CD e ambientes

### 9.1 Trunk-Based Development

- Branch única de longa vida: **`main`** — sempre deployável.
- Feature branches curtas (≤1-2 dias): nomenclatura `feat/<tópico>`, `fix/<tópico>`, `chore/<tópico>`.
- PR pequeno (≤400 linhas de diff) revisado e mergeado o quanto antes; squash merge.
- Work-in-progress fica no `main` protegido por **feature flag** (ver 9.4).
- Migrations **sempre backward-compatible** (expand/contract):
  1. Adiciona coluna nullable / nova tabela → deploy.
  2. Backfill via job.
  3. Código novo passa a ler/escrever a nova estrutura → deploy.
  4. Em release posterior: tornar coluna NOT NULL, ou remover obsoleta.
  Nunca remover e adicionar em um único deploy.

### 9.2 Ambientes

| Ambiente | URL exemplo                       | Branch    | Deploy           | Banco/MinIO   |
| -------- | --------------------------------- | --------- | ---------------- | ------------- |
| Dev      | `localhost`                       | feature/* | manual local     | docker-compose.dev.yml |
| **HML**  | `hml.app.helbor...` / `hml.api...`| `main`    | **auto** no push | Postgres+MinIO dedicados |
| **Prod** | `app.helbor...` / `api.helbor...` | tag git `vX.Y.Z` ou release manual | **manual** via Dokploy "Promote" | Postgres+MinIO de produção |

HML é réplica funcional de prod com dados de teste; usado para validação humana antes de promover. **Sem PII real, enforced**:

- **Proibido por política** (no `CLAUDE.md`) restaurar dump de prod em HML.
- HML inicializa com `APP_SEED_FAKE_DATA=true` que aciona um conjunto de Flyway repeatable migrations (`R__seed_hml_fake_users.sql` etc.) com 5-10 usuários sintéticos + classificados/recomendações exemplo.
- O env var `APP_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/testdata` adiciona as migrations só em HML.
- Banco HML não exposto publicamente; acesso só via tunel SSH com chave dedicada.
- WhatsApp em HML aponta para `MockWhatsAppClient` (log, sem disparo real) ou bot sandbox separado.

### 9.2.1 SLOs e DR

- **Disponibilidade**: 99,5% mensal (≈ 3,6h downtime/mês).
- **Latência p95** em endpoints autenticados: < 500ms.
- **Latência upload** (1MB): < 3s p95.
- **RPO**: 24h (último backup diário). **RTO**: 4h (provisionar Dokploy + restore + smoke).
- **Backup**:
  - Postgres: `pg_dump | gzip` diário via cron no servidor → upload para bucket MinIO `backups` (separado) e idealmente para S3 externo. Retenção 7 dias rolantes + 1 mensal.
  - MinIO: `mc mirror minio/<bucket> backups/<bucket>` diário; SSE-S3 ativado em `residence-proofs` e `recommendations`.
- **Restore drill**: documentado em `docs/runbooks/restore-postgres.md` e `docs/runbooks/restore-minio.md`. Executado antes do go-live e a cada 6 meses.

### 9.3 Pipeline CI/CD (GitHub Actions)

`.github/workflows/ci.yml`:
- **Em PR para `main`**:
  - `backend-tests`: `mvn -B verify`.
  - `frontend-checks`: `npm ci && npm run lint && npm run typecheck && npm run build && npm test -- --run`.
  - `spec-lint`: validar formato do commit, tamanho do diff (warn se > 400 linhas).
  - Required checks no GitHub: ambos verde para merge.

- **Em push para `main`** (após merge):
  - `deploy-hml`: chama webhook Dokploy do environment **HML**.
  - **Smoke real com wait-for-healthy**: poll `until curl -sf https://hml.api.../actuator/health/readiness; do sleep 10; done` com timeout 3 min. Falha o workflow se não ficar UP no prazo.
  - Registra `github.sha` + timestamp em arquivo `deployments-hml.log` (artifact) para o soak time da promoção.

- **Promoção para prod** (manual):
  - Workflow `promote-to-prod.yml` com `workflow_dispatch` + input `version` (sha ou tag).
  - **Soak time**: step verifica via `gh api` que o `version` foi deployado em HML há **≥30 min**; falha caso contrário (configurável via env `APP_PROMOTE_MIN_SOAK_MINUTES`).
  - Requires `manual approval` (GitHub Environments "production" protegido).
  - Chama webhook Dokploy do environment **Prod**.
  - Smoke real prod (mesmo poll de health).
  - Cria tag `vX.Y.Z` no commit promovido.

### 9.4 Feature flags

KISS — flags via env vars boolean lidas com `@Value("${app.feature.<nome>.enabled:false}")` (e não `@ConditionalOnProperty`, que congela no startup). Mudança de flag exige redeploy mas o código toma decisão por request, sem cache.

- Naming: `APP_FEATURE_<NOME>_ENABLED=true|false`.
- Default em HML: `true` (testar comportamento real).
- Default em Prod: `false` até o release oficial.
- Frontend recebe flags via `GET /api/config/features` (autenticado, exige permission `AUDIT_VIEW` para enxergar flags **inativas**; usuários comuns só vêem `true`). React Query com `refetchInterval: 5 * 60 * 1000` (5 min) reflete mudanças sem F5.
- **Auditoria de mudança**: spec exige que mudanças em prod sejam registradas em uma issue do GitHub no projeto com `actor`, `flag`, `from`, `to`, `motivo`. Sem ferramenta — convenção humana documentada no `CLAUDE.md`.
- Convenção: cada flag tem dono + data prevista de remoção. Flags são temporárias — remover dentro de 30 dias após ativar permanentemente.

### 9.5 Dokploy — dois environments + observabilidade

No painel `panel.paulobof.com.br`, replicar o environment atual:
- **`prod`** — environment já existente (gestor-condominio).
- **`hml`** — novo environment, com 4 serviços espelhados (backend-hml, frontend-hml, postgres-hml, minio-hml). Mesmo repo, mesma branch `main`, mas:
  - Domínios distintos (`hml.api.helbor...`, `hml.app.helbor...`).
  - Env vars próprias (banco HML, MinIO HML, JWT secret HML, WhatsApp **mock** ou bot sandbox, `APP_FEATURE_*_ENABLED=true`).
  - Webhook do GitHub Actions aciona deploy automático.
- Promoção prod: webhook separado disparado pelo workflow `promote-to-prod`.

**Stack de observabilidade (declarada explicitamente):**
- **Prometheus** como compose service no Dokploy (em **prod**), config em `deploy/dokploy-prometheus-compose.yml`. Scrape de `backend:8080/actuator/prometheus` a cada 15s. Storage local 14 dias.
- **Grafana** como compose service no Dokploy, dashboards versionados em `deploy/grafana/dashboards/*.json` (provisionados via auto-provisioning).
- **Alertmanager** integrado ao Prometheus, com receivers webhook **Slack** (canal `#alerts-gestor`) e/ou e-mail do Paulo (`APP_ALERT_EMAIL`). Rotas com severidade (critical → imediato, warning → batch 15min).
- Alternativa low-touch: usar **Grafana Cloud free tier** (10k series, 14 dias) — basta apontar `remote_write` do Prometheus. Decisão final na implementação (ambas viáveis).

### 9.6 Rollback (movido)

(idem antes — Dokploy 1-click + expand/contract).


---

## 10. Pre-commit / Qualidade

- Husky v9 + lint-staged v15 + commitlint.
- ESLint v9 flat + Prettier (frontend).
- Spotless + `google-java-format` (backend).
- Hooks: `pre-commit` (lint-staged), `commit-msg` (commitlint), `pre-push` (testes back+front).
- `.editorconfig`: 2 spaces TS, 4 spaces Java, LF, UTF-8.

---

## 11. Variáveis de ambiente (template)

```
# Admin inicial
APP_ADMIN_EMAIL=paulobof@gmail.com
APP_ADMIN_NAME=Paulo
APP_ADMIN_INITIAL_PASSWORD=troque-no-primeiro-login

# Segurança senha
APP_PASSWORD_PEPPER=base64-32-bytes
APP_BCRYPT_STRENGTH=12

# JWT
APP_JWT_KEYS=v1:base64-32-bytes
APP_JWT_ACTIVE_KID=v1
APP_JWT_ISSUER=gestor-condominio
APP_JWT_AUDIENCE=gestor-condominio-web
APP_JWT_ACCESS_TTL=PT15M
APP_JWT_REFRESH_TTL=P7D

# CORS / Cookie
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
APP_COOKIE_DOMAIN=
APP_COOKIE_SECURE=false

# Postgres / MinIO
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=gestor_condominio
POSTGRES_USER=condominio
POSTGRES_PASSWORD=troque
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=condominio
MINIO_SECRET_KEY=troque
MINIO_BUCKET_PROOFS=residence-proofs
MINIO_BUCKET_CLASSIFIEDS=classifieds
MINIO_BUCKET_RECOMMENDATIONS=recommendations
MINIO_PRESIGNED_TTL_PROOFS_SECONDS=300
MINIO_PRESIGNED_TTL_PHOTOS_SECONDS=600

# WhatsApp Bot
APP_WHATSAPP_WEBHOOK_URL=https://bot.paulobof.com.br/send-message
APP_WHATSAPP_HMAC_SECRET=base64-32-bytes
APP_WHATSAPP_TIMEOUT_MS=5000
APP_WHATSAPP_RETRY_MAX=3

# Reset de senha
APP_PASSWORD_RESET_TTL=PT30M
APP_PASSWORD_RESET_BASE_URL=https://app.helbor.../reset

# LGPD
APP_DPO_EMAIL=privacidade@helbor.exemplo
APP_CONTROLLER_NAME=Condomínio HELBOR TRILOGY HOME
APP_CONTROLLER_CNPJ=00.000.000/0001-00
APP_PROOF_RETENTION_DAYS=180

# Observabilidade (ver seção 13)
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus,loggers
APP_LOG_LEVEL_ROOT=INFO
APP_LOG_LEVEL_APP=DEBUG
APP_LOG_FORMAT=json
```

---

## 12. LGPD (Lei 13.709/2018)

- **Controlador**: `APP_CONTROLLER_NAME` / `APP_CONTROLLER_CNPJ`.
- **Operadores** (Art. 39) — listados em `docs/lgpd/operators.md` com finalidade e contrato:
  - Dokploy (hosting da aplicação).
  - PostgreSQL (banco de dados, no servidor Dokploy).
  - MinIO (storage de arquivos, no servidor Dokploy).
  - Bot WhatsApp do Paulo (envio de mensagens operacionais).
  - GitHub (repositório de código — sem dado pessoal de morador).
- **ROPA** (Record of Processing Activities, Art. 37) materializado em `docs/lgpd/ropa.md` listando: autenticação, comprovante, classificados, recomendações, WhatsApp outbound, logs, backups — com finalidade, base legal, retenção, operadores envolvidos.
- **Base legal** documentada por tratamento (cadastro=execução de contrato; comprovante=execução de contrato; logs=legítimo interesse com LIA; classificados/recomendações=consentimento; WhatsApp comunicação operacional=execução de contrato; WhatsApp marketing futuro=consentimento específico).
- **Termo de privacidade** versionado em `consent_document`; aceite registrado no cadastro com IP+timestamp; reaceite obrigatório em nova versão (`409 CONSENT_REQUIRED`).
- **Telefone para WhatsApp**: dado pessoal coletado com base legal "execução de contrato" para autenticação e reset. **Checkbox separado obrigatório** no wizard: "Aceito receber comunicações operacionais via WhatsApp" — gera `whatsapp_opt_in` + `whatsapp_opt_in_at` na tabela `user` (revogável em `PUT /users/me`). Sem opt-in, ainda recebe reset de senha (contrato) mas nenhum aviso/notificação.
- **Consentimento do residente indicado** em recommendation com `is_resident=true`: recomendação fica `status=PENDING_RESIDENT_CONSENT` (campo novo) até o residente aprovar; sem aprovação, oculta na listagem pública. Endpoint `POST /api/recommendations/{id}/resident-consent` (autenticado pelo residente) muda para `ACTIVE`. Evita exposição de profissional residente sem permissão.
- **DPIA** (Art. 38) para tratamento de comprovante de residência: documento `docs/lgpd/dpia-residence-proof.md` avalia risco (re-identificação, vazamento de PII), medidas (bucket isolado, TTL, retenção 180d, audit log) e residual.
- **Retenção**:
  - Comprovante: purgado do MinIO `APP_PROOF_RETENTION_DAYS` (180) dias após `approved_at`. Job `@Scheduled` diário.
  - Refresh tokens revogados/vencidos: 90 dias.
  - Password reset tokens usados/expirados: 30 dias.
  - WhatsApp outbox: 90 dias.
  - Conta inativa 12 meses: notifica e anonimiza.
- **Direitos do titular**:
  - `GET /api/privacy/me/export`.
  - `POST /api/privacy/me/anonymize` (double-opt + senha). Anonimiza usuário, purga comprovante do MinIO, preserva entidades históricas com autor "Usuário Removido".
- **DPO**: `APP_DPO_EMAIL` exibido em `/privacidade`. Canal de requisições com SLA 15 dias.
- **Auditoria**: `proof_access_log` (comprovantes) + `sensitive_access_log` (acessos por COUNCIL/STAFF/MANAGER a dados de outros titulares) + `user_permission_grant_log` (concessões de permissão) + logs JSON com requestId/userId.
- **Endpoint `GET /api/privacy/me/processing-activities`** lista todas as finalidades + bases legais + operadores que tocam dados do titular logado (Art. 9º — direito à informação).
- **Incidente**: alertas Prometheus + procedimento em `docs/security/incident-response.md`.

---

## 13. Observabilidade (logs em nível de produção)

### 13.1 Logs estruturados

- Formato JSON em produção (`APP_LOG_FORMAT=json`), texto colorido em dev.
- Library: `logstash-logback-encoder` ou `logback-spring.xml` com encoder JSON nativo do Spring Boot 3.
- **MDC** populado por `MdcFilter` (filtro Servlet) em cada request:
  - `requestId` (UUID, header `X-Request-Id` se cliente enviar, gerado caso contrário, ecoado no response).
  - `userId` (do `SecurityContextHolder` após auth).
  - `unitId`, `roles` (csv).
  - `clientIp` (com X-Forwarded-For respeitando proxy Traefik).
  - `userAgent` (resumido).
- Cada log line carrega esses campos automaticamente.

### 13.2 Níveis e categorias

- `INFO` — eventos de negócio: login OK, register-master, approve, reject, classified.create, recommendation.create, password.reset.requested/consumed, faq.publish.
- `WARN` — falhas previsíveis: login falho, rate-limit acionado, WhatsApp send falhou (com retry), magic-bytes inválido no upload, optimistic lock.
- `ERROR` — exceções inesperadas; sempre com stack; nunca com PII no message.
- `DEBUG` — detalhes de fluxo só em dev.

### 13.3 Métricas (Micrometer + Prometheus)

`/actuator/prometheus` exposto. Tags por endpoint, status, role.

Métricas custom:
- `auth.login.success{role}`, `auth.login.failure{reason}` (counter).
- `auth.lockout.triggered` (counter).
- `registration.submitted`, `registration.approved`, `registration.rejected` (counter).
- `whatsapp.send.attempt`, `whatsapp.send.success`, `whatsapp.send.failure{reason}` (counter).
- `password.reset.requested`, `password.reset.consumed` (counter).
- `proof.url.accessed` (counter, tag `admin_user_id`).
- `upload.size.bytes{bucket}` (distribution summary).
- `classifieds.active`, `recommendations.active`, `pending.registrations` (gauge).
- HikariCP, JVM, HTTP server timings (built-in).

### 13.4 Tracing

Out-of-scope no MVP; deixar `micrometer-tracing` como dep com `tracing.enabled=false`. Quando ativar (futuro), basta env.

### 13.5 Alertas (Prometheus) recomendados

- > 50 falhas de login em 5 min → possível ataque.
- > 20 acessos a `/proof-url` por admin/hora → revisão manual.
- WhatsApp success rate < 80% em 1h.
- Pool HikariCP > 80% por > 5 min.
- Healthcheck failing.

### 13.6 Logs nunca incluem PII

Auditoria de PII vs logs: `full_name`, `email`, telefone bruto, conteúdo do comprovante e do classificado **não** vão para o log. Para troubleshooting, registrar `userId`/`unitId`/`registrationId`. Wrapper `LogSanitizer` mascara campos sensíveis (`***`).

### 13.7 Retenção de logs

- App stdout → Dokploy coleta → 30 dias.
- Métricas Prometheus → 90 dias.
- `proof_access_log` (DB) → indefinido (auditoria legal).

---

## 14. Riscos e mitigações

| Risco                                          | Mitigação                                                                                       |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Dump do banco vaza hashes                      | Pepper fora do banco.                                                                           |
| Comprovante vaza                               | Bucket isolado, TTL curto, `Referrer-Policy: no-referrer`, retenção limitada.                   |
| XSS rouba refresh                              | Cookie HttpOnly+Secure+SameSite=Strict; access só em memória.                                   |
| Replay de refresh                              | Rotação atômica + token_family revogada.                                                        |
| Bot WhatsApp comprometido                      | HMAC do payload; webhook autenticado; idempotência no consume-reset; rate limit no request.    |
| Phishing via link de reset                     | Token de uso único, TTL 30min, mensagem WhatsApp identifica o serviço claramente.              |
| Concorrência em aprovação                      | `@Version` + transição guardada.                                                                |
| AdminBootstrap racey                           | UPDATE condicional atômico.                                                                     |
| Upload abusivo                                 | Rate limit + magic-bytes + tamanho + tipos.                                                     |
| Escalada de privilégio                         | RESIDENT hardcoded no auto-registro.                                                            |
| Foto pesada em mobile lento                    | Compressão client-side ≤1MB; lazy load; thumbnails.                                             |
| Tags spam                                      | Moderação por TAG_MANAGE; rate limit em POST /tags.                                                     |
| FAQ desatualizada                              | Campo `published`; admin pode despublicar rapidamente; histórico em soft delete.                |
| LGPD — base legal                              | Tabela `consent_document` + colunas no `user`.                                                  |
| LGPD — esquecimento                            | `User.anonymize()` + remoção física do comprovante.                                             |
| Crescimento sem limite                         | Jobs `@Scheduled` de limpeza diários.                                                           |
| Logs com PII                                   | `LogSanitizer` + revisão na entrega.                                                            |

---

## 15. Próximo passo

Após aprovação deste documento, invocar `superpowers:writing-plans` para gerar o plano executável. O `CLAUDE.md` raiz será criado com convenções:

- SOLID/KISS/STRIDE/POO obrigatórios.
- npm no frontend.
- Soft delete sempre, exceto: `user_role`, `user_permission_grant_log`, `role_permission`, `*_opening_hours`, `recommendation_tag`, `sensitive_access_log`, `proof_access_log` (M:N puros e logs imutáveis).
- `@SQLDelete`+`@SQLRestriction` (Hibernate 6) **não filtram** queries nativas; **nunca** `CascadeType.REMOVE`.
- Lombok: nunca `@Data` em entidade JPA; `@EqualsAndHashCode(of="id")` + setters protegidos.
- `AuditorAware` retorna `Optional.empty()` em contexto público (sem auth no SecurityContext).
- Idioma: código EN, UI pt-BR.
- Package-by-feature.
- `@Transactional` apenas em service; upload S3 **fora** da transação.
- Eventos com `@TransactionalEventListener(AFTER_COMMIT) + @Async`.
- Spring Security: `hasAuthority('<PERMISSION>')` — proibido `hasRole('STAFF')`.
- Logs nunca expõem PII (`LogSanitizer`).
- Comunicação outbound apenas WhatsApp; jamais e-mail.
- Compressão de imagem sempre no client (≤1MB foto, ≤5MB comprovante).
- Trunk-based: PR ≤400 linhas, branch ≤2 dias, feature flag para WIP, migrations expand/contract.
- Mudança de feature flag em prod registrada em issue GitHub.
- Proibido restaurar dump de prod em HML — HML usa seed sintético.
