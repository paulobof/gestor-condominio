# Runbook — Dokploy Setup (HML + Prod)

> Procedimento manual a executar no painel `panel.paulobof.com.br` para destravar o `deploy-hml` automático e a promoção para produção.

**Pré-requisitos:**
- Acesso ao painel Dokploy.
- Acesso ao GitHub do repo `paulobof/gestor-condominio`.
- Domínios configuráveis (subdomínios DNS apontando para o servidor Dokploy).

**Tempo estimado:** ~45 min.

---

## Passo 1 — Inventariar o que já existe em `prod`

Os 4 serviços já provisionados (ver `[[reference-dokploy-gestor-condominio]]`):

| Serviço | ID | Tipo |
|---|---|---|
| Backend prod | `2_XeVTFMJHdlSxulXUb8t` | application |
| Frontend prod | `MgQYN-8HQSnst0ayuCQkj` | application |
| PostgreSQL prod | `MlkOyRA-hKUI-bgnPkAcp` | postgres |
| MinIO prod | `af8M8nbBi2Z_9xUdNHZG8` | compose |

**Project ID:** `wTGVAmU9KA1zf02p2mse-`
**Environment Prod ID:** `0HS4YtT4b2vcyL71sxj-U`

---

## Passo 2 — Configurar `Backend prod`

1. Acesse o serviço backend prod.
2. **Source / Provider**: GitHub `paulobof/gestor-condominio`.
3. **Branch**: `main`.
4. **Build Path**: `./backend`.
5. **Dockerfile**: `Dockerfile`.
6. **Domain**: `api.<seu-domínio>` — registre o certificado TLS.
7. **Healthcheck**: `GET /actuator/health/readiness`, intervalo 30s.
8. **Environment Variables**: copie tudo de `deploy/dokploy-backend.env.example` no repo e:
   - **Mantenha** `SPRING_PROFILES_ACTIVE=prod`.
   - **Gere segredos** (via terminal local):
     ```bash
     openssl rand -base64 32   # APP_PASSWORD_PEPPER
     openssl rand -base64 32   # APP_JWT_KEYS (anexar como v1:<saída>)
     openssl rand -base64 32   # APP_WHATSAPP_HMAC_KEYS
     openssl rand -base64 32   # POSTGRES_PASSWORD
     openssl rand -base64 32   # MINIO_SECRET_KEY
     openssl rand -base64 24   # APP_ADMIN_INITIAL_PASSWORD
     ```
   - Preencha `POSTGRES_HOST` com o nome do serviço Postgres no Dokploy (rede interna; o painel mostra).
   - Preencha `MINIO_ENDPOINT=http://<nome-serviço-minio>:9000`.
   - Substitua `app.helbor.exemplo` / `api.helbor.exemplo` pelos seus domínios reais nas vars de URL.
9. **Webhook de deploy**: gerar no painel (Section *Deploy / Webhook*) e **copiar a URL + token**.

---

## Passo 3 — Configurar `Frontend prod`

1. **Build Path**: `./frontend`.
2. **Domain**: `app.<seu-domínio>`.
3. **Build args / env de build**: `VITE_API_BASE_URL=https://api.<seu-domínio>`.
4. **Webhook**: gerar e anotar.

---

## Passo 4 — Configurar `PostgreSQL prod`

1. **Database name**: `gestor_condominio`.
2. **User**: `condominio`.
3. **Password**: o mesmo que você gerou em `POSTGRES_PASSWORD` no Passo 2.
4. **Backup automático**: ativar diário; reter 7 dias.

---

## Passo 5 — Configurar `MinIO prod` (compose)

1. Atualizar o arquivo do compose com o conteúdo de `deploy/dokploy-minio-compose.yml` (se já não estiver).
2. Env vars do compose:
   - `MINIO_ROOT_USER=condominio`
   - `MINIO_ROOT_PASSWORD=<MINIO_SECRET_KEY do Passo 2>`
3. **Console**: expor `:9001` num subdomínio (`minio.<seu-domínio>`) protegido por Basic Auth do Traefik OU IP allowlist.

---

## Passo 6 — Criar environment `hml` (novo)

No painel:

1. **Create Environment** → nome `hml`.
2. Duplicar a estrutura de prod com 4 serviços:
   - `backend-hml` (application, mesmo repo+branch+build path)
   - `frontend-hml` (application)
   - `postgres-hml` (postgres)
   - `minio-hml` (compose)
3. Domínios distintos:
   - `hml.api.<seu-domínio>`
   - `hml.app.<seu-domínio>`
