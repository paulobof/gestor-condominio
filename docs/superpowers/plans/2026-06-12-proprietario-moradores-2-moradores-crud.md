## ⚠️ Premissas e perguntas em aberto

> Decisões que assumi para escrever este plano. **Confirmar com o Paulo antes de executar** — cada uma muda o escopo/contrato.

1. **Escopo = unidade ÚNICA do morador master.** Embora o spec (2026-06-12) descreva multi-unidade via `UnitOwnership`, o briefing fixou que multi-unidade é **futuro**. Este plano escopa o CRUD em **`User.unitId` do master** (a unidade onde ele mora e é master). **Não** consulto `UnitOwnership` para resolver "minhas unidades". Os endpoints/contratos ficam **single-unit** (sem `unitId` no body do POST, sem agrupamento por unidade na listagem). **Confirmar:** ok manter single-unit e adiar o agrupamento por unidade para o PR multi-unidade?

2. **Saneamento mantém os mesmos endpoints, exceto a troca de `PUT /{id}/disable` por `DELETE /{id}`.** O spec §4.1 pede `DELETE` (soft delete que libera e-mail). Hoje existe `PUT /{id}/disable` (apenas muda status para DISABLED, **não** libera e-mail). Vou **substituir** `disable` por `DELETE` (soft delete de `User` + `UserEmail`, como o admin faz). **Confirmar:** pode remover o endpoint `PUT /{id}/disable` (quebra de contrato; nenhum frontend de produção o consome ainda — a tela é PR4)?

3. **Senha provisória gerada e mostrada 1x** (igual ao admin). O `CreateUnitMemberRequest` atual recebe `password` digitada pelo master; o spec §4.1/§9 manda **gerar** e mostrar uma vez. Vou **remover** o campo `password` do request e retornar `CreatedUnitMemberResponse(id, fullName, password)`. **Confirmar:** ok quebrar o contrato do POST (remover `password` do body, mudar o tipo de retorno)?

4. **`UserProvisioning` é um helper de domínio/serviço compartilhado, não um @Service transacional próprio.** Extraio a mecânica comum (criar `User` ACTIVE + `UserEmail` primário único; editar perfil+e-mail com flush→`EMAIL_TAKEN`; soft delete que libera e-mail) para `feature/user/UserProvisioning.java`, injetado em `AccessService` **e** `UnitMemberService`. **`AccessService` é refatorado para delegar** a ele (sem mudança de comportamento; testes existentes do `AccessService` continuam verdes). **Confirmar:** ok tocar no `AccessService` neste PR (aumenta o diff), ou prefere extrair o helper só para o `UnitMemberService` e refatorar o `AccessService` num PR separado?

5. **Erros do morador reusam `AccessException`** (já mapeada no `GlobalExceptionHandler`: `EMAIL_TAKEN`→409, `USER_NOT_FOUND`→404, `USER_NOT_ACTIVE`→422). Acrescento o code **`MEMBER_NOT_IN_UNIT`** (alvo fora da minha unidade ou é master) → mapeado para **403 Forbidden**. **Confirmar:** 403 é o status certo para "morador não pertence à sua unidade" (alternativa: 404 para não vazar existência)? Assumi 403 por consistência com a checagem de escopo.

6. **Edição de e-mail NÃO dispara verificação/notificação.** Igual ao admin (`AccessService.updateUser`): troca o e-mail primário e força unicidade no flush. Sem e-mail de confirmação (projeto não envia e-mail) e **sem** WhatsApp. **Confirmar:** trocar o e-mail de login de um morador silenciosamente é aceitável, ou deve notificar o morador via WhatsApp?

7. **Sem limite de moradores por unidade** neste PR. Não há regra de negócio de teto. **Confirmar:** existe limite (ex.: N moradores por unidade)? Se sim, é fácil adicionar um guard no `createMember`.

8. **Master só age sobre morador `RESIDENT` não-master da sua unidade.** O guard de escopo verifica: alvo existe, `target.unitId == me.unitId`, `!target.isUnitMaster()`, `target.status == ACTIVE`. Não verifico a role `RESIDENT` explicitamente (qualquer não-master da unidade que não seja o próprio master). **Confirmar:** suficiente, ou exigir que o alvo tenha exatamente a role RESIDENT?

9. **Frontend é o PR4 (plano separado).** Este plano é **backend-only**. Nada de React/tela aqui.

10. **`PUT /{id}` permite trocar `unitId`? NÃO.** No spec do admin o `unitId` é editável, mas o master só gere a própria unidade — permitir mover um morador para outra unidade vazaria escopo. O `PUT` do master edita nome/greeting/telefone/e-mail/gênero/nascimento e **mantém** `unitId` fixo na unidade do master. **Confirmar.**

---

# Proprietário/Moradores — Plano 2 de 4: CRUD de moradores pelo morador master (backend)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sanear o `UnitMemberService`/`UnitMemberController` e trocar a autorização ad-hoc (`if(master.isUnitMaster())`) por `@PreAuthorize("hasAuthority('RESIDENT_MANAGE')")` + checagem de escopo "só a minha unidade" no service. Extrair a mecânica comum de provisionamento de usuário (`UserProvisioning`) usada por `AccessService` (admin) e `UnitMemberService` (master). Entregar os 4 endpoints escopados: listar, criar (senha provisória 1x), editar e remover (soft delete que libera e-mail). Cobre a **seção 4** do spec.

**Architecture:** Helper `UserProvisioning` (POJO `@Component`, sem `@Transactional` próprio — herda a transação do service chamador) concentra: criar `User` ACTIVE + `UserEmail` primário único (com senha provisória), editar perfil + e-mail (flush→`EMAIL_TAKEN`), soft delete (libera e-mail). `AccessService` passa a delegar a ele (comportamento idêntico; testes existentes continuam verdes). `UnitMemberService` é reescrito sem reflection e sem `findAll()`: lista por `findByUnitIdAndStatusNotAndIsUnitMasterFalse` escopado, cria via `UserProvisioning` + role RESIDENT, edita e remove com guard de escopo (`MEMBER_NOT_IN_UNIT`). `UnitMemberController` perde o `requireMaster` e ganha `@PreAuthorize("hasAuthority('RESIDENT_MANAGE')")` por método; troca `PUT /{id}/disable` por `PUT /{id}` (editar) e `DELETE /{id}` (remover).

**Tech Stack:** Spring Boot 3 (JPA/Hibernate 6, Spring Security method security já habilitado em `SecurityConfig` via `@EnableMethodSecurity`), JUnit 5, Mockito, AssertJ, MockMvc (`@WebMvcTest`). Convenções do projeto: domínio rico (sem reflection), `@Transactional` só em service, `@PreAuthorize` por permission (nunca `hasRole`), soft delete via `@SQLDelete`, senha provisória gerada (`ProvisionalPasswordGenerator`).

**Spec:** `docs/superpowers/specs/2026-06-12-proprietario-moradores-multiunidade-design.md` (seção 4 — ler como **single-unit**, ver Premissas acima).

