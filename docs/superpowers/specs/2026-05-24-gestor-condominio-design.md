# Gestor de Condomínio — Design

**Data:** 2026-05-24
**Status:** Aprovado pelo usuário; aguardando revisão final do documento escrito.
**Autor:** Sessão de brainstorming (Claude + Paulo).
**Repositório:** `git@github.com:paulobof/gestor-condominio.git`

---

## 1. Visão geral

Sistema web para gestão interna de um único condomínio residencial. Foco MVP: auto-cadastro de moradores com aprovação humana e comprovante de residência, classificados com fotos, telefones úteis, links úteis e recomendações/indicações de profissionais.

**Stack:**

- Backend: Java 21 + Spring Boot 3 + Lombok + Spring Security (JWT) + Spring Data JPA + Flyway.
- Banco: PostgreSQL.
- Storage de arquivos: MinIO (S3-compatible).
- Frontend: Vite + React 18 + TypeScript + Tailwind CSS + shadcn/ui.
- Deploy: Dokploy (panel.paulobof.com.br) — backend, frontend, postgres e minio como serviços independentes no mesmo *environment*.
- Build: Maven (backend), npm (frontend).
- Pre-commit: Husky + lint-staged + commitlint + ESLint/Prettier (frontend) + Spotless (backend).

**Princípios obrigatórios** (aplicados em todo código): SOLID, KISS, STRIDE (threat modeling), POO (orientação a objetos com domínio rico, não DTOs anêmicos).

**Idioma:** código (entidades, campos, endpoints) em inglês; UI em português brasileiro.

**Escopo MVP:**

1. Auto-cadastro com aprovação + comprovante de residência.
2. Autenticação JWT com refresh token; senha protegida com pepper + bcrypt.
3. Gestão de usuários e roles (ADMIN/RESIDENT/DOORMAN).
4. Classificados com fotos (upload MinIO, URLs pré-assinadas).
5. Telefones úteis (CRUD).
6. Links úteis (CRUD).
7. Recomendações/indicações de profissionais.

**Roadmap futuro (fora de escopo):** sorteio de vagas de garagem, reservas de áreas comuns, ocorrências/chamados, boletos/cobranças, mural de avisos.

---

## 2. Arquitetura

### 2.1 Estrutura de pastas (monorepo)

```
gestor-condominio/
├── backend/                              Spring Boot 3 + Java 21 (Dockerfile, pom.xml)
│   └── src/main/java/br/com/condominio/
│       ├── GestorCondominioApplication.java
│       ├── config/                       Security, MinIO, OpenAPI, CORS, Bucket4j
│       ├── shared/                       GlobalExceptionHandler, base DTOs, utils
│       ├── storage/                      MinioStorageService (abstração de upload/presigned URL)
│       └── feature/
│           ├── auth/                     login, refresh, register, me, logout
│           ├── user/                     CRUD admin de usuários, gestão de roles
│           ├── registration/             moderação de cadastros pendentes
│           ├── classified/               classificados + fotos
│           ├── contact/                  telefones úteis
│           ├── link/                     links úteis
│           └── recommendation/           indicações
├── frontend/                             Vite + React (Dockerfile, package.json)
│   └── src/
│       ├── main.tsx, App.tsx, router.tsx
│       ├── lib/                          api (axios), auth, utils
│       ├── components/                   ui/ (shadcn), Layout, ProtectedRoute, RoleGate
│       └── features/<name>/              pages + components + hooks + api
├── deploy/
│   ├── dokploy-backend.env.example
│   ├── dokploy-frontend.env.example
│   ├── dokploy-minio-compose.yml
│   └── README.md                         passo-a-passo Dokploy
├── docs/superpowers/specs/               este documento e os futuros
├── .husky/                               pre-commit, commit-msg, pre-push
├── package.json                          devDeps de husky/lint-staged/commitlint
├── commitlint.config.cjs
├── .lintstagedrc.json
├── .editorconfig
├── .gitignore
├── docker-compose.dev.yml                Postgres + MinIO local para dev
├── CLAUDE.md                             convenções para futuras sessões
└── README.md
```

**Organização por feature** (package-by-feature): cada feature contém suas entidades, repositórios, serviços, controllers e DTOs juntos. Facilita adicionar e remover módulos, reduz acoplamento.

