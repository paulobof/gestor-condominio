# Proprietário/Moradores — Plano 1 de 4: Fundação (modelo + migrações + permission)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduzir o modelo de posse de unidade (`UnitOwnership`) como fonte de verdade, fazer backfill dos masters atuais, liberar um usuário ser master de N unidades, e criar a permission `RESIDENT_MANAGE` concedida aos proprietários — sem ainda mexer no fluxo de registro/aprovação (PR2/PR3).

**Architecture:** Nova entidade JPA rica `UnitOwnership` (soft delete, `@Version`) + repositório. Duas migrations Flyway expand/contract: V27 cria a tabela, faz backfill a partir das colunas de comprovante do `User`, e dropa o índice único que proibia multi-unidade; V28 cria `RESIDENT_MANAGE`, concede ao MANAGER e faz backfill de grant aos masters ACTIVE. O `PermissionResolver` (já existente) passa a expor a authority no JWT no próximo login.

**Tech Stack:** Spring Boot 3 (JPA/Hibernate 6, Flyway), JUnit 5, AssertJ, Testcontainers (Postgres). Convenções do projeto: Lombok sem `@Data`; soft delete via `@SQLDelete`+`@SQLRestriction`; migrations `-- flyway:transactional=true`.

**Spec:** `docs/superpowers/specs/2026-06-12-proprietario-moradores-multiunidade-design.md` (seções 1 e 2).

---

## File Structure

**Backend (criar):**
- `feature/unit/OwnershipStatus.java` — enum PENDING/APPROVED/REJECTED.
- `feature/unit/UnitOwnership.java` — entidade rica (posse usuário↔unidade + comprovante + estado).
- `feature/unit/UnitOwnershipRepository.java` — finders por usuário/unidade/status.
- `backend/src/main/resources/db/migration/V27__unit_ownership.sql` — tabela + índices + backfill + drop do índice único antigo.
- `backend/src/main/resources/db/migration/V28__permission_resident_manage.sql` — permission + role_permission(MANAGER) + grant backfill.

**Backend (modificar):**
- `feature/role/PermissionCode.java` — adicionar `RESIDENT_MANAGE`.

**Backend (testes):**
- `feature/unit/UnitOwnershipTest.java` — domínio (pending/approve/reject).
- `persistence/RepositoryPostgresTest.java` — finders rodam contra Postgres.

---

## Task 1: Enum `OwnershipStatus`

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/unit/OwnershipStatus.java`

- [ ] **Step 1: Criar o enum**

```java
package br.com.condominio.feature.unit;

/** Estado de uma posse de unidade (claim do proprietário). */
public enum OwnershipStatus {
  PENDING,
  APPROVED,
  REJECTED
}
```

- [ ] **Step 2: Compila**

Run: `cd backend && ./mvnw -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/unit/OwnershipStatus.java
git commit -m "feat(owner): enum OwnershipStatus (PENDING/APPROVED/REJECTED)"
```

---

## Task 2: Entidade rica `UnitOwnership` (domínio com TDD)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/unit/UnitOwnership.java`
- Test: `backend/src/test/java/br/com/condominio/feature/unit/UnitOwnershipTest.java`

- [ ] **Step 1: Escrever o teste de domínio que falha**

