# Lista paginada de usuários em Gerenciar acessos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transformar a tela "Gerenciar acessos" de só-busca para uma lista paginada de todos os usuários ativos, com os perfis atuais em badge por linha, busca como filtro e "Carregar mais".

**Architecture:** Unificar o endpoint `GET /api/access/users` num retorno paginado (`Page<UserAccessRow>`) com os perfis geríveis de cada usuário; o service monta os badges sem N+1 (página de usuários + uma query de `user_role`); o frontend navega/filtra a lista e reusa o painel de toggles ao clicar.

**Tech Stack:** Spring Boot 3 (JPA, Spring Data Page), JUnit 5 + Mockito + Testcontainers; React + Vite + Vitest + Testing Library; axios.

**Spec:** `docs/superpowers/specs/2026-06-10-acessos-lista-usuarios-design.md`

---

## File Structure

**Backend (criar):**
- `backend/src/main/java/br/com/condominio/feature/access/dto/RoleBadge.java`
- `backend/src/main/java/br/com/condominio/feature/access/dto/UserAccessRow.java`

**Backend (modificar):**
- `.../feature/access/AccessUserRepository.java` — trocar `search` por `findActivePage` (paginado, term opcional).
- `.../feature/role/UserRoleRepository.java` — novo `findById_UserIdIn`.
- `.../feature/access/AccessService.java` — trocar `searchUsers` por `listUsers`.
- `.../feature/access/AccessController.java` — `GET /users` paginado retornando `Page<UserAccessRow>`.

**Backend (testes):**
- `.../persistence/RepositoryPostgresTest.java` — teste de `findActivePage` (Postgres real).
- `.../feature/access/AccessServiceTest.java` — testes de `listUsers`.
- `.../feature/access/AccessControllerWebTest.java` — substituir teste de busca por contrato paginado.

**Frontend (modificar):**
- `frontend/src/features/access/api/accessApi.ts` — trocar `searchUsers` por `listUsers` + tipos.
- `frontend/src/features/access/pages/AccessManagementPage.tsx` — lista + debounce + badges + "Carregar mais".

**Frontend (testes):**
- `frontend/src/features/access/api/accessApi.test.ts` — substituir teste de `searchUsers`.
- `frontend/src/features/access/pages/AccessManagementPage.test.tsx` — reescrever para o novo fluxo.

---

## Task 1: DTOs + repositórios (backend)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/access/dto/RoleBadge.java`
- Create: `backend/src/main/java/br/com/condominio/feature/access/dto/UserAccessRow.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessUserRepository.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/role/UserRoleRepository.java`
- Test: `backend/src/test/java/br/com/condominio/persistence/RepositoryPostgresTest.java`

- [ ] **Step 1: Criar os DTOs**

`RoleBadge.java`:
```java
package br.com.condominio.feature.access.dto;

/** Perfil gerível que um usuário possui, para exibição em badge na lista de acessos. */
public record RoleBadge(short id, String label) {}
```

`UserAccessRow.java`:
```java
package br.com.condominio.feature.access.dto;

import java.util.List;
import java.util.UUID;

/** Linha da lista de acessos: usuário + perfis geríveis atuais. {@code unitLabel} pode ser nulo. */
public record UserAccessRow(UUID id, String displayName, String unitLabel, List<RoleBadge> roles) {}
```

- [ ] **Step 2: Escrever o teste de repositório que falha (Postgres real)**

Em `RepositoryPostgresTest.java`, adicionar o autowire e o teste (a classe já tem `@DataJpaTest` + Testcontainers):

```java
  @Autowired private br.com.condominio.feature.access.AccessUserRepository accessUsers;

  @Test
  void findActivePage_nullAndNonNullTerm_runsAgainstPostgres() {
    // Pega bugs de HQL: ":term IS NULL", DISTINCT + countQuery, LEFT JOIN.
    assertThatCode(() -> accessUsers.findActivePage(null, PageRequest.of(0, 20)))
        .doesNotThrowAnyException();
    assertThatCode(() -> accessUsers.findActivePage("ana", PageRequest.of(0, 20)))
        .doesNotThrowAnyException();
    assertThat(accessUsers.findActivePage(null, PageRequest.of(0, 20))).isNotNull();
  }
```

- [ ] **Step 3: Rodar o teste e ver falhar (não compila: método não existe)**

Run: `cd backend && ./mvnw -o test -Dtest=RepositoryPostgresTest`
Expected: falha de compilação — `findActivePage` não existe em `AccessUserRepository`.

