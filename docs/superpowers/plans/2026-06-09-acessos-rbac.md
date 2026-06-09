# Gerenciar acessos (RBAC) + role "Editor do Mural" — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que o síndico (`ROLE_ASSIGN`) atribua/remova um conjunto curado de roles a usuários por uma tela "Gerenciar acessos", incluindo a nova role "Editor do Mural" (só `ANNOUNCEMENT_MANAGE`), com validação de `max_holders` e auditoria imutável.

**Architecture:** Migrations expand/contract adicionam `role.assignable`, a role `MURAL_EDITOR` (id 6) e a tabela imutável `role_assignment_log`. Um módulo backend novo `feature/access` (atrás da flag `app.feature.accessmanagement.enabled`) expõe busca de usuário, listagem de roles geríveis e endpoints idempotentes de atribuir/remover role, todos gated por `hasAuthority('ROLE_ASSIGN')`. O frontend ganha a feature `features/access` com a página de gestão, rota e item de menu condicionados à permissão.

**Tech Stack:** Spring Boot 3 + Hibernate 6 + Flyway + PostgreSQL; React + TypeScript + Vitest + Testing Library + shadcn/ui + Tailwind; Mockito + `@WebMvcTest`.

Spec: `docs/superpowers/specs/2026-06-09-acessos-rbac-design.md`.

Convenções (CLAUDE.md): TDD (teste primeiro), SOLID/KISS, soft delete intacto, Lombok sem `@Data`, `@Transactional` só no service, autorização por permission, sem PII em log, migrations backward-compatible, Conventional Commits, hooks rodam (sem `--no-verify`). PR ≤400 linhas.

---

## File Structure

**Fase 1 — dados/migrations (PR `feat/access-rbac-schema`)**
- Create: `backend/src/main/resources/db/migration/V24__role_assignable.sql`
- Create: `backend/src/main/resources/db/migration/V25__role_mural_editor.sql`
- Create: `backend/src/main/resources/db/migration/V26__role_assignment_log.sql`
- Modify: `backend/src/main/java/br/com/condominio/feature/role/RoleName.java` (+ `MURAL_EDITOR`)
- Modify: `backend/src/main/java/br/com/condominio/feature/role/Role.java` (+ campo `assignable`)
- Modify: `backend/src/main/java/br/com/condominio/feature/role/RoleRepository.java` (+ `findByAssignableTrue`)
- Modify: `backend/src/main/java/br/com/condominio/feature/role/UserRoleRepository.java` (+ `countById_RoleId`)
- Create: `backend/src/main/java/br/com/condominio/feature/access/RoleAssignmentLog.java`
- Create: `backend/src/main/java/br/com/condominio/feature/access/RoleAssignmentLogRepository.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/RoleAssignmentLogTest.java`

**Fase 2 — backend access (PR `feat/access-rbac-api`)**
- Create: `backend/src/main/java/br/com/condominio/feature/access/dto/UserSearchResult.java`
- Create: `backend/src/main/java/br/com/condominio/feature/access/dto/AssignableRoleView.java`
- Create: `backend/src/main/java/br/com/condominio/feature/access/AccessException.java`
- Create: `backend/src/main/java/br/com/condominio/feature/access/AccessUserRepository.java`
- Create: `backend/src/main/java/br/com/condominio/feature/access/AccessService.java`
- Create: `backend/src/main/java/br/com/condominio/feature/access/AccessController.java`
- Modify: `backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java` (+ handler de `AccessException`)
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java`

**Fase 3 — frontend (PR `feat/access-rbac-ui`)**
- Create: `frontend/src/features/access/api/accessApi.ts`
- Create: `frontend/src/features/access/pages/AccessManagementPage.tsx`
- Modify: `frontend/src/router.tsx` (+ rota `/admin/acessos`)
- Modify: `frontend/src/components/layout/Sidebar.tsx` (+ item gated por `ROLE_ASSIGN`)
- Test: `frontend/src/features/access/api/accessApi.test.ts`
- Test: `frontend/src/features/access/pages/AccessManagementPage.test.tsx`
- Modify: `frontend/src/components/layout/Sidebar.test.tsx` (+ caso do novo item)

---

## Commands (referência)

- Backend, um teste: `cd backend && ./mvnw -q -Dtest=ClassName test`
- Backend, suíte: `cd backend && ./mvnw -q test`
- Frontend, um arquivo: `cd frontend && npm run test -- src/features/access/...`
- Frontend, suíte + lint: `cd frontend && npm run test && npm run lint`

> Em `win32`/PowerShell use `cd backend; ./mvnw ...` (sem `&&`). Alguns testes de integração usam Testcontainers e são **skipados** sem Docker; os testes desta plano são unit/web e não exigem Docker.

---

# FASE 1 — Dados e migrations

### Task 1: Migration `V24` — coluna `assignable` na tabela `role`

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__role_assignable.sql`

- [ ] **Step 1: Escrever a migration**

```sql
-- flyway:transactional=true

-- Marca quais roles podem ser atribuídas/removidas pela tela "Gerenciar acessos".
-- Síndico (MANAGER, cap 1) e Morador (RESIDENT, automática no cadastro) ficam fora (assignable=false).
ALTER TABLE role ADD COLUMN assignable boolean NOT NULL DEFAULT false;

UPDATE role SET assignable = true WHERE name IN ('COUNCIL', 'STAFF', 'DOORMAN');
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/db/migration/V24__role_assignable.sql
git commit -m "feat(access): migration V24 adiciona role.assignable"
```

---

### Task 2: Migration `V25` — role "Editor do Mural"

**Files:**
- Create: `backend/src/main/resources/db/migration/V25__role_mural_editor.sql`

