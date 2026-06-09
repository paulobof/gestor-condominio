# Informações Gerais Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir a feature de Contatos por uma aba **Informações** com seções livres (título + rich text), ordenadas pelo síndico; renomear o FAQ para "Perguntas Frequentes".

**Architecture:** Novo módulo backend `feature/info` (entidade `InfoSection`, soft delete, CRUD + reorder, sanitização de HTML com jsoup) espelhando o módulo `faq`. Frontend: componentes `RichTextEditor`/`RichTextView` (TipTap + DOMPurify) e feature `generalinfo` (página pública + admin). Remoção do módulo `contact` (back+front) e renomeação das rotas/rótulos do FAQ.

**Tech Stack:** Spring Boot 3 + JPA + Hibernate 6 (`@SQLDelete`/`@SQLRestriction`), jsoup (sanitização), Flyway; React + TypeScript + Vitest, TipTap, DOMPurify, shadcn/ui + Tailwind.

**Spec:** `docs/superpowers/specs/2026-06-09-informacoes-gerais-design.md`

**Branch:** `feat/informacoes-gerais` (já criada; spec já commitado).

**Convenções do repositório (CLAUDE.md):**
- Testes primeiro (TDD). PR coeso.
- Soft delete obrigatório; Lombok sem `@Data`; `@Transactional` só no service.
- Autorização por permission (`hasAuthority('INFO_MANAGE')`), nunca por role.
- Commits Conventional; hooks rodam (lint-staged + commitlint + pre-push). **Mensagem de commit: linhas do corpo ≤100 chars.** Não usar `--no-verify`.
- Backend = Maven (`./mvnw`). Frontend = npm.

**Comandos úteis:**
- Backend um teste: `cd backend && ./mvnw -q -Dtest=ClasseDeTeste test`
- Backend tudo: `cd backend && ./mvnw -q test`
- Frontend um arquivo: `cd frontend && npx vitest run src/caminho/Arquivo.test.tsx`
- Frontend tudo: `cd frontend && npm test -- --run`

---

## File Structure

**Backend — criar (`backend/src/main/java/br/com/condominio/`):**
- `feature/info/InfoSection.java` — entidade (soft delete).
- `feature/info/InfoSectionRepository.java` — queries (lista por position, max position).
- `feature/info/InfoSectionService.java` — CRUD + reorder + sanitização.
- `feature/info/InfoSectionController.java` — `/api/info-sections`, flag + permission.
- `feature/info/InfoException.java` — erro NOT_FOUND.
- `feature/info/dto/CreateInfoSectionRequest.java`, `UpdateInfoSectionRequest.java`, `ReorderInfoRequest.java`, `InfoSectionView.java`.
- `shared/html/HtmlSanitizer.java` — wrapper jsoup (safelist).
- `resources/db/migration/V22__general_info.sql` — cria `info_section`, dropa contact, permissão.

**Backend — modificar:**
- `feature/role/PermissionCode.java` — `+INFO_MANAGE`, `-CONTACT_MANAGE`.
- `shared/exception/GlobalExceptionHandler.java` — `+InfoException`, `-ContactException`.
- `resources/application.yml` — `+app.feature.generalinfo`, `-app.feature.contacts`.
- `pom.xml` — `+jsoup`.
- `deploy/dokploy-backend.env.example` — `+APP_FEATURE_GENERALINFO_ENABLED`, `-APP_FEATURE_CONTACTS_ENABLED`.

**Backend — remover (módulo contato):** todo `feature/contact/**` e `test/.../feature/contact/**`.

**Backend — testar (criar):**
- `test/.../feature/info/InfoSectionServiceTest.java`
- `test/.../feature/info/InfoSectionControllerWebTest.java`
- `test/.../shared/html/HtmlSanitizerTest.java`

**Frontend — criar (`frontend/src/`):**
- `components/richtext/RichTextEditor.tsx`, `RichTextView.tsx`, `RichTextView.test.tsx`.
- `features/generalinfo/api/generalInfoApi.ts`.
- `features/generalinfo/pages/InfoPage.tsx`, `InfoPage.test.tsx`, `InfoAdminPage.tsx`, `InfoAdminPage.test.tsx`.

**Frontend — modificar:**
- `components/layout/Sidebar.tsx` — item "Contatos"→"Informações"; "Informações"(FAQ)→"Perguntas Frequentes" `/faq`.
- `router.tsx` — rotas info novas; FAQ em `/faq`; remover rotas de contatos.
- `features/faq/pages/InformacoesPage.tsx` → renomear para `FaqPage.tsx` (rótulo/links `/faq`).
- `features/faq/pages/FaqAdminPage.tsx` — link de volta `/informacoes`→`/faq`.
- `features/faq/pages/InformacoesPage.test.tsx` → `FaqPage.test.tsx` (atualizar).

**Frontend — remover:** `features/contacts/**`.

---

## Phase A — Backend: módulo Informações

### Task A1: Migration V22 (schema + permissão)

**Files:**
- Create: `backend/src/main/resources/db/migration/V22__general_info.sql`

- [ ] **Step 1: Escrever a migration**

```sql
-- flyway:transactional=true

-- Informações Gerais: seções livres (título + corpo rich text sanitizado), ordem manual.
-- Leitura por qualquer autenticado; escrita só com INFO_MANAGE (síndico).

CREATE TABLE info_section (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version     bigint NOT NULL DEFAULT 0,
    title       varchar(120) NOT NULL,
    body        text NOT NULL,
    position    integer NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);
CREATE INDEX idx_info_section_position ON info_section (position) WHERE deleted_at IS NULL;

-- Nova permissão INFO_MANAGE (próximo id livre = 15) + grant ao síndico (role 1).
-- O grant em massa de MANAGER na V6 (SELECT 1, id FROM permission) já rodou antes desta
-- permissão existir, então é necessário o grant explícito aqui.
INSERT INTO permission (id, code, label) VALUES
    (15, 'INFO_MANAGE', 'Gerenciar informações gerais');
INSERT INTO role_permission (role_id, permission_id) VALUES (1, 15);

-- Remove o módulo de Contatos (feature recém-criada, flag off em prod; HML usa seed sintético).
DROP TABLE IF EXISTS contact_opening_hours;
DROP TABLE IF EXISTS contact;

-- Remove a permissão CONTACT_MANAGE (id 6) e seus grants — sai junto com o módulo.
DELETE FROM role_permission WHERE permission_id = 6;
DELETE FROM permission WHERE id = 6;
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/db/migration/V22__general_info.sql
git commit -m "feat(generalinfo): migration V22 (info_section, INFO_MANAGE, drop contact)"
```

---

### Task A2: Dependência jsoup + HtmlSanitizer (TDD)

**Files:**
- Modify: `backend/pom.xml` (seção `<dependencies>`)
- Create: `backend/src/main/java/br/com/condominio/shared/html/HtmlSanitizer.java`
- Test: `backend/src/test/java/br/com/condominio/shared/html/HtmlSanitizerTest.java`

