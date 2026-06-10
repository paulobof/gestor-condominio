# Gestão de usuários em Gerenciar acessos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transformar `/admin/acessos` em gestão de usuários: corrigir o 500 da carga inicial, listar nome+telefone+ap+roles, criar usuário (senha gerada e mostrada uma vez) e excluir (soft delete), gated por `USER_MANAGE`.

**Architecture:** Backend `feature/access` ganha `createUser`/`deleteUser`/`creatableRoles` no service e endpoints no controller (criar/excluir sob `USER_MANAGE`, listar/roles sob `ROLE_ASSIGN`); o 500 some ao dividir a query em duas (sem parâmetro nulo). Criação usa factories de domínio (`User.newActiveByAdmin`, `UserEmail.primary`) e um gerador de senha forte. Frontend reusa a página, adicionando coluna telefone, form de cadastro inline e confirmação de exclusão inline (sem libs de modal).

**Tech Stack:** Spring Boot 3 (JPA, Spring Data Page), JUnit 5 + Mockito + Testcontainers; React + Vite + Vitest + Testing Library; axios.

**Spec:** `docs/superpowers/specs/2026-06-10-acessos-gestao-usuarios-design.md`

---

## File Structure

**Backend (criar):**
- `feature/access/dto/CreateUserRequest.java` — body do POST (validado).
- `feature/access/dto/CreatedUserResponse.java` — resposta com a senha mostrada uma vez.
- `shared/security/ProvisionalPasswordGenerator.java` — senha forte aleatória.

**Backend (modificar):**
- `feature/access/dto/UserSearchResult.java` — + `phone`.
- `feature/access/dto/UserAccessRow.java` — + `phone`.
- `feature/access/AccessUserRepository.java` — `findActivePageAll` + `findActivePageByTerm` (substitui `findActivePage`), trazendo `phone`.
- `feature/access/AccessService.java` — `listUsers` (branch null), `creatableRoles`, `createUser`, `deleteUser`; novos deps.
- `feature/access/AccessController.java` — `GET /creatable-roles`, `POST /users`, `DELETE /users/{id}`.
- `feature/user/User.java` — factory `newActiveByAdmin`.
- `feature/user/UserEmail.java` — factory `primary`.
- `shared/exception/GlobalExceptionHandler.java` — mapear `EMAIL_TAKEN`/`CANNOT_DELETE_SELF` (409) e `ROLE_NOT_CREATABLE` (422).

**Backend (testes):**
- `persistence/RepositoryPostgresTest.java` — troca por `findActivePageAll`/`findActivePageByTerm`.
- `feature/access/AccessServiceTest.java` — list (novos métodos + phone), creatableRoles, createUser, deleteUser.
- `feature/access/AccessControllerWebTest.java` — phone nas rows; gating `USER_MANAGE`; contratos POST/DELETE/creatable-roles.
- `shared/security/ProvisionalPasswordGeneratorTest.java` — passa na policy.

**Frontend (modificar):**
- `features/access/api/accessApi.ts` — `phone`, `getCreatableRoles`, `createUser`, `deleteUser`, `lookupUnit`.
- `features/access/pages/AccessManagementPage.tsx` — coluna telefone, form de cadastro, excluir com confirmação, gating `USER_MANAGE`.
- `features/access/api/accessApi.test.ts` — novos contratos.
- `features/access/pages/AccessManagementPage.test.tsx` — reescrito.

**Sem migração.**

---

## Task 1: Corrige o 500 + telefone na lista (backend)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/access/dto/UserSearchResult.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/dto/UserAccessRow.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessUserRepository.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessService.java`
- Test: `backend/src/test/java/br/com/condominio/persistence/RepositoryPostgresTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java`

- [ ] **Step 1: Atualizar os DTOs (adicionar `phone`)**

`UserSearchResult.java`:
```java
package br.com.condominio.feature.access.dto;

import java.util.UUID;

/** Projeção de usuário para a lista de acessos. {@code unitLabel}/{@code phone} podem ser nulos. */
public record UserSearchResult(UUID id, String displayName, String unitLabel, String phone) {}
```

`UserAccessRow.java`:
```java
package br.com.condominio.feature.access.dto;

import java.util.List;
import java.util.UUID;

/** Linha da lista de acessos: usuário + perfis geríveis. {@code unitLabel}/{@code phone} nulos ok. */
public record UserAccessRow(
    UUID id, String displayName, String unitLabel, String phone, List<RoleBadge> roles) {}
```

- [ ] **Step 2: Substituir a query no `AccessUserRepository` por duas (sem parâmetro nulo)**

Substituir o conteúdo de `AccessUserRepository.java` por:
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

  // Duas queries em vez de um guard ":term IS NULL": com termo nulo o Postgres não consegue
  // determinar o tipo do bind parameter no PREPARE (agravado por email citext) e estoura 500.
  @Query(
      value =
          """
          SELECT DISTINCT new br.com.condominio.feature.access.dto.UserSearchResult(
                 u.id, u.fullName, un.code, u.phone)
            FROM User u
            LEFT JOIN Unit un ON un.id = u.unitId
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
           ORDER BY u.fullName
          """,
      countQuery =
          """
          SELECT COUNT(u.id)
            FROM User u
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
          """)
  Page<UserSearchResult> findActivePageAll(Pageable pageable);

  @Query(
      value =
          """
          SELECT DISTINCT new br.com.condominio.feature.access.dto.UserSearchResult(
                 u.id, u.fullName, un.code, u.phone)
            FROM User u
            LEFT JOIN UserEmail ue ON ue.userId = u.id
            LEFT JOIN Unit un ON un.id = u.unitId
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
             AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%')))
           ORDER BY u.fullName
          """,
      countQuery =
          """
          SELECT COUNT(DISTINCT u.id)
            FROM User u
            LEFT JOIN UserEmail ue ON ue.userId = u.id
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
             AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%')))
          """)
  Page<UserSearchResult> findActivePageByTerm(@Param("term") String term, Pageable pageable);
}
```

- [ ] **Step 3: Atualizar `AccessService.listUsers` (branch no termo, mapear phone)**

Em `AccessService.java`, substituir o método `listUsers` por:
```java
  @Transactional(readOnly = true)
  public Page<UserAccessRow> listUsers(String q, Pageable pageable) {
    String term = (q == null || q.isBlank()) ? null : q.trim();
    Page<UserSearchResult> page =
        (term == null)
            ? userSearchRepo.findActivePageAll(pageable)
            : userSearchRepo.findActivePageByTerm(term, pageable);
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
        u ->
            new UserAccessRow(
                u.id(),
                u.displayName(),
                u.unitLabel(),
                u.phone(),
                rolesByUser.getOrDefault(u.id(), List.of())));
  }
