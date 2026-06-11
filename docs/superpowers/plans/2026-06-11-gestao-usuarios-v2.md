# Gestão de usuários v2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir o travamento por token expirado (401 em vez de 403), permitir editar todos os dados do usuário, buscar por ap, renomear a tela para "Gestão de usuários" e resetar as checkboxes ao trocar de usuário.

**Architecture:** Backend ganha um `AuthenticationEntryPoint` 401 + endpoints `GET/PUT /api/access/users/{id}` (gated `USER_MANAGE`) com factory/métodos de domínio; a query de busca passa a casar o código da unidade. Frontend reorganiza cada linha em 3 botões (Acessos/Dados/Excluir), adiciona o form de edição de dados e endurece o `selectUser`.

**Tech Stack:** Spring Boot 3 (Spring Security, JPA), JUnit 5 + Mockito + Testcontainers; React + Vite + Vitest + Testing Library; axios.

**Spec:** `docs/superpowers/specs/2026-06-11-gestao-usuarios-v2-design.md`

---

## File Structure

**Backend (criar):**
- `shared/security/RestAuthenticationEntryPoint.java` — 401 JSON para não-autenticado.
- `feature/access/dto/UserDetail.java` — dados completos do usuário p/ prefill.
- `feature/access/dto/UpdateUserRequest.java` — body do PUT.

**Backend (modificar):**
- `shared/security/SecurityConfig.java` — wire do entrypoint 401.
- `feature/access/AccessUserRepository.java` — busca casa `un.code` (ap).
- `feature/user/User.java` — `updateProfile(...)`.
- `feature/user/UserEmail.java` — `changeEmail(...)`.
- `feature/access/AccessService.java` — `getUserDetail`, `updateUser`; dep `UnitRepository`.
- `feature/access/AccessController.java` — `GET`/`PUT /users/{id}`.

**Backend (testes):**
- `feature/access/AccessControllerWebTest.java` — 401 sem token; GET/PUT detail (USER_MANAGE).
- `feature/access/AccessServiceTest.java` — getUserDetail, updateUser.
- `feature/user/UserTest.java` — updateProfile (novo).
- `persistence/RepositoryPostgresTest.java` — busca por ap.

**Frontend (modificar):**
- `features/access/api/accessApi.ts` — `getUser`, `updateUser`, tipos.
- `features/access/api/accessApi.test.ts` — contratos novos.
- `features/access/pages/AccessManagementPage.tsx` — rename, 3 botões, EditUserForm, fix flags, placeholder.
- `features/access/pages/AccessManagementPage.test.tsx` — reescrito.
- `components/layout/Sidebar.tsx` — label "Gestão de usuários".

**Sem migração.**

---

## Task 1: Fix do token — 401 em vez de 403 (backend)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/shared/security/RestAuthenticationEntryPoint.java`
- Modify: `backend/src/main/java/br/com/condominio/shared/security/SecurityConfig.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java`

- [ ] **Step 1: Criar `RestAuthenticationEntryPoint`**

```java
package br.com.condominio.shared.security;

import br.com.condominio.shared.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Requisição não-autenticada (sem token / token expirado/inválido) → 401 JSON. */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ApiError body =
        ApiError.of(
            401,
            "Unauthorized",
            "UNAUTHENTICATED",
            "Sessão expirada ou ausente. Faça login novamente.",
            MDC.get("requestId"));
    objectMapper.writeValue(response.getWriter(), body);
  }
}
```

- [ ] **Step 2: Escrever o teste que falha (401 sem token)**

Em `AccessControllerWebTest.java`: adicionar `RestAuthenticationEntryPoint.class` ao `@Import` (passando a ser `@Import({SecurityConfig.class, JwtAuthenticationConverter.class, RestAuthenticationEntryPoint.class})`). Adicionar `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;` (já existe). Adicionar o teste:
```java
  @Test
  void users_withoutToken_returns401() throws Exception {
    mvc.perform(get("/api/access/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `cd backend && ./mvnw -o test -Dtest=AccessControllerWebTest#users_withoutToken_returns401`
Expected: FAIL — hoje devolve 403 (entrypoint default), não 401. (Pode também falhar ao instanciar SecurityConfig se o construtor ainda não tiver o entrypoint — Step 4 resolve.)

- [ ] **Step 4: Wire no `SecurityConfig`**

Em `SecurityConfig.java`: injetar o entrypoint e configurar `exceptionHandling`. Trocar a declaração do campo/constructor para incluir o entrypoint, e adicionar `.exceptionHandling(...)` na cadeia.

Campo (junto ao já existente):
```java
  private final JwtAuthenticationConverter jwtAuthenticationConverter;
  private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
```
Na cadeia `http...`, logo após `.cors(...)` e antes de `.sessionManagement(...)`, adicionar:
```java
        .exceptionHandling(e -> e.authenticationEntryPoint(restAuthenticationEntryPoint))
```
(import `org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer` já existe; nenhum import novo necessário além do tipo do campo, que está no mesmo pacote.)

- [ ] **Step 5: Rodar e ver passar (e checar que 403 por falta de permissão segue)**

