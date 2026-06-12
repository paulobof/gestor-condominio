# Proprietário, Moradores e Múltiplas Unidades — Design

> Spec de design. Próximo passo após aprovação: `writing-plans` (plano de implementação por PRs ≤400 linhas).

**Data:** 2026-06-12
**Branch sugerida:** `feat/proprietario-moradores`

## Objetivo

Permitir que um **Proprietário** (dono de unidade) ou o **Admin** gerenciem os **Moradores** de uma unidade, com autorização **por permission/role**. Suportar que um proprietário seja dono de **várias unidades** (auto-registro de unidade extra com comprovante + aprovação). A posse de unidade é **independente da residência** — um proprietário pode ser **só dono**, sem morar em nenhuma unidade.

## Definições de domínio

- **Proprietário**: usuário que é **master de ≥1 unidade** (tem ≥1 `UnitOwnership` APPROVED). Não confundir com residência. Ver [[dominio-proprietario-morador]].
- **Morador**: sub-usuário que **pertence a uma unidade** (`User.unitId` setado, `isUnitMaster=false`, role `RESIDENT`). É quem o Proprietário/Admin cadastra.
- **Posse (`UnitOwnership`)**: relação (usuário, unidade, comprovante, status) — fonte de verdade de quem é/pleiteia master de qual unidade. **Independente** de `User.unitId`.
- **Residência (`User.unitId`)**: a unidade onde a pessoa **mora**. Opcional. Um proprietário "só dono" tem `unitId = null`.

## Princípio-chave

O conjunto de unidades que um proprietário gerencia = **suas `UnitOwnership` APPROVED**. Nunca derivar de `User.unitId` (que é residência, não posse).

---

## 1. Modelo de dados

### 1.1 Nova entidade `UnitOwnership` (tabela `unit_ownership`)

Fonte de verdade da posse. Move o comprovante + estado de aprovação (hoje na linha do `User`) para o par (usuário, unidade).

Campos:
- `id UUID` (PK, `gen_random_uuid()`)
- `version BIGINT`
- `user_id UUID NOT NULL` (FK → user.id, ON DELETE RESTRICT)
- `unit_id UUID NOT NULL` (FK → unit.id, ON DELETE RESTRICT)
- `status TEXT NOT NULL` — `PENDING` | `APPROVED` | `REJECTED`
- `residence_proof_object_key TEXT`, `residence_proof_filename TEXT`, `residence_proof_content_type VARCHAR(80)`, `residence_proof_uploaded_at TIMESTAMPTZ`
- `proof_verified_at TIMESTAMPTZ`
- `approved_by_user_id UUID`, `approved_at TIMESTAMPTZ`, `rejection_reason TEXT`
- auditoria: `created_at`, `updated_at`, `created_by_user_id`, `updated_by_user_id`
- soft delete: `deleted_at`, `deleted_by_user_id` (via `@SQLDelete`/`@SQLRestriction`, padrão do projeto)

Índices:
- `ux_unit_ownership_unit_approved` — **único** em `(unit_id) WHERE status='APPROVED' AND deleted_at IS NULL` → **1 master por unidade**.
- `ux_unit_ownership_user_unit_open` — **único** em `(user_id, unit_id) WHERE status IN ('PENDING','APPROVED') AND deleted_at IS NULL` → sem claim duplicado.
- `idx_unit_ownership_status_pending` em `(created_at DESC) WHERE status='PENDING'` → lista de pendências.
- `idx_unit_ownership_user` em `(user_id) WHERE status='APPROVED'` → resolver "minhas unidades".

Métodos de domínio (entidade rica, sem reflection): `static pending(...)`, `approve(approverId)`, `reject(approverId, reason)`.

### 1.2 `Unit`

- **Dropar** `ux_unit_master_user_active` (índice único em `master_user_id`) → um usuário pode ser master de N unidades.
- `unit.master_user_id` **mantido** (setado na aprovação da ownership; conveniência de lookup). Deixa de ser único entre unidades; continua "1 master por unidade" (coluna única).
- `Unit.assignMaster(userId)` mantém a guarda de "unidade ainda sem master".

### 1.3 `User`

