# Gerenciar acessos (RBAC) + role "Editor do Mural" — Design

**Data:** 2026-06-09
**Branch:** a criar (`feat/access-management`)

## Contexto

A permissão `ROLE_ASSIGN` (permission id 12, da role MANAGER/Síndico) existe desde o seed
inicial (`V6`), mas **nenhum código a consome**: hoje não há tela nem endpoint para atribuir ou
remover roles de usuários — roles só são definidas por migration/seed e pelos fluxos automáticos
de cadastro (`RegistrationService`/`UnitMemberService` atribuem `RESIDENT`). Também não existe
busca genérica de usuários, e `Role.max_holders` nunca é validado em runtime.

Este é o **Sub-projeto A** do par planejado em 2026-06-09. O **Sub-projeto B** (reorder de avisos)
já foi concluído e mergeado. A está sendo desenhado agora em spec próprio; compõe com B sem
dependência: o reorder de avisos é protegido por `ANNOUNCEMENT_MANAGE`, que a nova role carregará.

Motivação do usuário: o síndico quer poder dar a alguém acesso de **editar o mural de avisos** sem
ser conselheiro, via uma role dedicada **"Editor do Mural"**, e quer uma tela para gerir esses
acessos.

## Objetivo

1. Criar a role **"Editor do Mural"** (`MURAL_EDITOR`) contendo só `ANNOUNCEMENT_MANAGE`.
2. Construir a tela **"Gerenciar acessos"** (gated por `ROLE_ASSIGN`) que permite buscar um
   usuário e atribuir/remover um **subconjunto curado** de roles operacionais.
3. Endpoints de busca de usuário, listagem de roles geríveis e atribuição/remoção de role,
   com validação de `max_holders` e auditoria imutável.

**Não-objetivos:** gerir as roles MANAGER/Síndico (cap 1, sensível) e RESIDENT/Morador
(automática no cadastro) pela tela; UI genérica de permissões individuais (`UserPermissionGrant`);
edição de quais permissões compõem uma role; busca/listagem geral de usuários fora do contexto de
acessos; drag-and-drop; paginação infinita.

## Decisões de arquitetura

- **Role nova, não permissão individual.** `UserPermissionGrant` (com revogação) existe e seria
  alternativa, mas o usuário pediu explicitamente uma *role* "Editor do Mural" — mais legível para
  o síndico e reaproveita o `PermissionResolver` que já une permissões de role + grants.
- **"Gerível" é data-driven:** coluna `assignable boolean` na tabela `role` (default `false`),
  em vez de constante no código. Uma role futura opta por aparecer na tela via migration.
- **Feature flag** `app.feature.accessmanagement.enabled` (padrão `false`) no controller, para
  rollout seguro — mesmo padrão de `app.feature.announcements.enabled`.

## Escopo de roles geríveis

A tela atribui/remove **apenas** roles com `assignable = true`:

| id | name | label | max_holders | assignable |
|---|---|---|---|---|
| 1 | MANAGER | Síndico | 1 | false (fora da tela) |
| 2 | COUNCIL | Conselheiro | 3 | **true** |
| 3 | STAFF | Administração | 5 | **true** |
| 4 | RESIDENT | Morador | NULL | false (automática no cadastro) |
| 5 | DOORMAN | Porteiro | NULL | **true** |
| 6 | MURAL_EDITOR | Editor do Mural | NULL | **true** (nova) |

## Modelo de dados (migrations, expand/contract, backward-compatible)

Três migrations Flyway novas (`-- flyway:transactional=true`), sem rename/remove no mesmo migration
que adiciona:

1. **`V24__role_assignable.sql`** — `ALTER TABLE role ADD COLUMN assignable boolean NOT NULL
   DEFAULT false;` e `UPDATE role SET assignable = true WHERE name IN ('COUNCIL','STAFF','DOORMAN');`
   (a nova role MURAL_EDITOR já entra com `assignable=true` no seu próprio insert).
2. **`V25__role_mural_editor.sql`** — insere `role` id 6 (`MURAL_EDITOR`, label "Editor do Mural",
   `max_holders = NULL`, `assignable = true`) e `role_permission (6, <id de ANNOUNCEMENT_MANAGE>)`.
   O insert de `role_permission` resolve a permission por `code = 'ANNOUNCEMENT_MANAGE'`
   (`SELECT id FROM permission WHERE code = 'ANNOUNCEMENT_MANAGE'`) para não acoplar ao id numérico.