- [ ] **Step 1: Escrever a migration**

A permission é resolvida por `code` (não pelo id 15) para não acoplar ao número.

```sql
-- flyway:transactional=true

-- Nova role "Editor do Mural": só ANNOUNCEMENT_MANAGE, gerível pela tela de acessos.
INSERT INTO role (id, name, label, max_holders, assignable)
VALUES (6, 'MURAL_EDITOR', 'Editor do Mural', NULL, true);

INSERT INTO role_permission (role_id, permission_id)
SELECT 6, id FROM permission WHERE code = 'ANNOUNCEMENT_MANAGE';
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/db/migration/V25__role_mural_editor.sql
git commit -m "feat(access): migration V25 cria role Editor do Mural"
```

---

### Task 3: Migration `V26` — tabela imutável `role_assignment_log`

**Files:**
- Create: `backend/src/main/resources/db/migration/V26__role_assignment_log.sql`

- [ ] **Step 1: Escrever a migration**

```sql
-- flyway:transactional=true

-- Log imutável de atribuição/remoção de roles (auditoria do "Gerenciar acessos").
-- Sem deleted_at: é log append-only, como sensitive_access_log. Hard delete permitido por exceção.
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

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/db/migration/V26__role_assignment_log.sql
git commit -m "feat(access): migration V26 cria role_assignment_log"
```

---

### Task 4: Enum `RoleName` ganha `MURAL_EDITOR`

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/role/RoleName.java`

- [ ] **Step 1: Adicionar o valor (mantém alinhado com o seed do id 6)**

Arquivo completo após edição:

```java
package br.com.condominio.feature.role;