- [ ] **Step 4: Implementar `findActivePage` (substitui `search`) em `AccessUserRepository`**

Substituir o conteúdo da interface por:
```java
package br.com.condominio.feature.access;

import br.com.condominio.feature.access.dto.UserSearchResult;
import br.com.condominio.feature.user.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Listagem de usuários ativos restrita ao contexto de gestão de acessos. */
public interface AccessUserRepository extends Repository<User, UUID> {

  @Query(
      value =
          """
          SELECT DISTINCT new br.com.condominio.feature.access.dto.UserSearchResult(
                 u.id, u.fullName, un.code)
            FROM User u
            LEFT JOIN UserEmail ue ON ue.userId = u.id
            LEFT JOIN Unit un ON un.id = u.unitId
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
             AND (:term IS NULL
                  OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%')))
           ORDER BY u.fullName
          """,
      countQuery =
          """
          SELECT COUNT(DISTINCT u.id)
            FROM User u
            LEFT JOIN UserEmail ue ON ue.userId = u.id
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
             AND (:term IS NULL
                  OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%')))
          """)
  Page<UserSearchResult> findActivePage(@Param("term") String term, Pageable pageable);
}
```

- [ ] **Step 5: Adicionar `findById_UserIdIn` em `UserRoleRepository`**

Abrir `backend/src/main/java/br/com/condominio/feature/role/UserRoleRepository.java`, garantir `import java.util.Collection;` e `import java.util.List;` e adicionar o método à interface:
```java
  List<UserRole> findById_UserIdIn(Collection<UUID> userIds);
```
(Se `import java.util.UUID;` não existir, adicionar também.)

- [ ] **Step 6: Rodar o teste e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=RepositoryPostgresTest`
Expected: PASS (pula automaticamente se não houver Docker — nesse caso seguir; o CI/pre-push roda com Docker).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/dto/RoleBadge.java \
        backend/src/main/java/br/com/condominio/feature/access/dto/UserAccessRow.java \
        backend/src/main/java/br/com/condominio/feature/access/AccessUserRepository.java \
        backend/src/main/java/br/com/condominio/feature/role/UserRoleRepository.java \
        backend/src/test/java/br/com/condominio/persistence/RepositoryPostgresTest.java
git commit -m "feat(access): DTOs e findActivePage para lista paginada de usuários"
```

---

## Task 2: `AccessService.listUsers` (backend)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessService.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java`

- [ ] **Step 1: Escrever os testes que falham**

Em `AccessServiceTest.java` adicionar imports e testes. Imports no topo:
```java
import br.com.condominio.feature.access.dto.RoleBadge;
import br.com.condominio.feature.access.dto.UserAccessRow;
import br.com.condominio.feature.access.dto.UserSearchResult;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
```

Testes (dentro da classe):
```java
  @Test
  void listUsers_mapsRolesIntoBadges() {
    var u1 = new UserSearchResult(TARGET, "Ana Lima", "A-101");
    when(userSearchRepo.findActivePage(null, PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(u1)));
    when(roleRepo.findByAssignableTrue())
        .thenReturn(List.of(role((short) 6, "Editor do Mural", null, true)));
    when(userRoleRepo.findById_UserIdIn(List.of(TARGET)))
        .thenReturn(List.of(new UserRole(new UserRoleId(TARGET, (short) 6), null, ACTOR)));

    Page<UserAccessRow> page = service.listUsers("", PageRequest.of(0, 20));

    assertThat(page.getContent()).hasSize(1);
    UserAccessRow row = page.getContent().get(0);
    assertThat(row.id()).isEqualTo(TARGET);
    assertThat(row.displayName()).isEqualTo("Ana Lima");
    assertThat(row.roles()).containsExactly(new RoleBadge((short) 6, "Editor do Mural"));
  }

  @Test
  void listUsers_blankQuery_passesNullTerm_andUserWithoutRoleHasEmptyBadges() {
    var u1 = new UserSearchResult(TARGET, "Bruno Sá", null);
    when(userSearchRepo.findActivePage(null, PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(u1)));
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of());
    when(userRoleRepo.findById_UserIdIn(List.of(TARGET))).thenReturn(List.of());

    Page<UserAccessRow> page = service.listUsers("   ", PageRequest.of(0, 20));

    assertThat(page.getContent().get(0).roles()).isEmpty();
  }

  @Test
  void listUsers_withTerm_trimsAndForwards() {
    when(userSearchRepo.findActivePage("ana", PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of()));
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of());

    service.listUsers("  ana  ", PageRequest.of(0, 20));

    verify(userSearchRepo).findActivePage("ana", PageRequest.of(0, 20));
  }
```