### 2.2 Fluxo de requisição

```
Browser  →  Nginx (frontend estático SPA)  →  chamadas /api/*  →  Backend Spring Boot
                                                                       │
                                                                       ├── Postgres (JPA)
                                                                       └── MinIO (S3 SDK)
```

Comunicação entre serviços Dokploy via DNS interno (nomes dos serviços do *environment*).

---

## 3. Modelo de dados

### 3.1 Entidades

Todas as tabelas têm auditoria: `id` (UUID PK), `created_at`, `updated_at` (timestamptz), `created_by_user_id`, `updated_by_user_id`.

**Soft delete obrigatório em todas as tabelas** (regra global do projeto, NUNCA hard delete):

- `deleted_at` (timestamptz, null = ativo).
- `deleted_by_user_id` (uuid FK user, null = nunca deletado).

Implementação Hibernate: cada entidade usa `@SQLDelete(sql = "UPDATE <tabela> SET deleted_at = now(), deleted_by_user_id = ? WHERE id = ?")` e `@Where(clause = "deleted_at IS NULL")`. Repositórios automaticamente filtram registros ativos. Endpoints `DELETE` retornam 204 mas executam apenas o UPDATE.

**Uniqueness com soft delete:** índices únicos parciais. Ex. para `user.email`:
```sql
CREATE UNIQUE INDEX ux_user_email_active ON "user"(email) WHERE deleted_at IS NULL;
```
Permite reusar o mesmo e-mail após soft delete sem violar a constraint.

**user** — usuário do sistema (todos os papéis).

| Coluna                          | Tipo            | Notas                                                            |
| ------------------------------- | --------------- | ---------------------------------------------------------------- |
| id                              | uuid PK         |                                                                  |
| name                            | varchar(120)    | obrigatório                                                      |
| email                           | varchar(180)    | único, indexado                                                  |
| password_hash                   | varchar(255)    | bcrypt(HMAC-SHA256(senha, pepper))                               |
| password_pepper_version         | smallint        | default 1, permite rotação                                       |
| must_change_password            | boolean         | true para admin inicial; false após reset                        |
| apartment                       | varchar(40)     | ex. "Bl A apto 102"                                              |
| phone                           | varchar(20)     | opcional                                                         |
| status                          | enum            | PENDING_APPROVAL, ACTIVE, REJECTED, DISABLED                     |
| residence_proof_object_key      | varchar(255)    | MinIO key no bucket `residence-proofs`; null para usuários internos criados pelo admin |
| residence_proof_filename        | varchar(255)    | nome original para exibição                                      |
| residence_proof_content_type    | varchar(80)     | image/jpeg, image/png, application/pdf                           |
| approved_by_user_id             | uuid FK user    | preenchido no approve                                            |
| approved_at                     | timestamptz     |                                                                  |
| rejection_reason                | text            | preenchido no reject                                             |

**role** — `ADMIN`, `RESIDENT`, `DOORMAN`. Tabela seed.

**user_role** — junção M:N entre `user` e `role` (suporta múltiplos papéis por usuário; admin pode ser também resident).

**classified** — anúncio entre moradores.

| Coluna             | Tipo               | Notas                                  |
| ------------------ | ------------------ | -------------------------------------- |
| id                 | uuid PK            |                                        |
| title              | varchar(120)       |                                        |
| description        | text               |                                        |
| price              | numeric(12,2)      | nullable (anúncios de doação)          |
| status             | enum               | ACTIVE, SOLD, ARCHIVED                 |
| author_user_id     | uuid FK user       | ON DELETE RESTRICT                     |

**classified_photo**

| Coluna           | Tipo            |
| ---------------- | --------------- |
| id               | uuid PK         |
| classified_id    | uuid FK         |
| object_key       | varchar(255)    | bucket `classifieds`                   |
| content_type     | varchar(80)     |
| ordering         | int             | ordenação na galeria                   |

**contact** — telefones úteis. Campos: `name`, `category`, `phone`, `notes`.

**link** — links úteis. Campos: `title`, `url`, `description`, `category`.

**recommendation** — indicações.

