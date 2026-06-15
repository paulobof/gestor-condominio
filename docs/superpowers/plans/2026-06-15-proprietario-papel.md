# Proprietário — papel, cadastro e leitura-apenas — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduzir o papel `PROPRIETARIO` (read-only), desacoplar posse de mastership, e dar um cadastro público de proprietário não-residente com comprovante de propriedade.

**Architecture:** Reaproveita a feature de posse `UnitOwnership` (flag `app.feature.unitownership.enabled`, hoje off). Aprovar uma posse passa a conceder o **papel `PROPRIETARIO`** (em vez de tornar a pessoa master). Novo fluxo `register-owner` cria conta PENDENTE com papel `PROPRIETARIO`, sem residência, e abre um claim de posse aprovado pelo admin de "Pedidos de unidade".

**Tech Stack:** Spring Boot 3 (Java 17+, JPA/Hibernate 6, Flyway, JUnit 5 + MockMvc/Mockito), Postgres. Frontend React + TS + Vite, React Router, shadcn/ui, Vitest.

**Spec:** `docs/superpowers/specs/2026-06-15-proprietario-papel-cadastro-design.md`

**Fatos verificados no código (todos confirmados):**
- `RoleName` enum: `feature/role/RoleName.java`. `Role` tem colunas `id (Short)`, `name`, `label`, `max_holders`, `assignable (boolean NOT NULL)`.
- IDs de role usados: 1–5 (V6), 6 (MURAL_EDITOR), 7 (GUEST), 8 (DOCUMENT_EDITOR) → **próximo id livre = 9**.
- Última migration = `V36__whatsapp_outbox_recipient_widen.sql` → **próxima livre = V37**.
- Permissão `GENERAL_AREAS_VIEW` já existe (V29).
- `UserRole(new UserRoleId(userId, roleId), Instant.now(), assignedByUserId)`; `UserRoleRepository extends JpaRepository<UserRole, UserRoleId>` (tem `existsById`).
- `RoleRepository.findByName(RoleName)` retorna `Optional<Role>`.
- `User.approveAsMaster(approverId)` **lança** se `!isUnitMaster` → precisamos de `approveAsOwner`.
- `UnitOwnershipService` hoje injeta: `ownershipRepo, unitRepo, userRepo, permissionGrants, storage, magicBytes, props`. Falta `roleRepo, userRoleRepo` (a adicionar).
- `RegistrationService` já injeta `roleRepo, userRoleRepo, ownershipService, permissionGrants` e tem `@Value(app.feature.unitownership.enabled) boolean unitOwnershipEnabled`.
- `RegisterMasterController`: `POST /api/auth/register-master` multipart (`@ModelAttribute` + `@RequestPart("proof")`).

**Entrega em 3 PRs (≤400 linhas cada):**
- **PR1 — RBAC + desacoplamento** (Tarefas 1–4).
- **PR2 — cadastro register-owner** (Tarefa 5).
- **PR3 — frontend** (Tarefas 6–8).

**Convenções:** Conventional Commits; **sem `Co-Authored-By`** (regra do CLAUDE.md); NUNCA `--no-verify`; TDD.

---

## Estrutura de arquivos

**Backend (novos):**
- `backend/src/main/resources/db/migration/V37__role_proprietario.sql`
- `backend/src/main/java/br/com/condominio/feature/registration/dto/RegisterOwnerRequest.java`
- `backend/src/main/java/br/com/condominio/feature/registration/RegisterOwnerController.java`
- testes: `RegisterOwnerControllerWebTest.java`, ajustes em `UnitOwnershipServiceTest.java`, `UserTest.java`, `RegistrationServiceTest.java` (conforme existirem).

**Backend (modificados):**
- `feature/role/RoleName.java` (+ `PROPRIETARIO`)
- `feature/user/User.java` (+ `approveAsOwner`)
- `feature/unit/UnitOwnershipService.java` (approve decoupla; deps; rename msg)
- `feature/registration/RegistrationService.java` (remove coupling no `registerMaster`; add `registerOwner` + `setOwnerFields`)

**Frontend (novos):**
- `frontend/src/features/auth/pages/RegisterOwnerPage.tsx` (+ `.test.tsx`)

**Frontend (modificados):**
- `frontend/src/features/consent/api/consentApi.ts` (+ `registerOwner`)
- `frontend/src/router.tsx` (+ rota `/register-owner`)
- a tela onde hoje há link para `/register-master` (+ link "Sou proprietário").

---

# PR1 — RBAC + desacoplamento

## Tarefa 1: Papel `PROPRIETARIO` (enum + migration)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/role/RoleName.java`
- Create: `backend/src/main/resources/db/migration/V37__role_proprietario.sql`

- [ ] **Step 1: Adicionar o valor ao enum**

Em `RoleName.java`, adicionar `PROPRIETARIO` ao final do enum:

```java
public enum RoleName {
  MANAGER,
  COUNCIL,
  STAFF,
  RESIDENT,
  DOORMAN,
  MURAL_EDITOR,
  GUEST,
  DOCUMENT_EDITOR,
  PROPRIETARIO
}
```

- [ ] **Step 2: Criar a migration**

`V37__role_proprietario.sql`:

```sql
-- flyway:transactional=true

-- Papel Proprietário (read-only). Concedido na aprovação da posse (UnitOwnership), não pela
-- tela de acessos (assignable=false). Permissão: apenas GENERAL_AREAS_VIEW (ver portal).
INSERT INTO role (id, name, label, max_holders, assignable)
VALUES (9, 'PROPRIETARIO', 'Proprietário', NULL, false);

INSERT INTO role_permission (role_id, permission_id)
SELECT 9, id FROM permission WHERE code = 'GENERAL_AREAS_VIEW';
```

- [ ] **Step 3: Compilar (valida enum + sintaxe)**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/role/RoleName.java backend/src/main/resources/db/migration/V37__role_proprietario.sql
git commit -m "feat(proprietario): papel PROPRIETARIO read-only (enum + seed)"
```

---

## Tarefa 2: `User.approveAsOwner` (TDD)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/user/User.java`
- Test: `backend/src/test/java/br/com/condominio/feature/user/UserTest.java` (criar se não existir)

- [ ] **Step 1: Escrever o teste (falhando)**

Adicionar a `UserTest.java` (se o arquivo não existir, criar com este conteúdo; se existir, adicionar os dois métodos):

```java
package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void approveAsOwner_activatesPendingNonMaster() {
    User u = newPendingNonMaster();
    UUID approver = UUID.randomUUID();
    u.approveAsOwner(approver);
    assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(u.isUnitMaster()).isFalse();
  }

  @Test
  void approveAsOwner_whenNotPending_throws() {
    User u = newPendingNonMaster();
    u.approveAsOwner(UUID.randomUUID());
    assertThatThrownBy(() -> u.approveAsOwner(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  /** Cria um User PENDING_APPROVAL não-master via reflection (factories de produção exigem proof). */
  private User newPendingNonMaster() {
    try {
      var ctor = User.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      User u = ctor.newInstance();
      var status = User.class.getDeclaredField("status");
      status.setAccessible(true);
      status.set(u, UserStatus.PENDING_APPROVAL);
      var master = User.class.getDeclaredField("isUnitMaster");
      master.setAccessible(true);
      master.set(u, false);
      return u;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./mvnw -q -Dtest=UserTest test`
Expected: FALHA de compilação — `approveAsOwner` não existe.

- [ ] **Step 3: Implementar o método de domínio**

Em `User.java`, logo após `approveAsMaster`, adicionar:

```java
  /** Aprovar proprietário (posse verificada). Ativa a conta sem exigir mastership. */
  public void approveAsOwner(UUID approverId) {
    if (this.status != UserStatus.PENDING_APPROVAL) {
      throw new IllegalStateException(
          "User not in PENDING_APPROVAL state (current=" + this.status + ")");
    }
    this.status = UserStatus.ACTIVE;
    this.approvedByUserId = approverId;
    this.approvedAt = Instant.now();
    this.proofVerifiedAt = Instant.now();
  }
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend && ./mvnw -q -Dtest=UserTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/user/User.java backend/src/test/java/br/com/condominio/feature/user/UserTest.java
git commit -m "feat(proprietario): User.approveAsOwner ativa conta sem mastership (TDD)"
```

---

## Tarefa 3: Desacoplar `UnitOwnershipService.approve` (TDD)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/unit/UnitOwnershipService.java`
- Test: `backend/src/test/java/br/com/condominio/feature/unit/UnitOwnershipServiceTest.java`

- [ ] **Step 1: Ajustar/escrever o teste (falhando)**

Atualizar `UnitOwnershipServiceTest` para o **novo** comportamento de `approve`. Os pontos a garantir (substituir asserts antigos que esperavam `assignMaster`/`RESIDENT_MANAGE`):