- [ ] **Step 1: Adicionar jsoup ao pom**

Inserir dentro de `<dependencies>` (perto das demais, ex.: após o bloco `tika-core`):

```xml
        <dependency>
            <groupId>org.jsoup</groupId>
            <artifactId>jsoup</artifactId>
            <version>1.17.2</version>
        </dependency>
```

- [ ] **Step 2: Escrever o teste falho**

```java
package br.com.condominio.shared.html;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlSanitizerTest {

  private final HtmlSanitizer sanitizer = new HtmlSanitizer();

  @Test
  void removesScriptTags() {
    String dirty = "<p>Olá</p><script>alert('xss')</script>";
    assertThat(sanitizer.sanitize(dirty)).doesNotContain("script").contains("<p>Olá</p>");
  }

  @Test
  void removesEventHandlers() {
    String dirty = "<p onclick=\"steal()\">clique</p>";
    assertThat(sanitizer.sanitize(dirty)).doesNotContain("onclick");
  }

  @Test
  void keepsBasicFormattingAndSafeLinks() {
    String clean = "<p><strong>Portaria</strong></p><ul><li>24h</li></ul>"
        + "<a href=\"tel:+551130000000\">ligar</a>";
    String result = sanitizer.sanitize(clean);
    assertThat(result).contains("<strong>").contains("<ul>").contains("<li>").contains("href");
  }

  @Test
  void dropsJavascriptUrls() {
    String dirty = "<a href=\"javascript:alert(1)\">x</a>";
    assertThat(sanitizer.sanitize(dirty)).doesNotContain("javascript:");
  }
}
```

- [ ] **Step 3: Rodar o teste e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=HtmlSanitizerTest test`
Expected: FALHA na compilação (classe `HtmlSanitizer` não existe).

- [ ] **Step 4: Implementar o HtmlSanitizer**

```java
package br.com.condominio.shared.html;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Sanitiza HTML vindo do editor rich text antes de persistir (defesa primária contra XSS — STRIDE).
 * Permite só tags de formatação básica e links seguros (http/https/tel/mailto). O frontend ainda
 * sanitiza no render (DOMPurify) como defesa em profundidade.
 */
@Component
public class HtmlSanitizer {

  private final Safelist safelist =
      Safelist.none()
          .addTags("p", "br", "b", "strong", "i", "em", "u", "ul", "ol", "li", "a")
          .addAttributes("a", "href")
          .addProtocols("a", "href", "http", "https", "tel", "mailto");

  public String sanitize(String html) {
    if (html == null) {
      return null;
    }
    return Jsoup.clean(html, safelist);
  }
}
```

- [ ] **Step 5: Rodar o teste e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=HtmlSanitizerTest test`
Expected: PASS (4 testes).

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/java/br/com/condominio/shared/html/ \
  backend/src/test/java/br/com/condominio/shared/html/
git commit -m "feat(shared): HtmlSanitizer com jsoup safelist"
```

---

### Task A3: Entidade InfoSection + Repository

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/info/InfoSection.java`
- Create: `backend/src/main/java/br/com/condominio/feature/info/InfoSectionRepository.java`

- [ ] **Step 1: Criar a entidade**

```java
package br.com.condominio.feature.info;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Seção de informações gerais (título + corpo rich text sanitizado). Soft delete; ordem manual. */
@Entity
@Table(name = "info_section")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
@SQLDelete(sql = "UPDATE info_section SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class InfoSection {

  @Id @GeneratedValue @ToString.Include private UUID id;

  @Version private Long version;

  @ToString.Include
  @Column(nullable = false, length = 120)
  private String title;

  @Column(columnDefinition = "text", nullable = false)
  private String body;

  @Column(nullable = false)
  private int position;

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

  public static InfoSection create(String title, String body, int position) {
    InfoSection s = new InfoSection();
    s.title = title;
    s.body = body;
    s.position = position;
    return s;
  }

  public void edit(String title, String body) {
    this.title = title;
    this.body = body;
  }

  public void moveTo(int position) {
    this.position = position;
  }
}
```

- [ ] **Step 2: Criar o repositório**

```java
package br.com.condominio.feature.info;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InfoSectionRepository extends JpaRepository<InfoSection, UUID> {

  List<InfoSection> findAllByOrderByPositionAsc();

  @Query("select max(s.position) from InfoSection s")
  Integer findMaxPosition();
}
```

- [ ] **Step 3: Compilar**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/info/InfoSection.java \
  backend/src/main/java/br/com/condominio/feature/info/InfoSectionRepository.java
git commit -m "feat(generalinfo): entidade InfoSection e repository"
```

---

### Task A4: DTOs + InfoException

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/info/dto/CreateInfoSectionRequest.java`
- Create: `backend/src/main/java/br/com/condominio/feature/info/dto/UpdateInfoSectionRequest.java`
- Create: `backend/src/main/java/br/com/condominio/feature/info/dto/ReorderInfoRequest.java`
- Create: `backend/src/main/java/br/com/condominio/feature/info/dto/InfoSectionView.java`
- Create: `backend/src/main/java/br/com/condominio/feature/info/InfoException.java`

- [ ] **Step 1: CreateInfoSectionRequest**

```java
package br.com.condominio.feature.info.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInfoSectionRequest(
    @NotBlank @Size(max = 120) String title, @NotBlank String body) {}
```

- [ ] **Step 2: UpdateInfoSectionRequest**

```java
package br.com.condominio.feature.info.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateInfoSectionRequest(
    @NotBlank @Size(max = 120) String title, @NotBlank String body) {}
```

- [ ] **Step 3: ReorderInfoRequest** (espelha `ReorderFaqRequest`)

```java
package br.com.condominio.feature.info.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReorderInfoRequest(@NotEmpty List<Item> items) {
  public record Item(@NotNull UUID id, int position) {}
}
```

- [ ] **Step 4: InfoSectionView**

```java
package br.com.condominio.feature.info.dto;

import br.com.condominio.feature.info.InfoSection;
import java.time.Instant;
import java.util.UUID;

public record InfoSectionView(
    UUID id, String title, String body, int position, Instant updatedAt) {

  public static InfoSectionView of(InfoSection s) {
    return new InfoSectionView(
        s.getId(), s.getTitle(), s.getBody(), s.getPosition(), s.getUpdatedAt());
  }
}
```

- [ ] **Step 5: InfoException** (espelha `FaqException`)

```java
package br.com.condominio.feature.info;

/** Erros de Informações mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404, demais → 400. */
public class InfoException extends RuntimeException {

  private final String code;

  public InfoException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
```

- [ ] **Step 6: Compilar**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/info/
git commit -m "feat(generalinfo): DTOs e InfoException"
```

---

### Task A5: InfoSectionService (TDD)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/info/InfoSectionService.java`
- Test: `backend/src/test/java/br/com/condominio/feature/info/InfoSectionServiceTest.java`

