# Plano 2C — Reset de senha via WhatsApp + LGPD self-service

> **STATUS:** ✅ **DEPLOYED em HML e validado e2e** em 2026-05-28. Todo backend (PR 2C-1 + 2C-2: WhatsApp client com HMAC + Resilience4j, outbox + retry scheduler, PasswordResetService, PrivacyService com export/anonymize/processing-activities, 3 retention schedulers) + frontend (PR 2C-3: ForgotPasswordPage, ResetPasswordPage, PrivacyPage) estão no `main`. 45 testes backend passando. Smoke das 6 rotas SPA em HML retornou 200; bundle JS contém todas as strings esperadas (`Esqueci minha senha`, `ANONIMIZAR`, `request-reset`, `consume-reset`, `processing-activities`, `whatsapp-opt-in`). Task 14 (e2e full do reset com bot real) fica out-of-scope deste plano e depende de (1) `APP_WHATSAPP_WEBHOOK_URL` no backend-hml apontar pro bot do Paulo (hoje aponta pra placeholder), (2) um usuário em HML com `phone_verified_at != null`. **[ATUALIZAÇÃO 2026-06-03]** A abordagem do "bot do Paulo" via webhook HMAC foi substituída por integração direta com o **Evolution API** — ver `## Addendum 2026-06-03` no fim deste arquivo (PR 2C-4), que reescreve o transporte e fecha a Task 14. Sem isso o request-reset cria o token mas o envio cai em outbox FAILED — o scheduler reprocessa. Issue out-of-scope: InactiveAccountScheduler (anonimizar contas 12m sem login) — User não tem `last_login_at` ainda; precisa migration adicional.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute this plan task-by-task. Marcar `[x]` SÓ depois do e2e em HML passar (per [[feedback-validate-e2e]]).

**Goal:** Fechar o ciclo de autenticação com reset de senha via WhatsApp (sem e-mail, per CLAUDE.md) e implementar os direitos LGPD do titular (export, anonymize, processing-activities, revogar opt-in WhatsApp). Inclui infra de notificações outbound com outbox idempotente, retry resiliente e HMAC anti-replay para o webhook do bot do Paulo, mais jobs de retenção (purga de comprovante após 180d, limpeza de tokens, anonimização de contas inativas 12m).

**Architecture:** Continua package-by-feature. Esta fase introduz `feature/password/` (request-reset + consume-reset + history), `feature/whatsapp/` (notification client + outbox + scheduled job), `feature/privacy/` (LGPD endpoints + anonymization), e `feature/retention/` (jobs scheduled). Eventos de domínio (`PasswordResetRequestedEvent`, `PasswordResetCompletedEvent`, `UserAnonymizedEvent`) disparados após commit, listeners `@Async`. WhatsApp via `WebClient` com `Resilience4j` (retry+circuit-breaker), payload assinado HMAC-SHA256 com `kid` rotacionável e `timestamp`+`jti` anti-replay.

**Tech Stack:** Spring Boot 3.3.5 (mantido), `spring-boot-starter-webflux` (WebClient — verificar se já está no pom), `io.github.resilience4j:resilience4j-spring-boot3:2.2.0` + `resilience4j-reactor:2.2.0`, `spring-boot-starter-quartz` OU `@Scheduled` simples (default — Quartz só se precisar de cluster scheduling).

**Spec base:** `docs/superpowers/specs/2026-05-24-gestor-condominio-design.md` seções 2 (`password_reset_token`, `whatsapp_outbox`), 4.4 (fluxo reset), 4.5 (domínio rico — `PasswordResetToken.consume()`, `User.anonymize()`, `User.changePassword()`, `User.rehashPassword()`), 6.2 (UI reset), 12 (LGPD), 13.3 (métricas custom de auth e whatsapp).

**Pré-requisito:** Plano 2B concluído (registro + moderação funcionando — atendido). Branch `main` limpa. `MINIO_*` configurado em HML (atendido). Env vars `APP_WHATSAPP_*` e `APP_PASSWORD_RESET_*` no Dokploy do backend (já estão em `.env.prod.local` template, falta replicar em HML).

**Out-of-scope** (planos futuros):
- WhatsApp **inbound** (receber respostas do morador) — só outbound nesta fase.
- Templates de notificação para classifieds/recommendations/moderação (essas funcionalidades vêm em plano 3).
- Tracing distribuído (já fica como dep desligada em `application.yml`).
- Reset 2FA / TOTP.

---

## File Structure

