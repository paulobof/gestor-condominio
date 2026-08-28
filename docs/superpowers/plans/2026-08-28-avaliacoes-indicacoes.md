# Avaliações das indicações — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada apartamento pode registrar uma avaliação por indicação — recomendo/não recomendo, nota de 1 a 5 e comentário obrigatório — e a lista passa a ordenar pelos melhores avaliados.

**Architecture:** Uma tabela nova `recommendation_review` substitui `recommendation_vote` e `recommendation_comment` (ambas vazias em produção). A cota é do apartamento, garantida por índice único parcial em `(recommendation_id, unit_id)`. Um serviço novo, `RecommendationReviewService`, carrega a lógica de avaliação e recalcula os contadores desnormalizados, mantendo `RecommendationService` (já com 14,7 KB) fora do caminho. O anonimato é aplicado na montagem do DTO, no backend.

**Tech Stack:** Java 21 / Spring Boot 3 / Hibernate 6 / Flyway / PostgreSQL 18; React + TypeScript + Vite + vitest + Playwright.

**Spec:** `docs/superpowers/specs/2026-08-28-avaliacoes-indicacoes-design.md`

## Global Constraints

- **Próximo número livre de migration: `V40`** (a última é `V39__remove_guest_owner_ownership_proof.sql`). Migration aplicada nunca é editada.
- **Cabeçalho obrigatório da migration:** `-- flyway:transactional=true`.
- **Soft delete obrigatório** em conteúdo, via `@SQLDelete` + `@SQLRestriction` (Hibernate 6). `@SQLRestriction` **não** filtra query nativa nem `JdbcTemplate`.
- **Lombok em entidade JPA:** proibido `@Data`. Usar `@Getter`, `@Setter(AccessLevel.PROTECTED)`, `@EqualsAndHashCode(of="id")`, `@ToString(onlyExplicitlyIncluded=true)`.
- **`@Transactional` só em service.** Nunca em controller, nunca em entidade.
- **Autorização por permission:** `@PreAuthorize("hasAuthority('<PERMISSION>')")`. Proibido `hasRole(...)`.
- **Nunca PII em log** (nome, e-mail, telefone). O texto de uma avaliação é conteúdo de morador — não logar.
- **Português na UI, inglês no código e nos dados.**
- **Comentário: mínimo 10 caracteres** após `trim`.
- **Score da ordenação:** `(rating_sum + 10.5) / (review_count + 3)` — prior 3,5 com peso 3.
- **`JAVA_HOME` obrigatório em todo comando Maven:** `JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7"`, senão o build morre com `release version 21 not supported`.
- **Rodar `npm run build` no frontend antes de commitar:** o vitest usa esbuild e não checa tipos.
- **Conventional Commits.** Proibido trailer `Co-Authored-By` e mencionar agente de IA como autor. Nunca `--no-verify`. npm only.

---

### Task 1: Migration, entidade e repositório

**Files:**
- Create: `backend/src/main/resources/db/migration/V40__recommendation_reviews.sql`
- Create: `backend/src/main/java/br/com/condominio/feature/recommendation/RecommendationReview.java`
- Create: `backend/src/main/java/br/com/condominio/feature/recommendation/ReviewSentiment.java`
- Create: `backend/src/main/java/br/com/condominio/feature/recommendation/ReviewStatus.java`
- Create: `backend/src/main/java/br/com/condominio/feature/recommendation/RecommendationReviewRepository.java`
- Test: `backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationReviewTest.java`

**Interfaces:**
- Produces: `RecommendationReview.create(UUID recommendationId, UUID unitId, UUID authorUserId, ReviewSentiment sentiment, short rating, String text)`; `edit(ReviewSentiment, short, String)`; `hide()`; getters `getId/getRecommendationId/getUnitId/getAuthorUserId/getSentiment/getRating/getText/getStatus/getCreatedAt`. Repository: `Optional<RecommendationReview> findByRecommendationIdAndUnitId(UUID, UUID)`, `List<RecommendationReview> findByRecommendationIdOrderByCreatedAtDesc(UUID)`, `Optional<RecommendationReview> findByIdAndRecommendationId(UUID, UUID)`.

- [ ] **Step 1: Escrever a migration**