- [ ] **Step 2: Rodar e ver falhar (não compila: `listUsers` não existe)**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest`
Expected: falha de compilação — método `listUsers` ausente.

- [ ] **Step 3: Implementar `listUsers` e remover `searchUsers`**

Em `AccessService.java`: trocar imports e o método. Remover `import org.springframework.data.domain.PageRequest;` (não é mais usado pelo service) e as constantes `SEARCH_LIMIT`/`MIN_TERM`. Adicionar imports:
```java
import br.com.condominio.feature.access.dto.RoleBadge;
import br.com.condominio.feature.access.dto.UserAccessRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
```
Substituir o método `searchUsers` por:
```java
  @Transactional(readOnly = true)
  public Page<UserAccessRow> listUsers(String q, Pageable pageable) {
    String term = (q == null || q.isBlank()) ? null : q.trim();
    Page<UserSearchResult> page = userSearchRepo.findActivePage(term, pageable);
    List<UUID> ids = page.getContent().stream().map(UserSearchResult::id).toList();

    Map<Short, String> labelById =
        roleRepo.findByAssignableTrue().stream()
            .collect(Collectors.toMap(Role::getId, Role::getLabel));

    Map<UUID, List<RoleBadge>> rolesByUser = new HashMap<>();
    if (!ids.isEmpty()) {
      for (UserRole ur : userRoleRepo.findById_UserIdIn(ids)) {
        String label = labelById.get(ur.getId().getRoleId());
        if (label == null) {
          continue; // role não-gerível: não vira badge
        }
        rolesByUser
            .computeIfAbsent(ur.getId().getUserId(), k -> new ArrayList<>())
            .add(new RoleBadge(ur.getId().getRoleId(), label));
      }
    }
    rolesByUser.values().forEach(list -> list.sort(Comparator.comparing(RoleBadge::label)));

    return page.map(
        u -> new UserAccessRow(u.id(), u.displayName(), u.unitLabel(),
            rolesByUser.getOrDefault(u.id(), List.of())));
  }
```
(O import `UserSearchResult` permanece — ainda é o retorno de `findActivePage`.)

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest`
Expected: PASS (todos, incluindo os de assign/remove já existentes).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/AccessService.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java
git commit -m "feat(access): listUsers paginado com badges de perfil"
```

---

## Task 3: `AccessController` — endpoint paginado (backend)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java`

- [ ] **Step 1: Escrever/substituir os testes de contrato**

Em `AccessControllerWebTest.java`: **remover** o(s) teste(s) do antigo `searchUsers` (qualquer um que use `service.searchUsers`) e adicionar imports:
```java
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import br.com.condominio.feature.access.dto.UserAccessRow;
import br.com.condominio.feature.access.dto.RoleBadge;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
```
Adicionar os testes:
```java
  @Test
  void users_withRoleAssign_returns200_pagedWithBadges() throws Exception {
    var row = new UserAccessRow(TARGET, "Ana Lima", "A-101",
        List.of(new RoleBadge((short) 6, "Editor do Mural")));
    when(service.listUsers("", PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

    mvc.perform(get("/api/access/users").with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].displayName").value("Ana Lima"))
        .andExpect(jsonPath("$.content[0].roles[0].label").value("Editor do Mural"))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  void users_withoutPermission_returns403() throws Exception {
    mvc.perform(get("/api/access/users").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).listUsers(any(), any());
  }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -o test -Dtest=AccessControllerWebTest`
Expected: falha de compilação (referência a `service.listUsers`/`UserAccessRow`) ou, após ajustar mock, 200-test falha porque o endpoint ainda retorna `List<UserSearchResult>`.

- [ ] **Step 3: Atualizar o endpoint no controller**

Em `AccessController.java`: remover `import br.com.condominio.feature.access.dto.UserSearchResult;`, adicionar:
```java
import br.com.condominio.feature.access.dto.UserAccessRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
```
Substituir o método `searchUsers` por:
```java
  @GetMapping("/users")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public Page<UserAccessRow> users(
      @RequestParam(name = "q", defaultValue = "") String q,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return service.listUsers(q, PageRequest.of(page, Math.min(size, 100)));
  }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=AccessControllerWebTest`
Expected: PASS.

- [ ] **Step 5: Rodar a suíte do backend inteira**