4. **Env vars do backend-hml**: copie de `deploy/dokploy-backend.env.example` e altere:
   - `SPRING_PROFILES_ACTIVE=hml`
   - **Outros segredos diferentes** dos de prod (gere novos).
   - `APP_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/testdata` (preparação para seed sintético no Plano 2).
   - `APP_SEED_FAKE_DATA=true`.
   - `APP_WHATSAPP_WEBHOOK_URL=https://bot-sandbox.../send-message` ou um mock interno.
5. **Frontend-hml**: `VITE_API_BASE_URL=https://hml.api.<seu-domínio>`.
6. **Webhooks de deploy de cada serviço HML**: gerar e anotar.

---

## Passo 7 — (Opcional, mas recomendado) Observabilidade em prod

1. **Create Service** → compose → cole o conteúdo de `deploy/dokploy-observability-compose.yml`.
2. Env vars: `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`.
3. Expor Grafana em `grafana.<seu-domínio>` atrás de Basic Auth.
4. Verificar que Prometheus consegue scrape em `backend:8080/actuator/prometheus` (rede interna do environment).
5. Configurar Alertmanager com SMTP real OU substituir por webhook Slack/Telegram em `deploy/alertmanager/alertmanager.yml`.

---

## Passo 8 — Cadastrar segredos no GitHub

Repo → `Settings → Secrets and variables → Actions → New repository secret`:

| Nome | Valor |
|---|---|
| `DOKPLOY_HML_WEBHOOK` | URL do webhook do `backend-hml` (Dokploy) |
| `DOKPLOY_HML_TOKEN` | Token bearer (se Dokploy emitir; senão deixar vazio e ajustar workflow) |
| `DOKPLOY_PROD_WEBHOOK` | URL do webhook do `backend prod` |
| `DOKPLOY_PROD_TOKEN` | Idem para prod |

⚠ Se o Dokploy expõe **um webhook por serviço** (backend + frontend separados), você precisa de **2 secrets por environment** — atualize `.github/workflows/ci.yml` e `.github/workflows/promote-to-prod.yml` para chamar ambos sequencialmente.

---

## Passo 9 — Criar GitHub Environments

Repo → `Settings → Environments`:

1. **`hml`**: sem proteção; auto-deploy.
2. **`production`**: required reviewers = `paulobof`; webhook protegido.

---

## Passo 10 — Validar deploy automático

1. Faça uma alteração trivial em `main` (ex.: ajuste no README) e push.
2. Acompanhe o GitHub Actions:
   - Backend + Frontend devem passar.
   - `deploy-hml` deve disparar o webhook.
   - Step `Wait for HML readiness` deve passar em até 3 min.
3. Em paralelo, acompanhar logs do Dokploy: backend-hml em `Building` → `Running`.
4. `curl https://hml.api.<seu-domínio>/actuator/health` deve retornar `{"status":"UP"}`.
5. `https://hml.app.<seu-domínio>` deve mostrar a tela inicial HELBOR TRILOGY HOME.

---

## Passo 11 — Promoção manual para prod

Quando quiser promover a versão atual de HML para prod:

1. GitHub → `Actions → promote-to-prod → Run workflow`.
2. **Branch**: `main`. **Input `version`**: SHA da release (ex.: o último commit do main que está em HML há ≥30 min).
3. Aprovar no `production` environment quando solicitar.
4. Acompanhar; ao final, uma tag `vYYYY.MM.DD-HHmm` é criada no remote.

---

## Pós-setup — checklist

- [ ] Backend prod acessível em `api.<seu-domínio>` retornando `UP`.
- [ ] Frontend prod acessível em `app.<seu-domínio>`.
- [ ] Backend HML acessível em `hml.api.<seu-domínio>` retornando `UP`.
- [ ] Frontend HML acessível em `hml.app.<seu-domínio>`.
- [ ] Secrets `DOKPLOY_*` cadastrados no GitHub.
- [ ] Environments `hml` e `production` configurados.
- [ ] Push em `main` aciona deploy HML automático com sucesso.
- [ ] Tag `v0.1.0-foundation` criada (Task 14 do plano).

---

## Notas de segurança

- **Nunca commite** os segredos preenchidos. Somente `.env.example` (template) vai pro repo.
- **Rotacione** os pepper / JWT keys / HMAC keys a cada 90 dias (estratégia documentada na spec, seção 4.1 e 4.4).
- **Restrinja** o console MinIO por IP allowlist; nunca expor publicamente sem auth.
- **Backup** Postgres e MinIO ativados desde o dia 0.