```sql
-- flyway:transactional=true

-- Avaliações das indicações: uma por apartamento, com nota, sentimento e comentário obrigatório.
-- Substitui recommendation_vote e recommendation_comment (V35), ambas vazias em produção.

CREATE TABLE recommendation_review (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version           bigint NOT NULL DEFAULT 0,
    recommendation_id uuid NOT NULL REFERENCES recommendation (id) ON DELETE CASCADE,
    unit_id           uuid NOT NULL REFERENCES unit (id) ON DELETE RESTRICT,
    author_user_id    uuid NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    sentiment         varchar(10) NOT NULL,
    rating            smallint NOT NULL,
    text              text NOT NULL,
    status            varchar(10) NOT NULL DEFAULT 'ACTIVE',
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at        timestamptz,
    CONSTRAINT chk_review_sentiment CHECK (sentiment IN ('LIKE', 'DISLIKE')),
    CONSTRAINT chk_review_rating    CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_review_status    CHECK (status IN ('ACTIVE', 'HIDDEN')),
    CONSTRAINT chk_review_text      CHECK (length(btrim(text)) >= 10)
);

-- Cota do apartamento. PARCIAL de propósito: apagar a própria avaliação libera a vaga;
-- avaliação apenas OCULTA (moderada) continua ocupando, para o abuso não ser reescrito.
CREATE UNIQUE INDEX ux_review_unit ON recommendation_review (recommendation_id, unit_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_review_reco ON recommendation_review (recommendation_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- Contadores desnormalizados. Guarda a SOMA das notas, não a média: soma é inteiro exato,
-- média em coluna acumula erro de arredondamento a cada recálculo.
ALTER TABLE recommendation ADD COLUMN review_count integer NOT NULL DEFAULT 0;
ALTER TABLE recommendation ADD COLUMN rating_sum   integer NOT NULL DEFAULT 0;

-- A nota e o comentário do autor viram a primeira avaliação; as colunas saem da indicação.
ALTER TABLE recommendation DROP COLUMN rating;
ALTER TABLE recommendation DROP COLUMN comment;

DROP TABLE recommendation_vote;
DROP TABLE recommendation_comment;
```

- [ ] **Step 2: Escrever o teste da entidade (falha por não existir)**

Criar `RecommendationReviewTest.java`:

```java
package br.com.condominio.feature.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationReviewTest {

  private static final UUID RID = UUID.randomUUID();
  private static final UUID UNIT = UUID.randomUUID();
  private static final UUID AUTHOR = UUID.randomUUID();

  @Test
  void create_nasceAtivaEComOTextoAparado() {
    RecommendationReview r =
        RecommendationReview.create(
            RID, UNIT, AUTHOR, ReviewSentiment.LIKE, (short) 5, "  Serviço impecável.  ");

    assertThat(r.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
    assertThat(r.getText()).isEqualTo("Serviço impecável.");
    assertThat(r.getRecommendationId()).isEqualTo(RID);
    assertThat(r.getUnitId()).isEqualTo(UNIT);
    assertThat(r.getAuthorUserId()).isEqualTo(AUTHOR);
  }

  @Test
  void edit_trocaSentimentoNotaETexto() {
    RecommendationReview r =
        RecommendationReview.create(
            RID, UNIT, AUTHOR, ReviewSentiment.LIKE, (short) 5, "Serviço impecável.");

    r.edit(ReviewSentiment.DISLIKE, (short) 2, "Voltou e refez errado.");

    assertThat(r.getSentiment()).isEqualTo(ReviewSentiment.DISLIKE);
    assertThat(r.getRating()).isEqualTo((short) 2);
    assertThat(r.getText()).isEqualTo("Voltou e refez errado.");
  }

  @Test
  void hide_ocultaSemApagar() {
    RecommendationReview r =
        RecommendationReview.create(
            RID, UNIT, AUTHOR, ReviewSentiment.LIKE, (short) 5, "Serviço impecável.");

    r.hide();

    assertThat(r.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
  }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q -Dtest=RecommendationReviewTest`
Expected: FAIL de compilação — `RecommendationReview` não existe.

- [ ] **Step 4: Criar os dois enums**

`ReviewSentiment.java`:

```java
package br.com.condominio.feature.recommendation;

public enum ReviewSentiment {
  LIKE,
  DISLIKE
}
```

`ReviewStatus.java`:

```java
package br.com.condominio.feature.recommendation;

public enum ReviewStatus {
  ACTIVE,
  HIDDEN
}
```

- [ ] **Step 5: Criar a entidade**

Seguir o padrão de `RecommendationComment.java` (mesmo pacote) para anotações de soft delete e Lombok. Regras que valem aqui: **sem `@Data`**; usar `@Getter`, `@Setter(AccessLevel.PROTECTED)`, `@EqualsAndHashCode(of = "id")`, `@ToString(onlyExplicitlyIncluded = true)`; `@SQLDelete(sql = "UPDATE recommendation_review SET deleted_at = now() WHERE id = ?")` e `@SQLRestriction("deleted_at IS NULL")`; `@Version` no campo `version`; construtor protegido sem argumentos para o Hibernate.

Métodos de domínio (nada de setter público):

```java
public static RecommendationReview create(
    UUID recommendationId,
    UUID unitId,
    UUID authorUserId,
    ReviewSentiment sentiment,
    short rating,
    String text) {
  RecommendationReview r = new RecommendationReview();
  r.recommendationId = recommendationId;
  r.unitId = unitId;
  r.authorUserId = authorUserId;
  r.sentiment = sentiment;
  r.rating = rating;
  r.text = text.strip();
  r.status = ReviewStatus.ACTIVE;
  return r;
}

public void edit(ReviewSentiment sentiment, short rating, String text) {
  this.sentiment = sentiment;
  this.rating = rating;
  this.text = text.strip();
}

public void hide() {
  this.status = ReviewStatus.HIDDEN;
}
```