Run: `cd backend && ./mvnw -o test -Dtest=AccessControllerWebTest`
Expected: PASS — `users_withoutToken_returns401` passa; os testes que usam `MockAuth.user(UID)` (autenticado sem authority) seguem **403** (`...returns403`); `users_unauthenticated_isRejected` (espera 4xx) segue verde.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/com/condominio/shared/security/RestAuthenticationEntryPoint.java \
        backend/src/main/java/br/com/condominio/shared/security/SecurityConfig.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java
git commit -m "fix(security): 401 em requisição não-autenticada (front renova o token e repete)"
```

---

## Task 2: Busca por ap (backend)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessUserRepository.java`
- Test: `backend/src/test/java/br/com/condominio/persistence/RepositoryPostgresTest.java`

- [ ] **Step 1: Acrescentar `un.code` à busca**

Em `AccessUserRepository.java`, no método `findActivePageByTerm`, trocar o bloco `AND (...)` do `value` E do `countQuery` para incluir o código da unidade:
```java
             AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(un.code) LIKE LOWER(CONCAT('%', :term, '%')))
```
(O `value` já tem `LEFT JOIN Unit un`; o `countQuery` NÃO tem o join do Unit — adicionar `LEFT JOIN Unit un ON un.id = u.unitId` no countQuery também, logo após o `LEFT JOIN UserEmail ue ...`.)

Resultado do `countQuery`:
```java
          countQuery =
              """
              SELECT COUNT(DISTINCT u.id)
                FROM User u
                LEFT JOIN UserEmail ue ON ue.userId = u.id
                LEFT JOIN Unit un ON un.id = u.unitId
               WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
                 AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
                      OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%'))
                      OR LOWER(un.code) LIKE LOWER(CONCAT('%', :term, '%')))
              """)
```

- [ ] **Step 2: Atualizar o teste de Postgres (continua só validando que a query roda)**

Em `RepositoryPostgresTest.java`, no teste `findActivePage_allAndByTerm_runAgainstPostgres`, adicionar uma chamada que casa o novo termo de ap (valida que a HQL com 3 ORs + join roda):
```java
    assertThatCode(() -> accessUsers.findActivePageByTerm("101", PageRequest.of(0, 20)))
        .doesNotThrowAnyException();
```
(Inserir junto às asserções existentes do teste.)

- [ ] **Step 3: Rodar (compila/roda; pula sem Docker)**

Run: `cd backend && ./mvnw -o test -Dtest=RepositoryPostgresTest,AccessServiceTest`
Expected: BUILD SUCCESS (RepositoryPostgresTest pula sem Docker; AccessServiceTest segue verde — `findActivePageByTerm` continua sendo chamado com termo nos testes existentes).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/AccessUserRepository.java \
        backend/src/test/java/br/com/condominio/persistence/RepositoryPostgresTest.java
git commit -m "feat(access): busca de usuários também casa o código da unidade (ap)"
```

---

## Task 3: Métodos de domínio (User.updateProfile, UserEmail.changeEmail)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/user/User.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/user/UserEmail.java`
- Test: `backend/src/test/java/br/com/condominio/feature/user/UserTest.java`

- [ ] **Step 1: Teste que falha (`updateProfile`)**

Criar `backend/src/test/java/br/com/condominio/feature/user/UserTest.java`:
```java
package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void updateProfile_setsAllEditableFields() {
    User u =
        User.newActiveByAdmin(null, "Antigo Nome", "+5511000000000", "HASH", (short) 1);
    UUID unit = UUID.randomUUID();

    u.updateProfile("Novo Nome", "Novo", "+5511999999999", unit, Gender.FEMALE, LocalDate.of(1990, 1, 2));

    assertThat(u.getFullName()).isEqualTo("Novo Nome");
    assertThat(u.getGreetingName()).isEqualTo("Novo");
    assertThat(u.getPhone()).isEqualTo("+5511999999999");
    assertThat(u.getUnitId()).isEqualTo(unit);
    assertThat(u.getGender()).isEqualTo(Gender.FEMALE);
    assertThat(u.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 2));
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -o test -Dtest=UserTest`
Expected: falha de compilação — `updateProfile` não existe.

- [ ] **Step 3: Implementar os métodos de domínio**

Em `User.java`, na seção "Métodos de domínio", adicionar:
```java
  /** Atualiza os dados editáveis do usuário (admin). E-mail é tratado fora (UserEmail). */
  public void updateProfile(
      String fullName,
      String greetingName,
      String phone,
      UUID unitId,
      Gender gender,
      java.time.LocalDate birthDate) {
    this.fullName = fullName;
    this.greetingName = greetingName;
    this.phone = phone;
    this.unitId = unitId;
    this.gender = gender;
    this.birthDate = birthDate;
  }
```

Em `UserEmail.java`, antes do fechamento da classe, adicionar:
```java
  /** Troca o endereço de e-mail (login). Unicidade é validada no service. */
  public void changeEmail(String email) {
    this.email = email;
  }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=UserTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/user/User.java \
        backend/src/main/java/br/com/condominio/feature/user/UserEmail.java \
        backend/src/test/java/br/com/condominio/feature/user/UserTest.java
git commit -m "feat(user): updateProfile e changeEmail (domínio rico) para edição pelo admin"
```

---

## Task 4: `getUserDetail` + `GET /users/{id}` (backend)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/access/dto/UserDetail.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessService.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java`

