# Plano 3B — Indicações (Recomendações) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) ou superpowers:executing-plans para implementar task-by-task. Steps usam checkbox (`- [ ]`). Marcar `[x]` SÓ depois do e2e em HML passar (regra do projeto: validar e2e em HML, não só verde local).
>
> **Pré-requisito:** Plano 3A (Classifieds) já está na `main` e serve de **referência viva** para os padrões mecânicos (entidade rica + soft delete, service de fotos, controller atrás de flag, frontend de feature). Onde este plano diz "espelha o 3A", abra o arquivo correspondente em `feature/classified/` e replique trocando os tipos.

**Goal:** Entregar a feature de **indicações** (recomendações de profissionais): CRUD, tags livres, até 5 fotos, horários de funcionamento, moderação, e o **fluxo de consentimento** quando o profissional indicado é morador do condomínio (in-app + nudge WhatsApp), tudo atrás de feature flag.

**Architecture:** `package-by-feature` (`feature/tag/`, `feature/recommendation/`). Domínio rico (transições de estado em métodos da entidade), soft delete via `@SQLDelete`/`@SQLRestriction`, autorização autor-ou-moderador no service, fotos reusando `FileStorage`/`MagicBytesValidator` (bucket `recommendations`), tags M:N via `@ManyToMany`, consentimento via evento de domínio `@TransactionalEventListener(AFTER_COMMIT) + @Async` enfileirando WhatsApp na outbox do 2C. Controllers atrás de `@ConditionalOnProperty(app.feature.recommendations.enabled)`.

**Tech Stack:** Spring Boot 3.3.5, JPA/Hibernate 6, PostgreSQL (extensão `citext` já criada na V1), Flyway, MinIO, JUnit 5 + Mockito + AssertJ. Frontend: React + Vite + TS + shadcn/ui + Tailwind + axios + `browser-image-compression` + `date-fns-tz`.

**Spec base:** `docs/superpowers/specs/2026-06-05-plano3-classifieds-indicacoes-design.md` §3 (V16/V17), §5 (fase 3B). Endpoints na spec mestra §4.3 (linhas 606–632).

**Pré-requisitos confirmados no código (`main`):**
- `MinioProperties.bucketRecommendations = "recommendations"` + bucket provisionado em `MinioBootstrap`; `presignedTtlPhotosSeconds` (600); `MagicBytesValidator.isAcceptedForPhoto`.
- `PermissionCode.RECOMMENDATION_MODERATE` e `TAG_MANAGE` seedados.
- WhatsApp 2C: `WhatsAppOutboxService.enqueue(toPhone, WhatsAppTemplate, Map<String,Object>)` → entry; `markSent(id, now)` / `markFailed(id, reason, now)`; `WhatsAppNotificationClient.send(toPhone, template, data)`; `WhatsAppMessageRenderer.render(template, data)` (switch que lança `WhatsAppSendException` em campo ausente); `WhatsAppTemplate` enum; `PhoneNumberNormalizer`. Listener de referência: `feature/whatsapp/PasswordResetEventListener.java` (`@Component @Async @TransactionalEventListener(AFTER_COMMIT)`, helper de envio **sem** `@Transactional`).
- `User` tem `getGreetingName()`, `getPhone()`; `UserRepository extends JpaRepository<User,UUID>` (`findById`, `findByIdAndStatus`).
- `GlobalExceptionHandler` mapeia `IllegalStateException → 409`; padrão de exceção de negócio: ver `ClassifiedException` + `handleClassified` (switch code→status).
- `AuthenticatedUserPrincipal(userId, displayName, roles, authorities, unitId, isUnitMaster)`.

---

## File Structure

**Backend** (`backend/src/main/java/br/com/condominio/`):
- `feature/tag/Tag.java`, `TagRepository.java`, `TagService.java`, `TagController.java`, `dto/{TagView,CreateTagRequest}.java`
- `feature/recommendation/RecommendationStatus.java`, `Recommendation.java`, `RecommendationPhoto.java`, `RecommendationOpeningHours.java`
- `feature/recommendation/{RecommendationRepository,RecommendationPhotoRepository,RecommendationOpeningHoursRepository}.java`
- `feature/recommendation/RecommendationException.java`, `RecommendationService.java`, `RecommendationController.java`, `RecommendationProperties.java`
- `feature/recommendation/event/RecommendationConsentRequestedEvent.java`
- `feature/recommendation/dto/{CreateRecommendationRequest,UpdateRecommendationRequest,ResidentConsentRequest,OpeningHoursDto,RecommendationView,RecommendationPhotoView}.java`
- `feature/whatsapp/RecommendationConsentEventListener.java`
- Modify: `feature/whatsapp/WhatsAppTemplate.java` (+RECOMMENDATION_CONSENT), `feature/whatsapp/WhatsAppMessageRenderer.java` (+case), `shared/exception/GlobalExceptionHandler.java` (+handler), `application.yml` (+flag, +consent base url)
- Migrations: `db/migration/V16__tags.sql`, `db/migration/V17__recommendations.sql`

**Backend tests** (`backend/src/test/java/br/com/condominio/feature/`):
- `tag/TagServiceTest.java`
- `recommendation/RecommendationTest.java` (domínio), `recommendation/RecommendationServiceTest.java`
- `whatsapp/WhatsAppMessageRendererRecommendationTest.java` (template novo)

**Frontend** (`frontend/src/`):
- `features/recommendations/api/{recommendationsApi,tagsApi}.ts`
- `features/recommendations/pages/{RecommendationsListPage,RecommendationDetailPage,RecommendationFormPage,PendingConsentPage}.tsx`
- Modify: `router.tsx`

---

## Task 1: Branch + migrations V16/V17 + flag + properties

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__tags.sql`, `V17__recommendations.sql`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Branch**
```bash
git checkout -b feat/3b-recomendacoes
```

- [ ] **Step 2: V16 — tags.** Create `V16__tags.sql`:
```sql
-- flyway:transactional=true

-- Tags livres das indicações (ex.: "encanador"). Slug citext = case-insensitive nativo
-- (extensão citext criada na V1). Criação livre por morador; TAG_MANAGE modera/deleta.
CREATE TABLE tag (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        citext NOT NULL,
    label       varchar(80) NOT NULL,
    color       varchar(20),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);
CREATE UNIQUE INDEX uq_tag_slug ON tag (slug) WHERE deleted_at IS NULL;
```

- [ ] **Step 3: V17 — recommendations.** Create `V17__recommendations.sql`:
```sql
-- flyway:transactional=true

