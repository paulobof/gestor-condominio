# Plano 3A — Classifieds — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Marcar `[x]` SÓ depois do e2e em HML passar (per regra do projeto: validar e2e em HML, não só verde local).

**Goal:** Entregar a feature de **classifieds** (anúncios entre moradores) — CRUD, até 5 fotos, estados ACTIVE/SOLD/ARCHIVED e moderação — atrás de feature flag.

**Architecture:** `package-by-feature` (`feature/classified/`). Domínio rico (transições de estado em métodos da entidade), soft delete via `@SQLDelete`/`@SQLRestriction`, autorização autor-ou-moderador no service, fotos reusando `FileStorage` + `MagicBytesValidator` (multipart → backend valida magic-bytes → MinIO, leitura via presigned GET). Controller atrás de `@ConditionalOnProperty` (flag off → bean ausente → 404).

**Tech Stack:** Spring Boot 3.3.5, JPA/Hibernate 6, PostgreSQL, Flyway, MinIO, JUnit 5 + Mockito + AssertJ. Frontend: React + Vite + TypeScript + shadcn/ui + Tailwind + axios, `browser-image-compression`.

**Spec base:** `docs/superpowers/specs/2026-06-05-plano3-classifieds-indicacoes-design.md` (§3 V15, §4 fase 3A). Endpoints na spec mestra §4.3 (linhas 587–594).

**Pré-requisitos (já no `main`, confirmados):**
- `MinioProperties.bucketClassifieds = "classifieds"` + bucket provisionado em `MinioBootstrap`.
- `MinioProperties.presignedTtlPhotosSeconds` (600) + `MagicBytesValidator.isAcceptedForPhoto` (jpg/png/webp) **já existem**.
- `PermissionCode.CLASSIFIED_MODERATE` seedado; `GlobalExceptionHandler` já mapeia `IllegalStateException → 409`.
- `AuthenticatedUserPrincipal(userId, displayName, roles, authorities, unitId, isUnitMaster)` em `shared/security`.

---

## File Structure

**Backend** (`backend/src/main/java/br/com/condominio/`):
- `feature/classified/ClassifiedStatus.java` — enum ACTIVE/SOLD/ARCHIVED.
- `feature/classified/Classified.java` — entidade rica + soft delete + transições.
- `feature/classified/ClassifiedPhoto.java` — entidade foto + soft delete.
- `feature/classified/ClassifiedRepository.java`, `ClassifiedPhotoRepository.java`.
- `feature/classified/ClassifiedException.java` — erros de negócio (NOT_FOUND/FORBIDDEN/PHOTO_*).
- `feature/classified/ClassifiedService.java` — CRUD + fotos (TDD).
- `feature/classified/ClassifiedController.java` — REST, atrás de flag.
- `feature/classified/dto/CreateClassifiedRequest.java`, `UpdateClassifiedRequest.java`, `ClassifiedView.java`, `ClassifiedPhotoView.java`.
- Modify: `shared/exception/GlobalExceptionHandler.java` — handler de `ClassifiedException`.
- Modify: `src/main/resources/application.yml` — flag `app.feature.classifieds.enabled`.
- Create: `src/main/resources/db/migration/V15__classifieds.sql`.

**Backend tests** (`backend/src/test/java/br/com/condominio/feature/classified/`):
- `ClassifiedTest.java` — máquina de estados do domínio.
- `ClassifiedServiceTest.java` — CRUD + autorização + validação de fotos (Mockito).

**Frontend** (`frontend/src/`):
- `features/classifieds/api/classifiedsApi.ts`.
- `features/classifieds/pages/ClassifiedsListPage.tsx`, `ClassifiedDetailPage.tsx`, `ClassifiedFormPage.tsx`.
- Modify: `router.tsx` — rotas.

---

## Task 1: Branch + migration V15 + feature flag

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__classifieds.sql`
- Modify: `backend/src/main/resources/application.yml` (bloco `app:`, linha ~76)

- [ ] **Step 1: Criar branch**

```bash
git checkout -b feat/3a-classifieds
```

- [ ] **Step 2: Escrever a migration V15**

Create `backend/src/main/resources/db/migration/V15__classifieds.sql`:

```sql
-- flyway:transactional=true

-- Classifieds (anúncios entre moradores). Soft delete; estados ACTIVE/SOLD/ARCHIVED.
-- Fotos no bucket MinIO `classifieds`, leitura via presigned GET. Limite 5 fotos/anúncio
-- validado no service.

CREATE TABLE classified (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint NOT NULL DEFAULT 0,
    title           varchar(120) NOT NULL,
    description     text,
    price           numeric(12,2),
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',
    author_user_id  uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    CONSTRAINT chk_classified_status CHECK (status IN ('ACTIVE','SOLD','ARCHIVED'))
);

CREATE INDEX idx_classified_status ON classified (status, created_at)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_classified_author ON classified (author_user_id)
    WHERE deleted_at IS NULL;

CREATE TABLE classified_photo (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    classified_id   uuid NOT NULL REFERENCES classified (id) ON DELETE RESTRICT,
    object_key      varchar(255) NOT NULL,
    content_type    varchar(80) NOT NULL,
    ordering        int NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz
);

CREATE UNIQUE INDEX uq_classified_photo_ordering
    ON classified_photo (classified_id, ordering)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_classified_photo_owner ON classified_photo (classified_id)
    WHERE deleted_at IS NULL;