- [ ] **Step 1: DTO `UserDetail`**

```java
package br.com.condominio.feature.access.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Dados completos do usuário para preencher o formulário de edição. Campos opcionais podem ser nulos. */
public record UserDetail(
    UUID id,
    String fullName,
    String greetingName,
    String phone,
    UUID unitId,
    String unitCode,
    String email,
    String gender,
    LocalDate birthDate) {}
```

- [ ] **Step 2: Teste de service que falha**

Em `AccessServiceTest.java`, adicionar imports:
```java
import br.com.condominio.feature.access.dto.UserDetail;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.Gender;
import java.time.LocalDate;
```
Adicionar o `@Mock`:
```java
  @Mock private UnitRepository unitRepo;
```
Adicionar o teste:
```java
  @Test
  void getUserDetail_returnsFieldsAndEmailAndUnitCode() {
    UUID unitId = UUID.randomUUID();
    User u = mock(User.class);
    when(u.getId()).thenReturn(TARGET);
    when(u.getFullName()).thenReturn("Ana Lima");
    when(u.getGreetingName()).thenReturn("Ana");
    when(u.getPhone()).thenReturn("+5511999999999");
    when(u.getUnitId()).thenReturn(unitId);
    when(u.getGender()).thenReturn(Gender.FEMALE);
    when(u.getBirthDate()).thenReturn(LocalDate.of(1990, 1, 2));
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(u));
    UserEmail e = mock(UserEmail.class);
    when(e.isPrimary()).thenReturn(true);
    when(e.getEmail()).thenReturn("ana@x.com");
    when(emailRepo.findByUserId(TARGET)).thenReturn(List.of(e));
    Unit unit = mock(Unit.class);
    when(unit.getCode()).thenReturn("101A");
    when(unitRepo.findById(unitId)).thenReturn(Optional.of(unit));

    UserDetail d = service.getUserDetail(TARGET);

    assertThat(d.fullName()).isEqualTo("Ana Lima");
    assertThat(d.email()).isEqualTo("ana@x.com");
    assertThat(d.unitCode()).isEqualTo("101A");
    assertThat(d.gender()).isEqualTo("FEMALE");
    assertThat(d.birthDate()).isEqualTo(LocalDate.of(1990, 1, 2));
  }
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest`
Expected: falha de compilação — `getUserDetail` ausente / novo mock no construtor.

- [ ] **Step 4: Implementar no service**

Em `AccessService.java`, adicionar imports:
```java
import br.com.condominio.feature.access.dto.UserDetail;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
```
Adicionar o campo final (o `@RequiredArgsConstructor` injeta):
```java
  private final UnitRepository unitRepo;
```
Adicionar o método:
```java
  @Transactional(readOnly = true)
  public UserDetail getUserDetail(UUID id) {
    User u =
        userRepo
            .findById(id)
            .orElseThrow(() -> new AccessException("USER_NOT_FOUND", "Usuário não encontrado."));
    String email =
        emailRepo.findByUserId(id).stream()
            .filter(UserEmail::isPrimary)
            .findFirst()
            .map(UserEmail::getEmail)
            .orElse(null);
    String unitCode =
        u.getUnitId() == null
            ? null
            : unitRepo.findById(u.getUnitId()).map(Unit::getCode).orElse(null);
    return new UserDetail(
        u.getId(),
        u.getFullName(),
        u.getGreetingName(),
        u.getPhone(),
        u.getUnitId(),
        unitCode,
        email,
        u.getGender() == null ? null : u.getGender().name(),
        u.getBirthDate());
  }
```

- [ ] **Step 5: Endpoint no controller**

Em `AccessController.java`, adicionar import `import br.com.condominio.feature.access.dto.UserDetail;` e o método:
```java
  @GetMapping("/users/{id}")
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public UserDetail userDetail(@PathVariable UUID id) {
    return service.getUserDetail(id);
  }
```

- [ ] **Step 6: Teste de contrato**

Em `AccessControllerWebTest.java`, adicionar `import br.com.condominio.feature.access.dto.UserDetail;` e o teste:
```java
  @Test
  void userDetail_withUserManage_returns200() throws Exception {
    when(service.getUserDetail(TARGET))
        .thenReturn(
            new UserDetail(
                TARGET, "Ana Lima", "Ana", "+5511999999999", null, null, "ana@x.com", "FEMALE", null));

    mvc.perform(get("/api/access/users/{id}", TARGET).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ana@x.com"))
        .andExpect(jsonPath("$.fullName").value("Ana Lima"));
  }

  @Test
  void userDetail_withoutUserManage_returns403() throws Exception {
    mvc.perform(get("/api/access/users/{id}", TARGET).with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isForbidden());
    verify(service, never()).getUserDetail(any());
  }
```

- [ ] **Step 7: Rodar e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest,AccessControllerWebTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/dto/UserDetail.java \
        backend/src/main/java/br/com/condominio/feature/access/AccessService.java \
        backend/src/main/java/br/com/condominio/feature/access/AccessController.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java
git commit -m "feat(access): GET /users/{id} com dados completos para edição (USER_MANAGE)"
```

---

## Task 5: `updateUser` + `PUT /users/{id}` (backend)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/access/dto/UpdateUserRequest.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessService.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java`