public enum RoleName {
  MANAGER,
  COUNCIL,
  STAFF,
  RESIDENT,
  DOORMAN,
  MURAL_EDITOR
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/role/RoleName.java
git commit -m "feat(access): RoleName ganha MURAL_EDITOR"
```

---

### Task 5: Entidade `Role` ganha campo `assignable`

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/role/Role.java`

- [ ] **Step 1: Adicionar o campo mapeado**

Insira o campo logo após `maxHolders` (dentro da classe, antes do `}` final):

```java
  @Column(name = "assignable", nullable = false)
  private boolean assignable;
```

Resultado esperado do corpo da classe (campos):

```java
  @Id private Short id;

  @Column(name = "name", nullable = false, unique = true, length = 20)
  @Enumerated(EnumType.STRING)
  private RoleName name;

  @Column(name = "label", nullable = false, length = 40)
  private String label;

  @Column(name = "max_holders")
  private Short maxHolders;

  @Column(name = "assignable", nullable = false)
  private boolean assignable;
```

(`@Getter` da classe gera `isAssignable()`.)

- [ ] **Step 2: Compilar**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/role/Role.java
git commit -m "feat(access): Role.assignable mapeado"
```

---

### Task 6: `RoleRepository.findByAssignableTrue` e `UserRoleRepository.countById_RoleId`

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/role/RoleRepository.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/role/UserRoleRepository.java`

- [ ] **Step 1: Adicionar `findByAssignableTrue`**

Arquivo completo `RoleRepository.java`:

```java
package br.com.condominio.feature.role;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Short> {
  Optional<Role> findByName(RoleName name);

  List<Role> findByAssignableTrue();
}
```

- [ ] **Step 2: Adicionar `countById_RoleId`**

Arquivo completo `UserRoleRepository.java`:

```java
package br.com.condominio.feature.role;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
  List<UserRole> findById_UserId(UUID userId);

  long countById_RoleId(Short roleId);
}
```

- [ ] **Step 3: Compilar**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/role/RoleRepository.java backend/src/main/java/br/com/condominio/feature/role/UserRoleRepository.java
git commit -m "feat(access): repos findByAssignableTrue e countById_RoleId"
```

---

### Task 7: Entidade `RoleAssignmentLog` (TDD)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/access/RoleAssignmentLog.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/RoleAssignmentLogTest.java`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.condominio.feature.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleAssignmentLogTest {

  private static final UUID TARGET = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();

  @Test
  void assign_setsActionAndFields() {
    RoleAssignmentLog log = RoleAssignmentLog.assign(TARGET, (short) 6, ACTOR);

    assertThat(log.getAction()).isEqualTo("ASSIGN");
    assertThat(log.getTargetUserId()).isEqualTo(TARGET);
    assertThat(log.getRoleId()).isEqualTo((short) 6);
    assertThat(log.getActorUserId()).isEqualTo(ACTOR);
    assertThat(log.getCreatedAt()).isNotNull();
  }

  @Test
  void remove_setsActionRemove() {
    RoleAssignmentLog log = RoleAssignmentLog.remove(TARGET, (short) 2, ACTOR);

    assertThat(log.getAction()).isEqualTo("REMOVE");
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=RoleAssignmentLogTest test`
Expected: FAIL — compilação (classe `RoleAssignmentLog` não existe).

- [ ] **Step 3: Implementar a entidade**

```java
package br.com.condominio.feature.access;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * Log imutável (append-only) de atribuição/remoção de roles. Sem soft delete: é trilha de auditoria,
 * como {@code sensitive_access_log}. Criado só via factories {@link #assign}/{@link #remove}.
 */
@Entity
@Table(name = "role_assignment_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "action"})
public class RoleAssignmentLog {

  @Id @GeneratedValue private UUID id;

  @Column(name = "action", nullable = false, length = 10)
  private String action;

  @Column(name = "target_user_id", nullable = false)
  private UUID targetUserId;

  @Column(name = "role_id", nullable = false)
  private Short roleId;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  private RoleAssignmentLog(String action, UUID targetUserId, Short roleId, UUID actorUserId) {
    this.action = action;
    this.targetUserId = targetUserId;
    this.roleId = roleId;
    this.actorUserId = actorUserId;
    this.createdAt = Instant.now();
  }

  public static RoleAssignmentLog assign(UUID targetUserId, Short roleId, UUID actorUserId) {
    return new RoleAssignmentLog("ASSIGN", targetUserId, roleId, actorUserId);
  }

  public static RoleAssignmentLog remove(UUID targetUserId, Short roleId, UUID actorUserId) {
    return new RoleAssignmentLog("REMOVE", targetUserId, roleId, actorUserId);
  }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=RoleAssignmentLogTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/RoleAssignmentLog.java backend/src/test/java/br/com/condominio/feature/access/RoleAssignmentLogTest.java
git commit -m "feat(access): entidade RoleAssignmentLog"
```

---

### Task 8: `RoleAssignmentLogRepository`

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/access/RoleAssignmentLogRepository.java`

- [ ] **Step 1: Criar o repositório**

```java
package br.com.condominio.feature.access;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAssignmentLogRepository extends JpaRepository<RoleAssignmentLog, UUID> {}
```

- [ ] **Step 2: Compilar**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/RoleAssignmentLogRepository.java
git commit -m "feat(access): RoleAssignmentLogRepository"
```

> **Fim da Fase 1.** Abrir PR `feat/access-rbac-schema`. As migrations são validadas no boot dos testes de integração (Testcontainers).

---

# FASE 2 — Backend `feature/access`

### Task 9: DTOs `UserSearchResult` e `AssignableRoleView`

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/access/dto/UserSearchResult.java`
- Create: `backend/src/main/java/br/com/condominio/feature/access/dto/AssignableRoleView.java`

- [ ] **Step 1: `UserSearchResult`**

```java
package br.com.condominio.feature.access.dto;

import java.util.UUID;

/** Resultado de busca de usuário para a tela de acessos. {@code unitLabel} pode ser nulo. */
public record UserSearchResult(UUID id, String displayName, String unitLabel) {}
```

- [ ] **Step 2: `AssignableRoleView`**

```java
package br.com.condominio.feature.access.dto;

/** Role exibível na tela de acessos (apenas as {@code assignable}). */
public record AssignableRoleView(short id, String name, String label) {}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/dto/
git commit -m "feat(access): DTOs UserSearchResult e AssignableRoleView"
```

---

### Task 10: `AccessException`

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/access/AccessException.java`

- [ ] **Step 1: Criar a exceção (espelha `AnnouncementException`)**

```java
package br.com.condominio.feature.access;

/**
 * Erros de gestão de acessos mapeados em {@code GlobalExceptionHandler}:
 * ROLE_LIMIT_REACHED → 409; ROLE_NOT_FOUND/USER_NOT_FOUND → 404;
 * ROLE_NOT_ASSIGNABLE/USER_NOT_ACTIVE → 422; demais → 400.
 */
public class AccessException extends RuntimeException {

  private final String code;

  public AccessException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/AccessException.java
git commit -m "feat(access): AccessException"
```

---

### Task 11: `AccessUserRepository` (busca por nome/e-mail)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/access/AccessUserRepository.java`

- [ ] **Step 1: Criar a query de busca**

Usa entity join (Hibernate 6) e constructor expression; respeita soft delete automaticamente (entidades têm `@SQLRestriction`). Só `ACTIVE`. `DISTINCT` dedup quando o usuário tem mais de um e-mail. O limite (20) vem do `Pageable` passado pelo service.

```java
package br.com.condominio.feature.access;

import br.com.condominio.feature.access.dto.UserSearchResult;
import br.com.condominio.feature.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Busca de usuários restrita ao contexto de gestão de acessos. */
public interface AccessUserRepository extends Repository<User, UUID> {

  @Query(
      """
      SELECT DISTINCT new br.com.condominio.feature.access.dto.UserSearchResult(
             u.id, u.fullName, un.code)
        FROM User u
        JOIN UserEmail ue ON ue.userId = u.id
        LEFT JOIN Unit un ON un.id = u.unitId
       WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
         AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
              OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%')))
       ORDER BY u.fullName
      """)
  List<UserSearchResult> search(@Param("term") String term, Pageable pageable);
}
```

- [ ] **Step 2: Compilar**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/AccessUserRepository.java
git commit -m "feat(access): AccessUserRepository com busca por nome/e-mail"
```

---

### Task 12: `AccessService` (TDD)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/access/AccessService.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java`

- [ ] **Step 1: Escrever os testes que falham**

`Role` e `User` têm construtores protegidos — usar `mock(...)` para montá-los.

```java
package br.com.condominio.feature.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleId;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessServiceTest {

  private static final UUID ACTOR = UUID.randomUUID();
  private static final UUID TARGET = UUID.randomUUID();

  @Mock private RoleRepository roleRepo;
  @Mock private UserRoleRepository userRoleRepo;
  @Mock private RoleAssignmentLogRepository logRepo;
  @Mock private AccessUserRepository userSearchRepo;
  @Mock private UserRepository userRepo;

  @InjectMocks private AccessService service;

  // lenient(): nem todo teste usa todos os getters (ex.: role não-assignable nunca chama getLabel),
  // e o MockitoExtension é strict por padrão (UnnecessaryStubbingException).
  private Role role(short id, String label, Short maxHolders, boolean assignable) {
    Role r = mock(Role.class);
    lenient().when(r.getId()).thenReturn(id);
    lenient().when(r.getLabel()).thenReturn(label);
    lenient().when(r.getMaxHolders()).thenReturn(maxHolders);
    lenient().when(r.isAssignable()).thenReturn(assignable);
    return r;
  }

  private User activeUser() {
    User u = mock(User.class);
    when(u.getStatus()).thenReturn(UserStatus.ACTIVE);
    return u;
  }

  @Test
  void assign_happyPath_savesUserRoleAndLog() {
    when(roleRepo.findById((short) 6)).thenReturn(Optional.of(role((short) 6, "Editor do Mural", null, true)));
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(activeUser()));
    when(userRoleRepo.existsById(new UserRoleId(TARGET, (short) 6))).thenReturn(false);

    service.assign(ACTOR, TARGET, (short) 6);

    verify(userRoleRepo).save(any(UserRole.class));
    verify(logRepo).save(any(RoleAssignmentLog.class));
  }

  @Test
  void assign_alreadyHasRole_isNoOp() {
    when(roleRepo.findById((short) 6)).thenReturn(Optional.of(role((short) 6, "Editor do Mural", null, true)));
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(activeUser()));
    when(userRoleRepo.existsById(new UserRoleId(TARGET, (short) 6))).thenReturn(true);

    service.assign(ACTOR, TARGET, (short) 6);

    verify(userRoleRepo, never()).save(any());
    verify(logRepo, never()).save(any());
  }

  @Test
  void assign_atMaxHolders_throwsLimit() {
    when(roleRepo.findById((short) 2)).thenReturn(Optional.of(role((short) 2, "Conselheiro", (short) 3, true)));
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(activeUser()));
    when(userRoleRepo.existsById(new UserRoleId(TARGET, (short) 2))).thenReturn(false);
    when(userRoleRepo.countById_RoleId((short) 2)).thenReturn(3L);

    assertThatThrownBy(() -> service.assign(ACTOR, TARGET, (short) 2))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("ROLE_LIMIT_REACHED");
    verify(userRoleRepo, never()).save(any());
  }

  @Test
  void assign_roleNotAssignable_throws() {
    when(roleRepo.findById((short) 1)).thenReturn(Optional.of(role((short) 1, "Síndico", (short) 1, false)));

    assertThatThrownBy(() -> service.assign(ACTOR, TARGET, (short) 1))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("ROLE_NOT_ASSIGNABLE");
  }

  @Test
  void assign_userNotActive_throws() {
    when(roleRepo.findById((short) 6)).thenReturn(Optional.of(role((short) 6, "Editor do Mural", null, true)));
    User pending = mock(User.class);
    when(pending.getStatus()).thenReturn(UserStatus.PENDING_APPROVAL);
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.assign(ACTOR, TARGET, (short) 6))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("USER_NOT_ACTIVE");
  }

  @Test
  void remove_happyPath_deletesAndLogs() {
    when(roleRepo.findById((short) 6)).thenReturn(Optional.of(role((short) 6, "Editor do Mural", null, true)));
    when(userRoleRepo.existsById(new UserRoleId(TARGET, (short) 6))).thenReturn(true);

    service.remove(ACTOR, TARGET, (short) 6);

    verify(userRoleRepo).deleteById(new UserRoleId(TARGET, (short) 6));
    verify(logRepo).save(any(RoleAssignmentLog.class));
  }

  @Test
  void remove_notHeld_isNoOp() {
    when(roleRepo.findById((short) 6)).thenReturn(Optional.of(role((short) 6, "Editor do Mural", null, true)));
    when(userRoleRepo.existsById(new UserRoleId(TARGET, (short) 6))).thenReturn(false);

    service.remove(ACTOR, TARGET, (short) 6);

    verify(userRoleRepo, never()).deleteById(any());
    verify(logRepo, never()).save(any());
  }

  @Test
  void searchUsers_shortTerm_returnsEmpty() {
    assertThat(service.searchUsers("a")).isEmpty();
    verify(userSearchRepo, never()).search(any(), any());
  }

  @Test
  void userRoleIds_filtersToAssignable() {
    when(roleRepo.findByAssignableTrue())
        .thenReturn(List.of(role((short) 2, "Conselheiro", (short) 3, true),
                            role((short) 6, "Editor do Mural", null, true)));
    when(userRoleRepo.findById_UserId(TARGET))
        .thenReturn(List.of(
            new UserRole(new UserRoleId(TARGET, (short) 4), null, null),   // RESIDENT, não-assignable
            new UserRole(new UserRoleId(TARGET, (short) 6), null, null))); // MURAL_EDITOR, assignable

    assertThat(service.userRoleIds(TARGET)).containsExactly((short) 6);
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=AccessServiceTest test`
Expected: FAIL — classe `AccessService` não existe (compilação).

- [ ] **Step 3: Implementar o service**

```java
package br.com.condominio.feature.access;

import br.com.condominio.feature.access.dto.AssignableRoleView;
import br.com.condominio.feature.access.dto.UserSearchResult;
import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleId;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de acessos: atribuir/remover roles geríveis (assignable) a usuários, com validação de
 * {@code max_holders} e auditoria imutável. Autorização ({@code ROLE_ASSIGN}) é feita no controller.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

  private static final int SEARCH_LIMIT = 20;
  private static final int MIN_TERM = 2;

  private final RoleRepository roleRepo;
  private final UserRoleRepository userRoleRepo;
  private final RoleAssignmentLogRepository logRepo;
  private final AccessUserRepository userSearchRepo;
  private final UserRepository userRepo;

  @Transactional(readOnly = true)
  public List<AssignableRoleView> assignableRoles() {
    return roleRepo.findByAssignableTrue().stream()
        .map(r -> new AssignableRoleView(r.getId(), r.getName().name(), r.getLabel()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<UserSearchResult> searchUsers(String term) {
    if (term == null || term.trim().length() < MIN_TERM) {
      return List.of();
    }
    return userSearchRepo.search(term.trim(), PageRequest.of(0, SEARCH_LIMIT));
  }

  @Transactional(readOnly = true)
  public List<Short> userRoleIds(UUID userId) {
    Set<Short> assignable =
        roleRepo.findByAssignableTrue().stream().map(Role::getId).collect(Collectors.toSet());
    return userRoleRepo.findById_UserId(userId).stream()
        .map(ur -> ur.getId().getRoleId())
        .filter(assignable::contains)
        .toList();
  }

  @Transactional
  public void assign(UUID actorId, UUID targetUserId, short roleId) {
    Role role = requireAssignableRole(roleId);
    User target =
        userRepo
            .findById(targetUserId)
            .orElseThrow(() -> new AccessException("USER_NOT_FOUND", "Usuário não encontrado."));
    if (target.getStatus() != UserStatus.ACTIVE) {
      throw new AccessException("USER_NOT_ACTIVE", "Usuário não está ativo.");
    }
    UserRoleId id = new UserRoleId(targetUserId, roleId);
    if (userRoleRepo.existsById(id)) {
      return; // idempotente: já tem a role
    }
    if (role.getMaxHolders() != null && userRoleRepo.countById_RoleId(roleId) >= role.getMaxHolders()) {
      throw new AccessException(
          "ROLE_LIMIT_REACHED",
          "Limite de " + role.getMaxHolders() + " atingido para " + role.getLabel() + ".");
    }
    userRoleRepo.save(new UserRole(id, Instant.now(), actorId));
    logRepo.save(RoleAssignmentLog.assign(targetUserId, roleId, actorId));
  }

  @Transactional
  public void remove(UUID actorId, UUID targetUserId, short roleId) {
    requireAssignableRole(roleId);
    UserRoleId id = new UserRoleId(targetUserId, roleId);
    if (!userRoleRepo.existsById(id)) {
      return; // idempotente: não tinha a role
    }
    userRoleRepo.deleteById(id);
    logRepo.save(RoleAssignmentLog.remove(targetUserId, roleId, actorId));
  }

  private Role requireAssignableRole(short roleId) {
    Role role =
        roleRepo
            .findById(roleId)
            .orElseThrow(() -> new AccessException("ROLE_NOT_FOUND", "Role não encontrada."));
    if (!role.isAssignable()) {
      throw new AccessException(
          "ROLE_NOT_ASSIGNABLE", "Esta role não pode ser gerida por aqui.");
    }
    return role;
  }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=AccessServiceTest test`
Expected: PASS (9 testes)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/AccessService.java backend/src/test/java/br/com/condominio/feature/access/AccessServiceTest.java
git commit -m "feat(access): AccessService com validação de limite e auditoria"
```

---

### Task 13: Handler de `AccessException` no `GlobalExceptionHandler`

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Adicionar o import**

Logo abaixo de `import br.com.condominio.feature.announcement.AnnouncementException;` adicione:

```java
import br.com.condominio.feature.access.AccessException;
```

- [ ] **Step 2: Adicionar o handler**

Insira este método logo antes de `@ExceptionHandler(MethodArgumentNotValidException.class)`:

```java
  @ExceptionHandler(AccessException.class)
  public ResponseEntity<ApiError> handleAccess(AccessException ex) {
    HttpStatus status =
        switch (ex.getCode()) {
          case "ROLE_LIMIT_REACHED" -> HttpStatus.CONFLICT;
          case "ROLE_NOT_FOUND", "USER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "ROLE_NOT_ASSIGNABLE", "USER_NOT_ACTIVE" -> HttpStatus.UNPROCESSABLE_ENTITY;
          default -> HttpStatus.BAD_REQUEST;
        };
    return ResponseEntity.status(status)
        .body(
            ApiError.of(
                status.value(), status.getReasonPhrase(), ex.getCode(), ex.getMessage(), requestId()));
  }
```

- [ ] **Step 3: Compilar**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java
git commit -m "feat(access): mapeia AccessException para status HTTP"
```

---

### Task 14: `AccessController` (TDD via `@WebMvcTest`)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/access/AccessController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java`

- [ ] **Step 1: Escrever o teste de contrato que falha**

```java
package br.com.condominio.feature.access;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.access.dto.AssignableRoleView;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link AccessController}: feature flag ligada; todos os endpoints exigem
 * {@code ROLE_ASSIGN} (403 sem; 401 anônimo); limite atingido vira 409.
 */
@WebMvcTest(
    controllers = AccessController.class,
    properties = "app.feature.accessmanagement.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class AccessControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID TARGET = UUID.randomUUID();
  private static final String ASSIGN = "ROLE_ASSIGN";

  @Autowired private MockMvc mvc;
  @MockBean private AccessService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  @Test
  void roles_withRoleAssign_returns200() throws Exception {
    when(service.assignableRoles())
        .thenReturn(List.of(new AssignableRoleView((short) 6, "MURAL_EDITOR", "Editor do Mural")));

    mvc.perform(get("/api/access/roles").with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("Editor do Mural"));
  }

  @Test
  void roles_withoutRoleAssign_returns403() throws Exception {
    mvc.perform(get("/api/access/roles").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).assignableRoles();
  }

  @Test
  void users_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/access/users").param("q", "ana"))
        .andExpect(status().is4xxClientError());
    verify(service, never()).searchUsers(any());
  }

  @Test
  void assign_withRoleAssign_returns204() throws Exception {
    mvc.perform(
            post("/api/access/users/{id}/roles/{roleId}", TARGET, 6).with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isNoContent());
    verify(service).assign(eq(UID), eq(TARGET), eq((short) 6));
  }

  @Test
  void assign_withoutRoleAssign_returns403() throws Exception {
    mvc.perform(post("/api/access/users/{id}/roles/{roleId}", TARGET, 6).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).assign(any(), any(), eq((short) 6));
  }

  @Test
  void assign_atLimit_returns409() throws Exception {
    doThrow(new AccessException("ROLE_LIMIT_REACHED", "Limite de 3 atingido para Conselheiro."))
        .when(service)
        .assign(eq(UID), eq(TARGET), eq((short) 2));

    mvc.perform(
            post("/api/access/users/{id}/roles/{roleId}", TARGET, 2).with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROLE_LIMIT_REACHED"));
  }

  @Test
  void remove_withRoleAssign_returns204() throws Exception {
    mvc.perform(
            delete("/api/access/users/{id}/roles/{roleId}", TARGET, 6)
                .with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isNoContent());
    verify(service).remove(eq(UID), eq(TARGET), eq((short) 6));
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=AccessControllerWebTest test`
Expected: FAIL — `AccessController` não existe (compilação).

- [ ] **Step 3: Implementar o controller**

```java
package br.com.condominio.feature.access;

import br.com.condominio.feature.access.dto.AssignableRoleView;
import br.com.condominio.feature.access.dto.UserSearchResult;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Gestão de acessos. Toda a superfície exige {@code ROLE_ASSIGN} (hoje só do Síndico). Atrás da
 * feature flag {@code app.feature.accessmanagement.enabled}.
 */
@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.accessmanagement.enabled", havingValue = "true")
public class AccessController {

  private final AccessService service;

  @GetMapping("/roles")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public List<AssignableRoleView> roles() {
    return service.assignableRoles();
  }

  @GetMapping("/users")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public List<UserSearchResult> searchUsers(@RequestParam(name = "q", defaultValue = "") String q) {
    return service.searchUsers(q);
  }

  @GetMapping("/users/{id}/roles")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public List<Short> userRoles(@PathVariable UUID id) {
    return service.userRoleIds(id);
  }

  @PostMapping("/users/{id}/roles/{roleId}")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public ResponseEntity<Void> assign(
      @PathVariable UUID id,
      @PathVariable short roleId,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.assign(me.userId(), id, roleId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/users/{id}/roles/{roleId}")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public ResponseEntity<Void> remove(
      @PathVariable UUID id,
      @PathVariable short roleId,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.remove(me.userId(), id, roleId);
    return ResponseEntity.noContent().build();
  }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=AccessControllerWebTest test`
Expected: PASS

- [ ] **Step 5: Rodar a suíte backend completa**

Run: `cd backend && ./mvnw -q test`
Expected: PASS (integração com Testcontainers pode ser skipada sem Docker)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/access/AccessController.java backend/src/test/java/br/com/condominio/feature/access/AccessControllerWebTest.java
git commit -m "feat(access): AccessController gated por ROLE_ASSIGN atrás de feature flag"
```

> **Fim da Fase 2.** Abrir PR `feat/access-rbac-api`. Lembrar de ligar `app.feature.accessmanagement.enabled=true` no env de HML (Dokploy) quando for testar.

---

# FASE 3 — Frontend `features/access`

### Task 15: `accessApi.ts` (TDD)

**Files:**
- Create: `frontend/src/features/access/api/accessApi.ts`
- Test: `frontend/src/features/access/api/accessApi.test.ts`

- [ ] **Step 1: Escrever o teste de contrato que falha**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import {
  searchUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
} from './accessApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const del = vi.mocked(api.delete);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('accessApi — contrato com o backend', () => {
  it('searchUsers envia q como param', async () => {
    get.mockResolvedValue({ data: [] });
    await searchUsers('ana');
    expect(get).toHaveBeenCalledWith('/access/users', { params: { q: 'ana' } });
  });

  it('listAssignableRoles faz GET em /access/roles', async () => {
    get.mockResolvedValue({ data: [] });
    await listAssignableRoles();
    expect(get).toHaveBeenCalledWith('/access/roles');
  });

  it('getUserRoleIds usa o id no path', async () => {
    get.mockResolvedValue({ data: [6] });
    const r = await getUserRoleIds('u1');
    expect(get).toHaveBeenCalledWith('/access/users/u1/roles');
    expect(r).toEqual([6]);
  });

  it('assignRole faz POST no path do usuário/role', async () => {
    post.mockResolvedValue({ data: undefined });
    await assignRole('u1', 6);
    expect(post).toHaveBeenCalledWith('/access/users/u1/roles/6');
  });

  it('removeRole faz DELETE no path do usuário/role', async () => {
    del.mockResolvedValue({ data: undefined });
    await removeRole('u1', 6);
    expect(del).toHaveBeenCalledWith('/access/users/u1/roles/6');
  });
});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npm run test -- src/features/access/api/accessApi.test.ts`
Expected: FAIL — módulo `./accessApi` não existe.

- [ ] **Step 3: Implementar o client**

```ts
import { api } from '@/lib/api';

export interface UserSearchResult {
  id: string;
  displayName: string;
  unitLabel: string | null;
}

export interface AssignableRole {
  id: number;
  name: string;
  label: string;
}

export async function searchUsers(q: string) {
  const r = await api.get('/access/users', { params: { q } });
  return r.data as UserSearchResult[];
}

export async function listAssignableRoles() {
  const r = await api.get('/access/roles');
  return r.data as AssignableRole[];
}

export async function getUserRoleIds(userId: string) {
  const r = await api.get(`/access/users/${userId}/roles`);
  return r.data as number[];
}

export async function assignRole(userId: string, roleId: number) {
  await api.post(`/access/users/${userId}/roles/${roleId}`);
}

export async function removeRole(userId: string, roleId: number) {
  await api.delete(`/access/users/${userId}/roles/${roleId}`);
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npm run test -- src/features/access/api/accessApi.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/access/api/accessApi.ts frontend/src/features/access/api/accessApi.test.ts
git commit -m "feat(access): accessApi client"
```

---

### Task 16: `AccessManagementPage.tsx` (TDD)

**Files:**
- Create: `frontend/src/features/access/pages/AccessManagementPage.tsx`
- Test: `frontend/src/features/access/pages/AccessManagementPage.test.tsx`

- [ ] **Step 1: Escrever os testes que falham**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/accessApi', () => ({
  searchUsers: vi.fn(),
  listAssignableRoles: vi.fn(),
  getUserRoleIds: vi.fn(),
  assignRole: vi.fn(),
  removeRole: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { AccessManagementPage } from './AccessManagementPage';
import {
  searchUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
} from '../api/accessApi';
import { toast } from 'sonner';

const searchMock = vi.mocked(searchUsers);
const rolesMock = vi.mocked(listAssignableRoles);
const userRolesMock = vi.mocked(getUserRoleIds);
const assignMock = vi.mocked(assignRole);
const removeMock = vi.mocked(removeRole);

const ROLES = [
  { id: 2, name: 'COUNCIL', label: 'Conselheiro' },
  { id: 6, name: 'MURAL_EDITOR', label: 'Editor do Mural' },
];

beforeEach(() => {
  vi.clearAllMocks();
  rolesMock.mockResolvedValue(ROLES);
  searchMock.mockResolvedValue([{ id: 'u1', displayName: 'Ana Lima', unitLabel: 'A-101' }]);
  userRolesMock.mockResolvedValue([6]);
  assignMock.mockResolvedValue(undefined);
  removeMock.mockResolvedValue(undefined);
});

async function searchAndSelect() {
  const user = userEvent.setup();
  render(<AccessManagementPage />);
  await user.type(screen.getByLabelText(/buscar/i), 'ana');
  await user.click(screen.getByRole('button', { name: /buscar/i }));
  await user.click(await screen.findByText('Ana Lima'));
  return user;
}

describe('AccessManagementPage', () => {
  it('busca e lista usuários', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await user.type(screen.getByLabelText(/buscar/i), 'ana');
    await user.click(screen.getByRole('button', { name: /buscar/i }));

    expect(await screen.findByText('Ana Lima')).toBeInTheDocument();
    expect(searchMock).toHaveBeenCalledWith('ana');
  });

  it('ao selecionar usuário mostra toggles com estado atual', async () => {
    await searchAndSelect();

    const editor = await screen.findByRole('checkbox', { name: 'Editor do Mural' });
    const council = screen.getByRole('checkbox', { name: 'Conselheiro' });
    expect(editor).toBeChecked();
    expect(council).not.toBeChecked();
  });

  it('marcar uma role chama assignRole', async () => {
    const user = await searchAndSelect();
    await user.click(await screen.findByRole('checkbox', { name: 'Conselheiro' }));

    await waitFor(() => expect(assignMock).toHaveBeenCalledWith('u1', 2));
  });

  it('desmarcar uma role chama removeRole', async () => {
    const user = await searchAndSelect();
    await user.click(await screen.findByRole('checkbox', { name: 'Editor do Mural' }));

    await waitFor(() => expect(removeMock).toHaveBeenCalledWith('u1', 6));
  });

  it('erro 409 mostra a mensagem do servidor e reverte o toggle', async () => {
    assignMock.mockRejectedValue({
      response: { data: { message: 'Limite de 3 atingido para Conselheiro.' } },
    });
    const user = await searchAndSelect();
    const council = await screen.findByRole('checkbox', { name: 'Conselheiro' });
    await user.click(council);

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('Limite de 3 atingido para Conselheiro.')
    );
    expect(council).not.toBeChecked();
  });
});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npm run test -- src/features/access/pages/AccessManagementPage.test.tsx`
Expected: FAIL — módulo `./AccessManagementPage` não existe.

- [ ] **Step 3: Implementar a página**

```tsx
import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  searchUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
  type AssignableRole,
  type UserSearchResult,
} from '../api/accessApi';

function errorMessage(err: unknown, fallback: string): string {
  const maybe = err as { response?: { data?: { message?: string } } };
  return maybe?.response?.data?.message ?? fallback;
}

export function AccessManagementPage() {
  const [roles, setRoles] = useState<AssignableRole[]>([]);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [selected, setSelected] = useState<UserSearchResult | null>(null);
  const [roleIds, setRoleIds] = useState<Set<number>>(new Set());
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    listAssignableRoles()
      .then(setRoles)
      .catch(() => toast.error('Erro ao carregar as roles.'));
  }, []);

  const doSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim().length < 2) return;
    setSearching(true);
    setSelected(null);
    try {
      setResults(await searchUsers(query.trim()));
    } catch {
      toast.error('Erro ao buscar usuários.');
    } finally {
      setSearching(false);
    }
  };