3. **`V26__role_assignment_log.sql`** — cria tabela imutável:
   ```sql
   CREATE TABLE role_assignment_log (
     id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
     action          varchar(10) NOT NULL,   -- 'ASSIGN' | 'REMOVE'
     target_user_id  uuid NOT NULL,
     role_id         smallint NOT NULL,
     actor_user_id   uuid NOT NULL,
     created_at      timestamptz NOT NULL DEFAULT now()
   );
   CREATE INDEX idx_role_assignment_log_target ON role_assignment_log (target_user_id);
   ```
   Sem `deleted_at` (log imutável, como `sensitive_access_log`); hard delete permitido por exceção
   do CLAUDE.md (a tabela entra na lista de logs imutáveis).

`RoleName` (enum Java) ganha o valor `MURAL_EDITOR`. O enum e o seed do id 6 devem ir juntos.

## Backend — novo módulo `feature/access`

Pacote `br.com.condominio.feature.access`. Todos os endpoints sob `@ConditionalOnProperty(name =
"app.feature.accessmanagement.enabled", havingValue = "true")` e `@PreAuthorize`.

**Entidade `RoleAssignmentLog`** (append-only): `@Getter`, sem `@Setter` público; criada via factory
estática `RoleAssignmentLog.assign(...)` / `RoleAssignmentLog.remove(...)`.

**DTOs (records):**
- `UserSearchResult(UUID id, String displayName, String unitLabel)`.
- `AssignableRoleView(short id, String name, String label)`.
- (resposta de roles do usuário) `List<Short>` com os ids das roles geríveis que ele possui.

**`AccessController`** (`/api/access`):
- `GET /roles` — `@PreAuthorize("hasAuthority('ROLE_ASSIGN')")` → roles `assignable = true`.
- `GET /users?q={term}` — `ROLE_ASSIGN` → busca por trecho de nome OU e-mail (case-insensitive),
  no máximo 20 resultados. `q` com `@Size(min = 2)`; vazio/curto → lista vazia. Sem PII em log.
- `GET /users/{id}/roles` — `ROLE_ASSIGN` → ids das roles geríveis que o usuário possui hoje.
- `POST /users/{id}/roles/{roleId}` — `ROLE_ASSIGN` → atribui; **204** No Content.
- `DELETE /users/{id}/roles/{roleId}` — `ROLE_ASSIGN` → remove; **204** No Content.

**`AccessService`** (`@Transactional`):
- `searchUsers(term)`: consulta repositório por nome/e-mail; mapeia para `UserSearchResult`.
- `assignableRoles()`: `roleRepo` filtrando `assignable = true`.
- `userRoleIds(userId)`: roles geríveis que o usuário possui.
- `assign(actorId, targetUserId, roleId)`:
  - valida que a role existe e é `assignable` (senão `AccessException` → 422/400);
  - valida que o alvo existe e está `ACTIVE` (senão `AccessException`);
  - se `role.maxHolders != null` e `count(user_role ativos da role) >= maxHolders` →
    `AccessException` → **409** "Limite de N atingido para <label>";
  - se o usuário já tem a role, no-op idempotente (não duplica, não loga);
  - senão grava `UserRole(new UserRoleId(target, roleId), now(), actorId)` + `RoleAssignmentLog.assign`.
- `remove(actorId, targetUserId, roleId)`:
  - valida que a role é `assignable` (não permite remover MANAGER/RESIDENT por esta via);
  - hard delete da `UserRole` (M:N puro, permitido); se não existia, no-op;
  - grava `RoleAssignmentLog.remove`.

**Repositórios:**
- `RoleRepository`: + `List<Role> findByAssignableTrue()`.
- `UserRoleRepository`: + `long countByIdRoleId(short roleId)` (ocupantes da role),
  `List<UserRole> findByIdUserId(UUID userId)`, `Optional<UserRole> findById(UserRoleId)`,
  `void deleteById(UserRoleId)`.