---

## File Structure

**Backend (criar):**
- `feature/user/UserProvisioning.java` — helper compartilhado (criar/editar/soft delete de usuário + e-mail primário).
- `feature/user/dto/CreatedUnitMemberResponse.java` — resposta do POST com senha provisória mostrada 1x.
- `feature/user/dto/UpdateUnitMemberRequest.java` — body do PUT (sem senha, sem unitId).
- `feature/user/UnitMemberException.java` — erro de escopo (`MEMBER_NOT_IN_UNIT`) já mapeado no handler.

**Backend (modificar):**
- `feature/user/UnitMemberService.java` — reescrito: sem reflection, sem `findAll()`, escopo por `unitId`, delega a `UserProvisioning`.
- `feature/user/UnitMemberController.java` — `@PreAuthorize('RESIDENT_MANAGE')`; `PUT /{id}` (editar) + `DELETE /{id}` (remover) no lugar de `PUT /{id}/disable`.
- `feature/user/dto/CreateUnitMemberRequest.java` — remove `password`.
- `feature/access/AccessService.java` — delega criar/editar/soft-delete a `UserProvisioning` (sem mudança de comportamento).
- `feature/user/UserRepository.java` — finder escopado `findByUnitIdAndStatusNotAndIsUnitMasterFalse`.
- `shared/exception/GlobalExceptionHandler.java` — handler de `UnitMemberException` → 403.

**Backend (testes):**
- `feature/user/UserProvisioningTest.java` — unit (Mockito) da mecânica comum.
- `feature/user/UnitMemberServiceTest.java` — unit (Mockito) do escopo + CRUD.
- `feature/user/UnitMemberControllerWebTest.java` — reescrito: contrato HTTP com `RESIDENT_MANAGE`.

---

## Task 1: Finder escopado em `UserRepository`

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/user/UserRepository.java`

- [ ] **Step 1: Adicionar o finder derivado**

Em `UserRepository.java`, adicionar o método (lista moradores de uma unidade, excluindo masters e ignorando um status — usaremos `ANONYMIZED` para não retornar anonimizados; soft-deleted já é filtrado pelo `@SQLRestriction`):

```java
  /**
   * Moradores de uma unidade: não-master, em qualquer status exceto o informado (ex.: ANONYMIZED).
   * Soft-deleted já é filtrado pelo {@code @SQLRestriction} da entidade {@code User}.
   */
  List<User> findByUnitIdAndStatusNotAndIsUnitMasterFalse(UUID unitId, UserStatus status);
```

(`List` e `UUID` já estão importados em `UserRepository`.)

- [ ] **Step 2: Compila**

Run: `cd backend && ./mvnw -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/user/UserRepository.java
git commit -m "feat(owner): finder de moradores escopado por unitId (não-master)"
```

---

## Task 2: `UnitMemberException` + handler 403

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/user/UnitMemberException.java`
- Modify: `backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Criar a exceção**

Criar `backend/src/main/java/br/com/condominio/feature/user/UnitMemberException.java`:

```java
package br.com.condominio.feature.user;

/**
 * Erros da gestão de moradores pelo master. Mapeados em {@code GlobalExceptionHandler}:
 * {@code MEMBER_NOT_IN_UNIT} → 403 (alvo fora da unidade do master ou é master); demais reusam
 * {@code AccessException} (EMAIL_TAKEN → 409, etc.).
 */
public class UnitMemberException extends RuntimeException {

  private final String code;

  public UnitMemberException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
```

- [ ] **Step 2: Adicionar o handler**

Em `GlobalExceptionHandler.java`, adicionar o import e o handler (logo após `handleAccess`):

```java
import br.com.condominio.feature.user.UnitMemberException;
```

```java
  @ExceptionHandler(UnitMemberException.class)
  public ResponseEntity<ApiError> handleUnitMember(UnitMemberException ex) {
    HttpStatus status =
        switch (ex.getCode()) {
          case "MEMBER_NOT_IN_UNIT" -> HttpStatus.FORBIDDEN;
          case "MASTER_HAS_NO_UNIT" -> HttpStatus.UNPROCESSABLE_ENTITY;
          default -> HttpStatus.BAD_REQUEST;
        };
    return ResponseEntity.status(status)
        .body(
            ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getCode(),
                ex.getMessage(),
                requestId()));
  }
```

- [ ] **Step 3: Compila**

Run: `cd backend && ./mvnw -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/user/UnitMemberException.java \
        backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java
git commit -m "feat(owner): UnitMemberException (MEMBER_NOT_IN_UNIT 403) + handler"
```

---

## Task 3: Helper `UserProvisioning` (TDD)

Concentra a mecânica de provisionamento de usuário compartilhada por admin (`AccessService`) e master (`UnitMemberService`). POJO `@Component` **sem** `@Transactional` próprio — roda dentro da transação do service chamador (que tem `@Transactional`).

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/user/UserProvisioning.java`
- Test: `backend/src/test/java/br/com/condominio/feature/user/UserProvisioningTest.java`

- [ ] **Step 1: Escrever o teste que falha**

Criar `backend/src/test/java/br/com/condominio/feature/user/UserProvisioningTest.java`:

```java
package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.access.AccessException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserProvisioningTest {

  private static final UUID TARGET = UUID.randomUUID();

  @Mock private UserRepository userRepo;
  @Mock private UserEmailRepository emailRepo;
  @Mock private PasswordEncoder encoder;
  @Mock private ProvisionalPasswordGenerator passwordGenerator;

  @InjectMocks private UserProvisioning provisioning;

  @Test
  void createActiveUser_savesUserAndPrimaryEmail_returnsProvisionalPassword() {
    when(emailRepo.findActiveByEmailIgnoreCase("ana@x.com")).thenReturn(Optional.empty());
    when(passwordGenerator.generate()).thenReturn("Abc123!xYZ09__a");
    when(encoder.encode("Abc123!xYZ09__a")).thenReturn("HASH");
    User saved = mock(User.class);
    when(saved.getId()).thenReturn(TARGET);
    when(userRepo.save(any(User.class))).thenReturn(saved);

    UserProvisioning.Provisioned out =
        provisioning.createActiveUser(
            UUID.randomUUID(), "Ana Lima", "+5511999999999", "ana@x.com");

    assertThat(out.user()).isSameAs(saved);
    assertThat(out.provisionalPassword()).isEqualTo("Abc123!xYZ09__a");
    verify(emailRepo).save(any(UserEmail.class));
  }

  @Test
  void createActiveUser_emailTaken_throwsConflict() {
    when(emailRepo.findActiveByEmailIgnoreCase("dup@x.com"))
        .thenReturn(Optional.of(mock(UserEmail.class)));

    assertThatThrownBy(
            () ->
                provisioning.createActiveUser(
                    UUID.randomUUID(), "Ana", "+5511999999999", "dup@x.com"))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("EMAIL_TAKEN");
    verify(userRepo, never()).save(any());
  }

  @Test
  void changePrimaryEmail_differentEmail_changesAndFlushes() {
    UserEmail primary = mock(UserEmail.class);
    when(primary.getEmail()).thenReturn("old@x.com");
    when(emailRepo.findPrimaryByUserId(TARGET)).thenReturn(Optional.of(primary));
    when(emailRepo.findActiveByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());

    provisioning.changePrimaryEmail(TARGET, "new@x.com");

    verify(primary).changeEmail("new@x.com");
    verify(emailRepo).flush();
  }

  @Test
  void changePrimaryEmail_sameEmailIgnoreCase_isNoOp() {
    UserEmail primary = mock(UserEmail.class);
    when(primary.getEmail()).thenReturn("ana@x.com");
    when(emailRepo.findPrimaryByUserId(TARGET)).thenReturn(Optional.of(primary));

    provisioning.changePrimaryEmail(TARGET, "ANA@x.com");

    verify(primary, never()).changeEmail(any());
  }

  @Test
  void changePrimaryEmail_takenByOther_throwsConflict() {
    UserEmail primary = mock(UserEmail.class);
    when(primary.getEmail()).thenReturn("old@x.com");
    when(emailRepo.findPrimaryByUserId(TARGET)).thenReturn(Optional.of(primary));
    UserEmail other = mock(UserEmail.class);
    when(other.getUserId()).thenReturn(UUID.randomUUID());
    when(emailRepo.findActiveByEmailIgnoreCase("dup@x.com")).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> provisioning.changePrimaryEmail(TARGET, "dup@x.com"))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("EMAIL_TAKEN");
  }

  @Test
  void changePrimaryEmail_collisionOnFlush_throwsConflict() {
    UserEmail primary = mock(UserEmail.class);
    when(primary.getEmail()).thenReturn("old@x.com");
    when(emailRepo.findPrimaryByUserId(TARGET)).thenReturn(Optional.of(primary));
    when(emailRepo.findActiveByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());
    doThrow(new DataIntegrityViolationException("dup")).when(emailRepo).flush();

    assertThatThrownBy(() -> provisioning.changePrimaryEmail(TARGET, "new@x.com"))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("EMAIL_TAKEN");
  }

  @Test
  void changePrimaryEmail_noPrimary_createsPrimary() {
    when(emailRepo.findPrimaryByUserId(TARGET)).thenReturn(Optional.empty());
    when(emailRepo.findActiveByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());

    provisioning.changePrimaryEmail(TARGET, "new@x.com");

    verify(emailRepo).save(any(UserEmail.class));
    verify(emailRepo).flush();
  }

  @Test
  void softDelete_deletesEmailsAndUser() {
    User target = mock(User.class);
    UserEmail e = mock(UserEmail.class);
    when(emailRepo.findByUserId(TARGET)).thenReturn(List.of(e));

    provisioning.softDelete(target, TARGET);

    verify(emailRepo).delete(e);
    verify(userRepo).delete(target);
  }
}
```

- [ ] **Step 2: Rodar e ver falhar (não compila — `UserProvisioning` não existe)**

Run: `cd backend && ./mvnw -o test -Dtest=UserProvisioningTest`
Expected: falha de compilação (`UserProvisioning` ausente).

- [ ] **Step 3: Implementar o helper**

Criar `backend/src/main/java/br/com/condominio/feature/user/UserProvisioning.java`:

```java
package br.com.condominio.feature.user;

import br.com.condominio.feature.access.AccessException;
import br.com.condominio.shared.security.ProvisionalPasswordGenerator;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Mecânica de provisionamento de usuário compartilhada por {@code AccessService} (admin) e {@code
 * UnitMemberService} (master): criar {@link User} ACTIVE + {@link UserEmail} primário único (senha
 * provisória), trocar o e-mail primário com unicidade (flush → EMAIL_TAKEN) e soft delete que libera
 * o e-mail. Sem {@code @Transactional}: roda dentro da transação do service chamador. Erros usam
 * {@link AccessException} ({@code EMAIL_TAKEN}), já mapeada para 409 no handler global.
 */
@Component
@RequiredArgsConstructor
public class UserProvisioning {

  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final PasswordEncoder encoder;
  private final ProvisionalPasswordGenerator passwordGenerator;

  /** Resultado de {@link #createActiveUser}: o usuário salvo e a senha provisória (mostrar 1x). */
  public record Provisioned(User user, String provisionalPassword) {

    /** Não vaza a senha em log/exceção. */
    @Override
    public String toString() {
      return "Provisioned[userId=" + (user == null ? null : user.getId()) + ", password=***]";
    }
  }

  /**
   * Cria um usuário ACTIVE (não-master) na unidade informada, com e-mail primário único e senha
   * provisória gerada (must_change_password=true). Lança {@code EMAIL_TAKEN} se o e-mail já existe.
   */
  public Provisioned createActiveUser(UUID unitId, String fullName, String phone, String email) {
    String trimmedEmail = email.trim();
    if (emailRepo.findActiveByEmailIgnoreCase(trimmedEmail).isPresent()) {
      throw new AccessException("EMAIL_TAKEN", "E-mail já cadastrado.");
    }
    String plain = passwordGenerator.generate();
    User user =
        User.newActiveByAdmin(
            unitId, fullName.trim(), phone.trim(), encoder.encode(plain), (short) 1);
    user = userRepo.save(user);
    emailRepo.save(UserEmail.primary(user.getId(), trimmedEmail));
    return new Provisioned(user, plain);
  }

  /**
   * Troca o e-mail primário do usuário, forçando a unicidade (ux_user_email_email_active) no flush.
   * No-op se o novo e-mail é igual (case-insensitive) ao atual. Cria o primário se ausente.
   */
  public void changePrimaryEmail(UUID userId, String newEmail) {
    String trimmed = newEmail.trim();
    Optional<UserEmail> primary = emailRepo.findPrimaryByUserId(userId);
    String currentEmail = primary.map(UserEmail::getEmail).orElse(null);
    if (currentEmail != null && currentEmail.equalsIgnoreCase(trimmed)) {
      return; // mesmo e-mail: nada a fazer
    }
    emailRepo
        .findActiveByEmailIgnoreCase(trimmed)
        .ifPresent(
            e -> {
              if (!e.getUserId().equals(userId)) {
                throw new AccessException("EMAIL_TAKEN", "E-mail já cadastrado.");
              }
            });
    primary.ifPresentOrElse(
        p -> p.changeEmail(trimmed), () -> emailRepo.save(UserEmail.primary(userId, trimmed)));
    try {
      emailRepo.flush();
    } catch (DataIntegrityViolationException ex) {
      throw new AccessException("EMAIL_TAKEN", "E-mail já cadastrado.");
    }
  }

  /** Soft delete do usuário: libera o e-mail (soft delete dos UserEmail) e depois o próprio User. */
  public void softDelete(User user, UUID userId) {
    emailRepo.findByUserId(userId).forEach(emailRepo::delete);
    userRepo.delete(user);
  }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=UserProvisioningTest`