```

- [ ] **Step 3: Adicionar a feature flag em application.yml**

No bloco `app:` (após `name:`/antes de outras chaves de `app`), adicionar:

```yaml
app:
  feature:
    classifieds:
      enabled: ${APP_FEATURE_CLASSIFIEDS_ENABLED:false}
```

(Se já existir um bloco `app.feature`, só acrescentar a chave `classifieds`.)

- [ ] **Step 4: Subir o backend local e validar a migration**

Run: `cd backend && ./mvnw -q flyway:info` (ou subir a app em profile `dev` e ver o log do Flyway).
Expected: V15 aparece como pendente/aplicada sem erro; tabelas `classified` e `classified_photo` criadas.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V15__classifieds.sql backend/src/main/resources/application.yml
git commit -m "feat(3a): migration V15 classifieds + feature flag"
```

---

## Task 2: Entidades + repositórios (TDD do domínio)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/classified/ClassifiedStatus.java`
- Create: `backend/src/main/java/br/com/condominio/feature/classified/Classified.java`
- Create: `backend/src/main/java/br/com/condominio/feature/classified/ClassifiedPhoto.java`
- Create: `backend/src/main/java/br/com/condominio/feature/classified/ClassifiedRepository.java`
- Create: `backend/src/main/java/br/com/condominio/feature/classified/ClassifiedPhotoRepository.java`
- Test: `backend/src/test/java/br/com/condominio/feature/classified/ClassifiedTest.java`

- [ ] **Step 1: Escrever o teste do domínio (falhando)**

Create `ClassifiedTest.java`:

```java
package br.com.condominio.feature.classified;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassifiedTest {

  private Classified active() {
    return Classified.create(UUID.randomUUID(), "Bicicleta", "Aro 29", new BigDecimal("500.00"));
  }

  @Test
  void create_startsActive() {
    assertThat(active().getStatus()).isEqualTo(ClassifiedStatus.ACTIVE);
  }

  @Test
  void markSold_fromActive_becomesSold() {
    Classified c = active();
    c.markSold();
    assertThat(c.getStatus()).isEqualTo(ClassifiedStatus.SOLD);
  }

  @Test
  void markSold_whenNotActive_throws() {
    Classified c = active();
    c.markSold();
    assertThatThrownBy(c::markSold).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void archive_fromActive_becomesArchived() {
    Classified c = active();
    c.archive();
    assertThat(c.getStatus()).isEqualTo(ClassifiedStatus.ARCHIVED);
  }

  @Test
  void archive_whenAlreadyArchived_throws() {
    Classified c = active();
    c.archive();
    assertThatThrownBy(c::archive).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reactivate_fromArchived_becomesActive() {
    Classified c = active();
    c.archive();
    c.reactivate();
    assertThat(c.getStatus()).isEqualTo(ClassifiedStatus.ACTIVE);
  }

  @Test
  void edit_updatesFields() {
    Classified c = active();
    c.edit("Bike", "Nova descrição", new BigDecimal("600.00"));
    assertThat(c.getTitle()).isEqualTo("Bike");
    assertThat(c.getPrice()).isEqualByComparingTo("600.00");
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=ClassifiedTest test`
Expected: FAIL — `Classified`/`ClassifiedStatus` não existem (erro de compilação).

- [ ] **Step 3: Criar o enum**

Create `ClassifiedStatus.java`:

```java
package br.com.condominio.feature.classified;

public enum ClassifiedStatus {
  ACTIVE,
  SOLD,
  ARCHIVED
}
```

- [ ] **Step 4: Criar a entidade Classified**

Create `Classified.java`:

```java
package br.com.condominio.feature.classified;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "classified")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "status"})
@SQLDelete(sql = "UPDATE classified SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Classified {

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(columnDefinition = "text")
  private String description;

  @Column(precision = 12, scale = 2)
  private BigDecimal price;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ClassifiedStatus status;

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

  public static Classified create(
      UUID authorUserId, String title, String description, BigDecimal price) {
    Classified c = new Classified();
    c.authorUserId = authorUserId;
    c.title = title;
    c.description = description;
    c.price = price;
    c.status = ClassifiedStatus.ACTIVE;
    return c;
  }

  public void edit(String title, String description, BigDecimal price) {
    this.title = title;
    this.description = description;
    this.price = price;
  }

  public void markSold() {
    if (status != ClassifiedStatus.ACTIVE) {
      throw new IllegalStateException("Só anúncios ativos podem ser marcados como vendidos.");
    }
    status = ClassifiedStatus.SOLD;
  }

  public void archive() {
    if (status == ClassifiedStatus.ARCHIVED) {
      throw new IllegalStateException("Anúncio já está arquivado.");
    }
    status = ClassifiedStatus.ARCHIVED;
  }

  public void reactivate() {
    if (status == ClassifiedStatus.ACTIVE) {
      throw new IllegalStateException("Anúncio já está ativo.");
    }
    status = ClassifiedStatus.ACTIVE;
  }
}
```

- [ ] **Step 5: Criar a entidade ClassifiedPhoto**

Create `ClassifiedPhoto.java`:

```java
package br.com.condominio.feature.classified;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "classified_photo")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "ordering"})
@SQLDelete(sql = "UPDATE classified_photo SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ClassifiedPhoto {

  @Id @GeneratedValue private UUID id;

  @Column(name = "classified_id", nullable = false, updatable = false)
  private UUID classifiedId;

  @Column(name = "object_key", nullable = false)
  private String objectKey;

  @Column(name = "content_type", nullable = false, length = 80)
  private String contentType;

  @Column(nullable = false)
  private int ordering;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public static ClassifiedPhoto create(
      UUID classifiedId, String objectKey, String contentType, int ordering) {
    ClassifiedPhoto p = new ClassifiedPhoto();
    p.classifiedId = classifiedId;
    p.objectKey = objectKey;
    p.contentType = contentType;
    p.ordering = ordering;
    return p;
  }
}
```

- [ ] **Step 6: Criar os repositórios**

Create `ClassifiedRepository.java`:

```java
package br.com.condominio.feature.classified;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassifiedRepository extends JpaRepository<Classified, UUID> {
  Page<Classified> findByStatus(ClassifiedStatus status, Pageable pageable);
}
```

Create `ClassifiedPhotoRepository.java`:

```java
package br.com.condominio.feature.classified;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClassifiedPhotoRepository extends JpaRepository<ClassifiedPhoto, UUID> {

  List<ClassifiedPhoto> findByClassifiedIdOrderByOrdering(UUID classifiedId);

  long countByClassifiedId(UUID classifiedId);

  Optional<ClassifiedPhoto> findByIdAndClassifiedId(UUID id, UUID classifiedId);

  @Query("select coalesce(max(p.ordering), -1) from ClassifiedPhoto p where p.classifiedId = :id")
  int maxOrdering(UUID id);
}
```

- [ ] **Step 7: Rodar o teste do domínio e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=ClassifiedTest test`
Expected: PASS (7 testes verdes).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/classified/ backend/src/test/java/br/com/condominio/feature/classified/ClassifiedTest.java
git commit -m "feat(3a): entidades Classified/ClassifiedPhoto + repos + domínio (TDD)"
```

---

## Task 3: DTOs + exceção + handler HTTP

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/classified/dto/CreateClassifiedRequest.java`
- Create: `.../dto/UpdateClassifiedRequest.java`
- Create: `.../dto/ClassifiedView.java`
- Create: `.../dto/ClassifiedPhotoView.java`
- Create: `backend/src/main/java/br/com/condominio/feature/classified/ClassifiedException.java`
- Modify: `backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Criar a exceção de negócio**

Create `ClassifiedException.java`:

```java
package br.com.condominio.feature.classified;

/**
 * Erros de classifieds mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404, FORBIDDEN →
 * 403, PHOTO_* → 400.
 */
public class ClassifiedException extends RuntimeException {
  private final String code;

  public ClassifiedException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
```

- [ ] **Step 2: Criar os DTOs**

Create `dto/CreateClassifiedRequest.java`:

```java
package br.com.condominio.feature.classified.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateClassifiedRequest(
    @NotBlank @Size(max = 120) String title,
    @Size(max = 5000) String description,
    @PositiveOrZero BigDecimal price) {}
```

Create `dto/UpdateClassifiedRequest.java`:

```java
package br.com.condominio.feature.classified.dto;

import br.com.condominio.feature.classified.ClassifiedStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** {@code status} nulo = mantém o estado atual; preenchido = aplica a transição de domínio. */
public record UpdateClassifiedRequest(
    @NotBlank @Size(max = 120) String title,
    @Size(max = 5000) String description,
    @PositiveOrZero BigDecimal price,
    ClassifiedStatus status) {}
```

Create `dto/ClassifiedPhotoView.java`:

```java
package br.com.condominio.feature.classified.dto;

import java.util.UUID;

public record ClassifiedPhotoView(UUID id, int ordering, String contentType) {}
```

Create `dto/ClassifiedView.java`:

```java
package br.com.condominio.feature.classified.dto;

import br.com.condominio.feature.classified.Classified;
import br.com.condominio.feature.classified.ClassifiedStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClassifiedView(
    UUID id,
    String title,
    String description,
    BigDecimal price,
    ClassifiedStatus status,
    UUID authorUserId,
    Instant createdAt,
    List<ClassifiedPhotoView> photos) {

  public static ClassifiedView of(Classified c, List<ClassifiedPhotoView> photos) {
    return new ClassifiedView(
        c.getId(),
        c.getTitle(),
        c.getDescription(),
        c.getPrice(),
        c.getStatus(),
        c.getAuthorUserId(),
        c.getCreatedAt(),
        photos);
  }
}
```

- [ ] **Step 3: Mapear a exceção no GlobalExceptionHandler**

Modify `GlobalExceptionHandler.java` — adicionar o import e um handler (depois do `handlePrivacy`):

```java
import br.com.condominio.feature.classified.ClassifiedException;
```

```java
  @ExceptionHandler(ClassifiedException.class)
  public ResponseEntity<ApiError> handleClassified(ClassifiedException ex) {
    HttpStatus status =
        switch (ex.getCode()) {
          case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
          default -> HttpStatus.BAD_REQUEST;
        };
    return ResponseEntity.status(status)
        .body(
            ApiError.of(
                status.value(), status.getReasonPhrase(), ex.getCode(), ex.getMessage(), requestId()));
  }
```

- [ ] **Step 4: Compilar**

Run: `cd backend && ./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/classified/ backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java
git commit -m "feat(3a): DTOs + ClassifiedException + mapeamento HTTP"
```

---

## Task 4: ClassifiedService — CRUD + autorização (TDD)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/classified/ClassifiedService.java`
- Test: `backend/src/test/java/br/com/condominio/feature/classified/ClassifiedServiceTest.java`