  const selectUser = async (u: UserSearchResult) => {
    setSelected(u);
    try {
      setRoleIds(new Set(await getUserRoleIds(u.id)));
    } catch {
      toast.error('Erro ao carregar acessos do usuário.');
    }
  };

  const toggle = async (role: AssignableRole) => {
    if (!selected) return;
    const has = roleIds.has(role.id);
    // otimista
    setRoleIds((prev) => {
      const next = new Set(prev);
      if (has) next.delete(role.id);
      else next.add(role.id);
      return next;
    });
    try {
      if (has) await removeRole(selected.id, role.id);
      else await assignRole(selected.id, role.id);
    } catch (err) {
      // reverte
      setRoleIds((prev) => {
        const next = new Set(prev);
        if (has) next.add(role.id);
        else next.delete(role.id);
        return next;
      });
      toast.error(errorMessage(err, 'Falha ao atualizar acesso.'));
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

      <form onSubmit={doSearch} className="mb-4 flex gap-2">
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
        <Button type="submit" className="min-h-[44px]" disabled={searching}>
          Buscar
        </Button>
      </form>

      {results.length > 0 && !selected && (
        <ul className="mb-4 space-y-2">
          {results.map((u) => (
            <li key={u.id}>
              <button
                type="button"
                onClick={() => selectUser(u)}
                className="flex min-h-[44px] w-full items-center justify-between rounded-lg border border-border px-3 text-left text-sm hover:bg-accent"
              >
                <span className="font-medium">{u.displayName}</span>
                {u.unitLabel && <span className="text-muted-foreground">{u.unitLabel}</span>}
              </button>
            </li>
          ))}
        </ul>
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
                />
                <span>{role.label}</span>
              </label>
            ))}
            <Button
              type="button"
              variant="outline"
              className="min-h-[44px]"
              onClick={() => setSelected(null)}
            >
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

Run: `cd frontend && npm run test -- src/features/access/pages/AccessManagementPage.test.tsx`
Expected: PASS (5 testes)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/access/pages/AccessManagementPage.tsx frontend/src/features/access/pages/AccessManagementPage.test.tsx
git commit -m "feat(access): tela Gerenciar acessos"
```

---

### Task 17: Rota `/admin/acessos`

**Files:**
- Modify: `frontend/src/router.tsx`

- [ ] **Step 1: Importar a página**

Adicione junto aos demais imports de páginas (após a linha do `InfoAdminPage`):

```tsx
import { AccessManagementPage } from '@/features/access/pages/AccessManagementPage';
```

- [ ] **Step 2: Registrar a rota**

Dentro do array `children` da casca autenticada, após a linha `{ path: '/faq/gerenciar', element: <FaqAdminPage /> },`, adicione:

```tsx
      { path: '/admin/acessos', element: <AccessManagementPage /> },
```

- [ ] **Step 3: Verificar build/test do front**

Run: `cd frontend && npm run test -- src/features/access`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add frontend/src/router.tsx
git commit -m "feat(access): rota /admin/acessos"
```

---

### Task 18: Item de menu gated por `ROLE_ASSIGN` (TDD na Sidebar)

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.test.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`

- [ ] **Step 1: Adicionar os casos que falham na Sidebar.test.tsx**

Dentro do `describe('Sidebar', ...)`, adicione estes dois testes:

```tsx
  it('esconde "Gerenciar acessos" sem ROLE_ASSIGN', () => {
    renderSidebar([]);
    expect(screen.queryByRole('link', { name: /gerenciar acessos/i })).not.toBeInTheDocument();
  });

  it('mostra "Gerenciar acessos" com ROLE_ASSIGN', () => {
    renderSidebar(['ROLE_ASSIGN']);
    expect(screen.getAllByRole('link', { name: /gerenciar acessos/i })[0]).toHaveAttribute(
      'href',
      '/admin/acessos'
    );
  });
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npm run test -- src/components/layout/Sidebar.test.tsx`
Expected: FAIL — não existe link "Gerenciar acessos".

- [ ] **Step 3: Adicionar o item de menu**

No `Sidebar.tsx`, importe um ícone adicionando `UserCog` à lista de imports do `lucide-react`:

```tsx
import {
  Home,
  Megaphone,
  Lightbulb,
  ShoppingBag,
  ClipboardCheck,
  ShieldCheck,
  BookOpen,
  Info,
  UserCog,
} from 'lucide-react';
```

No array `ITEMS`, adicione o item logo após o de "Cadastros pendentes":

```tsx
  {
    to: '/admin/acessos',
    label: 'Gerenciar acessos',
    icon: UserCog,
    brand: 'ink',
    requires: 'ROLE_ASSIGN',
  },
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npm run test -- src/components/layout/Sidebar.test.tsx`
Expected: PASS

- [ ] **Step 5: Suíte frontend + lint**

Run: `cd frontend && npm run test && npm run lint`
Expected: PASS, sem erros de lint.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/layout/Sidebar.tsx frontend/src/components/layout/Sidebar.test.tsx
git commit -m "feat(access): item de menu Gerenciar acessos gated por ROLE_ASSIGN"
```

> **Fim da Fase 3.** Abrir PR `feat/access-rbac-ui`.

---

## Verificação final (após as 3 fases)

- [ ] Backend: `cd backend && ./mvnw -q test` — verde (integração pode skipar sem Docker).
- [ ] Frontend: `cd frontend && npm run test && npm run lint` — verde.
- [ ] Manual (com a flag ligada): logar como síndico → menu "Gerenciar acessos" aparece → buscar usuário → marcar "Editor do Mural" → confirmar que esse usuário passa a ver "Novo aviso"/setas no mural. Tentar estourar o limite de Conselheiro → ver mensagem de 409.
- [ ] Atualizar memory `rbac-editor-mural-pendente.md` → concluído (ou remover) e ajustar `MEMORY.md`.

## Notas de rollout

- Flag `app.feature.accessmanagement.enabled` (padrão `false`). Em HML/prod liga via env no Dokploy (ver memory `hml-feature-flags`). Mudança de flag em prod registrada em issue do GitHub (CLAUDE.md).
- A role "Editor do Mural" carrega `ANNOUNCEMENT_MANAGE`; compõe com o reorder de avisos (Sub-projeto B) sem mudanças adicionais.
```