Expected: PASS (8 testes).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/user/UserProvisioning.java \
        backend/src/test/java/br/com/condominio/feature/user/UserProvisioningTest.java
git commit -m "feat(user): UserProvisioning (criar/editar-email/soft-delete compartilhado)"
```

---

## Task 4: `AccessService` delega a `UserProvisioning` (sem mudança de comportamento)

Refatoração: `createUser`, `updateUser` e `deleteUser` passam a usar `UserProvisioning`. Os testes existentes (`AccessServiceTest`) são ajustados para verificar a **delegação** ao helper (os cenários de e-mail saem do `AccessServiceTest` e passam a viver no `UserProvisioningTest`).

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/access/AccessService.java`
- Modify: `backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java`

- [ ] **Step 1: Rodar os testes do AccessService como baseline (verde)**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest`
Expected: PASS (baseline antes de refatorar).

- [ ] **Step 2: Injetar `UserProvisioning`**

Em `AccessService.java`, adicionar o import:
```java
import br.com.condominio.feature.user.UserProvisioning;
```
E no bloco de campos, **adicionar**:
```java
  private final UserProvisioning provisioning;
```
> Manter `userRepo`, `emailRepo`, `encoder`, `passwordGenerator` — ainda usados por `getUserDetail`, `requireActiveUser`, `listUsers`, e a pré-checagem de e-mail no `createUser`.

- [ ] **Step 3: Reescrever `createUser` delegando ao helper**

Substituir o corpo de `createUser`:

```java
  @Transactional
  public CreatedUserResponse createUser(UUID actorId, CreateUserRequest req) {
    if (emailRepo.findActiveByEmailIgnoreCase(req.email()).isPresent()) {
      throw new AccessException("EMAIL_TAKEN", "E-mail já cadastrado.");
    }
    requireUnitExists(req.unitId());
    Set<Short> creatable = creatableRoleIds();
    for (Short rid : req.roleIds()) {
      if (!creatable.contains(rid)) {
        throw new AccessException(
            "ROLE_NOT_CREATABLE", "Perfil não pode ser atribuído no cadastro.");
      }
    }
    UserProvisioning.Provisioned provisioned =
        provisioning.createActiveUser(req.unitId(), req.fullName(), req.phone(), req.email());
    User user = provisioned.user();
    Instant now = Instant.now();
    for (Short rid : req.roleIds()) {
      userRoleRepo.save(new UserRole(new UserRoleId(user.getId(), rid), now, actorId));
      logRepo.save(RoleAssignmentLog.assign(user.getId(), rid, actorId));
    }
    log.info("Admin {} criou usuário {}", actorId, user.getId());
    return new CreatedUserResponse(
        user.getId(), user.getFullName(), provisioned.provisionalPassword());
  }
```

> Nota: a pré-checagem de e-mail é mantida porque o teste `createUser_emailTaken_throwsConflict` espera `EMAIL_TAKEN` **antes** da validação de unidade/roles. O `createActiveUser` re-checa internamente (defesa em profundidade).

- [ ] **Step 4: Reescrever `deleteUser` delegando ao helper**

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
    provisioning.softDelete(user, targetUserId);
    log.info("Admin {} excluiu (soft) usuário {}", actorId, targetUserId);
  }
```

- [ ] **Step 5: Reescrever o miolo de e-mail de `updateUser` delegando ao helper**

Substituir o bloco de e-mail (entre `requireUnitExists(req.unitId());` e `user.updateProfile(...)`) por uma única chamada:

```java
  @Transactional
  public void updateUser(UUID actorId, UUID targetUserId, UpdateUserRequest req) {
    User user = requireActiveUser(targetUserId);
    requireUnitExists(req.unitId());

    provisioning.changePrimaryEmail(targetUserId, req.email());

    user.updateProfile(
        req.fullName().trim(),
        trimToNull(req.greetingName()),
        req.phone().trim(),
        req.unitId(),
        req.gender(),
        req.birthDate());
    log.info("Admin {} atualizou usuário {}", actorId, targetUserId);
  }
```

Após editar, **verificar** se `import org.springframework.dao.DataIntegrityViolationException;` e `import java.util.Optional;` ficaram sem uso em `AccessService`; se sim, removê-los (o compilador/spotless acusará). O `import org.springframework.security.crypto.password.PasswordEncoder;` **permanece** (campo `encoder` ainda declarado).

- [ ] **Step 6: Atualizar `AccessServiceTest` para o novo colaborador**

Adicionar em `AccessServiceTest`:
```java
import br.com.condominio.feature.user.UserProvisioning;
```
```java
  @Mock private UserProvisioning provisioning;
```

Substituir `createUser_happyPath_savesUserEmailRolesAndReturnsPassword` por:
```java
  @Test
  void createUser_happyPath_savesRolesAndReturnsPassword() {
    Role resident = role((short) 4, "Morador", null, false);
    doReturn(br.com.condominio.feature.role.RoleName.RESIDENT).when(resident).getName();
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of());
    when(roleRepo.findByName(br.com.condominio.feature.role.RoleName.RESIDENT))
        .thenReturn(Optional.of(resident));
    when(emailRepo.findActiveByEmailIgnoreCase("ana@x.com")).thenReturn(Optional.empty());
    User saved = mock(User.class);
    when(saved.getId()).thenReturn(TARGET);
    when(saved.getFullName()).thenReturn("Ana Lima");
    when(provisioning.createActiveUser(null, "Ana Lima", "+5511999999999", "ana@x.com"))
        .thenReturn(new UserProvisioning.Provisioned(saved, "Abc123!xYZ09__a"));

    CreateUserRequest req =
        new CreateUserRequest("Ana Lima", "ana@x.com", "+5511999999999", null, List.of((short) 4));
    CreatedUserResponse out = service.createUser(ACTOR, req);

    assertThat(out.password()).isEqualTo("Abc123!xYZ09__a");
    assertThat(out.id()).isEqualTo(TARGET);
    verify(userRoleRepo).save(any(UserRole.class));
    verify(logRepo).save(any(RoleAssignmentLog.class));
  }
```

Substituir `deleteUser_softDeletesUserAndEmails` por:
```java
  @Test
  void deleteUser_softDeletesViaProvisioning() {
    User target = mock(User.class);
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(target));

    service.deleteUser(ACTOR, TARGET);

    verify(provisioning).softDelete(target, TARGET);
  }
```

Substituir `updateUser_happyPath_updatesProfileAndEmail` por:
```java
  @Test
  void updateUser_happyPath_delegatesEmailAndUpdatesProfile() {
    User u = activeUser();
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(u));

    UpdateUserRequest req =
        new UpdateUserRequest(
            "Ana Nova", "Ana", "+5511999999999", null, "new@x.com", Gender.FEMALE, null);
    service.updateUser(ACTOR, TARGET, req);

    verify(provisioning).changePrimaryEmail(TARGET, "new@x.com");
    verify(u)
        .updateProfile(
            eq("Ana Nova"),
            eq("Ana"),
            eq("+5511999999999"),
            eq(null),
            eq(Gender.FEMALE),
            eq(null));
  }
```