- Busca de usuário: query derivada/`@Query` por `full_name ILIKE %term%` OR e-mail
  (via `user_email`), retornando só `ACTIVE`, com `LIMIT 20`. Respeita soft delete
  (`deleted_at IS NULL` — atenção a query nativa, incluir manualmente conforme CLAUDE.md).
- `RoleAssignmentLogRepository`: `save`.

**Erros:** `AccessException` específica + `@ExceptionHandler` no `GlobalExceptionHandler`
retornando `ApiError(code, message, requestId)`. Código de status: **409** para limite,
**404** para usuário/role inexistente, **422** para role não-gerível ou alvo inativo.

## Frontend — nova feature `features/access`

Estrutura `frontend/src/features/access/{api,pages}`.

**`accessApi.ts`:**
- Tipos: `UserSearchResult { id, displayName, unitLabel }`, `AssignableRole { id, name, label }`.
- `searchUsers(q: string): Promise<UserSearchResult[]>` → `GET /access/users?q=`.
- `listAssignableRoles(): Promise<AssignableRole[]>` → `GET /access/roles`.
- `getUserRoleIds(userId: string): Promise<number[]>` → `GET /access/users/{id}/roles`.
- `assignRole(userId, roleId): Promise<void>` → `POST /access/users/{id}/roles/{roleId}`.
- `removeRole(userId, roleId): Promise<void>` → `DELETE /access/users/{id}/roles/{roleId}`.

**`AccessManagementPage.tsx`:**
- Campo de busca (debounce); abaixo, lista de resultados (nome + unidade), touch ≥44px.
- Ao selecionar um usuário: painel com as roles geríveis como **toggles/checkboxes**,
  refletindo `getUserRoleIds`. Alternar chama `assignRole`/`removeRole` e atualiza o estado;
  em erro exibe a mensagem (incl. limite 409) e reverte o toggle.
- Estados de loading/erro/vazio. Mobile-first, WCAG AA.

**Roteamento e navegação:**
- Rota nova protegida; item na Sidebar exibido **só** se `user.authorities.includes('ROLE_ASSIGN')`
  (padrão inline já usado no projeto).

## Testes (TDD — testes primeiro)

**Backend:**
- `AccessServiceTest`: atribui grava `UserRole` + log; remove faz hard delete + log; bloqueia em
  `max_holders` (count >= limite → exceção); rejeita role não-`assignable`; rejeita alvo inativo;
  idempotência (atribuir o que já existe não duplica); busca filtra por nome/e-mail e ACTIVE.
- `AccessControllerWebTest`: `roles`/`users`/`assign`/`remove` → 200/204 com `ROLE_ASSIGN`,
  **403** sem a permissão, **401** anônimo, **409** em limite atingido; busca com `q` curto.

**Frontend:**
- `accessApi.test.ts`: contrato de cada função (URL/método/payload).
- `AccessManagementPage.test.tsx`: busca renderiza resultados; selecionar usuário mostra toggles
  com estado correto; alternar chama `assignRole`/`removeRole`; 409 mostra mensagem de limite e
  reverte; sem `ROLE_ASSIGN` o item de menu/rota não aparece.

## Entrega (PRs ≤400 linhas, branch ≤2 dias)

O plano (writing-plans) fatiará em PRs incrementais, p.ex.:
1. **PR1** — migrations `V24`/`V25`/`V26` + `RoleName.MURAL_EDITOR` + entidade `RoleAssignmentLog`
   + ajustes de repositório. (Schema e role nova já úteis isoladamente.)
2. **PR2** — `feature/access` backend (DTOs, service, controller, busca, validações, auditoria)
   + testes service/web, atrás da feature flag.
3. **PR3** — `features/access` frontend (api, página, rota, Sidebar) + testes.

## Convenções

SOLID/KISS/POO, TDD, soft delete intacto (`@SQLDelete`/`@SQLRestriction`; logs e M:N são exceção),
Lombok sem `@Data`, `@Transactional` só no service, autorização por permission
(`hasAuthority('ROLE_ASSIGN')`, nunca por role), nunca PII em log (`LogSanitizer`/MDC),
migrations backward-compatible com `gen_random_uuid()`. Português na UI, inglês no código.
Conventional Commits, squash merge, hooks rodam (sem `--no-verify`).
