# CLAUDE.md — Convenções do projeto gestor-condominio

> Lido automaticamente por agentes de IA (Claude Code etc.) ao iniciar sessões neste repositório.

## Visão geral

Sistema de gestão do **HELBOR TRILOGY HOME**. Spec: `docs/superpowers/specs/2026-05-24-gestor-condominio-design.md`.

## Princípios obrigatórios

- **SOLID, KISS, STRIDE, POO** (domínio rico, não DTOs anêmicos).
- **TDD**: testes primeiro, depois implementação mínima.
- Português (UI), Inglês (código/dados).
- **Trunk-Based**: PR ≤400 linhas, branch ≤2 dias, feature flag para WIP.

## Soft delete

**Sempre** soft delete via `@SQLDelete` + `@SQLRestriction` (Hibernate 6). Hard delete **proibido**, exceto: `user_role`, `user_permission_grant_log`, `role_permission`, `*_opening_hours`, `recommendation_tag`, `sensitive_access_log`, `proof_access_log` (M:N puros e logs imutáveis).

Limitações: `@SQLRestriction` **não filtra** queries nativas nem `JdbcTemplate` — incluir `WHERE deleted_at IS NULL` manualmente. **Nunca** `CascadeType.REMOVE`.

## Lombok em entidades JPA

- **Proibido `@Data`** — causa `LazyInitializationException` e loops em relações.
- Usar `@Getter`, `@Setter(AccessLevel.PROTECTED)`, `@EqualsAndHashCode(of="id")`, `@ToString(onlyExplicitlyIncluded=true)`.

## Spring Security

- Autorização **por permission** com `@PreAuthorize("hasAuthority('<PERMISSION>')")`.
- **Proibido** `hasRole('STAFF')` — STAFF não tem permissions default; uso por role vaza acesso.

## Spring patterns

- `@Transactional` apenas em service; nunca em controller, nunca em entidade.
- Upload S3/MinIO **fora** da transação.
- Eventos: `@TransactionalEventListener(phase=AFTER_COMMIT) + @Async`.
- `AuditorAware` retorna `Optional.empty()` em contexto público.

## Logs

- JSON em prod/hml; texto em dev.
- **Nunca** PII em log (`full_name`, `email`, telefone, comprovante). Usar `LogSanitizer`.
- MDC populado por `MdcFilter`: `requestId`, `userId`, `unitId`, `clientIp`.

## Comunicação outbound

**Apenas via WhatsApp** através do **Evolution API** (`evo.paulobof.com.br`, contrato v2). **Nunca** e-mail. O **texto é renderizado no backend** (`WhatsAppMessageRenderer`), nunca no gateway; o telefone é normalizado para DDI antes do envio (`PhoneNumberNormalizer`). Auth por header `apikey` (não logar). Envio sempre via outbox + retry (`WhatsAppOutboxService` / `WhatsAppRetryScheduler`).

## Uploads

- Compressão **sempre client-side** antes do envio.
- Comprovante de residência ≤5MB. Fotos ≤1MB.
- Magic-bytes check server-side obrigatório.

## Database

- Migrations Flyway sempre **backward-compatible** (expand/contract).
- Cabeçalho `-- flyway:transactional=true` quando compatível.
- Nunca rename/remove no mesmo migration que adiciona.
- `gen_random_uuid()` (pgcrypto) para PKs.

## Frontend

- npm (não yarn/pnpm/bun).
- shadcn/ui + Tailwind + Outfit + Work Sans.
- Mobile-first; touch targets ≥44px; WCAG AA.
- `date-fns-tz` com `America/Sao_Paulo` para qualquer "horário de funcionamento".
- Imagens via `browser-image-compression` antes do upload.

## Feature flags

`@Value("${app.feature.<nome>.enabled:false}")`. Padrão Prod=`false`. Mudança em prod **registrada em issue do GitHub** com `actor`, `flag`, `from`, `to`, `motivo`.

## HML

**Proibido** restaurar dump de prod em HML. HML usa seed sintético (`R__seed_hml_fake_*.sql` + `APP_SEED_FAKE_DATA=true`).

## Commits

Conventional Commits. Squash merge. Hooks: `pre-commit` (lint-staged) + `commit-msg` (commitlint) + `pre-push` (testes back+front). Não usar `--no-verify`.

**Proibido** adicionar co-autor nos commits — sem trailer `Co-Authored-By`, sem mencionar agente de IA como autor.