- [ ] **Step 6: Criar o repositório**

```java
package br.com.condominio.feature.recommendation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationReviewRepository
    extends JpaRepository<RecommendationReview, UUID> {

  Optional<RecommendationReview> findByRecommendationIdAndUnitId(UUID recommendationId, UUID unitId);

  List<RecommendationReview> findByRecommendationIdOrderByCreatedAtDesc(UUID recommendationId);

  Optional<RecommendationReview> findByIdAndRecommendationId(UUID id, UUID recommendationId);
}
```

- [ ] **Step 7: Rodar e ver passar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q -Dtest=RecommendationReviewTest`
Expected: PASS

- [ ] **Step 8: Suíte completa + spotless**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw spotless:check -q`
Expected: PASS nos dois. **Vai falhar** em tudo que ainda usa `recommendation.rating`/`comment` e as tabelas removidas — isso é esperado e é o trabalho da Task 5. Se a suíte quebrar apenas nesses pontos, seguir; anotar quais quebraram no relatório.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/db/migration/V40__recommendation_reviews.sql backend/src/main/java/br/com/condominio/feature/recommendation backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationReviewTest.java
git commit -m "feat(indicacoes): tabela e entidade de avaliacao"
```

---

### Task 2: Serviço de avaliação — criar, editar, apagar e contadores

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/recommendation/RecommendationReviewService.java`
- Create: `backend/src/main/java/br/com/condominio/feature/recommendation/dto/ReviewRequest.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/recommendation/Recommendation.java` (novo método de contadores)
- Test: `backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationReviewServiceTest.java`

**Interfaces:**
- Consumes: `RecommendationReview.create/edit/hide` e `RecommendationReviewRepository` da Task 1.
- Produces: `RecommendationReviewService.create(UUID recommendationId, UUID actorId, UUID actorUnitId, ReviewRequest req)`, `.edit(UUID recommendationId, UUID reviewId, UUID actorId, ReviewRequest req)`, `.delete(UUID recommendationId, UUID reviewId, UUID actorId)`, `.recountAndSave(Recommendation r)`. `Recommendation.updateReviewCounts(int likeCount, int dislikeCount, int reviewCount, int ratingSum)`.

- [ ] **Step 1: Escrever os testes do serviço**

Criar `RecommendationReviewServiceTest.java` com Mockito, no padrão de `RecommendationServiceTest.java`. Casos:

```java
@Test
void create_semUnidade_lancaForbidden() {
  assertThatThrownBy(() -> service.create(RID, ACTOR, null, req(ReviewSentiment.LIKE, 5, "Serviço muito bom.")))
      .isInstanceOf(RecommendationException.class)
      .extracting("code")
      .isEqualTo("REVIEW_REQUIRES_UNIT");
}

@Test
void create_apartamentoJaAvaliou_lancaErroDeDominio() {
  when(reviewRepo.findByRecommendationIdAndUnitId(RID, UNIT))
      .thenReturn(Optional.of(existingReview()));

  assertThatThrownBy(() -> service.create(RID, ACTOR, UNIT, req(ReviewSentiment.LIKE, 5, "Serviço muito bom.")))
      .isInstanceOf(RecommendationException.class)
      .extracting("code")
      .isEqualTo("REVIEW_UNIT_ALREADY_REVIEWED");
}

@Test
void create_atualizaOsQuatroContadores() {
  when(reviewRepo.findByRecommendationIdAndUnitId(RID, UNIT)).thenReturn(Optional.empty());
  when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
      .thenReturn(List.of(review(ReviewSentiment.LIKE, 5), review(ReviewSentiment.DISLIKE, 2)));

  service.create(RID, ACTOR, UNIT, req(ReviewSentiment.LIKE, 5, "Serviço muito bom."));

  assertThat(rec.getLikeCount()).isEqualTo(1);
  assertThat(rec.getDislikeCount()).isEqualTo(1);
  assertThat(rec.getReviewCount()).isEqualTo(2);
  assertThat(rec.getRatingSum()).isEqualTo(7);
}

@Test
void contadores_ignoramAvaliacaoOculta() {
  when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
      .thenReturn(List.of(review(ReviewSentiment.LIKE, 5), hiddenReview(ReviewSentiment.DISLIKE, 1)));

  service.recountAndSave(rec);

  assertThat(rec.getReviewCount()).isEqualTo(1);
  assertThat(rec.getRatingSum()).isEqualTo(5);
  assertThat(rec.getDislikeCount()).isZero();
}

@Test
void edit_deOutroAutor_lancaForbidden() {
  when(reviewRepo.findByIdAndRecommendationId(REVIEW_ID, RID))
      .thenReturn(Optional.of(reviewBy(OTHER_USER)));

  assertThatThrownBy(() -> service.edit(RID, REVIEW_ID, ACTOR, req(ReviewSentiment.LIKE, 4, "Mudei de ideia depois.")))
      .isInstanceOf(RecommendationException.class)
      .extracting("code")
      .isEqualTo("FORBIDDEN");
}

@Test
void delete_daPropriaAvaliacao_apagaERecontabiliza() {
  when(reviewRepo.findByIdAndRecommendationId(REVIEW_ID, RID))
      .thenReturn(Optional.of(reviewBy(ACTOR)));

  service.delete(RID, REVIEW_ID, ACTOR);

  verify(reviewRepo).delete(any(RecommendationReview.class));
  assertThat(rec.getReviewCount()).isZero();
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q -Dtest=RecommendationReviewServiceTest`
Expected: FAIL de compilação — o serviço não existe.

