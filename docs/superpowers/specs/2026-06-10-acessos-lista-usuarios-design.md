# Gerenciar acessos — lista paginada de usuários

**Data:** 2026-06-10
**Status:** Aprovado (brainstorming)
**Depende de:** `2026-06-09-acessos-rbac-design.md` (feature já mergeada e ligada em HML, flag `app.feature.accessmanagement.enabled`).

## Problema

A tela **Gerenciar acessos** (`/admin/acessos`, gated por `ROLE_ASSIGN`) hoje é **só por busca**: o admin precisa digitar um termo (≥2 chars) para ver qualquer usuário. Não há como **navegar** quem existe nem ver de relance **quais perfis cada um tem**. O síndico pediu uma lista de usuários para alterar perfis direto, sem ter que adivinhar nomes.

O spec original (`2026-06-09`) deixou "listagem geral de usuários" fora de escopo por privacidade. Aqui reabrimos isso **de forma restrita**: a lista continua gated por `ROLE_ASSIGN` (só admin/síndico vê), então o diretório fica acessível apenas a quem já administra acessos.

## Objetivo

Transformar a tela em **lista paginada de todos os usuários ativos**, com os perfis atuais visíveis em cada linha, mantendo a busca como **filtro** e o painel de toggles existente para editar.

## Não-objetivos

- Edição de quais permissões compõem uma role.
- Toggles de perfil inline na linha (mantém o fluxo "clicar → painel").
- Scroll infinito (usamos "Carregar mais" explícito).
- Exportar lista / ações em massa.
- Listar usuários não-ACTIVE (pendentes, anonimizados).

## Decisões (do brainstorming)

1. **Quem listar:** todos os usuários **ACTIVE** (não só quem tem perfil), paginado.
2. **Linha:** nome + unidade + **badges dos perfis geríveis atuais**. Clicar abre o painel de toggles já existente; "Voltar" retorna à lista.
3. **Paginação:** busca filtra; sem busca, lista paginada com botão **"Carregar mais"**.
4. **Abordagem técnica:** **unificar** num único endpoint paginado (substitui o `/users?q=` atual), em vez de criar endpoint separado. Uma fonte de verdade; busca vira filtro natural.

## Backend

### Endpoint (contrato novo, substitui o atual)

```
GET /api/access/users?q={termo}&page={n}&size={20}      @PreAuthorize ROLE_ASSIGN
→ 200 Page<UserAccessRow>
```

- `q` ausente/vazio → todos os ACTIVE, ordenados por `full_name`, paginado.
- `q` presente → mesma lista filtrada por `full_name` OU e-mail (case-insensitive, `LIKE %termo%`). Sem mínimo de caracteres (é filtro de lista; vazio = todos).
- `size` default 20, teto 100. `page` default 0.
- **403** sem `ROLE_ASSIGN`, **401** anônimo, **404** (controller não registrado) se a flag estiver off.
- Sem PII em log.

### DTOs (`feature/access/dto`)

```java
record UserAccessRow(UUID id, String displayName, String unitLabel, List<RoleBadge> roles) {}
record RoleBadge(short id, String label) {}
```

`roles` = apenas os perfis **assignable** que o usuário possui hoje (mesma regra do `userRoleIds`).

### Service (`AccessService.listUsers`)

```java
@Transactional(readOnly = true)
Page<UserAccessRow> listUsers(String q, Pageable pageable)
```

Para evitar **N+1**:
1. Query 1 — `AccessUserRepository.findActivePage(term, pageable)` retorna `Page<UserSearchResult>` (id, displayName, unitLabel) dos ACTIVE, filtrando por termo quando presente (`:term IS NULL OR ...`). `countQuery` com `COUNT(DISTINCT u.id)`.
2. Conjunto de roles geríveis: `roleRepo.findByAssignableTrue()` → mapa `id → label`.
3. Query 2 — perfis dos usuários da página: `userRoleRepo.findById_UserIdIn(ids)` (novo método), filtrado pelo conjunto assignable.
4. Monta `UserAccessRow` por usuário com as `RoleBadge` ordenadas por label; preserva a ordem/paginação da query 1.