Run: `cd backend && ./mvnw -o test`
Expected: BUILD SUCCESS (0 failures).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/AccessController.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java
git commit -m "feat(access): GET /api/access/users paginado retornando perfis"
```

---

## Task 4: `accessApi.listUsers` (frontend)

**Files:**
- Modify: `frontend/src/features/access/api/accessApi.ts`
- Test: `frontend/src/features/access/api/accessApi.test.ts`

- [ ] **Step 1: Substituir o teste de `searchUsers`**

Em `accessApi.test.ts`: trocar o import `searchUsers` por `listUsers` e substituir o teste `'searchUsers envia q como param'` por:
```ts
  it('listUsers envia q, page e size como params', async () => {
    get.mockResolvedValue({ data: { content: [], number: 0, totalPages: 0, last: true } });
    await listUsers('ana', 1, 20);
    expect(get).toHaveBeenCalledWith('/access/users', { params: { q: 'ana', page: 1, size: 20 } });
  });
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/access/api/accessApi.test.ts`
Expected: FAIL — `listUsers` não exportado.

- [ ] **Step 3: Implementar `listUsers` (remover `searchUsers`)**

Em `accessApi.ts`: remover a interface `UserSearchResult` e a função `searchUsers`. Adicionar:
```ts
export interface RoleBadge {
  id: number;
  label: string;
}

export interface UserAccessRow {
  id: string;
  displayName: string;
  unitLabel: string | null;
  roles: RoleBadge[];
}

export interface PageResult<T> {
  content: T[];
  number: number;
  totalPages: number;
  last: boolean;
}

export async function listUsers(q: string, page = 0, size = 20) {
  const r = await api.get('/access/users', { params: { q, page, size } });
  return r.data as PageResult<UserAccessRow>;
}
```
(Manter `AssignableRole`, `listAssignableRoles`, `getUserRoleIds`, `assignRole`, `removeRole`.)

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/features/access/api/accessApi.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/access/api/accessApi.ts frontend/src/features/access/api/accessApi.test.ts
git commit -m "feat(access): accessApi.listUsers paginado"
```

---

## Task 5: `AccessManagementPage` — lista, debounce, badges, "Carregar mais" (frontend)

**Files:**
- Modify: `frontend/src/features/access/pages/AccessManagementPage.tsx`
- Test: `frontend/src/features/access/pages/AccessManagementPage.test.tsx`

- [ ] **Step 1: Reescrever o teste da página**

Substituir todo o conteúdo de `AccessManagementPage.test.tsx` por:
```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/accessApi', () => ({
  listUsers: vi.fn(),
  listAssignableRoles: vi.fn(),
  getUserRoleIds: vi.fn(),
  assignRole: vi.fn(),
  removeRole: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { AccessManagementPage } from './AccessManagementPage';
import {
  listUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
} from '../api/accessApi';

const listMock = vi.mocked(listUsers);
const rolesMock = vi.mocked(listAssignableRoles);
const userRolesMock = vi.mocked(getUserRoleIds);
const assignMock = vi.mocked(assignRole);

const ROLES = [
  { id: 2, name: 'COUNCIL', label: 'Conselheiro' },
  { id: 6, name: 'MURAL_EDITOR', label: 'Editor do Mural' },
];

function pageOf(content: unknown[], last = true, number = 0) {
  return { content, number, totalPages: last ? number + 1 : number + 2, last };
}

beforeEach(() => {
  vi.clearAllMocks();
  rolesMock.mockResolvedValue(ROLES);
  listMock.mockResolvedValue(
    pageOf([{ id: 'u1', displayName: 'Ana Lima', unitLabel: 'A-101', roles: [{ id: 6, label: 'Editor do Mural' }] }])
  );
  userRolesMock.mockResolvedValue([6]);
  assignMock.mockResolvedValue(undefined);
});

describe('AccessManagementPage', () => {
  it('lista usuários ao abrir, sem precisar buscar, com badges', async () => {
    render(<AccessManagementPage />);
    expect(await screen.findByText('Ana Lima')).toBeInTheDocument();
    expect(screen.getByText('Editor do Mural')).toBeInTheDocument();
    await waitFor(() => expect(listMock).toHaveBeenCalledWith('', 0, expect.anything()));
  });

  it('"Carregar mais" busca a próxima página e faz append', async () => {
    listMock.mockResolvedValueOnce(
      pageOf([{ id: 'u1', displayName: 'Ana Lima', unitLabel: 'A-101', roles: [] }], false, 0)
    );
    listMock.mockResolvedValueOnce(
      pageOf([{ id: 'u2', displayName: 'Bruno Sá', unitLabel: 'B-202', roles: [] }], true, 1)
    );
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /carregar mais/i }));
    expect(await screen.findByText('Bruno Sá')).toBeInTheDocument();
    expect(screen.getByText('Ana Lima')).toBeInTheDocument();
  });

  it('clicar numa linha abre os toggles e marcar role chama assignRole', async () => {
    userRolesMock.mockResolvedValue([]);
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await user.click(await screen.findByText('Ana Lima'));
    await user.click(await screen.findByLabelText('Editor do Mural'));
    await waitFor(() => expect(assignMock).toHaveBeenCalledWith('u1', 6));
  });

  it('digitar no filtro recarrega a página 0 com q', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.type(screen.getByLabelText(/buscar/i), 'bru');
    await waitFor(() => expect(listMock).toHaveBeenCalledWith('bru', 0, expect.anything()));
  });
});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/access/pages/AccessManagementPage.test.tsx`
Expected: FAIL — a página ainda importa `searchUsers` e exige buscar antes de listar.