```java
// Dado um claim PENDING de um usuário PENDING_APPROVAL não-master:
// approve deve: marcar APPROVED, conceder papel PROPRIETARIO (user_role), ativar a conta,
// e NÃO chamar assignMaster nem conceder RESIDENT_MANAGE.

@Test
void approve_grantsProprietarioRole_andActivates_withoutMastership() {
  UUID ownershipId = UUID.randomUUID();
  UUID approver = UUID.randomUUID();
  UUID userId = UUID.randomUUID();
  UUID unitId = UUID.randomUUID();

  UnitOwnership claim = UnitOwnership.pending(userId, unitId, "key", "f.pdf", "application/pdf");
  when(ownershipRepo.findByIdAndStatus(ownershipId, OwnershipStatus.PENDING))
      .thenReturn(Optional.of(claim));
  when(ownershipRepo.findApprovedUnitIdsByUser(userId)).thenReturn(List.of()); // 1ª posse

  Role proprietario = mock(Role.class);
  when(proprietario.getId()).thenReturn((short) 9);
  when(roleRepo.findByName(RoleName.PROPRIETARIO)).thenReturn(Optional.of(proprietario));
  when(userRoleRepo.existsById(any())).thenReturn(false);

  User user = mock(User.class);
  when(user.getStatus()).thenReturn(UserStatus.PENDING_APPROVAL);
  when(userRepo.findById(userId)).thenReturn(Optional.of(user));

  service.approve(ownershipId, approver);

  assertThat(claim.getStatus()).isEqualTo(OwnershipStatus.APPROVED);
  verify(userRoleRepo).save(any(UserRole.class));      // papel concedido
  verify(user).approveAsOwner(approver);               // ativa sem mastership
  verify(unitRepo, never()).findById(unitId);          // NÃO resolve unidade p/ assignMaster
  verifyNoInteractions(permissionGrants);              // NÃO concede RESIDENT_MANAGE
}
```

> Adapte os nomes dos mocks (`ownershipRepo`, `roleRepo`, `userRoleRepo`, etc.) aos campos já declarados no teste. Adicione `@Mock RoleRepository roleRepo;` e `@Mock UserRoleRepository userRoleRepo;` se ainda não existirem. Remova/ajuste quaisquer testes antigos que esperavam `unit.assignMaster` ou `permissionGrants.grantIfAbsent(RESIDENT_MANAGE)` no `approve`.

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./mvnw -q -Dtest=UnitOwnershipServiceTest test`
Expected: FALHA (compilação por deps novas e/ou asserts).

- [ ] **Step 3: Adicionar deps e o helper, e reescrever `approve`**

Em `UnitOwnershipService.java`:

1. Adicionar imports:
```java
import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleId;
import br.com.condominio.feature.role.UserRoleRepository;
import java.time.Instant;
```

2. Adicionar dois campos `final` (o `@RequiredArgsConstructor` os injeta):
```java
  private final RoleRepository roleRepo;
  private final UserRoleRepository userRoleRepo;
```

3. Substituir o corpo de `approve(...)` por:
```java
  @Transactional
  public void approve(UUID ownershipId, UUID approverId) {
    UnitOwnership o =
        ownershipRepo
            .findByIdAndStatus(ownershipId, OwnershipStatus.PENDING)
            .orElseThrow(
                () -> new UnitOwnershipException("CLAIM_NOT_FOUND", "Pedido não encontrado."));

    boolean firstApproved = ownershipRepo.findApprovedUnitIdsByUser(o.getUserId()).isEmpty();
    o.approve(approverId);

    // Posse != mastership: concede o papel PROPRIETARIO (read-only); NÃO atribui master
    // nem RESIDENT_MANAGE.
    grantProprietarioRole(o.getUserId(), approverId);

    if (firstApproved) {
      userRepo
          .findById(o.getUserId())
          .filter(u -> u.getStatus() == br.com.condominio.feature.user.UserStatus.PENDING_APPROVAL)
          .ifPresent(u -> u.approveAsOwner(approverId));
    }

    log.info("Ownership approved: ownershipId={} by approverId={}", ownershipId, approverId);
  }

  /** Concede o papel PROPRIETARIO ao usuário, idempotente. */
  private void grantProprietarioRole(UUID userId, UUID approverId) {
    Short roleId =
        roleRepo
            .findByName(RoleName.PROPRIETARIO)
            .orElseThrow(() -> new IllegalStateException("PROPRIETARIO role missing"))
            .getId();
    UserRoleId id = new UserRoleId(userId, roleId);
    if (!userRoleRepo.existsById(id)) {
      userRoleRepo.save(new UserRole(id, Instant.now(), approverId));
    }
  }
```

4. Em `openClaim`, renomear a mensagem do erro de unidade já possuída (semântica de proprietário):
```java
    if (ownershipRepo.existsByUnitIdAndStatus(unitId, OwnershipStatus.APPROVED)) {
      throw new UnitOwnershipException(
          "UNIT_HAS_OWNER", "Esta unidade já possui um proprietário.");
    }
```

> Remover o `import` e o uso de `PermissionCode`/`permissionGrants` **dentro do `approve`** (não no service inteiro, se forem usados em outro método — não são). Pode manter o campo `permissionGrants` se outro método usar; se ficar sem uso, remover o campo e o import para não quebrar o lint.

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend && ./mvnw -q -Dtest=UnitOwnershipServiceTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/unit/UnitOwnershipService.java backend/src/test/java/br/com/condominio/feature/unit/UnitOwnershipServiceTest.java
git commit -m "feat(proprietario): aprovar posse concede papel PROPRIETARIO (sem mastership) (TDD)"
```

---

## Tarefa 4: `registerMaster` deixa de abrir claim de posse (TDD)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/registration/RegistrationService.java`
- Test: `backend/src/test/java/br/com/condominio/feature/registration/RegistrationServiceTest.java` (se existir)