| Coluna                  | Tipo            |
| ----------------------- | --------------- |
| id                      | uuid PK         |
| service_name            | varchar(120)    | ex. "Pintor"                          |
| professional_name       | varchar(120)    | ex. "Carlos Lima"                     |
| phone                   | varchar(20)     | opcional                              |
| rating                  | smallint        | 1–5                                   |
| comment                 | text            |                                       |
| recommended_by_user_id  | uuid FK user    | ON DELETE RESTRICT                    |

**refresh_token** — para JWT refresh stateful.

| Coluna       | Tipo         |
| ------------ | ------------ |
| id           | uuid PK      |
| user_id      | uuid FK      |
| token_hash   | varchar(255) | SHA-256 do token, nunca o token claro |
| expires_at   | timestamptz  |                                       |
| revoked      | boolean      | default false                         |

### 3.2 Mapeamento STRIDE no modelo

- **Spoofing** — `password_hash` com bcrypt(HMAC-SHA256(senha, pepper)); JWT assinado HS256; aprovação humana valida identidade real do morador.
- **Tampering** — `updated_by_user_id` em mutações; FKs `ON DELETE RESTRICT` para preservar histórico; `approved_by_user_id` registra autoria da aprovação; soft delete preserva todos os registros (nada é fisicamente removido).
- **Repudiation** — colunas de auditoria; `deleted_by_user_id` + `deleted_at` rastreiam autor e momento de qualquer exclusão; log estruturado em INFO de login, approve, reject, role change e soft delete.
- **Information disclosure** — `password_hash` nunca em response (`@JsonIgnore`); comprovantes em bucket isolado com acesso só por ADMIN; URLs pré-assinadas TTL curto; object key é UUID (sem nome original na URL).
- **DoS** — limites de tamanho em todos os campos texto; upload máx 5MB; Bucket4j em `/api/auth/login` (5/min/IP) e `/api/auth/register` (3/h/IP).
- **Elevation of privilege** — auto-registro sempre cria com role `RESIDENT`; promoção a ADMIN/DOORMAN só via endpoint de admin existente; `@PreAuthorize` em endpoints sensíveis.

### 3.3 Migrações Flyway

`backend/src/main/resources/db/migration/`:

- `V1__create_user_and_role.sql` — tabelas `user`, `role`, `user_role`, `refresh_token` (com todas as colunas de status/aprovação/comprovante e soft delete). Inclui `CREATE UNIQUE INDEX ux_user_email_active ON "user"(email) WHERE deleted_at IS NULL;` para permitir reuso de e-mail após soft delete.
- `V2__create_classified.sql` — `classified` e `classified_photo`.
- `V3__create_contact_and_link.sql`.
- `V4__create_recommendation.sql`.
- `V5__seed_roles.sql` — insere ADMIN, RESIDENT, DOORMAN.
- `V6__seed_admin.sql` — insere usuário admin com `password_hash = '__PENDING__'` e `must_change_password = true`; usa placeholders Flyway `${adminEmail}` e `${adminName}` preenchidos a partir de env vars `APP_ADMIN_EMAIL` e `APP_ADMIN_NAME` via `spring.flyway.placeholders`.

**Bootstrap do hash do admin:** `AdminBootstrap` (ApplicationRunner) na inicialização do Spring detecta `password_hash = '__PENDING__'` e gera o hash real usando `PepperedBCryptPasswordEncoder.encode(APP_ADMIN_INITIAL_PASSWORD)`. Idempotente: só roda se ainda for `__PENDING__`.

---

## 4. API e autenticação

### 4.1 Autenticação JWT

- **Access token**: JWT HS256, TTL 15 min, claims `sub` (userId), `roles`, `exp`.
- **Refresh token**: JWT TTL 7 dias; hash SHA-256 armazenado em `refresh_token`. Logout marca `revoked = true`. Rotação no refresh.
- **Senha**: `bcrypt(HMAC-SHA256(senha, PEPPER), salt)` com cost 12.
- **Pepper**: 32 bytes em base64, env `APP_PASSWORD_PEPPER`. Coluna `password_pepper_version` permite rotação; pepper antigo configurável em `APP_PASSWORD_PEPPER_OLD_V<n>`. Re-hash transparente no login com pepper antigo.

### 4.2 Endpoints públicos (sem token)