- [ ] **Step 1: DTO `UpdateUserRequest`**

```java
package br.com.condominio.feature.access.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

/** Edição de dados do usuário pelo admin. {@code unitId}/{@code greetingName}/{@code gender}/{@code birthDate} opcionais. */
public record UpdateUserRequest(
    @NotBlank @Size(max = 180) String fullName,
    @Size(max = 60) String greetingName,
    @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
    UUID unitId,
    @NotBlank @Email @Size(max = 180) String email,
    String gender,
    LocalDate birthDate) {}
```

- [ ] **Step 2: Testes de service que falham**

Em `AccessServiceTest.java`, adicionar `import br.com.condominio.feature.access.dto.UpdateUserRequest;` e os testes:
```java
  @Test
  void updateUser_happyPath_updatesProfileAndEmail() {
    User u = mock(User.class);
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(u));
    UserEmail primary = mock(UserEmail.class);
    when(primary.isPrimary()).thenReturn(true);
    when(primary.getEmail()).thenReturn("old@x.com");
    when(primary.getUserId()).thenReturn(TARGET);
    when(emailRepo.findByUserId(TARGET)).thenReturn(List.of(primary));
    when(emailRepo.findActiveByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());

    UpdateUserRequest req =
        new UpdateUserRequest(
            "Ana Nova", "Ana", "+5511999999999", null, "new@x.com", "FEMALE", null);
    service.updateUser(ACTOR, TARGET, req);

    verify(primary).changeEmail("new@x.com");
    verify(u)
        .updateProfile(
            eq("Ana Nova"),
            eq("Ana"),
            eq("+5511999999999"),
            eq(null),
            eq(br.com.condominio.feature.user.Gender.FEMALE),
            eq(null));
  }

  @Test
  void updateUser_emailTakenByOther_throwsConflict() {
    User u = mock(User.class);
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(u));
    UserEmail primary = mock(UserEmail.class);
    when(primary.isPrimary()).thenReturn(true);
    when(primary.getEmail()).thenReturn("old@x.com");
    when(emailRepo.findByUserId(TARGET)).thenReturn(List.of(primary));
    UserEmail other = mock(UserEmail.class);
    when(other.getUserId()).thenReturn(UUID.randomUUID());
    when(emailRepo.findActiveByEmailIgnoreCase("dup@x.com")).thenReturn(Optional.of(other));

    UpdateUserRequest req =
        new UpdateUserRequest("Ana", "Ana", "+5511999999999", null, "dup@x.com", null, null);
    assertThatThrownBy(() -> service.updateUser(ACTOR, TARGET, req))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("EMAIL_TAKEN");
    verify(u, never()).updateProfile(any(), any(), any(), any(), any(), any());
  }

  @Test
  void updateUser_sameEmail_skipsUniquenessAndKeepsEmail() {
    User u = mock(User.class);
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(u));
    UserEmail primary = mock(UserEmail.class);
    when(primary.isPrimary()).thenReturn(true);
    when(primary.getEmail()).thenReturn("ana@x.com");
    when(emailRepo.findByUserId(TARGET)).thenReturn(List.of(primary));

    UpdateUserRequest req =
        new UpdateUserRequest("Ana", "Ana", "+5511999999999", null, "ANA@x.com", null, null);
    service.updateUser(ACTOR, TARGET, req);

    verify(primary, never()).changeEmail(any());
    verify(u).updateProfile(any(), any(), any(), any(), any(), any());
  }

  @Test
  void updateUser_notFound_throws() {
    when(userRepo.findById(TARGET)).thenReturn(Optional.empty());
    UpdateUserRequest req =
        new UpdateUserRequest("Ana", "Ana", "+5511999999999", null, "ana@x.com", null, null);
    assertThatThrownBy(() -> service.updateUser(ACTOR, TARGET, req))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("USER_NOT_FOUND");
  }
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest`
Expected: falha de compilação — `updateUser` ausente.

- [ ] **Step 4: Implementar no service**

Em `AccessService.java`, adicionar `import br.com.condominio.feature.access.dto.UpdateUserRequest;` e `import br.com.condominio.feature.user.Gender;`. Adicionar o método + 2 helpers privados:
```java
  @Transactional
  public void updateUser(UUID actorId, UUID targetUserId, UpdateUserRequest req) {
    User user =
        userRepo
            .findById(targetUserId)
            .orElseThrow(() -> new AccessException("USER_NOT_FOUND", "Usuário não encontrado."));

    String newEmail = req.email().trim();
    UserEmail primary =
        emailRepo.findByUserId(targetUserId).stream()
            .filter(UserEmail::isPrimary)
            .findFirst()
            .orElse(null);
    String currentEmail = primary == null ? null : primary.getEmail();
    if (currentEmail == null || !currentEmail.equalsIgnoreCase(newEmail)) {
      emailRepo
          .findActiveByEmailIgnoreCase(newEmail)
          .ifPresent(
              e -> {
                if (!e.getUserId().equals(targetUserId)) {
                  throw new AccessException("EMAIL_TAKEN", "E-mail já cadastrado.");
                }
              });
      if (primary != null) {
        primary.changeEmail(newEmail);
      }
    }

    user.updateProfile(
        req.fullName().trim(),
        trimToNull(req.greetingName()),
        req.phone().trim(),
        req.unitId(),
        parseGender(req.gender()),
        req.birthDate());
    log.info("Admin {} atualizou usuário {}", actorId, targetUserId);
  }

  private static String trimToNull(String s) {
    return (s == null || s.isBlank()) ? null : s.trim();
  }

  private static Gender parseGender(String g) {
    if (g == null || g.isBlank()) {
      return null;
    }
    try {
      return Gender.valueOf(g);
    } catch (IllegalArgumentException e) {
      throw new AccessException("INVALID_GENDER", "Gênero inválido.");
    }
  }
```

