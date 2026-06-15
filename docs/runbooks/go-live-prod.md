# Runbook — Go-Live de Produção (primeiro deploy)

> HELBOR TRILOGY HOME — `helbortrilogyhome.com.br`
> Este runbook cobre o **primeiro** deploy de produção (infra ainda não provisionada).
> Para provisionamento detalhado do Dokploy, ver `dokploy-setup.md`.
> Para restore de banco, ver `restore-postgres.md`.

## Topologia de produção

| Componente | Domínio |
|---|---|
| Frontend | `https://www.helbortrilogyhome.com.br` (+ apex `helbortrilogyhome.com.br`) |
| API | `https://api.helbortrilogyhome.com.br` |
| Cookie domain | `helbortrilogyhome.com.br` |
| CORS allowed origins | `https://www.helbortrilogyhome.com.br`, `https://helbortrilogyhome.com.br` |

## Estado de release deste go-live

- **Branch promovida:** `main` (em sincronia com `origin/main`).
- **Migrations:** Flyway até **V37** (`__role_proprietario.sql`).
- **Feature flags:** **todas as 9 LIGADAS** — `classifieds`, `recommendations`, `announcements`, `faq`, `generalinfo`, `parkingrental`, `unitownership`, `documents`, `accessmanagement`.
  - ⚠️ `parkingrental` e `unitownership` nunca rodaram em prod. Validar com atenção no smoke test.
- **Registro de flags:** issue do GitHub (ver passo 7) conforme exigência do CLAUDE.md.

---

## 0. Pré-flight (verificado nesta sessão)

- [x] Backend: `spotless:check` + `clean package -DskipTests` → **OK** (jar gerado).
- [x] Frontend: `lint` limpo, `typecheck` limpo, **246 testes** passando, `build` OK.
- [ ] Suite de integração do backend (Testcontainers) — roda no **CI** (Docker indisponível local). Confirmar CI verde para o SHA a promover.
- [x] `git status` limpo; `main` == `origin/main`.

> Conferir o último run do CI em `main`:
> `gh run list --workflow=ci.yml --branch=main -L 3`

---

## 1. DNS