- [ ] **Step 1: Escrever o teste falho**

```java
package br.com.condominio.feature.info;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.info.dto.CreateInfoSectionRequest;
import br.com.condominio.feature.info.dto.ReorderInfoRequest;
import br.com.condominio.feature.info.dto.UpdateInfoSectionRequest;
import br.com.condominio.shared.html.HtmlSanitizer;
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
class InfoSectionServiceTest {

  @Mock private InfoSectionRepository repo;
  @Mock private HtmlSanitizer sanitizer;
  @InjectMocks private InfoSectionService service;

  @BeforeEach
  void echoSanitizer() {
    lenient().when(sanitizer.sanitize(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void create_calculatesNextPosition_andSanitizesBody() {
    when(repo.findMaxPosition()).thenReturn(2);
    when(sanitizer.sanitize("<p>x</p><script>bad</script>")).thenReturn("<p>x</p>");
    when(repo.save(any(InfoSection.class))).thenAnswer(i -> i.getArgument(0));

    var view = service.create(new CreateInfoSectionRequest("Portaria", "<p>x</p><script>bad</script>"));

    assertThat(view.position()).isEqualTo(3);
    assertThat(view.body()).isEqualTo("<p>x</p>");
    assertThat(view.title()).isEqualTo("Portaria");
  }

  @Test
  void create_firstSection_positionZero() {
    when(repo.findMaxPosition()).thenReturn(null);
    when(repo.save(any(InfoSection.class))).thenAnswer(i -> i.getArgument(0));

    var view = service.create(new CreateInfoSectionRequest("Regras", "<p>r</p>"));

    assertThat(view.position()).isZero();
  }

  @Test
  void update_editsAndSanitizes() {
    UUID id = UUID.randomUUID();
    InfoSection existing = InfoSection.create("Old", "<p>old</p>", 0);
    when(repo.findById(id)).thenReturn(Optional.of(existing));
    when(sanitizer.sanitize("<p>new</p>")).thenReturn("<p>new</p>");

    var view = service.update(id, new UpdateInfoSectionRequest("New", "<p>new</p>"));

    assertThat(view.title()).isEqualTo("New");
    assertThat(view.body()).isEqualTo("<p>new</p>");
  }

  @Test
  void update_notFound_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(id, new UpdateInfoSectionRequest("a", "<p>b</p>")))
        .isInstanceOf(InfoException.class)
        .hasMessageContaining("não encontrada");
  }

  @Test
  void list_returnsOrderedByPosition() {
    when(repo.findAllByOrderByPositionAsc())
        .thenReturn(List.of(InfoSection.create("A", "<p>a</p>", 0), InfoSection.create("B", "<p>b</p>", 1)));

    var views = service.list();

    assertThat(views).extracting(v -> v.title()).containsExactly("A", "B");
  }

  @Test
  void reorder_appliesPositions() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    InfoSection s1 = InfoSection.create("A", "<p>a</p>", 0);
    InfoSection s2 = InfoSection.create("B", "<p>b</p>", 1);
    when(repo.findById(id1)).thenReturn(Optional.of(s1));
    when(repo.findById(id2)).thenReturn(Optional.of(s2));

    service.reorder(
        List.of(new ReorderInfoRequest.Item(id1, 1), new ReorderInfoRequest.Item(id2, 0)));

    assertThat(s1.getPosition()).isEqualTo(1);
    assertThat(s2.getPosition()).isEqualTo(0);
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=InfoSectionServiceTest test`
Expected: FALHA na compilação (`InfoSectionService` não existe).

- [ ] **Step 3: Implementar o service**

```java
package br.com.condominio.feature.info;

import br.com.condominio.feature.info.dto.CreateInfoSectionRequest;
import br.com.condominio.feature.info.dto.InfoSectionView;
import br.com.condominio.feature.info.dto.ReorderInfoRequest;
import br.com.condominio.feature.info.dto.UpdateInfoSectionRequest;
import br.com.condominio.shared.html.HtmlSanitizer;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Informações gerais: seções livres em rich text. Leitura por autenticados; escrita requer
 * {@code INFO_MANAGE} (checado no controller). O corpo é sanitizado na escrita (XSS — STRIDE).
 */
@Service
@RequiredArgsConstructor
public class InfoSectionService {

  private final InfoSectionRepository repo;
  private final HtmlSanitizer sanitizer;

  @Transactional(readOnly = true)
  public List<InfoSectionView> list() {
    return repo.findAllByOrderByPositionAsc().stream().map(InfoSectionView::of).toList();
  }

  @Transactional
  public InfoSectionView create(CreateInfoSectionRequest b) {
    Integer max = repo.findMaxPosition();
    int next = (max == null ? 0 : max + 1);
    InfoSection s = InfoSection.create(b.title(), sanitizer.sanitize(b.body()), next);
    return InfoSectionView.of(repo.save(s));
  }

  @Transactional
  public InfoSectionView update(UUID id, UpdateInfoSectionRequest b) {
    InfoSection s = find(id);
    s.edit(b.title(), sanitizer.sanitize(b.body()));
    return InfoSectionView.of(s);
  }

  @Transactional
  public void reorder(List<ReorderInfoRequest.Item> items) {
    for (ReorderInfoRequest.Item it : items) {
      find(it.id()).moveTo(it.position());
    }
  }

  @Transactional
  public void delete(UUID id) {
    repo.delete(find(id));
  }

  private InfoSection find(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new InfoException("NOT_FOUND", "Seção não encontrada."));
  }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=InfoSectionServiceTest test`
Expected: PASS (6 testes).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/info/InfoSectionService.java \
  backend/src/test/java/br/com/condominio/feature/info/InfoSectionServiceTest.java
git commit -m "feat(generalinfo): InfoSectionService com sanitizacao e reorder"
```

---

### Task A6: InfoSectionController (TDD — contrato web)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/info/InfoSectionController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/info/InfoSectionControllerWebTest.java`

- [ ] **Step 1: Escrever o teste falho** (espelha `FaqControllerWebTest`)