- [ ] **Step 3: Criar o DTO de entrada**

```java
package br.com.condominio.feature.recommendation.dto;

import br.com.condominio.feature.recommendation.ReviewSentiment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Avaliação de uma indicação: sentimento, nota e comentário — os três obrigatórios. */
public record ReviewRequest(
    @NotNull ReviewSentiment sentiment,
    @NotNull @Min(1) @Max(5) Integer rating,
    @NotBlank @Size(min = 10, max = 2000) String text) {}
```

- [ ] **Step 4: Adicionar o método de contadores na entidade `Recommendation`**

Substituir `updateVoteCounts(int, int)` (linha ~183) por:

```java
/** Contadores desnormalizados, recalculados a partir das avaliações ATIVAS. */
public void updateReviewCounts(int likeCount, int dislikeCount, int reviewCount, int ratingSum) {
  this.likeCount = likeCount;
  this.dislikeCount = dislikeCount;
  this.reviewCount = reviewCount;
  this.ratingSum = ratingSum;
}
```

E adicionar os campos `private int reviewCount;` e `private int ratingSum;` junto de `likeCount`/`dislikeCount` (linhas ~68-71).

- [ ] **Step 5: Criar o serviço**

Um `@Service` com `@RequiredArgsConstructor`, dependendo de `RecommendationRepository` e `RecommendationReviewRepository`. `@Transactional` em cada método público. O núcleo:

```java
/** Recalcula os quatro contadores a partir das avaliações ATIVAS. Oculta não conta. */
void recountAndSave(Recommendation r) {
  List<RecommendationReview> active =
      reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(r.getId()).stream()
          .filter(rev -> rev.getStatus() == ReviewStatus.ACTIVE)
          .toList();
  int likes = (int) active.stream().filter(rev -> rev.getSentiment() == ReviewSentiment.LIKE).count();
  int ratingSum = active.stream().mapToInt(RecommendationReview::getRating).sum();
  r.updateReviewCounts(likes, active.size() - likes, active.size(), ratingSum);
  repo.save(r);
}
```

`create` valida, nesta ordem: `actorUnitId != null` senão `REVIEW_REQUIRES_UNIT`; nenhuma avaliação viva do apartamento senão `REVIEW_UNIT_ALREADY_REVIEWED`; então salva e chama `recountAndSave`.

`edit` e `delete` carregam por `findByIdAndRecommendationId`, exigem `review.getAuthorUserId().equals(actorId)` senão `FORBIDDEN`, e chamam `recountAndSave` ao final.

- [ ] **Step 6: Rodar e ver passar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q -Dtest=RecommendationReviewServiceTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/recommendation backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationReviewServiceTest.java
git commit -m "feat(indicacoes): servico de avaliacao com cota por unidade e contadores"
```

---

### Task 3: Moderação e anonimato na leitura

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/recommendation/dto/ReviewView.java`
- Modify: `RecommendationReviewService.java` (métodos `list` e `hide`)
- Test: `backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationReviewServiceTest.java` (novos casos)

**Interfaces:**
- Consumes: tudo da Task 2.
- Produces: `ReviewView(UUID id, String sentiment, int rating, String text, Instant createdAt, boolean mine, String authorName, String unitCode)`; `RecommendationReviewService.list(UUID recommendationId, UUID actorId, UUID actorUnitId, boolean canModerate)`, `.hide(UUID recommendationId, UUID reviewId)`.

- [ ] **Step 1: Escrever os testes de anonimato e moderação**