- `unitId` passa a significar **apenas residência** (opcional, independente da posse). Moradores têm; proprietário "só dono" tem `null`.
- `isUnitMaster` vira **flag derivada** mantida na aprovação (true quando ganha a 1ª ownership APPROVED). Não usar para escopo de posse — usar as ownerships.
- Campos `residence_proof_*` / `proof_verified_at` / `approved_*` do `User` ficam **deprecados** (expand/contract: backfill copia para `unit_ownership`; **não** remover colunas nesta entrega).

### 1.4 Migrations (Flyway, a partir de V27, backward-compatible)

1. Cria `unit_ownership` + índices.
2. **Backfill**: para cada master atual (`user.is_unit_master=true`), cria 1 `unit_ownership` copiando `unit_id`(do user)+proof+status (`ACTIVE`→APPROVED, `PENDING_APPROVAL`→PENDING, `REJECTED`→REJECTED) e `approved_*`/`proof_verified_at`.
3. Dropa `ux_unit_master_user_active`.
4. Insere permission `RESIDENT_MANAGE` + `role_permission` (MANAGER) + **grant** (`user_permission_grant`) de `RESIDENT_MANAGE` para todo master ACTIVE (backfill).

---

## 2. Permissão (via role/permission)

- Nova permission **`RESIDENT_MANAGE`** ("Gerir moradores das minhas unidades") — adicionada ao `PermissionCode` e seed.
- **MANAGER** (admin) recebe via `role_permission`.
- **Proprietário** recebe via **grant automático** (`user_permission_grant`) na aprovação da 1ª ownership (e backfill dos atuais). Idempotente: aprovações seguintes não re-concedem. O `PermissionResolver` já une role+grant; efetivo no próximo login/refresh.
- **Não** entra no role `RESIDENT` (não vaza para moradores comuns).
- Revogação: ao anonimizar/desabilitar o proprietário, revogar o grant (cobrir no plano).

---

## 3. Registro & aprovação (multi-unidade)

### 3.1 Novo usuário (público) — `POST /api/auth/register-master`
Mantém a UX. Internamente: cria `User` (PENDING) + `UnitOwnership` (PENDING) com o comprovante (em vez de gravar proof na linha do User). Upload fora da transação, magic-bytes — como hoje. **Residência:** mantém `User.unitId` = unidade registrada (proprietário **e** residente, como hoje) — backward-compatible.

### 3.2 Usuário logado registra **unidade extra** (NOVO) — `POST /api/auth/me/unit-claims` (multipart)
- Autenticado. Valida unidade existe e **sem master**; cria `UnitOwnership` PENDING com comprovante. Usuário continua ACTIVE.
- **Residência:** **NÃO** seta `User.unitId` — registrar unidade extra é **só posse** (é aqui que nasce o "só dono"). A pessoa mora (ou não) onde já estava; passa a gerir os moradores da unidade extra ao aprovar.
- Reaproveita magic-bytes + storage + auditoria do fluxo atual.

### 3.3 Aprovação opera sobre `UnitOwnership`
- `approve(ownershipId, approverId)`: ownership → APPROVED, `unit.assignMaster(userId)`, e **se for a 1ª ownership APPROVED do usuário**: `user` → ACTIVE (quando aplicável) + **grant `RESIDENT_MANAGE`**.
- `reject(ownershipId, approverId, reason)`: ownership → REJECTED, purga comprovante do storage.
- Lista de pendências passa a ser por **claim** (`unit_ownership` PENDING) — inclui novo-usuário e unidade-extra.

### 3.4 Admin
- `RegistrationAdminController` passa a operar por `ownershipId` (lista/aprova/rejeita/ver comprovante). `proof_access_log` por claim.

---

## 4. Gestão de moradores

### 4.1 Endpoints `/api/units/me/members` — gated `@PreAuthorize("hasAuthority('RESIDENT_MANAGE')")`, escopo = minhas unidades (ownerships APPROVED)
- `GET /api/units/me/members` — lista moradores das minhas unidades, **agrupados/anotados por unidade**.
- `POST /api/units/me/members` — cria morador; body inclui **`unitId` (deve estar entre as minhas)**; senha **provisória gerada e mostrada uma vez** (`mustChangePassword=true`), role RESIDENT, e-mail único.
- `PUT /api/units/me/members/{id}` — edita dados (nome/greeting/telefone/e-mail/gênero/nascimento); alvo deve ser morador de uma das minhas unidades.
- `DELETE /api/units/me/members/{id}` — **soft delete** (libera e-mail), guard: alvo é morador (não master) de uma das minhas unidades.