- [ ] `www.helbortrilogyhome.com.br` → frontend (Dokploy).
- [ ] `helbortrilogyhome.com.br` (apex) → redirect 301 para `www` (ou aponta ao mesmo frontend).
- [ ] `api.helbortrilogyhome.com.br` → backend (Dokploy).
- [ ] TLS emitido (Let's Encrypt via Dokploy) para os 3 hosts.

## 2. Provisionar serviços no Dokploy (environment `prod`)

Ordem obrigatória (ver `dokploy-setup.md`):

1. [ ] **PostgreSQL** — db `gestor_condominio`, user `condominio`, senha do passo 3. Backup diário (retenção 7d + 1 mensal).
2. [ ] **MinIO** — compose `deploy/dokploy-minio-compose.yml`; `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` do passo 3. Buckets criados pelo backend no boot.
3. [ ] **Backend** — build path `./backend`, domain `api.helbortrilogyhome.com.br`, healthcheck `/actuator/health/readiness` (30s). Env vars do passo 4. Gerar e anotar **webhook**.
4. [ ] **Frontend** — build path `./frontend`, domain `www.helbortrilogyhome.com.br`, build arg `VITE_API_BASE_URL=https://api.helbortrilogyhome.com.br`. Gerar e anotar **webhook**.
5. [ ] **Observabilidade** (só prod, por último) — compose `deploy/dokploy-observability-compose.yml`. Grafana atrás de Basic Auth; Alertmanager com SMTP real.

## 3. Gerar segredos (nunca commitar)

```bash
openssl rand -base64 32   # APP_PASSWORD_PEPPER
openssl rand -base64 32   # APP_JWT_KEYS  (prefixar com "v1:")
openssl rand -base64 32   # POSTGRES_PASSWORD
openssl rand -base64 32   # MINIO_SECRET_KEY
openssl rand -base64 24   # APP_ADMIN_INITIAL_PASSWORD
```

- [ ] `APP_WHATSAPP_API_KEY` — pegar da instância Evolution (`Bot-Robo`). Não logar.

## 4. Env vars

- [ ] **Backend** — colar de `deploy/dokploy-backend.env.example` (já com domínios reais e **9 flags = true**), substituindo os `GERAR_*` pelos segredos do passo 3.
  - Confirmar: `SPRING_PROFILES_ACTIVE=prod`, `APP_COOKIE_SECURE=true`, **sem** `APP_SEED_FAKE_DATA` (proibido em prod).
  - Confirmar valores reais: `APP_DPO_EMAIL`, `APP_CONTROLLER_CNPJ`, `APP_ALERTS_WHATSAPP_GROUP_JID`.
- [ ] **Frontend** — `VITE_API_BASE_URL=https://api.helbortrilogyhome.com.br` (de `deploy/dokploy-frontend.env.example`).

## 5. GitHub Secrets & Environments

- [ ] Secrets: `DOKPLOY_PROD_WEBHOOK`, `DOKPLOY_PROD_TOKEN` (e os `*_HML_*` se ainda não existirem).
- [ ] Environment `production` com **required reviewer = paulobof**.
- [ ] Environment `hml` (auto-deploy, sem aprovação).

## 6. Primeiro deploy & verificação

1. [ ] Deploy na ordem do passo 2.
2. [ ] `curl -fs https://api.helbortrilogyhome.com.br/actuator/health/readiness` → `UP`.
3. [ ] Flyway aplicou até **V37** (checar logs do backend; tabela `flyway_schema_history`).
4. [ ] Buckets MinIO criados (`residence-proofs`, `classifieds`, `recommendations`, `documents`).
5. [ ] Frontend carrega em `https://www.helbortrilogyhome.com.br` (sem erro de CORS no console).
6. [ ] **Swagger desligado** em prod: `https://api.helbortrilogyhome.com.br/swagger-ui.html` → 404.

### Smoke test (login admin + flags)

- [ ] Login admin (`paulo.tamara.casal@gmail.com`) com `APP_ADMIN_INITIAL_PASSWORD` → forçar troca de senha.
- [ ] Cada feature aparece e responde (não-404): FAQ, Informações gerais, Avisos, Classificados, Indicações, Documentos, Vagas (parkingrental), Acessos (accessmanagement), cadastro de proprietário (unitownership).
- [ ] Upload de comprovante (≤5MB) e foto (≤1MB) funcionam (MinIO presigned).
- [ ] Envio de uma mensagem WhatsApp de teste (outbox processa).

## 7. Registrar feature flags (CLAUDE.md)

- [ ] Issue do GitHub registrando as 9 flags `false → true` (actor, flag, from, to, motivo). Criada nesta sessão — ver passo de artefatos.

## 8. Pós go-live

- [ ] Confirmar backup do Postgres rodou (job diário).
- [ ] Confirmar alertas Prometheus ativos (`BackendDown`, `HighLoginFailureRate`, `WhatsAppDeliveryDegraded`).
- [ ] Service worker / PWA: validar que não serve bundle antigo (hard refresh).
- [ ] Tag de release criada pelo workflow (`vYYYY.MM.DD-HHmm`).

---

## Rollback

- **App:** redeploy do último SHA/tag bom via Dokploy (ou `promote-to-prod` com a tag anterior).
- **Banco:** migrations são expand/contract (backward-compatible) → rollback de app não exige rollback de schema. Para corrupção de dados, ver `restore-postgres.md`.
- **Feature isolada com problema:** desligar a flag específica no Dokploy (`APP_FEATURE_<X>_ENABLED=false`) + redeploy backend, registrando na issue. Não precisa reverter código.

## Promoção via workflow (após HML)

O `promote-to-prod.yml` exige que o SHA tenha passado no CI e soake ≥ 30 min em HML. Para o **primeiro** deploy, a infra de prod (passos 1–5) precisa existir antes de o workflow conseguir o readiness check em `api.helbortrilogyhome.com.br`.

```
Actions → promote-to-prod → Run workflow → version = <SHA da main> → aprovar no environment production
```
