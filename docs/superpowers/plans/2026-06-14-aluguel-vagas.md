# Aluguel de Vagas — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quadro de anúncios autosserviço para moradores alugarem suas vagas de garagem (torre, andar, numeração, valor mensal), com contato via WhatsApp puxado do perfil.

**Architecture:** Espelha de perto a feature **Classificados** (`feature.classified`) — entidade com soft delete + status enum + `author_user_id`, controller atrás de feature flag, ownership-ou-moderação no service. Diferenças: **sem fotos**, **sem título/descrição**, e o contato (nome/telefone/WhatsApp) é **resolvido do perfil do autor** na view, não armazenado. Menu ganha um **grupo expansível "Vagas"** na sidebar.

**Tech Stack:** Backend Spring Boot 3 (Java 17+, JPA/Hibernate 6, Flyway, JUnit 5 + MockMvc), Postgres. Frontend React + TypeScript + Vite, React Router, shadcn/ui + Tailwind, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-06-14-aluguel-vagas-design.md`

**Convenções fixadas (todas verificadas no código existente):**
- Tabela de usuários: `"user"` (palavra reservada, sempre entre aspas no SQL); entidade `br.com.condominio.feature.user.User` com `getId()`, `getFullName()`, `getPhone()`.
- Próxima migration livre: **V32** (última é `V31__recommendation_links_owner.sql`).
- Próximo `permission.id` livre: **20** (máximo atual é 19, em V29).
- Roles que moderam (espelhando `CLASSIFIED_MODERATE`): role **1** (MANAGER) e **2** (COUNCIL).
- Código de erro de validação retornado pelo handler: `VALIDATION_FAILED` (campo `$.code`).
- Normalizador de telefone: `br.com.condominio.feature.whatsapp.PhoneNumberNormalizer#toEvolutionNumber(String)` — lança `WhatsAppSendException` em telefone inválido/vazio.

**Entrega em 2 PRs** (cada um ≤400 linhas, trunk-based):
- **PR1 — Backend** (Tarefas 1–6): migrations, domínio, DTOs, service, controller, wiring de exceção.
- **PR2 — Frontend** (Tarefas 7–13): api client, páginas, rotas, sidebar, card da home.

---

## Estrutura de arquivos

**Backend (novos):**
- `backend/src/main/resources/db/migration/V32__parking_rental.sql`
- `backend/src/main/resources/db/migration/V33__permission_parking_rental_moderate.sql`
- `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalStatus.java`
- `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRental.java`
- `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalRepository.java`
- `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalException.java`
- `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalService.java`
- `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalController.java`
- `backend/src/main/java/br/com/condominio/feature/parkingrental/dto/CreateParkingRentalRequest.java`
- `backend/src/main/java/br/com/condominio/feature/parkingrental/dto/UpdateParkingRentalRequest.java`
- `backend/src/main/java/br/com/condominio/feature/parkingrental/dto/ParkingRentalView.java`

**Backend (modificados):**
- `backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java`

**Backend (testes):**
- `backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalTest.java`
- `backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalServiceTest.java`
- `backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalControllerWebTest.java`
- `backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalFeatureFlagOffWebTest.java`

**Frontend (novos):**
- `frontend/src/features/parking-rentals/api/parkingRentalsApi.ts` (+ `.test.ts`)
- `frontend/src/features/parking-rentals/pages/ParkingRentalsListPage.tsx` (+ `.test.tsx`)
- `frontend/src/features/parking-rentals/pages/ParkingRentalDetailPage.tsx` (+ `.test.tsx`)
- `frontend/src/features/parking-rentals/pages/ParkingRentalFormPage.tsx` (+ `.test.tsx`)

**Frontend (modificados):**
- `frontend/src/router.tsx`
- `frontend/src/components/layout/Sidebar.tsx` (+ `.test.tsx`)
- `frontend/src/App.tsx`

---

## Tarefa 1: Migrations (tabela + permissão)

**Files:**
- Create: `backend/src/main/resources/db/migration/V32__parking_rental.sql`
- Create: `backend/src/main/resources/db/migration/V33__permission_parking_rental_moderate.sql`

- [ ] **Step 1: Criar a migration da tabela**

`V32__parking_rental.sql`:

```sql
-- flyway:transactional=true

-- Aluguel de vagas (anúncios entre moradores). Soft delete; estados ACTIVE/RENTED/ARCHIVED.
-- Sem fotos, sem título/descrição. Contato (nome/telefone) é resolvido do perfil do autor
-- na camada de aplicação, não armazenado aqui.
CREATE TABLE parking_rental (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint NOT NULL DEFAULT 0,
    tower           varchar(40) NOT NULL,
    floor           varchar(20) NOT NULL,
    spot_number     varchar(40) NOT NULL,
    monthly_price   numeric(12,2) NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',
    author_user_id  uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    CONSTRAINT chk_parking_rental_status CHECK (status IN ('ACTIVE','RENTED','ARCHIVED'))
);

CREATE INDEX idx_parking_rental_status ON parking_rental (status, created_at)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_parking_rental_author ON parking_rental (author_user_id)
    WHERE deleted_at IS NULL;
```

- [ ] **Step 2: Criar a migration da permissão**

`V33__permission_parking_rental_moderate.sql`:

```sql
-- flyway:transactional=true

-- Permissão de moderação do aluguel de vagas. Espelha CLASSIFIED_MODERATE:
-- concedida a MANAGER (role 1) e COUNCIL (role 2). id 20 = próximo livre (máx atual = 19).
INSERT INTO permission (id, code, label) VALUES
    (20, 'PARKING_RENTAL_MODERATE', 'Moderar aluguel de vagas');

INSERT INTO role_permission (role_id, permission_id) VALUES
    (1, 20),
    (2, 20);
```

- [ ] **Step 3: Validar que o backend sobe e o Flyway aplica as migrations**

Run: `cd backend && ./mvnw -q -DskipTests spring-boot:run` (ou o comando de boot do projeto; em dev pode ser necessário banco no Docker — ver memória "Stack local dev"). Alternativamente, validar sintaxe aplicando só o teste de contexto no Step seguinte.
Expected: boot sem erro de Flyway; tabela `parking_rental` criada.

> Se o boot dev falhar por validação Hibernate de citext (problema conhecido só de dev), seguir o workaround `ddl-auto=none` da memória — não bloqueia esta tarefa.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V32__parking_rental.sql backend/src/main/resources/db/migration/V33__permission_parking_rental_moderate.sql
git commit -m "feat(parking): migrations de parking_rental e permissao de moderacao"
```

---

## Tarefa 2: Enum de status + entidade de domínio (TDD)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalStatus.java`
- Create: `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRental.java`
- Test: `backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalTest.java`

- [ ] **Step 1: Escrever o teste de domínio (falhando)**

`ParkingRentalTest.java`:

```java
package br.com.condominio.feature.parkingrental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParkingRentalTest {

  private ParkingRental sample() {
    return ParkingRental.create(
        UUID.randomUUID(), "A", "-1", "045", new BigDecimal("350.00"));
  }

  @Test
  void create_startsActive() {
    ParkingRental r = sample();
    assertThat(r.getStatus()).isEqualTo(ParkingRentalStatus.ACTIVE);
    assertThat(r.getTower()).isEqualTo("A");
    assertThat(r.getFloor()).isEqualTo("-1");
    assertThat(r.getSpotNumber()).isEqualTo("045");
    assertThat(r.getMonthlyPrice()).isEqualByComparingTo("350.00");
  }

  @Test
  void edit_updatesAllFields() {
    ParkingRental r = sample();
    r.edit("B", "2", "B-200", new BigDecimal("500.00"));
    assertThat(r.getTower()).isEqualTo("B");
    assertThat(r.getFloor()).isEqualTo("2");
    assertThat(r.getSpotNumber()).isEqualTo("B-200");
    assertThat(r.getMonthlyPrice()).isEqualByComparingTo("500.00");
  }

  @Test
  void markRented_fromActive_succeeds() {
    ParkingRental r = sample();
    r.markRented();
    assertThat(r.getStatus()).isEqualTo(ParkingRentalStatus.RENTED);
  }

  @Test
  void markRented_whenNotActive_throws() {
    ParkingRental r = sample();
    r.archive();
    assertThatThrownBy(r::markRented).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void archive_thenReactivate_returnsToActive() {
    ParkingRental r = sample();
    r.archive();
    assertThat(r.getStatus()).isEqualTo(ParkingRentalStatus.ARCHIVED);
    r.reactivate();
    assertThat(r.getStatus()).isEqualTo(ParkingRentalStatus.ACTIVE);
  }

  @Test
  void reactivate_whenAlreadyActive_throws() {
    ParkingRental r = sample();
    assertThatThrownBy(r::reactivate).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void archive_whenAlreadyArchived_throws() {
    ParkingRental r = sample();
    r.archive();
    assertThatThrownBy(r::archive).isInstanceOf(IllegalStateException.class);
  }
}
```

