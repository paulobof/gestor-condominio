# Deploy — gestor-condominio

Configurações para Dokploy em `panel.paulobof.com.br`. Dois *environments*:

- **prod** — `app.helbor.exemplo` / `api.helbor.exemplo`.
- **hml** — `hml.app.helbor.exemplo` / `hml.api.helbor.exemplo`.

## Serviços por environment

| Serviço | Tipo Dokploy | Build path | Notas |
|---|---|---|---|
| backend | application | `./backend` | Dockerfile multi-stage |
| frontend | application | `./frontend` | Dockerfile + nginx |
| postgres | postgres | (gerado) | Backup diário configurar |
| minio | compose | `./deploy/dokploy-minio-compose.yml` | Buckets criados pelo backend |
| observabilidade | compose | `./deploy/dokploy-observability-compose.yml` | Prometheus + Grafana + Alertmanager — apenas em **prod** |

## Variáveis de ambiente

Templates em `dokploy-backend.env.example` e `dokploy-frontend.env.example`. Copiar valores para o painel Dokploy de cada serviço.

**Geração de segredos:**

```bash
# Pepper, JWT, HMAC do WhatsApp — 32 bytes base64
openssl rand -base64 32
```

## Ordem de deploy

1. Postgres (já provisionado).
2. MinIO (compose).
3. Backend (espera Postgres e MinIO em UP).
4. Frontend.
5. Observabilidade (só em prod, último).

## Promoção HML → Prod

Workflow GitHub Actions `promote-to-prod.yml` aciona webhook do environment **prod** após:
- Soak ≥ 30 min em HML.
- Aprovação manual no GitHub Environments.

## Backup

- **Postgres**: cron diário no host: `pg_dump | gzip | mc cp - minio-backups/postgres/<date>.sql.gz`. Retenção 7 dias + 1 mensal.
- **MinIO**: cron diário `mc mirror minio/<bucket> minio-backups/<bucket>`.
- **Restore drill**: ver `docs/runbooks/restore-postgres.md` (criado em Plano 2).
