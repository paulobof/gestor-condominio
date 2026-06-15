# Proprietário — papel próprio, cadastro e leitura-apenas

**Data:** 2026-06-15
**Branch sugerida:** `feat/proprietario-papel`
**Status:** RASCUNHO — aguardando revisão do Paulo.

> Refina a feature de posse (`UnitOwnership`, hoje flag-off) para separar **Proprietário** de **Morador Principal**. Próximo passo após aprovação: `writing-plans`.

---

## 1. Contexto e motivação

Hoje o sistema **não distingue** "Proprietário" de "Morador Principal":

- O único auto-cadastro "principal" é `POST /api/auth/register-master`, que **sempre** marca residência (`User.unitId = unidade`) + `isUnitMaster = true`. Não existe caminho para um **dono que não mora** no condomínio.
- A feature de posse já construída (`UnitOwnership`, flag `app.feature.unitownership.enabled`, **off**) trata **dono = master**: aprovar uma posse faz `assignMaster` + concede `RESIDENT_MANAGE` (escrita/gestão de moradores).

O Paulo definiu um modelo diferente:

- **Proprietário não é Morador Principal** (pode ser, mas são papéis distintos).
- **Ambos podem ter mais de uma unidade.**
- **Proprietário Puro** (dono que não mora) tem **acesso de leitura ao portal, apenas** — não posta, não modera, não gere moradores. No **futuro**, ganha a parte de **Escolha de Vagas** (exclusiva de proprietário).

Esta spec entrega: o **papel `PROPRIETARIO`** (read-only), o **desacoplamento posse↔mastership**, e o **cadastro público de proprietário** com comprovante de propriedade.

### 1.1 Decisões do brainstorming

| Tema | Decisão |
|---|---|
| Representação | **Novo papel `PROPRIETARIO`** (read-only). Papéis compõem (dono+master = `RESIDENT`+`PROPRIETARIO`). |
| Leitura ("tudo") | **Conteúdo do portal como morador, só-leitura.** Sem `CONTENT_CREATE`, sem `RESIDENT_MANAGE`, sem moderação/admin. |
| Cadastro | **Auto-cadastro público** (`register-owner`) com **comprovante de propriedade**; cria conta PENDENTE, sem residência; admin aprova. |
| Arquitetura | **Abordagem 1:** reaproveitar/refinar `UnitOwnership` (flag-off, seguro mexer), desacoplando posse de master. |
| Flag | **Reusar** `app.feature.unitownership.enabled` (cobre register-owner + claims + admin + aprovação). |
| Tela de cadastro | **Página separada** `/register-owner` (espelha `RegisterMasterPage`), acessada por link na tela de registro. |

---

## 2. Definições de domínio

- **Posse (`UnitOwnership`)**: relação (usuário, unidade, comprovante, status) — fonte de verdade de **quem é dono** de qual unidade. **Independente de residência.**
- **Residência (`User.unitId`)**: onde a pessoa **mora**. Opcional. Proprietário Puro = `unitId = null`.
- **Mastership (`Unit.master_user_id` + `RESIDENT_MANAGE`)**: quem **gere os moradores** da unidade. Amarrada à **residência** (`register-master`), **não** à posse.
- **Proprietário**: usuário com papel `PROPRIETARIO` (tem ≥1 posse APPROVED). Read-only.
- **Morador Principal**: master residente (`isUnitMaster`, `RESIDENT_MANAGE`). Gere moradores.

**Princípio:** posse, residência e mastership são **três relações independentes** com a unidade. Uma unidade pode ter Proprietário e Morador Principal — a mesma pessoa ou pessoas diferentes.

---

## 3. RBAC — papel `PROPRIETARIO`

- Novo valor `PROPRIETARIO` em `RoleName` (`feature/role/RoleName.java`).
- Seed na tabela `role` (próximo id livre; `label` = "Proprietário"; `max_holders = NULL`).
- **Conjunto de permissões do papel** (via `role_permission`): **somente `GENERAL_AREAS_VIEW`** (ver avisos/informações/FAQ).
  - Leitura de classificados, indicações, vagas e documentos já é gated por `isAuthenticated()` → o proprietário (autenticado) vê. Não precisa de permissão extra.
  - **Explicitamente NÃO recebe:** `CONTENT_CREATE` (não cria indicações/classificados), `RESIDENT_MANAGE` (não gere moradores), nem qualquer `*_MANAGE`/`*_MODERATE`/admin.