O método antigo `searchUsers(term)` é removido (substituído por `listUsers`). `userRoleIds`, `assign`, `remove`, `assignableRoles` permanecem.

### Repositório

- `AccessUserRepository.findActivePage(@Param("term") String term, Pageable)` — `@Query` + `countQuery`, `LEFT JOIN UserEmail`, `DISTINCT u.id`, `term` opcional, `ORDER BY u.fullName`.
- `UserRoleRepository.findById_UserIdIn(Collection<UUID> userIds)` — novo derived query.

### Sem migração

Usa `users`, `user_role`, `roles`, `units`, `user_email`. Nenhuma mudança de schema.

## Frontend

### `accessApi.ts`

```ts
interface RoleBadge { id: number; label: string }
interface UserAccessRow { id: string; displayName: string; unitLabel: string | null; roles: RoleBadge[] }
interface PageResult<T> { content: T[]; number: number; totalPages: number; last: boolean }

listUsers(q: string, page = 0, size = 20): Promise<PageResult<UserAccessRow>>
  → GET /access/users?q&page&size
```

Remove `searchUsers`. Mantém `listAssignableRoles`, `getUserRoleIds`, `assignRole`, `removeRole`.

### `AccessManagementPage.tsx`

- **Ao montar:** carrega página 0 de `listUsers('')` + `listAssignableRoles()`.
- **Busca:** input vira filtro com **debounce** (~300ms) → recarrega página 0 com `q`. (Substitui o submit do form.)
- **Lista:** cada linha = `displayName` + `unitLabel` + badges (`roles[].label`); touch ≥44px. Estado vazio: "Nenhum usuário encontrado." quando filtro não casa.
- **Carregar mais:** botão exibido quando `!last` → busca `page+1` e **append** ao conteúdo.
- **Editar:** clicar na linha → `selectUser` (chama `getUserRoleIds`) → painel de toggles existente (otimista, revert/toast, trava em voo). "Voltar à busca" → volta à lista; **atualiza as badges da linha editada** com o estado final de `roleIds` (sem refetch).
- Acessibilidade/copy pt-BR mantidas.

## Testes (TDD)

**Backend**
- `AccessServiceTest` (ou novo): `listUsers` sem `q` retorna ACTIVE paginado com badges; com `q` filtra; usuário sem perfil → `roles` vazio; respeita `size`/teto.
- `AccessControllerWebTest`: `GET /users?page&size` → 200 com shape paginado e `roles`; **403** sem `ROLE_ASSIGN`. **Atualiza** o teste de busca atual (contrato mudou de `List` para `Page`).
- Repositório: teste de `findActivePage` (filtro por nome/e-mail, só ACTIVE, paginação) se houver suíte de repositório `@DataJpaTest`; senão coberto via service com mock.

**Frontend**
- `accessApi.test.ts`: `listUsers` chama `/access/users` com `q/page/size` e devolve `content`.
- `AccessManagementPage.test.tsx` (**atualiza**): lista aparece ao abrir (sem buscar); badges renderizam; "Carregar mais" busca próxima página e faz append; clicar numa linha abre os toggles; filtro por busca recarrega.

## Riscos

- **Privacidade:** lista expõe diretório de ativos — mitigado pelo gating `ROLE_ASSIGN` (só admin). Sem PII em log.
- **Contrato quebrado:** mudar `/users` de `List` para `Page` quebra clientes do shape antigo — só o próprio frontend consome; atualizado junto.
- **Performance:** página com badges via 2 queries (sem N+1); teto de `size` 100.

## Ordem de implementação

1. Backend: DTOs + repo (`findActivePage`, `findById_UserIdIn`) + `AccessService.listUsers` + controller, com testes (TDD).
2. Frontend: `accessApi.listUsers` + tela (lista, debounce, badges, "Carregar mais", editar/voltar), com testes (TDD).
3. Verificação local (suítes back+front) → merge → redeploy HML.