- [ ] **Step 1: Ajustar o teste (falhando ou novo)**

Garantir que `registerMaster` **não** chama `ownershipService.openClaim`. Adicionar/ajustar:

```java
@Test
void registerMaster_doesNotOpenOwnershipClaim() {
  // ... arrange igual aos outros testes de registerMaster (mock unitRepo/emailRepo/roleRepo/etc.) ...
  service.registerMaster(validMasterRequest(), proofFile(), "1.2.3.4");
  verify(ownershipService, never()).openClaim(any(), any(), any(), any(), any());
}
```

> Se `RegistrationServiceTest` não existir ou não mockar `ownershipService`, basta adicionar `@Mock UnitOwnershipService ownershipService;` ao slice e o `verify(... never())`. Se houver um teste antigo afirmando que abre claim sob a flag, remova-o.

- [ ] **Step 2: Rodar e confirmar a falha (se aplicável)**

Run: `cd backend && ./mvnw -q -Dtest=RegistrationServiceTest test`
Expected: FALHA no novo assert enquanto o acoplamento existir.

- [ ] **Step 3: Remover o acoplamento**

Em `RegistrationService.registerMaster`, **remover** o bloco:

```java
    // Sob a flag: a posse vira a fonte de verdade do master. Mantém as colunas do User (expand).
    if (unitOwnershipEnabled) {
      ownershipService.openClaim(
          user.getId(), unit.getId(), objectKey, proof.getOriginalFilename(), detectedMime);
    }
```

O `register-master` volta a ser só residência/mastership (aprovação dele continua dando `RESIDENT_MANAGE` em `RegistrationService.approve`, inalterado). O campo `unitOwnershipEnabled` continuará sendo usado em outro ponto (não é); se ficar sem uso após a Tarefa 5 (onde `registerOwner` usa a flag no controller, não no service), pode remover o campo — confirmar no fim do PR2.

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend && ./mvnw -q -Dtest=RegistrationServiceTest test`
Expected: PASS.

- [ ] **Step 5: Rodar a suíte backend inteira**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS (ajustar qualquer teste antigo de posse que ainda espere o modelo dono=master).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/registration/RegistrationService.java backend/src/test/java/br/com/condominio/feature/registration/RegistrationServiceTest.java
git commit -m "refactor(proprietario): registerMaster nao abre mais claim de posse (TDD)"
```

> **Fim do PR1.**

---

# PR2 — Cadastro `register-owner`

## Tarefa 5: DTO + controller + service `registerOwner` (TDD)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/registration/dto/RegisterOwnerRequest.java`
- Create: `backend/src/main/java/br/com/condominio/feature/registration/RegisterOwnerController.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/registration/RegistrationService.java`
- Test: `backend/src/test/java/br/com/condominio/feature/registration/RegisterOwnerControllerWebTest.java`

- [ ] **Step 1: Escrever o teste de contrato (falhando)**

`RegisterOwnerControllerWebTest.java` (espelha o estilo dos web tests do projeto; `@WebMvcTest` + flag):

```java
package br.com.condominio.feature.registration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = RegisterOwnerController.class,
    properties = "app.feature.unitownership.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class RegisterOwnerControllerWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private RegistrationService service;
  @MockBean private JwtService jwtService;

  private MockMultipartFile proof() {
    return new MockMultipartFile("proof", "matricula.pdf", "application/pdf", new byte[] {1, 2, 3});
  }

  @Test
  void registerOwner_returns202() throws Exception {
    when(service.registerOwner(any(), any(), any()))
        .thenReturn(new RegistrationStatusResponse(java.util.UUID.randomUUID(), "PENDING_APPROVAL"));
    mvc.perform(
            multipart("/api/auth/register-owner")
                .file(proof())
                .param("fullName", "Dona Ana")
                .param("greetingName", "Ana")
                .param("email", "ana@example.com")
                .param("phone", "11999990000")
                .param("unitCode", "A101")
                .param("password", "Str0ng!Pass1")
                .param("consentVersion", "v1")
                .param("whatsappOptIn", "true"))
        .andExpect(status().isAccepted());
  }

  @Test
  void registerOwner_invalidEmail_returns400() throws Exception {
    mvc.perform(
            multipart("/api/auth/register-owner")
                .file(proof())
                .param("fullName", "Dona Ana")
                .param("greetingName", "Ana")
                .param("email", "nao-email")
                .param("phone", "11999990000")
                .param("unitCode", "A101")
                .param("password", "Str0ng!Pass1")
                .param("consentVersion", "v1")
                .param("whatsappOptIn", "true"))
        .andExpect(status().isBadRequest());
  }
}
```

E o teste de flag-off (404):

```java
// RegisterOwnerFeatureFlagOffWebTest.java — mesmo @WebMvcTest com
// properties = "app.feature.unitownership.enabled=false"; o POST deve dar 404.
```