- Resolução de authorities já é "papéis ∪ grants" (`PermissionResolver.effectivePermissions`) — o papel novo entra automaticamente.
- **Composição:** quem é dono **e** morador-principal tem `RESIDENT` + `PROPRIETARIO` (acumula). Proprietário Puro = só `PROPRIETARIO`.

### 3.1 Gancho futuro — Escolha de Vagas
Quando a feature "Escolha de Vagas" for construída, ela criará a permissão exclusiva (ex.: `PARKING_CHOOSE`) e a concederá ao papel `PROPRIETARIO` (via `role_permission`). **Não** criamos a permissão agora (YAGNI) — fica registrado o ponto de extensão.

---

## 4. Desacoplar posse de mastership

### 4.1 `UnitOwnershipService.approve()` (mudança de comportamento)
Hoje (modelo antigo): `o.approve()` + `unit.assignMaster(userId)` + `grant RESIDENT_MANAGE` + ativa conta.

**Passa a ser:**
- `o.approve(approverId)` → posse APPROVED.
- **Concede o papel `PROPRIETARIO`** ao usuário, **idempotente** (insere `user_role` se ausente). Cobre tanto o register-owner (papel já estará lá) quanto um residente que reivindica posse (ganha o papel agora).
- Se for a **1ª posse APPROVED** do usuário **e** ele estiver `PENDING_APPROVAL` → ativa a conta.
- **Remove** o `unit.assignMaster(...)` e o `grant RESIDENT_MANAGE` (posse não dá mastership).

### 4.2 `RegistrationService.registerMaster()` (remover acoplamento)
Remover o trecho `if (unitOwnershipEnabled) ownershipService.openClaim(...)`. `register-master` volta a ser **só residência/mastership**: cria `RESIDENT` + (na aprovação) `assignMaster` + `RESIDENT_MANAGE`, como antes da feature de posse. Quem é dono **e** mora registra-se nos **dois** fluxos (master e owner) — ou, futuro, um "também sou proprietário" no register-master.

### 4.3 Guardas mantidas
- `UnitOwnership.openClaim` já barra unidade com posse APPROVED (`UNIT_HAS_MASTER`) e claim duplicado → **1 proprietário por unidade** (índice único existente). Renomear a mensagem/código para refletir "proprietário" (ex.: `UNIT_HAS_OWNER`).
- `register-master` continua checando `unit.master_user_id` (mastership), **independente** da posse.

---

## 5. Cadastro — `register-owner`

### 5.1 Endpoint
`POST /api/auth/register-owner` (multipart), atrás de `@ConditionalOnProperty(app.feature.unitownership.enabled)` → flag-off = 404. Espelha o controller de `register-master` (rate-limit por IP, mesmo padrão).

### 5.2 DTO `RegisterOwnerRequest`
Igual ao `RegisterMasterRequest` (`fullName`, `greetingName`, `email`, `phone`, `gender`, `birthDate`, `unitCode`, `password`, `consentVersion`, `whatsappOptIn`) + o arquivo `proof` (multipart) — **comprovante de propriedade** (matrícula/escritura). Sem campo de residência.

### 5.3 Fluxo (service)
1. E-mail único (senão `EMAIL_TAKEN`).
2. Magic-bytes do comprovante (`isAcceptedForProof` — PDF/JPG/PNG/WEBP); upload pro bucket de proofs **fora da transação**.
3. Resolve a unidade por `unitCode` (senão `UNIT_NOT_FOUND`).
4. Cria `User` **PENDING_APPROVAL**, **`unitId = null`** (não mora), `isUnitMaster = false`; papel **`PROPRIETARIO`** (`user_role`); `UserEmail` primário; consentimento/IP como hoje.
5. Abre `UnitOwnership` **PENDING** (via `openClaim`) com o comprovante.
6. Retorna `RegistrationStatusResponse` (PENDING).

### 5.4 Aprovação
**Reusa** o admin de posses existente: `OwnershipAdminController` / tela "Pedidos de unidade" lista/aprova/rejeita/vê comprovante por `ownershipId`. Aprovar → `UnitOwnershipService.approve` (§4.1): posse APPROVED + ativa conta (papel já está). Rejeitar → purga comprovante.