```

- [ ] **Step 4: Atualizar o teste de repositório (Postgres real)**

Em `RepositoryPostgresTest.java`, substituir o teste `findActivePage_...` por (mantendo o autowire de `AccessUserRepository accessUsers`):
```java
  @Test
  void findActivePage_allAndByTerm_runAgainstPostgres() {
    // Pega o bug do 500: ":term IS NULL" com bind nulo não-tipado contra citext.
    assertThatCode(() -> accessUsers.findActivePageAll(PageRequest.of(0, 20)))
        .doesNotThrowAnyException();
    assertThatCode(() -> accessUsers.findActivePageByTerm("ana", PageRequest.of(0, 20)))
        .doesNotThrowAnyException();
    assertThat(accessUsers.findActivePageAll(PageRequest.of(0, 20))).isNotNull();
  }
```
(Se o autowire ainda referenciar `findActivePage`, ajustar. Imports `PageRequest`, `assertThat`, `assertThatCode` já existem no arquivo do plano anterior.)

- [ ] **Step 5: Atualizar `AccessServiceTest` (novos métodos + phone)**

Em `AccessServiceTest.java`, substituir os 3 testes de `listUsers` por (note o 4º arg `phone` no `UserSearchResult` e o branch de método):
```java
  @Test
  void listUsers_mapsRolesIntoBadges_withPhone() {
    var u1 = new UserSearchResult(TARGET, "Ana Lima", "A-101", "+5511999999999");
    Role muralEditor = role((short) 6, "Editor do Mural", null, true);
    when(userSearchRepo.findActivePageAll(PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(u1)));
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of(muralEditor));
    when(userRoleRepo.findById_UserIdIn(List.of(TARGET)))
        .thenReturn(List.of(new UserRole(new UserRoleId(TARGET, (short) 6), null, ACTOR)));

    Page<UserAccessRow> page = service.listUsers("", PageRequest.of(0, 20));

    UserAccessRow row = page.getContent().get(0);
    assertThat(row.displayName()).isEqualTo("Ana Lima");
    assertThat(row.phone()).isEqualTo("+5511999999999");
    assertThat(row.roles()).containsExactly(new RoleBadge((short) 6, "Editor do Mural"));
  }

  @Test
  void listUsers_blankQuery_usesFindAll_andUserWithoutRoleHasEmptyBadges() {
    var u1 = new UserSearchResult(TARGET, "Bruno Sá", null, null);
    when(userSearchRepo.findActivePageAll(PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(u1)));
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of());
    when(userRoleRepo.findById_UserIdIn(List.of(TARGET))).thenReturn(List.of());

    Page<UserAccessRow> page = service.listUsers("   ", PageRequest.of(0, 20));

    assertThat(page.getContent().get(0).roles()).isEmpty();
  }

  @Test
  void listUsers_withTerm_trimsAndUsesByTerm() {
    when(userSearchRepo.findActivePageByTerm("ana", PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of()));
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of());

    service.listUsers("  ana  ", PageRequest.of(0, 20));

    verify(userSearchRepo).findActivePageByTerm("ana", PageRequest.of(0, 20));
  }
```

- [ ] **Step 6: Atualizar `AccessControllerWebTest` (phone no construtor das rows)**

Em `AccessControllerWebTest.java`, no teste `users_withRoleAssign_returns200_pagedWithBadges`, trocar a criação da row por (4º arg `phone`):
```java
    var row =
        new UserAccessRow(
            TARGET,
            "Ana Lima",
            "A-101",
            "+5511999999999",
            List.of(new RoleBadge((short) 6, "Editor do Mural")));
```
E adicionar a asserção de phone:
```java
        .andExpect(jsonPath("$.content[0].phone").value("+5511999999999"))
```

- [ ] **Step 7: Rodar os testes do backend afetados**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest,AccessControllerWebTest,RepositoryPostgresTest`
Expected: PASS (RepositoryPostgresTest pula sem Docker; roda no pre-push/CI).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/dto/UserSearchResult.java \
        backend/src/main/java/br/com/condominio/feature/access/dto/UserAccessRow.java \
        backend/src/main/java/br/com/condominio/feature/access/AccessUserRepository.java \
        backend/src/main/java/br/com/condominio/feature/access/AccessService.java \
        backend/src/test/java/br/com/condominio/persistence/RepositoryPostgresTest.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java
git commit -m "fix(access): corrige 500 do termo nulo e adiciona telefone na lista"
```

---

## Task 2: Roles do cadastro (`creatableRoles`) (backend)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessService.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java`

- [ ] **Step 1: Teste de service — `creatableRoles` = assignable ∪ RESIDENT**

Em `AccessServiceTest.java`, adicionar imports `import br.com.condominio.feature.access.dto.AssignableRoleView;`, `import br.com.condominio.feature.role.RoleName;`, `import java.util.Optional;` (se faltarem) e o teste:
```java
  @Test
  void creatableRoles_areAssignablePlusResident() {
    Role council = role((short) 2, "Conselheiro", (short) 3, true);
    Role resident = role((short) 4, "Morador", null, false);
    doReturn(br.com.condominio.feature.role.RoleName.RESIDENT).when(resident).getName();
    doReturn(br.com.condominio.feature.role.RoleName.COUNCIL).when(council).getName();
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of(council));
    when(roleRepo.findByName(RoleName.RESIDENT)).thenReturn(Optional.of(resident));

    List<AssignableRoleView> out = service.creatableRoles();

    assertThat(out).extracting(AssignableRoleView::id).containsExactlyInAnyOrder((short) 2, (short) 4);
  }
```

- [ ] **Step 2: Rodar e ver falhar (método não existe)**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest#creatableRoles_areAssignablePlusResident`
Expected: falha de compilação — `creatableRoles` ausente.

- [ ] **Step 3: Implementar `creatableRoles` + helper de ids no `AccessService`**