- [ ] **Step 3: Reescrever a página**

Substituir todo o conteúdo de `AccessManagementPage.tsx` por:
```tsx
import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  listUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
  type AssignableRole,
  type UserAccessRow,
} from '../api/accessApi';

function errorMessage(err: unknown, fallback: string): string {
  const maybe = err as { response?: { data?: { message?: string } } };
  return maybe?.response?.data?.message ?? fallback;
}

export function AccessManagementPage() {
  const [roles, setRoles] = useState<AssignableRole[]>([]);
  const [query, setQuery] = useState('');
  const [rows, setRows] = useState<UserAccessRow[]>([]);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<UserAccessRow | null>(null);
  const [roleIds, setRoleIds] = useState<Set<number>>(new Set());
  const [pending, setPending] = useState<Set<number>>(new Set());

  useEffect(() => {
    listAssignableRoles()
      .then(setRoles)
      .catch(() => toast.error('Erro ao carregar os perfis de acesso.'));
  }, []);

  const load = async (q: string, p: number, append: boolean) => {
    setLoading(true);
    try {
      const res = await listUsers(q, p);
      setRows((prev) => (append ? [...prev, ...res.content] : res.content));
      setPage(res.number);
      setLast(res.last);
    } catch {
      toast.error('Erro ao carregar usuários.');
    } finally {
      setLoading(false);
    }
  };

  // debounce: recarrega a página 0 quando o filtro muda (inclui a carga inicial com q vazio)
  useEffect(() => {
    const t = setTimeout(() => {
      void load(query, 0, false);
    }, 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  const selectUser = async (u: UserAccessRow) => {
    setSelected(u);
    try {
      setRoleIds(new Set(await getUserRoleIds(u.id)));
    } catch {
      toast.error('Erro ao carregar acessos do usuário.');
    }
  };

  const back = () => {
    if (selected) {
      const updated = roles
        .filter((r) => roleIds.has(r.id))
        .map((r) => ({ id: r.id, label: r.label }));
      setRows((prev) =>
        prev.map((row) => (row.id === selected.id ? { ...row, roles: updated } : row))
      );
    }
    setSelected(null);
  };

  const toggle = async (role: AssignableRole) => {
    if (!selected) return;
    const has = roleIds.has(role.id);
    setRoleIds((prev) => {
      const next = new Set(prev);
      if (has) next.delete(role.id);
      else next.add(role.id);
      return next;
    });
    setPending((prev) => {
      const next = new Set(prev);
      next.add(role.id);
      return next;
    });
    try {
      if (has) await removeRole(selected.id, role.id);
      else await assignRole(selected.id, role.id);
      toast.success('Acesso atualizado.');
    } catch (err) {
      setRoleIds((prev) => {
        const next = new Set(prev);
        if (has) next.add(role.id);
        else next.delete(role.id);
        return next;
      });
      toast.error(errorMessage(err, 'Falha ao atualizar acesso.'));
    } finally {
      setPending((prev) => {
        const next = new Set(prev);
        next.delete(role.id);
        return next;
      });
    }
  };

  return (
    <main className="mx-auto max-w-2xl p-4">
      <h1 className="mb-4 flex items-center gap-2 text-2xl font-heading font-semibold">
        <span
          aria-hidden="true"
          className="inline-block h-6 w-1.5 rounded-full"
          style={{ backgroundColor: 'hsl(var(--brand-ink))' }}
        />
        Gerenciar acessos
      </h1>

      {!selected && (
        <>
          <label htmlFor="user-search" className="sr-only">
            Buscar usuário por nome ou e-mail
          </label>
          <input
            id="user-search"
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Buscar por nome ou e-mail"
            className="mb-4 min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
          />

          {!loading && rows.length === 0 && (
            <p className="text-muted-foreground">Nenhum usuário encontrado.</p>
          )}

          <ul className="space-y-2">
            {rows.map((u) => (
              <li key={u.id}>
                <button
                  type="button"
                  onClick={() => selectUser(u)}
                  className="flex min-h-[44px] w-full flex-col items-start gap-1 rounded-lg border border-border px-3 py-2 text-left text-sm hover:bg-accent"
                >
                  <span className="flex w-full items-center justify-between gap-2">
                    <span className="font-medium">{u.displayName}</span>
                    {u.unitLabel && <span className="text-muted-foreground">{u.unitLabel}</span>}
                  </span>
                  {u.roles.length > 0 && (
                    <span className="flex flex-wrap gap-1">
                      {u.roles.map((r) => (
                        <span
                          key={r.id}
                          className="rounded-full bg-accent px-2 py-0.5 text-xs text-accent-foreground"
                        >
                          {r.label}
                        </span>
                      ))}
                    </span>
                  )}
                </button>
              </li>
            ))}
          </ul>

          {!last && (
            <Button
              type="button"
              variant="outline"
              className="mt-4 min-h-[44px] w-full"
              disabled={loading}
              onClick={() => void load(query, page + 1, true)}
            >
              Carregar mais
            </Button>
          )}
        </>
      )}

      {selected && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              {selected.displayName}
              {selected.unitLabel ? ` — ${selected.unitLabel}` : ''}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {roles.map((role) => (
              <label key={role.id} className="flex min-h-[44px] items-center gap-3 text-sm">
                <input
                  type="checkbox"
                  className="h-5 w-5"
                  checked={roleIds.has(role.id)}
                  onChange={() => toggle(role)}
                  aria-label={role.label}
                  disabled={pending.has(role.id)}
                />
                <span>{role.label}</span>
              </label>
            ))}
            <Button type="button" variant="outline" className="min-h-[44px]" onClick={back}>
              Voltar à busca
            </Button>
          </CardContent>
        </Card>
      )}
    </main>
  );
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/features/access/pages/AccessManagementPage.test.tsx`
Expected: PASS (4 testes).