```
backend/
├── pom.xml                                          (Task 1 — deps resilience4j + webflux se faltar)
├── src/main/resources/
│   ├── application.yml                              (Task 1 — config resilience4j + scheduling)
│   └── db/migration/
│       └── V13__whatsapp_outbox.sql                 (Task 1)
└── src/main/java/br/com/condominio/
    ├── feature/
    │   ├── password/
    │   │   ├── PasswordResetToken.java              (Task 2 — entity + consume())
    │   │   ├── PasswordResetTokenRepository.java    (Task 2)
    │   │   ├── PasswordHistory.java                 (Task 2 — entity p/ V4 password_history)
    │   │   ├── PasswordHistoryRepository.java       (Task 2)
    │   │   ├── PasswordResetService.java            (Task 5 — TDD)
    │   │   ├── PasswordResetController.java         (Task 6)
    │   │   ├── PasswordResetException.java          (Task 5)
    │   │   ├── event/
    │   │   │   ├── PasswordResetRequestedEvent.java (Task 7)
    │   │   │   └── PasswordResetCompletedEvent.java (Task 7)
    │   │   └── dto/
    │   │       ├── RequestResetRequest.java         (Task 6)
    │   │       └── ConsumeResetRequest.java         (Task 6)
    │   ├── whatsapp/
    │   │   ├── WhatsAppNotificationClient.java      (Task 3 — WebClient + HMAC)
    │   │   ├── WhatsAppProperties.java              (Task 3 — @ConfigurationProperties)
    │   │   ├── WhatsAppOutboxEntry.java             (Task 2 — entity)
    │   │   ├── WhatsAppOutboxRepository.java        (Task 2)
    │   │   ├── WhatsAppOutboxService.java           (Task 4 — enqueue/markSent/markFailed)
    │   │   ├── WhatsAppRetryScheduler.java          (Task 8 — @Scheduled reprocess)
    │   │   ├── PasswordResetEventListener.java      (Task 7 — @TransactionalEventListener AFTER_COMMIT)
    │   │   └── dto/
    │   │       ├── WhatsAppSendPayload.java         (Task 3 — body: to, template, data, timestamp, jti)
    │   │       └── WhatsAppTemplate.java            (Task 3 — enum: PASSWORD_RESET, PASSWORD_CHANGED)
    │   ├── privacy/
    │   │   ├── PrivacyController.java               (Task 10)
    │   │   ├── PrivacyService.java                  (Task 9 — TDD)
    │   │   ├── ProcessingActivitiesProvider.java    (Task 9 — lista estática vinda de docs/lgpd/)
    │   │   └── dto/
    │   │       ├── PersonalDataExportResponse.java  (Task 9 — JSON com tudo do user)
    │   │       ├── AnonymizeRequest.java            (Task 10 — { currentPassword, confirmText })
    │   │       └── ProcessingActivityView.java      (Task 9)
    │   ├── user/
    │   │   ├── User.java                            (Task 11 — adiciona anonymize() method)
    │   │   └── UserController.java                  (Task 11 — PUT /api/users/me/whatsapp-opt-in)
    │   └── retention/
    │       ├── ProofRetentionScheduler.java         (Task 12 — purga > 180d)
    │       ├── RefreshTokenCleanupScheduler.java    (Task 12 — 90d)
    │       ├── WhatsAppOutboxCleanupScheduler.java  (Task 12 — 90d)
    │       └── InactiveAccountScheduler.java        (Task 12 — anonimiza 12m)
    └── src/test/java/br/com/condominio/feature/
        ├── password/PasswordResetServiceTest.java   (Task 5 — TDD)
        ├── whatsapp/WhatsAppNotificationClientTest.java (Task 3 — MockWebServer)
        └── privacy/PrivacyServiceTest.java          (Task 9 — TDD)

frontend/
└── src/
    ├── features/auth/pages/
    │   ├── ForgotPasswordPage.tsx                   (Task 13)
    │   └── ResetPasswordPage.tsx                    (Task 13)
    ├── features/privacy/
    │   ├── pages/PrivacyPage.tsx                    (Task 13)
    │   └── api/privacyApi.ts                        (Task 13)
    └── router.tsx                                   (Task 13 — adiciona rotas /forgot-password, /reset?token=, /privacidade)

docs/lgpd/
├── operators.md                                     (Task 9 — referência citada em ProcessingActivitiesProvider)
└── ropa.md                                          (Task 9 — base p/ /processing-activities)
```

---

## Convenções (mantidas dos planos anteriores)

- Branch: `feat/whatsapp-lgpd` a partir de `main`.
- TDD onde fizer sentido (services com lógica de domínio).
- Commits Conventional. Pre-push hook valida testes. Sem `--no-verify`.
- Working dir: `D:/Projetos/gestor-condominio`.
- Lombok: nunca `@Data` em entidade JPA. Setters `PROTECTED`.
- `@Transactional` apenas em service.
- Lógica de domínio em métodos do agregado (`User.anonymize()`, `PasswordResetToken.consume()`).
- Logs nunca com PII (e-mail, telefone bruto, senha). `LogSanitizer`.
- Migrations forward-only; header `-- flyway:transactional=true`. Tipos nativos do Postgres mapeados como String → use `text`, não `inet`/`citext` per [[feedback-jpa-ddl-auto]].

---

## Task 1: Setup branch + deps + V13 migration (whatsapp_outbox)

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V13__whatsapp_outbox.sql`

- [ ] **Step 1: Branch**
```bash
cd D:/Projetos/gestor-condominio
git checkout main && git pull --ff-only
git checkout -b feat/whatsapp-lgpd
```

- [ ] **Step 2: pom.xml — adicionar deps** (antes de `</dependencies>`):
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot3</artifactId>
  <version>2.2.0</version>
</dependency>
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-reactor</artifactId>
  <version>2.2.0</version>
</dependency>
```
Conferir se webflux já não está; se sim, manter só resilience4j.

- [ ] **Step 3: application.yml — config Resilience4j e scheduling**

Dentro de `spring:` (raiz, depois de `flyway:`):
```yaml
  task:
    scheduling:
      pool:
        size: 4
```

No final do arquivo (após `server:`):
```yaml
resilience4j:
  timelimiter:
    instances:
      whatsapp:
        timeoutDuration: ${APP_WHATSAPP_TIMEOUT_MS:5000}ms
  retry:
    instances:
      whatsapp:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
  circuitbreaker:
    instances:
      whatsapp:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s

app:
  whatsapp:
    webhook-url: ${APP_WHATSAPP_WEBHOOK_URL:http://localhost:9999/send-message}
    hmac-keys: ${APP_WHATSAPP_HMAC_KEYS:v1:placeholder-base64-32-bytes==}
    hmac-active-kid: ${APP_WHATSAPP_HMAC_ACTIVE_KID:v1}
    timeout-ms: ${APP_WHATSAPP_TIMEOUT_MS:5000}
    max-retries: ${APP_WHATSAPP_MAX_RETRIES:5}
  password-reset:
    ttl: ${APP_PASSWORD_RESET_TTL:PT30M}
    base-url: ${APP_PASSWORD_RESET_BASE_URL:http://localhost:5173/reset}
  proof-retention-days: ${APP_PROOF_RETENTION_DAYS:180}
  inactive-account-anonymize-months: ${APP_INACTIVE_ACCOUNT_MONTHS:12}
```