Adicionar imports em `AccessService.java`: `import br.com.condominio.feature.role.RoleName;`, `import java.util.HashSet;`, `import java.util.Set;`. Adicionar métodos:
```java
  @Transactional(readOnly = true)
  public List<AssignableRoleView> creatableRoles() {
    List<Role> roles = new ArrayList<>(roleRepo.findByAssignableTrue());
    roleRepo.findByName(RoleName.RESIDENT).ifPresent(roles::add);
    return roles.stream()
        .sorted(Comparator.comparing(Role::getId))
        .map(r -> new AssignableRoleView(r.getId(), r.getName().name(), r.getLabel()))
        .toList();
  }

  private Set<Short> creatableRoleIds() {
    Set<Short> ids =
        roleRepo.findByAssignableTrue().stream().map(Role::getId).collect(Collectors.toCollection(HashSet::new));
    roleRepo.findByName(RoleName.RESIDENT).ifPresent(r -> ids.add(r.getId()));
    return ids;
  }
```
(O import `AssignableRoleView` já existe — é usado por `assignableRoles`.)

- [ ] **Step 4: Endpoint `GET /creatable-roles`**

Em `AccessController.java`, adicionar:
```java
  @GetMapping("/creatable-roles")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public List<AssignableRoleView> creatableRoles() {
    return service.creatableRoles();
  }
```

- [ ] **Step 5: Teste de contrato**

Em `AccessControllerWebTest.java`, adicionar:
```java
  @Test
  void creatableRoles_withRoleAssign_returns200() throws Exception {
    when(service.creatableRoles())
        .thenReturn(List.of(new AssignableRoleView((short) 4, "RESIDENT", "Morador")));

    mvc.perform(get("/api/access/creatable-roles").with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("Morador"));
  }
```

- [ ] **Step 6: Rodar e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest,AccessControllerWebTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/AccessService.java \
        backend/src/main/java/br/com/condominio/feature/access/AccessController.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java
git commit -m "feat(access): endpoint de roles do cadastro (assignable + Morador)"
```

---

## Task 3: Factories de domínio + gerador de senha (backend)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/user/User.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/user/UserEmail.java`
- Create: `backend/src/main/java/br/com/condominio/shared/security/ProvisionalPasswordGenerator.java`
- Test: `backend/src/test/java/br/com/condominio/shared/security/ProvisionalPasswordGeneratorTest.java`

- [ ] **Step 1: Teste do gerador (passa na policy)**

`ProvisionalPasswordGeneratorTest.java`:
```java
package br.com.condominio.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominio.shared.validation.StrongPasswordValidator;
import org.junit.jupiter.api.RepeatedTest;

class ProvisionalPasswordGeneratorTest {

  private final ProvisionalPasswordGenerator generator = new ProvisionalPasswordGenerator();
  private final StrongPasswordValidator validator = new StrongPasswordValidator();

  @RepeatedTest(50)
  void generatesPasswordThatPassesStrongPolicy() {
    String pw = generator.generate();
    assertThat(pw).hasSizeGreaterThanOrEqualTo(8);
    assertThat(validator.isValid(pw, null)).isTrue();
  }
}
```

- [ ] **Step 2: Rodar e ver falhar (classe não existe)**

Run: `cd backend && ./mvnw -o test -Dtest=ProvisionalPasswordGeneratorTest`
Expected: falha de compilação.

- [ ] **Step 3: Implementar o gerador**

`ProvisionalPasswordGenerator.java`:
```java
package br.com.condominio.shared.security;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Gera senha provisória forte (passa em {@code StrongPasswordValidator}): 16 chars, 1 de cada classe. */
@Component
public class ProvisionalPasswordGenerator {

  private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
  private static final String DIGIT = "23456789";
  private static final String SPECIAL = "!@#$%*-_";
  private static final String ALL = UPPER + LOWER + DIGIT + SPECIAL;

  private final SecureRandom rnd = new SecureRandom();

  public String generate() {
    char[] out = new char[16];
    out[0] = pick(UPPER);
    out[1] = pick(LOWER);
    out[2] = pick(DIGIT);
    out[3] = pick(SPECIAL);
    for (int i = 4; i < out.length; i++) {
      out[i] = pick(ALL);
    }
    for (int i = out.length - 1; i > 0; i--) {
      int j = rnd.nextInt(i + 1);
      char t = out[i];
      out[i] = out[j];
      out[j] = t;
    }
    return new String(out);
  }

  private char pick(String pool) {
    return pool.charAt(rnd.nextInt(pool.length()));
  }
}
```

- [ ] **Step 4: Factory `User.newActiveByAdmin`**

Em `User.java`, adicionar dentro da seção "Métodos de domínio":
```java
  /** Cria usuário ACTIVE pelo admin (qualquer unidade), com troca de senha obrigatória no 1º login. */
  public static User newActiveByAdmin(
      UUID unitId, String fullName, String phone, String passwordHash, short pepperVersion) {
    User u = new User();
    u.unitId = unitId;
    u.isUnitMaster = false;
    u.fullName = fullName;
    u.phone = phone;
    u.passwordHash = passwordHash;
    u.passwordPepperVersion = pepperVersion;
    u.mustChangePassword = true;
    u.status = UserStatus.ACTIVE;
    u.whatsappOptIn = false;
    return u;
  }
```

- [ ] **Step 5: Factory `UserEmail.primary`**

Em `UserEmail.java`, adicionar antes do fechamento da classe:
```java
  /** Cria o e-mail primário de um usuário. */
  public static UserEmail primary(UUID userId, String email) {
    UserEmail e = new UserEmail();
    e.userId = userId;
    e.email = email;
    e.isPrimary = true;
    return e;
  }
```

- [ ] **Step 6: Rodar e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=ProvisionalPasswordGeneratorTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/user/User.java \
        backend/src/main/java/br/com/condominio/feature/user/UserEmail.java \
        backend/src/main/java/br/com/condominio/shared/security/ProvisionalPasswordGenerator.java \
        backend/src/test/java/br/com/condominio/shared/security/ProvisionalPasswordGeneratorTest.java
git commit -m "feat(user): factories de criação por admin + gerador de senha provisória"
```

---

## Task 4: `createUser` (service + controller + DTOs + erro)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/access/dto/CreateUserRequest.java`
- Create: `backend/src/main/java/br/com/condominio/feature/access/dto/CreatedUserResponse.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessService.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessController.java`
- Modify: `backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java`

