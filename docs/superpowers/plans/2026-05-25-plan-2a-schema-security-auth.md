# Plano 2A — Schema + Security + Auth core

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o backbone de identidade do sistema: schema completo (Postgres com `pgcrypto`+`citext`, 522 unidades pré-seedadas, RBAC granular com 5 roles e permissions, password history, refresh tokens, consent, audit logs), infraestrutura de segurança Spring (pepper+bcrypt encoder, JWT HS256 com `kid` e claim `authorities`, refresh em cookie HttpOnly com rotação atômica + replay detection, Bucket4j rate-limit em login), endpoints `/api/auth/login|refresh|logout|me`, e uma página mínima de login no frontend. Ao final do plano, é possível autenticar como admin seedado de HML via frontend ou curl.

**Architecture:** Package-by-feature já estabelecido no Foundation. Esta fase introduz `feature/auth/`, `feature/user/`, `feature/unit/` e `shared/security/`, `shared/audit/`, `shared/observability/`. Migrations Flyway numeradas V1-V6 com schema "additive" (expand/contract). JPA com Hibernate 6 (`@SQLRestriction`, `@SQLDelete`, `@Version`, `AuditingEntityListener` + `AuditorAware<UUID>`). Sem MinIO, sem WhatsApp ainda — esses entram no Plano 2B/2C. Frontend recebe AuthProvider com estado `loading`, axios com cookie e interceptor 401→refresh, e LoginPage shadcn.

**Tech Stack:** Java 21 + Spring Boot 3.3.5 + Spring Security + Spring Data JPA + Flyway 10 + Lombok + Bucket4j 8 + JWT (jjwt 0.12) + Postgres 18-alpine + Passay 1.6 + Vite/React 18/TS/Tailwind + shadcn (`form`, `input`, `button`, `label`, `card`, `sonner`) + axios + @tanstack/react-query + react-hook-form + zod.

**Spec base:** `docs/superpowers/specs/2026-05-24-gestor-condominio-design.md` (seções 1, 2, 3, 4.1–4.7, 11).

**Pré-requisito:** Plano 1 Foundation 100% (tag `v0.1.0-foundation`). HML e Prod com Postgres + MinIO + observabilidade deployados.

**Out-of-scope deste plano** (entra em 2B/2C):
- Upload de comprovante / MinIO storage abstraction.
- Registro de master público / aprovação / moderação.
- Unidades — endpoints de membros pelo master.
- WhatsApp / password reset / outbox.
- LGPD endpoints (export, anonymize, processing-activities).
- Admin pages de moderação no frontend.
- Páginas de classified/contact/link/recommendation/faq.

---

## File Structure

Arquivos criados/modificados neste plano:

```
backend/
├── pom.xml                                                  (Task 1 — deps novas)
├── src/main/resources/
│   ├── application.yml                                       (Task 3 — JPA/datasource/segurança)
│   ├── application-dev.yml                                   (Task 3)
│   ├── application-prod.yml                                  (Task 3)
│   ├── application-hml.yml                                   (Task 3)
│   └── db/migration/
│       ├── V1__pgcrypto_citext_units.sql                     (Task 4)
│       ├── V2__users_emails_consent.sql                      (Task 5)
│       ├── V3__roles_permissions_rbac.sql                    (Task 6)
│       ├── V4__refresh_password_tokens_history.sql           (Task 7)
│       ├── V5__audit_logs.sql                                (Task 8)
│       ├── V6__seed_roles_permissions_units.sql              (Task 9)
│       ├── V7__seed_consent_v1.sql                           (Task 10)
│       └── V8__seed_admin.sql                                (Task 11)
└── src/main/java/br/com/condominio/
    ├── shared/
    │   ├── audit/AuditorAwareImpl.java                       (Task 12)
    │   ├── audit/JpaAuditingConfig.java                      (Task 12)
    │   ├── security/PepperedBCryptPasswordEncoder.java        (Task 13)
    │   ├── security/PepperConfig.java                         (Task 13)
    │   ├── security/JwtService.java                          (Task 14)
    │   ├── security/JwtProperties.java                        (Task 14)
    │   ├── security/AuthenticatedUserPrincipal.java          (Task 14)
    │   ├── security/JwtAuthenticationConverter.java          (Task 15)
    │   ├── security/SecurityConfig.java                       (Task 15)
    │   ├── security/RateLimitFilter.java                     (Task 16)
    │   ├── security/RateLimitProperties.java                  (Task 16)
    │   ├── exception/GlobalExceptionHandler.java             (Task 19)
    │   ├── exception/ApiError.java                            (Task 19)
    │   ├── exception/AuthenticationException.java             (Task 19)
    │   └── time/Clock.java                                    (Task 17)
    ├── bootstrap/AdminBootstrap.java                          (Task 20)
    └── feature/
        ├── user/
        │   ├── User.java                                       (Task 21)
        │   ├── UserStatus.java                                 (Task 21)
        │   ├── Gender.java                                     (Task 21)
        │   ├── UserEmail.java                                  (Task 21)
        │   ├── UserRepository.java                             (Task 21)
        │   └── UserEmailRepository.java                        (Task 21)
        ├── unit/
        │   ├── Unit.java                                       (Task 22)
        │   └── UnitRepository.java                             (Task 22)
        ├── role/
        │   ├── Role.java                                       (Task 23)
        │   ├── RoleName.java                                   (Task 23)
        │   ├── Permission.java                                 (Task 23)
        │   ├── PermissionCode.java                             (Task 23)
        │   ├── UserRole.java                                   (Task 23)
        │   ├── UserRoleId.java                                 (Task 23)
        │   ├── RolePermission.java                             (Task 23)
        │   ├── UserPermissionGrant.java                        (Task 23)
        │   ├── RoleRepository.java                             (Task 23)
        │   ├── PermissionRepository.java                       (Task 23)
        │   ├── UserPermissionGrantRepository.java              (Task 23)
        │   └── PermissionResolver.java                         (Task 23)
        └── auth/
            ├── RefreshToken.java                              (Task 24)
            ├── RefreshTokenRepository.java                    (Task 24)
            ├── RefreshTokenService.java                       (Task 24)
            ├── LoginAttemptTracker.java                       (Task 25)
            ├── AuthService.java                               (Task 26)
            ├── AuthController.java                            (Task 27)
            └── dto/
                ├── LoginRequest.java                          (Task 27)
                ├── LoginResponse.java                         (Task 27)
                ├── MeResponse.java                            (Task 27)
                └── AuthenticatedUserView.java                  (Task 27)
└── src/test/java/br/com/condominio/
    ├── shared/security/PepperedBCryptPasswordEncoderTest.java  (Task 13)
    ├── shared/security/JwtServiceTest.java                    (Task 14)
    ├── shared/security/RateLimitFilterTest.java               (Task 16)
    ├── feature/auth/RefreshTokenServiceTest.java              (Task 24)
    ├── feature/auth/AuthServiceTest.java                      (Task 26)
    └── feature/role/PermissionResolverTest.java                (Task 23)

frontend/
├── src/
│   ├── App.tsx                                                (Task 30 — router)
│   ├── router.tsx                                             (Task 30)
│   ├── lib/
│   │   ├── api.ts                                              (Task 28)
│   │   └── auth.ts                                             (Task 28)
│   ├── features/auth/
│   │   ├── AuthProvider.tsx                                    (Task 28)
│   │   ├── useAuth.ts                                          (Task 28)
│   │   ├── pages/LoginPage.tsx                                 (Task 29)
│   │   └── api/authApi.ts                                      (Task 28)
│   ├── components/
│   │   ├── ProtectedRoute.tsx                                  (Task 30)
│   │   └── FullPageSpinner.tsx                                  (Task 28)
│   └── components/ui/
│       ├── button.tsx, input.tsx, label.tsx, card.tsx,
│       └── form.tsx, sonner.tsx                                  (Task 28 — shadcn cli)
└── (testes Vitest correspondentes)
```