- [ ] **Step 4: V13 migration**
```sql
-- flyway:transactional=true

CREATE TABLE whatsapp_outbox (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    to_phone        varchar(20) NOT NULL,
    template        varchar(60) NOT NULL,
    payload         jsonb NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'PENDING',
    attempts        smallint NOT NULL DEFAULT 0,
    last_attempt_at timestamptz,
    error_message   text,
    sent_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    deleted_by_user_id uuid REFERENCES "user" (id),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','SENT','FAILED'))
);

CREATE INDEX idx_outbox_pending ON whatsapp_outbox (created_at)
    WHERE status IN ('PENDING','FAILED') AND deleted_at IS NULL;
CREATE INDEX idx_outbox_status ON whatsapp_outbox (status, created_at)
    WHERE deleted_at IS NULL;
```

- [ ] **Step 5: Validar build local**
```bash
cd backend && ./mvnw -B -q clean compile
```

- [ ] **Step 6: Commit**
```bash
git add backend/pom.xml backend/src/main/resources/
git commit -m "build(2c): deps resilience4j + webflux; V13 whatsapp_outbox; config app/resilience"
```

---

## Task 2: Entities — PasswordResetToken, PasswordHistory, WhatsAppOutboxEntry

**Files:** Create entities + repositories. Soft delete via `@SQLDelete` + `@SQLRestriction` per CLAUDE.md (não aplica em `password_reset_token` pois é histórico imutável que será purgado por job; aplica em `whatsapp_outbox`).

**Step 1: `PasswordResetToken.java`** — domínio rico com `consume()`. Mapear: `id`, `userId`, `tokenHash`, `expiresAt`, `usedAt`, `createdIp`, `deliveredAt`, `createdAt`. Métodos:
- `boolean isUsable(Instant now)` — `usedAt == null && now.isBefore(expiresAt)`
- `void consume(Instant now)` — valida `isUsable`, set `usedAt = now`, lança `IllegalStateException` se já usado
- `void markDelivered(Instant now)` — set `deliveredAt = now` (idempotência)

**Step 2: `PasswordResetTokenRepository.java`** — Spring Data:
- `Optional<PasswordResetToken> findByTokenHash(String hash)`
- `@Modifying @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL") int invalidateAllUserTokens(...)` (chamado antes de criar novo, per spec 4.4 passo 3)
- `List<PasswordResetToken> findAllByUsedAtIsNotNullOrExpiresAtBefore(Instant cutoff)` (p/ retention job)

**Step 3: `PasswordHistory.java`** + repo — entity simples, queries `findTop5ByUserIdOrderByCreatedAtDesc(userId)` p/ User.changePassword validar.

**Step 4: `WhatsAppOutboxEntry.java`** + repo — soft delete. Status enum `OutboxStatus { PENDING, SENT, FAILED }`. Métodos:
- `markSent(Instant now)`, `markFailed(String error, Instant now)`, `recordAttempt(Instant now)`.
- repo: `Page<WhatsAppOutboxEntry> findRetryable(int maxAttempts, Pageable page)` com `@Query` filtrando `status IN (PENDING, FAILED) AND attempts < :maxAttempts`.

- [ ] Compile + spotless + commit `feat(2c): entities PasswordResetToken/PasswordHistory/WhatsAppOutboxEntry com dominio rico`.

---

## Task 3: WhatsAppNotificationClient + HMAC + Resilience4j (TDD com MockWebServer)

**Files:**
- Create: `WhatsAppProperties.java`, `WhatsAppNotificationClient.java`, `WhatsAppSendPayload.java`, `WhatsAppTemplate.java`.
- Create test: `WhatsAppNotificationClientTest.java` com `okhttp3.mockwebserver.MockWebServer`.

**Step 1: Teste primeiro** — `WhatsAppNotificationClientTest`:
- Test: `sendPasswordReset envia POST com X-Signature, X-Hmac-Kid, e body com {to,template,data,timestamp,jti}`.
- Test: `signature bate com HMAC-SHA256 do body usando chave kid ativa`.
- Test: `bot retorna 200 → client retorna SENT`.
- Test: `bot retorna 5xx → client lança WhatsAppSendException`.
- Test: `kid invalido em hmac-keys causa IllegalStateException no startup`.

**Step 2: Implementação:**
- `WhatsAppProperties` (`@ConfigurationProperties(prefix="app.whatsapp")`) com parsing de `hmacKeys` em `Map<String, byte[]>` (formato `v1:base64,v2:base64`).
- `WhatsAppNotificationClient` recebe `WebClient`, `WhatsAppProperties`, `ObjectMapper`. Método público `Mono<Void> sendPasswordReset(String toPhone, String resetLink)` cria payload, serializa JSON canônico (chaves ordenadas), calcula HMAC, envia POST com headers `X-Signature: sha256=<hex>`, `X-Hmac-Kid: v1`, `Content-Type: application/json`. Anotado com `@TimeLimiter("whatsapp") @Retry("whatsapp") @CircuitBreaker(name="whatsapp", fallbackMethod="fallback")`. Fallback: log WARN sem propagar (caller é listener async).

- [ ] TDD test → fail, implement → pass, spotless, commit.

---

## Task 4: WhatsAppOutboxService (enqueue + markSent + markFailed)

**Files:** Create `WhatsAppOutboxService.java` + test.

Service injeta `WhatsAppOutboxRepository` e `ObjectMapper`. Métodos:
- `WhatsAppOutboxEntry enqueue(String toPhone, WhatsAppTemplate template, Map<String,Object> data)` — cria PENDING, persiste, retorna.
- `void markSent(UUID id, Instant now)` — set `status=SENT`, `sent_at=now`.
- `void markFailed(UUID id, String reason, Instant now)` — set `status=FAILED`, increment `attempts`, set `last_attempt_at`, `error_message`.
- `Page<WhatsAppOutboxEntry> listRetryable(int maxAttempts, int pageSize)`.

Testes com `@DataJpaTest` ou unit com mock do repo.