(Implementar `RegisterOwnerFeatureFlagOffWebTest` análogo ao `ParkingRentalFeatureFlagOffWebTest`, esperando `status().isNotFound()` no `multipart("/api/auth/register-owner")...`.)

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd backend && ./mvnw -q -Dtest='RegisterOwnerControllerWebTest,RegisterOwnerFeatureFlagOffWebTest' test`
Expected: FALHA de compilação — controller/DTO/método inexistentes.

- [ ] **Step 3: Criar o DTO**

`RegisterOwnerRequest.java`:

```java
package br.com.condominio.feature.registration.dto;

import br.com.condominio.shared.validation.StrongPassword;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RegisterOwnerRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Size(max = 60) String greetingName,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
    @Size(max = 20) String gender,
    LocalDate birthDate,
    @NotBlank String unitCode,
    @NotBlank @StrongPassword String password,
    @NotBlank String consentVersion,
    boolean whatsappOptIn) {}
```

- [ ] **Step 4: Criar o controller**

`RegisterOwnerController.java`:

```java
package br.com.condominio.feature.registration;

import br.com.condominio.feature.registration.dto.RegisterOwnerRequest;
import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.unitownership.enabled", havingValue = "true")
public class RegisterOwnerController {

  private final RegistrationService service;

  @PostMapping(value = "/register-owner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<RegistrationStatusResponse> registerOwner(
      @Valid @ModelAttribute RegisterOwnerRequest req,
      @RequestPart("proof") MultipartFile proof,
      HttpServletRequest request) {
    String ip = resolveClientIp(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.registerOwner(req, proof, ip));
  }

  private String resolveClientIp(HttpServletRequest request) {
    String fwd = request.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
    return request.getRemoteAddr();
  }
}
```

- [ ] **Step 5: Implementar `registerOwner` + `setOwnerFields` no service**

Em `RegistrationService.java`, adicionar (perto de `registerMaster`):

```java
  @Transactional
  public RegistrationStatusResponse registerOwner(
      RegisterOwnerRequest req, MultipartFile proof, String clientIp) {

    if (emailRepo.findActiveByEmailIgnoreCase(req.email()).isPresent()) {
      throw new RegistrationException("EMAIL_TAKEN", "Este e-mail já está cadastrado.");
    }

    String detectedMime;
    try {
      detectedMime = magicBytes.detect(proof.getInputStream());
    } catch (IOException e) {
      throw new RegistrationException("PROOF_READ_FAILED", "Falha ao ler comprovante.");
    }
    if (!magicBytes.isAcceptedForProof(detectedMime)) {
      throw new RegistrationException(
          "PROOF_TYPE_INVALID", "Tipo de comprovante inválido. Aceitamos PDF, JPG, PNG ou WEBP.");
    }

    Unit unit =
        unitRepo
            .findByCode(req.unitCode())
            .orElseThrow(() -> new RegistrationException("UNIT_NOT_FOUND", "Unidade não encontrada."));

    ConsentDocument consent =
        consentRepo
            .findByVersion(req.consentVersion())
            .orElseThrow(
                () ->
                    new RegistrationException(
                        "CONSENT_VERSION_INVALID", "Versão do termo de privacidade inválida."));

    String objectKey;
    try {
      objectKey =
          storage.upload(
              props.getBucketProofs(), proof.getInputStream(), proof.getSize(), detectedMime);
    } catch (IOException e) {
      throw new RegistrationException("PROOF_UPLOAD_FAILED", "Falha ao enviar comprovante.");
    }

    Role ownerRole =
        roleRepo
            .findByName(RoleName.PROPRIETARIO)
            .orElseThrow(() -> new IllegalStateException("PROPRIETARIO role missing"));

    User user = newInstance(User.class);
    setOwnerFields(user, req, consent, clientIp);
    user = userRepo.save(user);

    UserEmail userEmail = newInstance(UserEmail.class);
    setEmail(userEmail, user.getId(), req.email());
    emailRepo.save(userEmail);

    userRoleRepo.save(
        new UserRole(new UserRoleId(user.getId(), ownerRole.getId()), Instant.now(), null));

    // Abre o pedido de posse PENDING com o comprovante de propriedade.
    ownershipService.openClaim(
        user.getId(), unit.getId(), objectKey, proof.getOriginalFilename(), detectedMime);

    log.info(
        "Owner registered: userId={} unitCode={} ip={}", user.getId(), unit.getCode(), clientIp);
    return new RegistrationStatusResponse(user.getId(), user.getStatus().name());
  }

  private void setOwnerFields(
      User user, RegisterOwnerRequest req, ConsentDocument consent, String clientIp) {
    try {
      setField(user, "unitId", null); // proprietário não mora
      setField(user, "isUnitMaster", false);
      setField(user, "fullName", req.fullName());
      setField(user, "greetingName", req.greetingName());
      setField(user, "phone", req.phone());
      if (req.gender() != null && !req.gender().isBlank() && !"NOT_INFORMED".equals(req.gender())) {
        setField(user, "gender", Gender.valueOf(req.gender()));
      }
      setField(user, "birthDate", req.birthDate());
      setField(user, "passwordHash", encoder.encode(req.password()));
      setField(user, "passwordPepperVersion", (short) 1);
      setField(user, "mustChangePassword", false);
      setField(user, "status", UserStatus.PENDING_APPROVAL);
      setField(user, "consentDocumentVersion", consent.getVersion());
      setField(user, "consentAcceptedAt", Instant.now());
      setField(user, "consentAcceptedIp", clientIp);
      setField(user, "whatsappOptIn", req.whatsappOptIn());
      if (req.whatsappOptIn()) setField(user, "whatsappOptInAt", Instant.now());
    } catch (Exception e) {
      throw new IllegalStateException("Failed setting owner User fields", e);
    }
  }