```java
@Test
void list_paraQualquerUm_naoRevelaAutorNemApartamento() {
  when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
      .thenReturn(List.of(reviewBy(OTHER_USER)));

  List<ReviewView> views = service.list(RID, ACTOR, UNIT, false);

  assertThat(views).hasSize(1);
  assertThat(views.get(0).authorName()).isNull();
  assertThat(views.get(0).unitCode()).isNull();
  assertThat(views.get(0).text()).isEqualTo("Serviço muito bom.");
}

@Test
void list_paraModerador_revelaAutorEApartamento() {
  when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
      .thenReturn(List.of(reviewBy(OTHER_USER)));

  List<ReviewView> views = service.list(RID, ACTOR, UNIT, true);

  assertThat(views.get(0).authorName()).isEqualTo("Ana Moradora");
  assertThat(views.get(0).unitCode()).isEqualTo("402A");
}

@Test
void list_marcaAPropriaAvaliacaoComoMine() {
  when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
      .thenReturn(List.of(reviewBy(ACTOR)));

  assertThat(service.list(RID, ACTOR, UNIT, false).get(0).mine()).isTrue();
}

@Test
void list_paraVizinhoDoMesmoApartamento_revelaQuemAvaliou() {
  // O segundo morador precisa saber que a Ana avaliou, senao so ve "voce nao pode avaliar".
  when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
      .thenReturn(List.of(reviewBy(OTHER_USER))); // OTHER_USER mora na mesma UNIT

  List<ReviewView> views = service.list(RID, ACTOR, UNIT, false);

  assertThat(views.get(0).authorName()).isEqualTo("Ana Moradora");
  assertThat(views.get(0).mine()).isFalse();
}

@Test
void list_paraMoradorDeOutroApartamento_naoRevelaQuemAvaliou() {
  when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
      .thenReturn(List.of(reviewBy(OTHER_USER))); // avaliacao da UNIT

  List<ReviewView> views = service.list(RID, ACTOR, OUTRA_UNIT, false);

  assertThat(views.get(0).authorName()).isNull();
  assertThat(views.get(0).unitCode()).isNull();
}

@Test
void list_naoDevolveOcultas_paraQuemNaoModera() {
  when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
      .thenReturn(List.of(hiddenReview(ReviewSentiment.DISLIKE, 1)));

  assertThat(service.list(RID, ACTOR, UNIT, false)).isEmpty();
}

@Test
void hide_ocultaSemLiberarAVagaDoApartamento() {
  RecommendationReview r = reviewBy(OTHER_USER);
  when(reviewRepo.findByIdAndRecommendationId(REVIEW_ID, RID)).thenReturn(Optional.of(r));

  service.hide(RID, REVIEW_ID);

  assertThat(r.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
  verify(reviewRepo, never()).delete(any());
  assertThat(rec.getReviewCount()).isZero();
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q -Dtest=RecommendationReviewServiceTest`
Expected: FAIL — `ReviewView`, `list` e `hide` não existem.

- [ ] **Step 3: Criar o DTO de saída**

```java
package br.com.condominio.feature.recommendation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Avaliação como o app a exibe. {@code authorName} e {@code unitCode} vêm nulos — a avaliação é
 * anônima entre vizinhos. Duas exceções, e só elas:
 *
 * <ul>
 *   <li>quem tem {@code RECOMMENDATION_MODERATE}, para tratar um abuso com a pessoa em vez de só
 *       ocultar;
 *   <li>quem mora na MESMA unidade da avaliação — é assim que o segundo morador entende por que
 *       não pode avaliar ("seu apartamento já avaliou, por Ana"). Revelação dentro de casa, entre
 *       pessoas que já se conhecem, não é exposição a vizinho.
 * </ul>
 *
 * <p>O anonimato é decidido AQUI, no backend. Nunca mandar o nome e esconder no frontend.
 */
public record ReviewView(
    UUID id,
    String sentiment,
    int rating,
    String text,
    Instant createdAt,
    boolean mine,
    String authorName,
    String unitCode) {}
```

- [ ] **Step 4: Implementar `list` e `hide`**

`list` filtra `ACTIVE` quando `canModerate` é falso, marca `mine` comparando `authorUserId` com `actorId`, e preenche `authorName`/`unitCode` **somente** quando `canModerate` é verdadeiro — buscando os nomes em lote por `userRepo.findAllById(...)` e o código da unidade por `unitRepo`, no padrão que `RecommendationService.listComments` já usa.

`hide` carrega por `findByIdAndRecommendationId`, chama `review.hide()`, salva e chama `recountAndSave`.

- [ ] **Step 5: Rodar e ver passar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q -Dtest=RecommendationReviewServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/recommendation backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationReviewServiceTest.java
git commit -m "feat(indicacoes): moderacao de avaliacao e anonimato na leitura"
```

---

### Task 4: Endpoints novos e remoção de voto e comentário

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/recommendation/RecommendationController.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/recommendation/RecommendationService.java` (remover `vote`, `listComments`, `addComment`, `deleteComment` e as dependências `voteRepo`/`commentRepo`)
- Delete: `RecommendationVote.java`, `RecommendationVoteRepository.java`, `VoteValue.java`, `RecommendationComment.java`, `RecommendationCommentRepository.java`, `dto/VoteRequest.java`, `dto/CommentRequest.java`, `dto/CommentView.java`
- Modify: `backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationControllerWebTest.java`
- Modify: `backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationServiceTest.java`