- [ ] **Step 2: Rodar o teste e confirmar a falha (não compila)**

Run: `cd backend && ./mvnw -q -Dtest=ParkingRentalTest test`
Expected: FALHA de compilação — `ParkingRental` e `ParkingRentalStatus` não existem.

- [ ] **Step 3: Criar o enum**

`ParkingRentalStatus.java`:

```java
package br.com.condominio.feature.parkingrental;

public enum ParkingRentalStatus {
  ACTIVE,
  RENTED,
  ARCHIVED
}
```

- [ ] **Step 4: Criar a entidade**

`ParkingRental.java` (espelha `Classified.java`: Lombok restrito, soft delete, `@Version`, `@PreUpdate`):

```java
package br.com.condominio.feature.parkingrental;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "parking_rental")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "status"})
@SQLDelete(sql = "UPDATE parking_rental SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class ParkingRental {

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  @Column(nullable = false, length = 40)
  private String tower;

  @Column(nullable = false, length = 20)
  private String floor;

  @Column(name = "spot_number", nullable = false, length = 40)
  private String spotNumber;

  @Column(name = "monthly_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal monthlyPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ParkingRentalStatus status;

  @Column(name = "author_user_id", nullable = false, updatable = false)
  private UUID authorUserId;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public static ParkingRental create(
      UUID authorUserId, String tower, String floor, String spotNumber, BigDecimal monthlyPrice) {
    ParkingRental r = new ParkingRental();
    r.authorUserId = authorUserId;
    r.tower = tower;
    r.floor = floor;
    r.spotNumber = spotNumber;
    r.monthlyPrice = monthlyPrice;
    r.status = ParkingRentalStatus.ACTIVE;
    return r;
  }

  public void edit(String tower, String floor, String spotNumber, BigDecimal monthlyPrice) {
    this.tower = tower;
    this.floor = floor;
    this.spotNumber = spotNumber;
    this.monthlyPrice = monthlyPrice;
  }

  public void markRented() {
    if (status != ParkingRentalStatus.ACTIVE) {
      throw new IllegalStateException("Só anúncios ativos podem ser marcados como alugados.");
    }
    status = ParkingRentalStatus.RENTED;
  }

  public void archive() {
    if (status == ParkingRentalStatus.ARCHIVED) {
      throw new IllegalStateException("Anúncio já está arquivado.");
    }
    status = ParkingRentalStatus.ARCHIVED;
  }

  public void reactivate() {
    if (status == ParkingRentalStatus.ACTIVE) {
      throw new IllegalStateException("Anúncio já está ativo.");
    }
    status = ParkingRentalStatus.ACTIVE;
  }
}
```

- [ ] **Step 5: Rodar o teste e confirmar que passa**

Run: `cd backend && ./mvnw -q -Dtest=ParkingRentalTest test`
Expected: PASS (7 testes).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalStatus.java backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRental.java backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalTest.java
git commit -m "feat(parking): entidade ParkingRental com guards de status (TDD)"
```

---

## Tarefa 3: Repository, DTOs e exceção

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalRepository.java`
- Create: `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalException.java`
- Create: `backend/src/main/java/br/com/condominio/feature/parkingrental/dto/CreateParkingRentalRequest.java`
- Create: `backend/src/main/java/br/com/condominio/feature/parkingrental/dto/UpdateParkingRentalRequest.java`
- Create: `backend/src/main/java/br/com/condominio/feature/parkingrental/dto/ParkingRentalView.java`

> Sem teste isolado nesta tarefa — estes tipos são cobertos pelos testes de service (Tarefa 4) e controller (Tarefa 5). É só plumbing. Commit junto com a Tarefa 4.

- [ ] **Step 1: Criar o repository**

`ParkingRentalRepository.java`:

```java
package br.com.condominio.feature.parkingrental;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingRentalRepository extends JpaRepository<ParkingRental, UUID> {
  Page<ParkingRental> findByStatus(ParkingRentalStatus status, Pageable pageable);
}
```

- [ ] **Step 2: Criar a exceção**

`ParkingRentalException.java` (espelha `ClassifiedException`; mapeada no handler na Tarefa 5):

```java
package br.com.condominio.feature.parkingrental;

/**
 * Erros de aluguel de vagas mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404,
 * FORBIDDEN → 403, demais → 400.
 */
public class ParkingRentalException extends RuntimeException {
  private final String code;

  public ParkingRentalException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
```

- [ ] **Step 3: Criar `CreateParkingRentalRequest`**

```java
package br.com.condominio.feature.parkingrental.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateParkingRentalRequest(
    @NotBlank @Size(max = 40) String tower,
    @NotBlank @Size(max = 20) String floor,
    @NotBlank @Size(max = 40) String spotNumber,
    @NotNull @Positive BigDecimal monthlyPrice) {}
```

- [ ] **Step 4: Criar `UpdateParkingRentalRequest`** (inclui `status` — mudança de estado viaja no PUT)

```java
package br.com.condominio.feature.parkingrental.dto;

import br.com.condominio.feature.parkingrental.ParkingRentalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateParkingRentalRequest(
    @NotBlank @Size(max = 40) String tower,
    @NotBlank @Size(max = 20) String floor,
    @NotBlank @Size(max = 40) String spotNumber,
    @NotNull @Positive BigDecimal monthlyPrice,
    ParkingRentalStatus status) {}
```

- [ ] **Step 5: Criar `ParkingRentalView`** (resolve contato do autor; `authorWhatsapp` = telefone normalizado ou null)

```java
package br.com.condominio.feature.parkingrental.dto;

import br.com.condominio.feature.parkingrental.ParkingRental;
import br.com.condominio.feature.parkingrental.ParkingRentalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ParkingRentalView(
    UUID id,
    String tower,
    String floor,
    String spotNumber,
    BigDecimal monthlyPrice,
    ParkingRentalStatus status,
    UUID authorUserId,
    Instant createdAt,
    String authorName,
    String authorPhone,
    String authorWhatsapp) {

  public static ParkingRentalView of(
      ParkingRental r, String authorName, String authorPhone, String authorWhatsapp) {
    return new ParkingRentalView(
        r.getId(),
        r.getTower(),
        r.getFloor(),
        r.getSpotNumber(),
        r.getMonthlyPrice(),
        r.getStatus(),
        r.getAuthorUserId(),
        r.getCreatedAt(),
        authorName,
        authorPhone,
        authorWhatsapp);
  }
}
```

- [ ] **Step 6: Compilar** (sem testes novos ainda)

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

(Commit acontece junto da Tarefa 4.)

---

## Tarefa 4: Service (TDD)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalService.java`
- Test: `backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalServiceTest.java`

- [ ] **Step 1: Escrever o teste de service (falhando)**

`ParkingRentalServiceTest.java`:

```java
package br.com.condominio.feature.parkingrental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.parkingrental.dto.CreateParkingRentalRequest;
import br.com.condominio.feature.parkingrental.dto.ParkingRentalView;
import br.com.condominio.feature.parkingrental.dto.UpdateParkingRentalRequest;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.whatsapp.PhoneNumberNormalizer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingRentalServiceTest {

  @Mock private ParkingRentalRepository repo;
  @Mock private UserRepository userRepo;
  @Mock private PhoneNumberNormalizer normalizer;
  @InjectMocks private ParkingRentalService service;

  private final UUID author = UUID.randomUUID();
  private final UUID rentalId = UUID.randomUUID();

  private ParkingRental sample() {
    return ParkingRental.create(author, "A", "-1", "045", new BigDecimal("350.00"));
  }

  @BeforeEach
  void stubAuthorLookup() {
    User u = org.mockito.Mockito.mock(User.class);
    lenient().when(u.getId()).thenReturn(author);
    lenient().when(u.getFullName()).thenReturn("Ana Costa");
    lenient().when(u.getPhone()).thenReturn("11999990000");
    lenient().when(userRepo.findAllById(any())).thenReturn(List.of(u));
    lenient().when(normalizer.toEvolutionNumber("11999990000")).thenReturn("5511999990000");
  }

  @Test
  void create_savesActive_andResolvesContact() {
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    ParkingRentalView v =
        service.create(
            author, new CreateParkingRentalRequest("A", "-1", "045", new BigDecimal("350.00")));
    assertThat(v.status()).isEqualTo(ParkingRentalStatus.ACTIVE);
    assertThat(v.authorName()).isEqualTo("Ana Costa");
    assertThat(v.authorPhone()).isEqualTo("11999990000");
    assertThat(v.authorWhatsapp()).isEqualTo("5511999990000");
    verify(repo).save(any(ParkingRental.class));
  }

  @Test
  void update_byOwner_succeeds() {
    ParkingRental r = sample();
    when(repo.findById(rentalId)).thenReturn(Optional.of(r));
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    ParkingRentalView v =
        service.update(
            rentalId,
            author,
            false,
            new UpdateParkingRentalRequest("B", "2", "B-200", new BigDecimal("500.00"), null));
    assertThat(v.tower()).isEqualTo("B");
    assertThat(v.spotNumber()).isEqualTo("B-200");
  }

  @Test
  void update_byNonOwnerWithoutModerate_throwsForbidden() {
    ParkingRental r = sample();
    when(repo.findById(rentalId)).thenReturn(Optional.of(r));
    UUID stranger = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                service.update(
                    rentalId,
                    stranger,
                    false,
                    new UpdateParkingRentalRequest(
                        "B", "2", "B-200", new BigDecimal("500.00"), null)))
        .isInstanceOf(ParkingRentalException.class)
        .extracting("code")
        .isEqualTo("FORBIDDEN");
  }

  @Test
  void update_byModerator_succeeds() {
    ParkingRental r = sample();
    when(repo.findById(rentalId)).thenReturn(Optional.of(r));
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    UUID moderator = UUID.randomUUID();
    ParkingRentalView v =
        service.update(
            rentalId,
            moderator,
            true,
            new UpdateParkingRentalRequest("A", "-1", "045", new BigDecimal("350.00"), null));
    assertThat(v).isNotNull();
  }

  @Test
  void update_withStatusChange_appliesTransition() {
    ParkingRental r = sample();
    when(repo.findById(rentalId)).thenReturn(Optional.of(r));
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    ParkingRentalView v =
        service.update(
            rentalId,
            author,
            false,
            new UpdateParkingRentalRequest(
                "A", "-1", "045", new BigDecimal("350.00"), ParkingRentalStatus.RENTED));
    assertThat(v.status()).isEqualTo(ParkingRentalStatus.RENTED);
  }

  @Test
  void getById_notFound_throws() {
    when(repo.findById(rentalId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getById(rentalId))
        .isInstanceOf(ParkingRentalException.class)
        .extracting("code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void view_whenPhoneInvalid_authorWhatsappIsNull() {
    when(repo.findById(rentalId)).thenReturn(Optional.of(sample()));
    when(normalizer.toEvolutionNumber(any()))
        .thenThrow(new br.com.condominio.feature.whatsapp.WhatsAppSendException("inválido"));
    ParkingRentalView v = service.getById(rentalId);
    assertThat(v.authorWhatsapp()).isNull();
    assertThat(v.authorPhone()).isEqualTo("11999990000");
  }
}
```