-- Indicações de profissionais. Soft delete. Quando o profissional é morador
-- (is_resident), entra como PENDING_RESIDENT_CONSENT até o morador aprovar.
CREATE TABLE recommendation (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint NOT NULL DEFAULT 0,
    service_name            varchar(120) NOT NULL,
    professional_name       varchar(120),
    phone                   varchar(20),
    is_resident             boolean NOT NULL DEFAULT false,
    resident_user_id        uuid REFERENCES "user" (id) ON DELETE RESTRICT,
    address_line            varchar(255),
    price_range             varchar(40),
    rating                  smallint,
    comment                 text,
    recommended_by_user_id  uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    status                  varchar(30) NOT NULL DEFAULT 'ACTIVE',
    resident_consent_at     timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    deleted_at              timestamptz,
    CONSTRAINT chk_reco_status CHECK (status IN ('ACTIVE','PENDING_RESIDENT_CONSENT','HIDDEN')),
    CONSTRAINT chk_reco_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reco_resident CHECK (is_resident = false OR resident_user_id IS NOT NULL)
);
CREATE INDEX idx_reco_status ON recommendation (status, created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_reco_resident_pending ON recommendation (resident_user_id, status)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_reco_author ON recommendation (recommended_by_user_id) WHERE deleted_at IS NULL;

CREATE TABLE recommendation_photo (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id uuid NOT NULL REFERENCES recommendation (id) ON DELETE RESTRICT,
    object_key        varchar(255) NOT NULL,
    content_type      varchar(80) NOT NULL,
    ordering          int NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at        timestamptz
);
-- ordering pode ter gaps após soft-delete; service normaliza (max+1). Índice único também
-- serve lookups por recommendation_id (prefixo à esquerda).
CREATE UNIQUE INDEX uq_reco_photo_ordering ON recommendation_photo (recommendation_id, ordering)
    WHERE deleted_at IS NULL;

-- M:N puro recommendation<->tag. Sem soft delete, CASCADE.
CREATE TABLE recommendation_tag (
    recommendation_id uuid NOT NULL REFERENCES recommendation (id) ON DELETE CASCADE,
    tag_id            uuid NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (recommendation_id, tag_id)
);

-- Horários de funcionamento. FK real, sem polimorfismo, sem soft delete (CASCADE).
CREATE TABLE recommendation_opening_hours (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    uuid NOT NULL REFERENCES recommendation (id) ON DELETE CASCADE,
    day_of_week smallint NOT NULL,
    opens_at    time,
    closes_at   time,
    notes       varchar(120),
    CONSTRAINT chk_reco_oh_dow CHECK (day_of_week BETWEEN 0 AND 6)
);
CREATE INDEX idx_reco_oh_owner ON recommendation_opening_hours (owner_id, day_of_week);
```

- [ ] **Step 4: Flag + consent base url em application.yml.** Sob `app:`, adicionar:
```yaml
  feature:
    recommendations:
      enabled: ${APP_FEATURE_RECOMMENDATIONS_ENABLED:false}
  recommendation:
    consent-base-url: ${APP_RECOMMENDATION_CONSENT_BASE_URL:http://localhost:5173/indicacoes/pendentes}
```
(Se o bloco `app.feature` já existir do 3A, só acrescente `recommendations:` ao lado de `classifieds:`.)

- [ ] **Step 5: Validar.** `cd backend && ./mvnw -q -DskipTests compile` → BUILD SUCCESS; confirmar V16/V17 seguem V15.

- [ ] **Step 6: Commit.**
```bash
git add backend/src/main/resources/db/migration/V16__tags.sql backend/src/main/resources/db/migration/V17__recommendations.sql backend/src/main/resources/application.yml
git commit -m "feat(3b): migrations V16 tags + V17 recommendations + flag"
```

---

## Task 2: Tag — entidade + service (TDD)

**Files:**
- Create: `feature/tag/Tag.java`, `TagRepository.java`, `TagService.java`, `dto/TagView.java`, `dto/CreateTagRequest.java`
- Test: `feature/tag/TagServiceTest.java`

- [ ] **Step 1: Entidade `Tag.java`** (soft delete, espelha o boilerplate de `feature/classified/ClassifiedPhoto.java` — mesmas anotações Lombok/`@SQLDelete`/`@SQLRestriction`/`@DynamicInsert`):
```java
package br.com.condominio.feature.tag;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "tag")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "slug"})
@SQLDelete(sql = "UPDATE tag SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Tag {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, columnDefinition = "citext")
  private String slug;

  @Column(nullable = false, length = 80)
  private String label;

  @Column(length = 20)
  private String color;

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

  public static Tag create(String slug, String label, String color) {
    Tag t = new Tag();
    t.slug = slug;
    t.label = label;
    t.color = color;
    return t;
  }
}
```

- [ ] **Step 2: `TagRepository.java`** (citext resolve case-insensitive, então `findBySlug` casa "Encanador"/"encanador"):
```java
package br.com.condominio.feature.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, UUID> {
  Optional<Tag> findBySlug(String slug);

  @Query("select t from Tag t where lower(t.slug) like lower(concat(:q, '%')) order by t.slug")
  List<Tag> searchBySlugPrefix(String q);
}
```

- [ ] **Step 3: DTOs.** `dto/TagView.java`:
```java
package br.com.condominio.feature.tag.dto;

import br.com.condominio.feature.tag.Tag;
import java.util.UUID;

public record TagView(UUID id, String slug, String label, String color) {
  public static TagView of(Tag t) {
    return new TagView(t.getId(), t.getSlug(), t.getLabel(), t.getColor());
  }
}
```
`dto/CreateTagRequest.java`:
```java
package br.com.condominio.feature.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTagRequest(
    @NotBlank @Size(max = 80) String slug,
    @Size(max = 80) String label,
    @Size(max = 20) String color) {}