Criar `backend/src/test/java/br/com/condominio/feature/unit/UnitOwnershipTest.java`:
```java
package br.com.condominio.feature.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UnitOwnershipTest {

  @Test
  void pending_setsClaimFieldsAndStatus() {
    UUID user = UUID.randomUUID();
    UUID unit = UUID.randomUUID();

    UnitOwnership o = UnitOwnership.pending(user, unit, "key/abc", "comprovante.pdf", "application/pdf");

    assertThat(o.getUserId()).isEqualTo(user);
    assertThat(o.getUnitId()).isEqualTo(unit);
    assertThat(o.getStatus()).isEqualTo(OwnershipStatus.PENDING);
    assertThat(o.getResidenceProofObjectKey()).isEqualTo("key/abc");
    assertThat(o.getResidenceProofFilename()).isEqualTo("comprovante.pdf");
    assertThat(o.getResidenceProofContentType()).isEqualTo("application/pdf");
    assertThat(o.getResidenceProofUploadedAt()).isNotNull();
  }

  @Test
  void approve_pending_marksApprovedAndStampsApprover() {
    UUID approver = UUID.randomUUID();
    UnitOwnership o =
        UnitOwnership.pending(UUID.randomUUID(), UUID.randomUUID(), "k", "f", "application/pdf");

    o.approve(approver);

    assertThat(o.getStatus()).isEqualTo(OwnershipStatus.APPROVED);
    assertThat(o.getApprovedByUserId()).isEqualTo(approver);
    assertThat(o.getApprovedAt()).isNotNull();
    assertThat(o.getProofVerifiedAt()).isNotNull();
  }

  @Test
  void approve_notPending_throws() {
    UnitOwnership o =
        UnitOwnership.pending(UUID.randomUUID(), UUID.randomUUID(), "k", "f", "application/pdf");
    o.approve(UUID.randomUUID());

    assertThatThrownBy(() -> o.approve(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reject_pending_marksRejectedWithReason() {
    UUID approver = UUID.randomUUID();
    UnitOwnership o =
        UnitOwnership.pending(UUID.randomUUID(), UUID.randomUUID(), "k", "f", "application/pdf");

    o.reject(approver, "comprovante ilegível");

    assertThat(o.getStatus()).isEqualTo(OwnershipStatus.REJECTED);
    assertThat(o.getApprovedByUserId()).isEqualTo(approver);
    assertThat(o.getRejectionReason()).isEqualTo("comprovante ilegível");
  }
}
```

- [ ] **Step 2: Rodar e ver falhar (não compila — `UnitOwnership` não existe)**

Run: `cd backend && ./mvnw -o test -Dtest=UnitOwnershipTest`
Expected: falha de compilação (`UnitOwnership` ausente).

- [ ] **Step 3: Implementar a entidade**

Criar `backend/src/main/java/br/com/condominio/feature/unit/UnitOwnership.java`:
```java
package br.com.condominio.feature.unit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Posse de uma unidade por um usuário (proprietário). Fonte de verdade de quem é/pleiteia master. */
@Entity
@Table(name = "unit_ownership")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "status"})
@SQLDelete(sql = "UPDATE unit_ownership SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class UnitOwnership {

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "unit_id", nullable = false)
  private UUID unitId;

  @Column(name = "status", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private OwnershipStatus status;

  @Column(name = "residence_proof_object_key")
  private String residenceProofObjectKey;

  @Column(name = "residence_proof_filename")
  private String residenceProofFilename;

  @Column(name = "residence_proof_content_type", length = 80)
  private String residenceProofContentType;

  @Column(name = "residence_proof_uploaded_at")
  private Instant residenceProofUploadedAt;

  @Column(name = "proof_verified_at")
  private Instant proofVerifiedAt;

  @Column(name = "approved_by_user_id")
  private UUID approvedByUserId;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "rejection_reason", columnDefinition = "text")
  private String rejectionReason;

  @Column(name = "created_at", updatable = false)
  @CreatedDate
  private Instant createdAt;

  @Column(name = "updated_at")
  @LastModifiedDate
  private Instant updatedAt;

  @Column(name = "created_by_user_id", updatable = false)
  @CreatedBy
  private UUID createdByUserId;

  @Column(name = "updated_by_user_id")
  @LastModifiedBy
  private UUID updatedByUserId;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deleted_by_user_id")
  private UUID deletedByUserId;

  // ===== Métodos de domínio =====

  /** Cria uma posse PENDING com o comprovante anexado. */
  public static UnitOwnership pending(
      UUID userId, UUID unitId, String proofObjectKey, String proofFilename, String proofContentType) {
    UnitOwnership o = new UnitOwnership();
    o.userId = userId;
    o.unitId = unitId;
    o.status = OwnershipStatus.PENDING;
    o.residenceProofObjectKey = proofObjectKey;
    o.residenceProofFilename = proofFilename;
    o.residenceProofContentType = proofContentType;
    o.residenceProofUploadedAt = Instant.now();
    return o;
  }

  /** Aprova a posse (comprovante verificado). Só a partir de PENDING. */
  public void approve(UUID approverId) {
    if (this.status != OwnershipStatus.PENDING) {
      throw new IllegalStateException("Ownership not PENDING (current=" + this.status + ")");
    }
    this.status = OwnershipStatus.APPROVED;
    this.approvedByUserId = approverId;
    this.approvedAt = Instant.now();
    this.proofVerifiedAt = Instant.now();
  }

  /** Rejeita a posse pendente, registrando o motivo. */
  public void reject(UUID approverId, String reason) {
    if (this.status != OwnershipStatus.PENDING) {
      throw new IllegalStateException("Ownership not PENDING (current=" + this.status + ")");
    }
    this.status = OwnershipStatus.REJECTED;
    this.approvedByUserId = approverId;
    this.rejectionReason = reason;
  }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -o test -Dtest=UnitOwnershipTest`