- [ ] **Step 2: Rodar e confirmar a falha (não compila — service inexistente)**

Run: `cd backend && ./mvnw -q -Dtest=ParkingRentalServiceTest test`
Expected: FALHA de compilação — `ParkingRentalService` não existe.

- [ ] **Step 3: Implementar o service**

`ParkingRentalService.java` (espelha `ClassifiedService` sem fotos; resolve contato + WhatsApp):

```java
package br.com.condominio.feature.parkingrental;

import br.com.condominio.feature.parkingrental.dto.CreateParkingRentalRequest;
import br.com.condominio.feature.parkingrental.dto.ParkingRentalView;
import br.com.condominio.feature.parkingrental.dto.UpdateParkingRentalRequest;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.whatsapp.PhoneNumberNormalizer;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingRentalService {

  private final ParkingRentalRepository repo;
  private final UserRepository userRepo;
  private final PhoneNumberNormalizer normalizer;

  @Transactional
  public ParkingRentalView create(UUID authorId, CreateParkingRentalRequest req) {
    ParkingRental r =
        ParkingRental.create(
            authorId, req.tower(), req.floor(), req.spotNumber(), req.monthlyPrice());
    repo.save(r);
    return view(r);
  }

  public ParkingRentalView getById(UUID id) {
    return view(load(id));
  }

  public Page<ParkingRentalView> list(ParkingRentalStatus status, Pageable pageable) {
    Page<ParkingRental> page =
        status == null
            ? repo.findByStatus(ParkingRentalStatus.ACTIVE, pageable)
            : repo.findByStatus(status, pageable);

    Set<UUID> authorIds =
        page.getContent().stream().map(ParkingRental::getAuthorUserId).collect(Collectors.toSet());
    Map<UUID, User> userIndex =
        userRepo.findAllById(authorIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

    return page.map(
        r -> {
          User u = userIndex.get(r.getAuthorUserId());
          String name = u != null ? u.getFullName() : null;
          String phone = u != null ? u.getPhone() : null;
          return ParkingRentalView.of(r, name, phone, whatsapp(phone));
        });
  }

  @Transactional
  public ParkingRentalView update(
      UUID id, UUID actorId, boolean canModerate, UpdateParkingRentalRequest req) {
    ParkingRental r = loadOwned(id, actorId, canModerate);
    r.edit(req.tower(), req.floor(), req.spotNumber(), req.monthlyPrice());
    if (req.status() != null && req.status() != r.getStatus()) {
      applyStatus(r, req.status());
    }
    repo.save(r);
    return view(r);
  }

  @Transactional
  public void delete(UUID id, UUID actorId, boolean canModerate) {
    repo.delete(loadOwned(id, actorId, canModerate));
  }

  private void applyStatus(ParkingRental r, ParkingRentalStatus target) {
    switch (target) {
      case RENTED -> r.markRented();
      case ARCHIVED -> r.archive();
      case ACTIVE -> r.reactivate();
    }
  }

  private ParkingRental load(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new ParkingRentalException("NOT_FOUND", "Anúncio não encontrado."));
  }

  private ParkingRental loadOwned(UUID id, UUID actorId, boolean canModerate) {
    ParkingRental r = load(id);
    if (!r.getAuthorUserId().equals(actorId) && !canModerate) {
      throw new ParkingRentalException("FORBIDDEN", "Sem permissão sobre este anúncio.");
    }
    return r;
  }

  /** Resolve o autor (1 query) e monta a view com contato + número de WhatsApp normalizado. */
  private ParkingRentalView view(ParkingRental r) {
    User author =
        userRepo.findAllById(List.of(r.getAuthorUserId())).stream().findFirst().orElse(null);
    String name = author != null ? author.getFullName() : null;
    String phone = author != null ? author.getPhone() : null;
    return ParkingRentalView.of(r, name, phone, whatsapp(phone));
  }

  /** Número pronto para wa.me (DDI), ou null se ausente/inválido. Nunca propaga PII em log. */
  private String whatsapp(String phone) {
    if (phone == null || phone.isBlank()) {
      return null;
    }
    try {
      return normalizer.toEvolutionNumber(phone);
    } catch (RuntimeException e) {
      log.debug("Telefone do autor não normalizável para WhatsApp; botão será omitido.");
      return null;
    }
  }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend && ./mvnw -q -Dtest=ParkingRentalServiceTest test`
Expected: PASS (7 testes).

> Se `UserRepository.findAllById`/`User.getPhone()` tiverem assinaturas diferentes do esperado, ajustar conforme o código real (verificado: `ClassifiedService` usa exatamente `userRepo.findAllById(...)`, `User#getFullName()`, `User#getPhone()`).

- [ ] **Step 5: Commit** (inclui Tarefa 3)

```bash
git add backend/src/main/java/br/com/condominio/feature/parkingrental/
git add backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalServiceTest.java
git commit -m "feat(parking): service de aluguel de vagas com contato do perfil (TDD)"
```

---

## Tarefa 5: Controller + wiring da exceção (TDD)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalController.java`
- Modify: `backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalControllerWebTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalFeatureFlagOffWebTest.java`

- [ ] **Step 1: Escrever o teste de contrato (falhando)**

`ParkingRentalControllerWebTest.java`:

```java
package br.com.condominio.feature.parkingrental;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.parkingrental.dto.ParkingRentalView;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ParkingRentalController.class,
    properties = "app.feature.parkingrental.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ParkingRentalControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID RID = UUID.randomUUID();
  private static final String MODERATE = "PARKING_RENTAL_MODERATE";

  @Autowired private MockMvc mvc;
  @MockBean private ParkingRentalService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private ParkingRentalView view() {
    return new ParkingRentalView(
        RID,
        "A",
        "-1",
        "045",
        new BigDecimal("350.00"),
        ParkingRentalStatus.ACTIVE,
        UID,
        Instant.now(),
        "Ana Costa",
        "11999990000",
        "5511999990000");
  }

  @Test
  void list_authenticated_returns200() throws Exception {
    when(service.list(any(), any()))
        .thenReturn(new PageImpl<>(List.of(view()), PageRequest.of(0, 20), 1));
    mvc.perform(get("/api/parking-rentals").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].spotNumber").value("045"))
        .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
  }

  @Test
  void list_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/parking-rentals")).andExpect(status().is4xxClientError());
    verifyNoInteractions(service);
  }

  @Test
  void get_returns200() throws Exception {
    when(service.getById(RID)).thenReturn(view());
    mvc.perform(get("/api/parking-rentals/{id}", RID).with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(RID.toString()))
        .andExpect(jsonPath("$.authorWhatsapp").value("5511999990000"));
  }

  @Test
  void create_returns201() throws Exception {
    when(service.create(eq(UID), any())).thenReturn(view());
    mvc.perform(
            post("/api/parking-rentals")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"A\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":350.00}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void create_blankTower_returns400() throws Exception {
    mvc.perform(
            post("/api/parking-rentals")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":350.00}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(service, never()).create(any(), any());
  }

  @Test
  void create_negativePrice_returns400() throws Exception {
    mvc.perform(
            post("/api/parking-rentals")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"A\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":-5}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void update_passesCanModerateFalse_whenNotModerator() throws Exception {
    when(service.update(eq(RID), eq(UID), eq(false), any())).thenReturn(view());
    mvc.perform(
            put("/api/parking-rentals/{id}", RID)
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"A\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":350.00}"))
        .andExpect(status().isOk());
    verify(service).update(eq(RID), eq(UID), eq(false), any());
  }

  @Test
  void update_passesCanModerateTrue_whenModerator() throws Exception {
    when(service.update(eq(RID), eq(UID), eq(true), any())).thenReturn(view());
    mvc.perform(
            put("/api/parking-rentals/{id}", RID)
                .with(MockAuth.user(UID, MODERATE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"A\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":350.00}"))
        .andExpect(status().isOk());
    verify(service).update(eq(RID), eq(UID), eq(true), any());
  }

  @Test
  void delete_returns204_andPassesCanModerate() throws Exception {
    mvc.perform(delete("/api/parking-rentals/{id}", RID).with(MockAuth.user(UID)))
        .andExpect(status().isNoContent());
    verify(service).delete(RID, UID, false);
  }

  @Test
  void notFound_mapsTo404() throws Exception {
    when(service.getById(RID)).thenThrow(new ParkingRentalException("NOT_FOUND", "não encontrado"));
    mvc.perform(get("/api/parking-rentals/{id}", RID).with(MockAuth.user(UID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void forbidden_mapsTo403() throws Exception {
    when(service.update(eq(RID), eq(UID), eq(false), any()))
        .thenThrow(new ParkingRentalException("FORBIDDEN", "não é o autor"));
    mvc.perform(
            put("/api/parking-rentals/{id}", RID)
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"A\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":350.00}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }
}
```

- [ ] **Step 2: Escrever o teste de feature flag off (falhando)**

`ParkingRentalFeatureFlagOffWebTest.java` (espelha `ClassifiedFeatureFlagOffWebTest` — sem a flag, o bean do controller não existe → 404):

```java
package br.com.condominio.feature.parkingrental;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Com a feature flag desligada (default), o controller não é registrado: rotas → 404. */
@WebMvcTest(
    controllers = ParkingRentalController.class,
    properties = "app.feature.parkingrental.enabled=false")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ParkingRentalFeatureFlagOffWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private ParkingRentalService service;
  @MockBean private JwtService jwtService;

  @Test
  void list_withFlagOff_returns404() throws Exception {
    mvc.perform(get("/api/parking-rentals").with(MockAuth.user(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }
}
```

- [ ] **Step 3: Rodar e confirmar a falha (controller inexistente)**

Run: `cd backend && ./mvnw -q -Dtest='ParkingRentalControllerWebTest,ParkingRentalFeatureFlagOffWebTest' test`
Expected: FALHA de compilação — `ParkingRentalController` não existe.

- [ ] **Step 4: Criar o controller**

`ParkingRentalController.java` (espelha `ClassifiedController`, sem endpoints de foto):

```java
package br.com.condominio.feature.parkingrental;

import br.com.condominio.feature.parkingrental.dto.CreateParkingRentalRequest;
import br.com.condominio.feature.parkingrental.dto.ParkingRentalView;
import br.com.condominio.feature.parkingrental.dto.UpdateParkingRentalRequest;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parking-rentals")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.parkingrental.enabled", havingValue = "true")
public class ParkingRentalController {

  private final ParkingRentalService service;

  private static boolean canModerate(AuthenticatedUserPrincipal me) {
    return me.authorities().contains("PARKING_RENTAL_MODERATE");
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public Page<ParkingRentalView> list(
      @RequestParam(required = false) ParkingRentalStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    return service.list(status, PageRequest.of(safePage, safeSize));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ParkingRentalView get(@PathVariable UUID id) {
    return service.getById(id);
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ParkingRentalView> create(
      @Valid @RequestBody CreateParkingRentalRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.userId(), body));
  }

  @PutMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ParkingRentalView update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateParkingRentalRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.update(id, me.userId(), canModerate(me), body);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.delete(id, me.userId(), canModerate(me));
    return ResponseEntity.noContent().build();
  }
}
```

- [ ] **Step 5: Registrar a exceção no `GlobalExceptionHandler`**

Adicionar o import (junto aos demais, em ordem):

```java
import br.com.condominio.feature.parkingrental.ParkingRentalException;
```

E adicionar o handler (logo após `handleClassified`, espelhando-o):

```java
  @ExceptionHandler(ParkingRentalException.class)
  public ResponseEntity<ApiError> handleParkingRental(ParkingRentalException ex) {
    HttpStatus status =
        switch (ex.getCode()) {
          case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
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

- [ ] **Step 6: Rodar e confirmar que passam**

Run: `cd backend && ./mvnw -q -Dtest='ParkingRentalControllerWebTest,ParkingRentalFeatureFlagOffWebTest' test`
Expected: PASS (12 testes no contrato + 1 no flag-off).

- [ ] **Step 7: Rodar a suíte backend inteira (garantir nada quebrado)**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/parkingrental/ParkingRentalController.java backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalControllerWebTest.java backend/src/test/java/br/com/condominio/feature/parkingrental/ParkingRentalFeatureFlagOffWebTest.java
git commit -m "feat(parking): controller REST e mapeamento de erros (TDD)"
```

> **Fim do PR1 (backend).** Abrir PR se estiver usando fluxo de PR. Caso contrário, seguir para o frontend.

---

## Tarefa 6: API client (frontend, TDD)

**Files:**
- Create: `frontend/src/features/parking-rentals/api/parkingRentalsApi.ts`
- Test: `frontend/src/features/parking-rentals/api/parkingRentalsApi.test.ts`

- [ ] **Step 1: Escrever o teste do client (falhando)**

`parkingRentalsApi.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import {
  listParkingRentals,
  getParkingRental,
  createParkingRental,
  updateParkingRental,
  deleteParkingRental,
} from './parkingRentalsApi';

const apiMock = vi.mocked(api);

beforeEach(() => vi.clearAllMocks());

describe('parkingRentalsApi', () => {
  it('listParkingRentals passa status/page/size', async () => {
    apiMock.get.mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0, number: 0 } });
    await listParkingRentals('ACTIVE');
    expect(apiMock.get).toHaveBeenCalledWith('/parking-rentals', {
      params: { status: 'ACTIVE', page: 0, size: 20 },
    });
  });

  it('getParkingRental busca por id', async () => {
    apiMock.get.mockResolvedValue({ data: { id: 'r1' } });
    const r = await getParkingRental('r1');
    expect(apiMock.get).toHaveBeenCalledWith('/parking-rentals/r1');
    expect(r.id).toBe('r1');
  });

  it('createParkingRental faz POST com o corpo', async () => {
    apiMock.post.mockResolvedValue({ data: { id: 'r1' } });
    await createParkingRental({ tower: 'A', floor: '-1', spotNumber: '045', monthlyPrice: 350 });
    expect(apiMock.post).toHaveBeenCalledWith('/parking-rentals', {
      tower: 'A',
      floor: '-1',
      spotNumber: '045',
      monthlyPrice: 350,
    });
  });

  it('updateParkingRental faz PUT com o corpo', async () => {
    apiMock.put.mockResolvedValue({ data: { id: 'r1' } });
    await updateParkingRental('r1', {
      tower: 'A',
      floor: '-1',
      spotNumber: '045',
      monthlyPrice: 350,
      status: 'RENTED',
    });
    expect(apiMock.put).toHaveBeenCalledWith('/parking-rentals/r1', {
      tower: 'A',
      floor: '-1',
      spotNumber: '045',
      monthlyPrice: 350,
      status: 'RENTED',
    });
  });

  it('deleteParkingRental faz DELETE', async () => {
    apiMock.delete.mockResolvedValue({ data: null });
    await deleteParkingRental('r1');
    expect(apiMock.delete).toHaveBeenCalledWith('/parking-rentals/r1');
  });
});
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd frontend && npx vitest run src/features/parking-rentals/api/parkingRentalsApi.test.ts`
Expected: FAIL — módulo `./parkingRentalsApi` não existe.

- [ ] **Step 3: Implementar o client**

`parkingRentalsApi.ts`:

```ts
import { api } from '@/lib/api';

export type ParkingRentalStatus = 'ACTIVE' | 'RENTED' | 'ARCHIVED';

export interface ParkingRental {
  id: string;
  tower: string;
  floor: string;
  spotNumber: string;
  monthlyPrice: number;
  status: ParkingRentalStatus;
  authorUserId: string;
  createdAt: string;
  authorName: string | null;
  authorPhone: string | null;
  authorWhatsapp: string | null;
}

export interface ParkingRentalPage {
  content: ParkingRental[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface ParkingRentalBody {
  tower: string;
  floor: string;
  spotNumber: string;
  monthlyPrice: number;
}

export async function listParkingRentals(status?: ParkingRentalStatus, page = 0, size = 20) {
  const r = await api.get('/parking-rentals', { params: { status, page, size } });
  return r.data as ParkingRentalPage;
}

export async function getParkingRental(id: string) {
  const r = await api.get(`/parking-rentals/${id}`);
  return r.data as ParkingRental;
}

export async function createParkingRental(body: ParkingRentalBody) {
  const r = await api.post('/parking-rentals', body);
  return r.data as ParkingRental;
}

export async function updateParkingRental(
  id: string,
  body: ParkingRentalBody & { status?: ParkingRentalStatus }
) {
  const r = await api.put(`/parking-rentals/${id}`, body);
  return r.data as ParkingRental;
}

export async function deleteParkingRental(id: string) {
  await api.delete(`/parking-rentals/${id}`);
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd frontend && npx vitest run src/features/parking-rentals/api/parkingRentalsApi.test.ts`
Expected: PASS (5 testes).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/parking-rentals/api/
git commit -m "feat(parking): api client de aluguel de vagas (TDD)"
```

---

## Tarefa 7: Página de listagem (TDD)

**Files:**
- Create: `frontend/src/features/parking-rentals/pages/ParkingRentalsListPage.tsx`
- Test: `frontend/src/features/parking-rentals/pages/ParkingRentalsListPage.test.tsx`

- [ ] **Step 1: Escrever o teste (falhando)**

`ParkingRentalsListPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/parkingRentalsApi', () => ({ listParkingRentals: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { ParkingRentalsListPage } from './ParkingRentalsListPage';
import { listParkingRentals } from '../api/parkingRentalsApi';

const listMock = vi.mocked(listParkingRentals);

function page(content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0 } as never;
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ParkingRentalsListPage />
    </MemoryRouter>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('ParkingRentalsListPage', () => {
  it('carrega anúncios ACTIVE por padrão', async () => {
    listMock.mockResolvedValue(
      page([{ id: 'r1', tower: 'A', floor: '-1', spotNumber: '045', monthlyPrice: 350, status: 'ACTIVE' }])
    );
    renderPage();
    expect(await screen.findByText(/Vaga 045/)).toBeInTheDocument();
    expect(listMock).toHaveBeenCalledWith('ACTIVE');
  });

  it('mostra estado vazio', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    expect(await screen.findByText('Nenhum anúncio.')).toBeInTheDocument();
  });

  it('trocar o filtro para Alugadas refaz a busca com RENTED', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    await screen.findByText('Nenhum anúncio.');
    await userEvent.click(screen.getByRole('tab', { name: 'Alugadas' }));
    await waitFor(() => expect(listMock).toHaveBeenLastCalledWith('RENTED'));
  });
});
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd frontend && npx vitest run src/features/parking-rentals/pages/ParkingRentalsListPage.test.tsx`
Expected: FAIL — módulo da página não existe.

- [ ] **Step 3: Implementar a página**

`ParkingRentalsListPage.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  listParkingRentals,
  type ParkingRental,
  type ParkingRentalStatus,
} from '../api/parkingRentalsApi';

const STATUS_LABEL: Record<ParkingRentalStatus, string> = {
  ACTIVE: 'Ativas',
  RENTED: 'Alugadas',
  ARCHIVED: 'Arquivadas',
};