```

Adicionar o import do DTO no topo:
```java
import br.com.condominio.feature.registration.dto.RegisterOwnerRequest;
```
(`Role`, `RoleName`, `UserRole`, `UserRoleId` já vêm de `import br.com.condominio.feature.role.*;`; `Gender`, `UserEmail`, `UserStatus` de `feature.user.*`.)

- [ ] **Step 6: Rodar os testes de contrato**

Run: `cd backend && ./mvnw -q -Dtest='RegisterOwnerControllerWebTest,RegisterOwnerFeatureFlagOffWebTest' test`
Expected: PASS.

- [ ] **Step 7: Suíte backend inteira**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/registration/ backend/src/test/java/br/com/condominio/feature/registration/RegisterOwner*WebTest.java
git commit -m "feat(proprietario): cadastro publico register-owner com comprovante de propriedade (TDD)"
```

> **Fim do PR2.**

---

# PR3 — Frontend

## Tarefa 6: API `registerOwner`

**Files:**
- Modify: `frontend/src/features/consent/api/consentApi.ts`

- [ ] **Step 1: Localizar `registerMaster` no arquivo**

Run: `cd frontend && grep -n "registerMaster" src/features/consent/api/consentApi.ts`
Expected: ver a função `registerMaster(fd: FormData)`.

- [ ] **Step 2: Adicionar `registerOwner` espelhando `registerMaster`**

Logo após a função `registerMaster`, adicionar (ajustar o nome do client `api` ao que o arquivo usa):

```ts
export async function registerOwner(fd: FormData) {
  const r = await api.post('/auth/register-owner', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}
```

> Se `registerMaster` usa outro helper/cliente (ex.: `apiClient` ou um `fetch` próprio), copiar exatamente o mesmo padrão trocando a rota para `/auth/register-owner`.

- [ ] **Step 3: Verificar tipos**