Expected: PASS (4 testes).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/unit/UnitOwnership.java \
        backend/src/test/java/br/com/condominio/feature/unit/UnitOwnershipTest.java
git commit -m "feat(owner): entidade UnitOwnership (domínio rico: pending/approve/reject)"
```

---

## Task 3: Repositório `UnitOwnershipRepository`

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/unit/UnitOwnershipRepository.java`

- [ ] **Step 1: Criar o repositório**

```java
package br.com.condominio.feature.unit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitOwnershipRepository extends JpaRepository<UnitOwnership, UUID> {

  /** Posses do usuário em um dado status (ex.: APPROVED = "minhas unidades"). */
  List<UnitOwnership> findByUserIdAndStatus(UUID userId, OwnershipStatus status);

  /** Master atual de uma unidade (posse APPROVED). */
  Optional<UnitOwnership> findByUnitIdAndStatus(UUID unitId, OwnershipStatus status);

  /** Claims pendentes (para a tela admin), mais antigos primeiro. */
  Page<UnitOwnership> findByStatusOrderByCreatedAtAsc(OwnershipStatus status, Pageable pageable);
}
```

- [ ] **Step 2: Compila**

Run: `cd backend && ./mvnw -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/unit/UnitOwnershipRepository.java
git commit -m "feat(owner): UnitOwnershipRepository (finders por usuário/unidade/status)"
```

---

## Task 4: Migration V27 — tabela `unit_ownership` + backfill + drop do índice único

**Files:**
- Create: `backend/src/main/resources/db/migration/V27__unit_ownership.sql`

- [ ] **Step 1: Escrever a migration**