```
POST /api/auth/login            { email, password }                 → { accessToken, refreshToken, user }
POST /api/auth/register         multipart: campos + residenceProof  → 202 Accepted { id, status:PENDING_APPROVAL }
POST /api/auth/refresh          { refreshToken }                    → { accessToken, refreshToken }
```

Rate-limited via Bucket4j (login 5/min/IP, register 3/h/IP).

### 4.3 Endpoints autenticados

```
GET    /api/auth/me                                                 self
POST   /api/auth/logout                                             self

# Auto-serviço do usuário
PUT    /api/users/me                                                self
PUT    /api/users/me/password                                       self

# Admin de usuários
POST   /api/users                          criar interno (doorman)  ADMIN
GET    /api/users                                                   ADMIN
GET    /api/users/{id}                                              ADMIN | self
PUT    /api/users/{id}                                              ADMIN
DELETE /api/users/{id}                     soft delete              ADMIN
PUT    /api/users/{id}/roles               { roles: [...] }         ADMIN

# Moderação de cadastros
GET    /api/registrations?status=PENDING_APPROVAL                   ADMIN
GET    /api/registrations/{id}                                      ADMIN
GET    /api/registrations/{id}/proof-url   presigned URL TTL 5min   ADMIN
POST   /api/registrations/{id}/approve                              ADMIN
POST   /api/registrations/{id}/reject      { reason }               ADMIN

# Classificados
GET    /api/classifieds                                             any logado
GET    /api/classifieds/{id}                                        any logado
POST   /api/classifieds                                             any (autor = self)
PUT    /api/classifieds/{id}                                        author | ADMIN
DELETE /api/classifieds/{id}                                        author | ADMIN
POST   /api/classifieds/{id}/photos        multipart                author | ADMIN
DELETE /api/classifieds/{id}/photos/{photoId}                       author | ADMIN
GET    /api/classifieds/{id}/photos/{photoId}/url  presigned URL    any logado

# Telefones úteis
GET    /api/contacts                                                any logado
POST   /api/contacts                                                ADMIN
PUT    /api/contacts/{id}                                           ADMIN
DELETE /api/contacts/{id}                                           ADMIN

# Links úteis
GET    /api/links                                                   any logado
POST   /api/links                                                   ADMIN
PUT    /api/links/{id}                                              ADMIN
DELETE /api/links/{id}                                              ADMIN

# Recomendações
GET    /api/recommendations                                         any logado
POST   /api/recommendations                                         any (autor = self)
PUT    /api/recommendations/{id}                                    author | ADMIN
DELETE /api/recommendations/{id}                                    author | ADMIN
```

Login rejeita usuário com `status != ACTIVE`, com mensagens distintas (pending/rejected/disabled).

### 4.4 Padrões POO/SOLID nos controllers/services

- Cada feature: `XxxController` (apenas HTTP), `XxxService` (regra de negócio), `XxxRepository` (Spring Data JPA), `XxxMapper` (entity ↔ DTO).
- DTOs: `XxxCreateRequest`, `XxxUpdateRequest`, `XxxResponse`. Validação Jakarta Bean Validation (`@NotBlank`, `@Email`, `@Size`, etc.).
- `XxxResponse` nunca expõe `passwordHash`, `residenceProofObjectKey` (para não-admin), nem outros campos sensíveis.
- Domínio rico: lógicas como "ativar usuário", "rejeitar cadastro", "publicar classificado" são métodos da entidade (`user.approve(approver)`, `user.reject(reason)`), não somente setters chamados do service.

### 4.5 Tratamento de erros

`GlobalExceptionHandler` (`@RestControllerAdvice`) retorna formato uniforme:

```json
{
  "timestamp": "2026-05-24T19:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Verifique os campos.",
  "fields": [{ "field": "email", "message": "deve ser um email válido" }]
}
```

Mapeamento:

- `MethodArgumentNotValidException` → 400 + `fields`.
- `AccessDeniedException` → 403.
- `EntityNotFoundException` → 404.
- `IllegalStateException` (regra de negócio) → 409.
- `Exception` genérica → 500, sem stack ao cliente; log completo no servidor.

### 4.6 Documentação

`springdoc-openapi-starter-webmvc-ui` em `/swagger-ui.html`.

### 4.7 CORS

Origens permitidas configuráveis via `APP_CORS_ALLOWED_ORIGINS` (CSV). Dev: `http://localhost:5173`. Produção: domínio do frontend.