```java
package br.com.condominio.feature.info;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.info.dto.InfoSectionView;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = InfoSectionController.class,
    properties = "app.feature.generalinfo.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class InfoSectionControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID SID = UUID.randomUUID();
  private static final String MANAGE = "INFO_MANAGE";

  @Autowired private MockMvc mvc;
  @MockBean private InfoSectionService service;
  @MockBean private JwtService jwtService;

  private InfoSectionView view() {
    return new InfoSectionView(SID, "Portaria", "<p>24h</p>", 0, Instant.now());
  }

  @Test
  void list_authenticated_returns200() throws Exception {
    when(service.list()).thenReturn(List.of(view()));

    mvc.perform(get("/api/info-sections").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Portaria"));
  }

  @Test
  void list_anonymous_returns401() throws Exception {
    mvc.perform(get("/api/info-sections")).andExpect(status().isUnauthorized());
  }

  @Test
  void create_withoutManage_returns403() throws Exception {
    mvc.perform(
            post("/api/info-sections")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"body\":\"<p>b</p>\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void create_withManage_validBody_returns201() throws Exception {
    when(service.create(any())).thenReturn(view());

    mvc.perform(
            post("/api/info-sections")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Portaria\",\"body\":\"<p>24h</p>\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Portaria"));
  }

  @Test
  void create_blankTitle_returns400() throws Exception {
    mvc.perform(
            post("/api/info-sections")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"body\":\"<p>b</p>\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void update_notFound_returns404() throws Exception {
    when(service.update(eq(SID), any()))
        .thenThrow(new InfoException("NOT_FOUND", "Seção não encontrada."));

    mvc.perform(
            put("/api/info-sections/" + SID)
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"body\":\"<p>b</p>\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void delete_withManage_returns204() throws Exception {
    mvc.perform(delete("/api/info-sections/" + SID).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isNoContent());
  }

  @Test
  void reorder_withManage_returns204() throws Exception {
    mvc.perform(
            put("/api/info-sections/reorder")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":\"" + SID + "\",\"position\":0}]}"))
        .andExpect(status().isNoContent());
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=InfoSectionControllerWebTest test`
Expected: FALHA na compilação (`InfoSectionController` não existe).

- [ ] **Step 3: Implementar o controller** (espelha `FaqController`; `/reorder` antes de `/{id}`)

```java
package br.com.condominio.feature.info;

import br.com.condominio.feature.info.dto.CreateInfoSectionRequest;
import br.com.condominio.feature.info.dto.InfoSectionView;
import br.com.condominio.feature.info.dto.ReorderInfoRequest;
import br.com.condominio.feature.info.dto.UpdateInfoSectionRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Informações gerais do condomínio. Leitura para qualquer autenticado; escrita só com
 * {@code INFO_MANAGE} (síndico).
 */
@RestController
@RequestMapping("/api/info-sections")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.generalinfo.enabled", havingValue = "true")
public class InfoSectionController {

  private final InfoSectionService service;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<InfoSectionView> list() {
    return service.list();
  }

  @PostMapping
  @PreAuthorize("hasAuthority('INFO_MANAGE')")
  public ResponseEntity<InfoSectionView> create(@Valid @RequestBody CreateInfoSectionRequest body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  // /reorder ANTES de /{id} para "reorder" não ser capturado como path variable.
  @PutMapping("/reorder")
  @PreAuthorize("hasAuthority('INFO_MANAGE')")
  public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderInfoRequest body) {
    service.reorder(body.items());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('INFO_MANAGE')")
  public InfoSectionView update(
      @PathVariable UUID id, @Valid @RequestBody UpdateInfoSectionRequest body) {
    return service.update(id, body);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('INFO_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
```

- [ ] **Step 4: Adicionar o handler de InfoException** em `GlobalExceptionHandler.java`

Adicionar o import `import br.com.condominio.feature.info.InfoException;` e o método (junto aos demais handlers de feature):

```java
  @ExceptionHandler(InfoException.class)
  public ResponseEntity<ApiError> handleInfo(InfoException ex) {
    HttpStatus status =
        "NOT_FOUND".equals(ex.getCode()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
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

- [ ] **Step 5: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=InfoSectionControllerWebTest test`
Expected: PASS (8 testes).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/info/InfoSectionController.java \
  backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java \
  backend/src/test/java/br/com/condominio/feature/info/InfoSectionControllerWebTest.java
git commit -m "feat(generalinfo): InfoSectionController e handler de InfoException"
```

---

### Task A7: Flag, PermissionCode, env

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/br/com/condominio/feature/role/PermissionCode.java`
- Modify: `deploy/dokploy-backend.env.example`

- [ ] **Step 1: Adicionar a flag generalinfo** no `application.yml` (bloco `app.feature`, junto às outras)

Adicionar (manter `contacts` por enquanto — removido na Task A8):

```yaml
    generalinfo:
      enabled: ${APP_FEATURE_GENERALINFO_ENABLED:false}
```

- [ ] **Step 2: Adicionar INFO_MANAGE ao enum** `PermissionCode.java`

Adicionar `INFO_MANAGE,` ao enum (ex.: após `FAQ_MANAGE,`). NÃO remover `CONTACT_MANAGE` ainda (Task A8).

- [ ] **Step 3: Adicionar a var de ambiente** em `deploy/dokploy-backend.env.example`

Adicionar a linha:

```
APP_FEATURE_GENERALINFO_ENABLED=true
```

- [ ] **Step 4: Compilar**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/application.yml \
  backend/src/main/java/br/com/condominio/feature/role/PermissionCode.java \
  deploy/dokploy-backend.env.example
