# gestor-condominio

Sistema de gestão do **Condomínio HELBOR TRILOGY HOME** — 3 torres (A/B/C), andares 4–32, 522 unidades.

Monorepo com backend Spring Boot 3 + Java 21 e frontend Vite/React/TypeScript.

## Estrutura

- `backend/` — API Spring Boot 3.
- `frontend/` — SPA Vite + React.
- `deploy/` — configs Dokploy (Prod + HML).
- `docs/superpowers/` — specs e planos de implementação.

## Desenvolvimento local

Pré-requisitos: JDK 21, Node 22, Docker.

```bash
# Subir Postgres + MinIO locais
docker compose -f docker-compose.dev.yml up -d

# Backend
cd backend && ./mvnw spring-boot:run

# Frontend (outro terminal)
cd frontend && npm run dev
```

Frontend em http://localhost:5173 com proxy `/api` → backend `:8080`.

## Comandos

| Comando | Ação |
|---|---|
| `npm run test:backend` | Roda testes Maven |
| `npm run test:frontend` | Roda Vitest |
| `npm run lint:backend` | Spotless check |
| `npm run lint:frontend` | ESLint |
| `npm run format:backend` | Spotless apply |

## Workflow

**Trunk-Based Development**. Branch única `main`; feature branches ≤2 dias; PR ≤400 linhas. Push em `main` → deploy HML automático. Promoção a Prod via workflow manual `promote-to-prod.yml`.

## Documentação

Especificações em `docs/superpowers/specs/`. Planos em `docs/superpowers/plans/`. Convenções de código em `CLAUDE.md`.
