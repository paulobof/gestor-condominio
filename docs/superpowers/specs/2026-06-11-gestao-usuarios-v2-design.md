# Gestão de usuários (v2) — Design

**Data:** 2026-06-11
**Status:** aprovado (design)
**Contexto:** evolução da tela `/admin/acessos` (já vira gestão de usuários na v1: lista paginada, criar, excluir, telefone). Specs anteriores: `2026-06-10-acessos-gestao-usuarios-design.md`, `2026-06-10-acessos-lista-usuarios-design.md`.

## Problemas e pedidos

1. **Token expirado trava a tela (raiz do "não consigo selecionar Editor do Mural").** Diagnóstico com evidência de rede: com o access token expirado, `GET`/`POST /api/access/users/{id}/roles...` retornam **403 com corpo vazio** (falha de autenticação no filtro). `JwtAuthenticationConverter` deixa o `SecurityContext` vazio em token inválido/expirado e "deixa o entrypoint do Spring decidir" — e o default devolve **403**. O interceptor do front (`api.ts`) só renova o token em **401**, então não dispara: a chamada falha em silêncio e o toggle reverte. Com token fresco (após reload) tudo funciona (assign → 204). **Não é bug da feature de roles; é o tratamento de auth.**
2. **"Abri outro usuário e não resetou as flags".** Consequência do item 1: quando o GET de roles falha, `selectUser` cai no `catch`, que mostra um toast mas **não limpa** `roleIds` — as checkboxes ficam com os valores do usuário anterior.
3. **Busca deve casar nome OU ap** (hoje: nome + e-mail).
4. **Renomear a tela para "Gestão de usuários".**
5. **Editar dados do usuário** (além de editar roles): a tela passa a ter, por usuário, duas ações — **editar dados** e **editar acessos**.

## Decisões (confirmadas)

- **Editar dados = todos os campos:** `fullName`, `greetingName`, `phone`, `unitId`, `email`, `gender`, `birthDate`. **E-mail é editável** (muda o login do usuário) — revalida unicidade.
- **Layout:** cada linha tem 3 botões — **Acessos**, **Dados**, **Excluir**. O nome vira texto (não-clicável). **Acessos** exige `ROLE_ASSIGN`; **Dados** e **Excluir** exigem `USER_MANAGE`. (Acessos aparece pra quem só tem `ROLE_ASSIGN`.)
- **Token:** backend passa a retornar **401** em requisição não-autenticada/expirada; `@PreAuthorize` negado (autenticado sem permissão) **continua 403**.
- **Sem migração** (todos os campos já existem).

## Arquitetura

### Backend

**Auth (fix do 401)** — `SecurityConfig`:
- Adicionar `http.exceptionHandling(e -> e.authenticationEntryPoint(restAuthEntryPoint))`, onde `restAuthEntryPoint` escreve **401** com JSON no formato do `ApiError` (`code=UNAUTHENTICATED`). Requisição sem token / token expirado/inválido → entrypoint → **401**. `@PreAuthorize` negado → `AccessDeniedException` → `GlobalExceptionHandler` → **403** (inalterado).
- Criar `RestAuthenticationEntryPoint` (implements `AuthenticationEntryPoint`) em `shared/security`, escrevendo `ApiError.of(401, "Unauthorized", "UNAUTHENTICATED", "Sessão expirada ou ausente. Faça login novamente.", requestId)` como JSON (status 401, content-type application/json). Reusa `ApiError` (mover/expor o `of(...)` já é público).

**Busca por ap** — `AccessUserRepository.findActivePageByTerm`:
- Acrescentar `OR LOWER(un.code) LIKE LOWER(CONCAT('%', :term, '%'))` ao `WHERE` (value e countQuery). A query já tem `LEFT JOIN Unit un`.

**Editar dados** — `AccessController` + `AccessService`:
- `GET /api/access/users/{id}` (`USER_MANAGE`) → `UserDetail(UUID id, String fullName, String greetingName, String phone, UUID unitId, String unitCode, String email, String gender, LocalDate birthDate)`. Service `getUserDetail`: carrega `User` (404 se não achar), pega e-mail primário (`emailRepo.findByUserId` → primary), unitCode via `unitRepo`/lookup. `gender` serializado como nome do enum ou null.
- `PUT /api/access/users/{id}` (`USER_MANAGE`) com `UpdateUserRequest(@NotBlank @Size(max=180) fullName, @Size(max=60) greetingName, @NotBlank @Pattern("\\+?[0-9]{10,15}") phone, UUID unitId, @NotBlank @Email @Size(max=180) email, String gender, LocalDate birthDate)`. Service `updateUser`:
  - carrega `User` (404).
  - se o e-mail mudou (ignore-case): valida que nenhum **outro** usuário ativo o usa (`emailRepo.findActiveByEmailIgnoreCase`; se presente e `userId != alvo` → `AccessException("EMAIL_TAKEN", 409)`), e atualiza o `UserEmail` primário.
  - atualiza os demais campos via método de domínio `User.updateProfile(fullName, greetingName, phone, unitId, gender, birthDate)`.
  - `204 No Content`.