---

## 5. Frontend

### 5.1 Estrutura

```
frontend/
├── package.json (npm)
├── vite.config.ts            proxy /api → backend em dev
├── tsconfig.json
├── tailwind.config.ts, postcss.config.js
├── components.json           config shadcn/ui
├── index.html
├── nginx.conf                produção
├── Dockerfile                multi-stage (Node build → Nginx)
└── src/
    ├── main.tsx, App.tsx, router.tsx
    ├── lib/                  api.ts (axios + interceptors), auth.ts, utils.ts
    ├── components/
    │   ├── ui/               shadcn gerados localmente
    │   ├── Layout.tsx, ProtectedRoute.tsx, RoleGate.tsx
    ├── features/
    │   ├── auth/             LoginPage, RegisterPage (upload), PendingApprovalPage, ChangePasswordPage
    │   ├── classifieds/      List, Detail, Form (upload de fotos)
    │   ├── contacts/         List, Form
    │   ├── links/            List, Form
    │   ├── recommendations/  List, Form
    │   └── admin/            PendingRegistrationsPage, UsersPage
    └── types/                DTOs espelhando backend
```

### 5.2 Bibliotecas

**Runtime:** `react`, `react-dom`, `react-router-dom`, `@tanstack/react-query`, `axios`, `react-hook-form`, `zod`, `@hookform/resolvers`, `tailwindcss`, `clsx`, `tailwind-merge`, shadcn/ui (componentes locais), `lucide-react`, `date-fns`, `next-themes`.

**Dev:** `vite`, `typescript`, `@types/react`, `@types/react-dom`, `eslint` v9 + plugins (`@typescript-eslint`, `eslint-plugin-react`, `eslint-plugin-react-hooks`, `eslint-plugin-jsx-a11y`, `eslint-plugin-import`), `prettier`, `eslint-config-prettier`, `vitest` + `@testing-library/react` + `@testing-library/jest-dom` + `jsdom` (instalados desde o MVP para o hook pre-push funcionar com `--passWithNoTests`).

### 5.3 Estado e padrões

- Server state → React Query.
- Auth global → `AuthProvider` (Context). `refreshToken` em `localStorage`, `accessToken` em memória.
- Form state → React Hook Form + Zod.
- UI local → `useState`.

### 5.4 Fluxo de autenticação no cliente

1. Interceptor anexa `Authorization: Bearer ...` em todo request.
2. Resposta 401 → tenta `/api/auth/refresh`; sucesso retenta a request original; falha desloga e redireciona `/login`.
3. `ProtectedRoute` exige user autenticado; `RoleGate` filtra UI/rotas por role.

### 5.5 Rotas

```
Públicas       /login, /register, /pending-approval, /change-password (apenas se must_change_password)
Protegidas     /, /classifieds, /classifieds/new, /classifieds/:id, /contacts, /links, /recommendations
ADMIN-only     /admin/registrations, /admin/users, /admin/contacts/manage, /admin/links/manage
```

### 5.6 SOLID/POO no frontend

- **SRP**: `*Api.ts` só faz HTTP; `pages/*Page.tsx` só compõe; lógica em `hooks/use*.ts`.
- **DIP**: componentes usam hooks (`useClassifieds`), não `axios` direto.
- **OCP**: `RoleGate` recebe roles via prop; extensível sem editar.
- Componentes < 150 linhas; split quando crescer.

### 5.7 i18n

pt-BR hardcoded no MVP; `Intl.NumberFormat('pt-BR')` para preços, `date-fns/locale/ptBR` para datas. Estrutura compatível com `react-i18next` no futuro.

---

## 6. Deploy (Dokploy)

### 6.1 Serviços já provisionados no painel

- **Backend** (app service) — build path `./backend`, Dockerfile.
- **Frontend** (app service) — build path `./frontend`, Dockerfile.
- **PostgreSQL** (postgres service) — credenciais geradas pelo Dokploy.
- **MinIO** (compose service) — via `deploy/dokploy-minio-compose.yml` versionado no repo.

Todos no mesmo *environment*; comunicação por nome de serviço.

### 6.2 Configuração de cada serviço

**Backend**