- [ ] **Step 1: Escrever o teste do service (CRUD + autorização) — falhando**

Create `ClassifiedServiceTest.java`:

```java
package br.com.condominio.feature.classified;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.classified.dto.ClassifiedView;
import br.com.condominio.feature.classified.dto.CreateClassifiedRequest;
import br.com.condominio.feature.classified.dto.UpdateClassifiedRequest;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ClassifiedServiceTest {

  private ClassifiedRepository repo;
  private ClassifiedPhotoRepository photoRepo;
  private FileStorage storage;
  private MagicBytesValidator magicBytes;
  private MinioProperties props;
  private ClassifiedService service;

  private final UUID author = UUID.randomUUID();
  private final UUID stranger = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repo = mock(ClassifiedRepository.class);
    photoRepo = mock(ClassifiedPhotoRepository.class);
    storage = mock(FileStorage.class);
    magicBytes = mock(MagicBytesValidator.class);
    props = new MinioProperties();
    service = new ClassifiedService(repo, photoRepo, storage, magicBytes, props);
    when(repo.save(any(Classified.class))).thenAnswer(i -> i.getArgument(0));
    when(photoRepo.findByClassifiedIdOrderByOrdering(any())).thenReturn(List.of());
  }

  private Classified persisted(UUID id, UUID authorId, ClassifiedStatus status) {
    Classified c = Classified.create(authorId, "Bicicleta", "Aro 29", new BigDecimal("500.00"));
    ReflectionTestUtils.setField(c, "id", id);
    ReflectionTestUtils.setField(c, "status", status);
    return c;
  }

  @Test
  void create_savesActiveClassified() {
    ClassifiedView v =
        service.create(
            author, new CreateClassifiedRequest("Bike", "desc", new BigDecimal("100.00")));
    assertThat(v.status()).isEqualTo(ClassifiedStatus.ACTIVE);
    assertThat(v.authorUserId()).isEqualTo(author);
    verify(repo).save(any(Classified.class));
  }

  @Test
  void getById_notFound_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getById(id))
        .isInstanceOf(ClassifiedException.class)
        .hasMessageContaining("não encontrado");
  }

  @Test
  void update_byAuthor_editsFields() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    ClassifiedView v =
        service.update(
            id, author, false, new UpdateClassifiedRequest("Novo", "nova", new BigDecimal("9.00"), null));
    assertThat(v.title()).isEqualTo("Novo");
  }

  @Test
  void update_byStranger_withoutModerate_forbidden() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    assertThatThrownBy(
            () ->
                service.update(
                    id, stranger, false, new UpdateClassifiedRequest("x", "y", null, null)))
        .isInstanceOf(ClassifiedException.class)
        .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
  }

  @Test
  void update_byModerator_allowed() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    ClassifiedView v =
        service.update(id, stranger, true, new UpdateClassifiedRequest("Mod", "z", null, null));
    assertThat(v.title()).isEqualTo("Mod");
  }

  @Test
  void update_withStatusTransition_marksSold() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    ClassifiedView v =
        service.update(
            id,
            author,
            false,
            new UpdateClassifiedRequest("Bike", "desc", null, ClassifiedStatus.SOLD));
    assertThat(v.status()).isEqualTo(ClassifiedStatus.SOLD);
  }

  @Test
  void delete_byAuthor_softDeletes() {
    UUID id = UUID.randomUUID();
    Classified c = persisted(id, author, ClassifiedStatus.ACTIVE);
    when(repo.findById(id)).thenReturn(Optional.of(c));
    service.delete(id, author, false);
    verify(repo).delete(c);
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=ClassifiedServiceTest test`
Expected: FAIL — `ClassifiedService` não existe.

- [ ] **Step 3: Implementar o ClassifiedService (CRUD)**

Create `ClassifiedService.java`:

```java
package br.com.condominio.feature.classified;

import br.com.condominio.feature.classified.dto.ClassifiedPhotoView;
import br.com.condominio.feature.classified.dto.ClassifiedView;
import br.com.condominio.feature.classified.dto.CreateClassifiedRequest;
import br.com.condominio.feature.classified.dto.UpdateClassifiedRequest;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassifiedService {

  private final ClassifiedRepository repo;
  private final ClassifiedPhotoRepository photoRepo;
  private final FileStorage storage;
  private final MagicBytesValidator magicBytes;
  private final MinioProperties props;

  @Transactional
  public ClassifiedView create(UUID authorId, CreateClassifiedRequest req) {
    Classified c = Classified.create(authorId, req.title(), req.description(), req.price());
    repo.save(c);
    return view(c);
  }

  public ClassifiedView getById(UUID id) {
    return view(load(id));
  }

  public Page<ClassifiedView> list(ClassifiedStatus status, Pageable pageable) {
    Page<Classified> page =
        status == null ? repo.findByStatus(ClassifiedStatus.ACTIVE, pageable)
                       : repo.findByStatus(status, pageable);
    return page.map(this::view);
  }

  @Transactional
  public ClassifiedView update(
      UUID id, UUID actorId, boolean canModerate, UpdateClassifiedRequest req) {
    Classified c = loadOwned(id, actorId, canModerate);
    c.edit(req.title(), req.description(), req.price());
    if (req.status() != null && req.status() != c.getStatus()) {
      applyStatus(c, req.status());
    }
    repo.save(c);
    return view(c);
  }

  @Transactional
  public void delete(UUID id, UUID actorId, boolean canModerate) {
    Classified c = loadOwned(id, actorId, canModerate);
    photoRepo.findByClassifiedIdOrderByOrdering(id).forEach(photoRepo::delete);
    repo.delete(c);
  }

  private void applyStatus(Classified c, ClassifiedStatus target) {
    switch (target) {
      case SOLD -> c.markSold();
      case ARCHIVED -> c.archive();
      case ACTIVE -> c.reactivate();
    }
  }

  private Classified load(UUID id) {
    return repo
        .findById(id)
        .orElseThrow(() -> new ClassifiedException("NOT_FOUND", "Anúncio não encontrado."));
  }

  private Classified loadOwned(UUID id, UUID actorId, boolean canModerate) {
    Classified c = load(id);
    if (!c.getAuthorUserId().equals(actorId) && !canModerate) {
      throw new ClassifiedException("FORBIDDEN", "Sem permissão sobre este anúncio.");
    }
    return c;
  }

  private ClassifiedView view(Classified c) {
    List<ClassifiedPhotoView> photos =
        photoRepo.findByClassifiedIdOrderByOrdering(c.getId()).stream()
            .map(p -> new ClassifiedPhotoView(p.getId(), p.getOrdering(), p.getContentType()))
            .toList();
    return ClassifiedView.of(c, photos);
  }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=ClassifiedServiceTest test`