- Domínio: `User.updateProfile(...)` (setters seguem protegidos); `UserEmail.changeEmail(String)` (ou `setEmail` via método de domínio) para trocar o e-mail primário.
- `gender`: aceitar string vazia/nula → `null`; caso contrário `Gender.valueOf(...)` (422 `INVALID_GENDER` se inválido, ou validar antes).

### Frontend (`features/access`)

**accessApi.ts**
- `getUser(id) → UserDetail` (`GET /access/users/{id}`).
- `updateUser(id, payload) → void` (`PUT /access/users/{id}`).
- Tipos `UserDetail`, `UpdateUserPayload`.

**AccessManagementPage.tsx** (renomear título/menu para "Gestão de usuários")
- Linha: nome (texto) · ap · telefone + badges; botões **Acessos** (`ROLE_ASSIGN`), **Dados** (`USER_MANAGE`), **Excluir** (`USER_MANAGE`, com confirmação inline).
- **Acessos** → painel de toggles (atual). `selectUser`/abertura passa a **limpar `roleIds`** antes do fetch e, em erro, **resetar e fechar** com toast (fix do item 2).
- **Dados** → `EditUserForm`: faz `getUser(id)`, preenche (nome, greeting, telefone, unidade por código, e-mail, gênero [select], nascimento [date]), salva via `updateUser`, recarrega a lista. Unidade: campo de código resolvido via `lookupUnit` (igual ao cadastro); vazio = sem unidade.
- Busca: placeholder "Buscar por nome, e-mail ou ap".
- `useAuth`: `canManage = USER_MANAGE`; `canAssign = ROLE_ASSIGN` (já implícito por estar na tela). Botão **Acessos** condicionado a `canAssign`, **Dados/Excluir** a `canManage`.

**Sidebar.tsx:** label "Gerenciar acessos" → "Gestão de usuários" (rota e `requires: ROLE_ASSIGN` inalterados).

## Tratamento de erros

- `EMAIL_TAKEN` (409), `USER_NOT_FOUND` (404) já mapeados. `UNAUTHENTICATED` (401) via entrypoint. Validação de body → 400 (handler existente).

## Testes

**Backend**
- `SecurityConfig`/auth: teste `@WebMvcTest` ou de integração — sem token em rota protegida → **401**; com token mas sem a authority → **403** (os testes atuais que esperam 403 para `MockAuth.user(UID)` sem authority continuam válidos: são autenticados-sem-permissão).
- `RepositoryPostgresTest`: `findActivePageByTerm` casando por código de unidade (ap).
- `AccessServiceTest`: `getUserDetail`; `updateUser` (happy; e-mail duplicado de outro usuário → `EMAIL_TAKEN`; e-mail igual ao próprio → ok; não-encontrado → `USER_NOT_FOUND`).
- `AccessControllerWebTest`: `GET /users/{id}` e `PUT /users/{id}` exigem `USER_MANAGE` (403 sem); `PUT` 204 happy, 409 e-mail duplicado.

**Frontend**
- `accessApi.test.ts`: `getUser`, `updateUser`.
- `AccessManagementPage.test.tsx`: botões Dados/Acessos/Excluir com gating; abrir Dados preenche e salva (updateUser chamado); abrir Acessos reseta flags; busca por ap chama listUsers com o termo; título "Gestão de usuários".

## Entrega

Um spec, um plano, um PR (assume-se > 400 linhas — override consciente, como na v1). TDD por tarefa, execução por subagentes com revisão dupla. Após merge: redeploy HML + validar (lembrar do service worker — [[hml-frontend-pwa-cache]]).

## Fora de escopo

- Auditoria de edição de dados (log dedicado) — depois.
- Reenvio de senha / fluxo de "esqueci a senha" pela tela.
- Regras extras de exclusão (já anotado na v1).