Substituir `updateUser_emailTakenByOther_throwsConflict` por (o conflito agora borbulha do helper):
```java
  @Test
  void updateUser_emailConflictBubblesUp() {
    User u = activeUser();
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(u));
    doThrow(new AccessException("EMAIL_TAKEN", "E-mail já cadastrado."))
        .when(provisioning)
        .changePrimaryEmail(TARGET, "dup@x.com");

    UpdateUserRequest req =
        new UpdateUserRequest("Ana", "Ana", "+5511999999999", null, "dup@x.com", null, null);
    assertThatThrownBy(() -> service.updateUser(ACTOR, TARGET, req))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("EMAIL_TAKEN");
    verify(u, never()).updateProfile(any(), any(), any(), any(), any(), any());
  }
```

**Remover** do `AccessServiceTest` os testes de e-mail que agora vivem em `UserProvisioningTest`:
`updateUser_sameEmail_skipsUniquenessAndKeepsEmail`, `updateUser_noPrimaryEmail_createsPrimary`, `updateUser_emailCollisionOnFlush_throwsConflict`.
**Manter** `createUser_emailTaken_throwsConflict` (pré-checagem permanece no service), `createUser_unitNotFound_throws`, `createUser_roleNotCreatable_throws`, `deleteUser_self_throwsConflict`, `deleteUser_notFound_throws`, `updateUser_userNotActive_throws`, `updateUser_unitNotFound_throws`, `updateUser_notFound_throws`, `getUserDetail_*` e todos os de `assign`/`remove`/`listUsers` (inalterados).

- [ ] **Step 7: Rodar os testes do AccessService e do helper**

Run: `cd backend && ./mvnw -o test -Dtest=AccessServiceTest,UserProvisioningTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/AccessService.java \
        backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java
git commit -m "refactor(access): AccessService delega a UserProvisioning (sem mudança de contrato)"
```

---

## Task 5: DTOs do morador (request sem senha; response com senha provisória; update)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/user/dto/CreateUnitMemberRequest.java`
- Create: `backend/src/main/java/br/com/condominio/feature/user/dto/CreatedUnitMemberResponse.java`
- Create: `backend/src/main/java/br/com/condominio/feature/user/dto/UpdateUnitMemberRequest.java`

- [ ] **Step 1: Remover `password` do `CreateUnitMemberRequest`**

Reescrever `CreateUnitMemberRequest.java`:

```java
package br.com.condominio.feature.user.dto;

import br.com.condominio.feature.user.Gender;
import br.com.condominio.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * Cadastro de morador pelo master. Senha é provisória (gerada pelo backend, mostrada 1x); não vem no
 * body. {@code gender} desserializado direto para o enum (valor inválido → 400).
 */
public record CreateUnitMemberRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Size(max = 60) String greetingName,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = ValidationPatterns.PHONE) String phone,
    Gender gender,
    LocalDate birthDate,
    boolean whatsappOptIn) {}
```

- [ ] **Step 2: Criar `CreatedUnitMemberResponse`**

Criar `backend/src/main/java/br/com/condominio/feature/user/dto/CreatedUnitMemberResponse.java`:

```java
package br.com.condominio.feature.user.dto;

import java.util.UUID;

/** Resposta do cadastro de morador: senha provisória mostrada uma única vez. */
public record CreatedUnitMemberResponse(UUID id, String fullName, String password) {

  /** Não vaza a senha em log/exceção; ela só deve trafegar no corpo da resposta. */
  @Override
  public String toString() {
    return "CreatedUnitMemberResponse[id=" + id + ", password=***]";
  }
}
```

- [ ] **Step 3: Criar `UpdateUnitMemberRequest`**

Criar `backend/src/main/java/br/com/condominio/feature/user/dto/UpdateUnitMemberRequest.java`:

```java
package br.com.condominio.feature.user.dto;

import br.com.condominio.feature.user.Gender;
import br.com.condominio.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * Edição de morador pelo master. Sem {@code unitId} (o master não move o morador para outra
 * unidade) e sem senha. {@code gender} desserializado direto para o enum (valor inválido → 400).
 */
public record UpdateUnitMemberRequest(
    @NotBlank @Size(max = 180) String fullName,
    @Size(max = 60) String greetingName,
    @NotBlank @Pattern(regexp = ValidationPatterns.PHONE) String phone,
    @NotBlank @Email @Size(max = 180) String email,
    Gender gender,
    LocalDate birthDate) {}
```

- [ ] **Step 4: Commit (compilação completa só após a Task 6)**

> O `UnitMemberService` antigo ainda referencia `req.password()` e reflection, então `./mvnw compile` falha aqui — esperado; será resolvido na Task 6. Não rodar compile completo agora.

```bash
git add backend/src/main/java/br/com/condominio/feature/user/dto/CreateUnitMemberRequest.java \
        backend/src/main/java/br/com/condominio/feature/user/dto/CreatedUnitMemberResponse.java \
        backend/src/main/java/br/com/condominio/feature/user/dto/UpdateUnitMemberRequest.java
git commit -m "feat(owner): DTOs de morador (create sem senha, response 1x, update)"
```

---

## Task 6: Reescrever `UnitMemberService` (TDD — escopo + CRUD sem reflection)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/user/UnitMemberService.java`
- Test: `backend/src/test/java/br/com/condominio/feature/user/UnitMemberServiceTest.java` (criar)

- [ ] **Step 1: Escrever o teste que falha**

Criar `backend/src/test/java/br/com/condominio/feature/user/UnitMemberServiceTest.java`:

```java
package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.user.dto.CreateUnitMemberRequest;
import br.com.condominio.feature.user.dto.CreatedUnitMemberResponse;
import br.com.condominio.feature.user.dto.UnitMemberResponse;
import br.com.condominio.feature.user.dto.UpdateUnitMemberRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnitMemberServiceTest {

  private static final UUID MASTER = UUID.randomUUID();
  private static final UUID UNIT = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();

  @Mock private UserRepository userRepo;
  @Mock private UserEmailRepository emailRepo;
  @Mock private UserRoleRepository userRoleRepo;
  @Mock private RoleRepository roleRepo;
  @Mock private UserProvisioning provisioning;

  @InjectMocks private UnitMemberService service;

  private User masterInUnit() {
    User m = mock(User.class);
    when(m.getUnitId()).thenReturn(UNIT);
    return m;
  }

  @Test
  void listMyUnitMembers_scopesToMyUnit() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = mock(User.class);
    when(member.getId()).thenReturn(MEMBER);
    when(member.getFullName()).thenReturn("Maria");
    when(member.getGreetingName()).thenReturn("Maria");
    when(member.getPhone()).thenReturn("11999998888");
    when(member.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(userRepo.findByUnitIdAndStatusNotAndIsUnitMasterFalse(UNIT, UserStatus.ANONYMIZED))
        .thenReturn(List.of(member));
    UserEmail e = mock(UserEmail.class);
    when(e.getEmail()).thenReturn("maria@x.com");
    when(emailRepo.findPrimaryByUserId(MEMBER)).thenReturn(Optional.of(e));

    List<UnitMemberResponse> out = service.listMyUnitMembers(MASTER);

    assertThat(out).hasSize(1);
    assertThat(out.get(0).email()).isEqualTo("maria@x.com");
  }

  @Test
  void listMyUnitMembers_masterWithoutUnit_returnsEmpty() {
    User master = mock(User.class);
    when(master.getUnitId()).thenReturn(null);
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));

    assertThat(service.listMyUnitMembers(MASTER)).isEmpty();
    verify(userRepo, never()).findByUnitIdAndStatusNotAndIsUnitMasterFalse(any(), any());
  }

  @Test
  void createMember_happyPath_createsResidentAndReturnsProvisionalPassword() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    Role resident = mock(Role.class);
    when(resident.getId()).thenReturn((short) 4);
    when(roleRepo.findByName(RoleName.RESIDENT)).thenReturn(Optional.of(resident));
    User saved = mock(User.class);
    when(saved.getId()).thenReturn(MEMBER);
    when(saved.getFullName()).thenReturn("Maria Silva");
    when(provisioning.createActiveUser(UNIT, "Maria Silva", "11999998888", "maria@x.com"))
        .thenReturn(new UserProvisioning.Provisioned(saved, "Abc123!xYZ09__a"));

    CreateUnitMemberRequest req =
        new CreateUnitMemberRequest(
            "Maria Silva", "Maria", "maria@x.com", "11999998888", null, null, false);
    CreatedUnitMemberResponse out = service.createMember(MASTER, req);

    assertThat(out.password()).isEqualTo("Abc123!xYZ09__a");
    assertThat(out.id()).isEqualTo(MEMBER);
    verify(userRoleRepo).save(any(UserRole.class));
  }

  @Test
  void createMember_masterWithoutUnit_throws() {
    User master = mock(User.class);
    when(master.getUnitId()).thenReturn(null);
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));

    CreateUnitMemberRequest req =
        new CreateUnitMemberRequest(
            "Maria", "Maria", "maria@x.com", "11999998888", null, null, false);
    assertThatThrownBy(() -> service.createMember(MASTER, req))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("MASTER_HAS_NO_UNIT");
    verify(provisioning, never()).createActiveUser(any(), any(), any(), any());
  }

  @Test
  void updateMember_inMyUnit_updatesProfileAndEmail() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = mock(User.class);
    when(member.getUnitId()).thenReturn(UNIT);
    when(member.isUnitMaster()).thenReturn(false);
    when(member.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(member));

    UpdateUnitMemberRequest req =
        new UpdateUnitMemberRequest(
            "Maria Nova", "Maria", "11999998888", "nova@x.com", Gender.FEMALE, null);
    service.updateMember(MASTER, MEMBER, req);

    verify(provisioning).changePrimaryEmail(MEMBER, "nova@x.com");
    verify(member)
        .updateProfile(
            eq("Maria Nova"), eq("Maria"), eq("11999998888"), eq(UNIT), eq(Gender.FEMALE), eq(null));
  }

  @Test
  void updateMember_otherUnit_throwsForbidden() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = mock(User.class);
    when(member.getUnitId()).thenReturn(UUID.randomUUID()); // outra unidade
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(member));

    UpdateUnitMemberRequest req =
        new UpdateUnitMemberRequest("X", "X", "11999998888", "x@x.com", null, null);
    assertThatThrownBy(() -> service.updateMember(MASTER, MEMBER, req))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("MEMBER_NOT_IN_UNIT");
    verify(provisioning, never()).changePrimaryEmail(any(), any());
  }

  @Test
  void deleteMember_inMyUnit_softDeletes() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = mock(User.class);
    when(member.getUnitId()).thenReturn(UNIT);
    when(member.isUnitMaster()).thenReturn(false);
    when(member.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(member));

    service.deleteMember(MASTER, MEMBER);

    verify(provisioning).softDelete(member, MEMBER);
  }

  @Test
  void deleteMember_targetIsMaster_throwsForbidden() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User other = mock(User.class);
    when(other.getUnitId()).thenReturn(UNIT);
    when(other.isUnitMaster()).thenReturn(true); // é master, não morador
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> service.deleteMember(MASTER, MEMBER))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("MEMBER_NOT_IN_UNIT");
    verify(provisioning, never()).softDelete(any(), any());
  }

  @Test
  void deleteMember_notFound_throws() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    when(userRepo.findById(MEMBER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteMember(MASTER, MEMBER))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("MEMBER_NOT_IN_UNIT");
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -o test -Dtest=UnitMemberServiceTest`
Expected: falha de compilação (`UnitMemberService` ainda na forma antiga).

- [ ] **Step 3: Reescrever o service**

Substituir o conteúdo inteiro de `backend/src/main/java/br/com/condominio/feature/user/UnitMemberService.java`:

```java
package br.com.condominio.feature.user;

import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleId;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.user.dto.CreateUnitMemberRequest;
import br.com.condominio.feature.user.dto.CreatedUnitMemberResponse;
import br.com.condominio.feature.user.dto.UnitMemberResponse;
import br.com.condominio.feature.user.dto.UpdateUnitMemberRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de moradores pelo morador master da unidade. Autorização ({@code RESIDENT_MANAGE}) é feita
 * no controller; o escopo (alvo na unidade do master, não-master) é garantido aqui. Reusa a mecânica
 * comum de provisionamento ({@link UserProvisioning}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnitMemberService {

  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final UserRoleRepository userRoleRepo;
  private final RoleRepository roleRepo;
  private final UserProvisioning provisioning;

  @Transactional(readOnly = true)
  public List<UnitMemberResponse> listMyUnitMembers(UUID masterUserId) {
    UUID unitId = requireMaster(masterUserId).getUnitId();
    if (unitId == null) {
      return List.of();
    }
    return userRepo
        .findByUnitIdAndStatusNotAndIsUnitMasterFalse(unitId, UserStatus.ANONYMIZED)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public CreatedUnitMemberResponse createMember(UUID masterUserId, CreateUnitMemberRequest req) {
    UUID unitId = requireMaster(masterUserId).getUnitId();
    if (unitId == null) {
      throw new UnitMemberException("MASTER_HAS_NO_UNIT", "Você não está vinculado a uma unidade.");
    }
    Role residentRole = roleRepo.findByName(RoleName.RESIDENT).orElseThrow();

    UserProvisioning.Provisioned provisioned =
        provisioning.createActiveUser(unitId, req.fullName(), req.phone(), req.email());
    User member = provisioned.user();
    member.updateProfile(
        req.fullName().trim(),
        trimToNull(req.greetingName()),
        req.phone().trim(),
        unitId,
        req.gender(),
        req.birthDate());
    member.setWhatsappOptIn(req.whatsappOptIn());

    userRoleRepo.save(
        new UserRole(
            new UserRoleId(member.getId(), residentRole.getId()), Instant.now(), masterUserId));

    log.info("Master {} criou morador {}", masterUserId, member.getId());
    return new CreatedUnitMemberResponse(
        member.getId(), member.getFullName(), provisioned.provisionalPassword());
  }

  @Transactional
  public void updateMember(UUID masterUserId, UUID memberId, UpdateUnitMemberRequest req) {
    UUID unitId = requireMaster(masterUserId).getUnitId();
    User member = requireMemberInUnit(memberId, unitId);

    provisioning.changePrimaryEmail(memberId, req.email());
    member.updateProfile(
        req.fullName().trim(),
        trimToNull(req.greetingName()),
        req.phone().trim(),
        unitId,
        req.gender(),
        req.birthDate());
    log.info("Master {} atualizou morador {}", masterUserId, memberId);
  }

  @Transactional
  public void deleteMember(UUID masterUserId, UUID memberId) {
    UUID unitId = requireMaster(masterUserId).getUnitId();
    User member = requireMemberInUnit(memberId, unitId);
    provisioning.softDelete(member, memberId);
    log.info("Master {} excluiu (soft) morador {}", masterUserId, memberId);
  }

  // ===== helpers de escopo =====

  private User requireMaster(UUID masterUserId) {
    return userRepo
        .findById(masterUserId)
        .orElseThrow(() -> new UnitMemberException("MEMBER_NOT_IN_UNIT", "Master não encontrado."));
  }

  /** Garante que o alvo existe, está na unidade do master, é não-master e está ACTIVE. */
  private User requireMemberInUnit(UUID memberId, UUID masterUnitId) {
    User member =
        userRepo
            .findById(memberId)
            .orElseThrow(
                () -> new UnitMemberException("MEMBER_NOT_IN_UNIT", "Morador não encontrado."));
    if (masterUnitId == null
        || !masterUnitId.equals(member.getUnitId())
        || member.isUnitMaster()) {
      throw new UnitMemberException(
          "MEMBER_NOT_IN_UNIT", "Este morador não pertence à sua unidade.");
    }
    if (member.getStatus() != UserStatus.ACTIVE) {
      throw new UnitMemberException("MEMBER_NOT_IN_UNIT", "Morador não está ativo.");
    }
    return member;
  }

  private UnitMemberResponse toResponse(User u) {
    String email = emailRepo.findPrimaryByUserId(u.getId()).map(UserEmail::getEmail).orElse(null);
    return new UnitMemberResponse(
        u.getId(), u.getFullName(), u.getGreetingName(), email, u.getPhone(), u.getStatus().name());
  }

  private static String trimToNull(String s) {
    return (s == null || s.isBlank()) ? null : s.trim();
  }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=UnitMemberServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/user/UnitMemberService.java \
        backend/src/test/java/br/com/condominio/feature/user/UnitMemberServiceTest.java
git commit -m "feat(owner): UnitMemberService escopado (sem reflection, escopo por unidade)"
```

---

## Task 7: Reescrever `UnitMemberController` (autorização por permission + TDD de contrato)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/user/UnitMemberController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/user/UnitMemberControllerWebTest.java` (reescrever)

- [ ] **Step 1: Reescrever o controller**

Substituir o conteúdo de `backend/src/main/java/br/com/condominio/feature/user/UnitMemberController.java`:

```java
package br.com.condominio.feature.user;

import br.com.condominio.feature.user.dto.CreateUnitMemberRequest;
import br.com.condominio.feature.user.dto.CreatedUnitMemberResponse;
import br.com.condominio.feature.user.dto.UnitMemberResponse;
import br.com.condominio.feature.user.dto.UpdateUnitMemberRequest;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Gestão de moradores pelo morador master da unidade. Toda a superfície exige a permission {@code
 * RESIDENT_MANAGE} (concedida aos masters); o escopo "só a minha unidade" é garantido no service.
 */
@RestController
@RequestMapping("/api/units/me/members")
@RequiredArgsConstructor
public class UnitMemberController {

  private final UnitMemberService service;

  @GetMapping
  @PreAuthorize("hasAuthority('RESIDENT_MANAGE')")
  public List<UnitMemberResponse> listMy(@AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.listMyUnitMembers(me.userId());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('RESIDENT_MANAGE')")
  public ResponseEntity<CreatedUnitMemberResponse> create(
      @Valid @RequestBody CreateUnitMemberRequest req,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createMember(me.userId(), req));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('RESIDENT_MANAGE')")
  public ResponseEntity<Void> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUnitMemberRequest req,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.updateMember(me.userId(), id, req);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('RESIDENT_MANAGE')")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.deleteMember(me.userId(), id);
    return ResponseEntity.noContent().build();
  }
}
```

- [ ] **Step 2: Reescrever o teste de contrato**

Substituir o conteúdo de `backend/src/test/java/br/com/condominio/feature/user/UnitMemberControllerWebTest.java`:

```java
package br.com.condominio.feature.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.user.dto.CreatedUnitMemberResponse;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link UnitMemberController}: toda a superfície exige a permission {@code
 * RESIDENT_MANAGE}; sem ela (morador comum) recebe 403; payload inválido → 400.
 */
@WebMvcTest(controllers = UnitMemberController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class UnitMemberControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final String MANAGE = "RESIDENT_MANAGE";

  @Autowired private MockMvc mvc;
  @MockBean private UnitMemberService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private static final String VALID_BODY =
      "{\"fullName\":\"Maria Silva\",\"greetingName\":\"Maria\",\"email\":\"maria@test.com\","
          + "\"phone\":\"11999998888\",\"whatsappOptIn\":true}";

  private static final String INVALID_PHONE_BODY =
      "{\"fullName\":\"Maria Silva\",\"greetingName\":\"Maria\",\"email\":\"maria@test.com\","
          + "\"phone\":\"abc\",\"whatsappOptIn\":true}";

  @Test
  void listMy_withPermission_returns200() throws Exception {
    when(service.listMyUnitMembers(UID)).thenReturn(List.of());
    mvc.perform(get("/api/units/me/members").with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isOk());
  }

  @Test
  void listMy_withoutPermission_returns403() throws Exception {
    mvc.perform(get("/api/units/me/members").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).listMyUnitMembers(any());
  }

  @Test
  void create_withPermission_returns201_andShowsProvisionalPassword() throws Exception {
    when(service.createMember(eq(UID), any()))
        .thenReturn(
            new CreatedUnitMemberResponse(UUID.randomUUID(), "Maria Silva", "Prov!1234abcd"));

    mvc.perform(
            post("/api/units/me/members")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.password").value("Prov!1234abcd"));
  }

  @Test
  void create_withoutPermission_returns403() throws Exception {
    mvc.perform(
            post("/api/units/me/members")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isForbidden());
    verify(service, never()).createMember(any(), any());
  }

  @Test
  void create_invalidPhone_returns400() throws Exception {
    mvc.perform(
            post("/api/units/me/members")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(INVALID_PHONE_BODY))
        .andExpect(status().isBadRequest());
    verify(service, never()).createMember(any(), any());
  }

  @Test
  void update_withPermission_returns204() throws Exception {
    UUID memberId = UUID.randomUUID();
    mvc.perform(
            put("/api/units/me/members/{id}", memberId)
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isNoContent());
    verify(service).updateMember(eq(UID), eq(memberId), any());
  }

  @Test
  void update_withoutPermission_returns403() throws Exception {
    mvc.perform(
            put("/api/units/me/members/{id}", UUID.randomUUID())
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isForbidden());
    verify(service, never()).updateMember(any(), any(), any());
  }

  @Test
  void delete_withPermission_returns204() throws Exception {
    UUID memberId = UUID.randomUUID();
    mvc.perform(delete("/api/units/me/members/{id}", memberId).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isNoContent());
    verify(service).deleteMember(UID, memberId);
  }

  @Test
  void delete_withoutPermission_returns403() throws Exception {
    mvc.perform(delete("/api/units/me/members/{id}", UUID.randomUUID()).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).deleteMember(any(), any());
  }
}
```