- [ ] **Step 5: Endpoint no controller**

Em `AccessController.java`, adicionar `import br.com.condominio.feature.access.dto.UpdateUserRequest;` e o método:
```java
  @PutMapping("/users/{id}")
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ResponseEntity<Void> updateUser(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUserRequest req,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.updateUser(me.userId(), id, req);
    return ResponseEntity.noContent().build();
  }
```
(`@Valid`, `ResponseEntity`, `AuthenticatedUserPrincipal`, `@AuthenticationPrincipal` já estão importados de tasks anteriores.)

- [ ] **Step 6: Testes de contrato**

Em `AccessControllerWebTest.java`, adicionar `import br.com.condominio.feature.access.dto.UpdateUserRequest;` e `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;`. Testes:
```java
  @Test
  void updateUser_withUserManage_returns204() throws Exception {
    var body =
        new UpdateUserRequest("Ana Nova", "Ana", "+5511999999999", null, "new@x.com", "FEMALE", null);
    mvc.perform(
            put("/api/access/users/{id}", TARGET)
                .with(MockAuth.user(UID, MANAGE))
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isNoContent());
    verify(service).updateUser(eq(UID), eq(TARGET), any(UpdateUserRequest.class));
  }

  @Test
  void updateUser_withoutUserManage_returns403() throws Exception {
    var body =
        new UpdateUserRequest("Ana", "Ana", "+5511999999999", null, "new@x.com", null, null);
    mvc.perform(
            put("/api/access/users/{id}", TARGET)
                .with(MockAuth.user(UID, ASSIGN))
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isForbidden());
    verify(service, never()).updateUser(any(), any(), any());
  }

  @Test
  void updateUser_emailTaken_returns409() throws Exception {
    doThrow(new AccessException("EMAIL_TAKEN", "E-mail já cadastrado."))
        .when(service)
        .updateUser(eq(UID), eq(TARGET), any(UpdateUserRequest.class));
    var body =
        new UpdateUserRequest("Ana", "Ana", "+5511999999999", null, "dup@x.com", null, null);
    mvc.perform(
            put("/api/access/users/{id}", TARGET)
                .with(MockAuth.user(UID, MANAGE))
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }
```

- [ ] **Step 7: Rodar a suíte inteira do backend**

Run: `cd backend && ./mvnw -o test`
Expected: BUILD SUCCESS (0 failures; 4 skip de Postgres sem Docker).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/dto/UpdateUserRequest.java \
        backend/src/main/java/br/com/condominio/feature/access/AccessService.java \
        backend/src/main/java/br/com/condominio/feature/access/AccessController.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java
git commit -m "feat(access): PUT /users/{id} edita dados do usuário (USER_MANAGE, e-mail único)"
```

---

## Task 6: accessApi — getUser/updateUser (frontend)

**Files:**
- Modify: `frontend/src/features/access/api/accessApi.ts`
- Test: `frontend/src/features/access/api/accessApi.test.ts`

- [ ] **Step 1: Testes que falham**

Em `accessApi.test.ts`, adicionar `getUser, updateUser` ao import de `'./accessApi'` e os testes:
```ts
  it('getUser faz GET em /access/users/:id', async () => {
    get.mockResolvedValue({ data: { id: 'u1', fullName: 'Ana', email: 'ana@x.com' } });
    const out = await getUser('u1');
    expect(get).toHaveBeenCalledWith('/access/users/u1');
    expect(out.email).toBe('ana@x.com');
  });

  it('updateUser faz PUT em /access/users/:id com o payload', async () => {
    const put = vi.mocked(api.put);
    put.mockResolvedValue({ data: undefined });
    const payload = {
      fullName: 'Ana',
      greetingName: 'Ana',
      phone: '+5511999999999',
      unitId: null,
      email: 'ana@x.com',
      gender: 'FEMALE',
      birthDate: '1990-01-02',
    };
    await updateUser('u1', payload);
    expect(put).toHaveBeenCalledWith('/access/users/u1', payload);
  });
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/access/api/accessApi.test.ts`
Expected: FAIL — `getUser`/`updateUser` não exportados.

- [ ] **Step 3: Implementar no `accessApi.ts`**

Adicionar tipos e funções:
```ts
export interface UserDetail {
  id: string;
  fullName: string;
  greetingName: string | null;
  phone: string | null;
  unitId: string | null;
  unitCode: string | null;
  email: string | null;
  gender: string | null;
  birthDate: string | null;
}

export interface UpdateUserPayload {
  fullName: string;
  greetingName: string | null;
  phone: string;
  unitId: string | null;
  email: string;
  gender: string | null;
  birthDate: string | null;
}