- [ ] **Step 1: DTOs**

`CreateUserRequest.java`:
```java
package br.com.condominio.feature.access.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** Cadastro de usuário pelo admin. {@code unitId} opcional; {@code roleIds} ≥1. */
public record CreateUserRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
    UUID unitId,
    @NotEmpty List<Short> roleIds) {}
```

`CreatedUserResponse.java`:
```java
package br.com.condominio.feature.access.dto;

import java.util.UUID;

/** Resposta do cadastro: senha provisória mostrada uma única vez. */
public record CreatedUserResponse(UUID id, String fullName, String password) {}
```

- [ ] **Step 2: Testes de service (happy, e-mail duplicado, role não-criável)**

Em `AccessServiceTest.java`, adicionar imports:
```java
import br.com.condominio.feature.access.dto.CreateUserRequest;
import br.com.condominio.feature.access.dto.CreatedUserResponse;
import br.com.condominio.feature.user.UserEmail;
import br.com.condominio.feature.user.UserEmailRepository;
import br.com.condominio.shared.security.ProvisionalPasswordGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
```
Adicionar mocks ao topo da classe (junto aos outros `@Mock`):
```java
  @Mock private UserEmailRepository emailRepo;
  @Mock private PasswordEncoder encoder;
  @Mock private ProvisionalPasswordGenerator passwordGenerator;
```
Adicionar os testes:
```java
  @Test
  void createUser_happyPath_savesUserEmailRolesAndReturnsPassword() {
    Role resident = role((short) 4, "Morador", null, false);
    doReturn(br.com.condominio.feature.role.RoleName.RESIDENT).when(resident).getName();
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of());
    when(roleRepo.findByName(br.com.condominio.feature.role.RoleName.RESIDENT))
        .thenReturn(Optional.of(resident));
    when(emailRepo.findActiveByEmailIgnoreCase("ana@x.com")).thenReturn(Optional.empty());
    when(passwordGenerator.generate()).thenReturn("Abc123!xYZ09__a");
    when(encoder.encode("Abc123!xYZ09__a")).thenReturn("HASH");
    User saved = mock(User.class);
    when(saved.getId()).thenReturn(TARGET);
    when(saved.getFullName()).thenReturn("Ana Lima");
    when(userRepo.save(any(User.class))).thenReturn(saved);

    CreateUserRequest req =
        new CreateUserRequest("Ana Lima", "ana@x.com", "+5511999999999", null, List.of((short) 4));
    CreatedUserResponse out = service.createUser(ACTOR, req);

    assertThat(out.password()).isEqualTo("Abc123!xYZ09__a");
    assertThat(out.id()).isEqualTo(TARGET);
    verify(emailRepo).save(any(UserEmail.class));
    verify(userRoleRepo).save(any(UserRole.class));
    verify(logRepo).save(any(RoleAssignmentLog.class));
  }

  @Test
  void createUser_emailTaken_throwsConflict() {
    when(emailRepo.findActiveByEmailIgnoreCase("dup@x.com"))
        .thenReturn(Optional.of(mock(UserEmail.class)));

    CreateUserRequest req =
        new CreateUserRequest("Ana", "dup@x.com", "+5511999999999", null, List.of((short) 4));
    assertThatThrownBy(() -> service.createUser(ACTOR, req))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("EMAIL_TAKEN");
    verify(userRepo, never()).save(any());
  }

  @Test
  void createUser_roleNotCreatable_throws() {
    Role resident = role((short) 4, "Morador", null, false);
    doReturn(br.com.condominio.feature.role.RoleName.RESIDENT).when(resident).getName();
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of());
    when(roleRepo.findByName(br.com.condominio.feature.role.RoleName.RESIDENT))
        .thenReturn(Optional.of(resident));
    when(emailRepo.findActiveByEmailIgnoreCase("ana@x.com")).thenReturn(Optional.empty());

    CreateUserRequest req =
        new CreateUserRequest(
            "Ana", "ana@x.com", "+5511999999999", null, List.of((short) 1)); // MANAGER
    assertThatThrownBy(() -> service.createUser(ACTOR, req))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("ROLE_NOT_CREATABLE");
    verify(userRepo, never()).save(any());
  }
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest`
Expected: falha de compilação — `createUser` ausente / construtor do service.

- [ ] **Step 4: Implementar `createUser` no service (novos deps)**

Em `AccessService.java`: adicionar `@Slf4j` na classe (import `lombok.extern.slf4j.Slf4j;`) e os campos finais:
```java
  private final UserEmailRepository emailRepo;
  private final PasswordEncoder encoder;
  private final ProvisionalPasswordGenerator passwordGenerator;
```
Adicionar imports:
```java
import br.com.condominio.feature.access.dto.CreateUserRequest;
import br.com.condominio.feature.access.dto.CreatedUserResponse;
import br.com.condominio.feature.user.UserEmail;
import br.com.condominio.feature.user.UserEmailRepository;
import br.com.condominio.shared.security.ProvisionalPasswordGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
```
Adicionar o método:
```java
  @Transactional
  public CreatedUserResponse createUser(UUID actorId, CreateUserRequest req) {
    if (emailRepo.findActiveByEmailIgnoreCase(req.email()).isPresent()) {
      throw new AccessException("EMAIL_TAKEN", "E-mail já cadastrado.");
    }
    Set<Short> creatable = creatableRoleIds();
    for (Short rid : req.roleIds()) {
      if (!creatable.contains(rid)) {
        throw new AccessException(
            "ROLE_NOT_CREATABLE", "Perfil não pode ser atribuído no cadastro.");
      }
    }
    String plain = passwordGenerator.generate();
    User user =
        User.newActiveByAdmin(
            req.unitId(), req.fullName().trim(), req.phone().trim(), encoder.encode(plain), (short) 1);
    user = userRepo.save(user);
    emailRepo.save(UserEmail.primary(user.getId(), req.email().trim()));
    Instant now = Instant.now();
    for (Short rid : req.roleIds()) {
      userRoleRepo.save(new UserRole(new UserRoleId(user.getId(), rid), now, actorId));
      logRepo.save(RoleAssignmentLog.assign(user.getId(), rid, actorId));
    }
    log.info("Admin {} criou usuário {}", actorId, user.getId());
    return new CreatedUserResponse(user.getId(), user.getFullName(), plain);
  }
```