- Multi-stage Dockerfile (Maven build → JRE 21 slim).
- Porta interna 8080; Traefik com domínio `api.<dominio>`.
- Healthcheck `/actuator/health`.
- Env vars no painel Dokploy (template em `deploy/dokploy-backend.env.example`):
  - `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
  - `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET_CLASSIFIEDS`, `MINIO_BUCKET_PROOFS`, `MINIO_PRESIGNED_TTL_SECONDS`
  - `APP_ADMIN_EMAIL=paulobof@gmail.com`, `APP_ADMIN_NAME`, `APP_ADMIN_INITIAL_PASSWORD`
  - `APP_PASSWORD_PEPPER`, `APP_BCRYPT_STRENGTH=12`
  - `APP_JWT_SECRET`, `APP_JWT_ACCESS_TTL=PT15M`, `APP_JWT_REFRESH_TTL=P7D`
  - `APP_CORS_ALLOWED_ORIGINS`

**Frontend**

- Multi-stage Dockerfile (Node 22 build → Nginx alpine).
- Porta interna 80; Traefik com domínio raiz.
- Build arg `VITE_API_BASE_URL=https://api.<dominio>`.
- Nginx: SPA fallback `try_files $uri /index.html`, gzip, cache de assets, headers de segurança (CSP, X-Frame-Options DENY, Referrer-Policy).

**Postgres**

- Database `gestor_condominio`. Backend recebe credenciais via env do painel.

**MinIO**

- Compose com volume `minio-data`. Console em `:9001` protegido por domínio Traefik com basic auth ou IP allowlist (decisão na implementação).
- Buckets `classifieds` e `residence-proofs` criados pelo backend via `MinioBootstrap` (idempotente) na inicialização.

### 6.3 Ordem de deploy

1. Postgres (já provisionado).
2. MinIO (já provisionado).
3. Backend (após Postgres e MinIO acessíveis).
4. Frontend (independe, mas precisa da URL do backend no build).

### 6.4 Acesso ao servidor

Chave SSH local em `C:\Users\paulo\Downloads\pc_paulo_private_id_rsa.txt` (privada — **NUNCA commitar, ler ou imprimir o conteúdo**). Host/usuário do servidor a confirmar antes do primeiro uso. Toda ação destrutiva via SSH requer confirmação do usuário.

---

## 7. Testes

Estratégia escolhida: **testes básicos só nas regras** (services).

- JUnit 5 + Mockito + AssertJ no backend.
- Sem `@SpringBootTest`, sem Testcontainers no MVP — mocks de repositórios (KISS).
- Cobertura mínima: caminho feliz + 1-2 erros principais por service.
- **Exceção crítica**: `PepperedBCryptPasswordEncoder` tem teste real, sem mocks (segurança).

Localização:

```
backend/src/test/java/br/com/condominio/
├── feature/auth/AuthServiceTest.java
├── feature/registration/RegistrationServiceTest.java
├── feature/user/UserServiceTest.java
├── feature/classified/ClassifiedServiceTest.java
├── feature/contact/ContactServiceTest.java
├── feature/link/LinkServiceTest.java
├── feature/recommendation/RecommendationServiceTest.java
└── shared/security/PepperedBCryptPasswordEncoderTest.java
```

Frontend: Vitest instalado e configurado (script `npm test` rodando `vitest run --passWithNoTests`). Sem testes escritos no MVP — estrutura pronta para receber testes incrementalmente.

---

## 8. CI/CD

`.github/workflows/ci.yml`:

- PR para `main`: build backend (`mvn -B verify`), build frontend (`npm ci && npm run build && npm run lint && npm run typecheck`).
- Push para `main`: aciona webhook Dokploy → deploy automático.

Webhook URL por serviço configurado no painel Dokploy.

---

## 9. Qualidade de código (pre-commit)

### 9.1 Ferramentas

- **Husky** v9 — git hooks gerenciados.
- **lint-staged** v15 — roda lint só nos arquivos staged.
- **commitlint** + `@commitlint/config-conventional` — conventional commits obrigatório.
- **ESLint** v9 (flat config) + **Prettier** + `eslint-config-prettier` — frontend.
- **Spotless** + `google-java-format` (Maven plugin) — backend.

### 9.2 Hooks