Expected: PASS (7 testes verdes).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/classified/ClassifiedService.java backend/src/test/java/br/com/condominio/feature/classified/ClassifiedServiceTest.java
git commit -m "feat(3a): ClassifiedService CRUD + autor-ou-moderador (TDD)"
```

---

## Task 5: ClassifiedService — fotos (TDD)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/classified/ClassifiedService.java`
- Modify: `backend/src/test/java/br/com/condominio/feature/classified/ClassifiedServiceTest.java`

Regras: máx **5 fotos** ativas; content-type detectado por magic-bytes deve casar com `isAcceptedForPhoto`; tamanho ≤ **1MB** (1_048_576 bytes); upload pro MinIO **fora de transação** (método não-`@Transactional`, cada `save`/`delete` é sua própria tx).

- [ ] **Step 1: Acrescentar os testes de foto (falhando)**

Adicionar ao `ClassifiedServiceTest.java` os imports e testes:

```java
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

import br.com.condominio.feature.classified.dto.ClassifiedPhotoView;
import org.springframework.mock.web.MockMultipartFile;
```

```java
  private MockMultipartFile jpeg(int size) {
    return new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[size]);
  }

  @Test
  void addPhoto_overLimit_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    when(photoRepo.countByClassifiedId(id)).thenReturn(5L);
    assertThatThrownBy(() -> service.addPhoto(id, author, false, jpeg(10)))
        .isInstanceOf(ClassifiedException.class)
        .hasFieldOrPropertyWithValue("code", "PHOTO_LIMIT");
  }

  @Test
  void addPhoto_invalidType_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    when(photoRepo.countByClassifiedId(id)).thenReturn(0L);
    when(magicBytes.detect(any())).thenReturn("application/pdf");
    when(magicBytes.isAcceptedForPhoto("application/pdf")).thenReturn(false);
    assertThatThrownBy(() -> service.addPhoto(id, author, false, jpeg(10)))
        .isInstanceOf(ClassifiedException.class)
        .hasFieldOrPropertyWithValue("code", "PHOTO_TYPE_INVALID");
  }

  @Test
  void addPhoto_tooLarge_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    when(photoRepo.countByClassifiedId(id)).thenReturn(0L);
    when(magicBytes.detect(any())).thenReturn("image/jpeg");
    when(magicBytes.isAcceptedForPhoto("image/jpeg")).thenReturn(true);
    assertThatThrownBy(() -> service.addPhoto(id, author, false, jpeg(1_048_577)))
        .isInstanceOf(ClassifiedException.class)
        .hasFieldOrPropertyWithValue("code", "PHOTO_TOO_LARGE");
  }

  @Test
  void addPhoto_happyPath_uploadsAndSaves() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    when(photoRepo.countByClassifiedId(id)).thenReturn(0L);
    when(photoRepo.maxOrdering(id)).thenReturn(-1);
    when(magicBytes.detect(any())).thenReturn("image/jpeg");
    when(magicBytes.isAcceptedForPhoto("image/jpeg")).thenReturn(true);
    when(storage.upload(eq(props.getBucketClassifieds()), any(), anyLong(), eq("image/jpeg")))
        .thenReturn("obj-key-1");
    when(photoRepo.save(any(ClassifiedPhoto.class))).thenAnswer(i -> i.getArgument(0));

    ClassifiedPhotoView v = service.addPhoto(id, author, false, jpeg(100));

    assertThat(v.ordering()).isEqualTo(0);
    verify(storage).upload(eq(props.getBucketClassifieds()), any(), anyLong(), eq("image/jpeg"));
    verify(photoRepo).save(any(ClassifiedPhoto.class));
  }

  @Test
  void photoUrl_returnsPresigned() {
    UUID id = UUID.randomUUID();
    UUID photoId = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    ClassifiedPhoto p = ClassifiedPhoto.create(id, "obj-key-1", "image/jpeg", 0);
    ReflectionTestUtils.setField(p, "id", photoId);
    when(photoRepo.findByIdAndClassifiedId(photoId, id)).thenReturn(Optional.of(p));
    when(storage.presignedGetUrl(eq(props.getBucketClassifieds()), eq("obj-key-1"), any()))
        .thenReturn("https://minio/obj-key-1?sig");
    assertThat(service.photoUrl(id, photoId)).startsWith("https://minio/");
  }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=ClassifiedServiceTest test`