- [ ] **Step 5: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/access/pages/AccessManagementPage.tsx \
        frontend/src/features/access/pages/AccessManagementPage.test.tsx
git commit -m "feat(access): tela de acessos vira lista paginada com badges e Carregar mais"
```

---

## Task 6: Verificação e entrega

**Files:** nenhum (verificação).

- [ ] **Step 1: Suíte completa do frontend**

Run: `cd frontend && npx vitest run`
Expected: todos passam.

- [ ] **Step 2: Suíte completa do backend**

Run: `cd backend && ./mvnw -o test`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Merge na main + push**

```bash
git checkout main
git merge --no-ff feat/access-user-list -m "Merge branch 'feat/access-user-list'"
git push origin main
```
(O pre-push roda back+front; deve passar.)

- [ ] **Step 4: Redeploy HML (backend + frontend) e validar**

Disparar deploy dos dois apps HML via Dokploy (`backend-hml` `LS9cOzFIeHV3ikQ-Dv9aK`, `frontend-hml` `vlvpO2U7y51r-zq2q1vuM`), aguardar `done` + readiness 200 + bundle novo. Validar em `/admin/acessos`: lista aparece ao abrir, badges corretas, "Carregar mais" e filtro funcionam, clicar edita.

---

## Self-Review

- **Cobertura do spec:** endpoint paginado (Task 3) ✓; DTOs + badges sem N+1 (Tasks 1-2) ✓; `findActivePage` term opcional + `findById_UserIdIn` (Task 1) ✓; frontend `listUsers` (Task 4) ✓; tela lista/debounce/badges/"Carregar mais"/editar-voltar (Task 5) ✓; sem migração ✓; testes back (repo/service/web) e front (api/page) ✓.
- **Sem placeholders:** todos os steps têm código/comando completos.
- **Consistência de tipos:** `UserAccessRow(id, displayName, unitLabel, roles: RoleBadge[])` e `RoleBadge(id, label)` idênticos em back e front; `listUsers(q, page, size)` e `findActivePage(term, pageable)` consistentes entre tasks; `findById_UserIdIn` usado no service e definido no repo.