git commit -m "feat(generalinfo): feature flag, permission e env"
```

---

### Task A8: Remover o módulo Contatos (backend)

**Files:**
- Delete: `backend/src/main/java/br/com/condominio/feature/contact/**` (todo o diretório)
- Delete: `backend/src/test/java/br/com/condominio/feature/contact/**` (todo o diretório)
- Modify: `backend/src/main/java/br/com/condominio/shared/exception/GlobalExceptionHandler.java` (remover `ContactException`)
- Modify: `backend/src/main/java/br/com/condominio/feature/role/PermissionCode.java` (remover `CONTACT_MANAGE`)
- Modify: `backend/src/main/resources/application.yml` (remover bloco `contacts`)
- Modify: `deploy/dokploy-backend.env.example` (remover `APP_FEATURE_CONTACTS_ENABLED`)

- [ ] **Step 1: Apagar os diretórios do módulo contato**

```bash
git rm -r backend/src/main/java/br/com/condominio/feature/contact
git rm -r backend/src/test/java/br/com/condominio/feature/contact
```

- [ ] **Step 2: Remover o handler de ContactException** em `GlobalExceptionHandler.java`

Remover o import `import br.com.condominio.feature.contact.ContactException;` e o método `handleContact(...)` inteiro.

- [ ] **Step 3: Remover CONTACT_MANAGE** do enum `PermissionCode.java`.

- [ ] **Step 4: Remover o bloco `contacts`** do `application.yml` (as duas linhas `contacts:` / `enabled: ...`).

- [ ] **Step 5: Remover** a linha `APP_FEATURE_CONTACTS_ENABLED=...` de `deploy/dokploy-backend.env.example`.

- [ ] **Step 6: Verificar que nada mais referencia contato**

Run: `cd backend && grep -rn "contact\|Contact\|CONTACT_MANAGE" src/main src/test --include=*.java --include=*.yml --include=*.sql | grep -v "V20__contacts"`
Expected: nenhuma linha (V20 fica no histórico de migrations, intocada).

- [ ] **Step 7: Compilar + rodar a suíte de teste do backend inteira**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS, todos os testes passam (sem mais testes de contato).

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor(contacts): remove modulo Contatos do backend"
```

---

## Phase B — Frontend: componentes de rich text

### Task B1: Dependências (TipTap + DOMPurify)

**Files:**
- Modify: `frontend/package.json` (+ `package-lock.json`)

- [ ] **Step 1: Instalar**

Run:
```bash
cd frontend && npm install @tiptap/react @tiptap/starter-kit @tiptap/extension-link dompurify
```

- [ ] **Step 2: Verificar instalação**

Run: `cd frontend && node -e "require('@tiptap/react'); require('dompurify'); console.log('ok')"`
Expected: `ok`.

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "build(frontend): adiciona tiptap e dompurify"
```

---

### Task B2: RichTextView (TDD)

**Files:**
- Create: `frontend/src/components/richtext/RichTextView.tsx`
- Test: `frontend/src/components/richtext/RichTextView.test.tsx`

- [ ] **Step 1: Escrever o teste falho**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { RichTextView } from './RichTextView';

describe('RichTextView', () => {
  it('renderiza formatação básica', () => {
    render(<RichTextView html="<p><strong>Portaria</strong> 24h</p>" />);
    expect(screen.getByText('Portaria').tagName).toBe('STRONG');
  });

  it('remove script perigoso', () => {
    const { container } = render(
      <RichTextView html={'<p>ok</p><script>window.x=1</script>'} />
    );
    expect(container.querySelector('script')).toBeNull();
    expect(container.textContent).toContain('ok');
  });

  it('remove handlers inline', () => {
    const { container } = render(<RichTextView html={'<p onclick="x()">t</p>'} />);
    expect(container.querySelector('p')?.getAttribute('onclick')).toBeNull();
  });
});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/components/richtext/RichTextView.test.tsx`
Expected: FALHA (módulo `RichTextView` não existe).

- [ ] **Step 3: Implementar o RichTextView**

```tsx
import DOMPurify from 'dompurify';

const ALLOWED_TAGS = ['p', 'br', 'b', 'strong', 'i', 'em', 'u', 'ul', 'ol', 'li', 'a'];
const ALLOWED_ATTR = ['href', 'target', 'rel'];

/** Renderiza HTML de rich text sanitizado (defesa em profundidade; o backend já sanitiza). */
export function RichTextView({ html, className }: { html: string; className?: string }) {
  const clean = DOMPurify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR });
  return (
    <div
      className={className ?? 'prose prose-sm max-w-none text-sm text-foreground'}
      // eslint-disable-next-line react/no-danger
      dangerouslySetInnerHTML={{ __html: clean }}
    />
  );
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/components/richtext/RichTextView.test.tsx`
Expected: PASS (3 testes).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/richtext/RichTextView.tsx \
  frontend/src/components/richtext/RichTextView.test.tsx
git commit -m "feat(richtext): RichTextView com DOMPurify"
```

---

### Task B3: RichTextEditor (TipTap)

**Files:**
- Create: `frontend/src/components/richtext/RichTextEditor.tsx`

Nota: o editor TipTap é difícil de testar de forma estável em jsdom; a cobertura de comportamento fica nas páginas (onde o editor é mockado). Aqui só implementamos.

- [ ] **Step 1: Implementar o RichTextEditor**

```tsx
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import { useEffect } from 'react';
import { Bold, Italic, List, ListOrdered, Link as LinkIcon } from 'lucide-react';

interface Props {
  value: string;
  onChange: (html: string) => void;
}

const BTN =
  'inline-flex h-9 min-w-9 items-center justify-center rounded-md border border-border ' +
  'text-sm hover:bg-accent data-[active=true]:bg-accent data-[active=true]:text-accent-foreground';

/** Editor rich text mínimo (negrito, itálico, listas, link). Emite HTML em onChange. */
export function RichTextEditor({ value, onChange }: Props) {
  const editor = useEditor({
    extensions: [StarterKit, Link.configure({ openOnClick: false })],
    content: value,
    onUpdate: ({ editor }) => onChange(editor.getHTML()),
    editorProps: {
      attributes: {
        class:
          'prose prose-sm max-w-none min-h-[140px] rounded-md border border-input bg-background ' +
          'px-3 py-2 focus:outline-none focus-visible:ring-2 focus-visible:ring-ring',
      },
    },
  });

  // Sincroniza quando o valor externo muda (ex.: abrir outro item para editar).
  useEffect(() => {
    if (editor && value !== editor.getHTML()) {
      editor.commands.setContent(value, false);
    }
  }, [value, editor]);

  if (!editor) return null;

  const setLink = () => {
    const url = window.prompt('URL do link (https://, tel:, mailto:)') ?? '';
    if (url === '') {
      editor.chain().focus().unsetLink().run();
    } else {
      editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1" role="toolbar" aria-label="Formatação">
        <button
          type="button"
          aria-label="Negrito"
          className={BTN}
          data-active={editor.isActive('bold')}
          onClick={() => editor.chain().focus().toggleBold().run()}
        >
          <Bold className="h-4 w-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label="Itálico"
          className={BTN}
          data-active={editor.isActive('italic')}
          onClick={() => editor.chain().focus().toggleItalic().run()}
        >
          <Italic className="h-4 w-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label="Lista com marcadores"
          className={BTN}
          data-active={editor.isActive('bulletList')}
          onClick={() => editor.chain().focus().toggleBulletList().run()}
        >
          <List className="h-4 w-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label="Lista numerada"
          className={BTN}
          data-active={editor.isActive('orderedList')}
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
        >
          <ListOrdered className="h-4 w-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label="Link"
          className={BTN}
          data-active={editor.isActive('link')}
          onClick={setLink}
        >
          <LinkIcon className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
      <EditorContent editor={editor} />
    </div>
  );
}
```

- [ ] **Step 2: Type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/richtext/RichTextEditor.tsx
git commit -m "feat(richtext): RichTextEditor com TipTap"
```

---

## Phase C — Frontend: feature Informações

### Task C1: generalInfoApi

**Files:**
- Create: `frontend/src/features/generalinfo/api/generalInfoApi.ts`

- [ ] **Step 1: Implementar a api** (espelha `faqApi`)

```ts
import { api } from '@/lib/api';

export interface InfoSection {
  id: string;
  title: string;
  body: string;
  position: number;
  updatedAt: string;
}

export interface InfoSectionBody {
  title: string;
  body: string;
}

export async function listSections() {
  return (await api.get('/info-sections')).data as InfoSection[];
}

export async function createSection(b: InfoSectionBody) {
  return (await api.post('/info-sections', b)).data as InfoSection;
}

export async function updateSection(id: string, b: InfoSectionBody) {
  return (await api.put(`/info-sections/${id}`, b)).data as InfoSection;
}