Expected: FAIL — métodos `addPhoto`/`photoUrl`/`removePhoto` não existem.

- [ ] **Step 3: Implementar os métodos de foto no service**

Adicionar ao `ClassifiedService.java` os imports e métodos:

```java
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import org.springframework.web.multipart.MultipartFile;
```

```java
  private static final long MAX_PHOTO_BYTES = 1_048_576L;
  private static final int MAX_PHOTOS = 5;

  /** NÃO transacional: upload pro MinIO acontece fora de transação (CLAUDE.md). */
  public ClassifiedPhotoView addPhoto(
      UUID id, UUID actorId, boolean canModerate, MultipartFile file) {
    loadOwned(id, actorId, canModerate);
    if (photoRepo.countByClassifiedId(id) >= MAX_PHOTOS) {
      throw new ClassifiedException("PHOTO_LIMIT", "Máximo de 5 fotos por anúncio.");
    }
    String mime;
    try (InputStream in = file.getInputStream()) {
      mime = magicBytes.detect(in);
    } catch (IOException e) {
      throw new ClassifiedException("PHOTO_READ_FAILED", "Falha ao ler a imagem.");
    }
    if (!magicBytes.isAcceptedForPhoto(mime)) {
      throw new ClassifiedException("PHOTO_TYPE_INVALID", "Aceitamos JPG, PNG ou WEBP.");
    }
    if (file.getSize() > MAX_PHOTO_BYTES) {
      throw new ClassifiedException("PHOTO_TOO_LARGE", "Foto deve ter no máximo 1MB.");
    }
    String objectKey;
    try (InputStream in = file.getInputStream()) {
      objectKey = storage.upload(props.getBucketClassifieds(), in, file.getSize(), mime);
    } catch (IOException e) {
      throw new ClassifiedException("PHOTO_UPLOAD_FAILED", "Falha ao enviar a imagem.");
    }
    int ordering = photoRepo.maxOrdering(id) + 1;
    ClassifiedPhoto saved = photoRepo.save(ClassifiedPhoto.create(id, objectKey, mime, ordering));
    return new ClassifiedPhotoView(saved.getId(), saved.getOrdering(), saved.getContentType());
  }

  @Transactional
  public void removePhoto(UUID id, UUID photoId, UUID actorId, boolean canModerate) {
    loadOwned(id, actorId, canModerate);
    ClassifiedPhoto p =
        photoRepo
            .findByIdAndClassifiedId(photoId, id)
            .orElseThrow(() -> new ClassifiedException("NOT_FOUND", "Foto não encontrada."));
    photoRepo.delete(p);
  }

  public String photoUrl(UUID id, UUID photoId) {
    load(id);
    ClassifiedPhoto p =
        photoRepo
            .findByIdAndClassifiedId(photoId, id)
            .orElseThrow(() -> new ClassifiedException("NOT_FOUND", "Foto não encontrada."));
    return storage.presignedGetUrl(
        props.getBucketClassifieds(),
        p.getObjectKey(),
        Duration.ofSeconds(props.getPresignedTtlPhotosSeconds()));
  }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=ClassifiedServiceTest test`
Expected: PASS (12 testes verdes).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/classified/ClassifiedService.java backend/src/test/java/br/com/condominio/feature/classified/ClassifiedServiceTest.java
git commit -m "feat(3a): upload/remoção/URL de fotos do classified (TDD)"
```

---

## Task 6: ClassifiedController + flag + suíte verde

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/classified/ClassifiedController.java`

- [ ] **Step 1: Criar o controller (atrás da flag)**

Create `ClassifiedController.java`:

```java
package br.com.condominio.feature.classified;

import br.com.condominio.feature.classified.dto.ClassifiedPhotoView;
import br.com.condominio.feature.classified.dto.ClassifiedView;
import br.com.condominio.feature.classified.dto.CreateClassifiedRequest;
import br.com.condominio.feature.classified.dto.UpdateClassifiedRequest;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/classifieds")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.classifieds.enabled", havingValue = "true")
public class ClassifiedController {

  private final ClassifiedService service;

  private static boolean canModerate(AuthenticatedUserPrincipal me) {
    return me.authorities().contains("CLASSIFIED_MODERATE");
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public Page<ClassifiedView> list(
      @RequestParam(required = false) ClassifiedStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(status, PageRequest.of(page, Math.min(size, 100)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ClassifiedView get(@PathVariable UUID id) {
    return service.getById(id);
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ClassifiedView> create(
      @Valid @RequestBody CreateClassifiedRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.userId(), body));
  }

  @PutMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ClassifiedView update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateClassifiedRequest body,
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

  @PostMapping("/{id}/photos")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ClassifiedPhotoView> addPhoto(
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.addPhoto(id, me.userId(), canModerate(me), file));
  }

  @DeleteMapping("/{id}/photos/{photoId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> removePhoto(
      @PathVariable UUID id,
      @PathVariable UUID photoId,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.removePhoto(id, photoId, me.userId(), canModerate(me));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/photos/{photoId}/url")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> photoUrl(
      @PathVariable UUID id, @PathVariable UUID photoId) {
    String url = service.photoUrl(id, photoId);
    return ResponseEntity.ok()
        .header("Referrer-Policy", "no-referrer")
        .body(Map.of("url", url));
  }
}
```