export async function getUser(id: string) {
  const r = await api.get(`/access/users/${id}`);
  return r.data as UserDetail;
}

export async function updateUser(id: string, payload: UpdateUserPayload) {
  await api.put(`/access/users/${id}`, payload);
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/features/access/api/accessApi.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/access/api/accessApi.ts frontend/src/features/access/api/accessApi.test.ts
git commit -m "feat(access): accessApi getUser/updateUser para edição de dados"
```

---

## Task 7: Sidebar + página (rename, 3 botões, EditUserForm, fix flags, busca por ap)

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Modify: `frontend/src/features/access/pages/AccessManagementPage.tsx`
- Test: `frontend/src/features/access/pages/AccessManagementPage.test.tsx`

- [ ] **Step 1: Renomear o item de menu**

Em `Sidebar.tsx`, trocar o label do item `/admin/acessos`:
```tsx
  {
    to: '/admin/acessos',
    label: 'Gestão de usuários',
    icon: UserCog,
    brand: 'ink',
    requires: 'ROLE_ASSIGN',
  },
```

- [ ] **Step 2: Reescrever o teste da página**

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
  getUser: vi.fn(),
  updateUser: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { AccessManagementPage } from './AccessManagementPage';
import {
  listUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
  getCreatableRoles,
  getUser,
  updateUser,
  type UserAccessRow,
} from '../api/accessApi';
import { useAuth } from '@/features/auth/useAuth';
import { toast } from 'sonner';

const listMock = vi.mocked(listUsers);
const rolesMock = vi.mocked(listAssignableRoles);
const userRolesMock = vi.mocked(getUserRoleIds);
const assignMock = vi.mocked(assignRole);
const removeMock = vi.mocked(removeRole);
const creatableMock = vi.mocked(getCreatableRoles);
const getUserMock = vi.mocked(getUser);
const updateMock = vi.mocked(updateUser);
const authMock = vi.mocked(useAuth);

const ROLES = [{ id: 6, name: 'MURAL_EDITOR', label: 'Editor do Mural' }];

function pageOf(content: UserAccessRow[], last = true, number = 0) {
  return { content, number, totalPages: last ? number + 1 : number + 2, last };
}
function setAuth(authorities: string[]) {
  authMock.mockReturnValue({ user: { authorities } } as unknown as ReturnType<typeof useAuth>);
}

beforeEach(() => {
  vi.clearAllMocks();
  setAuth(['ROLE_ASSIGN', 'USER_MANAGE']);
  rolesMock.mockResolvedValue(ROLES);
  creatableMock.mockResolvedValue([{ id: 4, name: 'RESIDENT', label: 'Morador' }]);
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
  userRolesMock.mockResolvedValue([]);
  assignMock.mockResolvedValue(undefined);
  removeMock.mockResolvedValue(undefined);
  updateMock.mockResolvedValue(undefined);
  getUserMock.mockResolvedValue({
    id: 'u1',
    fullName: 'Ana Lima',
    greetingName: 'Ana',
    phone: '+5511988887777',
    unitId: null,
    unitCode: null,
    email: 'ana@x.com',
    gender: 'FEMALE',
    birthDate: '1990-01-02',
  });
});

describe('AccessManagementPage', () => {
  it('mostra o título "Gestão de usuários" e a lista com telefone', async () => {
    render(<AccessManagementPage />);
    expect(screen.getByRole('heading', { name: /gestão de usuários/i })).toBeInTheDocument();
    expect(await screen.findByText('Ana Lima')).toBeInTheDocument();
    expect(screen.getByText('+5511988887777')).toBeInTheDocument();
  });

  it('"Acessos" abre os toggles e marcar role chama assignRole', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /^acessos$/i }));
    await user.click(await screen.findByLabelText('Editor do Mural'));
    await waitFor(() => expect(assignMock).toHaveBeenCalledWith('u1', 6));
  });

  it('"Dados" abre o form preenchido e salvar chama updateUser', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /^dados$/i }));
    expect(await screen.findByDisplayValue('ana@x.com')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^salvar$/i }));
    await waitFor(() => expect(updateMock).toHaveBeenCalled());
    expect(updateMock.mock.calls[0][0]).toBe('u1');
  });

  it('abrir "Acessos" com erro no fetch fecha o painel e avisa', async () => {
    userRolesMock.mockRejectedValue(new Error('boom'));
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /^acessos$/i }));
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    // voltou pra lista (sem checkboxes abertos)
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /voltar à busca/i })).not.toBeInTheDocument()
    );
  });

  it('busca por ap chama listUsers com o termo', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.type(screen.getByLabelText(/buscar/i), '101');
    await waitFor(() => expect(listMock).toHaveBeenCalledWith('101', 0, expect.anything()));
  });

  it('sem USER_MANAGE esconde Dados/Excluir mas mostra Acessos', async () => {
    setAuth(['ROLE_ASSIGN']);
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    expect(screen.getByRole('button', { name: /^acessos$/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^dados$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /excluir ana lima/i })).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/access/pages/AccessManagementPage.test.tsx`
Expected: FAIL — página ainda não tem botões Acessos/Dados nem o form de dados.

- [ ] **Step 4: Reescrever a página**

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
  getUser,
  updateUser,
  type AssignableRole,
  type UserAccessRow,
} from '../api/accessApi';