export async function reorderSections(items: { id: string; position: number }[]) {
  await api.put('/info-sections/reorder', { items });
}

export async function deleteSection(id: string) {
  await api.delete(`/info-sections/${id}`);
}
```

- [ ] **Step 2: Type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/generalinfo/api/generalInfoApi.ts
git commit -m "feat(generalinfo): cliente de api"
```

---

### Task C2: InfoPage — página pública (TDD)

**Files:**
- Create: `frontend/src/features/generalinfo/pages/InfoPage.tsx`
- Test: `frontend/src/features/generalinfo/pages/InfoPage.test.tsx`

- [ ] **Step 1: Escrever o teste falho** (espelha `InformacoesPage.test.tsx` do FAQ)

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/generalInfoApi', () => ({ listSections: vi.fn() }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { InfoPage } from './InfoPage';
import { listSections } from '../api/generalInfoApi';
import { useAuth } from '@/features/auth/useAuth';

const section = {
  id: 's1',
  title: 'Portaria',
  body: '<p>Aberta 24h</p>',
  position: 0,
  updatedAt: '2026-06-09T00:00:00Z',
};

function renderPage() {
  return render(
    <MemoryRouter>
      <InfoPage />
    </MemoryRouter>
  );
}

describe('InfoPage', () => {
  beforeEach(() => {
    vi.mocked(listSections).mockResolvedValue([section]);
    vi.mocked(useAuth).mockReturnValue({ user: { authorities: [] } } as never);
  });

  it('lista as seções com título e corpo', async () => {
    renderPage();
    expect(await screen.findByText('Portaria')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Aberta 24h')).toBeInTheDocument());
  });

  it('sem INFO_MANAGE não mostra "Gerenciar"', async () => {
    renderPage();
    await screen.findByText('Portaria');
    expect(screen.queryByRole('link', { name: 'Gerenciar' })).toBeNull();
  });

  it('com INFO_MANAGE mostra "Gerenciar" apontando para /informacoes/gerenciar', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { authorities: ['INFO_MANAGE'] } } as never);
    renderPage();
    const link = await screen.findByRole('link', { name: 'Gerenciar' });
    expect(link).toHaveAttribute('href', '/informacoes/gerenciar');
  });
});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/generalinfo/pages/InfoPage.test.tsx`
Expected: FALHA (módulo `InfoPage` não existe).

- [ ] **Step 3: Implementar a InfoPage**

```tsx
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import { RichTextView } from '@/components/richtext/RichTextView';
import { listSections, type InfoSection } from '../api/generalInfoApi';

export function InfoPage() {
  const { user } = useAuth();
  const canManage = !!user && user.authorities.includes('INFO_MANAGE');
  const [items, setItems] = useState<InfoSection[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listSections()
      .then((data) => {
        if (active) setItems(data);
      })
      .catch(() => {
        if (active) toast.error('Erro ao carregar informações.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="flex items-center gap-2 text-2xl font-heading font-semibold">
          <span
            aria-hidden="true"
            className="inline-block h-6 w-1.5 rounded-full"
            style={{ backgroundColor: 'hsl(var(--brand-blue))' }}
          />
          Informações
        </h1>
        {canManage && (
          <Button asChild className="min-h-[44px]">
            <Link to="/informacoes/gerenciar">Gerenciar</Link>
          </Button>
        )}
      </div>

      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">Nenhuma informação publicada ainda.</p>
      ) : (
        <div className="space-y-4">
          {items.map((s) => (
            <section key={s.id} className="rounded-lg border border-border p-4">
              <h2 className="mb-2 text-lg font-heading font-semibold">{s.title}</h2>
              <RichTextView html={s.body} />
            </section>
          ))}
        </div>
      )}
    </main>
  );
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/features/generalinfo/pages/InfoPage.test.tsx`
Expected: PASS (3 testes).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/generalinfo/pages/InfoPage.tsx \
  frontend/src/features/generalinfo/pages/InfoPage.test.tsx
git commit -m "feat(generalinfo): pagina publica InfoPage"
```

---

### Task C3: InfoAdminPage — gestão (TDD)

**Files:**
- Create: `frontend/src/features/generalinfo/pages/InfoAdminPage.tsx`
- Test: `frontend/src/features/generalinfo/pages/InfoAdminPage.test.tsx`

O `RichTextEditor` é mockado no teste (substituído por um textarea simples), para o teste ficar determinístico sem TipTap no jsdom.

- [ ] **Step 1: Escrever o teste falho**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/generalInfoApi', () => ({
  listSections: vi.fn(),
  createSection: vi.fn(),
  updateSection: vi.fn(),
  reorderSections: vi.fn(),
  deleteSection: vi.fn(),
}));

// Substitui o editor TipTap por um textarea simples no teste.
vi.mock('@/components/richtext/RichTextEditor', () => ({
  RichTextEditor: ({ value, onChange }: { value: string; onChange: (v: string) => void }) => (
    <textarea aria-label="Conteúdo" value={value} onChange={(e) => onChange(e.target.value)} />
  ),
}));

import { InfoAdminPage } from './InfoAdminPage';
import {
  listSections,
  createSection,
  reorderSections,
} from '../api/generalInfoApi';

const a = { id: 'a', title: 'Portaria', body: '<p>p</p>', position: 0, updatedAt: '' };
const b = { id: 'b', title: 'Regras', body: '<p>r</p>', position: 1, updatedAt: '' };

function renderPage() {
  return render(
    <MemoryRouter>
      <InfoAdminPage />
    </MemoryRouter>
  );
}

describe('InfoAdminPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listSections).mockResolvedValue([a, b]);
    vi.mocked(createSection).mockResolvedValue({ ...a, id: 'new' });
    vi.mocked(reorderSections).mockResolvedValue(undefined);
  });

  it('cria uma seção chamando createSection', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Portaria');

    await user.type(screen.getByLabelText('Título'), 'Nova seção');
    await user.type(screen.getByLabelText('Conteúdo'), '<p>oi</p>');
    await user.click(screen.getByRole('button', { name: /salvar/i }));

    await waitFor(() =>
      expect(createSection).toHaveBeenCalledWith({ title: 'Nova seção', body: '<p>oi</p>' })
    );
  });

  it('reordena para baixo chamando reorderSections', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Portaria');

    // Botão "descer" da primeira seção troca posição com a segunda.
    await user.click(screen.getAllByRole('button', { name: '↓' })[0]);

    await waitFor(() =>
      expect(reorderSections).toHaveBeenCalledWith([
        { id: 'a', position: 1 },
        { id: 'b', position: 0 },
      ])
    );
  });
});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/generalinfo/pages/InfoAdminPage.test.tsx`
Expected: FALHA (módulo `InfoAdminPage` não existe).

- [ ] **Step 3: Implementar a InfoAdminPage**

```tsx
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RichTextEditor } from '@/components/richtext/RichTextEditor';
import {
  listSections,
  createSection,
  updateSection,
  reorderSections,
  deleteSection,
  type InfoSection,
} from '../api/generalInfoApi';