export function ParkingRentalsListPage() {
  const [status, setStatus] = useState<ParkingRentalStatus>('ACTIVE');
  const [items, setItems] = useState<ParkingRental[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listParkingRentals(status)
      .then((p) => {
        if (active) setItems(p.content);
      })
      .catch(() => {
        if (active) toast.error('Erro ao carregar anúncios.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [status]);

  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="flex items-center gap-2 text-2xl font-heading font-semibold">
          <span
            aria-hidden="true"
            className="inline-block h-6 w-1.5 rounded-full"
            style={{ backgroundColor: 'hsl(var(--brand-blue))' }}
          />
          Aluguel de Vagas
        </h1>
        <Button asChild className="min-h-[44px]">
          <Link to="/vagas/aluguel/novo">Anunciar vaga</Link>
        </Button>
      </div>
      <div className="mb-4 flex flex-wrap gap-2" role="tablist" aria-label="Filtrar por status">
        {(Object.keys(STATUS_LABEL) as ParkingRentalStatus[]).map((s) => (
          <Button
            key={s}
            type="button"
            role="tab"
            aria-selected={s === status}
            variant={s === status ? 'default' : 'secondary'}
            className="min-h-[44px]"
            onClick={() => setStatus(s)}
          >
            {STATUS_LABEL[s]}
          </Button>
        ))}
      </div>
      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">Nenhum anúncio.</p>
      ) : (
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {items.map((r) => (
            <li key={r.id}>
              <Link
                to={`/vagas/aluguel/${r.id}`}
                className="block rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <Card className="h-full transition-colors hover:bg-accent">
                  <CardHeader>
                    <CardTitle className="text-base">
                      Torre {r.tower} · Andar {r.floor} · Vaga {r.spotNumber}
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    <p className="text-sm font-medium">
                      {r.monthlyPrice.toLocaleString('pt-BR', {
                        style: 'currency',
                        currency: 'BRL',
                      })}
                      /mês
                    </p>
                  </CardContent>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd frontend && npx vitest run src/features/parking-rentals/pages/ParkingRentalsListPage.test.tsx`
Expected: PASS (3 testes).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/parking-rentals/pages/ParkingRentalsListPage.tsx frontend/src/features/parking-rentals/pages/ParkingRentalsListPage.test.tsx
git commit -m "feat(parking): pagina de listagem de aluguel de vagas (TDD)"
```

---

## Tarefa 8: Página de detalhe com botão WhatsApp (TDD)

**Files:**
- Create: `frontend/src/features/parking-rentals/pages/ParkingRentalDetailPage.tsx`
- Test: `frontend/src/features/parking-rentals/pages/ParkingRentalDetailPage.test.tsx`

- [ ] **Step 1: Escrever o teste (falhando)**

`ParkingRentalDetailPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const navigateMock = vi.fn();

vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('../api/parkingRentalsApi', () => ({
  getParkingRental: vi.fn(),
  deleteParkingRental: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { ParkingRentalDetailPage } from './ParkingRentalDetailPage';
import { useAuth } from '@/features/auth/useAuth';
import { getParkingRental, deleteParkingRental } from '../api/parkingRentalsApi';

const useAuthMock = vi.mocked(useAuth);
const getMock = vi.mocked(getParkingRental);
const deleteMock = vi.mocked(deleteParkingRental);

function rental(over: Record<string, unknown> = {}) {
  return {
    id: 'r1',
    tower: 'A',
    floor: '-1',
    spotNumber: '045',
    monthlyPrice: 350,
    status: 'ACTIVE',
    authorUserId: 'u1',
    createdAt: '2026-06-14T00:00:00Z',
    authorName: 'Ana Costa',
    authorPhone: '11999990000',
    authorWhatsapp: '5511999990000',
    ...over,
  } as never;
}

function setUser(id: string, authorities: string[] = []) {
  useAuthMock.mockReturnValue({ user: { id, authorities } } as never);
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/vagas/aluguel/r1']}>
      <Routes>
        <Route path="/vagas/aluguel/:id" element={<ParkingRentalDetailPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('ParkingRentalDetailPage', () => {
  it('renderiza os dados da vaga', async () => {
    setUser('u9');
    getMock.mockResolvedValue(rental());
    renderPage();
    expect(await screen.findByText(/Vaga 045/)).toBeInTheDocument();
    expect(screen.getByText('Ativa')).toBeInTheDocument();
  });

  it('mostra botão de WhatsApp com link wa.me quando há número', async () => {
    setUser('u9');
    getMock.mockResolvedValue(rental());
    renderPage();
    await screen.findByText(/Vaga 045/);
    const link = screen.getByRole('link', { name: /whatsapp/i });
    expect(link).toHaveAttribute('href', 'https://wa.me/5511999990000');
    expect(screen.getByText('Ana Costa')).toBeInTheDocument();
  });

  it('não mostra botão de WhatsApp quando authorWhatsapp é null', async () => {
    setUser('u9');
    getMock.mockResolvedValue(rental({ authorWhatsapp: null }));
    renderPage();
    await screen.findByText(/Vaga 045/);
    expect(screen.queryByRole('link', { name: /whatsapp/i })).not.toBeInTheDocument();
  });

  it('autor vê Editar e Excluir', async () => {
    setUser('u1');
    getMock.mockResolvedValue(rental());
    renderPage();
    await screen.findByText(/Vaga 045/);
    expect(screen.getByRole('link', { name: /editar/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /excluir/i })).toBeInTheDocument();
  });

  it('terceiro sem permissão não vê ações', async () => {
    setUser('u9');
    getMock.mockResolvedValue(rental());
    renderPage();
    await screen.findByText(/Vaga 045/);
    expect(screen.queryByRole('link', { name: /editar/i })).not.toBeInTheDocument();
  });

  it('moderador vê ações mesmo sem ser autor', async () => {
    setUser('u9', ['PARKING_RENTAL_MODERATE']);
    getMock.mockResolvedValue(rental());
    renderPage();
    await screen.findByText(/Vaga 045/);
    expect(screen.getByRole('button', { name: /excluir/i })).toBeInTheDocument();
  });

  it('autor exclui após confirmar e navega de volta', async () => {
    setUser('u1');
    getMock.mockResolvedValue(rental());
    deleteMock.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();
    await screen.findByText(/Vaga 045/);
    await userEvent.click(screen.getByRole('button', { name: /excluir/i }));
    expect(deleteMock).toHaveBeenCalledWith('r1');
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/vagas/aluguel', { replace: true })
    );
  });
});
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd frontend && npx vitest run src/features/parking-rentals/pages/ParkingRentalDetailPage.test.tsx`
Expected: FAIL — módulo da página não existe.

- [ ] **Step 3: Implementar a página**

`ParkingRentalDetailPage.tsx`:

```tsx
import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft, MessageCircle, Pencil, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import {
  deleteParkingRental,
  getParkingRental,
  type ParkingRental,
  type ParkingRentalStatus,
} from '../api/parkingRentalsApi';

const STATUS_LABEL: Record<ParkingRentalStatus, string> = {
  ACTIVE: 'Ativa',
  RENTED: 'Alugada',
  ARCHIVED: 'Arquivada',
};

export function ParkingRentalDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [r, setR] = useState<ParkingRental | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      setR(await getParkingRental(id));
    } catch {
      toast.error('Erro ao carregar o anúncio.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const isOwnerOrMod =
    !!user &&
    !!r &&
    (user.id === r.authorUserId || user.authorities.includes('PARKING_RENTAL_MODERATE'));

  const handleDelete = async () => {
    if (!id) return;
    if (!window.confirm('Tem certeza que deseja excluir este anúncio?')) return;
    setDeleting(true);
    try {
      await deleteParkingRental(id);
      toast.success('Anúncio excluído.');
      navigate('/vagas/aluguel', { replace: true });
    } catch {
      toast.error('Erro ao excluir o anúncio.');
      setDeleting(false);
    }
  };

  if (loading) return <main className="mx-auto max-w-3xl p-4">Carregando…</main>;
  if (!r)
    return (
      <main className="mx-auto max-w-3xl p-4">
        <p className="text-muted-foreground">Anúncio não encontrado.</p>
        <Button asChild variant="link" className="mt-2 px-0">
          <Link to="/vagas/aluguel">Voltar ao aluguel de vagas</Link>
        </Button>
      </main>
    );

  return (
    <main className="mx-auto max-w-3xl p-4 space-y-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to="/vagas/aluguel">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-2xl font-heading font-semibold">
            Torre {r.tower} · Andar {r.floor} · Vaga {r.spotNumber}
          </h1>
          <span className="inline-block rounded-full bg-muted px-3 py-1 text-xs font-medium">
            {STATUS_LABEL[r.status]}
          </span>
        </div>
        <p className="text-xl font-semibold">
          {r.monthlyPrice.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}/mês
        </p>
      </div>

      {r.authorName && (
        <div className="rounded-lg border bg-muted/40 p-4 space-y-2">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Contato
          </p>
          <p className="text-sm font-medium">{r.authorName}</p>
          {r.authorWhatsapp && (
            <Button asChild className="min-h-[44px]">
              <a
                href={`https://wa.me/${r.authorWhatsapp}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                <MessageCircle aria-hidden="true" /> Falar no WhatsApp
              </a>
            </Button>
          )}
        </div>
      )}

      {isOwnerOrMod && (
        <div className="flex flex-wrap gap-2 pt-2">
          <Button asChild className="min-h-[44px]">
            <Link to={`/vagas/aluguel/${r.id}/editar`}>
              <Pencil aria-hidden="true" /> Editar
            </Link>
          </Button>
          <Button
            variant="destructive"
            className="min-h-[44px]"
            onClick={handleDelete}
            disabled={deleting}
          >
            <Trash2 aria-hidden="true" /> {deleting ? 'Excluindo…' : 'Excluir'}
          </Button>
        </div>
      )}
    </main>
  );
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd frontend && npx vitest run src/features/parking-rentals/pages/ParkingRentalDetailPage.test.tsx`
Expected: PASS (7 testes).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/parking-rentals/pages/ParkingRentalDetailPage.tsx frontend/src/features/parking-rentals/pages/ParkingRentalDetailPage.test.tsx
git commit -m "feat(parking): pagina de detalhe com botao WhatsApp (TDD)"
```

---

## Tarefa 9: Página de formulário (TDD)

**Files:**
- Create: `frontend/src/features/parking-rentals/pages/ParkingRentalFormPage.tsx`
- Test: `frontend/src/features/parking-rentals/pages/ParkingRentalFormPage.test.tsx`

- [ ] **Step 1: Escrever o teste (falhando)**

`ParkingRentalFormPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const navigateMock = vi.fn();

vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});
vi.mock('../api/parkingRentalsApi', () => ({
  createParkingRental: vi.fn(),
  updateParkingRental: vi.fn(),
  getParkingRental: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { ParkingRentalFormPage } from './ParkingRentalFormPage';
import { createParkingRental } from '../api/parkingRentalsApi';

const createMock = vi.mocked(createParkingRental);

function renderNew() {
  return render(
    <MemoryRouter initialEntries={['/vagas/aluguel/novo']}>
      <Routes>
        <Route path="/vagas/aluguel/novo" element={<ParkingRentalFormPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('ParkingRentalFormPage', () => {
  it('renderiza os 4 campos no modo criação', () => {
    renderNew();
    expect(screen.getByLabelText('Torre')).toBeInTheDocument();
    expect(screen.getByLabelText('Andar')).toBeInTheDocument();
    expect(screen.getByLabelText('Numeração da vaga')).toBeInTheDocument();
    expect(screen.getByLabelText('Valor mensal em R$')).toBeInTheDocument();
  });

  it('não envia com campos vazios (validação client-side)', async () => {
    renderNew();
    await userEvent.click(screen.getByRole('button', { name: /anunciar/i }));
    expect(createMock).not.toHaveBeenCalled();
  });

  it('cria e navega para o detalhe', async () => {
    createMock.mockResolvedValue({ id: 'r1' } as never);
    renderNew();
    await userEvent.type(screen.getByLabelText('Torre'), 'A');
    await userEvent.type(screen.getByLabelText('Andar'), '-1');
    await userEvent.type(screen.getByLabelText('Numeração da vaga'), '045');
    await userEvent.type(screen.getByLabelText('Valor mensal em R$'), '350,00');
    await userEvent.click(screen.getByRole('button', { name: /anunciar/i }));
    await waitFor(() =>
      expect(createMock).toHaveBeenCalledWith({
        tower: 'A',
        floor: '-1',
        spotNumber: '045',
        monthlyPrice: 350,
      })
    );
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/vagas/aluguel/r1'));
  });
});
```

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd frontend && npx vitest run src/features/parking-rentals/pages/ParkingRentalFormPage.test.tsx`
Expected: FAIL — módulo da página não existe.

- [ ] **Step 3: Implementar a página**

`ParkingRentalFormPage.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  createParkingRental,
  getParkingRental,
  updateParkingRental,
  type ParkingRental,
  type ParkingRentalStatus,
} from '../api/parkingRentalsApi';

const STATUS_OPTIONS: { value: ParkingRentalStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Ativa' },
  { value: 'RENTED', label: 'Alugada' },
  { value: 'ARCHIVED', label: 'Arquivada' },
];

function backendMessage(e: unknown, fallback: string): string {
  return (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback;
}

export function ParkingRentalFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const navigate = useNavigate();

  const [tower, setTower] = useState('');
  const [floor, setFloor] = useState('');
  const [spotNumber, setSpotNumber] = useState('');
  const [price, setPrice] = useState('');
  const [status, setStatus] = useState<ParkingRentalStatus>('ACTIVE');

  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);

  const apply = (r: ParkingRental) => {
    setTower(r.tower);
    setFloor(r.floor);
    setSpotNumber(r.spotNumber);
    setPrice(String(r.monthlyPrice));
    setStatus(r.status);
  };

  useEffect(() => {
    if (!id) return;
    let active = true;
    setLoading(true);
    getParkingRental(id)
      .then((r) => {
        if (active) apply(r);
      })
      .catch(() => {
        if (active) toast.error('Erro ao carregar o anúncio.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id]);

  const parsedPrice = (): number | null => {
    const trimmed = price.trim().replace(',', '.');
    if (!trimmed) return null;
    const n = Number(trimmed);
    return Number.isFinite(n) && n > 0 ? n : null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const monthlyPrice = parsedPrice();
    if (!tower.trim() || !floor.trim() || !spotNumber.trim() || monthlyPrice == null) {
      toast.error('Preencha torre, andar, numeração e um valor mensal válido.');
      return;
    }
    setSaving(true);
    try {
      const body = {
        tower: tower.trim(),
        floor: floor.trim(),
        spotNumber: spotNumber.trim(),
        monthlyPrice,
      };
      if (isEdit && id) {
        await updateParkingRental(id, { ...body, status });
        toast.success('Anúncio atualizado.');
        navigate(`/vagas/aluguel/${id}`);
      } else {
        const created = await createParkingRental(body);
        toast.success('Anúncio criado.');
        navigate(`/vagas/aluguel/${created.id}`);
      }
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao salvar o anúncio.'));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <main className="mx-auto max-w-2xl p-4">Carregando…</main>;

  return (
    <main className="mx-auto max-w-2xl p-4 space-y-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to={isEdit && id ? `/vagas/aluguel/${id}` : '/vagas/aluguel'}>
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'Editar anúncio' : 'Anunciar vaga'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="tower">Torre</Label>
              <Input id="tower" value={tower} onChange={(e) => setTower(e.target.value)} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="floor">Andar</Label>
              <Input id="floor" value={floor} onChange={(e) => setFloor(e.target.value)} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="spotNumber">Numeração da vaga</Label>
              <Input
                id="spotNumber"
                value={spotNumber}
                onChange={(e) => setSpotNumber(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="price">Valor mensal em R$</Label>
              <Input
                id="price"
                type="text"
                inputMode="decimal"
                placeholder="Ex.: 350,00"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                required
              />
            </div>
            {isEdit && (
              <div className="space-y-2">
                <Label htmlFor="status">Status</Label>
                <select
                  id="status"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  value={status}
                  onChange={(e) => setStatus(e.target.value as ParkingRentalStatus)}
                >
                  {STATUS_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </div>
            )}
            <Button type="submit" disabled={saving} className="min-h-[44px] w-full">
              {saving ? 'Salvando…' : isEdit ? 'Salvar alterações' : 'Anunciar vaga'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd frontend && npx vitest run src/features/parking-rentals/pages/ParkingRentalFormPage.test.tsx`
Expected: PASS (3 testes).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/parking-rentals/pages/ParkingRentalFormPage.tsx frontend/src/features/parking-rentals/pages/ParkingRentalFormPage.test.tsx
git commit -m "feat(parking): formulario de aluguel de vagas (TDD)"
```

---

## Tarefa 10: Registrar as rotas

**Files:**
- Modify: `frontend/src/router.tsx`

- [ ] **Step 1: Importar as páginas**

Adicionar os imports junto aos demais imports de páginas:

```tsx
import { ParkingRentalsListPage } from '@/features/parking-rentals/pages/ParkingRentalsListPage';
import { ParkingRentalDetailPage } from '@/features/parking-rentals/pages/ParkingRentalDetailPage';
import { ParkingRentalFormPage } from '@/features/parking-rentals/pages/ParkingRentalFormPage';
```

- [ ] **Step 2: Adicionar as rotas dentro de `children` da casca autenticada**

Inserir logo após as rotas de `/classificados`:

```tsx
      { path: '/vagas/aluguel', element: <ParkingRentalsListPage /> },
      { path: '/vagas/aluguel/novo', element: <ParkingRentalFormPage /> },
      { path: '/vagas/aluguel/:id', element: <ParkingRentalDetailPage /> },
      { path: '/vagas/aluguel/:id/editar', element: <ParkingRentalFormPage /> },
```

- [ ] **Step 3: Verificar o build (sem erro de tipos)**

Run: `cd frontend && npm run build`
Expected: build OK (sem type error). *(O pre-push só roda vitest, que não checa tipos — rodar `npm run build` aqui é obrigatório; ver memória "Gap build vs teste no frontend".)*

- [ ] **Step 4: Commit**

```bash
git add frontend/src/router.tsx
git commit -m "feat(parking): rotas de aluguel de vagas"
```

---

## Tarefa 11: Sidebar — grupo expansível "Vagas" (TDD)

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Test: `frontend/src/components/layout/Sidebar.test.tsx`

> **ATENÇÃO — reconciliar com o estado atual do arquivo.** O `Sidebar.test.tsx` em `main` referencia itens (`Moradores`/`RESIDENT_MANAGE`, classe `hover:bg-transparent` no item ativo) que **podem não estar** no `Sidebar.tsx` de `main` — provável trabalho paralelo em worktree. **Antes de editar, leia o arquivo atual** e aplique a mudança de forma **aditiva** (introduzir o conceito de grupo e adicionar a entrada "Vagas"), preservando tudo o que já existir. O código abaixo assume a estrutura de `NavItem` que vimos; adapte os nomes/classes ao que estiver no arquivo real.

- [ ] **Step 1: Escrever/!ajustar o teste do grupo (falhando)**

Adicionar a `Sidebar.test.tsx` um bloco de testes para o grupo "Vagas":

```tsx
  it('mostra o grupo "Vagas" e expande para revelar "Aluguel de Vagas"', async () => {
    const userEvent = (await import('@testing-library/user-event')).default;
    renderSidebar();
    const toggles = screen.getAllByRole('button', { name: /vagas/i });
    await userEvent.click(toggles[0]);
    expect(screen.getAllByRole('link', { name: /aluguel de vagas/i })[0]).toHaveAttribute(
      'href',
      '/vagas/aluguel'
    );
  });

  it('mostra "Escolha de Vaga" como item desabilitado (Em breve)', async () => {
    const userEvent = (await import('@testing-library/user-event')).default;
    renderSidebar();
    const toggles = screen.getAllByRole('button', { name: /vagas/i });
    await userEvent.click(toggles[0]);
    // "Escolha de Vaga" não é um link navegável ainda
    expect(screen.queryByRole('link', { name: /escolha de vaga/i })).not.toBeInTheDocument();
    expect(screen.getAllByText(/escolha de vaga/i)[0]).toBeInTheDocument();
  });
```

> `renderSidebar` já existe no arquivo e renderiza com `open={true}` (drawer + desktop). Como ambos renderizam, use `getAllByRole(...)[0]`.

- [ ] **Step 2: Rodar e confirmar a falha**

Run: `cd frontend && npx vitest run src/components/layout/Sidebar.test.tsx`
Expected: FAIL — não há grupo "Vagas".

- [ ] **Step 3: Refatorar o `Sidebar.tsx` para suportar grupos e adicionar "Vagas"**

Introduzir um tipo de entrada que seja item **ou** grupo, e renderizar grupos com um botão `aria-expanded`. Abaixo, a versão completa do arquivo **assumindo a estrutura que vimos** (adapte se o arquivo real divergir — preserve itens extras como "Moradores"):

```tsx
import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import {
  Home,
  Megaphone,
  Lightbulb,
  ShoppingBag,
  ClipboardCheck,
  BookOpen,
  Info,
  UserCog,
  SquareParking,
  ChevronDown,
} from 'lucide-react';
import { useAuth } from '@/features/auth/useAuth';

type Brand = 'red' | 'orange' | 'green' | 'blue' | 'ink';

interface NavItem {
  to: string;
  label: string;
  icon: typeof Home;
  brand: Brand;
  end?: boolean;
  requires?: string;
}

interface NavChild {
  to?: string; // ausente => item desabilitado ("Em breve")
  label: string;
  requires?: string;
  comingSoon?: boolean;
}

interface NavGroup {
  label: string;
  icon: typeof Home;
  brand: Brand;
  children: NavChild[];
}

type NavEntry = ({ kind: 'item' } & NavItem) | ({ kind: 'group' } & NavGroup);

const ENTRIES: NavEntry[] = [
  { kind: 'item', to: '/', label: 'Início', icon: Home, brand: 'ink', end: true },
  { kind: 'item', to: '/avisos', label: 'Avisos', icon: Megaphone, brand: 'red' },
  { kind: 'item', to: '/informacoes', label: 'Informações', icon: Info, brand: 'blue' },
  { kind: 'item', to: '/faq', label: 'Perguntas Frequentes', icon: BookOpen, brand: 'blue' },
  { kind: 'item', to: '/indicacoes', label: 'Indicações', icon: Lightbulb, brand: 'orange' },
  { kind: 'item', to: '/classificados', label: 'Classificados', icon: ShoppingBag, brand: 'green' },
  {
    kind: 'group',
    label: 'Vagas',
    icon: SquareParking,
    brand: 'blue',
    children: [
      { to: '/vagas/aluguel', label: 'Aluguel de Vagas' },
      { label: 'Escolha de Vaga', comingSoon: true },
    ],
  },
  {
    kind: 'item',
    to: '/admin/registrations',
    label: 'Cadastros pendentes',
    icon: ClipboardCheck,
    brand: 'ink',
    requires: 'REGISTRATION_VIEW',
  },
  {
    kind: 'item',
    to: '/admin/acessos',
    label: 'Gestão de usuários',
    icon: UserCog,
    brand: 'ink',
    requires: 'ROLE_ASSIGN',
  },
];

const brandVar = (b: Brand) => (b === 'ink' ? '--foreground' : `--brand-${b}`);
const hsl = (b: Brand, a?: number) =>
  a == null ? `hsl(var(${brandVar(b)}))` : `hsl(var(${brandVar(b)}) / ${a})`;

function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth();
  const can = (requires?: string) =>
    !requires || (user?.authorities.includes(requires) ?? false);
  const [vagasOpen, setVagasOpen] = useState(true);

  return (
    <nav aria-label="Navegação principal" className="flex flex-col gap-1 p-3">
      {ENTRIES.map((entry) => {
        if (entry.kind === 'item') {
          if (!can(entry.requires)) return null;
          const Icon = entry.icon;
          return (
            <NavLink
              key={entry.to}
              to={entry.to}
              end={entry.end}
              onClick={onNavigate}
              className={({ isActive }) =>
                [
                  'flex min-h-[44px] items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors',
                  isActive ? 'font-semibold' : 'text-foreground hover:bg-accent',
                ].join(' ')
              }
              style={({ isActive }) =>
                isActive
                  ? { backgroundColor: hsl(entry.brand, 0.12), color: hsl(entry.brand) }
                  : undefined
              }
            >
              {() => (
                <>
                  <Icon className="h-5 w-5 shrink-0" aria-hidden="true" style={{ color: hsl(entry.brand) }} />
                  <span>{entry.label}</span>
                </>
              )}
            </NavLink>
          );
        }

        // grupo
        const Icon = entry.icon;
        const visibleChildren = entry.children.filter((c) => can(c.requires));
        if (visibleChildren.length === 0) return null;
        return (
          <div key={entry.label}>
            <button
              type="button"
              aria-expanded={vagasOpen}
              onClick={() => setVagasOpen((v) => !v)}
              className="flex min-h-[44px] w-full items-center gap-3 rounded-lg px-3 text-sm font-medium text-foreground transition-colors hover:bg-accent"
            >
              <Icon className="h-5 w-5 shrink-0" aria-hidden="true" style={{ color: hsl(entry.brand) }} />
              <span className="flex-1 text-left">{entry.label}</span>
              <ChevronDown
                className={['h-4 w-4 shrink-0 transition-transform', vagasOpen ? 'rotate-180' : ''].join(' ')}
                aria-hidden="true"
              />
            </button>
            {vagasOpen && (
              <div className="ml-4 flex flex-col gap-1 border-l border-border pl-3 pt-1">
                {visibleChildren.map((child) =>
                  child.to && !child.comingSoon ? (
                    <NavLink
                      key={child.label}
                      to={child.to}
                      onClick={onNavigate}
                      className={({ isActive }) =>
                        [
                          'flex min-h-[44px] items-center rounded-lg px-3 text-sm font-medium transition-colors',
                          isActive ? 'font-semibold' : 'text-foreground hover:bg-accent',
                        ].join(' ')
                      }
                      style={({ isActive }) =>
                        isActive
                          ? { backgroundColor: hsl(entry.brand, 0.12), color: hsl(entry.brand) }
                          : undefined
                      }
                    >
                      {child.label}
                    </NavLink>
                  ) : (
                    <span
                      key={child.label}
                      aria-disabled="true"
                      className="flex min-h-[44px] items-center gap-2 rounded-lg px-3 text-sm font-medium text-muted-foreground"
                    >
                      {child.label}
                      <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] uppercase tracking-wide">
                        Em breve
                      </span>
                    </span>
                  )
                )}
              </div>
            )}
          </div>
        );
      })}
    </nav>
  );
}

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

/** Menu lateral: fixo no desktop (lg+), drawer deslizante no mobile. */
export function Sidebar({ open, onClose }: SidebarProps) {
  return (
    <>
      <aside className="sticky top-14 hidden h-[calc(100dvh-3.5rem)] w-64 shrink-0 overflow-y-auto border-r border-border bg-card lg:block">
        <SidebarNav />
      </aside>

      {open && (
        <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <button
            type="button"
            aria-label="Fechar menu"
            className="absolute inset-0 bg-black/50"
            onClick={onClose}
          />
          <aside className="absolute left-0 top-0 flex h-full w-72 max-w-[82%] flex-col overflow-y-auto border-r border-border bg-card shadow-xl">
            <div className="px-4 py-3 font-heading text-sm font-semibold text-muted-foreground">
              Menu
            </div>
            <SidebarNav onNavigate={onClose} />
          </aside>
        </div>
      )}
    </>
  );
}
```

> Se `SquareParking` não existir na versão do `lucide-react` do projeto, usar `CircleParking` ou `ParkingSquare` (todos presentes em `node_modules/lucide-react` — verificado).

- [ ] **Step 4: Rodar o teste da sidebar**

Run: `cd frontend && npx vitest run src/components/layout/Sidebar.test.tsx`
Expected: PASS (incluindo os 2 testes novos do grupo). Se algum teste preexistente (ex.: "Moradores") quebrar, é porque o arquivo real divergia — reconciliar mantendo os itens originais.

- [ ] **Step 5: Build de tipos**

Run: `cd frontend && npm run build`
Expected: build OK.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/layout/Sidebar.tsx frontend/src/components/layout/Sidebar.test.tsx
git commit -m "feat(parking): grupo expansivel 'Vagas' na sidebar (TDD)"
```

---

## Tarefa 12: Card "Vagas" na home

**Files:**
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Importar o ícone**

Adicionar `SquareParking` (ou o ícone escolhido na Tarefa 11) ao import de `lucide-react` em `App.tsx`.

- [ ] **Step 2: Adicionar a entrada ao array `NAV`**

Inserir logo após o item de Classificados:

```tsx
  {
    to: '/vagas/aluguel',
    title: 'Vagas',
    desc: 'Aluguel e escolha de vagas de garagem.',
    icon: SquareParking,
    brand: 'blue',
  },
```

- [ ] **Step 3: Verificar o build e rodar o teste da home (se houver)**

Run: `cd frontend && npm run build && npx vitest run src/App.test.tsx`
Expected: build OK; testes da home passam (o `App.test.tsx` não fixa a contagem de cards — confirmar; se fixar, ajustar o teste para incluir o novo card).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/App.tsx
git commit -m "feat(parking): card 'Vagas' na home"
```

---

## Tarefa 13: Verificação final do frontend

- [ ] **Step 1: Rodar a suíte de testes do frontend inteira**

Run: `cd frontend && npx vitest run`
Expected: todos os testes de `parking-rentals`, `Sidebar` e `App` passam. (Falhas preexistentes não relacionadas — ex.: `RegisterMasterPage` mencionada na memória — não são desta feature; anotar mas não corrigir aqui.)

- [ ] **Step 2: Build de produção**

Run: `cd frontend && npm run build`
Expected: build sem erro de tipos.

- [ ] **Step 3: Commit final / abrir PR2 (frontend)** se estiver usando fluxo de PR.

---

## Pós-implementação (fora do escopo de código)

- **Ligar a feature flag em HML:** `app.feature.parkingrental.enabled=true` via env do app no Dokploy (ver memória "Feature flags HML"). Sem a flag, as rotas retornam 404 (comportamento testado). Prod permanece `false`.
- **Smoke test em HML:** criar um anúncio, ver na lista, abrir o detalhe, clicar "Falar no WhatsApp", marcar como alugada, arquivar, excluir. Validar com o cache PWA limpo (ver memória "Cache PWA frontend HML").
- **"Escolha de Vaga":** continua como "Em breve" até a spec dedicada entregar a rota `/vagas/escolha`; quando entregar, trocar o `NavChild` desabilitado por um link e remover o selo.

---

## Self-review (preenchido pelo autor do plano)

- **Cobertura do spec:** §2 navegação → Tarefas 10–12; §3 dados → Tarefa 1; §4 backend → Tarefas 2–5; §5 frontend → Tarefas 6–9; §6 testes → embutidos em cada tarefa (TDD); §7 convenções → flag/migration/PR no cabeçalho e pós-implementação. ✔
- **Placeholders:** nenhum "TBD"/"implementar depois"; todo passo de código traz o código completo. ✔
- **Consistência de tipos:** `ParkingRental`/`ParkingRentalView`/`ParkingRentalStatus`, métodos `create/edit/markRented/archive/reactivate`, `update(id, actorId, canModerate, req)`, rotas `/api/parking-rentals` e `/vagas/aluguel/*`, permissão `PARKING_RENTAL_MODERATE`, flag `app.feature.parkingrental.enabled` — usados de forma idêntica em backend, frontend e testes. ✔
- **Riscos sinalizados:** divergência possível do `Sidebar.tsx`/`Sidebar.test.tsx` (Tarefa 11) e contagem de cards em `App.test.tsx` (Tarefa 12) — instruções de reconciliação dadas. ✔