**Interfaces:**
- Consumes: `RecommendationReviewService` das Tasks 2 e 3.
- Produces: as cinco rotas de `/api/recommendations/{id}/reviews`.

- [ ] **Step 1: Escrever os testes de contrato HTTP**

Em `RecommendationControllerWebTest.java`, apagar os testes de `/vote` e `/comments` e acrescentar:

```java
@Test
void createReview_semSessao_ehRejeitado() throws Exception {
  mvc.perform(
          post("/api/recommendations/{id}/reviews", RID)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"sentiment\":\"LIKE\",\"rating\":5,\"text\":\"Serviço muito bom.\"}"))
      .andExpect(status().is4xxClientError());
  verifyNoInteractions(reviewService);
}

@Test
void createReview_comentarioCurto_returns400() throws Exception {
  mvc.perform(
          post("/api/recommendations/{id}/reviews", RID)
              .with(MockAuth.userWithUnit(UID, UNIT_ID))
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"sentiment\":\"LIKE\",\"rating\":5,\"text\":\"ok\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  verifyNoInteractions(reviewService);
}

@Test
void createReview_notaForaDeEscala_returns400() throws Exception {
  mvc.perform(
          post("/api/recommendations/{id}/reviews", RID)
              .with(MockAuth.userWithUnit(UID, UNIT_ID))
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"sentiment\":\"LIKE\",\"rating\":9,\"text\":\"Serviço muito bom.\"}"))
      .andExpect(status().isBadRequest());
}

@Test
void listReviews_semSessao_ehPublico() throws Exception {
  when(reviewService.list(eq(RID), isNull(), isNull(), eq(false))).thenReturn(List.of());
  mvc.perform(get("/api/recommendations/{id}/reviews", RID)).andExpect(status().isOk());
}

@Test
void hideReview_semModerate_returns403() throws Exception {
  mvc.perform(post("/api/recommendations/{id}/reviews/{rev}/hide", RID, REVIEW_ID)
          .with(MockAuth.user(UID)))
      .andExpect(status().isForbidden());
  verifyNoInteractions(reviewService);
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q -Dtest=RecommendationControllerWebTest`
Expected: FAIL — as rotas não existem.

- [ ] **Step 3: Adicionar as rotas no controller**

```java
@GetMapping("/{id}/reviews")
public List<ReviewView> listReviews(
    @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
  boolean canModerate = me != null && me.authorities().contains("RECOMMENDATION_MODERATE");
  return reviewService.list(
      id, me == null ? null : me.userId(), me == null ? null : me.unitId(), canModerate);
}

@PostMapping("/{id}/reviews")
@PreAuthorize("isAuthenticated()")
@ResponseStatus(HttpStatus.CREATED)
public ReviewView createReview(
    @PathVariable UUID id,
    @Valid @RequestBody ReviewRequest req,
    @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
  return reviewService.create(id, me.userId(), me.unitId(), req);
}
```

`PUT /{id}/reviews/{reviewId}` e `DELETE /{id}/reviews/{reviewId}` com `@PreAuthorize("isAuthenticated()")`, e `POST /{id}/reviews/{reviewId}/hide` com `@PreAuthorize("hasAuthority('RECOMMENDATION_MODERATE')")`.

- [ ] **Step 4: Apagar voto e comentário**

```bash
cd backend/src/main/java/br/com/condominio/feature/recommendation
rm RecommendationVote.java RecommendationVoteRepository.java VoteValue.java
rm RecommendationComment.java RecommendationCommentRepository.java
rm dto/VoteRequest.java dto/CommentRequest.java dto/CommentView.java
```

Remover de `RecommendationService.java` os métodos `vote`, `listComments`, `addComment`, `deleteComment` e os campos `voteRepo`/`commentRepo`; do controller, as rotas `/vote` e `/comments`. Ajustar `RecommendationServiceTest.java` removendo os casos correspondentes.

- [ ] **Step 5: Rodar a suíte inteira**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw spotless:check -q`
Expected: PASS nos dois. Se algo ainda quebrar por `rating`/`comment` da indicação, é da Task 5.

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "feat(indicacoes): endpoints de avaliacao e remocao de voto/comentario"
```

---

### Task 5: Primeira avaliação na criação e ordenação por nota

**Files:**
- Modify: `RecommendationService.java` (método `create`, linha ~51)
- Modify: `RecommendationRepository.java` (o `order by` da query `search`)
- Modify: `dto/CreateRecommendationRequest.java`, `dto/UpdateRecommendationRequest.java`, `dto/RecommendationView.java`
- Modify: `Recommendation.java` (remover `rating`/`comment` e seus usos)
- Test: `RecommendationServiceTest.java`, `RecommendationControllerWebTest.java`

**Interfaces:**
- Consumes: `RecommendationReviewService.create(...)` da Task 2.
- Produces: `RecommendationView` sem `rating`/`comment` e com `reviewCount`, `ratingSum`.