function errorMessage(err: unknown, fallback: string): string {
  const maybe = err as { response?: { data?: { message?: string } } };
  return maybe?.response?.data?.message ?? fallback;
}

const PAGE_SIZE = 20;

const GENDERS = [
  { value: '', label: '—' },
  { value: 'MALE', label: 'Masculino' },
  { value: 'FEMALE', label: 'Feminino' },
  { value: 'OTHER', label: 'Outro' },
  { value: 'NOT_INFORMED', label: 'Não informado' },
];

export function AccessManagementPage() {
  const { user } = useAuth();
  const canManage = user?.authorities.includes('USER_MANAGE') ?? false;
  const canAssign = user?.authorities.includes('ROLE_ASSIGN') ?? false;

  const [roles, setRoles] = useState<AssignableRole[]>([]);
  const [query, setQuery] = useState('');
  const [rows, setRows] = useState<UserAccessRow[]>([]);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<UserAccessRow | null>(null);
  const [editing, setEditing] = useState<UserAccessRow | null>(null);
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

  const openRoles = async (u: UserAccessRow) => {
    setRoleIds(new Set());
    setSelected(u);
    try {
      setRoleIds(new Set(await getUserRoleIds(u.id)));
    } catch {
      toast.error('Erro ao carregar acessos do usuário.');
      setSelected(null);
    }
  };

  const backFromRoles = () => {
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

  const showList = !selected && !editing && !adding;

  return (
    <main className="mx-auto max-w-2xl p-4">
      <h1 className="mb-4 flex items-center gap-2 text-2xl font-heading font-semibold">
        <span
          aria-hidden="true"
          className="inline-block h-6 w-1.5 rounded-full"
          style={{ backgroundColor: 'hsl(var(--brand-ink))' }}
        />
        Gestão de usuários
      </h1>

      {showList && (
        <>
          <div className="mb-4 flex gap-2">
            <label htmlFor="user-search" className="sr-only">
              Buscar usuário por nome, e-mail ou ap
            </label>
            <input
              id="user-search"
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar por nome, e-mail ou ap"
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
                className="flex flex-wrap items-center gap-2 rounded-lg border border-border px-3 py-2"
              >
                <span className="flex min-w-0 flex-1 flex-col gap-1 text-sm">
                  <span className="flex flex-wrap items-center gap-x-2">
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
                </span>
                <span className="flex shrink-0 flex-wrap gap-1">
                  {canAssign && (
                    <Button
                      type="button"
                      variant="outline"
                      className="min-h-[44px]"
                      onClick={() => void openRoles(u)}
                    >
                      Acessos
                    </Button>
                  )}
                  {canManage && (
                    <Button
                      type="button"
                      variant="outline"
                      className="min-h-[44px]"
                      onClick={() => setEditing(u)}
                    >
                      Dados
                    </Button>
                  )}
                  {canManage &&
                    (confirmDelete === u.id ? (
                      <>
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
                      </>
                    ) : (
                      <Button
                        type="button"
                        variant="outline"
                        className="min-h-[44px]"
                        aria-label={`Excluir ${u.displayName}`}
                        onClick={() => setConfirmDelete(u.id)}
                      >
                        Excluir
                      </Button>
                    ))}
                </span>
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
            setQuery('');
            void load('', 0, false);
          }}
          onCancel={() => setAdding(false)}
        />
      )}

      {editing && (
        <EditUserForm
          userId={editing.id}
          onDone={() => {
            setEditing(null);
            void load(query, 0, false);
          }}
          onCancel={() => setEditing(null)}
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
            <Button
              type="button"
              variant="outline"
              className="min-h-[44px]"
              onClick={backFromRoles}
            >
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
        <form className="space-y-3" onSubmit={(e) => void submit(e)}>
          <Field id="nu-name" label="Nome" value={fullName} onChange={setFullName} required />
          <Field
            id="nu-email"
            label="E-mail"
            type="email"
            value={email}
            onChange={setEmail}
            required
          />
          <Field
            id="nu-phone"
            label="Telefone"
            value={phone}
            onChange={setPhone}
            required
            placeholder="+5511999999999"
          />
          <Field
            id="nu-unit"
            label="Unidade (código, opcional)"
            value={unitCode}
            onChange={setUnitCode}
          />
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
            <Button type="button" variant="outline" className="min-h-[44px]" onClick={onCancel}>
              Cancelar
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

function EditUserForm({
  userId,
  onDone,
  onCancel,
}: {
  userId: string;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [fullName, setFullName] = useState('');
  const [greetingName, setGreetingName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [unitCode, setUnitCode] = useState('');
  const [gender, setGender] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [loaded, setLoaded] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getUser(userId)
      .then((d) => {
        setFullName(d.fullName ?? '');
        setGreetingName(d.greetingName ?? '');
        setEmail(d.email ?? '');
        setPhone(d.phone ?? '');
        setUnitCode(d.unitCode ?? '');
        setGender(d.gender ?? '');
        setBirthDate(d.birthDate ?? '');
        setLoaded(true);
      })
      .catch(() => {
        toast.error('Erro ao carregar o usuário.');
        onCancel();
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
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
      await updateUser(userId, {
        fullName: fullName.trim(),
        greetingName: greetingName.trim() || null,
        phone: phone.trim(),
        unitId,
        email: email.trim(),
        gender: gender || null,
        birthDate: birthDate || null,
      });
      toast.success('Dados atualizados.');
      onDone();
    } catch (err) {
      toast.error(errorMessage(err, 'Falha ao atualizar os dados.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Editar dados</CardTitle>
      </CardHeader>
      <CardContent>
        {!loaded ? (
          <p className="text-sm text-muted-foreground">Carregando…</p>
        ) : (
          <form className="space-y-3" onSubmit={(e) => void submit(e)}>
            <Field id="eu-name" label="Nome" value={fullName} onChange={setFullName} required />
            <Field
              id="eu-greeting"
              label="Como chamar (opcional)"
              value={greetingName}
              onChange={setGreetingName}
            />
            <Field
              id="eu-email"
              label="E-mail"
              type="email"
              value={email}
              onChange={setEmail}
              required
            />
            <Field
              id="eu-phone"
              label="Telefone"
              value={phone}
              onChange={setPhone}
              required
              placeholder="+5511999999999"
            />
            <Field
              id="eu-unit"
              label="Unidade (código, opcional)"
              value={unitCode}
              onChange={setUnitCode}
            />
            <div className="space-y-1">
              <label htmlFor="eu-gender" className="text-sm font-medium">
                Gênero
              </label>
              <select
                id="eu-gender"
                value={gender}
                onChange={(e) => setGender(e.target.value)}
                className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
              >
                {GENDERS.map((g) => (
                  <option key={g.value} value={g.value}>
                    {g.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <label htmlFor="eu-birth" className="text-sm font-medium">
                Data de nascimento
              </label>
              <input
                id="eu-birth"
                type="date"
                value={birthDate}
                onChange={(e) => setBirthDate(e.target.value)}
                className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
              />
            </div>
            <div className="flex gap-2">
              <Button type="submit" className="min-h-[44px]" disabled={saving}>
                Salvar
              </Button>
              <Button type="button" variant="outline" className="min-h-[44px]" onClick={onCancel}>
                Cancelar
              </Button>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  );
}

function Field({
  id,
  label,
  value,
  onChange,
  type = 'text',
  required = false,
  placeholder,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  required?: boolean;
  placeholder?: string;
}) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        type={type}
        required={required}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
      />
    </div>
  );
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/features/access/pages/AccessManagementPage.test.tsx`
Expected: PASS (6 testes).

- [ ] **Step 6: Typecheck + Sidebar test**

Run: `cd frontend && npx tsc --noEmit && npx vitest run src/components/layout/Sidebar.test.tsx`
Expected: tsc exit 0; Sidebar test — se algum caso fixa o label antigo "Gerenciar acessos", atualizar para "Gestão de usuários" (mesmo arquivo) e re-rodar até verde.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/layout/Sidebar.tsx \
        frontend/src/features/access/pages/AccessManagementPage.tsx \
        frontend/src/features/access/pages/AccessManagementPage.test.tsx
git commit -m "feat(access): Gestão de usuários — editar dados, 3 botões por linha, fix flags, busca por ap"
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
git merge --no-ff feat/gestao-usuarios-v2 -m "Merge branch 'feat/gestao-usuarios-v2'"
git push origin main
```

- [ ] **Step 4: Redeploy HML + validar**

Disparar deploy dos apps HML via Dokploy (`backend-hml` `LS9cOzFIeHV3ikQ-Dv9aK`, `frontend-hml` `vlvpO2U7y51r-zq2q1vuM`); aguardar `done` + readiness 200 + bundle novo. **Service worker:** unregister+clear+reload antes de validar (ver [[hml-frontend-pwa-cache]]). Validar em `/admin/acessos`: título "Gestão de usuários", busca por ap, botão "Acessos" abre roles e o toggle de Editor do Mural funciona (token agora renova em 401), botão "Dados" abre o form preenchido e salva, e trocar de usuário reseta as checkboxes.

---

## Self-Review

- **Cobertura do spec:** fix 401 (Task 1) ✓; busca por ap (Task 2) ✓; domínio updateProfile/changeEmail (Task 3) ✓; GET detail (Task 4) ✓; PUT update + e-mail único + rename de erro (Task 5) ✓; accessApi getUser/updateUser (Task 6) ✓; rename, 3 botões com gating, EditUserForm, fix flags, placeholder de ap (Task 7) ✓; testes back (security/repo/service/web) e front (api/página/sidebar) ✓; sem migração ✓.
- **Sem placeholders:** todos os steps têm código/comando completos.
- **Consistência de tipos:** `UserDetail` e `UpdateUserRequest` idênticos entre back (records) e front (interfaces); `updateUser(actorId, targetUserId, req)` ↔ `PUT /users/{id}` ↔ `updateUser(id, payload)`; `getUserDetail`/`getUser` no path `/access/users/{id}`; gating `USER_MANAGE` (GET detail/PUT) e `ROLE_ASSIGN` (Acessos) consistentes entre controller, página e testes; `Gender` enum (MALE/FEMALE/OTHER/NOT_INFORMED) consistente entre back e o select do front.
```