```sql
-- flyway:transactional=true

-- Posse de unidade (proprietário ↔ unidade). Move comprovante + estado de aprovação
-- (hoje na linha do "user") para o par (usuário, unidade), habilitando multi-unidade.
CREATE TABLE unit_ownership (
    id                            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                       bigint NOT NULL DEFAULT 0,
    user_id                       uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    unit_id                       uuid NOT NULL REFERENCES unit (id) ON DELETE RESTRICT,
    status                        text NOT NULL,
    residence_proof_object_key    text,
    residence_proof_filename      text,
    residence_proof_content_type  varchar(80),
    residence_proof_uploaded_at   timestamptz,
    proof_verified_at             timestamptz,
    approved_by_user_id           uuid,
    approved_at                   timestamptz,
    rejection_reason              text,
    created_at                    timestamptz NOT NULL DEFAULT now(),
    updated_at                    timestamptz,
    created_by_user_id            uuid,
    updated_by_user_id            uuid,
    deleted_at                    timestamptz,
    deleted_by_user_id            uuid,
    CONSTRAINT chk_unit_ownership_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- 1 master por unidade (posse APPROVED única por unidade).
CREATE UNIQUE INDEX ux_unit_ownership_unit_approved
    ON unit_ownership (unit_id)
    WHERE status = 'APPROVED' AND deleted_at IS NULL;

-- Sem claim duplicado do mesmo usuário na mesma unidade.
CREATE UNIQUE INDEX ux_unit_ownership_user_unit_open
    ON unit_ownership (user_id, unit_id)
    WHERE status IN ('PENDING', 'APPROVED') AND deleted_at IS NULL;

-- Lista de pendências (admin).
CREATE INDEX idx_unit_ownership_pending
    ON unit_ownership (created_at)
    WHERE status = 'PENDING' AND deleted_at IS NULL;

-- "Minhas unidades" (posses aprovadas do usuário).
CREATE INDEX idx_unit_ownership_user_approved
    ON unit_ownership (user_id)
    WHERE status = 'APPROVED' AND deleted_at IS NULL;

-- Backfill: cada master atual vira 1 posse, copiando o comprovante da linha do user.
INSERT INTO unit_ownership (
    user_id, unit_id, status,
    residence_proof_object_key, residence_proof_filename, residence_proof_content_type,
    residence_proof_uploaded_at, proof_verified_at, approved_by_user_id, approved_at,
    rejection_reason, created_at)
SELECT
    u.id, u.unit_id,
    CASE u.status
        WHEN 'ACTIVE' THEN 'APPROVED'
        WHEN 'PENDING_APPROVAL' THEN 'PENDING'
        WHEN 'REJECTED' THEN 'REJECTED'
        ELSE 'APPROVED'
    END,
    u.residence_proof_object_key, u.residence_proof_filename, u.residence_proof_content_type,
    u.residence_proof_uploaded_at, u.proof_verified_at, u.approved_by_user_id, u.approved_at,
    u.rejection_reason, u.created_at
FROM "user" u
WHERE u.is_unit_master = true AND u.unit_id IS NOT NULL AND u.deleted_at IS NULL;

-- Libera um usuário ser master de N unidades (removendo a unicidade por master_user_id).
DROP INDEX IF EXISTS ux_unit_master_user_active;
```

- [ ] **Step 2: Validar que a migration aplica num Postgres limpo (Testcontainers)**

Run: `cd backend && ./mvnw -o test -Dtest=RepositoryPostgresTest`
Expected: PASS ou **skip** (pula sem Docker). Se houver Docker, o boot do contexto aplica V27 sem erro de SQL. (Cobertura real do mapeamento vem na Task 6.)

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V27__unit_ownership.sql
git commit -m "feat(owner): migration unit_ownership + backfill masters + drop índice único de master"
```

---

## Task 5: Migration V28 — permission `RESIDENT_MANAGE` + grants; enum

**Files:**
- Create: `backend/src/main/resources/db/migration/V28__permission_resident_manage.sql`
- Modify: `backend/src/main/java/br/com/condominio/feature/role/PermissionCode.java`

- [ ] **Step 1: Escrever a migration**

(Próximo id de permission livre = 17; ids 1–16 já usados — 15=ANNOUNCEMENT_MANAGE/V18, 16=INFO_MANAGE/V22.)
```sql
-- flyway:transactional=true

-- Permissão do proprietário: gerir moradores das suas unidades.
INSERT INTO permission (id, code, label) VALUES
    (17, 'RESIDENT_MANAGE', 'Gerir moradores das minhas unidades');

-- Admin (MANAGER) recebe via role_permission (consistente com as demais permissions globais).
INSERT INTO role_permission (role_id, permission_id)
SELECT 1, id FROM permission WHERE code = 'RESIDENT_MANAGE';

-- Backfill: todo master ACTIVE atual ganha o grant (granted_by NULL = concessão do sistema).
INSERT INTO user_permission_grant (user_id, permission_id, granted_by_user_id)
SELECT u.id, p.id, NULL
FROM "user" u
CROSS JOIN permission p
WHERE p.code = 'RESIDENT_MANAGE'
  AND u.is_unit_master = true
  AND u.status = 'ACTIVE'
  AND u.deleted_at IS NULL;
```

- [ ] **Step 2: Adicionar `RESIDENT_MANAGE` ao enum**

Em `backend/src/main/java/br/com/condominio/feature/role/PermissionCode.java`, adicionar a constante ao final da lista:
```java
  PERMISSION_GRANT,
  AUDIT_VIEW,
  RESIDENT_MANAGE
}
```

- [ ] **Step 3: Compila + migration aplica**

Run: `cd backend && ./mvnw -o test -Dtest=RepositoryPostgresTest`
Expected: PASS ou skip (sem Docker). Com Docker, V28 aplica sem violar FK/unique.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V28__permission_resident_manage.sql \
        backend/src/main/java/br/com/condominio/feature/role/PermissionCode.java
git commit -m "feat(owner): permission RESIDENT_MANAGE (MANAGER + grant aos masters)"
```