- [ ] **Step 3: Rodar e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=UnitMemberControllerWebTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/user/UnitMemberController.java \
        backend/src/test/java/br/com/condominio/feature/user/UnitMemberControllerWebTest.java
git commit -m "feat(owner): endpoints de moradores gated RESIDENT_MANAGE (list/create/update/delete)"
```

---

## Task 8: Suíte completa + formatação

**Files:** nenhum novo (validação cruzada).

- [ ] **Step 1: Rodar a suíte do backend + spotless**

Run: `cd backend && ./mvnw -o spotless:check test`
Expected: BUILD SUCCESS (0 failures). Garante que: (a) o saneamento do `UnitMemberService` (remoção da reflection e do `findAll()`) não quebrou nada; (b) a refatoração do `AccessService` para `UserProvisioning` manteve o contrato (testes de access verdes); (c) a troca de `PUT /{id}/disable` por `PUT/DELETE /{id}` não deixou referência pendente.

> Se o `spotless:check` falhar por formatação, rodar `cd backend && ./mvnw -o spotless:apply` e refazer o commit do arquivo afetado.

- [ ] **Step 2: Grep de sanidade — nada deve referenciar o endpoint/método antigos**

Run: `cd backend && grep -rn "disableMember\|/disable\|setField\|findAll()" src/main/java/br/com/condominio/feature/user || echo "OK: sem residuos"`
Expected: `OK: sem residuos` (ou só ocorrências não relacionadas).

- [ ] **Step 3: Commit (se houve `spotless:apply`)**

```bash
git add -A
git commit -m "chore(owner): spotless apply na gestao de moradores"
```

---

## Self-Review

- **Cobertura do spec (seção 4, lida como single-unit):**
  - §4.1 `GET /api/units/me/members` (lista moradores) ✓ — escopado por `User.unitId` do master, excluindo masters e anonimizados. *Agrupamento por unidade adiado (multi-unidade = futuro; ver Premissa 1).*
  - §4.1 `POST` (criar; **senha provisória gerada/mostrada 1x**; role RESIDENT; e-mail único) ✓ — `createMember` via `UserProvisioning.createActiveUser` + `UserRole` RESIDENT; `CreatedUnitMemberResponse` com `password`. *`unitId` no body removido (single-unit; Premissa 1/3).*
  - §4.1 `PUT /{id}` (editar nome/greeting/telefone/e-mail/gênero/nascimento) ✓ — `updateMember`; `unitId` fixo na unidade do master (Premissa 10).
  - §4.1 `DELETE /{id}` (soft delete, libera e-mail; guard não-master da minha unidade) ✓ — `deleteMember` via `UserProvisioning.softDelete`; guard `requireMemberInUnit`.
  - §4.2 saneamento: remove `setField`/reflection ✓; remove `userRepo.findAll()` ✓ (finder escopado `findByUnitIdAndStatusNotAndIsUnitMasterFalse`); autorização por permission (não `if(master.isUnitMaster())`) ✓.
  - §4.3 helper compartilhado `UserProvisioning` ✓ — usado por `AccessService` e `UnitMemberService`.
  - §4.4 admin inalterado ✓ — `AccessService` refatorado **sem** mudança de contrato (testes verdes).
- **Sem placeholders:** todo passo tem código/comando completos.
- **Consistência de tipos:**
  - `CreateUnitMemberRequest` (sem `password`, `Gender gender`) ↔ usado em `createMember` e no `VALID_BODY` do WebTest (sem `password`). ✓
  - `CreatedUnitMemberResponse(UUID,String,String)` ↔ retorno do `createMember` ↔ `jsonPath("$.password")` no WebTest. ✓
  - `UpdateUnitMemberRequest` (sem `unitId`/senha) ↔ `updateMember`. ✓
  - `UserProvisioning.Provisioned(User, String)` ↔ consumido por `AccessService.createUser` e `UnitMemberService.createMember`. ✓
  - `findByUnitIdAndStatusNotAndIsUnitMasterFalse(UUID, UserStatus)` ↔ derived query Spring Data válida (campos `unitId`, `status`, `isUnitMaster` existem em `User`). ✓
  - `UnitMemberException` codes (`MEMBER_NOT_IN_UNIT`→403, `MASTER_HAS_NO_UNIT`→422) ↔ `GlobalExceptionHandler`. ✓
  - `MockAuth.user(UID, "RESIDENT_MANAGE")` injeta a authority no token **e** no principal — casa com `@PreAuthorize("hasAuthority('RESIDENT_MANAGE')")`. ✓
- **Riscos/decisões:** quebras de contrato (POST sem `password`, troca de `disable` por `DELETE`) e o status 403 para escopo estão nas Premissas para o Paulo confirmar antes de executar. A refatoração do `AccessService` é a maior fonte de diff/risco — por isso a Task 4 roda o baseline antes e ajusta os testes movendo os cenários de e-mail para `UserProvisioningTest`.
- **PR ≤400 linhas:** o conjunto (helper + saneamento + DTOs + 4 endpoints + refatoração do AccessService + testes) provavelmente **excede 400 linhas**. **Sugestão:** se necessário, dividir em 2 PRs — (a) Tasks 1–4 (`UserProvisioning` + refatoração `AccessService` + exceção/handler/finder); (b) Tasks 5–8 (DTOs + saneamento `UnitMemberService`/controller + endpoints). As tasks já estão ordenadas para permitir esse corte.