Run: `cd frontend && npm run build`
Expected: build OK.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/features/consent/api/consentApi.ts
git commit -m "feat(proprietario): api client registerOwner"
```

---

## Tarefa 7: Página `/register-owner` + rota + link (TDD)

**Files:**
- Create: `frontend/src/features/auth/pages/RegisterOwnerPage.tsx`
- Test: `frontend/src/features/auth/pages/RegisterOwnerPage.test.tsx`
- Modify: `frontend/src/router.tsx`
- Modify: a tela que linka para `/register-master`

- [ ] **Step 1: Escrever o teste (falhando)**

`RegisterOwnerPage.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => vi.fn() };
});
vi.mock('@/features/consent/api/consentApi', () => ({ registerOwner: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { RegisterOwnerPage } from './RegisterOwnerPage';

beforeEach(() => vi.clearAllMocks());

describe('RegisterOwnerPage', () => {
  it('renderiza o cadastro de proprietário com comprovante de propriedade', () => {
    render(
      <MemoryRouter>
        <RegisterOwnerPage />
      </MemoryRouter>
    );
    expect(
      screen.getByRole('heading', { name: /propriet[áa]rio/i })
    ).toBeInTheDocument();
    expect(screen.getByText(/comprovante de propriedade/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/nome completo/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd frontend && npx vitest run src/features/auth/pages/RegisterOwnerPage.test.tsx`
Expected: FAIL — módulo inexistente.

- [ ] **Step 3: Criar a página (espelha `RegisterMasterPage`, sem checagem de master)**

`RegisterOwnerPage.tsx`:

```tsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { UnitSelector } from '@/components/UnitSelector';
import { ProofUploader } from '@/components/ProofUploader';
import { ConsentBox } from '@/features/consent/ConsentBox';
import { registerOwner } from '@/features/consent/api/consentApi';
import { PasswordInput } from '@/components/ui/password-input';
import { PhoneInput } from '@/components/ui/phone-input';
import { PasswordChecklist } from '@/components/auth/PasswordChecklist';
import { isStrongPassword } from '@/features/auth/passwordPolicy';
import { parsePhone, isValidPhone } from '@/lib/phone';

export function RegisterOwnerPage() {
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [greetingName, setGreetingName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [gender, setGender] = useState('NOT_INFORMED');
  const [birthDate, setBirthDate] = useState('');
  const [unitCode, setUnitCode] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [consentVersion, setConsentVersion] = useState<string | null>(null);
  const [whatsappOptIn, setWhatsappOptIn] = useState(true);
  const [proof, setProof] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const phoneParsed = parsePhone(phone);
  const phoneValid = isValidPhone(phoneParsed.ddi, phoneParsed.national);

  // Proprietário NÃO bloqueia por "unidade já tem master" — posse é independente da residência.
  const canSubmit =
    !!fullName &&
    !!greetingName &&
    !!email &&
    phoneValid &&
    !!unitCode &&
    isStrongPassword(password) &&
    !!consentVersion &&
    !!proof;

  const submit = async () => {
    if (!canSubmit || !proof) return;
    setSubmitting(true);
    try {
      const fd = new FormData();
      fd.append('fullName', fullName);
      fd.append('greetingName', greetingName);
      fd.append('email', email);
      fd.append('phone', phone);
      fd.append('gender', gender);
      if (birthDate) fd.append('birthDate', birthDate);
      fd.append('unitCode', unitCode!);
      fd.append('password', password);
      fd.append('consentVersion', consentVersion!);
      fd.append('whatsappOptIn', whatsappOptIn ? 'true' : 'false');
      fd.append('proof', proof);
      await registerOwner(fd);
      toast.success('Cadastro enviado! Aguarde aprovação do síndico.');
      navigate('/pending-approval', { replace: true });
    } catch (e) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Erro ao cadastrar. Tente novamente.';
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="min-h-dvh flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-2xl my-8">
        <CardHeader>
          <CardTitle>Cadastro de proprietário</CardTitle>
          <p className="text-sm text-muted-foreground">
            Para donos de unidade que <strong>não moram</strong> no condomínio.
          </p>
        </CardHeader>
        <CardContent className="space-y-6">
          <section>
            <h3 className="font-semibold mb-3">1. Unidade que você possui</h3>
            <UnitSelector value={unitCode} onChange={(c) => setUnitCode(c)} />
          </section>
          <section className="space-y-3">
            <h3 className="font-semibold">2. Seus dados</h3>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor="fullName">Nome completo</Label>
                <Input id="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} />
              </div>
              <div>
                <Label htmlFor="greetingName">Como prefere ser chamado</Label>
                <Input
                  id="greetingName"
                  value={greetingName}
                  onChange={(e) => setGreetingName(e.target.value)}
                />
              </div>
              <div>
                <Label htmlFor="email">E-mail</Label>
                <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
              </div>
              <div>
                <Label htmlFor="phone">Telefone (WhatsApp)</Label>
                <PhoneInput id="phone" value={phone} onChange={setPhone} />
              </div>
              <div>
                <Label>Data de nascimento</Label>
                <Input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} />
              </div>
              <div>
                <Label>Gênero (opcional)</Label>
                <select
                  className="w-full rounded-md border border-input bg-background px-3 py-2"
                  value={gender}
                  onChange={(e) => setGender(e.target.value)}
                >
                  <option value="NOT_INFORMED">Prefiro não informar</option>
                  <option value="MALE">Masculino</option>
                  <option value="FEMALE">Feminino</option>
                  <option value="OTHER">Outro</option>
                </select>
              </div>
              <div className="col-span-2">
                <Label htmlFor="password">Senha</Label>
                <PasswordInput id="password" value={password} onChange={(e) => setPassword(e.target.value)} />
                <PasswordChecklist value={password} />
              </div>
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={whatsappOptIn}
                onChange={(e) => setWhatsappOptIn(e.target.checked)}
              />
              Aceito receber comunicações operacionais via WhatsApp neste número.
            </label>
          </section>
          <section>
            <h3 className="font-semibold mb-3">3. Comprovante de propriedade</h3>
            <ProofUploader value={proof} onChange={setProof} />
          </section>
          <section>
            <h3 className="font-semibold mb-3">4. Termo de privacidade</h3>
            <ConsentBox accepted={!!consentVersion} onChange={(a, v) => setConsentVersion(a ? v : null)} />
          </section>
          <Button onClick={submit} disabled={!canSubmit || submitting} className="w-full">
            {submitting ? 'Enviando...' : 'Enviar cadastro'}
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
```

> `UnitSelector` hoje chama `onChange(code, hasMaster)`. Aqui ignoramos o 2º argumento (a posse não depende de mastership). Se o TS reclamar da assinatura, usar `onChange={(c) => setUnitCode(c)}` (o callback aceita argumentos extras sem erro).

- [ ] **Step 4: Registrar a rota**

Em `frontend/src/router.tsx`, importar e adicionar a rota **pública** (junto de `/register-master`):

```tsx
import { RegisterOwnerPage } from '@/features/auth/pages/RegisterOwnerPage';
// ...
  { path: '/register-owner', element: <RegisterOwnerPage /> },
```

- [ ] **Step 5: Link na tela de registro**

Localizar onde há link para `/register-master`:

Run: `cd frontend && grep -rn "register-master" src --include=*.tsx | grep -i link`

No mesmo lugar, adicionar um link para `/register-owner` com o texto **"Sou proprietário (não moro no condomínio)"**. Exemplo (adaptar ao componente de link usado no arquivo — `Link` do react-router):

```tsx
<Link to="/register-owner" className="text-sm text-primary underline-offset-4 hover:underline">
  Sou proprietário (não moro no condomínio)
</Link>
```

- [ ] **Step 6: Rodar o teste da página + build**

Run: `cd frontend && npx vitest run src/features/auth/pages/RegisterOwnerPage.test.tsx && npm run build`
Expected: PASS + build OK.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/features/auth/pages/RegisterOwnerPage.tsx frontend/src/features/auth/pages/RegisterOwnerPage.test.tsx frontend/src/router.tsx
git commit -m "feat(proprietario): pagina /register-owner + rota + link (TDD)"
```

---

## Tarefa 8: Garantir experiência read-only do PROPRIETARIO (TDD leve)

**Files:**
- Verify/Modify: `frontend/src/components/layout/Sidebar.tsx`, `frontend/src/App.tsx`
- Test: `frontend/src/components/layout/Sidebar.test.tsx`

- [ ] **Step 1: Mapear ações de escrita gated por permissão**

Run: `cd frontend && grep -rn "CONTENT_CREATE\|RESIDENT_MANAGE\|authorities.includes" src/components src/features --include=*.tsx | head -30`
Expected: confirmar que "Adicionar morador"/"Moradores" (RESIDENT_MANAGE) e criar indicação/classificado (CONTENT_CREATE) já são gated por permissão.

- [ ] **Step 2: Escrever um teste de sidebar para o PROPRIETARIO**

Adicionar a `Sidebar.test.tsx` um caso: usuário com authorities apenas de leitura (sem `RESIDENT_MANAGE`, sem `ROLE_ASSIGN`) **não** vê "Moradores" nem itens administrativos. (Reusa o `renderSidebar(authorities)` já existente.)

```tsx
it('proprietário (só leitura) não vê itens de escrita/admin', () => {
  renderSidebar(['GENERAL_AREAS_VIEW']);
  expect(screen.queryByRole('link', { name: /^moradores$/i })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: /gestão de usuários/i })).not.toBeInTheDocument();
});
```

- [ ] **Step 3: Rodar; ajustar se algo de escrita vazar**

Run: `cd frontend && npx vitest run src/components/layout/Sidebar.test.tsx`
Expected: PASS. Se algum item de escrita aparecer sem `requires`, adicionar o `requires` apropriado.

- [ ] **Step 4: Suíte frontend + build**

Run: `cd frontend && npx vitest run && npm run build`
Expected: tudo verde.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/layout/Sidebar.test.tsx frontend/src/components/layout/Sidebar.tsx frontend/src/App.tsx
git commit -m "test(proprietario): garante sidebar read-only para PROPRIETARIO"
```

> **Fim do PR3.**

---

## Pós-implementação

- **Flag:** ligar `app.feature.unitownership.enabled=true` no env do backend HML (Dokploy) para testar register-owner + aprovação. Sem a flag, `/api/auth/register-owner` dá 404.
- **Smoke HML:** cadastrar proprietário → aprovar em "Pedidos de unidade" → logar → confirmar acesso só-leitura (sem criar conteúdo, sem "Moradores") → registrar unidade extra.

---

## Self-review (autor do plano)

- **Cobertura do spec:** §3 papel → T1; §3 leitura → T1 (GENERAL_AREAS_VIEW) + T8; §4.1 approve decoupla → T3 (+ T2 approveAsOwner); §4.2 registerMaster sem coupling → T4; §5 register-owner → T5; §6 frontend → T6–T8; §7 migrations/flag → T1 + pós; §8 testes → embutidos. ✔
- **Placeholders:** os passos de código trazem o código; pontos "localizar o link/grep" são buscas explícitas, não TBDs. ✔
- **Consistência de tipos:** `PROPRIETARIO` (role id 9), `approveAsOwner`, `grantProprietarioRole`, `registerOwner`/`setOwnerFields`, `RegisterOwnerRequest`, rota `/api/auth/register-owner` e `/register-owner`, flag `app.feature.unitownership.enabled` — usados igual em back, front e testes. ✔
- **Riscos sinalizados:** ajustar testes antigos de `UnitOwnershipService`/posse que assumiam dono=master (T3/T4); assinatura do `UnitSelector.onChange` (T7). ✔