- [ ] Commit `feat(2c): WhatsAppOutboxService (enqueue/markSent/markFailed) com TDD`.

---

## Task 5: PasswordResetService (TDD)

**Files:** Create `PasswordResetService.java` + `PasswordResetException.java` + test.

**Lógica:**
- `requestReset(String emailOrLogin, String clientIp)`:
  - Sempre retorna `void` (resposta 202 vem do controller, idempotente).
  - Busca user case-insensitive via `UserEmailRepository.findActiveByEmailIgnoreCase`.
  - Se user não existe, OU `status != ACTIVE`, OU `phone_verified_at == null` → **return silenciosamente** (não vaza existência). Mas registra métrica `password.reset.requested{outcome=ignored,reason=...}`.
  - Invalida tokens anteriores: `tokenRepo.invalidateAllUserTokens(userId, now)`.
  - Gera token: `byte[32] = SecureRandom`, base64-url-encode → `rawToken`. SHA-256 → `tokenHash`.
  - Persiste `PasswordResetToken` com `expires_at = now + props.ttl`, `created_ip = clientIp`.
  - Publica `PasswordResetRequestedEvent(userId, tokenId, rawToken, userPhone)` — `rawToken` só vive nesse evento, nunca persistido em texto.
- `consumeReset(String rawToken, String newPassword, String clientIp)`:
  - SHA-256 do `rawToken` → busca via `findByTokenHash`. Se não acha ou `!isUsable(now)` → lança `PasswordResetException("INVALID_OR_EXPIRED_TOKEN")` (mensagem genérica, 400).
  - Carrega User, chama `user.changePassword(newPassword, encoder, passwordHistoryRepo.findTop5ByUserIdOrderByCreatedAtDesc(userId))`.
  - `token.consume(now)`.
  - Persiste `PasswordHistory` (snapshot do novo hash + pepperVersion + now).
  - Revoga todos refresh tokens do usuário (`refreshTokenRepo.revokeAllByUserId(userId)` — método novo se não existe).
  - Publica `PasswordResetCompletedEvent(userId, userPhone)`.

**Testes TDD** (cobre):
- `requestReset não vaza existência (user inexistente → return void sem erro)`.
- `requestReset com user ACTIVE e phone_verified gera token + publica evento`.
- `requestReset invalida tokens anteriores`.
- `consumeReset com token inválido → lança INVALID_OR_EXPIRED_TOKEN`.
- `consumeReset com token expirado → INVALID_OR_EXPIRED_TOKEN`.
- `consumeReset com token já usado → INVALID_OR_EXPIRED_TOKEN`.
- `consumeReset sucesso: muda senha, marca usado, revoga refresh tokens, publica evento`.
- `consumeReset com nova senha igual a uma das últimas 5 → lança PASSWORD_REUSED`.

- [ ] TDD: testes → fail, implement → pass, spotless, commit.

---

## Task 6: PasswordResetController + SecurityConfig permitAll

**Files:** Create `PasswordResetController.java`, dto `RequestResetRequest.java`, `ConsumeResetRequest.java`. Modify `SecurityConfig.java` para `permitAll` em `/api/auth/password/**`.

```java
@RestController
@RequestMapping("/api/auth/password")
@RequiredArgsConstructor
public class PasswordResetController {
  private final PasswordResetService service;

  @PostMapping("/request-reset")
  public ResponseEntity<Void> request(@Valid @RequestBody RequestResetRequest req, HttpServletRequest http) {
    service.requestReset(req.email(), resolveIp(http));
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/consume-reset")
  public ResponseEntity<Void> consume(@Valid @RequestBody ConsumeResetRequest req, HttpServletRequest http) {
    service.consumeReset(req.token(), req.newPassword(), resolveIp(http));
    return ResponseEntity.noContent().build();
  }
}
```

SecurityConfig: `.requestMatchers(HttpMethod.POST, "/api/auth/password/**").permitAll()`.

`PasswordResetException` mapeada em `GlobalExceptionHandler` → 400 `BAD_REQUEST` com `code` do exception.

- [ ] Commit `feat(2c): POST /api/auth/password/{request,consume}-reset`.

---

## Task 7: Events + listeners (`@TransactionalEventListener AFTER_COMMIT + @Async`)

**Files:**
- `event/PasswordResetRequestedEvent.java`, `event/PasswordResetCompletedEvent.java` (records).
- `feature/whatsapp/PasswordResetEventListener.java`.
- `shared/async/AsyncConfig.java` (define `whatsappTaskExecutor` bean — `ThreadPoolTaskExecutor` core=2 max=4 queue=100).
- `GestorCondominioApplication.java` — adicionar `@EnableAsync`.