> **Aprovação única:** o proprietário **não** aparece na lista de "Cadastros pendentes" (que é dos masters, via `findPendingMasters`). A conta PENDENTE dele é ativada exatamente quando a **posse** é aprovada em "Pedidos de unidade" — um só passo de aprovação.

### 5.5 Unidade extra (multi-unidade)
Proprietário já logado usa o `POST /api/auth/me/unit-claims` (`MyUnitClaimController`) que **já existe** → abre posse PENDING de outra unidade, sem mexer em residência. Aprovação idêntica. Papel `PROPRIETARIO` idempotente.

---

## 6. Frontend

- **Página de cadastro de proprietário** (`/register-owner`): espelha `RegisterMasterPage` — dados pessoais + lookup de unidade + upload de **comprovante de propriedade** + consentimento. Texto deixa claro "para donos que **não moram** no condomínio".
- **Link na tela de registro**: na entrada de cadastro (onde hoje se escolhe master/convidado), adicionar a opção **"Sou proprietário (não moro no condomínio)"** → leva a `/register-owner`.
- **Experiência read-only**: o `PROPRIETARIO` vê o portal; ações de escrita já somem por permissão ausente (`CONTENT_CREATE` esconde criar indicação/classificado; `RESIDENT_MANAGE` esconde "Moradores"). Revisar menu/home para garantir que nada de escrita aparece só para `PROPRIETARIO`.
- Reuso de "Registrar unidade" (claim extra) e "Pedidos de unidade" (admin), já existentes.

---

## 7. Migrations + flag

- **Migration nova** (próxima versão livre de Flyway, backward-compatible):
  - Insere papel `PROPRIETARIO` na tabela `role` (id livre, label "Proprietário").
  - `role_permission` ligando `PROPRIETARIO` → `GENERAL_AREAS_VIEW`.
- **Sem novas permissões** nesta entrega (reusa `GENERAL_AREAS_VIEW`).
- **Sem remoção de colunas** (expand/contract). As mudanças de comportamento (§4) são em código.
- **Flag:** `app.feature.unitownership.enabled` (reusada). Cobre register-owner, claims, admin e a nova semântica de aprovação. Default off; ligar em HML via Dokploy (ver convenções HML).

---

## 8. Testes (TDD)

**Backend**
- `RegisterOwnerControllerWebTest`: 201 cria; e-mail duplicado → 400; comprovante inválido → 400; **flag-off → 404**.
- `RegistrationService`/owner: cria `User` PENDING + papel `PROPRIETARIO` + `unitId == null` + `UnitOwnership` PENDING.
- `UnitOwnershipServiceTest` (ajuste): `approve` **concede `PROPRIETARIO`** e **não** faz `assignMaster` nem concede `RESIDENT_MANAGE`; 1ª aprovação ativa conta PENDING; idempotência do papel; rejeição purga comprovante.
- `registerMaster` **não** abre mais claim de posse (acoplamento removido); master ainda ganha `RESIDENT_MANAGE` na aprovação dele.
- `PermissionResolver`: usuário só-`PROPRIETARIO` resolve apenas leitura (tem `GENERAL_AREAS_VIEW`, não tem `CONTENT_CREATE`/`RESIDENT_MANAGE`).
- Multi-unidade: owner aprova 2 posses → 2 unidades, papel não duplica.

**Frontend**
- Página `/register-owner`: campos, validação, upload, submit.
- Menu/home: usuário `PROPRIETARIO` (mock) não vê ações de escrita (criar indicação/classificado, "Moradores").

---

## 9. Fora de escopo (futuro)

- **Escolha de Vagas** (só o gancho da permissão `PARKING_CHOOSE` no papel).
- **Co-propriedade** (vários donos por unidade) — hoje 1 proprietário por unidade.
- Proprietário **convidar/designar** o Morador Principal da sua unidade.
- Admin **vincular** proprietário a unidade **sem comprovante**.
- Remover colunas `residence_proof_*` deprecadas do `User` (contract — outra entrega).

---

## 10. Decomposição em PRs (≤400 linhas — o plano detalha)

1. **RBAC + desacoplamento**: papel `PROPRIETARIO` (enum + migration + seed), mudança em `UnitOwnershipService.approve` (concede papel, não master), remoção do acoplamento no `registerMaster`, ajuste de testes.
2. **Cadastro `register-owner`**: DTO, controller (flag), service (cria PENDING + papel + claim), testes.
3. **Frontend**: página `/register-owner` + link na tela de registro + revisão de menu/home para o read-only.