- [ ] **Step 2: Rodar a suíte backend inteira**

Run: `cd backend && ./mvnw -q test`
Expected: PASS — todos os testes anteriores (58 do 2C) + 19 novos de classifieds verdes.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/classified/ClassifiedController.java
git commit -m "feat(3a): ClassifiedController atrás de feature flag"
```

---

## Task 7: Frontend — API client + páginas + rotas

**Files:**
- Create: `frontend/src/features/classifieds/api/classifiedsApi.ts`
- Create: `frontend/src/features/classifieds/pages/ClassifiedsListPage.tsx`
- Create: `frontend/src/features/classifieds/pages/ClassifiedDetailPage.tsx`
- Create: `frontend/src/features/classifieds/pages/ClassifiedFormPage.tsx`
- Modify: `frontend/src/router.tsx`

Seguir os padrões de `features/admin/` (estrutura `api/` + `pages/`), `@/lib/api` (axios), shadcn/ui, Tailwind, mobile-first, PT-BR na UI. Comprimir imagem com `browser-image-compression` antes do upload (já usado no cadastro — reusar o mesmo util/abordagem).

- [ ] **Step 1: Criar o API client**

Create `classifiedsApi.ts`:

```ts
import { api } from '@/lib/api';

export type ClassifiedStatus = 'ACTIVE' | 'SOLD' | 'ARCHIVED';

export interface ClassifiedPhoto {
  id: string;
  ordering: number;
  contentType: string;
}

export interface Classified {
  id: string;
  title: string;
  description: string | null;
  price: number | null;
  status: ClassifiedStatus;
  authorUserId: string;
  createdAt: string;
  photos: ClassifiedPhoto[];
}

