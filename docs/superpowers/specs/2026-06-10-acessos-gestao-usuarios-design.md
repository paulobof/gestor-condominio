# Gerenciar acessos → Gestão de usuários — Design

**Data:** 2026-06-10
**Status:** aprovado (design)
**Contexto:** evolução da tela `/admin/acessos`, hoje só RBAC (atribuir/remover roles geríveis) + lista paginada. Spec anterior: `2026-06-10-acessos-lista-usuarios-design.md`.

## Problema

1. **Bug em produção (HML):** a carga inicial da lista (`GET /api/access/users` com termo vazio) retorna **500**. Causa raiz confirmada em `AccessUserRepository.findActivePage`: o guard `(:term IS NULL OR ... CONCAT('%', :term, '%') ...)` passa `:term` como `null` sem tipo; o Postgres precisa resolver o tipo do bind parameter no PREPARE (antes do short-circuit) e lança *"could not determine data type of parameter"* (agravado por `email` ser `citext`). Só quebra com termo nulo; com termo preenchido funciona. O teste que pegaria isso (`RepositoryPostgresTest.findActivePage_nullAndNonNullTerm_runsAgainstPostgres`) é **pulado** sem Docker (local e pre-push), então passou batido.
2. A tela precisa virar **gestão de usuários**: listar com **nome, telefone, ap, roles**; **criar** usuário; **excluir** usuário.

## Escopo e autorização

- Rota mantida: `/admin/acessos` (menu "Gerenciar acessos").
- **Listar + alternar roles**: `ROLE_ASSIGN` (inalterado).
- **Criar + excluir usuário**: `USER_MANAGE` (permission já existente). Defesa em profundidade: quem só tem `ROLE_ASSIGN` vê a lista e mexe em role, mas não vê os botões criar/excluir; o backend também barra.
- **Sem migração**: colunas (`user.phone`, `user.deleted_at`, `user_email.deleted_at`) e a permission `USER_MANAGE` já existem.

## Decisões de produto (confirmadas)

- **Excluir** = **soft delete** ("sai da lista"): marca `deleted_at` no `User` (via `@SQLDelete`) **e** nos `UserEmail` dele (libera o e-mail `citext` para reuso). Não anonimiza (regras extras de exclusão ficam para depois). Guard: não pode excluir a si mesmo; não excluir usuário já fora de `ACTIVE`/já deletado.
- **Criar** = admin cria direto, qualquer unidade, já `ACTIVE`, sem passar por aprovação.
  - Campos: **nome (obrig.)**, **e-mail (obrig., único)**, **telefone (obrig.)**, **unidade/ap (opcional)**, **roles (obrig., ≥1)**.
  - Senha: **gerada pelo backend** (forte, compatível com a policy), `must_change_password=true`, **retornada uma única vez** no response para o admin repassar. Admin não escolhe a senha.
  - `isUnitMaster=false`.
- **Roles no cadastro** = **Morador (RESIDENT) + geríveis** (COUNCIL, STAFF, DOORMAN, MURAL_EDITOR). Ou seja, `creatableRoles = assignableRoles ∪ {RESIDENT}`. **Síndico (MANAGER) e Admin ficam de fora.** Morador vem marcado por padrão.

## Arquitetura

### Backend

**`AccessUserRepository`** — corrige o 500 dividindo em duas queries (sem parâmetro nulo) e trazendo `phone`:
- `findActivePageAll(Pageable)` — sem termo.
- `findActivePageByTerm(String term, Pageable)` — com termo (term garantidamente não-nulo).
- Projeção passa a ser `UserSearchResult(id, displayName, unitLabel, phone)` (novo campo `phone` ← `u.phone`).

**`AccessService`**
- `listUsers(q, pageable)`: escolhe `findActivePageAll` quando `term == null`, senão `findActivePageByTerm`. Mapeia para `UserAccessRow(id, displayName, unitLabel, phone, roles[])` (badges montadas sem N+1 como hoje).
- `creatableRoles()`: retorna `assignable ∪ {RESIDENT}` como `List<AssignableRoleView>` (com flag/marca de default = RESIDENT na borda do frontend).
- `createUser(actorId, CreateUserCommand)` (`@Transactional`):
  - valida e-mail único (`emailRepo.findActiveByEmailIgnoreCase`) → `AccessException("EMAIL_TAKEN", 409)`.
  - valida `roleIds` não vazio e ⊆ `creatableRoles` → `AccessException("ROLE_NOT_CREATABLE")`.
  - gera senha via `ProvisionalPasswordGenerator` (nova classe; produz senha que passa na `StrongPasswordValidator`).
  - cria `User` via **factory de domínio** `User.newActiveByAdmin(...)` (evita o hack de reflection do `UnitMemberService`; setters continuam protegidos, domínio rico).
  - salva `User`, `UserEmail` (primary), `UserRole`s (+ `RoleAssignmentLog.assign` por role).
  - retorna `CreatedUserResult(id, fullName, generatedPassword)`.