- [ ] **Step 5: Mapear os erros novos no `GlobalExceptionHandler`**

Em `GlobalExceptionHandler.java`, no `switch` do `handleAccess`, trocar as duas primeiras linhas por:
```java
          case "ROLE_LIMIT_REACHED", "EMAIL_TAKEN", "CANNOT_DELETE_SELF" -> HttpStatus.CONFLICT;
          case "ROLE_NOT_FOUND", "USER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "ROLE_NOT_ASSIGNABLE", "USER_NOT_ACTIVE", "ROLE_NOT_CREATABLE" ->
              HttpStatus.UNPROCESSABLE_ENTITY;
```

- [ ] **Step 6: Endpoint `POST /users`**

Em `AccessController.java`, adicionar imports:
```java
import br.com.condominio.feature.access.dto.CreateUserRequest;
import br.com.condominio.feature.access.dto.CreatedUserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
```
Adicionar o método:
```java
  @PostMapping("/users")
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ResponseEntity<CreatedUserResponse> createUser(
      @Valid @RequestBody CreateUserRequest req,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(me.userId(), req));
  }
```

- [ ] **Step 7: Testes de contrato (201, 403 sem USER_MANAGE, 409 e-mail)**

Em `AccessControllerWebTest.java`, adicionar imports:
```java
import br.com.condominio.feature.access.dto.CreateUserRequest;
import br.com.condominio.feature.access.dto.CreatedUserResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
```
Adicionar `@Autowired private ObjectMapper om;` e a constante `private static final String MANAGE = "USER_MANAGE";`. Testes:
```java
  @Test
  void createUser_withUserManage_returns201_withPassword() throws Exception {
    when(service.createUser(eq(UID), any(CreateUserRequest.class)))
        .thenReturn(new CreatedUserResponse(TARGET, "Ana Lima", "Abc123!xYZ09__a"));
    var body =
        new CreateUserRequest("Ana Lima", "ana@x.com", "+5511999999999", null, List.of((short) 4));

    mvc.perform(
            post("/api/access/users")
                .with(MockAuth.user(UID, MANAGE))
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.password").value("Abc123!xYZ09__a"));
  }

  @Test
  void createUser_withoutUserManage_returns403() throws Exception {
    var body =
        new CreateUserRequest("Ana Lima", "ana@x.com", "+5511999999999", null, List.of((short) 4));
    mvc.perform(
            post("/api/access/users")
                .with(MockAuth.user(UID, ASSIGN)) // só ROLE_ASSIGN
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isForbidden());
    verify(service, never()).createUser(any(), any());
  }

  @Test
  void createUser_emailTaken_returns409() throws Exception {
    doThrow(new AccessException("EMAIL_TAKEN", "E-mail já cadastrado."))
        .when(service)
        .createUser(eq(UID), any(CreateUserRequest.class));
    var body =
        new CreateUserRequest("Ana", "dup@x.com", "+5511999999999", null, List.of((short) 4));

    mvc.perform(
            post("/api/access/users")
                .with(MockAuth.user(UID, MANAGE))
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }
```

- [ ] **Step 8: Rodar e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest,AccessControllerWebTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/dto/CreateUserRequest.java \
        backend/src/main/java/br/com/condominio/feature/access/dto/CreatedUserResponse.java \
        backend/src/main/java/br/com/condominio/feature/access/AccessService.java \
        backend/src/main/java/br/com/condominio/feature/access/AccessController.java \
        backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java
git commit -m "feat(access): criar usuário pelo admin com senha gerada (USER_MANAGE)"
```

---

## Task 5: `deleteUser` (service + controller)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessService.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java`

- [ ] **Step 1: Testes de service (soft delete, self, inexistente)**

Em `AccessServiceTest.java`, adicionar:
```java
  @Test
  void deleteUser_softDeletesUserAndEmails() {
    User target = mock(User.class);
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(target));
    UserEmail e = mock(UserEmail.class);
    when(emailRepo.findByUserId(TARGET)).thenReturn(List.of(e));

    service.deleteUser(ACTOR, TARGET);

    verify(emailRepo).delete(e);
    verify(userRepo).delete(target);
  }

  @Test
  void deleteUser_self_throwsConflict() {
    assertThatThrownBy(() -> service.deleteUser(ACTOR, ACTOR))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("CANNOT_DELETE_SELF");
    verify(userRepo, never()).delete(any());
  }

  @Test
  void deleteUser_notFound_throws() {
    when(userRepo.findById(TARGET)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.deleteUser(ACTOR, TARGET))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("USER_NOT_FOUND");
  }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest`
Expected: falha de compilação — `deleteUser` ausente.

- [ ] **Step 3: Implementar `deleteUser`**

Em `AccessService.java`, adicionar:
```java
  @Transactional
  public void deleteUser(UUID actorId, UUID targetUserId) {
    if (actorId.equals(targetUserId)) {
      throw new AccessException("CANNOT_DELETE_SELF", "Você não pode excluir a si mesmo.");
    }
    User user =
        userRepo
            .findById(targetUserId)
            .orElseThrow(() -> new AccessException("USER_NOT_FOUND", "Usuário não encontrado."));
    emailRepo.findByUserId(targetUserId).forEach(emailRepo::delete);
    userRepo.delete(user);
    log.info("Admin {} excluiu (soft) usuário {}", actorId, targetUserId);
  }
```
(Requer que `UserRepository` exponha `delete(User)` — herda de `JpaRepository`. `UserEmailRepository` já tem `findByUserId` e `delete`.)

- [ ] **Step 4: Endpoint `DELETE /users/{id}`**

Em `AccessController.java`, adicionar:
```java
  @DeleteMapping("/users/{id}")
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ResponseEntity<Void> deleteUser(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.deleteUser(me.userId(), id);
    return ResponseEntity.noContent().build();
  }
```

- [ ] **Step 5: Testes de contrato (204, 403 sem USER_MANAGE, 409 self)**