export interface ClassifiedPage {
  content: Classified[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export async function listClassifieds(status?: ClassifiedStatus, page = 0, size = 20) {
  const r = await api.get('/classifieds', { params: { status, page, size } });
  return r.data as ClassifiedPage;
}

export async function getClassified(id: string) {
  const r = await api.get(`/classifieds/${id}`);
  return r.data as Classified;
}

export async function createClassified(body: {
  title: string;
  description?: string;
  price?: number | null;
}) {
  const r = await api.post('/classifieds', body);
  return r.data as Classified;
}

export async function updateClassified(
  id: string,
  body: { title: string; description?: string; price?: number | null; status?: ClassifiedStatus },
) {
  const r = await api.put(`/classifieds/${id}`, body);
  return r.data as Classified;
}

export async function deleteClassified(id: string) {
  await api.delete(`/classifieds/${id}`);
}

export async function uploadClassifiedPhoto(id: string, file: File) {
  const form = new FormData();
  form.append('file', file);
  const r = await api.post(`/classifieds/${id}/photos`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data as ClassifiedPhoto;
}

export async function deleteClassifiedPhoto(id: string, photoId: string) {
  await api.delete(`/classifieds/${id}/photos/${photoId}`);
}

export async function getClassifiedPhotoUrl(id: string, photoId: string) {
  const r = await api.get(`/classifieds/${id}/photos/${photoId}/url`);
  return (r.data as { url: string }).url;
}
```

- [ ] **Step 2: Criar a página de listagem**

Create `ClassifiedsListPage.tsx` — lista paginada com filtro de status, grid de cards (mobile-first, touch ≥44px), botão "Novo anúncio" e link para o detalhe. Carrega via `listClassifieds`. Estrutura mínima:

```tsx
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listClassifieds, type Classified, type ClassifiedStatus } from '../api/classifiedsApi';

const STATUS_LABEL: Record<ClassifiedStatus, string> = {
  ACTIVE: 'Ativos',
  SOLD: 'Vendidos',
  ARCHIVED: 'Arquivados',
};

export function ClassifiedsListPage() {
  const [status, setStatus] = useState<ClassifiedStatus>('ACTIVE');
  const [items, setItems] = useState<Classified[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    listClassifieds(status)
      .then((p) => setItems(p.content))
      .finally(() => setLoading(false));
  }, [status]);

  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Classificados</h1>
        <Link
          to="/classificados/novo"
          className="inline-flex min-h-[44px] items-center rounded-md bg-primary px-4 text-primary-foreground"
        >
          Novo anúncio
        </Link>
      </div>
      <div className="mb-4 flex gap-2">
        {(Object.keys(STATUS_LABEL) as ClassifiedStatus[]).map((s) => (
          <button
            key={s}
            onClick={() => setStatus(s)}
            className={`min-h-[44px] rounded-md px-3 ${s === status ? 'bg-primary text-primary-foreground' : 'bg-muted'}`}
          >
            {STATUS_LABEL[s]}
          </button>
        ))}
      </div>
      {loading ? (
        <p>Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">Nenhum anúncio.</p>
      ) : (
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {items.map((c) => (
            <li key={c.id} className="rounded-lg border p-4">
              <Link to={`/classificados/${c.id}`} className="block">
                <h2 className="font-medium">{c.title}</h2>
                {c.price != null && (
                  <p className="text-sm">
                    {c.price.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                  </p>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
```

- [ ] **Step 3: Criar a página de detalhe**

Create `ClassifiedDetailPage.tsx` — carrega `getClassified(id)`, mostra título/descrição/preço/status, carrossel de fotos (cada foto via `getClassifiedPhotoUrl`), e — quando o usuário é autor (ou tem `CLASSIFIED_MODERATE`) — ações editar/excluir/mudar status. Usa `useParams` para o `id` e o contexto de auth existente (`useAuth`/AuthProvider) para `userId`/authorities. Botões com `min-h-[44px]`.

- [ ] **Step 4: Criar a página de formulário (criar/editar + fotos)**

Create `ClassifiedFormPage.tsx` — formulário título/descrição/preço; no modo edição carrega o anúncio e permite trocar status; upload de fotos comprimindo com `browser-image-compression` (máx 1MB, mesmo util do cadastro de comprovante) antes de `uploadClassifiedPhoto`. Em criação, salva via `createClassified` e redireciona pro detalhe.

- [ ] **Step 5: Registrar as rotas**

Modify `router.tsx` — adicionar imports e rotas protegidas:

```tsx
import { ClassifiedsListPage } from '@/features/classifieds/pages/ClassifiedsListPage';
import { ClassifiedDetailPage } from '@/features/classifieds/pages/ClassifiedDetailPage';
import { ClassifiedFormPage } from '@/features/classifieds/pages/ClassifiedFormPage';
```

Acrescentar ao array de rotas (cada uma envolta em `<ProtectedRoute>` como as demais):

```tsx
  { path: '/classificados', element: (<ProtectedRoute><ClassifiedsListPage /></ProtectedRoute>) },
  { path: '/classificados/novo', element: (<ProtectedRoute><ClassifiedFormPage /></ProtectedRoute>) },
  { path: '/classificados/:id', element: (<ProtectedRoute><ClassifiedDetailPage /></ProtectedRoute>) },
  { path: '/classificados/:id/editar', element: (<ProtectedRoute><ClassifiedFormPage /></ProtectedRoute>) },
```

- [ ] **Step 6: Build/lint do frontend**

Run: `cd frontend && npm run lint && npm run build`
Expected: sem erros de type/lint; build conclui.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/features/classifieds/ frontend/src/router.tsx
git commit -m "feat(3a): frontend de classifieds (lista, detalhe, formulário, fotos)"
```

---

## Task 8: Deploy HML + e2e real

**Files:** nenhum (deploy + validação).

- [ ] **Step 1: Abrir PR(s) e mergear na `main`**

Trunk-based: PR ≤400 linhas. Se a soma passar de 400, fatiar (backend num PR, frontend noutro). Squash merge; hooks rodam (não usar `--no-verify`).

- [ ] **Step 2: Ligar a flag em HML**

No Dokploy do backend-hml, setar `APP_FEATURE_CLASSIFIEDS_ENABLED=true` e redeploy. Confirmar no log que `ClassifiedController` foi registrado (rota `/api/classifieds` responde, não 404).

- [ ] **Step 3: e2e do fluxo completo em HML**

Logado como morador ativo em HML:
1. `POST /api/classifieds` (criar) → 201, retorna `status=ACTIVE`.
2. `POST /api/classifieds/{id}/photos` com um JPG ≤1MB → 201; tentar um PDF → 400 `PHOTO_TYPE_INVALID`; tentar 6ª foto → 400 `PHOTO_LIMIT`.
3. `GET /api/classifieds/{id}/photos/{photoId}/url` → URL presigned abre a imagem.
4. `PUT /api/classifieds/{id}` com `status=SOLD` → 200, `status=SOLD`.
5. Como **outro** morador (sem `CLASSIFIED_MODERATE`): `PUT`/`DELETE` no anúncio alheio → 403 `FORBIDDEN`.
6. Como morador com `CLASSIFIED_MODERATE`: `DELETE` no anúncio alheio → 204 (soft delete; `GET` depois → 404).
7. Frontend: criar/editar/listar/excluir pela UI; fotos aparecem; ações de moderação visíveis só para autor/moderador.

Expected: todos os passos conforme descrito; nenhuma PII (telefone/nome) nos logs.

- [ ] **Step 4: Marcar o plano como validado**

Após e2e verde em HML, marcar os `[x]` e registrar no topo do plano "VALIDADO em HML em <data>" com a instância/usuário usados. Commit do doc.

```bash
git add docs/superpowers/plans/2026-06-05-plan-3a-classifieds.md
git commit -m "docs(3a): marca Plano 3A (classifieds) como VALIDADO em HML"
```

---

## Self-review (cobertura vs spec §4 fase 3A)

- ✅ Entidades `classified` + `classified_photo` (V15) com soft delete e `UNIQUE(classified_id, ordering) WHERE deleted_at IS NULL` — Task 1/2.
- ✅ Domínio rico (markSold/archive/reactivate) — Task 2, testado em `ClassifiedTest`.
- ✅ CRUD + autor-ou-moderador no service (sem `hasRole`) — Task 4.
- ✅ Fotos ≤5/≤1MB, magic-bytes, upload fora da tx, presigned GET — Task 5.
- ✅ Endpoints da spec §4.3 (linhas 587–594) — Task 6.
- ✅ Feature flag (`@ConditionalOnProperty`, default off) — Task 1/6.
- ✅ Frontend lista/detalhe/formulário + compressão client-side — Task 7.
- ✅ e2e em HML antes de fechar — Task 8.

**Fora do 3A (vai no 3B):** recommendations, tags, consentimento, WhatsApp template. Sem dependências do 3B sobre o 3A além do padrão de foto já estabelecido aqui.
```