`PasswordResetEventListener`:
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("whatsappTaskExecutor")
public void on(PasswordResetRequestedEvent e) {
  PasswordResetToken token = tokenRepo.findById(e.tokenId()).orElse(null);
  if (token == null || token.getDeliveredAt() != null) return; // idempotência
  String link = props.buildResetLink(e.rawToken());
  WhatsAppOutboxEntry entry = outboxService.enqueue(e.phone(), WhatsAppTemplate.PASSWORD_RESET,
      Map.of("greetingName", e.greetingName(), "link", link, "ttlMinutes", props.ttl().toMinutes()));
  client.sendPasswordReset(e.phone(), link)
      .doOnSuccess(v -> { outboxService.markSent(entry.getId(), Instant.now());
                          tokenRepo.markDelivered(token.getId(), Instant.now()); })
      .doOnError(err -> outboxService.markFailed(entry.getId(), err.getMessage(), Instant.now()))
      .subscribe();
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("whatsappTaskExecutor")
public void on(PasswordResetCompletedEvent e) {
  WhatsAppOutboxEntry entry = outboxService.enqueue(e.phone(), WhatsAppTemplate.PASSWORD_CHANGED,
      Map.of("greetingName", e.greetingName()));
  client.sendPasswordChanged(e.phone()).doOnSuccess(v -> outboxService.markSent(entry.getId(), Instant.now()))
                                       .doOnError(err -> outboxService.markFailed(entry.getId(), err.getMessage(), Instant.now()))
                                       .subscribe();
}
```

- [ ] Commit `feat(2c): listeners async AFTER_COMMIT p/ PasswordReset* events`.

---

## Task 8: WhatsAppRetryScheduler (`@Scheduled` reprocessa outbox PENDING/FAILED)

**Files:** Create `WhatsAppRetryScheduler.java`.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppRetryScheduler {
  private final WhatsAppOutboxService outboxService;
  private final WhatsAppNotificationClient client;
  private final WhatsAppProperties props;

  @Scheduled(fixedDelayString = "${app.whatsapp.retry-interval-ms:60000}")
  public void process() {
    var page = outboxService.listRetryable(props.maxRetries(), 50);
    for (var entry : page) {
      // chama client.send genérico baseado em entry.getTemplate() + payload
      // marca sent/failed conforme resultado
    }
  }
}
```

Lock distribuído: usar `ShedLock` (`net.javacrumbs.shedlock:shedlock-spring`) **se** o backend tiver mais de uma réplica em prod. Por enquanto `replicas=1` (per config Dokploy) — sem lock. Documentar em comentário no scheduler.

- [ ] Compile + commit `feat(2c): WhatsAppRetryScheduler reprocessa outbox`.

---

## Task 9: PrivacyService — export + processing-activities (TDD)

**Files:**
- `PrivacyService.java`, `ProcessingActivitiesProvider.java`, dtos.
- `docs/lgpd/operators.md`, `docs/lgpd/ropa.md` (texto base).
- `PrivacyServiceTest.java`.

**`exportSelf(userId)`** retorna `PersonalDataExportResponse` JSON com:
- User core: id, fullName, greetingName, gender, birthDate, phone, status, createdAt, consentDocumentVersion, consentAcceptedAt, whatsappOptIn, whatsappOptInAt.
- Emails: lista (UserEmail).
- Roles: nomes (não tudo da tabela role).
- Unidade: code, isUnitMaster, blockNumber, floor, position.
- residence_proof: filename, contentType, uploadedAt (não o conteúdo binário — link presigned via `GET /api/privacy/me/proof-url` separado se quiser, ou referência por filename).
- Histórico: registrations status changes (de log se existir), sensitive_access_log onde o user é actor (suas ações), audit timeline.

**`processingActivities(userId)`** retorna lista do `ProcessingActivitiesProvider` (estática) com: finalidade, base legal, dados tratados, retenção, operadores. Conteúdo vem de `docs/lgpd/ropa.md` materializado em código (constants) para ser stable.

**Testes:** `exportSelf inclui apenas dados do user logado` (não vaza outros), `processingActivities retorna lista completa de finalidades`.

- [ ] TDD + commit.

---

## Task 10: PrivacyController + AnonymizeRequest + WhatsApp opt-in toggle

**Files:**
- `PrivacyController.java`.
- `UserController.java` (existe — adicionar `PUT /api/users/me/whatsapp-opt-in`).
- SecurityConfig — todos sob `/api/privacy/me/**` exigem auth (`hasAuthority` não, é self-service de qualquer usuário autenticado).

```java
@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
public class PrivacyController {

  @GetMapping("/me/export")
  public PersonalDataExportResponse export(@AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.exportSelf(me.userId());
  }

  @PostMapping("/me/anonymize")
  public ResponseEntity<Void> anonymize(@AuthenticationPrincipal AuthenticatedUserPrincipal me,
                                         @Valid @RequestBody AnonymizeRequest req) {
    service.anonymizeSelf(me.userId(), req.currentPassword(), req.confirmText());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me/processing-activities")
  public List<ProcessingActivityView> activities(@AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.processingActivities(me.userId());
  }

  @GetMapping("/document/current")
  public ConsentDocumentView currentTerm() { /* já existe em ConsentController, mover/manter */ }
}
```

`AnonymizeRequest`:
```java
public record AnonymizeRequest(@NotBlank String currentPassword,
                                @NotBlank @Pattern(regexp = "ANONIMIZAR") String confirmText) {}
```

**UserController**: adicionar
```java
@PutMapping("/me/whatsapp-opt-in")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<Void> updateOptIn(@AuthenticationPrincipal AuthenticatedUserPrincipal me,
                                         @RequestBody Map<String, Boolean> body) {
  service.updateWhatsappOptIn(me.userId(), body.getOrDefault("optIn", false));
  return ResponseEntity.noContent().build();
}
```

- [ ] Commit `feat(2c): endpoints LGPD self-service (export/anonymize/activities) + toggle whatsapp opt-in`.

---

## Task 11: User.anonymize() + PrivacyService.anonymizeSelf

**Files:** Modify `User.java`. Modify `PrivacyService.java`.

`User.anonymize()`:
- Substitui `fullName="Usuário Removido"`, `greetingName="Usuário Removido"`, `phone=null`, `gender=null`, `birthDate=null`.
- `password_hash="__ANONYMIZED__"` (impede login).
- `status=DELETED` (ou novo `ANONYMIZED` se quiser distinguir — usa `DELETED` por simplicidade).
- Marca `anonymized_at=now`.
- Limpa `residence_proof_object_key`/`filename`/`contentType` (mas mantém `residence_proof_uploaded_at` p/ historicidade).
- Atualiza `UserEmail` para `redacted+{id}@anonymized.local` (mantém unicidade).
- **NÃO** apaga: `proof_access_log`, `sensitive_access_log`, `user_role` (histórico legal).

`PrivacyService.anonymizeSelf(userId, currentPassword, confirmText)`:
- Valida `confirmText.equals("ANONIMIZAR")`.
- Carrega user; verifica senha com `passwordEncoder.matches(currentPassword, user.getPasswordHash())`. Se não bate → 401 `INVALID_PASSWORD`.
- Soft delete: `user.anonymize()`.
- **Purga comprovante do MinIO** (fora da transação, via `@TransactionalEventListener AFTER_COMMIT`): `UserAnonymizedEvent` → listener chama `fileStorage.delete(bucketProofs, objectKey)`.
- Revoga todos refresh tokens.
- Sensitive log: `actor=user, target=user, action=SELF_ANONYMIZE`.

- [ ] Tests + commit.

---

## Task 12: Retention @Scheduled jobs (proof 180d, refresh 90d, outbox 90d, inactive 12m)

**Files:** Create 4 schedulers em `feature/retention/`. Todos `@Scheduled(cron = "...")` rodando 1x/dia 03h00 horário SP.

- `ProofRetentionScheduler` — busca users com `status=ACTIVE AND residence_proof_uploaded_at < now() - 180 days`. Para cada: deleta object key no MinIO, null nos campos do user. Métrica `proof.purged{count}`.
- `RefreshTokenCleanupScheduler` — `DELETE FROM refresh_token WHERE (revoked_at < now() - 90 days) OR (expires_at < now() - 90 days)`. Native query.
- `WhatsAppOutboxCleanupScheduler` — soft delete `whatsapp_outbox` com `created_at < now() - 90 days` (já tem `deleted_at`).
- `InactiveAccountScheduler` — users com `last_login_at < now() - 12 months AND status = ACTIVE`. Para cada: envia WhatsApp aviso 7 dias antes (template `INACTIVITY_WARNING` — adiciona em `WhatsAppTemplate` enum); se passar mais 7d sem login, anonimiza (`user.anonymize()`).

Cron: `cron = "0 0 3 * * *"` (03:00 todo dia, timezone `America/Sao_Paulo` via `@Scheduled` property se configurável, ou usar `ZonedDateTime` internamente).

- [ ] Tests unitários por scheduler + commit `feat(2c): jobs de retencao LGPD (proof/refresh/outbox/inactive)`.

---

## Task 13: Frontend — ForgotPasswordPage, ResetPasswordPage, PrivacyPage

**Files:** novos componentes + rotas + API client.

- `features/auth/api/authApi.ts` (modify) — adicionar `requestPasswordReset(email)` e `consumePasswordReset(token, newPassword)`.
- `features/auth/pages/ForgotPasswordPage.tsx` — form simples com input email, botão "Enviar instruções". Sempre mostra mensagem genérica "Se a conta existir, enviaremos um link via WhatsApp" (não vaza existência).
- `features/auth/pages/ResetPasswordPage.tsx` — lê `?token=` da URL, form com `newPassword` + `confirmPassword`, validação de política (regex composto: ≥10 chars, maiúscula, minúscula, número, especial). POST → redireciona /login com toast sucesso.
- `features/privacy/api/privacyApi.ts` — `exportMyData()`, `getProcessingActivities()`, `anonymizeAccount(currentPassword, confirmText)`, `updateWhatsappOptIn(optIn)`.
- `features/privacy/pages/PrivacyPage.tsx` — tela com 3 sessões: "Suas atividades de tratamento" (lista de `processingActivities`), "Exportar meus dados" (botão que baixa JSON), "Comunicações WhatsApp" (toggle), "Anonimizar minha conta" (botão danger → modal com password + texto de confirmação "ANONIMIZAR").
- `router.tsx` (modify) — rotas `/forgot-password`, `/reset` (lê `?token=`), `/privacidade` (autenticado).

- [ ] Build local `npm run build` no frontend, sem erros TS. Commit `feat(2c-frontend): paginas ForgotPassword/Reset/Privacy + integracoes API`.

---

## Task 14: E2E validation em HML

Per [[feedback-validate-e2e]] — não fechar tasks sem confirmar runtime.

**Pré-condições no Dokploy:**
- Setar `APP_WHATSAPP_*` no `backend-hml-gestao-condominio` (URL bot, HMAC keys — gerar novas pra HML separadas de prod).
- Verificar que o bot do WhatsApp (do Paulo) está acessível pela `dokploy-network` (ou via internet).

**Fluxo E2E:**
1. Criar usuário de teste com `phone_verified_at != null` (via SQL direto ou via fluxo de cadastro do 2B + UPDATE).
2. `POST /api/auth/password/request-reset` com email do test user → 202.
3. Conferir entrada em `whatsapp_outbox` (PENDING ou SENT).
4. Conferir entrada em `password_reset_token` (com `delivered_at` setado após bot enviar).
5. Pegar o `rawToken` (não dá pra recuperar — tem que ser mockado em HML OU usar query direta no DB pra ler o último hash + ter o raw na sessão; melhor: criar teste de integração que captura o evento e expõe o token via header/log de debug).
6. `POST /api/auth/password/consume-reset` → 204; refresh tokens revogados.
7. Login com nova senha → 200.
8. `GET /api/privacy/me/export` autenticado → 200 com JSON do usuário.
9. `GET /api/privacy/me/processing-activities` → 200 com lista.
10. `PUT /api/users/me/whatsapp-opt-in` `{optIn:false}` → 204.
11. `POST /api/privacy/me/anonymize` com senha errada → 401; com senha certa + confirmText `"ANONIMIZAR"` → 204. Verificar user com `status=DELETED`, `full_name="Usuário Removido"`, comprovante apagado do MinIO.
12. Smoke do frontend: páginas `/forgot-password`, `/reset?token=xxx`, `/privacidade` carregam sem erro.

- [ ] Marcar plano como DEPLOYED. Atualizar memory `project-plan-2c-status`.

---

## Riscos & decisões

| Risco | Mitigação |
|---|---|
| Bot WhatsApp do Paulo fora do ar | Fallback Resilience4j + outbox + retry job. Usuário recebe 202 mesmo se bot estiver fora. Job reprocessa. |
| Token vazado em log | `rawToken` só vive no evento `PasswordResetRequestedEvent` (em memória) e no listener síncrono que monta o link. Nunca persistido em texto. `LogSanitizer` deve filtrar campo `token` se aparecer. |
| Anonimização irreversível | Confirmação dupla (senha + texto literal "ANONIMIZAR"). Mensagem clara no frontend. Backup do postgres prod (responsabilidade do ops). |
| Race entre listener síncrono e listener async (outbox PENDING criado antes do evento commitar) | `@TransactionalEventListener(phase=AFTER_COMMIT)` garante que só roda após commit do `PasswordResetService.requestReset`. Outbox entry criado **dentro** do listener, não no service — evita race. |
| Bot recebe mensagens duplicadas se job de retry rodar antes do listener marcar sent | `delivered_at` em `password_reset_token` impede duplo envio do mesmo reset. Para `password_changed` (informativo), duplicata é aceitável. |
| LGPD: anonimização não pode quebrar histórico de moderação | `User.anonymize()` preserva FK e histórico; só substitui PII por placeholder. `proof_access_log` mantém ref ao adminUserId mesmo se admin se anonimizar (rastreio legal). |
| Cron timezone diferente em prod (UTC) vs SP | Schedulers usam `ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"))` para lógica de "passou X dias", não confia no fuso do servidor. |

---

## Resumo de mudanças por área

| Área | Arquivos novos | Arquivos modificados |
|---|---|---|
| Backend deps/config | 1 (V13) | 2 (pom, application.yml) |
| Backend domínio | ~12 entities/services | 2 (User, UserController) |
| Backend tests | 3 TDDs principais | — |
| Frontend | 4 pages + 2 api clients | 2 (router, authApi) |
| Docs LGPD | 2 (operators.md, ropa.md) | — |
| **Migrations** | V13 (whatsapp_outbox) | — |

PR target: ≤400 linhas é inviável neste plano todo. Split em **3 PRs**:
- PR 2C-1: Backend reset de senha (Tasks 1–8) — ~600 linhas
- PR 2C-2: Backend LGPD self-service (Tasks 9–12) — ~500 linhas
- PR 2C-3: Frontend + E2E (Tasks 13–14) — ~400 linhas

Cada PR mergeável independente, mas 2C-3 depende dos endpoints existirem (2C-1 e 2C-2 mergeados primeiro).

---

## Addendum 2026-06-03 — Migração do transporte: "bot do Paulo" → Evolution API direto (PR 2C-4)

> **Contexto:** o transporte WhatsApp do 2C foi escrito para um intermediário hipotético (`bot.paulobof.com.br`) que recebia um POST assinado HMAC + enum de template e renderizava o texto. Na prática o gateway é o **Evolution API** (`evo.paulobof.com.br`, distribuição "Evolution GO", contrato v2), que **não renderiza template** e autentica por **`apikey`**, não HMAC. Este addendum migra o transporte para falar Evolution direto, fechando a dependência da **Task 14** (e2e do reset com bot real). Decisão de arquitetura tomada com o Paulo em 2026-06-03.

**Goal:** Substituir o transporte HMAC→webhook por chamada direta ao Evolution API `sendText`, movendo a renderização dos templates para o backend e normalizando o telefone para o formato que o Evolution exige (dígitos com DDI). Nenhuma outra parte do 2C muda.

**Princípio — só o transporte muda.** Permanecem intactos: `WhatsAppOutboxService`, `WhatsAppOutboxEntry`/`Repository`, `WhatsAppRetryScheduler`, `PasswordResetEventListener` (`@TransactionalEventListener(phase=AFTER_COMMIT)`), `WhatsAppTemplate`, `WhatsAppSendException`, e o Resilience4j (`@CircuitBreaker`+`@Retry`+fallback). A assinatura pública `WhatsAppNotificationClient.send(String toPhone, WhatsAppTemplate, Map<String,Object> data)` **não muda** → nenhum caller é tocado.

### Contrato Evolution GO (confirmado via swagger `/swagger/doc.json` em 2026-06-04)

> **Correção:** o gateway real é o **Evolution GO** (`evoapicloud/evolution-go`), cujo path difere do Evolution API clássico (TS). NÃO é `/message/sendText/{instance}`. É `/send/text`, e a **instância é selecionada pelo `apikey`** (token da instância), não pela URL. Verificado: `GET /instance/status` com o token retorna `{"Connected":true,"LoggedIn":true}`.

```
POST {base-url}/send/text
Headers: apikey: <token-da-instância>, Content-Type: application/json
Body:    { "number": "5511988887777", "text": "<texto já renderizado>" }
Sucesso: 2xx ({"message":"success", ...}). Não-2xx → WhatsAppSendException (gatilha retry/outbox FAILED).
```

### Mudanças

**Removido (HMAC inteiro):** em `WhatsAppNotificationClient` — `sign()`, `signWith()`, `hmacKeys`, `buildSignedBody()`, headers `X-Signature`/`X-Hmac-Kid`. Em `WhatsAppProperties` — `webhookUrl`, `hmacKeys`, `hmacActiveKid`, `antiReplayWindowSeconds`, `parsedHmacKeys()`.

**`WhatsAppProperties` (alterado):** novos campos `baseUrl`, `apiKey`, `instance`. Mantidos: `timeoutMs`, `maxRetries`, `retryIntervalMs`, `timeout()`.

**`WhatsAppMessageRenderer` (novo):** `String render(WhatsAppTemplate, Map<String,Object> data)`. Templates PT-BR (copy aprovada pelo Paulo em 2026-06-03):
- `PASSWORD_RESET` (espera `greetingName`, `link`, `ttlMinutes`):
  > Olá, {greetingName}! 👋\n\nVocê pediu a redefinição da sua senha no HELBOR TRILOGY HOME.\n\nCrie uma nova senha pelo link (válido por {ttlMinutes} min):\n{link}\n\nNão foi você? Pode ignorar esta mensagem.
- `PASSWORD_CHANGED` (espera `greetingName`):
  > Olá, {greetingName}! ✅\n\nSua senha do HELBOR TRILOGY HOME foi alterada com sucesso.\n\nNão reconhece? Fale com a administração imediatamente.
- `INACTIVITY_WARNING` (espera `greetingName`, `daysLeft`):
  > Olá, {greetingName}.\n\nSua conta no HELBOR TRILOGY HOME será anonimizada em {daysLeft} dias por inatividade (LGPD). Faça login para mantê-la ativa.

  Campo ausente em `data` → `WhatsAppSendException` (não envia texto com `null`/`{var}` cru).

**`PhoneNumberNormalizer` (novo):** `String toEvolutionNumber(String raw)`. Heurística BR: remove tudo que não é dígito; se resultado tem 10 ou 11 dígitos (DDD+número, sem DDI) prefixa `55`; se 12 ou 13, assume DDI presente; qualquer outro tamanho → `WhatsAppSendException` com motivo (vira outbox FAILED rastreável).

**`WhatsAppNotificationClient` (reescrito, mesmo nome/assinatura):** `send()` → `number = normalizer.toEvolutionNumber(toPhone)`; `text = renderer.render(template, data)`; `POST {baseUrl}/message/sendText/{instance}` com header `apikey` e body `{number, text}`. Mantém `@CircuitBreaker(name="whatsapp")`+`@Retry(name="whatsapp")`+`sendFallback`. Não-2xx (`WebClientResponseException`) e falha de transporte → `WhatsAppSendException`. `redactPhone()` mantido nos logs (nunca logar telefone/texto cru — CLAUDE.md).

**`application.yml`:**
```yaml
app.whatsapp:
  base-url: ${APP_WHATSAPP_BASE_URL:http://localhost:9999}
  api-key:  ${APP_WHATSAPP_API_KEY:dev-placeholder}
  instance: ${APP_WHATSAPP_INSTANCE:dev}
  timeout-ms: ${APP_WHATSAPP_TIMEOUT_MS:5000}
  max-retries: ${APP_WHATSAPP_MAX_RETRIES:5}
  retry-interval-ms: ${APP_WHATSAPP_RETRY_INTERVAL_MS:60000}
```
Remover as linhas `webhook-url`, `hmac-keys`, `hmac-active-kid`, `anti-replay-window-seconds`.

**`deploy/dokploy-backend.env.example`:** trocar `APP_WHATSAPP_WEBHOOK_URL=...` + HMAC por `APP_WHATSAPP_BASE_URL=https://evo.paulobof.com.br`, `APP_WHATSAPP_API_KEY=<set-no-dokploy>`, `APP_WHATSAPP_INSTANCE=<nome-da-instancia>`.

**`CLAUDE.md`:** seção "Comunicação outbound" — trocar "através do bot do Paulo" por "através do **Evolution API** (`evo.paulobof.com.br`); o **texto é renderizado no backend** (`WhatsAppMessageRenderer`), nunca no gateway". Manter "**Nunca** e-mail".

### Tasks (TDD — marcar `[x]` só após verde; e2e só após HML, per [[feedback-validate-e2e]])

- [ ] **T14.1 — `PhoneNumberNormalizerTest` + `PhoneNumberNormalizer`.** Casos: `+55 11 98888-7777`→`5511988887777`; `11988887777`→`5511988887777`; `1133334444`→`551133334444`; `5511988887777`→inalterado; `551133334444`→inalterado; `"123"`→exceção; `null`/vazio→exceção.
- [ ] **T14.2 — `WhatsAppMessageRendererTest` + `WhatsAppMessageRenderer`.** Um teste por template verificando substituição das variáveis e presença de "HELBOR TRILOGY HOME"; teste de `data` faltando campo → `WhatsAppSendException`.
- [ ] **T14.3 — `WhatsAppProperties`.** Trocar campos (remover HMAC, adicionar baseUrl/apiKey/instance). Sem `parsedHmacKeys()`.
- [ ] **T14.4 — Reescrever `WhatsAppNotificationClientTest`.** Usar o mesmo mock HTTP que o projeto já usa (verificar `pom.xml`: WireMock/MockWebServer/`okhttp mockwebserver`). Asserts: método/URL = `POST .../message/sendText/{instance}`; header `apikey` presente e correto; body JSON = `{number, text}` com number normalizado e text renderizado; resposta 4xx/5xx → `WhatsAppSendException`; remover todos os testes de HMAC/assinatura/jti.
- [ ] **T14.5 — Reescrever `WhatsAppNotificationClient`** até os testes T14.4 passarem (injeta `WhatsAppMessageRenderer` + `PhoneNumberNormalizer`).
- [ ] **T14.6 — `application.yml` + `deploy/dokploy-backend.env.example` + `CLAUDE.md`.** Suite backend completa verde (`./mvnw test`).
- [ ] **T14.7 — Deploy HML + e2e real.** Setar `APP_WHATSAPP_BASE_URL/API_KEY/INSTANCE` no Dokploy do backend-hml (api-key + nome da instância fornecidos pelo Paulo, fora do repo). Usuário HML com `phone_verified_at != null` e telefone real do Paulo. `POST /api/auth/password/request-reset` → confirmar mensagem chegando no WhatsApp e `whatsapp_outbox` = SENT. Marcar Task 14 (e o plano) como concluídos.

### Riscos específicos do addendum

| Risco | Mitigação |
|---|---|
| `apikey` vaza em log | Header nunca logado; `WhatsAppProperties.apiKey` fora de `@ToString`. Logs só com `redactPhone` e nome do template. |
| Telefone sem DDI/ inválido em base legada | `PhoneNumberNormalizer` lança exceção clara → outbox FAILED com motivo; não trava o fluxo (usuário recebeu 202). Auditável depois. |
| Evolution offline / instância desconectada | Resilience4j + outbox + retry job (já existentes) cobrem; mesma garantia do desenho original. |
| Contrato Evolution GO divergir do v2 | Confirmado contrato v2 (`{number,text}`) na doc oficial; T14.7 valida e2e real antes de fechar. |

PR target: **PR 2C-4** (~250–350 linhas, cabe em ≤400).