Em `AccessControllerWebTest.java`, adicionar:
```java
  @Test
  void deleteUser_withUserManage_returns204() throws Exception {
    mvc.perform(delete("/api/access/users/{id}", TARGET).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isNoContent());
    verify(service).deleteUser(UID, TARGET);
  }

  @Test
  void deleteUser_withoutUserManage_returns403() throws Exception {
    mvc.perform(delete("/api/access/users/{id}", TARGET).with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isForbidden());
    verify(service, never()).deleteUser(any(), any());
  }

  @Test
  void deleteUser_self_returns409() throws Exception {
    doThrow(new AccessException("CANNOT_DELETE_SELF", "Você não pode excluir a si mesmo."))
        .when(service)
        .deleteUser(eq(UID), eq(UID));
    mvc.perform(delete("/api/access/users/{id}", UID).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANNOT_DELETE_SELF"));
  }
```

- [ ] **Step 6: Rodar a suíte inteira do backend**

Run: `cd backend && ./mvnw -o test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/AccessService.java \
        backend/src/main/java/br/com/condominio/feature/access/AccessController.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java
git commit -m "feat(access): excluir usuário (soft delete + libera e-mail, USER_MANAGE)"
```

---

## Task 6: `accessApi` frontend

**Files:**
- Modify: `frontend/src/features/access/api/accessApi.ts`
- Test: `frontend/src/features/access/api/accessApi.test.ts`

- [ ] **Step 1: Testes dos novos contratos**

Em `accessApi.test.ts`, adicionar ao import de `./accessApi`: `getCreatableRoles, createUser, deleteUser, lookupUnit`. Adicionar os testes dentro do `describe`:
```ts
  it('getCreatableRoles faz GET em /access/creatable-roles', async () => {
    get.mockResolvedValue({ data: [] });
    await getCreatableRoles();
    expect(get).toHaveBeenCalledWith('/access/creatable-roles');
  });

  it('createUser faz POST em /access/users com o payload', async () => {
    post.mockResolvedValue({ data: { id: 'u9', fullName: 'Ana', password: 'X1y!aaaa' } });
    const out = await createUser({
      fullName: 'Ana',
      email: 'ana@x.com',
      phone: '+5511999999999',
      unitId: null,
      roleIds: [4],
    });
    expect(post).toHaveBeenCalledWith('/access/users', {
      fullName: 'Ana',
      email: 'ana@x.com',
      phone: '+5511999999999',
      unitId: null,
      roleIds: [4],
    });
    expect(out.password).toBe('X1y!aaaa');
  });

  it('deleteUser faz DELETE no path do usuário', async () => {
    del.mockResolvedValue({ data: undefined });
    await deleteUser('u9');
    expect(del).toHaveBeenCalledWith('/access/users/u9');
  });

  it('lookupUnit faz GET em /units/lookup com o code', async () => {
    get.mockResolvedValue({ data: { id: 'unit1', code: '101A' } });
    const out = await lookupUnit('101A');
    expect(get).toHaveBeenCalledWith('/units/lookup', { params: { code: '101A' } });
    expect(out.id).toBe('unit1');
  });
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/access/api/accessApi.test.ts`
Expected: FAIL — funções não exportadas.

- [ ] **Step 3: Implementar no `accessApi.ts`**

Em `accessApi.ts`: adicionar `phone` em `UserAccessRow` e as funções/tipos novos:
```ts
export interface UserAccessRow {
  id: string;
  displayName: string;
  unitLabel: string | null;
  phone: string | null;
  roles: RoleBadge[];
}

export interface CreateUserPayload {
  fullName: string;
  email: string;
  phone: string;
  unitId: string | null;
  roleIds: number[];
}

export interface CreatedUser {
  id: string;
  fullName: string;
  password: string;
}

export async function getCreatableRoles() {
  const r = await api.get('/access/creatable-roles');
  return r.data as AssignableRole[];
}

export async function createUser(payload: CreateUserPayload) {
  const r = await api.post('/access/users', payload);
  return r.data as CreatedUser;
}

export async function deleteUser(id: string) {
  await api.delete(`/access/users/${id}`);
}

export async function lookupUnit(code: string) {
  const r = await api.get('/units/lookup', { params: { code } });
  return r.data as { id: string; code: string };
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/features/access/api/accessApi.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/access/api/accessApi.ts frontend/src/features/access/api/accessApi.test.ts
git commit -m "feat(access): accessApi para criar/excluir usuário, creatable-roles e lookup de unidade"
```

---

## Task 7: `AccessManagementPage` — telefone, cadastro, excluir, gating

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
  getCreatableRoles: vi.fn(),
  createUser: vi.fn(),
  deleteUser: vi.fn(),
  lookupUnit: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { AccessManagementPage } from './AccessManagementPage';
import {
  listUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  getCreatableRoles,
  createUser,
  deleteUser,
  type UserAccessRow,
} from '../api/accessApi';
import { useAuth } from '@/features/auth/useAuth';

const listMock = vi.mocked(listUsers);
const rolesMock = vi.mocked(listAssignableRoles);
const userRolesMock = vi.mocked(getUserRoleIds);
const assignMock = vi.mocked(assignRole);
const creatableMock = vi.mocked(getCreatableRoles);
const createMock = vi.mocked(createUser);
const deleteMock = vi.mocked(deleteUser);
const authMock = vi.mocked(useAuth);

const ROLES = [
  { id: 2, name: 'COUNCIL', label: 'Conselheiro' },
  { id: 6, name: 'MURAL_EDITOR', label: 'Editor do Mural' },
];
const CREATABLE = [
  { id: 4, name: 'RESIDENT', label: 'Morador' },
  { id: 2, name: 'COUNCIL', label: 'Conselheiro' },
];

function pageOf(content: UserAccessRow[], last = true, number = 0) {
  return { content, number, totalPages: last ? number + 1 : number + 2, last };
}

function setAuth(authorities: string[]) {
  // só o que a página usa: user.authorities
  authMock.mockReturnValue({ user: { authorities } } as unknown as ReturnType<typeof useAuth>);
}

beforeEach(() => {
  vi.clearAllMocks();
  setAuth(['ROLE_ASSIGN', 'USER_MANAGE']);
  rolesMock.mockResolvedValue(ROLES);
  creatableMock.mockResolvedValue(CREATABLE);
  listMock.mockResolvedValue(
    pageOf([
      {
        id: 'u1',
        displayName: 'Ana Lima',
        unitLabel: 'A-101',
        phone: '+5511988887777',
        roles: [{ id: 6, label: 'Editor do Mural' }],
      },
    ])
  );
  userRolesMock.mockResolvedValue([6]);
  assignMock.mockResolvedValue(undefined);
  deleteMock.mockResolvedValue(undefined);
  createMock.mockResolvedValue({ id: 'u9', fullName: 'Novo User', password: 'Abc123!xyZ09__a' });
});