---

## Task 6: Teste de integração Postgres do `UnitOwnershipRepository`

**Files:**
- Modify: `backend/src/test/java/br/com/condominio/persistence/RepositoryPostgresTest.java`

- [ ] **Step 1: Adicionar a injeção e o teste**

Em `RepositoryPostgresTest.java`, adicionar o import e o `@Autowired`:
```java
import br.com.condominio.feature.unit.OwnershipStatus;
import br.com.condominio.feature.unit.UnitOwnershipRepository;
```
```java
  @Autowired private UnitOwnershipRepository ownerships;
```
E adicionar o teste (valida mapeamento Hibernate + os 3 finders rodando contra Postgres; sem linhas seedadas, retornam vazio — como o padrão dos demais testes):
```java
  @Test
  void unitOwnershipFinders_runAgainstPostgres() {
    java.util.UUID anyUser = java.util.UUID.randomUUID();
    java.util.UUID anyUnit = java.util.UUID.randomUUID();

    assertThatCode(() -> ownerships.findByUserIdAndStatus(anyUser, OwnershipStatus.APPROVED))
        .doesNotThrowAnyException();
    assertThatCode(() -> ownerships.findByUnitIdAndStatus(anyUnit, OwnershipStatus.APPROVED))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                ownerships.findByStatusOrderByCreatedAtAsc(
                    OwnershipStatus.PENDING, PageRequest.of(0, 20)))
        .doesNotThrowAnyException();

    assertThat(ownerships.findByUserIdAndStatus(anyUser, OwnershipStatus.APPROVED)).isEmpty();
  }
```

- [ ] **Step 2: Rodar (passa ou pula sem Docker)**

Run: `cd backend && ./mvnw -o test -Dtest=RepositoryPostgresTest`
Expected: PASS (com Docker) ou skip (sem). Com Docker, valida que a entidade mapeia 1:1 com a tabela V27 e os finders geram SQL válido.

- [ ] **Step 3: Suíte completa do backend + formatação**

Run: `cd backend && ./mvnw -o spotless:check test`
Expected: BUILD SUCCESS (0 failures; os testes de Postgres pulam sem Docker). Garante que dropar `ux_unit_master_user_active` não quebrou nenhum teste existente.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/br/com/condominio/persistence/RepositoryPostgresTest.java
git commit -m "test(owner): integração Postgres dos finders de UnitOwnership"
```

---

## Self-Review

- **Cobertura do spec (seções 1–2):** entidade `UnitOwnership` (1.1) ✓; índices únicos/parciais (1.1) ✓; drop do `ux_unit_master_user_active` (1.2) ✓; `User`/`Unit` inalterados nesta fase, colunas de proof do User mantidas (expand/contract, 1.3) ✓; migração + backfill (1.4) ✓; permission `RESIDENT_MANAGE` + MANAGER + grant backfill (2) ✓. **Fora desta fase (vai em PR2/PR3):** refatorar `RegistrationService`/aprovação para usar ownership; `unit.master_user_id` setado na aprovação; endpoints. Coberto pelos próximos planos.
- **Sem placeholders:** todo passo tem código/SQL/comando completos.
- **Consistência de tipos:** `OwnershipStatus` {PENDING,APPROVED,REJECTED} idêntico no enum, no CHECK da V27 e nos finders; `UnitOwnership.pending/approve/reject` usados no teste batem com a entidade; `unit_ownership` colunas batem com os `@Column` da entidade; `permission_id` é smallint (V3) e o grant insere via `SELECT id ... WHERE code='RESIDENT_MANAGE'`; id 17 confirmado livre (max atual = 16).
- **Nota de execução:** os testes de Postgres pulam sem Docker localmente; a validação real do mapeamento/migração roda no CI/HML. As migrations são expand-only (não removem colunas), backward-compatible.