- `deleteUser(actorId, targetUserId)` (`@Transactional`):
  - guard `actorId != targetUserId` → `AccessException("CANNOT_DELETE_SELF", 409)`.
  - carrega `User` (managed) → não encontrado = `USER_NOT_FOUND`.
  - soft delete: `userRepo.delete(user)` (dispara `@SQLDelete`) + soft delete dos `UserEmail` do usuário.
  - `log.info` só com `userId` (sem PII). user_role permanece (inofensivo; usuário some das queries).

**`AccessController`**
- `GET /users` (inalterado, `ROLE_ASSIGN`) — agora devolve `phone` por linha.
- `GET /creatable-roles` (`ROLE_ASSIGN`) — roles do cadastro.
- `POST /users` (`USER_MANAGE`) — body `CreateUserRequest(fullName, email, phone, unitId?, roleIds[])`; `201` com `CreatedUserResponse(id, fullName, password)`.
- `DELETE /users/{id}` (`USER_MANAGE`) — `204`.

**Domínio:** `User.newActiveByAdmin(unitId, fullName, phone, passwordHash, pepperVersion)` retorna usuário `ACTIVE`, `mustChangePassword=true`, `isUnitMaster=false`.

### Frontend (`features/access`)

**`accessApi.ts`**
- `UserAccessRow` ganha `phone: string | null`.
- `listUsers` (inalterada em assinatura).
- `getCreatableRoles(): AssignableRole[]`.
- `createUser(payload): { id, fullName, password }`.
- `deleteUser(id): void`.

**`AccessManagementPage.tsx`**
- Linha da lista: **nome · ap · telefone** + badges de role. Botão **Excluir** por linha (só com `USER_MANAGE`) → **AlertDialog** (shadcn) de confirmação → remoção otimista com revert/toast em erro.
- Botão **Adicionar usuário** no topo (só com `USER_MANAGE`) → form: nome, e-mail, unidade (UnitLookup existente), telefone, checkboxes de role (de `getCreatableRoles`, Morador default, ≥1 obrigatório). Ao enviar com sucesso: dialog mostrando a **senha gerada uma única vez** ("copie e repasse") + recarrega a lista.
- Gate de `USER_MANAGE` no cliente reusa o mesmo mecanismo de permissão que já condiciona o menu/`ROLE_ASSIGN` (backend continua sendo a fonte de verdade).

## Tratamento de erros

- `EMAIL_TAKEN` (409), `ROLE_NOT_CREATABLE` (422), `CANNOT_DELETE_SELF` (409), `USER_NOT_FOUND` (404) via `AccessException` → handler já existente. Frontend mostra a `message` no toast.
- Telefone obrigatório validado no front (campo) e no back (`@NotBlank`).

## Testes

**Backend**
- `RepositoryPostgresTest`: troca o teste atual por dois — `findActivePageAll` e `findActivePageByTerm` contra Postgres real (fecha a lacuna que escondeu o 500). Continua pulando sem Docker, mas pre-push/CI com Docker roda.
- `AccessServiceTest`: list mapeia `phone`; `createUser` (gera senha forte, cria user+email+roles, valida e-mail único e roles ⊆ creatable); `deleteUser` (soft-deleta user+email, bloqueia self, bloqueia inexistente); `creatableRoles` = assignable ∪ RESIDENT.
- `AccessControllerWebTest`: `POST/DELETE` exigem `USER_MANAGE` (403 sem); `GET /users` e `/creatable-roles` exigem `ROLE_ASSIGN`; contratos (201 com password, 204, 409 self).

**Frontend**
- `accessApi.test.ts`: `createUser`, `deleteUser`, `getCreatableRoles`, `listUsers` com `phone`.
- `AccessManagementPage.test.tsx`: coluna telefone; abrir form, criar e ver a senha mostrada uma vez; excluir com confirmação (append/remove); botões escondidos sem `USER_MANAGE`.

## Entrega

Um spec, um plano, um PR (assume-se > 400 linhas — override consciente da convenção trunk-based, a pedido). TDD por tarefa. Após merge: redeploy HML (backend+frontend) e validar `/admin/acessos`.

## Fora de escopo

- Regras adicionais de exclusão (ex.: impedir excluir o último `USER_MANAGE`) — depois.
- Editar dados cadastrais do usuário (nome/telefone/unidade) — só roles e criar/excluir por ora.
- Anonimização/LGPD pela tela; envio de senha por WhatsApp.