- `.husky/pre-commit` → `npx lint-staged`.
- `.husky/commit-msg` → `npx --no -- commitlint --edit "$1"`.
- `.husky/pre-push` → `npm run test:backend && npm run test:frontend`.

### 9.3 `package.json` raiz (somente devDeps de hooks)

```json
{
  "name": "gestor-condominio",
  "private": true,
  "scripts": {
    "prepare": "husky",
    "test:backend": "cd backend && ./mvnw test -q",
    "test:frontend": "cd frontend && npm test -- --run --passWithNoTests",
    "lint:backend": "cd backend && ./mvnw spotless:check",
    "format:backend": "cd backend && ./mvnw spotless:apply"
  },
  "devDependencies": {
    "husky": "^9",
    "lint-staged": "^15",
    "@commitlint/cli": "^19",
    "@commitlint/config-conventional": "^19"
  }
}
```

### 9.4 Convenção de commits

`feat | fix | chore | docs | refactor | test | style | perf | ci | build`. Ex: `feat(classified): adicionar upload de fotos`.

### 9.5 .editorconfig

Indentação 2 spaces TS, 4 spaces Java; LF; UTF-8.

---

## 10. Variáveis de ambiente (template `.env.example` para dev local)

```
# Admin inicial
APP_ADMIN_EMAIL=paulobof@gmail.com
APP_ADMIN_NAME=Paulo
APP_ADMIN_INITIAL_PASSWORD=troque-no-primeiro-login

# Segurança de senha
APP_PASSWORD_PEPPER=base64-32-bytes-aqui    # gerar: openssl rand -base64 32
APP_BCRYPT_STRENGTH=12

# JWT
APP_JWT_SECRET=base64-32-bytes-aqui
APP_JWT_ACCESS_TTL=PT15M
APP_JWT_REFRESH_TTL=P7D

# CORS
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173

# Postgres
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=gestor_condominio
POSTGRES_USER=condominio
POSTGRES_PASSWORD=troque

# MinIO
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=condominio
MINIO_SECRET_KEY=troque
MINIO_BUCKET_CLASSIFIEDS=classifieds
MINIO_BUCKET_PROOFS=residence-proofs
MINIO_PRESIGNED_TTL_SECONDS=600
```

`.env` **nunca** commitado (`.gitignore` já cobre); `.env.example` versionado com placeholders.

---

## 11. Out-of-scope (explícito)

Não entram no MVP, ficam para release futura:

- Sorteio de vagas de garagem.
- Reservas de áreas comuns (salão, churrasqueira).
- Ocorrências/chamados de manutenção.
- Boletos/cobranças mensais e controle de inadimplência.
- Mural de avisos / comunicados.
- Notificações por e-mail/push.
- Antivírus em comprovantes (ClamAV).
- Multi-tenancy (múltiplos condomínios).
- App mobile.
- Tests E2E e Testcontainers.

---

## 12. Riscos e mitigações

| Risco                                                 | Mitigação                                                                                     |
| ----------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| Dump do banco vaza hashes de senha                    | Pepper armazenado em env, fora do banco; hashes inúteis sem o pepper.                          |
| Comprovante de residência vaza (privacidade/LGPD)     | Bucket isolado, presigned URL TTL 5min, acesso só por ADMIN, object key UUID.                  |
| Auto-registro abusado por bots                        | Rate-limit Bucket4j (3/h/IP), aprovação manual humana antes de ACTIVE.                         |
| Escalonamento de privilégio                           | Auto-registro hardcoded para role RESIDENT; `@PreAuthorize` em endpoints de admin.             |
| Senha admin inicial vaza (em logs/env)                | `must_change_password=true` força troca no primeiro login; senha inicial nunca persiste.       |
| Deploy quebra produção                                | Healthcheck Spring Actuator; rollback automático Dokploy se healthcheck falha; CI antes do PR. |
| Testes ignorados por bypass                           | CI roda testes obrigatoriamente; pre-push é segunda linha.                                     |
| Perda acidental de dados                              | Soft delete obrigatório em toda tabela; `deleted_at` e `deleted_by_user_id` registram quem/quando; nada é fisicamente removido. |

---

## 13. Próximo passo

Após aprovação deste documento pelo usuário, invocar a skill `superpowers:writing-plans` para gerar o plano de implementação detalhado (com fases, ordem, critérios de aceite por step).