---

## Convenções deste plano

- **Branch**: criar `feat/auth-core` a partir de `main` no início do plano. Merge via PR ao final.
- **TDD obrigatório**: cada classe de comportamento (encoder, jwt, refresh token, auth service) tem teste escrito **antes** da implementação. Migrations não têm testes diretos — validadas via teste de `AuthService` que faz roundtrip real contra Postgres (Spring Boot test slice).
- **Cada Task = um ou mais commits Conventional**. Não fazer push até final do plano (último Task abre PR).
- Toda mudança em SQL respeita expand/contract: V1..V8 são adições "puras" (não removem nada do schema atual — schema atual é vazio, então tudo é novo).
- **Logs nunca expõem PII**. Mensagens de erro genéricas no login.
- **Sem MinIO, sem WhatsApp** neste plano — `User.residenceProofObjectKey` fica null para admin/staff/doorman; entidades expostas no schema mas APIs de upload entram em 2B.
- Working directory: `D:/Projetos/gestor-condominio` (PowerShell ou Git Bash — comandos abaixo são shell-agnósticos quando possível).

---

## Task 1: Setup branch e dependências Maven

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: Criar branch**

```bash
cd D:/Projetos/gestor-condominio
git checkout main
git pull --ff-only
git checkout -b feat/auth-core
```

- [ ] **Step 2: Adicionar dependências em `backend/pom.xml`**