- [ ] **Step 1: Escrever os testes**

Use a assinatura **real** de `RecommendationService.create` (linha ~51 do arquivo) — leia-a antes de
escrever o teste, não invente parâmetros. Os casos a cobrir, com os argumentos adaptados a ela:

```java
@Test
void create_comUnidade_criaIndicacaoEPrimeiraAvaliacao() {
  // ... chamar service.create(...) com actorId = ACTOR e unitId = UNIT, e o request trazendo
  // sentiment, rating e text preenchidos
  verify(reviewService).create(any(UUID.class), eq(ACTOR), eq(UNIT), any(ReviewRequest.class));
}

@Test
void create_semUnidade_criaIndicacaoSemAvaliacao() {
  // ... mesma chamada com unitId = null e o request sem sentiment/rating/text
  verifyNoInteractions(reviewService);
}

@Test
void create_comUnidadeMasSemAvaliacao_lancaErroDeDominio() {
  // morador tem unidade, entao nota e comentario sao obrigatorios
  assertThatThrownBy(() -> /* service.create(...) com unitId = UNIT e request sem os tres campos */)
      .isInstanceOf(RecommendationException.class)
      .extracting("code")
      .isEqualTo("REVIEW_REQUIRED");
}
```

E um teste de ordenação em `RecommendationRepository` (query JPQL) verificando que 12 avaliações 4,8★ vêm antes de 1 avaliação 5★ — como `RepositoryPostgresTest` não roda nesta máquina, cobrir isso por teste de serviço sobre uma lista já ordenada não prova nada; então **anotar no relatório** que essa ordenação só é validada pela verificação manual do Step 5.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q -Dtest=RecommendationServiceTest`
Expected: FAIL

- [ ] **Step 3: Ligar a criação da primeira avaliação**

Em `RecommendationService.create`, depois de salvar a indicação e ainda dentro da mesma transação: se `actorUnitId != null`, chamar `reviewService.create(saved.getId(), actorId, actorUnitId, new ReviewRequest(req.sentiment(), req.rating(), req.comment()))`. Se for nulo, não criar avaliação nenhuma.

`CreateRecommendationRequest` troca `rating`/`comment` por `sentiment`, `rating` e `text`, **todos opcionais no DTO** — a obrigatoriedade depende de o autor ter unidade, e isso é regra de domínio, não de validação de campo. Validar no service: com unidade e algum dos três nulo → `RecommendationException("REVIEW_REQUIRED", ...)`.

- [ ] **Step 4: Trocar a ordenação**

Em `RecommendationRepository.search`, substituir a linha do `order by` por:

```
order by r.resident desc,
         (r.ratingSum + 10.5) / (r.reviewCount + 3) desc,
         r.createdAt desc
```

O literal `10.5` é `double`, então a divisão promove e não há divisão inteira. Manter o comentário existente sobre `cast(:param as string)`, que continua valendo.

- [ ] **Step 5: Verificar a migration contra dump real**

`RepositoryPostgresTest` não roda nesta máquina (Testcontainers x Docker Desktop 29.3.1), então a `V40` chegaria ao deploy sem nunca ter rodado. Validar à mão, no servidor:

```bash
scp backend/src/main/resources/db/migration/V40__recommendation_reviews.sql ubuntu@201.23.72.49:/tmp/
ssh ubuntu@201.23.72.49
sudo docker run -d --name v40test -e POSTGRES_PASSWORD=t -e POSTGRES_USER=condominio \
  -e POSTGRES_DB=gestor_condominio postgres:18
gunzip -c ~/backup-pre-remocao-postgresshml-2026-08-28.sql.gz | \
  sudo docker exec -i v40test psql -q -U condominio -d gestor_condominio
sudo docker exec -i v40test psql -v ON_ERROR_STOP=1 -U condominio -d gestor_condominio < /tmp/V40.sql
sudo docker rm -f v40test
```

Conferir que a `V39` já está aplicada no dump usado; se o dump for anterior a ela, aplicar `V39` antes da `V40`. Reportar a saída completa.

- [ ] **Step 6: Suíte + spotless**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw spotless:check -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src
git commit -m "feat(indicacoes): primeira avaliacao na criacao e ordenacao por nota"
```

---

### Task 6: Frontend — cliente de API e tela de detalhe

**Files:**
- Modify: `frontend/src/features/recommendations/api/recommendationsApi.ts` e `.test.ts`
- Modify: `frontend/src/features/recommendations/pages/RecommendationDetailPage.tsx` e `.test.tsx`
- Create: `frontend/src/features/recommendations/components/ReviewForm.tsx` e `.test.tsx`
- Create: `frontend/src/features/recommendations/components/StarRating.tsx`