describe('AccessManagementPage', () => {
  it('lista usuários com nome, telefone e badges', async () => {
    render(<AccessManagementPage />);
    expect(await screen.findByText('Ana Lima')).toBeInTheDocument();
    expect(screen.getByText('+5511988887777')).toBeInTheDocument();
    expect(screen.getByText('Editor do Mural')).toBeInTheDocument();
  });

  it('clicar no nome abre os toggles e marcar role chama assignRole', async () => {
    userRolesMock.mockResolvedValue([]);
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await user.click(await screen.findByRole('button', { name: /ana lima/i }));
    await user.click(await screen.findByLabelText('Editor do Mural'));
    await waitFor(() => expect(assignMock).toHaveBeenCalledWith('u1', 6));
  });

  it('excluir pede confirmação e chama deleteUser', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /excluir ana lima/i }));
    await user.click(await screen.findByRole('button', { name: /^confirmar$/i }));
    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith('u1'));
    await waitFor(() => expect(screen.queryByText('Ana Lima')).not.toBeInTheDocument());
  });

  it('adicionar usuário cria e mostra a senha uma vez', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /adicionar usuário/i }));
    await user.type(screen.getByLabelText(/nome/i), 'Novo User');
    await user.type(screen.getByLabelText(/e-mail/i), 'novo@x.com');
    await user.type(screen.getByLabelText(/telefone/i), '+5511988887777');
    // Morador já vem marcado (default)
    await user.click(screen.getByRole('button', { name: /^criar$/i }));
    await waitFor(() => expect(createMock).toHaveBeenCalled());
    expect(await screen.findByText('Abc123!xyZ09__a')).toBeInTheDocument();
  });

  it('sem USER_MANAGE esconde adicionar e excluir', async () => {
    setAuth(['ROLE_ASSIGN']);
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    expect(screen.queryByRole('button', { name: /adicionar usuário/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /excluir ana lima/i })).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/access/pages/AccessManagementPage.test.tsx`
Expected: FAIL — página não tem cadastro/excluir/gating.

- [ ] **Step 3: Reescrever a página**

Substituir todo o conteúdo de `AccessManagementPage.tsx` por:
```tsx
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';
import {
  listUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
  getCreatableRoles,
  createUser,
  deleteUser,
  lookupUnit,
  type AssignableRole,
  type UserAccessRow,
} from '../api/accessApi';

function errorMessage(err: unknown, fallback: string): string {
  const maybe = err as { response?: { data?: { message?: string } } };
  return maybe?.response?.data?.message ?? fallback;
}

const PAGE_SIZE = 20;

export function AccessManagementPage() {
  const { user } = useAuth();
  const canManage = user?.authorities.includes('USER_MANAGE') ?? false;

  const [roles, setRoles] = useState<AssignableRole[]>([]);
  const [query, setQuery] = useState('');
  const [rows, setRows] = useState<UserAccessRow[]>([]);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<UserAccessRow | null>(null);
  const [roleIds, setRoleIds] = useState<Set<number>>(new Set());
  const [pending, setPending] = useState<Set<number>>(new Set());
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    listAssignableRoles()
      .then(setRoles)
      .catch(() => toast.error('Erro ao carregar os perfis de acesso.'));
  }, []);

  const load = useCallback(async (q: string, p: number, append: boolean) => {
    setLoading(true);
    try {
      const res = await listUsers(q, p, PAGE_SIZE);
      setRows((prev) => (append ? [...prev, ...res.content] : res.content));
      setPage(res.number);
      setLast(res.last);
    } catch {
      toast.error('Erro ao carregar usuários.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const t = setTimeout(() => {
      void load(query, 0, false);
    }, 300);
    return () => clearTimeout(t);
  }, [query, load]);

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
    setPending((prev) => new Set(prev).add(role.id));
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

  const onDelete = async (id: string) => {
    try {
      await deleteUser(id);
      setRows((prev) => prev.filter((r) => r.id !== id));
      toast.success('Usuário excluído.');
    } catch (err) {
      toast.error(errorMessage(err, 'Falha ao excluir usuário.'));
    } finally {
      setConfirmDelete(null);
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

      {!selected && !adding && (
        <>
          <div className="mb-4 flex gap-2">
            <label htmlFor="user-search" className="sr-only">
              Buscar usuário por nome ou e-mail
            </label>
            <input
              id="user-search"
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar por nome ou e-mail"
              className="min-h-[44px] flex-1 rounded-lg border border-border bg-background px-3 text-sm"
            />
            {canManage && (
              <Button type="button" className="min-h-[44px]" onClick={() => setAdding(true)}>
                Adicionar usuário
              </Button>
            )}
          </div>

          {!loading && rows.length === 0 && (
            <p className="text-muted-foreground">Nenhum usuário encontrado.</p>
          )}

          <ul className="space-y-2">
            {rows.map((u) => (
              <li
                key={u.id}
                className="flex items-center gap-2 rounded-lg border border-border px-2 py-1"
              >
                <button
                  type="button"
                  onClick={() => selectUser(u)}
                  className="flex min-h-[44px] flex-1 flex-col items-start gap-1 px-1 py-1 text-left text-sm hover:bg-accent"
                >
                  <span className="flex w-full flex-wrap items-center gap-x-2">
                    <span className="font-medium">{u.displayName}</span>
                    {u.unitLabel && <span className="text-muted-foreground">{u.unitLabel}</span>}
                    {u.phone && <span className="text-muted-foreground">{u.phone}</span>}
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
                {canManage &&
                  (confirmDelete === u.id ? (
                    <span className="flex shrink-0 gap-1">
                      <Button
                        type="button"
                        variant="destructive"
                        className="min-h-[44px]"
                        onClick={() => void onDelete(u.id)}
                      >
                        Confirmar
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        className="min-h-[44px]"
                        onClick={() => setConfirmDelete(null)}
                      >
                        Cancelar
                      </Button>
                    </span>
                  ) : (
                    <Button
                      type="button"
                      variant="outline"
                      className="min-h-[44px] shrink-0"
                      aria-label={`Excluir ${u.displayName}`}
                      onClick={() => setConfirmDelete(u.id)}
                    >
                      Excluir
                    </Button>
                  ))}
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

      {adding && (
        <AddUserForm
          onDone={() => {
            setAdding(false);
            void load('', 0, false);
            setQuery('');
          }}
          onCancel={() => setAdding(false)}
        />
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

function AddUserForm({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const [creatable, setCreatable] = useState<AssignableRole[]>([]);
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [unitCode, setUnitCode] = useState('');
  const [picked, setPicked] = useState<Set<number>>(new Set());
  const [saving, setSaving] = useState(false);
  const [createdPassword, setCreatedPassword] = useState<string | null>(null);

  useEffect(() => {
    getCreatableRoles()
      .then((rs) => {
        setCreatable(rs);
        const resident = rs.find((r) => r.name === 'RESIDENT');
        if (resident) setPicked(new Set([resident.id]));
      })
      .catch(() => toast.error('Erro ao carregar os perfis.'));
  }, []);

  const toggle = (id: number) =>
    setPicked((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (picked.size === 0) {
      toast.error('Selecione ao menos um perfil.');
      return;
    }
    setSaving(true);
    try {
      let unitId: string | null = null;
      if (unitCode.trim()) {
        try {
          unitId = (await lookupUnit(unitCode.trim())).id;
        } catch {
          toast.error('Unidade não encontrada.');
          setSaving(false);
          return;
        }
      }
      const out = await createUser({
        fullName: fullName.trim(),
        email: email.trim(),
        phone: phone.trim(),
        unitId,
        roleIds: [...picked],
      });
      setCreatedPassword(out.password);
      toast.success('Usuário criado.');
    } catch (err) {
      toast.error(errorMessage(err, 'Falha ao criar usuário.'));
    } finally {
      setSaving(false);
    }
  };

  if (createdPassword) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Usuário criado</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <p>Copie a senha provisória e repasse ao usuário. Ela não será mostrada de novo:</p>
          <code className="block rounded-md bg-accent px-3 py-2 font-mono text-base">
            {createdPassword}
          </code>
          <Button type="button" className="min-h-[44px]" onClick={onDone}>
            Concluir
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Adicionar usuário</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-3" onSubmit={submit}>
          <div className="space-y-1">
            <label htmlFor="nu-name" className="text-sm font-medium">
              Nome
            </label>
            <input
              id="nu-name"
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="nu-email" className="text-sm font-medium">
              E-mail
            </label>
            <input
              id="nu-email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="nu-phone" className="text-sm font-medium">
              Telefone
            </label>
            <input
              id="nu-phone"
              required
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+5511999999999"
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="nu-unit" className="text-sm font-medium">
              Unidade (código, opcional)
            </label>
            <input
              id="nu-unit"
              value={unitCode}
              onChange={(e) => setUnitCode(e.target.value)}
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            />
          </div>
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Perfis</legend>
            {creatable.map((r) => (
              <label key={r.id} className="flex min-h-[44px] items-center gap-3 text-sm">
                <input
                  type="checkbox"
                  className="h-5 w-5"
                  checked={picked.has(r.id)}
                  onChange={() => toggle(r.id)}
                  aria-label={r.label}
                />
                <span>{r.label}</span>
              </label>
            ))}
          </fieldset>
          <div className="flex gap-2">
            <Button type="submit" className="min-h-[44px]" disabled={saving}>
              Criar
            </Button>
            <Button
              type="button"
              variant="outline"
              className="min-h-[44px]"
              onClick={onCancel}
            >
              Cancelar
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/features/access/pages/AccessManagementPage.test.tsx`
Expected: PASS (5 testes).

- [ ] **Step 5: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/access/pages/AccessManagementPage.tsx \
        frontend/src/features/access/pages/AccessManagementPage.test.tsx
git commit -m "feat(access): tela com telefone, cadastro de usuário e exclusão (gated USER_MANAGE)"
```

---

## Task 8: Verificação e entrega

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
git merge --no-ff feat/access-user-management -m "Merge branch 'feat/access-user-management'"
git push origin main
```
(O pre-push roda back+front; com Docker, roda também o `RepositoryPostgresTest` que cobre o fix do 500.)

- [ ] **Step 4: Redeploy HML + validar**

Disparar deploy dos apps HML via Dokploy (`backend-hml` `LS9cOzFIeHV3ikQ-Dv9aK`, `frontend-hml` `vlvpO2U7y51r-zq2q1vuM`); aguardar `done` + readiness 200 + bundle novo. **Lembrar do service worker** (ver [[hml-frontend-pwa-cache]]): unregister+clear+reload antes de validar. Validar em `/admin/acessos`: lista carrega ao abrir (sem 500), telefone na linha, criar usuário mostra a senha uma vez, excluir pede confirmação e remove, e os botões somem sem `USER_MANAGE`.

---

## Self-Review

- **Cobertura do spec:** fix do 500 (Task 1) ✓; telefone na lista (Task 1) ✓; creatableRoles = assignable ∪ RESIDENT (Task 2) ✓; factories + gerador (Task 3) ✓; criar usuário com senha gerada + USER_MANAGE + e-mail único + roles ⊆ creatable + mapeamento de erro (Task 4) ✓; excluir soft-delete + libera e-mail + guard self + USER_MANAGE (Task 5) ✓; accessApi (Task 6) ✓; tela telefone/cadastro/excluir/gating (Task 7) ✓; testes back (repo Postgres/service/web) e front (api/page) ✓; sem migração ✓.
- **Sem placeholders:** todos os steps têm código/comando completos.
- **Consistência de tipos:** `UserSearchResult(id, displayName, unitLabel, phone)` e `UserAccessRow(id, displayName, unitLabel, phone, roles)` idênticos entre repo/DTO/service/web/front; `findActivePageAll`/`findActivePageByTerm` usados no service e definidos no repo; `CreateUserRequest(fullName, email, phone, unitId, roleIds)` ↔ `CreateUserPayload` (front) ↔ corpo do POST; `CreatedUserResponse(id, fullName, password)` ↔ `CreatedUser`; códigos de erro (`EMAIL_TAKEN`/`CANNOT_DELETE_SELF`→409, `ROLE_NOT_CREATABLE`→422) consistentes entre service e `GlobalExceptionHandler`; gating `USER_MANAGE` (POST/DELETE) e `ROLE_ASSIGN` (GETs) consistentes entre controller e testes.
```