Dentro de `<dependencies>` (antes de `</dependencies>`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.github.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.passay</groupId>
    <artifactId>passay</artifactId>
    <version>1.6.5</version>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.20.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.3</version>
    <scope>test</scope>
</dependency>
```

⚠ Decisão neste plano: usar **Testcontainers** especificamente em `AuthServiceTest` (E2E real contra Postgres). Demais testes seguem sendo Mockito puro (KISS conforme spec).

- [ ] **Step 3: Validar compilação**

```bash
cd backend && ./mvnw -B -q clean compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml
git commit -m "build(backend): deps JPA, Security, Flyway, Postgres, jjwt, Passay, Bucket4j, Testcontainers"
```

---

## Task 2: Configurar profile dev com Postgres local

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`

- [ ] **Step 1: Adicionar configurações JPA + Datasource em `application.yml`**

Substituir o conteúdo:

```yaml
spring:
  application:
    name: gestor-condominio
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:gestor_condominio}
    username: ${POSTGRES_USER:condominio}
    password: ${POSTGRES_PASSWORD:condominio_dev}
    hikari:
      maximum-pool-size: 10
      connection-timeout: 2000
      leak-detection-threshold: 60000
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true
        jdbc.lob.non_contextual_creation: true
    open-in-view: false
  flyway:
    enabled: true
    baseline-on-migrate: false
    placeholders:
      adminEmail: ${APP_ADMIN_EMAIL:paulobof@gmail.com}
      adminName: ${APP_ADMIN_NAME:Paulo}
  lifecycle:
    timeout-per-shutdown-phase: 20s

server:
  port: 8080
  shutdown: graceful

management:
  endpoints:
    web:
      exposure:
        include: ${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE:health,info,metrics,prometheus}
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
  info:
    git:
      mode: simple

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs

app:
  security:
    password:
      pepper: ${APP_PASSWORD_PEPPER:VFJPQ0EtRVNTRS1QRVBQRVItRU0tUFJPRC09PT09PT09PT09PT0=}
      pepper-version: ${APP_PASSWORD_PEPPER_VERSION:1}
      bcrypt-strength: ${APP_BCRYPT_STRENGTH:12}
    jwt:
      issuer: ${APP_JWT_ISSUER:gestor-condominio}
      audience: ${APP_JWT_AUDIENCE:gestor-condominio-web}
      access-ttl: ${APP_JWT_ACCESS_TTL:PT15M}
      refresh-ttl: ${APP_JWT_REFRESH_TTL:P7D}
      active-kid: ${APP_JWT_ACTIVE_KID:v1}
      keys: ${APP_JWT_KEYS:v1:VFJPQ0EtRVNTRS1KV1QtRU0tUFJPRC09PT09PT09PT09PT0=}
    cors:
      allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://localhost:5173}
    cookie:
      domain: ${APP_COOKIE_DOMAIN:}
      secure: ${APP_COOKIE_SECURE:false}
  ratelimit:
    login-per-min-per-ip: 5
    login-lockout-attempts: 10
    login-lockout-window: PT30M
  admin:
    email: ${APP_ADMIN_EMAIL:paulobof@gmail.com}
    name: ${APP_ADMIN_NAME:Paulo}
    initial-password: ${APP_ADMIN_INITIAL_PASSWORD:trocar-no-primeiro-login}
```

- [ ] **Step 2: Atualizar `application-dev.yml`**

```yaml
logging:
  level:
    root: INFO
    br.com.condominio: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE
```

- [ ] **Step 3: Atualizar `application-prod.yml`**

Adicionar (mantendo o que já existe):

```yaml
spring:
  jpa:
    properties:
      hibernate:
        format_sql: false
logging:
  level:
    org.hibernate.SQL: WARN
```

- [ ] **Step 4: Atualizar `application-hml.yml`**

Adicionar (mantendo):

```yaml
spring:
  jpa:
    properties:
      hibernate:
        format_sql: false
logging:
  level:
    org.hibernate.SQL: WARN
```

- [ ] **Step 5: Subir Postgres local e testar boot**

```bash
docker compose -f docker-compose.dev.yml up -d postgres
sleep 5
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "SELECT version();"
```

Expected: versão PostgreSQL retornada.

⚠ O app **não** vai subir ainda porque `ddl-auto=validate` exige tabelas que não existem. Isso será resolvido na Task 4 (V1 migration).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/
git commit -m "feat(backend): configurar datasource Postgres + JPA + Flyway + propriedades de seguranca"
```

---

## Task 3: V1 migration — pgcrypto, citext, unit (522 unidades)

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__pgcrypto_citext_units.sql`

- [ ] **Step 1: Criar V1__pgcrypto_citext_units.sql**

```sql
-- flyway:transactional=true

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

-- =========================================================================
-- UNIT — 522 unidades pré-cadastradas
-- =========================================================================
CREATE TABLE unit (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint NOT NULL DEFAULT 0,
    tower                   varchar(1) NOT NULL,
    floor                   smallint NOT NULL,
    "position"              smallint NOT NULL,
    code                    varchar(8) NOT NULL,
    master_user_id          uuid,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by_user_id      uuid,
    updated_by_user_id      uuid,
    deleted_at              timestamptz,
    deleted_by_user_id      uuid,
    CONSTRAINT chk_unit_tower CHECK (tower IN ('A','B','C')),
    CONSTRAINT chk_unit_floor CHECK (floor BETWEEN 4 AND 32),
    CONSTRAINT chk_unit_position CHECK ("position" BETWEEN 1 AND 6)
);

CREATE UNIQUE INDEX ux_unit_tower_floor_position_active
    ON unit (tower, floor, "position")
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_unit_code_active
    ON unit (code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_unit_master_user_active
    ON unit (master_user_id)
    WHERE deleted_at IS NULL AND master_user_id IS NOT NULL;
```

⚠ FK `master_user_id → user(id)` será adicionada na V2 (após `user` existir).

- [ ] **Step 2: Validar migration sintaticamente**

```bash
cd backend && ./mvnw -B -q flyway:validate -Dflyway.url="jdbc:postgresql://localhost:5432/gestor_condominio" -Dflyway.user=condominio -Dflyway.password=condominio_dev
```

⚠ Vai falhar se o plugin Flyway Maven não estiver configurado — alternativa: subir o app e deixar Flyway rodar em boot. Vamos validar via boot.

- [ ] **Step 3: Tentar boot e confirmar Flyway aplica V1 (e falha em validate porque ainda não há entidades JPA)**

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev POSTGRES_HOST=localhost POSTGRES_USER=condominio POSTGRES_PASSWORD=condominio_dev ./mvnw spring-boot:run
```

App pode falhar no `validate` mas Flyway aplica V1 antes. Após Ctrl+C:

```bash
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "\d unit"
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "SELECT extname FROM pg_extension WHERE extname IN ('pgcrypto','citext');"
```

Expected: tabela `unit` existe, extensions presentes.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V1__pgcrypto_citext_units.sql
git commit -m "feat(db): V1 - extensions pgcrypto+citext e tabela unit com 522 unidades (constraints)"
```

---

## Task 4: V2 migration — user + user_email + consent_document

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__users_emails_consent.sql`

- [ ] **Step 1: Criar V2__users_emails_consent.sql**

```sql
-- flyway:transactional=true

-- =========================================================================
-- CONSENT_DOCUMENT
-- =========================================================================
CREATE TABLE consent_document (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 varchar(20) NOT NULL,
    body                    text NOT NULL,
    published_at            timestamptz NOT NULL DEFAULT now(),
    created_at              timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_consent_document_version ON consent_document (version);

-- =========================================================================
-- USER
-- =========================================================================
CREATE TABLE "user" (
    id                              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                         bigint NOT NULL DEFAULT 0,
    unit_id                         uuid REFERENCES unit (id) ON DELETE RESTRICT,
    is_unit_master                  boolean NOT NULL DEFAULT false,
    full_name                       varchar(180) NOT NULL,
    greeting_name                   varchar(60),
    phone                           varchar(20),
    phone_verified_at               timestamptz,
    gender                          varchar(20),
    birth_date                      date,
    password_hash                   varchar(255) NOT NULL,
    password_pepper_version         smallint NOT NULL DEFAULT 1,
    must_change_password            boolean NOT NULL DEFAULT false,
    status                          varchar(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    residence_proof_object_key      varchar(255),
    residence_proof_filename        varchar(255),
    residence_proof_content_type    varchar(80),
    residence_proof_uploaded_at     timestamptz,
    proof_verified_at               timestamptz,
    approved_by_user_id             uuid,
    approved_at                     timestamptz,
    rejection_reason                text,
    anonymized_at                   timestamptz,
    consent_document_version        varchar(20),
    consent_accepted_at             timestamptz,
    consent_accepted_ip             inet,
    whatsapp_opt_in                 boolean NOT NULL DEFAULT false,
    whatsapp_opt_in_at              timestamptz,
    created_at                      timestamptz NOT NULL DEFAULT now(),
    updated_at                      timestamptz NOT NULL DEFAULT now(),
    created_by_user_id              uuid,
    updated_by_user_id              uuid,
    deleted_at                      timestamptz,
    deleted_by_user_id              uuid,
    CONSTRAINT chk_user_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER','NOT_INFORMED')),
    CONSTRAINT chk_user_status CHECK (status IN ('PENDING_APPROVAL','ACTIVE','REJECTED','DISABLED','ANONYMIZED')),
    CONSTRAINT chk_user_master_needs_unit CHECK (is_unit_master = false OR unit_id IS NOT NULL)
);

CREATE INDEX idx_user_status ON "user" (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_unit_id ON "user" (unit_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_full_name_trgm ON "user" USING gin (full_name gin_trgm_ops);

-- FK postergada: unit.master_user_id → user.id
ALTER TABLE unit
    ADD CONSTRAINT fk_unit_master_user
    FOREIGN KEY (master_user_id) REFERENCES "user" (id) ON DELETE RESTRICT;

-- =========================================================================
-- USER_EMAIL
-- =========================================================================
CREATE TABLE user_email (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint NOT NULL DEFAULT 0,
    user_id                 uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    email                   citext NOT NULL,
    is_primary              boolean NOT NULL DEFAULT false,
    verified_at             timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by_user_id      uuid,
    updated_by_user_id      uuid,
    deleted_at              timestamptz,
    deleted_by_user_id      uuid
);

CREATE UNIQUE INDEX ux_user_email_email_active
    ON user_email (email)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_user_email_primary_per_user_active
    ON user_email (user_id)
    WHERE is_primary = true AND deleted_at IS NULL;
CREATE INDEX idx_user_email_user_id ON user_email (user_id) WHERE deleted_at IS NULL;
```

⚠ Note `pg_trgm` extension precisa estar habilitada para `gin_trgm_ops`. Adicionar nessa migration:

No início, antes do CREATE TABLE consent_document, adicione:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

- [ ] **Step 2: Validar via Flyway**

Stop o app se estiver rodando, e re-start:

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev POSTGRES_HOST=localhost POSTGRES_USER=condominio POSTGRES_PASSWORD=condominio_dev ./mvnw spring-boot:run
```

Após Ctrl+C:

```bash
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "\d \"user\""
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "\d user_email"
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V2__users_emails_consent.sql
git commit -m "feat(db): V2 - tabelas user (com soft delete + audit + LGPD), user_email (citext, primary), consent_document"
```

---

## Task 5: V3 migration — RBAC (role, permission, user_role, role_permission, user_permission_grant)

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__roles_permissions_rbac.sql`

- [ ] **Step 1: Criar V3__roles_permissions_rbac.sql**

```sql
-- flyway:transactional=true

CREATE TABLE role (
    id              smallint PRIMARY KEY,
    name            varchar(20) NOT NULL UNIQUE,
    label           varchar(40) NOT NULL,
    max_holders     smallint,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE permission (
    id              smallint PRIMARY KEY,
    code            varchar(60) NOT NULL UNIQUE,
    label           varchar(80) NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE user_role (
    user_id         uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    role_id         smallint NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    assigned_at     timestamptz NOT NULL DEFAULT now(),
    assigned_by_user_id uuid REFERENCES "user" (id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_role_role_id ON user_role (role_id);

CREATE TABLE role_permission (
    role_id         smallint NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission_id   smallint NOT NULL REFERENCES permission (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX idx_role_permission_permission_id ON role_permission (permission_id);

CREATE TABLE user_permission_grant (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    permission_id   smallint NOT NULL REFERENCES permission (id) ON DELETE RESTRICT,
    granted_by_user_id uuid REFERENCES "user" (id) ON DELETE SET NULL,
    granted_at      timestamptz NOT NULL DEFAULT now(),
    revoked_at      timestamptz,
    revoked_by_user_id uuid REFERENCES "user" (id) ON DELETE SET NULL,
    CONSTRAINT chk_grant_self CHECK (user_id <> granted_by_user_id OR granted_by_user_id IS NULL)
);
CREATE UNIQUE INDEX ux_user_permission_grant_active
    ON user_permission_grant (user_id, permission_id)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_user_permission_grant_user ON user_permission_grant (user_id);
```

- [ ] **Step 2: Validar boot e tabelas**

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev POSTGRES_HOST=localhost POSTGRES_USER=condominio POSTGRES_PASSWORD=condominio_dev ./mvnw spring-boot:run
```

(Ctrl+C)

```bash
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "\dt"
```

Expected: lista de tabelas inclui `role`, `permission`, `user_role`, `role_permission`, `user_permission_grant`.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V3__roles_permissions_rbac.sql
git commit -m "feat(db): V3 - RBAC (role com max_holders, permission, M:N + user_permission_grant)"
```

---

## Task 6: V4 migration — refresh_token + password_history + password_reset_token

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__refresh_password_tokens_history.sql`

- [ ] **Step 1: Criar V4__refresh_password_tokens_history.sql**

```sql
-- flyway:transactional=true

CREATE TABLE refresh_token (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint NOT NULL DEFAULT 0,
    user_id             uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    token_hash          varchar(255) NOT NULL,
    token_family        uuid NOT NULL,
    expires_at          timestamptz NOT NULL,
    revoked             boolean NOT NULL DEFAULT false,
    revoked_at          timestamptz,
    revoked_reason      varchar(80),
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by_user_id  uuid,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    updated_by_user_id  uuid,
    deleted_at          timestamptz,
    deleted_by_user_id  uuid
);
CREATE UNIQUE INDEX ux_refresh_token_hash ON refresh_token (token_hash);
CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_refresh_token_family ON refresh_token (token_family) WHERE revoked = false;
CREATE INDEX idx_refresh_token_active_expires
    ON refresh_token (expires_at)
    WHERE revoked = false AND deleted_at IS NULL;

CREATE TABLE password_history (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    password_hash               varchar(255) NOT NULL,
    password_pepper_version     smallint NOT NULL,
    created_at                  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_history_user_created
    ON password_history (user_id, created_at DESC);

CREATE TABLE password_reset_token (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    token_hash          varchar(255) NOT NULL,
    expires_at          timestamptz NOT NULL,
    used_at             timestamptz,
    created_ip          inet,
    delivered_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_password_reset_token_hash ON password_reset_token (token_hash);
CREATE INDEX idx_password_reset_token_user
    ON password_reset_token (user_id, used_at);
CREATE INDEX idx_password_reset_token_expires
    ON password_reset_token (expires_at)
    WHERE used_at IS NULL;
```

- [ ] **Step 2: Validar e commit**

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev POSTGRES_HOST=localhost POSTGRES_USER=condominio POSTGRES_PASSWORD=condominio_dev ./mvnw spring-boot:run
```

(Ctrl+C, valida tabelas)

```bash
git add backend/src/main/resources/db/migration/V4__refresh_password_tokens_history.sql
git commit -m "feat(db): V4 - refresh_token (familia+replay), password_history (5 ultimos), password_reset_token (uso unico)"
```

---

## Task 7: V5 migration — audit logs

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__audit_logs.sql`

- [ ] **Step 1: Criar V5__audit_logs.sql**

```sql
-- flyway:transactional=true

CREATE TABLE user_permission_grant_log (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    action                  varchar(20) NOT NULL,
    target_user_id          uuid NOT NULL,
    permission_id           smallint NOT NULL,
    actor_user_id           uuid,
    acted_at                timestamptz NOT NULL DEFAULT now(),
    request_id              varchar(40),
    CONSTRAINT chk_grant_log_action CHECK (action IN ('GRANT','REVOKE'))
);
CREATE INDEX idx_grant_log_target_user ON user_permission_grant_log (target_user_id, acted_at);

CREATE TABLE proof_access_log (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id               uuid NOT NULL,
    target_user_id              uuid NOT NULL,
    accessed_at                 timestamptz NOT NULL DEFAULT now(),
    ip                          inet,
    user_agent                  varchar(255),
    presigned_url_ttl_seconds   integer,
    request_id                  varchar(40)
);
CREATE INDEX idx_proof_access_log_admin ON proof_access_log (admin_user_id, accessed_at);
CREATE INDEX idx_proof_access_log_target ON proof_access_log (target_user_id, accessed_at);

CREATE TABLE sensitive_access_log (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id       uuid NOT NULL,
    target_user_id      uuid,
    action              varchar(40) NOT NULL,
    acted_at            timestamptz NOT NULL DEFAULT now(),
    client_ip           inet,
    user_agent          varchar(255),
    request_id          varchar(40)
);
CREATE INDEX idx_sensitive_access_log_actor ON sensitive_access_log (actor_user_id, acted_at);
CREATE INDEX idx_sensitive_access_log_target ON sensitive_access_log (target_user_id, acted_at)
    WHERE target_user_id IS NOT NULL;
```

- [ ] **Step 2: Validar e commit**

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev POSTGRES_HOST=localhost POSTGRES_USER=condominio POSTGRES_PASSWORD=condominio_dev ./mvnw spring-boot:run
```

(Ctrl+C)

```bash
git add backend/src/main/resources/db/migration/V5__audit_logs.sql
git commit -m "feat(db): V5 - audit logs (user_permission_grant_log, proof_access_log, sensitive_access_log)"
```

---

## Task 8: V6 migration — seed roles, permissions, role_permission, 522 unidades

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__seed_roles_permissions_units.sql`

- [ ] **Step 1: Criar V6__seed_roles_permissions_units.sql**

```sql
-- flyway:transactional=true

-- Roles fixas
INSERT INTO role (id, name, label, max_holders) VALUES
    (1, 'MANAGER',  'Síndico',       1),
    (2, 'COUNCIL',  'Conselheiro',   3),
    (3, 'STAFF',    'Administração', 5),
    (4, 'RESIDENT', 'Morador',       NULL),
    (5, 'DOORMAN',  'Porteiro',      NULL);

-- Permissions
INSERT INTO permission (id, code, label) VALUES
    (1,  'USER_VIEW',                  'Visualizar usuários'),
    (2,  'USER_MANAGE',                'Gerenciar usuários'),
    (3,  'REGISTRATION_VIEW',          'Visualizar cadastros pendentes'),
    (4,  'REGISTRATION_APPROVE',       'Aprovar/rejeitar cadastros'),
    (5,  'RESIDENCE_PROOF_VIEW',       'Visualizar comprovantes de residência'),
    (6,  'CONTACT_MANAGE',             'Gerenciar telefones úteis'),
    (7,  'LINK_MANAGE',                'Gerenciar links úteis'),
    (8,  'FAQ_MANAGE',                 'Gerenciar FAQ'),
    (9,  'TAG_MANAGE',                 'Gerenciar tags'),
    (10, 'RECOMMENDATION_MODERATE',    'Moderar indicações'),
    (11, 'CLASSIFIED_MODERATE',        'Moderar classificados'),
    (12, 'ROLE_ASSIGN',                'Atribuir/remover roles'),
    (13, 'PERMISSION_GRANT',           'Conceder/revogar permissões'),
    (14, 'AUDIT_VIEW',                 'Visualizar trilhas de auditoria');

-- MANAGER recebe todas
INSERT INTO role_permission (role_id, permission_id)
SELECT 1, id FROM permission;

-- COUNCIL: ver usuários/cadastros, aprovar, ver proof, FAQ, moderar conteúdo
INSERT INTO role_permission (role_id, permission_id) VALUES
    (2, 1), (2, 3), (2, 4), (2, 5), (2, 8), (2, 10), (2, 11);

-- STAFF: nenhuma por default (concedido individualmente)

-- RESIDENT: nenhuma (acesso a recursos próprios não exige permission)

-- DOORMAN
INSERT INTO role_permission (role_id, permission_id) VALUES
    (5, 1);

-- Seed das 522 unidades: 3 torres × andares 4..32 × posições 1..6
INSERT INTO unit (tower, floor, "position", code)
SELECT
    t.tower,
    f.floor,
    p.pos,
    f.floor || lpad(p.pos::text, 2, '0') || t.tower AS code
FROM (VALUES ('A'), ('B'), ('C')) AS t(tower)
CROSS JOIN generate_series(4, 32) AS f(floor)
CROSS JOIN generate_series(1, 6) AS p(pos);
```

- [ ] **Step 2: Validar contagem (522)**

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev POSTGRES_HOST=localhost POSTGRES_USER=condominio POSTGRES_PASSWORD=condominio_dev ./mvnw spring-boot:run
```

(Ctrl+C)

```bash
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "SELECT COUNT(*) FROM unit;"
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "SELECT code FROM unit ORDER BY code LIMIT 5;"
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "SELECT COUNT(*) FROM role_permission WHERE role_id = 1;"
```

Expected: `522`, lista (`402A`, `402B`, `402C`, `403A`, `403B`), `14`.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V6__seed_roles_permissions_units.sql
git commit -m "feat(db): V6 - seed roles (5), permissions (14), role_permission defaults, 522 unidades"
```

---

## Task 9: V7 — seed consent v1

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__seed_consent_v1.sql`

- [ ] **Step 1: Criar V7__seed_consent_v1.sql**

```sql
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
```

- [ ] **Step 2: Validar e commit**

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev POSTGRES_HOST=localhost POSTGRES_USER=condominio POSTGRES_PASSWORD=condominio_dev ./mvnw spring-boot:run
```

(Ctrl+C)

```bash
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "SELECT version, length(body) FROM consent_document;"
```

Expected: 1 linha versão `1.0.0` com `length > 500`.

```bash
git add backend/src/main/resources/db/migration/V7__seed_consent_v1.sql
git commit -m "feat(db): V7 - seed do termo de privacidade v1.0.0"
```

---

## Task 10: V8 — seed admin com password_hash __PENDING__

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__seed_admin.sql`

- [ ] **Step 1: Criar V8__seed_admin.sql**

```sql
-- flyway:transactional=true

INSERT INTO "user" (
    id,
    unit_id,
    is_unit_master,
    full_name,
    greeting_name,
    password_hash,
    password_pepper_version,
    must_change_password,
    status,
    consent_document_version,
    consent_accepted_at,
    consent_accepted_ip
) VALUES (
    gen_random_uuid(),
    NULL,
    false,
    '${adminName}',
    '${adminName}',
    '__PENDING__',
    1,
    true,
    'ACTIVE',
    '1.0.0',
    now(),
    '127.0.0.1'::inet
) RETURNING id \gset

-- Adiciona email primário do admin
INSERT INTO user_email (user_id, email, is_primary, verified_at)
SELECT id, '${adminEmail}', true, now()
FROM "user" WHERE password_hash = '__PENDING__';

-- Atribui role MANAGER ao admin
INSERT INTO user_role (user_id, role_id)
SELECT u.id, 1
FROM "user" u WHERE u.password_hash = '__PENDING__';
```

⚠ Flyway placeholders `${adminName}` e `${adminEmail}` são substituídos por env vars via `spring.flyway.placeholders.adminName` / `adminEmail` (já configurado no application.yml da Task 2).

⚠ `\gset` é comando psql interativo — Flyway pode não suportar. Vou simplificar usando uma única transação sem RETURNING:

Substitua o conteúdo por:

```sql
-- flyway:transactional=true

WITH new_admin AS (
    INSERT INTO "user" (
        unit_id,
        is_unit_master,
        full_name,
        greeting_name,
        password_hash,
        password_pepper_version,
        must_change_password,
        status,
        consent_document_version,
        consent_accepted_at,
        consent_accepted_ip
    ) VALUES (
        NULL,
        false,
        '${adminName}',
        '${adminName}',
        '__PENDING__',
        1,
        true,
        'ACTIVE',
        '1.0.0',
        now(),
        '127.0.0.1'::inet
    )
    RETURNING id
),
admin_email AS (
    INSERT INTO user_email (user_id, email, is_primary, verified_at)
    SELECT id, '${adminEmail}', true, now() FROM new_admin
    RETURNING user_id
)
INSERT INTO user_role (user_id, role_id)
SELECT user_id, 1 FROM admin_email;
```

- [ ] **Step 2: Validar e commit**

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev POSTGRES_HOST=localhost POSTGRES_USER=condominio POSTGRES_PASSWORD=condominio_dev APP_ADMIN_EMAIL=paulobof@gmail.com APP_ADMIN_NAME=Paulo ./mvnw spring-boot:run
```

(Ctrl+C)

```bash
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "
SELECT u.full_name, ue.email, r.name, u.password_hash
FROM \"user\" u
JOIN user_email ue ON ue.user_id = u.id
JOIN user_role ur ON ur.user_id = u.id
JOIN role r ON r.id = ur.role_id;
"
```

Expected: 1 linha com `Paulo | paulobof@gmail.com | MANAGER | __PENDING__`.

```bash
git add backend/src/main/resources/db/migration/V8__seed_admin.sql
git commit -m "feat(db): V8 - seed admin com password_hash __PENDING__ (substituido por bootstrap)"
```

---

## Task 11: AuditorAware + JpaAuditingConfig

**Files:**
- Create: `backend/src/main/java/br/com/condominio/shared/audit/AuditorAwareImpl.java`
- Create: `backend/src/main/java/br/com/condominio/shared/audit/JpaAuditingConfig.java`

- [ ] **Step 1: Criar `JpaAuditingConfig.java`**

```java
package br.com.condominio.shared.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

  AuditorAware<java.util.UUID> auditorAware(AuditorAwareImpl impl) {
    return impl;
  }
}
```

Na verdade Spring Data já registra via `@Configuration` se houver bean. Vamos usar:

```java
package br.com.condominio.shared.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

  @Bean
  public AuditorAware<UUID> auditorProvider(AuditorAwareImpl impl) {
    return impl;
  }
}
```

- [ ] **Step 2: Criar `AuditorAwareImpl.java`**

```java
package br.com.condominio.shared.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AuditorAwareImpl implements AuditorAware<UUID> {

  @Override
  public Optional<UUID> getCurrentAuditor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return Optional.empty();
    }
    if (auth.getPrincipal() instanceof br.com.condominio.shared.security.AuthenticatedUserPrincipal p) {
      return Optional.of(p.userId());
    }
    return Optional.empty();
  }
}
```

⚠ `AuthenticatedUserPrincipal` será criado na Task 14. Por enquanto, comentar a referência:

```java
// if (auth.getPrincipal() instanceof br.com.condominio.shared.security.AuthenticatedUserPrincipal p) {
//   return Optional.of(p.userId());
// }
return Optional.empty();
```

Isso compila e respeita o contrato (sempre retorna empty em contexto público). Será descomentado quando `AuthenticatedUserPrincipal` existir.

- [ ] **Step 3: Não há teste unitário neste step** (cobertura indireta via teste E2E na Task 26).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/br/com/condominio/shared/audit/
git commit -m "feat(audit): AuditorAware retorna Optional.empty() em contexto publico + JpaAuditing config"
```

---

## Task 12: PepperedBCryptPasswordEncoder + testes

**Files:**
- Create: `backend/src/main/java/br/com/condominio/shared/security/PepperConfig.java`
- Create: `backend/src/main/java/br/com/condominio/shared/security/PepperedBCryptPasswordEncoder.java`
- Create: `backend/src/test/java/br/com/condominio/shared/security/PepperedBCryptPasswordEncoderTest.java`

- [ ] **Step 1: Escrever teste primeiro**

`backend/src/test/java/br/com/condominio/shared/security/PepperedBCryptPasswordEncoderTest.java`:

```java
package br.com.condominio.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PepperedBCryptPasswordEncoderTest {

  private static final byte[] PEPPER_V1 = Base64.getDecoder().decode(
      "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
  private static final byte[] PEPPER_V2 = Base64.getDecoder().decode(
      "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");

  private PepperedBCryptPasswordEncoder encoder;

  @BeforeEach
  void setUp() {
    encoder = new PepperedBCryptPasswordEncoder(PEPPER_V1, 1, new BCryptPasswordEncoder(4));
  }

  @Test
  void encodesAndMatchesSamePassword() {
    String hash = encoder.encode("Hunter2!@");
    assertThat(encoder.matches("Hunter2!@", hash)).isTrue();
  }

  @Test
  void rejectsWrongPassword() {
    String hash = encoder.encode("Hunter2!@");
    assertThat(encoder.matches("hunter2!@", hash)).isFalse();
  }

  @Test
  void rejectsPendingPlaceholderExplicitly() {
    assertThat(encoder.matches("__PENDING__", "__PENDING__")).isFalse();
    assertThat(encoder.matches("anything", "__PENDING__")).isFalse();
  }

  @Test
  void differentPepperProducesDifferentHash() {
    PepperedBCryptPasswordEncoder enc1 = new PepperedBCryptPasswordEncoder(
        PEPPER_V1, 1, new BCryptPasswordEncoder(4));
    PepperedBCryptPasswordEncoder enc2 = new PepperedBCryptPasswordEncoder(
        PEPPER_V2, 2, new BCryptPasswordEncoder(4));
    String h1 = enc1.encode("samepass");
    String h2 = enc2.encode("samepass");
    assertThat(h1).isNotEqualTo(h2);
    assertThat(enc1.matches("samepass", h1)).isTrue();
    assertThat(enc2.matches("samepass", h1)).isFalse();
  }

  @Test
  void emptyPasswordHashesDifferentlyEachCall() {
    String h1 = encoder.encode("Hunter2!@");
    String h2 = encoder.encode("Hunter2!@");
    assertThat(h1).isNotEqualTo(h2);
    assertThat(encoder.matches("Hunter2!@", h1)).isTrue();
    assertThat(encoder.matches("Hunter2!@", h2)).isTrue();
  }
}
```

- [ ] **Step 2: Rodar teste (deve falhar — class não existe)**

```bash
cd backend && ./mvnw -B -q -Dtest=PepperedBCryptPasswordEncoderTest test
```

Expected: BUILD FAILURE com erro de compilação.

- [ ] **Step 3: Criar `PepperConfig.java`**

```java
package br.com.condominio.shared.security;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PepperConfig {

  @Bean
  public PasswordEncoder passwordEncoder(
      @Value("${app.security.password.pepper}") String pepperBase64,
      @Value("${app.security.password.pepper-version:1}") int pepperVersion,
      @Value("${app.security.password.bcrypt-strength:12}") int bcryptStrength) {
    byte[] pepper = Base64.getDecoder().decode(pepperBase64);
    return new PepperedBCryptPasswordEncoder(pepper, pepperVersion, new BCryptPasswordEncoder(bcryptStrength));
  }
}
```

- [ ] **Step 4: Criar `PepperedBCryptPasswordEncoder.java`**

```java
package br.com.condominio.shared.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class PepperedBCryptPasswordEncoder implements PasswordEncoder {

  static final String PENDING_PLACEHOLDER = "__PENDING__";

  private final byte[] pepper;
  private final int pepperVersion;
  private final BCryptPasswordEncoder bcrypt;

  public PepperedBCryptPasswordEncoder(byte[] pepper, int pepperVersion, BCryptPasswordEncoder bcrypt) {
    if (pepper == null || pepper.length < 32) {
      throw new IllegalArgumentException("Pepper must be at least 32 bytes");
    }
    this.pepper = pepper.clone();
    this.pepperVersion = pepperVersion;
    this.bcrypt = bcrypt;
  }

  public int pepperVersion() {
    return pepperVersion;
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return bcrypt.encode(hmacBase64(rawPassword));
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    if (encodedPassword == null || encodedPassword.isEmpty()) return false;
    if (PENDING_PLACEHOLDER.equals(encodedPassword)) return false;
    return bcrypt.matches(hmacBase64(rawPassword), encodedPassword);
  }

  private String hmacBase64(CharSequence raw) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
      byte[] out = mac.doFinal(raw.toString().getBytes(StandardCharsets.UTF_8));
      return java.util.Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }
}
```

- [ ] **Step 5: Rodar teste (deve passar)**

```bash
cd backend && ./mvnw -B -q -Dtest=PepperedBCryptPasswordEncoderTest test
```

Expected: BUILD SUCCESS, 5 testes passam.

- [ ] **Step 6: Spotless apply**

```bash
cd backend && ./mvnw -q spotless:apply
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/condominio/shared/security/ backend/src/test/java/br/com/condominio/shared/security/PepperedBCryptPasswordEncoderTest.java
git commit -m "feat(security): PepperedBCryptPasswordEncoder (HMAC-SHA256 + bcrypt) com rejeicao explicita de __PENDING__"
```

---

## Task 13: JwtService + JwtProperties + AuthenticatedUserPrincipal

**Files:**
- Create: `backend/src/main/java/br/com/condominio/shared/security/JwtProperties.java`
- Create: `backend/src/main/java/br/com/condominio/shared/security/JwtService.java`
- Create: `backend/src/main/java/br/com/condominio/shared/security/AuthenticatedUserPrincipal.java`
- Create: `backend/src/test/java/br/com/condominio/shared/security/JwtServiceTest.java`

- [ ] **Step 1: Escrever teste primeiro**

```java
package br.com.condominio.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private JwtService jwt;
  private UUID userId;

  @BeforeEach
  void setUp() {
    JwtProperties props = new JwtProperties();
    props.setIssuer("gestor-condominio");
    props.setAudience("gestor-condominio-web");
    props.setAccessTtl(Duration.ofMinutes(15));
    props.setRefreshTtl(Duration.ofDays(7));
    props.setActiveKid("v1");
    props.setKeys(List.of("v1:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="));
    jwt = new JwtService(props);
    userId = UUID.randomUUID();
  }

  @Test
  void signsAndParsesAccessToken() {
    String token = jwt.signAccessToken(userId, List.of("RESIDENT"), List.of("USER_VIEW"), null, true);
    JwtService.ParsedAccessToken p = jwt.parseAccessToken(token);
    assertThat(p.userId()).isEqualTo(userId);
    assertThat(p.roles()).containsExactly("RESIDENT");
    assertThat(p.authorities()).containsExactly("USER_VIEW");
    assertThat(p.isUnitMaster()).isTrue();
    assertThat(p.unitId()).isNull();
  }

  @Test
  void rejectsTokenWithWrongIssuer() {
    String token = jwt.signAccessToken(userId, List.of(), List.of(), null, false);
    JwtProperties other = new JwtProperties();
    other.setIssuer("other");
    other.setAudience("gestor-condominio-web");
    other.setAccessTtl(Duration.ofMinutes(15));
    other.setActiveKid("v1");
    other.setKeys(List.of("v1:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="));
    JwtService otherJwt = new JwtService(other);
    assertThatThrownBy(() -> otherJwt.parseAccessToken(token))
        .isInstanceOf(io.jsonwebtoken.JwtException.class);
  }

  @Test
  void rejectsExpiredToken() throws InterruptedException {
    JwtProperties props = new JwtProperties();
    props.setIssuer("gestor-condominio");
    props.setAudience("gestor-condominio-web");
    props.setAccessTtl(Duration.ofMillis(50));
    props.setActiveKid("v1");
    props.setKeys(List.of("v1:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="));
    JwtService shortLived = new JwtService(props);
    String token = shortLived.signAccessToken(userId, List.of(), List.of(), null, false);
    Thread.sleep(120);
    assertThatThrownBy(() -> shortLived.parseAccessToken(token))
        .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
  }

  @Test
  void supportsKeyRotationParsesWithAnyConfiguredKey() {
    String tokenV1 = jwt.signAccessToken(userId, List.of(), List.of(), null, false);
    JwtProperties propsWithBoth = new JwtProperties();
    propsWithBoth.setIssuer("gestor-condominio");
    propsWithBoth.setAudience("gestor-condominio-web");
    propsWithBoth.setAccessTtl(Duration.ofMinutes(15));
    propsWithBoth.setActiveKid("v2");
    propsWithBoth.setKeys(List.of(
        "v1:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "v2:AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA="));
    JwtService rotated = new JwtService(propsWithBoth);
    JwtService.ParsedAccessToken p = rotated.parseAccessToken(tokenV1);
    assertThat(p.userId()).isEqualTo(userId);
  }
}
```

- [ ] **Step 2: Rodar teste, esperado FAIL**

```bash
cd backend && ./mvnw -B -q -Dtest=JwtServiceTest test
```

- [ ] **Step 3: Criar `JwtProperties.java`**

```java
package br.com.condominio.shared.security;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {
  private String issuer;
  private String audience;
  private Duration accessTtl = Duration.ofMinutes(15);
  private Duration refreshTtl = Duration.ofDays(7);
  private String activeKid;
  private List<String> keys = List.of();
}
```

- [ ] **Step 4: Criar `AuthenticatedUserPrincipal.java`**

```java
package br.com.condominio.shared.security;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUserPrincipal(
    UUID userId,
    String displayName,
    List<String> roles,
    List<String> authorities,
    UUID unitId,
    boolean isUnitMaster) {}
```

- [ ] **Step 5: Criar `JwtService.java`**

```java
package br.com.condominio.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

  private final JwtProperties props;
  private final Map<String, SecretKey> keysByKid;
  private final SecretKey activeKey;

  public JwtService(JwtProperties props) {
    this.props = props;
    this.keysByKid = new HashMap<>();
    for (String entry : props.getKeys()) {
      String[] parts = entry.split(":", 2);
      if (parts.length != 2) throw new IllegalStateException("Invalid JWT key entry: " + entry);
      byte[] keyBytes = Base64.getDecoder().decode(parts[1]);
      if (keyBytes.length < 32) throw new IllegalStateException("JWT key " + parts[0] + " too short");
      keysByKid.put(parts[0], Keys.hmacShaKeyFor(keyBytes));
    }
    this.activeKey = keysByKid.get(props.getActiveKid());
    if (activeKey == null) {
      throw new IllegalStateException("Active JWT kid '" + props.getActiveKid() + "' not in keys");
    }
  }

  public String signAccessToken(
      UUID userId,
      List<String> roles,
      List<String> authorities,
      UUID unitId,
      boolean isUnitMaster) {
    Instant now = Instant.now();
    return Jwts.builder()
        .header().keyId(props.getActiveKid()).and()
        .issuer(props.getIssuer())
        .audience().add(props.getAudience()).and()
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(props.getAccessTtl())))
        .id(UUID.randomUUID().toString())
        .claim("roles", roles)
        .claim("authorities", authorities)
        .claim("unitId", unitId == null ? null : unitId.toString())
        .claim("isUnitMaster", isUnitMaster)
        .signWith(activeKey)
        .compact();
  }

  public ParsedAccessToken parseAccessToken(String token) {
    Jws<Claims> jws =
        Jwts.parser()
            .keyLocator(header -> {
              String kid = header.get("kid").toString();
              SecretKey key = keysByKid.get(kid);
              if (key == null) throw new JwtException("Unknown kid: " + kid);
              return key;
            })
            .requireIssuer(props.getIssuer())
            .requireAudience(props.getAudience())
            .build()
            .parseSignedClaims(token);
    Claims c = jws.getPayload();
    return new ParsedAccessToken(
        UUID.fromString(c.getSubject()),
        listClaim(c, "roles"),
        listClaim(c, "authorities"),
        c.get("unitId") == null ? null : UUID.fromString(c.get("unitId", String.class)),
        Boolean.TRUE.equals(c.get("isUnitMaster", Boolean.class)));
  }

  @SuppressWarnings("unchecked")
  private List<String> listClaim(Claims c, String name) {
    Object v = c.get(name);
    if (v instanceof List<?> list) return list.stream().map(Object::toString).toList();
    return List.of();
  }

  public record ParsedAccessToken(
      UUID userId, List<String> roles, List<String> authorities, UUID unitId, boolean isUnitMaster) {}
}
```

- [ ] **Step 6: Rodar testes (devem passar)**

```bash
cd backend && ./mvnw -B -q -Dtest=JwtServiceTest test
```

Expected: BUILD SUCCESS, 4 testes passam.

- [ ] **Step 7: Spotless + commit**

```bash
cd backend && ./mvnw -q spotless:apply
git add backend/src/main/java/br/com/condominio/shared/security/ backend/src/test/java/br/com/condominio/shared/security/JwtServiceTest.java
git commit -m "feat(security): JwtService (HS256 com kid rotacionavel, claims iss/aud/jti/roles/authorities/unitId)"
```

Agora descomente o bloco em `AuditorAwareImpl` (Task 11) que usa `AuthenticatedUserPrincipal`:

```java
if (auth.getPrincipal() instanceof br.com.condominio.shared.security.AuthenticatedUserPrincipal p) {
  return Optional.of(p.userId());
}
return Optional.empty();
```

```bash
git add backend/src/main/java/br/com/condominio/shared/audit/AuditorAwareImpl.java
git commit -m "fix(audit): AuditorAware le userId do AuthenticatedUserPrincipal"
```

---

> **Continuação do plano** segue na próxima seção. Por limites de tamanho desta resposta, **Tasks 14 a 31 são desenvolvidas em fragmentos progressivos a serem implementados sequencialmente.**

## Tasks 14-31: visão geral (a expandir antes de cada execução)

| # | Título | Resumo |
|---|---|---|
| 14 | JwtAuthenticationConverter + SecurityConfig | Filter chain Spring Security 6, `/api/auth/login\|refresh` público, demais autenticado. Converter lê claim `authorities` direto sem hit no DB. |
| 15 | RateLimitFilter + Bucket4j | Filter applied em `/api/auth/login` (5/min/IP) e `/api/auth/refresh` (10/min/IP). |
| 16 | Clock service | `Clock` injetável para testes determinísticos. |
| 17 | GlobalExceptionHandler + ApiError | Envelope JSON uniforme com `requestId` do MDC; mapeia validation/access-denied/optimistic-lock. |
| 18 | AdminBootstrap | `ApplicationRunner` faz `UPDATE "user" SET password_hash=?, password_pepper_version=? WHERE password_hash='__PENDING__'`. Idempotente. Log de quantas linhas atualizadas. |
| 19 | JPA entities — User, UserStatus, Gender, UserEmail + Repositories | Lombok `@Getter @Setter(AccessLevel.PROTECTED) @EqualsAndHashCode(of="id")`. `@SQLDelete` + `@SQLRestriction`. Métodos de domínio: `User.approveAsMaster`, `disable`, `anonymize`, `changePassword`. |
| 20 | JPA entities — Unit + Repository | `Unit.assignMaster(User)`. UNIQUE parcial validado em service. |
| 21 | JPA entities — Role/Permission/UserRole/RolePermission/UserPermissionGrant + Repositories + PermissionResolver | `PermissionResolver.effectiveAuthorities(User)` retorna `Set<String>` com union de role.permissions + grants. Teste de unit. |
| 22 | RefreshToken entity + repository | `RefreshToken.rotate()`, `revoke(reason)` com transição guardada. |
| 23 | RefreshTokenService + replay detection | Gera token opaco 32 bytes, hash SHA-256, `family_id` (UUID); rotação atômica via SQL update conditional. |
| 24 | LoginAttemptTracker | In-memory tracker por userId (ConcurrentHashMap), reset em sucesso, lockout após 10 falhas em 30 min. |
| 25 | AuthService — login | Resolve email no `user_email`, valida senha, rejeita não-ACTIVE com mensagem única, gera access+refresh, registra `sensitive_access_log`. |
| 26 | AuthService — refresh, logout, me + AuthServiceTest (Testcontainers) | Teste E2E real com Postgres via Testcontainers. |
| 27 | AuthController + DTOs | `POST /api/auth/login` body `{email, password}` → 200 `{accessToken, user}` + Set-Cookie HttpOnly refresh. `POST /refresh` lê cookie. `POST /logout` revoga família. `GET /me` lê SecurityContext. |
| 28 | Frontend axios + AuthProvider + shadcn init | `npx shadcn@latest add button input label form card sonner toast`. `lib/api.ts` com `withCredentials:true` e interceptor 401→refresh. `AuthProvider` com `status: 'loading'\|'authenticated'\|'unauthenticated'`. |
| 29 | Frontend LoginPage + tests | Form react-hook-form + Zod schema, integração com `authApi.login()`. Toast erro genérico. Redirect para `/` em sucesso. |
| 30 | Frontend router + ProtectedRoute + Layout placeholder | `react-router-dom` v6 com `createBrowserRouter`. Rota raiz redireciona para `/login` se não autenticado. |
| 31 | Smoke E2E + PR | (1) deploy HML automático funciona, (2) `curl -X POST /api/auth/login -d ...` no HML retorna 200 com cookie, (3) frontend HML faz login com sucesso. PR para `main` com squash. |

---

## Critérios de aceite do Plano 2A

- [ ] Migrations V1-V8 aplicam limpas em DB vazio e idempotente em re-execução.
- [ ] Admin seedado: `paulobof@gmail.com` com role MANAGER, password trocada do `__PENDING__` para hash real do `APP_ADMIN_INITIAL_PASSWORD`.
- [ ] `POST /api/auth/login` com credenciais corretas retorna 200, body `{accessToken, user}`, header `Set-Cookie: refresh_token=...; HttpOnly; SameSite=Strict; Path=/api/auth`.
- [ ] `POST /api/auth/login` 3x com senha errada retorna mensagem genérica; 10x ativa lockout (próximas tentativas retornam 429).
- [ ] `POST /api/auth/refresh` com cookie válido retorna novo access + rota o refresh; cookie revogado retorna 401 e revoga família.
- [ ] `GET /api/auth/me` autenticado retorna user atual; sem auth retorna 401.
- [ ] `POST /api/auth/logout` revoga refresh família.
- [ ] Frontend LoginPage faz login no HML deployado e mostra placeholder "Logado como Paulo".
- [ ] Pre-push hook passa (testes back+front verdes).
- [ ] PR mergeado em main, CI verde, deploy HML automático verde.

---

## Próximo plano

**Plano 2B — Registration + Unit Members + Moderation + MinIO upload**: introduz comprovante de residência (FileStorage abstraction + MinIO), endpoint `/api/auth/register-master`, fluxo de aprovação por COUNCIL/MANAGER, cadastro de membros pela master, `proof_access_log`, `sensitive_access_log` em endpoints de moderação.