**Interfaces:**
- Consumes: as rotas da Task 4.
- Produces: `listReviews(id)`, `createReview(id, body)`, `updateReview(id, reviewId, body)`, `deleteReview(id, reviewId)`, `hideReview(id, reviewId)`; tipo `Review { id, sentiment, rating, text, createdAt, mine, authorName, unitCode }`.

- [ ] **Step 1: Escrever o teste do formulário**

`ReviewForm.test.tsx` cobrindo: botão de publicar desabilitado com menos de 10 caracteres; escolher estrelas altera o valor enviado; `onSubmit` recebe `{ sentiment, rating, text }`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/recommendations/components/ReviewForm.test.tsx`
Expected: FAIL — o componente não existe.

- [ ] **Step 3: Criar `StarRating` e `ReviewForm`**

`StarRating` é um grupo de 5 botões com `aria-label` "1 estrela"… "5 estrelas", `aria-pressed` no valor escolhido, e alvo de toque ≥44px (regra do projeto). `ReviewForm` junta os dois botões de sentimento, o `StarRating` e a textarea, e desabilita o envio enquanto o texto aparado tiver menos de 10 caracteres.

- [ ] **Step 4: Trocar os blocos na tela de detalhe**

Em `RecommendationDetailPage.tsx`, remover `handleVote`, `handleAddComment`, `loadComments` e os estados de comentário; no lugar, carregar `listReviews` e renderizar a lista mais o `ReviewForm`. Três estados possíveis: visitante → `promptLogin({ reason: 'Avaliar é para quem tem conta.' })`; morador cujo apartamento já avaliou → mensagem "Seu apartamento já avaliou, por Ana" com editar/apagar apenas se `mine`; morador sem avaliação → formulário. Cada avaliação é assinada "Morador do condomínio".

- [ ] **Step 5: Suíte + build**

Run: `cd frontend && npx vitest run && npm run build`
Expected: PASS nos dois.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/recommendations
git commit -m "feat(indicacoes): formulario de avaliacao na tela de detalhe"
```

---

### Task 7: Frontend — listagem e formulário de nova indicação

**Files:**
- Modify: `frontend/src/features/recommendations/pages/RecommendationsListPage.tsx` e `.test.tsx`
- Modify: `frontend/src/features/recommendations/pages/RecommendationFormPage.tsx` e `.test.tsx`

**Interfaces:**
- Consumes: o tipo `Recommendation` com `reviewCount`/`ratingSum` da Task 5.

- [ ] **Step 1: Escrever os testes**

Na listagem: card mostra `★ 4,3 · 12 avaliações` quando há avaliações, e "Sem avaliações" quando `reviewCount` é zero (nunca "★ 0,0", que sugere nota péssima). No formulário: com unidade, nota e comentário são obrigatórios e o envio bloqueia sem eles; sem unidade, os campos não aparecem.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/recommendations/pages`
Expected: FAIL

- [ ] **Step 3: Implementar**

Na listagem, calcular a média como `ratingSum / reviewCount` com uma casa decimal e vírgula decimal (`toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })`). No formulário de nova indicação, trocar os campos de nota/comentário atuais pelo mesmo `ReviewForm` embutido, exibido apenas quando o usuário tem unidade.

- [ ] **Step 4: Suíte + build**

Run: `cd frontend && npx vitest run && npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/recommendations
git commit -m "feat(indicacoes): media na listagem e avaliacao no cadastro"
```

---

### Task 8: E2E e verificação final

**Files:**
- Modify: `frontend/e2e/recommendations.spec.ts`

- [ ] **Step 1: Escrever o e2e**

Dois casos, com a API mockada por `page.route` (lembrar do gotcha do projeto: o matcher precisa casar só o pathname `/api/`, senão os módulos do Vite caem em 404 e a tela fica branca):

- visitante abre uma indicação, vê as avaliações assinadas "Morador do condomínio" e, ao clicar em avaliar, recebe o popup de login;
- morador com unidade preenche o formulário e a média do card muda.

- [ ] **Step 2: Rodar**

Run: `cd frontend && npm run test:e2e`
Expected: PASS

- [ ] **Step 3: Varredura por referências órfãs**

```bash
grep -rn "voteRecommendation\|listRecommendationComments\|addRecommendationComment\|deleteRecommendationComment\|VoteValue\|recommendation_vote\|recommendation_comment" backend/src frontend/src frontend/e2e
```

Expected: nada fora de migrations históricas (`V35`), que não se reescrevem.

- [ ] **Step 4: Verificação completa**

```bash
cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q
cd ../frontend && npx vitest run && npm run build && npm run test:e2e
```

Expected: PASS nos quatro.

- [ ] **Step 5: PARADA OBRIGATÓRIA — não dar push sem o OK do Paulo**

O push dispara autodeploy e roda a `V40`, que **dropa `recommendation_vote`, `recommendation_comment` e as colunas `rating`/`comment`**. Produção não tem indicações, então a perda real é nula, mas a operação é irreversível. Relatar o resultado da validação manual do Step 5 da Task 5 e aguardar autorização explícita.