```

- [ ] **Step 4: Teste do service (falhando).** `TagServiceTest.java`:
```java
package br.com.condominio.feature.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TagServiceTest {

  private TagRepository repo;
  private TagService service;

  @BeforeEach
  void setUp() {
    repo = mock(TagRepository.class);
    service = new TagService(repo);
    when(repo.save(any(Tag.class))).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void getOrCreate_existingSlug_reuses() {
    Tag existing = Tag.create("encanador", "Encanador", null);
    when(repo.findBySlug("encanador")).thenReturn(Optional.of(existing));
    Tag result = service.getOrCreate("Encanador", null);
    assertThat(result).isSameAs(existing);
    verify(repo, never()).save(any());
  }

  @Test
  void getOrCreate_newSlug_creates() {
    when(repo.findBySlug("eletricista")).thenReturn(Optional.empty());
    Tag result = service.getOrCreate("Eletricista", null);
    assertThat(result.getSlug()).isEqualTo("eletricista");
    assertThat(result.getLabel()).isEqualTo("Eletricista");
    verify(repo).save(any(Tag.class));
  }
}
```

- [ ] **Step 5: Rodar e ver falhar.** `cd backend && ./mvnw -q -Dtest=TagServiceTest test` → FAIL (TagService não existe).

- [ ] **Step 6: `TagService.java`.** Normaliza o slug (trim + lowercase; o `findBySlug` em citext casa qualquer caixa, mas persistimos minúsculo p/ consistência):
```java
package br.com.condominio.feature.tag;

import br.com.condominio.feature.tag.dto.TagView;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TagService {

  private final TagRepository repo;

  @Transactional
  public Tag getOrCreate(String rawSlug, String label) {
    String slug = normalize(rawSlug);
    return repo.findBySlug(slug)
        .orElseGet(() -> repo.save(Tag.create(slug, label == null || label.isBlank() ? defaultLabel(rawSlug) : label, null)));
  }

  public List<TagView> searchForAutocomplete(String q) {
    if (q == null || q.isBlank()) return List.of();
    return repo.searchBySlugPrefix(normalize(q)).stream().map(TagView::of).toList();
  }

  @Transactional
  public void delete(UUID id) {
    repo.findById(id).ifPresent(repo::delete);
  }

  private static String normalize(String s) {
    return s == null ? "" : s.trim().toLowerCase();
  }

  private static String defaultLabel(String raw) {
    String r = raw == null ? "" : raw.trim();
    return r.isEmpty() ? r : Character.toUpperCase(r.charAt(0)) + r.substring(1);
  }
}
```

- [ ] **Step 7: Rodar verde.** `cd backend && ./mvnw -q -Dtest=TagServiceTest test` → PASS (2 testes).

- [ ] **Step 8: Commit.**
```bash
git add backend/src/main/java/br/com/condominio/feature/tag/ backend/src/test/java/br/com/condominio/feature/tag/TagServiceTest.java
git commit -m "feat(3b): Tag entidade + TagService getOrCreate/autocomplete (TDD)"
```

---

## Task 3: TagController (atrás da flag)

**Files:** Create `feature/tag/TagController.java`.

- [ ] **Step 1: Controller** (gated por `app.feature.recommendations.enabled` — tags só existem para indicações; espelha o idioma de `feature/classified/ClassifiedController.java`):
```java
package br.com.condominio.feature.tag;

import br.com.condominio.feature.tag.dto.CreateTagRequest;
import br.com.condominio.feature.tag.dto.TagView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.recommendations.enabled", havingValue = "true")
public class TagController {

  private final TagService service;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<TagView> autocomplete(@RequestParam(name = "q", required = false) String q) {
    return service.searchForAutocomplete(q);
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<TagView> create(@Valid @RequestBody CreateTagRequest body) {
    Tag t = service.getOrCreate(body.slug(), body.label());
    return ResponseEntity.status(HttpStatus.CREATED).body(TagView.of(t));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('TAG_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
```

- [ ] **Step 2: Compilar.** `cd backend && ./mvnw -q compile` → BUILD SUCCESS.

- [ ] **Step 3: Commit.**
```bash
git add backend/src/main/java/br/com/condominio/feature/tag/TagController.java
git commit -m "feat(3b): TagController (autocomplete/criar/deletar) atrás de flag"
```

---

## Task 4: Recommendation — entidades + repos (TDD do domínio)

**Files:**
- Create: `feature/recommendation/RecommendationStatus.java`, `Recommendation.java`, `RecommendationPhoto.java`, `RecommendationOpeningHours.java`, `RecommendationRepository.java`, `RecommendationPhotoRepository.java`, `RecommendationOpeningHoursRepository.java`
- Test: `feature/recommendation/RecommendationTest.java`

- [ ] **Step 1: Teste do domínio (falhando).** `RecommendationTest.java`:
```java
package br.com.condominio.feature.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationTest {

  private Recommendation external() {
    return Recommendation.create(
        UUID.randomUUID(), "Pintor", "João", "11999990000", false, null, "Rua X", "R$80/h",
        5, "ótimo");
  }

  private Recommendation resident(UUID residentId) {
    return Recommendation.create(
        UUID.randomUUID(), "Pintor", "João", "11999990000", true, residentId, "Apto 101",
        "R$80/h", 5, "ótimo");
  }

  @Test
  void create_external_isActive() {
    assertThat(external().getStatus()).isEqualTo(RecommendationStatus.ACTIVE);
  }

  @Test
  void create_resident_isPendingConsent() {
    assertThat(resident(UUID.randomUUID()).getStatus())
        .isEqualTo(RecommendationStatus.PENDING_RESIDENT_CONSENT);
  }

  @Test
  void create_resident_withoutResidentId_throws() {
    assertThatThrownBy(
            () ->
                Recommendation.create(
                    UUID.randomUUID(), "Pintor", "João", "11999990000", true, null, "Apto",
                    "R$80/h", 5, "ok"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void consentByResident_fromPending_becomesActive() {
    Recommendation r = resident(UUID.randomUUID());
    r.consentByResident();
    assertThat(r.getStatus()).isEqualTo(RecommendationStatus.ACTIVE);
    assertThat(r.getResidentConsentAt()).isNotNull();
  }

  @Test
  void consentByResident_whenNotPending_throws() {
    Recommendation r = external();
    assertThatThrownBy(r::consentByResident).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void hide_setsHidden() {
    Recommendation r = external();
    r.hide();
    assertThat(r.getStatus()).isEqualTo(RecommendationStatus.HIDDEN);
  }

  @Test
  void hide_whenAlreadyHidden_throws() {
    Recommendation r = external();
    r.hide();
    assertThatThrownBy(r::hide).isInstanceOf(IllegalStateException.class);
  }
}
```

- [ ] **Step 2: Rodar e ver falhar.** `cd backend && ./mvnw -q -Dtest=RecommendationTest test` → FAIL.

- [ ] **Step 3: `RecommendationStatus.java`:**
```java
package br.com.condominio.feature.recommendation;

public enum RecommendationStatus {
  ACTIVE,
  PENDING_RESIDENT_CONSENT,
  HIDDEN
}
```

- [ ] **Step 4: `Recommendation.java`** (rica; tags via `@ManyToMany`):
```java
package br.com.condominio.feature.recommendation;

import br.com.condominio.feature.tag.Tag;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "recommendation")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "serviceName", "status"})
@SQLDelete(sql = "UPDATE recommendation SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Recommendation {

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  @Column(name = "service_name", nullable = false, length = 120)
  private String serviceName;

  @Column(name = "professional_name", length = 120)
  private String professionalName;

  @Column(length = 20)
  private String phone;

  @Column(name = "is_resident", nullable = false)
  private boolean resident;

  @Column(name = "resident_user_id")
  private UUID residentUserId;

  @Column(name = "address_line", length = 255)
  private String addressLine;

  @Column(name = "price_range", length = 40)
  private String priceRange;

  @Column private Short rating;

  @Column(columnDefinition = "text")
  private String comment;

  @Column(name = "recommended_by_user_id", nullable = false, updatable = false)
  private UUID recommendedByUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private RecommendationStatus status;

  @Column(name = "resident_consent_at")
  private Instant residentConsentAt;

  @ManyToMany
  @JoinTable(
      name = "recommendation_tag",
      joinColumns = @JoinColumn(name = "recommendation_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  private Set<Tag> tags = new HashSet<>();

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

  public static Recommendation create(
      UUID recommendedByUserId, String serviceName, String professionalName, String phone,
      boolean resident, UUID residentUserId, String addressLine, String priceRange,
      Integer rating, String comment) {
    if (resident && residentUserId == null) {
      throw new IllegalArgumentException("Indicação de morador exige residentUserId.");
    }
    Recommendation r = new Recommendation();
    r.recommendedByUserId = recommendedByUserId;
    r.serviceName = serviceName;
    r.professionalName = professionalName;
    r.phone = phone;
    r.resident = resident;
    r.residentUserId = residentUserId;
    r.addressLine = addressLine;
    r.priceRange = priceRange;
    r.rating = rating == null ? null : rating.shortValue();
    r.comment = comment;
    r.status = resident ? RecommendationStatus.PENDING_RESIDENT_CONSENT : RecommendationStatus.ACTIVE;
    return r;
  }

  public void edit(String serviceName, String professionalName, String phone, String addressLine,
      String priceRange, Integer rating, String comment) {
    this.serviceName = serviceName;
    this.professionalName = professionalName;
    this.phone = phone;
    this.addressLine = addressLine;
    this.priceRange = priceRange;
    this.rating = rating == null ? null : rating.shortValue();
    this.comment = comment;
  }

  public void replaceTags(Set<Tag> newTags) {
    this.tags.clear();
    this.tags.addAll(newTags);
  }

  public boolean isPendingConsent() {
    return status == RecommendationStatus.PENDING_RESIDENT_CONSENT;
  }

  public void consentByResident() {
    if (status != RecommendationStatus.PENDING_RESIDENT_CONSENT) {
      throw new IllegalStateException("Indicação não está aguardando consentimento.");
    }
    status = RecommendationStatus.ACTIVE;
    residentConsentAt = Instant.now();
  }

  public void hide() {
    if (status == RecommendationStatus.HIDDEN) {
      throw new IllegalStateException("Indicação já está oculta.");
    }
    status = RecommendationStatus.HIDDEN;
  }
}
```

- [ ] **Step 5: `RecommendationPhoto.java`** — espelha `feature/classified/ClassifiedPhoto.java` trocando: package `recommendation`, `@Table(name="recommendation_photo")`, `@SQLDelete`/`@SQLRestriction` em `recommendation_photo`, coluna `recommendation_id`, static `create(recommendationId, objectKey, contentType, ordering)`.

- [ ] **Step 6: `RecommendationOpeningHours.java`** (sem soft delete — CASCADE):
```java
package br.com.condominio.feature.recommendation;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "recommendation_opening_hours")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "dayOfWeek"})
public class RecommendationOpeningHours {

  @Id @GeneratedValue private UUID id;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private UUID ownerId;

  @Column(name = "day_of_week", nullable = false)
  private short dayOfWeek;

  @Column(name = "opens_at")
  private LocalTime opensAt;

  @Column(name = "closes_at")
  private LocalTime closesAt;

  @Column(length = 120)
  private String notes;

  public static RecommendationOpeningHours create(
      UUID ownerId, short dayOfWeek, LocalTime opensAt, LocalTime closesAt, String notes) {
    RecommendationOpeningHours h = new RecommendationOpeningHours();
    h.ownerId = ownerId;
    h.dayOfWeek = dayOfWeek;
    h.opensAt = opensAt;
    h.closesAt = closesAt;
    h.notes = notes;
    return h;
  }
}
```

- [ ] **Step 7: Repositórios.** `RecommendationPhotoRepository.java` — espelha `ClassifiedPhotoRepository` trocando `Classified`→`Recommendation`, métodos `findByRecommendationIdOrderByOrdering`, `countByRecommendationId`, `findByIdAndRecommendationId`, `@Query maxOrdering` (`where p.recommendationId = :id`). `RecommendationOpeningHoursRepository.java`:
```java
package br.com.condominio.feature.recommendation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationOpeningHoursRepository
    extends JpaRepository<RecommendationOpeningHours, UUID> {
  List<RecommendationOpeningHours> findByOwnerIdOrderByDayOfWeek(UUID ownerId);

  void deleteByOwnerId(UUID ownerId);
}
```
`RecommendationRepository.java`:
```java
package br.com.condominio.feature.recommendation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

  List<Recommendation> findByResidentUserIdAndStatus(UUID residentUserId, RecommendationStatus status);

  @Query(
      """
      select distinct r from Recommendation r
      left join r.tags t
      where r.status = br.com.condominio.feature.recommendation.RecommendationStatus.ACTIVE
        and (:tag is null or lower(t.slug) = lower(:tag))
        and (:residentOnly = false or r.resident = true)
        and (:search is null
             or lower(r.serviceName) like lower(concat('%', :search, '%'))
             or lower(r.professionalName) like lower(concat('%', :search, '%')))
      order by r.resident desc, r.rating desc nulls last, r.createdAt desc
      """)
  Page<Recommendation> search(String tag, boolean residentOnly, String search, Pageable pageable);
}
```

- [ ] **Step 8: Rodar verde.** `cd backend && ./mvnw -q -Dtest=RecommendationTest test` → PASS (7 testes).

- [ ] **Step 9: Commit.**
```bash
git add backend/src/main/java/br/com/condominio/feature/recommendation/ backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationTest.java
git commit -m "feat(3b): entidades Recommendation/Photo/OpeningHours + repos + domínio (TDD)"
```

---

## Task 5: DTOs + exceção + handler + properties + evento

**Files:**
- Create: `feature/recommendation/dto/{CreateRecommendationRequest,UpdateRecommendationRequest,ResidentConsentRequest,OpeningHoursDto,RecommendationView,RecommendationPhotoView}.java`
- Create: `feature/recommendation/RecommendationException.java`, `RecommendationProperties.java`
- Create: `feature/recommendation/event/RecommendationConsentRequestedEvent.java`
- Modify: `shared/exception/GlobalExceptionHandler.java`; main app class (habilitar `@ConfigurationPropertiesScan` já existe — confirmar) 

- [ ] **Step 1: `RecommendationException.java`** (espelha `ClassifiedException` — codes NOT_FOUND→404, FORBIDDEN→403, PHOTO_*/default→400):
```java
package br.com.condominio.feature.recommendation;

public class RecommendationException extends RuntimeException {
  private final String code;

  public RecommendationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
```

- [ ] **Step 2: Handler em `GlobalExceptionHandler.java`** — import + método (espelha `handleClassified`):
```java
import br.com.condominio.feature.recommendation.RecommendationException;
```
```java
  @ExceptionHandler(RecommendationException.class)
  public ResponseEntity<ApiError> handleRecommendation(RecommendationException ex) {
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

- [ ] **Step 3: `RecommendationProperties.java`** (espelha `PasswordResetProperties`):
```java
package br.com.condominio.feature.recommendation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.recommendation")
public class RecommendationProperties {
  private String consentBaseUrl = "http://localhost:5173/indicacoes/pendentes";

  public String buildConsentLink() {
    return consentBaseUrl;
  }
}
```
(Confirme que o projeto usa `@ConfigurationPropertiesScan` — `PasswordResetProperties`/`MinioProperties` já são descobertos assim; se houver registro explícito por `@EnableConfigurationProperties`, adicione `RecommendationProperties.class` lá.)

- [ ] **Step 4: Evento `event/RecommendationConsentRequestedEvent.java`:**
```java
package br.com.condominio.feature.recommendation.event;

import java.util.UUID;

/** Disparado após persistir uma indicação PENDING_RESIDENT_CONSENT. O listener async enfileira o
 * WhatsApp de consentimento para o morador indicado. */
public record RecommendationConsentRequestedEvent(
    UUID recommendationId,
    UUID residentUserId,
    String residentPhone,
    String residentGreetingName,
    String recommenderName,
    String serviceName) {}
```

- [ ] **Step 5: DTOs.**
`dto/OpeningHoursDto.java`:
```java
package br.com.condominio.feature.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalTime;

public record OpeningHoursDto(
    @Min(0) @Max(6) int dayOfWeek, LocalTime opensAt, LocalTime closesAt, String notes) {}
```
`dto/CreateRecommendationRequest.java`:
```java
package br.com.condominio.feature.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateRecommendationRequest(
    @NotBlank @Size(max = 120) String serviceName,
    @Size(max = 120) String professionalName,
    @Size(max = 20) String phone,
    boolean isResident,
    UUID residentUserId,
    @Size(max = 255) String addressLine,
    @Size(max = 40) String priceRange,
    @Min(1) @Max(5) Integer rating,
    String comment,
    List<String> tagSlugs,
    List<OpeningHoursDto> openingHours) {}
```
`dto/UpdateRecommendationRequest.java` — iguais campos editáveis (sem `isResident`/`residentUserId`, que não mudam após criação):
```java
package br.com.condominio.feature.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateRecommendationRequest(
    @NotBlank @Size(max = 120) String serviceName,
    @Size(max = 120) String professionalName,
    @Size(max = 20) String phone,
    @Size(max = 255) String addressLine,
    @Size(max = 40) String priceRange,
    @Min(1) @Max(5) Integer rating,
    String comment,
    List<String> tagSlugs,
    List<OpeningHoursDto> openingHours) {}
```
`dto/ResidentConsentRequest.java`:
```java
package br.com.condominio.feature.recommendation.dto;

public record ResidentConsentRequest(boolean approved) {}
```
`dto/RecommendationPhotoView.java` — igual ao `ClassifiedPhotoView`: `record RecommendationPhotoView(UUID id, int ordering, String contentType)`.
`dto/RecommendationView.java`:
```java
package br.com.condominio.feature.recommendation.dto;

import br.com.condominio.feature.recommendation.Recommendation;
import br.com.condominio.feature.recommendation.RecommendationStatus;
import br.com.condominio.feature.tag.dto.TagView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecommendationView(
    UUID id,
    String serviceName,
    String professionalName,
    String phone,
    boolean isResident,
    UUID residentUserId,
    String addressLine,
    String priceRange,
    Integer rating,
    String comment,
    UUID recommendedByUserId,
    RecommendationStatus status,
    Instant createdAt,
    List<TagView> tags,
    List<OpeningHoursDto> openingHours,
    List<RecommendationPhotoView> photos) {

  public static RecommendationView of(
      Recommendation r,
      List<TagView> tags,
      List<OpeningHoursDto> openingHours,
      List<RecommendationPhotoView> photos) {
    return new RecommendationView(
        r.getId(), r.getServiceName(), r.getProfessionalName(), r.getPhone(), r.isResident(),
        r.getResidentUserId(), r.getAddressLine(), r.getPriceRange(),
        r.getRating() == null ? null : r.getRating().intValue(), r.getComment(),
        r.getRecommendedByUserId(), r.getStatus(), r.getCreatedAt(), tags, openingHours, photos);
  }
}
```

- [ ] **Step 6: Compilar.** `cd backend && ./mvnw -q compile` → BUILD SUCCESS.

- [ ] **Step 7: Commit.**
```bash
git add backend/src/main/java/br/com/condominio/feature/recommendation/ backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java
git commit -m "feat(3b): DTOs + RecommendationException + properties + evento de consentimento"
```

---

## Task 6: RecommendationService — CRUD + tags + horários + consentimento (TDD)

**Files:**
- Create: `feature/recommendation/RecommendationService.java`
- Test: `feature/recommendation/RecommendationServiceTest.java`

Service deps: `RecommendationRepository repo`, `RecommendationPhotoRepository photoRepo`, `RecommendationOpeningHoursRepository hoursRepo`, `TagService tagService`, `UserRepository userRepo`, `FileStorage storage`, `MagicBytesValidator magicBytes`, `MinioProperties props`, `ApplicationEventPublisher events`.

- [ ] **Step 1: Teste (falhando).** `RecommendationServiceTest.java` (cobre: create external→ACTIVE sem evento; create resident→PENDING + publica evento; getById not found; update autor; update terceiro→FORBIDDEN; residentConsent approved→ACTIVE; residentConsent por não-residente sem moderate→FORBIDDEN; declined→soft delete; hide por moderador; pendingConsent lista). Mocks: repos, tagService, userRepo, storage, magicBytes, props (`new MinioProperties()`), events. Usa `ReflectionTestUtils` p/ setar id/status. Exemplo dos casos centrais:
```java
package br.com.condominio.feature.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.recommendation.dto.CreateRecommendationRequest;
import br.com.condominio.feature.recommendation.dto.RecommendationView;
import br.com.condominio.feature.recommendation.event.RecommendationConsentRequestedEvent;
import br.com.condominio.feature.tag.TagService;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class RecommendationServiceTest {

  private RecommendationRepository repo;
  private RecommendationPhotoRepository photoRepo;
  private RecommendationOpeningHoursRepository hoursRepo;
  private TagService tagService;
  private UserRepository userRepo;
  private FileStorage storage;
  private MagicBytesValidator magicBytes;
  private MinioProperties props;
  private ApplicationEventPublisher events;
  private RecommendationService service;

  private final UUID author = UUID.randomUUID();
  private final UUID stranger = UUID.randomUUID();
  private final UUID resident = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repo = mock(RecommendationRepository.class);
    photoRepo = mock(RecommendationPhotoRepository.class);
    hoursRepo = mock(RecommendationOpeningHoursRepository.class);
    tagService = mock(TagService.class);
    userRepo = mock(UserRepository.class);
    storage = mock(FileStorage.class);
    magicBytes = mock(MagicBytesValidator.class);
    props = new MinioProperties();
    events = mock(ApplicationEventPublisher.class);
    service =
        new RecommendationService(
            repo, photoRepo, hoursRepo, tagService, userRepo, storage, magicBytes, props, events);
    when(repo.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));
    when(photoRepo.findByRecommendationIdOrderByOrdering(any())).thenReturn(List.of());
    when(hoursRepo.findByOwnerIdOrderByDayOfWeek(any())).thenReturn(List.of());
  }

  private CreateRecommendationRequest req(boolean isResident, UUID residentId) {
    return new CreateRecommendationRequest(
        "Pintor", "João", "11999990000", isResident, residentId, "Rua X", "R$80/h", 5, "ok",
        List.of(), List.of());
  }

  private Recommendation persisted(UUID id, UUID authorId, RecommendationStatus status) {
    Recommendation r =
        Recommendation.create(
            authorId, "Pintor", "João", "11999990000", false, null, "Rua X", "R$80/h", 5, "ok");
    ReflectionTestUtils.setField(r, "id", id);
    ReflectionTestUtils.setField(r, "status", status);
    return r;
  }

  @Test
  void create_external_active_noEvent() {
    RecommendationView v = service.create(author, req(false, null));
    assertThat(v.status()).isEqualTo(RecommendationStatus.ACTIVE);
    verify(events, never()).publishEvent(any(RecommendationConsentRequestedEvent.class));
  }

  @Test
  void create_resident_pending_publishesEvent() {
    User u = mock(User.class);
    when(u.getPhone()).thenReturn("11988887777");
    when(u.getGreetingName()).thenReturn("Maria");
    when(userRepo.findById(resident)).thenReturn(Optional.of(u));
    RecommendationView v = service.create(author, req(true, resident));
    assertThat(v.status()).isEqualTo(RecommendationStatus.PENDING_RESIDENT_CONSENT);
    verify(events).publishEvent(any(RecommendationConsentRequestedEvent.class));
  }

  @Test
  void update_byStranger_forbidden() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    assertThatThrownBy(
            () ->
                service.update(
                    id, stranger, false,
                    new br.com.condominio.feature.recommendation.dto.UpdateRecommendationRequest(
                        "X", null, null, null, null, null, null, List.of(), List.of())))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
  }

  @Test
  void residentConsent_approve_activates() {
    UUID id = UUID.randomUUID();
    Recommendation r = persisted(id, author, RecommendationStatus.PENDING_RESIDENT_CONSENT);
    ReflectionTestUtils.setField(r, "residentUserId", resident);
    when(repo.findById(id)).thenReturn(Optional.of(r));
    service.residentConsent(id, resident, false, true);
    assertThat(r.getStatus()).isEqualTo(RecommendationStatus.ACTIVE);
  }

  @Test
  void residentConsent_decline_softDeletes() {
    UUID id = UUID.randomUUID();
    Recommendation r = persisted(id, author, RecommendationStatus.PENDING_RESIDENT_CONSENT);
    ReflectionTestUtils.setField(r, "residentUserId", resident);
    when(repo.findById(id)).thenReturn(Optional.of(r));
    service.residentConsent(id, resident, false, false);
    verify(repo).delete(r);
  }

  @Test
  void residentConsent_byOther_withoutModerate_forbidden() {
    UUID id = UUID.randomUUID();
    Recommendation r = persisted(id, author, RecommendationStatus.PENDING_RESIDENT_CONSENT);
    ReflectionTestUtils.setField(r, "residentUserId", resident);
    when(repo.findById(id)).thenReturn(Optional.of(r));
    assertThatThrownBy(() -> service.residentConsent(id, stranger, false, true))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
  }

  @Test
  void hide_byModerator() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    service.hide(id);
    verify(repo).save(any(Recommendation.class));
  }
}
```

- [ ] **Step 2: Rodar e ver falhar.** `cd backend && ./mvnw -q -Dtest=RecommendationServiceTest test` → FAIL.

- [ ] **Step 3: `RecommendationService.java`** (CRUD + tags + horários + consentimento; fotos vêm na Task 7). Pontos-chave: `create` resolve tags via `tagService.getOrCreate`, salva horários, e — se `isResident` — busca o `User` indicado, valida que existe e tem phone, e publica o evento; `view` monta tags + horários + (fotos via photoRepo). Autorização autor-ou-moderador igual ao 3A.
```java
package br.com.condominio.feature.recommendation;

import br.com.condominio.feature.recommendation.dto.*;
import br.com.condominio.feature.recommendation.event.RecommendationConsentRequestedEvent;
import br.com.condominio.feature.tag.Tag;
import br.com.condominio.feature.tag.TagService;
import br.com.condominio.feature.tag.dto.TagView;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

  private final RecommendationRepository repo;
  private final RecommendationPhotoRepository photoRepo;
  private final RecommendationOpeningHoursRepository hoursRepo;
  private final TagService tagService;
  private final UserRepository userRepo;
  private final FileStorage storage;
  private final MagicBytesValidator magicBytes;
  private final MinioProperties props;
  private final ApplicationEventPublisher events;

  @Transactional
  public RecommendationView create(UUID authorId, CreateRecommendationRequest req) {
    Recommendation r =
        Recommendation.create(
            authorId, req.serviceName(), req.professionalName(), req.phone(), req.isResident(),
            req.residentUserId(), req.addressLine(), req.priceRange(), req.rating(), req.comment());
    applyTags(r, req.tagSlugs());
    repo.save(r);
    replaceHours(r.getId(), req.openingHours());
    if (req.isResident()) {
      User resident =
          userRepo
              .findById(req.residentUserId())
              .orElseThrow(() -> new RecommendationException("NOT_FOUND", "Morador indicado não encontrado."));
      events.publishEvent(
          new RecommendationConsentRequestedEvent(
              r.getId(), resident.getId(), resident.getPhone(), resident.getGreetingName(),
              authorDisplay(authorId), r.getServiceName()));
    }
    return view(r);
  }

  // @Transactional nas leituras: view() acessa r.getTags() (@ManyToMany lazy); sem sessão aberta
  // daria LazyInitializationException. (Os testes com mock não pegam isso — a entidade mockada tem
  // tags em HashSet de memória; só explode com sessão real. Diferente do 3A, que não tinha @ManyToMany.)
  @Transactional
  public RecommendationView getById(UUID id) {
    return view(load(id));
  }

  @Transactional
  public Page<RecommendationView> list(String tag, boolean residentOnly, String search, Pageable pageable) {
    String t = (tag == null || tag.isBlank()) ? null : tag;
    String s = (search == null || search.isBlank()) ? null : search;
    return repo.search(t, residentOnly, s, pageable).map(this::view);
  }

  @Transactional
  public List<RecommendationView> pendingConsentFor(UUID residentUserId) {
    return repo.findByResidentUserIdAndStatus(residentUserId, RecommendationStatus.PENDING_RESIDENT_CONSENT)
        .stream().map(this::view).toList();
  }

  @Transactional
  public RecommendationView update(UUID id, UUID actorId, boolean canModerate, UpdateRecommendationRequest req) {
    Recommendation r = loadOwned(id, actorId, canModerate);
    r.edit(req.serviceName(), req.professionalName(), req.phone(), req.addressLine(),
        req.priceRange(), req.rating(), req.comment());
    applyTags(r, req.tagSlugs());
    repo.save(r);
    replaceHours(id, req.openingHours());
    return view(r);
  }

  @Transactional
  public void delete(UUID id, UUID actorId, boolean canModerate) {
    Recommendation r = loadOwned(id, actorId, canModerate);
    photoRepo.findByRecommendationIdOrderByOrdering(id).forEach(photoRepo::delete);
    repo.delete(r);
  }

  @Transactional
  public void hide(UUID id) {
    Recommendation r = load(id);
    r.hide();
    repo.save(r);
  }

  @Transactional
  public void residentConsent(UUID id, UUID actorId, boolean canModerate, boolean approved) {
    Recommendation r = load(id);
    boolean isTheResident = actorId.equals(r.getResidentUserId());
    if (!isTheResident && !canModerate) {
      throw new RecommendationException("FORBIDDEN", "Apenas o morador indicado pode responder.");
    }
    if (!r.isPendingConsent()) {
      throw new RecommendationException("INVALID_STATE", "Indicação não está aguardando consentimento.");
    }
    if (approved) {
      r.consentByResident();
      repo.save(r);
    } else {
      repo.delete(r); // recusa = soft delete (direito do titular)
    }
  }

  private void applyTags(Recommendation r, List<String> slugs) {
    Set<Tag> tags = new HashSet<>();
    if (slugs != null) {
      for (String slug : slugs) {
        if (slug != null && !slug.isBlank()) tags.add(tagService.getOrCreate(slug, null));
      }
    }
    r.replaceTags(tags);
  }

  private void replaceHours(UUID recommendationId, List<OpeningHoursDto> hours) {
    hoursRepo.deleteByOwnerId(recommendationId);
    if (hours == null) return;
    for (OpeningHoursDto h : hours) {
      hoursRepo.save(
          RecommendationOpeningHours.create(
              recommendationId, (short) h.dayOfWeek(), h.opensAt(), h.closesAt(), h.notes()));
    }
  }

  private String authorDisplay(UUID authorId) {
    return userRepo.findById(authorId).map(User::getGreetingName).orElse("Um morador");
  }

  private Recommendation load(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new RecommendationException("NOT_FOUND", "Indicação não encontrada."));
  }

  private Recommendation loadOwned(UUID id, UUID actorId, boolean canModerate) {
    Recommendation r = load(id);
    if (!r.getRecommendedByUserId().equals(actorId) && !canModerate) {
      throw new RecommendationException("FORBIDDEN", "Sem permissão sobre esta indicação.");
    }
    return r;
  }

  private RecommendationView view(Recommendation r) {
    List<TagView> tags = r.getTags().stream().map(TagView::of).toList();
    List<OpeningHoursDto> hours =
        hoursRepo.findByOwnerIdOrderByDayOfWeek(r.getId()).stream()
            .map(h -> new OpeningHoursDto(h.getDayOfWeek(), h.getOpensAt(), h.getClosesAt(), h.getNotes()))
            .toList();
    List<RecommendationPhotoView> photos =
        photoRepo.findByRecommendationIdOrderByOrdering(r.getId()).stream()
            .map(p -> new RecommendationPhotoView(p.getId(), p.getOrdering(), p.getContentType()))
            .toList();
    return RecommendationView.of(r, tags, hours, photos);
  }
}
```

- [ ] **Step 4: Rodar verde.** `cd backend && ./mvnw -q -Dtest=RecommendationServiceTest test` → PASS.

- [ ] **Step 5: Commit.**
```bash
git add backend/src/main/java/br/com/condominio/feature/recommendation/RecommendationService.java backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationServiceTest.java
git commit -m "feat(3b): RecommendationService CRUD + tags + horários + consentimento (TDD)"
```

---

## Task 7: RecommendationService — fotos (TDD)

**Files:** Modify `RecommendationService.java` + `RecommendationServiceTest.java`.

Espelha **exatamente** os métodos `addPhoto`/`removePhoto`/`photoUrl` de `feature/classified/ClassifiedService.java` (constantes `MAX_PHOTO_BYTES=1_048_576L`, `MAX_PHOTOS=5`; `addPhoto` **não** `@Transactional`; magic-bytes → tamanho → upload → save; bucket `props.getBucketRecommendations()`; ordering `photoRepo.maxOrdering(id)+1`; erros `RecommendationException` com codes `PHOTO_LIMIT`/`PHOTO_TYPE_INVALID`/`PHOTO_TOO_LARGE`/`PHOTO_READ_FAILED`/`PHOTO_UPLOAD_FAILED`/`NOT_FOUND`). Os métodos recebem `(UUID id, UUID actorId, boolean canModerate, MultipartFile file)` e usam `loadOwned`.

- [ ] **Step 1: Testes de foto (falhando)** — espelham os 5+2 testes de foto de `ClassifiedServiceTest` (over-limit, invalid-type, too-large, happy-path, photoUrl, removePhoto happy, removePhoto not-found), trocando tipos e `props.getBucketRecommendations()`.

- [ ] **Step 2: Rodar e ver falhar.**

- [ ] **Step 3: Implementar os 3 métodos** copiando de `ClassifiedService` e trocando: tipo `ClassifiedPhoto`→`RecommendationPhoto`, repo `photoRepo` (recommendation), `props.getBucketRecommendations()`, exceções `RecommendationException`, comentário do trade-off orphan/TOCTOU mantido.

- [ ] **Step 4: Rodar verde** `cd backend && ./mvnw -q -Dtest=RecommendationServiceTest test`.

- [ ] **Step 5: Commit.**
```bash
git add backend/src/main/java/br/com/condominio/feature/recommendation/RecommendationService.java backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationServiceTest.java
git commit -m "feat(3b): fotos da indicação (espelha 3A) (TDD)"
```

---

## Task 8: Consentimento — template WhatsApp + listener

**Files:**
- Modify: `feature/whatsapp/WhatsAppTemplate.java`, `feature/whatsapp/WhatsAppMessageRenderer.java`
- Create: `feature/whatsapp/RecommendationConsentEventListener.java`
- Test: `feature/whatsapp/WhatsAppMessageRendererRecommendationTest.java`

- [ ] **Step 1: Teste do renderer (falhando).** `WhatsAppMessageRendererRecommendationTest.java`:
```java
package br.com.condominio.feature.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatsAppMessageRendererRecommendationTest {

  private final WhatsAppMessageRenderer renderer = new WhatsAppMessageRenderer();

  @Test
  void consent_rendersAllVars() {
    String text =
        renderer.render(
            WhatsAppTemplate.RECOMMENDATION_CONSENT,
            Map.of(
                "greetingName", "Maria",
                "recommenderName", "João",
                "serviceName", "Pintor",
                "link", "https://app/indicacoes/pendentes"));
    assertThat(text).contains("Maria").contains("João").contains("Pintor")
        .contains("HELBOR TRILOGY HOME").contains("https://app/indicacoes/pendentes");
  }

  @Test
  void consent_missingField_throws() {
    assertThatThrownBy(
            () ->
                renderer.render(
                    WhatsAppTemplate.RECOMMENDATION_CONSENT, Map.of("greetingName", "Maria")))
        .isInstanceOf(WhatsAppSendException.class);
  }
}
```

- [ ] **Step 2: Rodar e ver falhar** (`RECOMMENDATION_CONSENT` não existe no enum).

- [ ] **Step 3: Adicionar ao enum `WhatsAppTemplate.java`:**
```java
  /** Pedido de consentimento ao morador indicado. Espera:
   * {@code {greetingName, recommenderName, serviceName, link}}. */
  RECOMMENDATION_CONSENT
```

- [ ] **Step 4: Adicionar o `case` em `WhatsAppMessageRenderer.render`** (copy PT-BR aprovada):
```java
      case RECOMMENDATION_CONSENT ->
          "Olá, "
              + req(d, "greetingName", template)
              + "! 👋\n\n"
              + req(d, "recommenderName", template)
              + " indicou você como "
              + req(d, "serviceName", template)
              + " no "
              + CONDO
              + ".\n\n"
              + "Você autoriza que essa indicação apareça para os moradores? Responda pelo link:\n"
              + req(d, "link", template)
              + "\n\n"
              + "Se não foi você ou não quer aparecer, é só recusar no link.";
```

- [ ] **Step 5: Rodar verde** o teste do renderer.

- [ ] **Step 6: Listener `RecommendationConsentEventListener.java`** (espelha `PasswordResetEventListener`; helper `sendAndRecord` **sem** `@Transactional`):
```java
package br.com.condominio.feature.whatsapp;

import br.com.condominio.feature.recommendation.RecommendationProperties;
import br.com.condominio.feature.recommendation.event.RecommendationConsentRequestedEvent;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationConsentEventListener {

  private final WhatsAppOutboxService outbox;
  private final WhatsAppNotificationClient client;
  private final RecommendationProperties props;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onConsentRequested(RecommendationConsentRequestedEvent e) {
    if (e.residentPhone() == null || e.residentPhone().isBlank()) {
      log.warn("Consent ignorado recommendationId={} sem phone do morador", e.recommendationId());
      return;
    }
    Map<String, Object> data =
        Map.of(
            "greetingName", e.residentGreetingName() == null ? "" : e.residentGreetingName(),
            "recommenderName", e.recommenderName() == null ? "" : e.recommenderName(),
            "serviceName", e.serviceName(),
            "link", props.buildConsentLink());
    WhatsAppOutboxEntry entry =
        outbox.enqueue(e.residentPhone(), WhatsAppTemplate.RECOMMENDATION_CONSENT, data);
    Instant now = Instant.now();
    try {
      client.send(e.residentPhone(), WhatsAppTemplate.RECOMMENDATION_CONSENT, data);
      outbox.markSent(entry.getId(), now);
    } catch (RuntimeException ex) {
      outbox.markFailed(entry.getId(), ex.getMessage(), now);
      log.warn("Falha ao enviar consentimento recommendationId={}", e.recommendationId());
    }
  }
}
```

- [ ] **Step 7: Suíte verde** `cd backend && ./mvnw -q test` (todos + renderer novo).

- [ ] **Step 8: Commit.**
```bash
git add backend/src/main/java/br/com/condominio/feature/whatsapp/ backend/src/test/java/br/com/condominio/feature/whatsapp/WhatsAppMessageRendererRecommendationTest.java
git commit -m "feat(3b): template RECOMMENDATION_CONSENT + listener WhatsApp do consentimento"
```

---

## Task 9: RecommendationController + flag + suíte verde

**Files:** Create `feature/recommendation/RecommendationController.java`.

- [ ] **Step 1: Controller** (gated por flag; `canModerate(me)` = `me.authorities().contains("RECOMMENDATION_MODERATE")`; fotos/CRUD espelham o `ClassifiedController`; novos: `pending-consent`, `resident-consent`, `hide`):
```java
package br.com.condominio.feature.recommendation;

import br.com.condominio.feature.recommendation.dto.*;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.recommendations.enabled", havingValue = "true")
public class RecommendationController {

  private final RecommendationService service;

  private static boolean canModerate(AuthenticatedUserPrincipal me) {
    return me.authorities().contains("RECOMMENDATION_MODERATE");
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public Page<RecommendationView> list(
      @RequestParam(required = false) String tag,
      @RequestParam(defaultValue = "false") boolean residentOnly,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    return service.list(tag, residentOnly, search, PageRequest.of(safePage, safeSize));
  }

  @GetMapping("/pending-consent")
  @PreAuthorize("isAuthenticated()")
  public List<RecommendationView> pendingConsent(@AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.pendingConsentFor(me.userId());
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public RecommendationView get(@PathVariable UUID id) {
    return service.getById(id);
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<RecommendationView> create(
      @Valid @RequestBody CreateRecommendationRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.userId(), body));
  }

  @PutMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public RecommendationView update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRecommendationRequest body,
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

  @PostMapping("/{id}/resident-consent")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> residentConsent(
      @PathVariable UUID id,
      @Valid @RequestBody ResidentConsentRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.residentConsent(id, me.userId(), canModerate(me), body.approved());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/hide")
  @PreAuthorize("hasAuthority('RECOMMENDATION_MODERATE')")
  public ResponseEntity<Void> hide(@PathVariable UUID id) {
    service.hide(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/photos")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<RecommendationPhotoView> addPhoto(
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
    return ResponseEntity.ok()
        .header("Referrer-Policy", "no-referrer")
        .body(Map.of("url", service.photoUrl(id, photoId)));
  }
}
```

- [ ] **Step 2: Suíte completa** `cd backend && ./mvnw -q test` → BUILD SUCCESS (todos os anteriores + 3B).

- [ ] **Step 3: Commit.**
```bash
git add backend/src/main/java/br/com/condominio/feature/recommendation/RecommendationController.java
git commit -m "feat(3b): RecommendationController (CRUD/consentimento/fotos) atrás de flag"
```

---

## Task 10: Frontend — API clients (tags + recommendations)

**Files:** Create `frontend/src/features/recommendations/api/tagsApi.ts` e `recommendationsApi.ts`. Espelha o estilo de `features/classifieds/api/classifiedsApi.ts` (axios `@/lib/api`, multipart no upload).

- [ ] **Step 1: `tagsApi.ts`:**
```ts
import { api } from '@/lib/api';

export interface Tag {
  id: string;
  slug: string;
  label: string;
  color: string | null;
}

export async function searchTags(q: string) {
  const r = await api.get('/tags', { params: { q } });
  return r.data as Tag[];
}

export async function createTag(slug: string, label?: string) {
  const r = await api.post('/tags', { slug, label });
  return r.data as Tag;
}
```

- [ ] **Step 2: `recommendationsApi.ts`:**
```ts
import { api } from '@/lib/api';
import type { Tag } from './tagsApi';

export type RecommendationStatus = 'ACTIVE' | 'PENDING_RESIDENT_CONSENT' | 'HIDDEN';

export interface OpeningHours {
  dayOfWeek: number;
  opensAt: string | null;
  closesAt: string | null;
  notes: string | null;
}

export interface RecommendationPhoto {
  id: string;
  ordering: number;
  contentType: string;
}

export interface Recommendation {
  id: string;
  serviceName: string;
  professionalName: string | null;
  phone: string | null;
  isResident: boolean;
  residentUserId: string | null;
  addressLine: string | null;
  priceRange: string | null;
  rating: number | null;
  comment: string | null;
  recommendedByUserId: string;
  status: RecommendationStatus;
  createdAt: string;
  tags: Tag[];
  openingHours: OpeningHours[];
  photos: RecommendationPhoto[];
}

export interface RecommendationPage {
  content: Recommendation[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface RecommendationFilters {
  tag?: string;
  residentOnly?: boolean;
  search?: string;
  page?: number;
  size?: number;
}

export async function listRecommendations(f: RecommendationFilters = {}) {
  const r = await api.get('/recommendations', { params: f });
  return r.data as RecommendationPage;
}

export async function getRecommendation(id: string) {
  const r = await api.get(`/recommendations/${id}`);
  return r.data as Recommendation;
}

export interface RecommendationBody {
  serviceName: string;
  professionalName?: string;
  phone?: string;
  isResident: boolean;
  residentUserId?: string | null;
  addressLine?: string;
  priceRange?: string;
  rating?: number | null;
  comment?: string;
  tagSlugs: string[];
  openingHours: OpeningHours[];
}

export async function createRecommendation(body: RecommendationBody) {
  const r = await api.post('/recommendations', body);
  return r.data as Recommendation;
}

export async function updateRecommendation(id: string, body: Omit<RecommendationBody, 'isResident' | 'residentUserId'>) {
  const r = await api.put(`/recommendations/${id}`, body);
  return r.data as Recommendation;
}

export async function deleteRecommendation(id: string) {
  await api.delete(`/recommendations/${id}`);
}

export async function listPendingConsent() {
  const r = await api.get('/recommendations/pending-consent');
  return r.data as Recommendation[];
}

export async function respondConsent(id: string, approved: boolean) {
  await api.post(`/recommendations/${id}/resident-consent`, { approved });
}

export async function hideRecommendation(id: string) {
  await api.post(`/recommendations/${id}/hide`);
}

export async function uploadRecommendationPhoto(id: string, file: File) {
  const form = new FormData();
  form.append('file', file);
  const r = await api.post(`/recommendations/${id}/photos`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data as RecommendationPhoto;
}

export async function deleteRecommendationPhoto(id: string, photoId: string) {
  await api.delete(`/recommendations/${id}/photos/${photoId}`);
}

export async function getRecommendationPhotoUrl(id: string, photoId: string) {
  const r = await api.get(`/recommendations/${id}/photos/${photoId}/url`);
  return (r.data as { url: string }).url;
}
```

- [ ] **Step 3: `npm run typecheck`** (de `frontend/`) → exit 0.

- [ ] **Step 4: Commit.**
```bash
git add frontend/src/features/recommendations/api/
git commit -m "feat(3b): API clients de tags e recommendations"
```

---

## Task 11: Frontend — páginas + rotas

**Files:**
- Create: `features/recommendations/pages/{RecommendationsListPage,RecommendationDetailPage,RecommendationFormPage,PendingConsentPage}.tsx`
- Modify: `frontend/src/router.tsx`

Seguir os padrões de `features/classifieds/pages/*` (mesma estrutura, shadcn, toasts `sonner`, compressão `browser-image-compression`, `useAuth().user` com `id`/`authorities`). PT-BR, mobile-first, touch ≥44px.

- [ ] **Step 1: `RecommendationsListPage.tsx`** — filtros (busca por texto, toggle "só moradores", input de tag com autocomplete via `searchTags`), lista via `listRecommendations`, badge "mora aqui" quando `isResident`, link "Nova indicação". Sort vem pronto do backend.

- [ ] **Step 2: `RecommendationDetailPage.tsx`** — carrega via `getRecommendation`; mostra serviço/profissional/telefone/preço/rating/comentário, tags, **horários** (renderizar `openingHours` por dia 0–6 em PT-BR com `date-fns-tz`/`America/Sao_Paulo` só para formatação de hora), fotos via `getRecommendationPhotoUrl`; ações editar/excluir quando `user.id === recommendedByUserId || authorities.includes('RECOMMENDATION_MODERATE')`; botão "Ocultar" só quando `authorities.includes('RECOMMENDATION_MODERATE')` (chama `hideRecommendation`).

- [ ] **Step 3: `RecommendationFormPage.tsx`** — criar/editar: campos do serviço; **toggle "é morador do condomínio"** → quando ligado, mostra um seletor de morador (campo `residentUserId`; em HML pode ser um input de UUID/busca simples — reusar endpoint de busca de usuário se existir, senão input de UUID com aviso); autocomplete de **tags** (multi, via `searchTags`/`createTag`); **editor de horários** (lista de 7 dias com opens/closes opcionais); upload de fotos (compressão ≤1MB, máx 5, remover) — igual ao `ClassifiedFormPage`. Ao criar indicação de morador, avisar via toast que ela fica "pendente de consentimento do morador".

- [ ] **Step 4: `PendingConsentPage.tsx`** — `listPendingConsent` → lista de indicações onde sou o morador indicado; cada item com botões **Autorizar** (`respondConsent(id, true)`) e **Recusar** (`respondConsent(id, false)`), com refresh após a ação e toasts.

- [ ] **Step 5: Rotas em `router.tsx`** (protegidas, como as do 3A):
```tsx
import { RecommendationsListPage } from '@/features/recommendations/pages/RecommendationsListPage';
import { RecommendationDetailPage } from '@/features/recommendations/pages/RecommendationDetailPage';
import { RecommendationFormPage } from '@/features/recommendations/pages/RecommendationFormPage';
import { PendingConsentPage } from '@/features/recommendations/pages/PendingConsentPage';
```
```tsx
  { path: '/indicacoes', element: (<ProtectedRoute><RecommendationsListPage /></ProtectedRoute>) },
  { path: '/indicacoes/nova', element: (<ProtectedRoute><RecommendationFormPage /></ProtectedRoute>) },
  { path: '/indicacoes/pendentes', element: (<ProtectedRoute><PendingConsentPage /></ProtectedRoute>) },
  { path: '/indicacoes/:id', element: (<ProtectedRoute><RecommendationDetailPage /></ProtectedRoute>) },
  { path: '/indicacoes/:id/editar', element: (<ProtectedRoute><RecommendationFormPage /></ProtectedRoute>) },
```
(Ordem importa: `/indicacoes/pendentes` antes de `/indicacoes/:id` para não casar "pendentes" como id.)

- [ ] **Step 6: Verificar** `cd frontend && npm run lint && npm run typecheck && npm run build` → tudo verde.

- [ ] **Step 7: Commit.**
```bash
git add frontend/src/features/recommendations/ frontend/src/router.tsx
git commit -m "feat(3b): frontend de indicações (lista, detalhe, form, consentimento)"
```

---

## Task 12: Deploy HML + e2e

- [ ] **Step 1: PR(s)/merge na `main`** (trunk-based; fatiar se >400 linhas — sugestão: backend tags+reco num PR, consentimento/WhatsApp noutro, frontend noutro).
- [ ] **Step 2: Ligar flag em HML** — `APP_FEATURE_RECOMMENDATIONS_ENABLED=true` (e `APP_RECOMMENDATION_CONSENT_BASE_URL` apontando pro front HML) no Dokploy backend-hml; redeploy; confirmar `/api/recommendations` e `/api/tags` respondem (não 404).
- [ ] **Step 3: e2e real em HML:**
  1. Criar indicação externa (não-morador) → 201 ACTIVE; aparece na listagem; filtrar por tag e por "só moradores".
  2. Criar indicação **de morador** (`isResident=true`, `residentUserId` de um morador com `phone_verified_at`) → 201 PENDING_RESIDENT_CONSENT; **WhatsApp de consentimento chega** ao morador indicado (outbox SENT); aparece em `GET /recommendations/pending-consent` do morador.
  3. Morador indicado `POST /resident-consent {approved:true}` → 204; indicação vira ACTIVE e aparece na listagem pública (moradores primeiro no sort).
  4. Outro fluxo: `{approved:false}` → 204; indicação some (soft delete).
  5. Fotos: upload JPG ok / PDF → 400 / 6ª → 400; presigned URL abre.
  6. Moderação: `RECOMMENDATION_MODERATE` consegue `PUT/DELETE`/`hide` em indicação alheia; terceiro sem permission → 403.
  7. Tags: criar tag livre; `TAG_MANAGE` deleta.
- [ ] **Step 4: Marcar `[x]`** e registrar "VALIDADO em HML em <data>" no topo; commit do doc.

---

## Self-review (cobertura vs spec §5 fase 3B)

- ✅ Entidades `tag`, `recommendation`, `recommendation_photo`, `recommendation_tag`, `recommendation_opening_hours` (V16/V17) — Task 1; soft delete onde exigido; M:N e opening_hours sem soft delete (CASCADE).
- ✅ Domínio rico: `consentByResident`, `hide`, invariante `is_resident ⇒ resident_user_id`, status inicial por `isResident` — Task 4.
- ✅ Tags criação livre (`getOrCreate` citext) + autocomplete + `TAG_MANAGE` delete — Tasks 2/3.
- ✅ CRUD + autor-ou-moderador + filtros (tag/residentOnly/search) + sort moradores-first — Task 6/9.
- ✅ Fluxo de consentimento: status PENDING, evento AFTER_COMMIT, template `RECOMMENDATION_CONSENT` + listener (outbox 2C), `pending-consent` (self), `resident-consent` {approved} (approve→ACTIVE / decline→soft delete) — Tasks 5/6/8/9.
- ✅ Fotos ≤5/≤1MB/magic-bytes/upload fora da tx (espelha 3A) — Task 7.
- ✅ Endpoints spec §4.3 (606–632) — Task 9.
- ✅ Frontend lista/detalhe/form (tags/horários/é-morador) + pendências de consentimento + rotas — Tasks 10/11.
- ✅ Feature flag default off; e2e HML antes de fechar — Tasks 1/12.

**Decisões herdadas do 3A a confirmar antes de prod:** link de navegação para `/indicacoes`; visibilidade de status na listagem (aqui a `search` já filtra só ACTIVE no backend, então HIDDEN/PENDING não vazam na listagem pública — diferente do 3A).
```