### 4.2 Saneamento do `UnitMemberService`
- Remove `setField` por reflection → usa factory de domínio (`User.newActiveByAdmin` + setters de domínio) e `updateProfile`.
- Remove `userRepo.findAll()` → query escopada por `unitId IN (:minhasUnidades)`.
- Autorização por permission (não mais `if(master.isUnitMaster())`); escopo por ownerships.

### 4.3 Helper compartilhado `UserProvisioning`
Extrai a mecânica comum a `AccessService` (admin) e `UnitMemberService` (proprietário): criar User ACTIVE + `UserEmail` primário com unicidade, editar perfil+e-mail (com flush→`EMAIL_TAKEN`), soft delete (libera e-mail), senha provisória. Evita duplicar a lógica já endurecida no code review.

### 4.4 Admin
Inalterado: cria moradores por `POST /api/access/users` (`USER_MANAGE`), qualquer unidade.

---

## 5. Frontend

- **Tela "Moradores" (Proprietário)** — item de sidebar + rota (ex.: `/minha-unidade/moradores`), gated pela authority `RESIDENT_MANAGE`. Lista por unidade; "Adicionar morador" (seleciona unidade entre as minhas; mostra senha provisória 1x); editar; excluir. Reaproveita padrões de `/admin/acessos`.
- **"Registrar outra unidade"** — em "Minha conta": form com lookup de unidade + upload de comprovante → claim pendente (`/api/auth/me/unit-claims`).
- **`PendingRegistrationsPage` (admin)** — passa a listar claims de unidade (inclui unidade-extra), aprova/rejeita/visualiza comprovante por `ownershipId`.

---

## 6. Rollout, flag e testes

- **Feature flag** `app.feature.unitownership.enabled` (padrão off) cobrindo o conjunto.
- Migrations **expand/contract** (não remove colunas do User agora). Backfill idempotente.
- **TDD** em todas as camadas. Cenários-chave:
  - Proprietário só mexe nas **suas** unidades (não em unidade de terceiros); morador comum (RESIDENT sem grant) recebe 403; admin inalterado.
  - Multi-unidade: criar morador exige `unitId ∈ minhas`; listar agrupa N unidades; aprovar 2ª ownership não duplica grant.
  - Aprovação por ownership: 1 master por unidade (índice único), claim duplicado barrado.
  - Backfill: masters atuais viram 1 ownership APPROVED + grant.
- **HML**: validar com login autenticado; service worker unregister+clear+reload (ver [[hml-frontend-pwa-cache]]); flag ligada via Dokploy (ver [[hml-feature-flags]]).

## 7. Decomposição em PRs (≤400 linhas — o plano detalha)

1. **Modelo + migração + ownership/aprovação**: entidade `UnitOwnership`, migrations (tabela, backfill, drop índice, RESIDENT_MANAGE+grants), aprovação por ownership, admin pendências por claim.
2. **Auto-registro de unidade extra**: `POST /api/auth/me/unit-claims` + frontend "Registrar outra unidade".
3. **RESIDENT_MANAGE + moradores CRUD**: saneamento `UnitMemberService`, `UserProvisioning`, endpoints escopados.
4. **Frontend moradores**: tela do Proprietário (criar/listar/editar/excluir).

## 8. Fora de escopo (futuro)

- Remover as colunas `residence_proof_*` do `User` (contract — outra entrega).
- Admin vincular/desvincular unidades a um proprietário sem comprovante.
- Transferência de posse entre proprietários; histórico de posse.
- Proprietário gerir outros papéis além de morador na unidade.

## 9. Decisões registradas (deste brainstorming)

- Autorização **via permission `RESIDENT_MANAGE`** (não role novo, não boolean) — grant automático na aprovação.
- Superfície do Proprietário: **criar + listar + editar + remover**.
- Remover morador = **soft delete** (libera e-mail).
- Multi-unidade **dentro deste spec** (não decompor); auto-registro de unidade extra com comprovante.
- Posse **independente da residência**: proprietário **pode ser só dono** (`User.unitId` opcional).
- Senha do morador: **provisória gerada e mostrada uma vez** (melhoria sobre o atual "master digita a senha").