interface FormState {
  id?: string;
  title: string;
  body: string;
}

const EMPTY_FORM: FormState = { title: '', body: '' };

export function InfoAdminPage() {
  const [items, setItems] = useState<InfoSection[]>([]);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const reload = () => {
    setLoading(true);
    return listSections()
      .then((data) => setItems(data))
      .catch(() => toast.error('Erro ao carregar informações.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    reload();
  }, []);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (form.id) {
        await updateSection(form.id, { title: form.title, body: form.body });
        toast.success('Seção atualizada.');
      } else {
        await createSection({ title: form.title, body: form.body });
        toast.success('Seção criada.');
      }
      setForm(EMPTY_FORM);
      await reload();
    } catch {
      toast.error('Erro ao salvar a seção.');
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: string) => {
    try {
      await deleteSection(id);
      toast.success('Seção excluída.');
      await reload();
    } catch {
      toast.error('Erro ao excluir.');
    }
  };

  const swap = async (i: number, j: number) => {
    if (j < 0 || j >= items.length) return;
    const a = items[i];
    const b = items[j];
    try {
      await reorderSections([
        { id: a.id, position: b.position },
        { id: b.id, position: a.position },
      ]);
      await reload();
    } catch {
      toast.error('Erro ao reordenar.');
    }
  };

  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center gap-2">
        <Button asChild variant="ghost" size="icon" className="min-h-[44px] min-w-[44px]">
          <Link to="/informacoes" aria-label="Voltar">
            <ArrowLeft className="h-5 w-5" />
          </Link>
        </Button>
        <h1 className="text-2xl font-heading font-semibold">Gerenciar informações</h1>
      </div>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>{form.id ? 'Editar seção' : 'Nova seção'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div className="space-y-1">
              <Label htmlFor="title">Título</Label>
              <Input
                id="title"
                value={form.title}
                maxLength={120}
                onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
                required
              />
            </div>
            <div className="space-y-1">
              <Label>Conteúdo</Label>
              <RichTextEditor
                value={form.body}
                onChange={(html) => setForm((f) => ({ ...f, body: html }))}
              />
            </div>
            <div className="flex gap-2">
              <Button type="submit" disabled={saving} className="min-h-[44px]">
                {saving ? 'Salvando…' : 'Salvar'}
              </Button>
              {form.id && (
                <Button
                  type="button"
                  variant="outline"
                  className="min-h-[44px]"
                  onClick={() => setForm(EMPTY_FORM)}
                >
                  Cancelar
                </Button>
              )}
            </div>
          </form>
        </CardContent>
      </Card>

      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : (
        <ul className="space-y-2">
          {items.map((s, idx) => (
            <li
              key={s.id}
              className="flex items-center justify-between gap-3 rounded-lg border border-border p-3"
            >
              <span className="font-medium">{s.title}</span>
              <div className="flex gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  aria-label="↑"
                  className="min-h-[44px] min-w-[44px]"
                  disabled={idx === 0}
                  onClick={() => swap(idx, idx - 1)}
                >
                  ↑
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  aria-label="↓"
                  className="min-h-[44px] min-w-[44px]"
                  disabled={idx === items.length - 1}
                  onClick={() => swap(idx, idx + 1)}
                >
                  ↓
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  className="min-h-[44px]"
                  onClick={() => setForm({ id: s.id, title: s.title, body: s.body })}
                >
                  Editar
                </Button>
                <Button
                  type="button"
                  variant="destructive"
                  className="min-h-[44px]"
                  onClick={() => remove(s.id)}
                >
                  Excluir
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/features/generalinfo/pages/InfoAdminPage.test.tsx`
Expected: PASS (2 testes).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/generalinfo/pages/InfoAdminPage.tsx \
  frontend/src/features/generalinfo/pages/InfoAdminPage.test.tsx
git commit -m "feat(generalinfo): pagina de gestao InfoAdminPage"
```

---

## Phase D — Integração: router, sidebar, renomeação do FAQ, remoção de Contatos

### Task D1: Renomear FAQ → "Perguntas Frequentes" (/faq)

**Files:**
- Rename: `frontend/src/features/faq/pages/InformacoesPage.tsx` → `FaqPage.tsx`
- Rename: `frontend/src/features/faq/pages/InformacoesPage.test.tsx` → `FaqPage.test.tsx`
- Modify: `frontend/src/features/faq/pages/FaqAdminPage.tsx`

- [ ] **Step 1: Renomear os arquivos**

```bash
cd frontend
git mv src/features/faq/pages/InformacoesPage.tsx src/features/faq/pages/FaqPage.tsx
git mv src/features/faq/pages/InformacoesPage.test.tsx src/features/faq/pages/FaqPage.test.tsx
```

- [ ] **Step 2: Em `FaqPage.tsx`** — renomear o componente e ajustar rótulo/link:
  - `export function InformacoesPage()` → `export function FaqPage()`
  - heading `Informações` → `Perguntas Frequentes`
  - `<Link to="/informacoes/gerenciar">` → `<Link to="/faq/gerenciar">`

- [ ] **Step 3: Em `FaqPage.test.tsx`** — atualizar:
  - `import { InformacoesPage } from './InformacoesPage';` → `import { FaqPage } from './FaqPage';`
  - usos de `<InformacoesPage />` → `<FaqPage />`
  - `describe('InformacoesPage' ...)` → `describe('FaqPage' ...)`
  - o teste do link "Gerenciar": esperar `href` = `/faq/gerenciar` (era `/informacoes/gerenciar`)

- [ ] **Step 4: Em `FaqAdminPage.tsx`** — o link de voltar `<Link to="/informacoes">` → `<Link to="/faq">`.

- [ ] **Step 5: Rodar os testes do FAQ**

Run: `cd frontend && npx vitest run src/features/faq/`
Expected: PASS (FaqPage + FaqAdminPage).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/faq/
git commit -m "refactor(faq): renomeia aba para Perguntas Frequentes em /faq"
```

---

### Task D2: Router — rotas info novas, FAQ em /faq, remover contatos

**Files:**
- Modify: `frontend/src/router.tsx`

- [ ] **Step 1: Atualizar imports** no topo de `router.tsx`:
  - Remover: `import { InformacoesPage } from '@/features/faq/pages/InformacoesPage';`
  - Remover: `import { ContactsPage } from '@/features/contacts/pages/ContactsPage';`
  - Remover: `import { ContactsAdminPage } from '@/features/contacts/pages/ContactsAdminPage';`
  - Adicionar: `import { FaqPage } from '@/features/faq/pages/FaqPage';`
  - Adicionar: `import { InfoPage } from '@/features/generalinfo/pages/InfoPage';`
  - Adicionar: `import { InfoAdminPage } from '@/features/generalinfo/pages/InfoAdminPage';`
  - Manter: `import { FaqAdminPage } from '@/features/faq/pages/FaqAdminPage';`

- [ ] **Step 2: Atualizar as rotas** (as 4 linhas atuais `/informacoes*` + `/contatos*`) para:

```tsx
      { path: '/informacoes', element: <InfoPage /> },
      { path: '/informacoes/gerenciar', element: <InfoAdminPage /> },
      { path: '/faq', element: <FaqPage /> },
      { path: '/faq/gerenciar', element: <FaqAdminPage /> },
```

- [ ] **Step 3: Type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/router.tsx
git commit -m "feat(generalinfo): rotas de Informacoes e FAQ em /faq"
```

---

### Task D3: Sidebar — "Informações" (novo) + "Perguntas Frequentes" (FAQ), remove "Contatos"

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Possível: teste de sidebar (`Sidebar.test.tsx` / `App.test.tsx`) se referenciar os rótulos.

- [ ] **Step 1: Ajustar os ITEMS** em `Sidebar.tsx`:
  - O item atual `{ to: '/informacoes', label: 'Informações', icon: BookOpen, brand: 'blue' }` (que era o FAQ) passa a ser o **FAQ**: `{ to: '/faq', label: 'Perguntas Frequentes', icon: BookOpen, brand: 'blue' }`.
  - O item atual `{ to: '/contatos', label: 'Contatos', icon: Phone, brand: 'blue' }` passa a ser a **nova Informações**: `{ to: '/informacoes', label: 'Informações', icon: Info, brand: 'blue' }`.
  - Ordem sugerida: "Informações" antes de "Perguntas Frequentes".
  - Imports de ícones: remover `Phone` (não mais usado); adicionar `Info` à lista de `lucide-react`. Manter `BookOpen`.

  Resultado do array (trecho relevante):

```tsx
  { to: '/', label: 'Início', icon: Home, brand: 'ink', end: true },
  { to: '/avisos', label: 'Avisos', icon: Megaphone, brand: 'red' },
  { to: '/informacoes', label: 'Informações', icon: Info, brand: 'blue' },
  { to: '/faq', label: 'Perguntas Frequentes', icon: BookOpen, brand: 'blue' },
  { to: '/indicacoes', label: 'Indicações', icon: Lightbulb, brand: 'orange' },
  { to: '/classificados', label: 'Classificados', icon: ShoppingBag, brand: 'green' },
```

- [ ] **Step 2: Verificar testes que citam os rótulos da sidebar**

Run: `cd frontend && grep -rn "Contatos\|Informações\|Perguntas" src/components/layout src/App.test.tsx 2>/dev/null`
Expected: ajustar quaisquer asserções (ex.: se um teste espera "Contatos", trocar por "Informações"/"Perguntas Frequentes").

- [ ] **Step 3: Rodar os testes de layout/app**

Run: `cd frontend && npx vitest run src/components/layout src/App.test.tsx`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/layout/Sidebar.tsx frontend/src/App.test.tsx 2>/dev/null
git commit -m "feat(generalinfo): sidebar com Informacoes e Perguntas Frequentes"
```

---

### Task D4: Remover a feature Contatos (frontend)

**Files:**
- Delete: `frontend/src/features/contacts/**` (todo o diretório)

- [ ] **Step 1: Apagar o diretório**

```bash
cd frontend && git rm -r src/features/contacts
```

- [ ] **Step 2: Confirmar que nada referencia mais contatos**

Run: `cd frontend && grep -rn "features/contacts\|ContactsPage\|ContactsAdminPage\|contactsApi\|/contatos" src --include=*.ts --include=*.tsx`
Expected: nenhuma linha.

- [ ] **Step 3: Type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(contacts): remove feature Contatos do frontend"
```

---

## Phase E — Verificação final e integração

### Task E1: Suíte completa (back + front)

- [ ] **Step 1: Backend inteiro**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS, todos passam.

- [ ] **Step 2: Frontend inteiro**

Run: `cd frontend && npm test -- --run`
Expected: todos os arquivos passam.

- [ ] **Step 3: Se algo falhar**, corrigir e re-rodar antes de seguir. Não prosseguir com testes vermelhos.

---

### Task E2: Verificação manual (opcional, recomendada)

Pré-requisito: stack local de dev (ver memória `local-dev-stack`), com a flag `APP_FEATURE_GENERALINFO_ENABLED=true` no ambiente do backend dev. Login admin dev: `paulobof@gmail.com`.

- [ ] **Step 1:** Subir backend + frontend; logar como síndico/admin.
- [ ] **Step 2:** Abrir **Informações** na sidebar → criar uma seção "Portaria" com texto em negrito e uma lista; salvar; confirmar render formatado na página pública.
- [ ] **Step 3:** Criar uma segunda seção "Regras"; usar ↑/↓ para reordenar; recarregar e confirmar a ordem.
- [ ] **Step 4:** Confirmar que **Perguntas Frequentes** abre o FAQ em `/faq` e que **Contatos** não existe mais.
- [ ] **Step 5:** Logar como morador comum → ver Informações sem botão "Gerenciar".

---

### Task E3: Integração na main

- [ ] **Step 1: Revisão de código** (recomendado): usar `superpowers:requesting-code-review` ou `/code-review` sobre o diff da branch.
- [ ] **Step 2: Finalizar a branch** com `superpowers:finishing-a-development-branch` (merge `--no-ff` em `main`, push). O `pre-push` roda back+front; não usar `--no-verify`.
- [ ] **Step 3: Deploy/flag HML:** ligar `APP_FEATURE_GENERALINFO_ENABLED=true` e remover `APP_FEATURE_CONTACTS_ENABLED` no Dokploy (ver memória `hml-feature-flags`). A migration V22 roda no deploy.

---

## Self-Review (cobertura do spec)

- **Substituir Contatos** → Tasks A1 (drop tabelas/permissão), A8 (backend), D4 (frontend). ✓
- **Seções livres (título + rich text)** → A3 (entidade), B2/B3 (componentes), C2/C3 (páginas). ✓
- **Editor rich text** → B3 (TipTap), com render sanitizado B2 (DOMPurify). ✓
- **Ordem manual** → A5/A6 (reorder backend), C3 (↑/↓ frontend). ✓
- **Sanitização dupla (jsoup + DOMPurify)** → A2 (jsoup), B2 (DOMPurify). ✓
- **Flag `generalinfo` + permissão `INFO_MANAGE`** → A1, A7. ✓
- **Resolução de nomenclatura (FAQ → Perguntas Frequentes /faq)** → D1, D2, D3. ✓
- **Leitura autenticado / escrita INFO_MANAGE** → A6 (web test cobre 200/401/403/201/400/404/204). ✓
- **Manter `components/openinghours`** → não tocado (só Contatos sai). ✓
```
