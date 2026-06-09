# Reorder de Avisos + Remoção de "Fixado" — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir a ordenação por `pinned` do mural de avisos por **posição manual** (setas ↑/↓ inline para quem tem `ANNOUNCEMENT_MANAGE`) e remover o conceito de "fixado" de toda a stack.

**Architecture:** Backend `feature/announcement` troca o campo `pinned` por `position` (entidade, DTOs, repository, service, controller com novo `PUT /reorder`); migration V23 adiciona `position`, faz backfill preservando a ordem atual e dropa `pinned`. Frontend: a lista (feed) ganha setas inline para reordenar (mesmo padrão de `InfoAdminPage`), o formulário perde o checkbox e o detalhe perde o badge.

**Tech Stack:** Spring Boot 3 + JPA/Hibernate 6 (`@SQLDelete`/`@SQLRestriction`), Flyway; React + TypeScript + Vitest.

**Spec:** `docs/superpowers/specs/2026-06-09-avisos-reorder-design.md`

**Branch:** `feat/avisos-reorder` (já criada; spec já commitado).

**Convenções (CLAUDE.md):** TDD, soft delete intacto, Lombok sem `@Data`, `@Transactional` só no service, autorização por permissão (`hasAuthority`), Conventional Commits, hooks rodam (não usar `--no-verify`), corpo de commit ≤100 chars/linha. Backend = Maven (`./mvnw`), frontend = npm.

**Comandos úteis:**
- Backend um teste: `cd backend && ./mvnw -q -Dtest=ClasseDeTeste test`
- Backend tudo: `cd backend && ./mvnw -q test`
- Frontend um arquivo: `cd frontend && npx vitest run src/caminho/Arquivo.test.tsx`
- Frontend tudo: `cd frontend && npm test -- --run`

> Nota de ambiente: o Docker pode estar desligado; os 3 testes `RepositoryPostgresTest` (Testcontainers) são **pulados** automaticamente nesse caso — isso é esperado e não é falha.

---

## File Structure

**Backend — modificar:**
- `feature/announcement/Announcement.java` — entidade: `-pinned`, `+position`, `+moveTo`.
- `feature/announcement/AnnouncementRepository.java` — troca finder, `+findMinPosition`.
- `feature/announcement/AnnouncementService.java` — create no topo, `+reorder`, list por position.
- `feature/announcement/AnnouncementController.java` — `+PUT /reorder`.
- `feature/announcement/dto/CreateAnnouncementRequest.java` — `-pinned`.
- `feature/announcement/dto/UpdateAnnouncementRequest.java` — `-pinned`.
- `feature/announcement/dto/AnnouncementView.java` — `-pinned`, `+position`.

**Backend — criar:**
- `feature/announcement/dto/ReorderAnnouncementsRequest.java`.
- `resources/db/migration/V23__announcement_position.sql`.

**Backend — testar (modificar):**
- `test/.../announcement/AnnouncementTest.java`
- `test/.../announcement/AnnouncementServiceTest.java`
- `test/.../announcement/AnnouncementControllerWebTest.java`

**Frontend — modificar:**
- `features/announcements/api/announcementsApi.ts` — tipos sem `pinned`, `+position`, `+reorderAnnouncements`.
- `features/announcements/api/announcementsApi.test.ts` — contrato novo.
- `features/announcements/pages/AnnouncementsListPage.tsx` — setas inline, sem badge.
- `features/announcements/pages/AnnouncementsListPage.test.tsx` — testes de setas.
- `features/announcements/pages/AnnouncementFormPage.tsx` — sem checkbox.
- `features/announcements/pages/AnnouncementDetailPage.tsx` — sem badge.

---

## Phase A — Backend

### Task A1: Migration V23 (position + backfill + drop pinned)

**Files:**
- Create: `backend/src/main/resources/db/migration/V23__announcement_position.sql`

- [ ] **Step 1: Escrever a migration**

```sql
-- flyway:transactional=true

-- Reordenação manual do mural: substitui "pinned" por "position" (ordem definida pelo editor).
-- Topo = menor position. Decisão registrada no spec: tudo num migration (app single-instance).

ALTER TABLE announcement ADD COLUMN position integer;

-- Backfill preservando a ordem atual: fixados primeiro, depois mais recentes.
WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY pinned DESC, published_at DESC) AS rn
    FROM announcement
)
UPDATE announcement a SET position = ordered.rn
FROM ordered WHERE ordered.id = a.id;

ALTER TABLE announcement ALTER COLUMN position SET NOT NULL;

-- Troca o índice do feed: antes (pinned DESC, published_at DESC); agora (position).
DROP INDEX IF EXISTS idx_announcement_feed;
CREATE INDEX idx_announcement_position ON announcement (position) WHERE deleted_at IS NULL;

ALTER TABLE announcement DROP COLUMN pinned;
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/db/migration/V23__announcement_position.sql
git commit -m "feat(avisos): migration V23 (position, backfill, drop pinned)"
```

---

### Task A2: Domínio + DTOs + repository + service (position substitui pinned)

Esta é uma mudança de contrato atômica: remover `pinned` e adicionar `position` afeta entidade,
DTOs, repository, service e os três arquivos de teste de uma vez (não compila em estados
intermediários). Por isso atualizamos os testes para o novo contrato (vermelho), depois a produção
(verde), e commitamos uma vez. O reorder fica na Task A3.

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/announcement/Announcement.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/announcement/dto/CreateAnnouncementRequest.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/announcement/dto/UpdateAnnouncementRequest.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/announcement/dto/AnnouncementView.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/announcement/AnnouncementRepository.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/announcement/AnnouncementService.java`
- Test: `backend/src/test/java/br/com/condominio/feature/announcement/AnnouncementTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/announcement/AnnouncementServiceTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/announcement/AnnouncementControllerWebTest.java`

- [ ] **Step 1: Atualizar `AnnouncementTest.java`** (novo contrato — `create` recebe `position`, sem `pinned`; novo `moveTo`)

Substituir o corpo inteiro da classe (após os imports) por:

```java
class AnnouncementTest {

  private final UUID author = UUID.randomUUID();

  @Test
  void create_setsFields() {
    Announcement a = Announcement.create(author, "Manutenção", "Água desligada 9h-12h", 0);

    assertThat(a.getAuthorUserId()).isEqualTo(author);
    assertThat(a.getTitle()).isEqualTo("Manutenção");
    assertThat(a.getBody()).isEqualTo("Água desligada 9h-12h");
    assertThat(a.getPosition()).isZero();
  }

  @Test
  void edit_updatesTitleAndBody() {
    Announcement a = Announcement.create(author, "Antigo", "corpo", 0);

    a.edit("Novo título", "novo corpo");

    assertThat(a.getTitle()).isEqualTo("Novo título");
    assertThat(a.getBody()).isEqualTo("novo corpo");
  }

  @Test
  void moveTo_changesPosition() {
    Announcement a = Announcement.create(author, "Aviso", "corpo", 0);

    a.moveTo(5);

    assertThat(a.getPosition()).isEqualTo(5);
  }
}
```

- [ ] **Step 2: Atualizar `AnnouncementServiceTest.java`** (helper, create no topo, reorder fora; sem pinned)

Substituir o corpo da classe (após os imports) por:

```java
class AnnouncementServiceTest {

  private AnnouncementRepository repo;
  private AnnouncementService service;

  private final UUID author = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repo = mock(AnnouncementRepository.class);
    service = new AnnouncementService(repo);
    when(repo.save(any(Announcement.class))).thenAnswer(i -> i.getArgument(0));
  }

  private Announcement persisted(UUID id, int position) {
    Announcement a = Announcement.create(author, "Aviso", "corpo", position);
    ReflectionTestUtils.setField(a, "id", id);
    return a;
  }

  @Test
  void create_putsNewAtTop_minusOne() {
    when(repo.findMinPosition()).thenReturn(2);

    AnnouncementView v =
        service.create(author, new CreateAnnouncementRequest("Manutenção", "corpo"));

    assertThat(v.title()).isEqualTo("Manutenção");
    assertThat(v.authorUserId()).isEqualTo(author);
    assertThat(v.position()).isEqualTo(1);
    verify(repo).save(any(Announcement.class));
  }

  @Test
  void create_firstAnnouncement_positionZero() {
    when(repo.findMinPosition()).thenReturn(null);

    AnnouncementView v = service.create(author, new CreateAnnouncementRequest("Regras", "corpo"));

    assertThat(v.position()).isZero();
  }

  @Test
  void getById_notFound_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(id))
        .isInstanceOf(AnnouncementException.class)
        .hasMessageContaining("não encontrado");
  }

  @Test
  void update_editsFields() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, 0)));

    AnnouncementView v = service.update(id, new UpdateAnnouncementRequest("Novo", "novo corpo"));

    assertThat(v.title()).isEqualTo("Novo");
    assertThat(v.body()).isEqualTo("novo corpo");
  }

  @Test
  void delete_softDeletesViaRepository() {
    UUID id = UUID.randomUUID();
    Announcement a = persisted(id, 0);
    when(repo.findById(id)).thenReturn(Optional.of(a));

    service.delete(id);

    verify(repo).delete(a);
  }

  @Test
  void list_mapsPageToViews() {
    when(repo.findAllByOrderByPositionAsc(any()))
        .thenReturn(new PageImpl<>(List.of(persisted(UUID.randomUUID(), 0))));

    var page = service.list(PageRequest.of(0, 20));

    assertThat(page.getContent()).hasSize(1);
  }
}
```

- [ ] **Step 3: Atualizar `AnnouncementControllerWebTest.java`** para o novo `AnnouncementView` (sem `pinned`) e remover asserções/JSON de pinned

Trocar o método `view()`:

```java
  private AnnouncementView view() {
    return new AnnouncementView(AID, "Manutenção", "corpo", 0, Instant.now(), UID, Instant.now());
  }
```

No teste `list_authenticated_returns200`, remover a linha:

```java
        .andExpect(jsonPath("$.content[0].pinned").value(true));
```

(deixando a asserção de `title` como última.)

No teste `create_withManage_returns201`, trocar o `content(...)` para:

```java
                .content("{\"title\":\"Manutenção\",\"body\":\"corpo\"}"))
```

No teste `create_withoutManage_returns403`, trocar o `content(...)` para:

```java
                .content("{\"title\":\"Manutenção\",\"body\":\"corpo\"}"))
```

- [ ] **Step 4: Rodar os testes e ver falhar (compilação)**

Run: `cd backend && ./mvnw -q -Dtest=AnnouncementTest,AnnouncementServiceTest,AnnouncementControllerWebTest test`
Expected: FALHA de compilação (`create` com aridade antiga, `pinned` inexistente etc.).

- [ ] **Step 5: Atualizar a entidade `Announcement.java`**

Remover o campo `pinned` e seu `@Column`:

```java
  @Column(nullable = false)
  private boolean pinned;
```

Adicionar, no lugar (após o campo `body`):

```java
  @Column(nullable = false)
  private int position;
```

Trocar a factory e o `edit`, e adicionar `moveTo` (remover o antigo `create`/`edit` com `pinned`):

```java
  public static Announcement create(UUID authorUserId, String title, String body, int position) {
    Announcement a = new Announcement();
    a.authorUserId = authorUserId;
    a.title = title;
    a.body = body;
    a.position = position;
    return a;
  }

  public void edit(String title, String body) {
    this.title = title;
    this.body = body;
  }

  public void moveTo(int position) {
    this.position = position;
  }
```

Atualizar o Javadoc da classe (linha que menciona "fixados aparecem primeiro"):

```java
/** Aviso do mural publicado por quem tem ANNOUNCEMENT_MANAGE. Soft delete; ordem manual (position). */
```

- [ ] **Step 6: Atualizar os DTOs**

`CreateAnnouncementRequest.java`:

```java
public record CreateAnnouncementRequest(
    @NotBlank @Size(max = 140) String title, @NotBlank @Size(max = 5000) String body) {}
```

`UpdateAnnouncementRequest.java`:

```java
public record UpdateAnnouncementRequest(
    @NotBlank @Size(max = 140) String title, @NotBlank @Size(max = 5000) String body) {}
```

`AnnouncementView.java`:

```java
public record AnnouncementView(
    UUID id,
    String title,
    String body,
    int position,
    Instant publishedAt,
    UUID authorUserId,
    Instant updatedAt) {

  public static AnnouncementView of(Announcement a) {
    return new AnnouncementView(
        a.getId(),
        a.getTitle(),
        a.getBody(),
        a.getPosition(),
        a.getPublishedAt(),
        a.getAuthorUserId(),
        a.getUpdatedAt());
  }
}
```

- [ ] **Step 7: Atualizar o `AnnouncementRepository.java`**

Substituir o método do finder e adicionar `findMinPosition` (manter import de `@Query`):

```java
package br.com.condominio.feature.announcement;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

  /** Feed do mural ordenado por posição manual. {@code @SQLRestriction} filtra deletados. */
  Page<Announcement> findAllByOrderByPositionAsc(Pageable pageable);

  @Query("select min(a.position) from Announcement a")
  Integer findMinPosition();
}
```

- [ ] **Step 8: Atualizar o `AnnouncementService.java`** (sem reorder ainda)

Trocar `list`, `create` e `update`:

```java
  @Transactional(readOnly = true)
  public Page<AnnouncementView> list(Pageable pageable) {
    return repo.findAllByOrderByPositionAsc(pageable).map(AnnouncementView::of);
  }

  @Transactional
  public AnnouncementView create(UUID authorId, CreateAnnouncementRequest body) {
    Integer min = repo.findMinPosition();
    int top = (min == null ? 0 : min - 1);
    Announcement a = Announcement.create(authorId, body.title(), body.body(), top);
    return AnnouncementView.of(repo.save(a));
  }

  @Transactional
  public AnnouncementView update(UUID id, UpdateAnnouncementRequest body) {
    Announcement a = find(id);
    a.edit(body.title(), body.body());
    return AnnouncementView.of(a);
  }
```

(Atualizar o Javadoc da classe que cita "só o síndico publica" continua válido; sem mudança obrigatória.)

- [ ] **Step 9: Rodar os testes e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=AnnouncementTest,AnnouncementServiceTest,AnnouncementControllerWebTest test`
Expected: PASS (os três; o web test ainda sem testes de reorder).

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/announcement/ \
  backend/src/test/java/br/com/condominio/feature/announcement/
git commit -m "refactor(avisos): position manual substitui pinned"
```

---

### Task A3: Endpoint de reorder (TDD)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/announcement/dto/ReorderAnnouncementsRequest.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/announcement/AnnouncementService.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/announcement/AnnouncementController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/announcement/AnnouncementServiceTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/announcement/AnnouncementControllerWebTest.java`

- [ ] **Step 1: Criar o DTO `ReorderAnnouncementsRequest.java`**

```java
package br.com.condominio.feature.announcement.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReorderAnnouncementsRequest(@NotEmpty List<Item> items) {
  public record Item(@NotNull UUID id, int position) {}
}
```

- [ ] **Step 2: Adicionar o teste de service `reorder_appliesPositions`** em `AnnouncementServiceTest.java`

Adicionar o import:

```java
import br.com.condominio.feature.announcement.dto.ReorderAnnouncementsRequest;
```

Adicionar o teste:

```java
  @Test
  void reorder_appliesPositions() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Announcement a1 = persisted(id1, 0);
    Announcement a2 = persisted(id2, 1);
    when(repo.findById(id1)).thenReturn(Optional.of(a1));
    when(repo.findById(id2)).thenReturn(Optional.of(a2));

    service.reorder(
        List.of(
            new ReorderAnnouncementsRequest.Item(id1, 1),
            new ReorderAnnouncementsRequest.Item(id2, 0)));

    assertThat(a1.getPosition()).isEqualTo(1);
    assertThat(a2.getPosition()).isZero();
  }
```

- [ ] **Step 3: Adicionar os testes de contrato em `AnnouncementControllerWebTest.java`**

Adicionar os imports (se ainda não houver `put`):

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
```

Adicionar os testes:

```java
  @Test
  void reorder_withManage_returns204() throws Exception {
    mvc.perform(
            put("/api/announcements/reorder")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":\"" + AID + "\",\"position\":0}]}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void reorder_withoutManage_returns403() throws Exception {
    mvc.perform(
            put("/api/announcements/reorder")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":\"" + AID + "\",\"position\":0}]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void reorder_unauthenticated_isRejected() throws Exception {
    mvc.perform(
            put("/api/announcements/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":\"" + AID + "\",\"position\":0}]}"))
        .andExpect(status().is4xxClientError());
  }
```

- [ ] **Step 4: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=AnnouncementServiceTest,AnnouncementControllerWebTest test`
Expected: FALHA de compilação (`service.reorder` e endpoint `/reorder` não existem).

- [ ] **Step 5: Adicionar `reorder` ao `AnnouncementService.java`**

Adicionar o import:

```java
import br.com.condominio.feature.announcement.dto.ReorderAnnouncementsRequest;
import java.util.List;
```

Adicionar o método (após `update`):

```java
  @Transactional
  public void reorder(List<ReorderAnnouncementsRequest.Item> items) {
    for (ReorderAnnouncementsRequest.Item it : items) {
      find(it.id()).moveTo(it.position());
    }
  }
```

- [ ] **Step 6: Adicionar o endpoint no `AnnouncementController.java`**

Adicionar o import:

```java
import br.com.condominio.feature.announcement.dto.ReorderAnnouncementsRequest;
```

Adicionar o método **antes** de `@PutMapping("/{id}")` (para `/reorder` não cair em `/{id}`):

```java
  // /reorder ANTES de /{id} para "reorder" não ser capturado como path variable.
  @PutMapping("/reorder")
  @PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
  public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderAnnouncementsRequest body) {
    service.reorder(body.items());
    return ResponseEntity.noContent().build();
  }
```

- [ ] **Step 7: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=AnnouncementServiceTest,AnnouncementControllerWebTest test`
Expected: PASS.

- [ ] **Step 8: Rodar a suíte backend inteira**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS (3 `RepositoryPostgresTest` podem aparecer como skipped sem Docker).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/announcement/ \
  backend/src/test/java/br/com/condominio/feature/announcement/
git commit -m "feat(avisos): endpoint de reorder protegido por ANNOUNCEMENT_MANAGE"
```

---

## Phase B — Frontend

### Task B1: API client (position, reorder; sem pinned) — TDD de contrato

**Files:**
- Modify: `frontend/src/features/announcements/api/announcementsApi.ts`
- Test: `frontend/src/features/announcements/api/announcementsApi.test.ts`

- [ ] **Step 1: Atualizar os testes de contrato**

Em `announcementsApi.test.ts`, trocar o import para incluir `reorderAnnouncements`:

```ts
import {
  listAnnouncements,
  getAnnouncement,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
  reorderAnnouncements,
} from './announcementsApi';
```

Trocar o teste `createAnnouncement` por (sem `pinned`):

```ts
  it('createAnnouncement faz POST com o corpo', async () => {
    post.mockResolvedValue({ data: { id: 'a1' } });
    await createAnnouncement({ title: 'Manutenção', body: 'corpo' });
    expect(post).toHaveBeenCalledWith('/announcements', { title: 'Manutenção', body: 'corpo' });
  });
```

Trocar o teste `updateAnnouncement` por (sem `pinned`):

```ts
  it('updateAnnouncement faz PUT no id', async () => {
    put.mockResolvedValue({ data: { id: 'a1' } });
    await updateAnnouncement('a1', { title: 'Novo', body: 'corpo' });
    expect(put).toHaveBeenCalledWith(
      '/announcements/a1',
      expect.objectContaining({ title: 'Novo' })
    );
  });
```

Adicionar o teste de reorder (antes do `deleteAnnouncement`):

```ts
  it('reorderAnnouncements faz PUT em /reorder com os items', async () => {
    put.mockResolvedValue({ data: undefined });
    await reorderAnnouncements([{ id: 'a1', position: 0 }]);
    expect(put).toHaveBeenCalledWith('/announcements/reorder', {
      items: [{ id: 'a1', position: 0 }],
    });
  });
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/announcements/api/announcementsApi.test.ts`
Expected: FALHA (`reorderAnnouncements` não existe; `pinned` obrigatório no tipo).

- [ ] **Step 3: Atualizar `announcementsApi.ts`**

Trocar a interface `Announcement` (remover `pinned`, adicionar `position`):

```ts
export interface Announcement {
  id: string;
  title: string;
  body: string;
  position: number;
  publishedAt: string;
  authorUserId: string;
  updatedAt: string;
}
```

Trocar a interface `AnnouncementBody` (remover `pinned`):

```ts
export interface AnnouncementBody {
  title: string;
  body: string;
}
```

Adicionar a função (após `updateAnnouncement`):

```ts
export async function reorderAnnouncements(items: { id: string; position: number }[]) {
  await api.put('/announcements/reorder', { items });
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/features/announcements/api/announcementsApi.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/announcements/api/announcementsApi.ts \
  frontend/src/features/announcements/api/announcementsApi.test.ts
git commit -m "feat(avisos): api com position e reorderAnnouncements"
```

---

### Task B2: Lista com setas inline + sem badge (TDD)

**Files:**
- Modify: `frontend/src/features/announcements/pages/AnnouncementsListPage.tsx`
- Test: `frontend/src/features/announcements/pages/AnnouncementsListPage.test.tsx`

- [ ] **Step 1: Atualizar o teste** `AnnouncementsListPage.test.tsx`

Trocar o mock da api e os imports:

```tsx
vi.mock('../api/announcementsApi', () => ({
  listAnnouncements: vi.fn(),
  reorderAnnouncements: vi.fn(),
}));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import userEvent from '@testing-library/user-event';
import { AnnouncementsListPage } from './AnnouncementsListPage';
import { listAnnouncements, reorderAnnouncements } from '../api/announcementsApi';
import { useAuth } from '@/features/auth/useAuth';

const listMock = vi.mocked(listAnnouncements);
const reorderMock = vi.mocked(reorderAnnouncements);
const useAuthMock = vi.mocked(useAuth);
```

Trocar o helper `announcement` (sem `pinned`, com `position`):

```tsx
function announcement(over: Record<string, unknown> = {}) {
  return {
    id: 'a1',
    title: 'Manutenção da bomba',
    body: 'Água desligada das 9h às 12h.',
    position: 0,
    publishedAt: '2026-06-06T00:00:00Z',
    authorUserId: 'u1',
    updatedAt: '2026-06-06T00:00:00Z',
    ...over,
  };
}
```

Trocar o primeiro teste (`lista avisos e marca os fixados`) por um sem badge:

```tsx
  it('lista avisos', async () => {
    listMock.mockResolvedValue(page([announcement()]));
    renderPage();

    expect(await screen.findByText('Manutenção da bomba')).toBeInTheDocument();
    expect(screen.queryByText('Fixado')).not.toBeInTheDocument();
  });
```

Manter os testes `mostra estado vazio`, `quem tem ANNOUNCEMENT_MANAGE vê "Novo aviso"` e
`sem permissão não vê "Novo aviso"` como estão. Adicionar, ao final do `describe`, o teste das setas:

```tsx
  it('com ANNOUNCEMENT_MANAGE reordena ao clicar na seta para baixo', async () => {
    setUser(['ANNOUNCEMENT_MANAGE']);
    const a = announcement({ id: 'a', title: 'Primeiro', position: 0 });
    const b = announcement({ id: 'b', title: 'Segundo', position: 1 });
    listMock.mockResolvedValue(page([a, b]));
    reorderMock.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Primeiro');

    await user.click(screen.getAllByRole('button', { name: 'Mover para baixo' })[0]);

    await waitFor(() =>
      expect(reorderMock).toHaveBeenCalledWith([
        { id: 'a', position: 1 },
        { id: 'b', position: 0 },
      ])
    );
  });

  it('sem ANNOUNCEMENT_MANAGE não mostra setas', async () => {
    setUser([]);
    listMock.mockResolvedValue(page([announcement()]));
    renderPage();
    await screen.findByText('Manutenção da bomba');

    expect(screen.queryByRole('button', { name: 'Mover para baixo' })).not.toBeInTheDocument();
  });
```

Adicionar `waitFor` ao import do topo:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/features/announcements/pages/AnnouncementsListPage.test.tsx`
Expected: FALHA (sem setas; badge ainda presente).

- [ ] **Step 3: Reescrever `AnnouncementsListPage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowUp, ArrowDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';
import {
  listAnnouncements,
  reorderAnnouncements,
  type Announcement,
} from '../api/announcementsApi';

export function AnnouncementsListPage() {
  const { user } = useAuth();
  const canManage = !!user && user.authorities.includes('ANNOUNCEMENT_MANAGE');
  const [items, setItems] = useState<Announcement[]>([]);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    return listAnnouncements()
      .then((p) => setItems(p.content))
      .catch(() => toast.error('Erro ao carregar avisos.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const move = async (i: number, j: number) => {
    if (j < 0 || j >= items.length) return;
    const a = items[i];
    const b = items[j];
    try {
      await reorderAnnouncements([
        { id: a.id, position: b.position },
        { id: b.id, position: a.position },
      ]);
      await load();
    } catch {
      toast.error('Erro ao reordenar.');
    }
  };

  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="flex items-center gap-2 text-2xl font-heading font-semibold">
          <span
            aria-hidden="true"
            className="inline-block h-6 w-1.5 rounded-full"
            style={{ backgroundColor: 'hsl(var(--brand-red))' }}
          />
          Mural de avisos
        </h1>
        {canManage && (
          <Button asChild className="min-h-[44px]">
            <Link to="/avisos/novo">Novo aviso</Link>
          </Button>
        )}
      </div>

      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">Nenhum aviso.</p>
      ) : (
        <ul className="space-y-3">
          {items.map((a, idx) => (
            <li key={a.id} className="flex items-stretch gap-2">
              <Link
                to={`/avisos/${a.id}`}
                className="block flex-1 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <Card className="h-full transition-colors hover:bg-accent">
                  <CardHeader>
                    <CardTitle className="text-base">{a.title}</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <p className="line-clamp-2 text-sm text-muted-foreground">{a.body}</p>
                  </CardContent>
                </Card>
              </Link>
              {canManage && (
                <div className="flex flex-col justify-center gap-1">
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    aria-label="Mover para cima"
                    className="min-h-[44px] min-w-[44px]"
                    disabled={idx === 0}
                    onClick={() => move(idx, idx - 1)}
                  >
                    <ArrowUp className="h-4 w-4" aria-hidden="true" />
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    aria-label="Mover para baixo"
                    className="min-h-[44px] min-w-[44px]"
                    disabled={idx === items.length - 1}
                    onClick={() => move(idx, idx + 1)}
                  >
                    <ArrowDown className="h-4 w-4" aria-hidden="true" />
                  </Button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/features/announcements/pages/AnnouncementsListPage.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/announcements/pages/AnnouncementsListPage.tsx \
  frontend/src/features/announcements/pages/AnnouncementsListPage.test.tsx
git commit -m "feat(avisos): setas de reorder inline na lista e remove badge Fixado"
```

---

### Task B3: Formulário sem checkbox "Fixar"

**Files:**
- Modify: `frontend/src/features/announcements/pages/AnnouncementFormPage.tsx`

- [ ] **Step 1: Remover o estado `pinned`**

Remover a linha:

```tsx
  const [pinned, setPinned] = useState(false);
```

- [ ] **Step 2: Remover o `setPinned` do load do modo edição**

Remover a linha (dentro do `.then((a) => {...})`):

```tsx
        setPinned(a.pinned);
```

- [ ] **Step 3: Remover `pinned` do payload**

Trocar:

```tsx
      const payload = { title: title.trim(), body: body.trim(), pinned };
```

por:

```tsx
      const payload = { title: title.trim(), body: body.trim() };
```

- [ ] **Step 4: Remover o bloco do checkbox** (o `<label>` com o input `type="checkbox"` e o texto "Fixar no topo do mural"), inteiro:

```tsx
            <label className="flex min-h-[44px] cursor-pointer items-center gap-2">
              <input
                type="checkbox"
                className="h-5 w-5"
                checked={pinned}
                onChange={(e) => setPinned(e.target.checked)}
              />
              <span className="text-sm font-medium">Fixar no topo do mural</span>
            </label>
```

- [ ] **Step 5: Type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/announcements/pages/AnnouncementFormPage.tsx
git commit -m "feat(avisos): remove checkbox Fixar do formulario"
```

---

### Task B4: Detalhe sem badge "Fixado"

**Files:**
- Modify: `frontend/src/features/announcements/pages/AnnouncementDetailPage.tsx`

- [ ] **Step 1: Substituir o bloco do título** (que envolve o badge condicional) por só o `<h1>`

Trocar:

```tsx
      <div className="flex flex-wrap items-center gap-2">
        {a.pinned && (
          <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
            Fixado
          </span>
        )}
        <h1 className="text-2xl font-heading font-semibold">{a.title}</h1>
      </div>
```

por:

```tsx
      <h1 className="text-2xl font-heading font-semibold">{a.title}</h1>
```

- [ ] **Step 2: Type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros (nenhuma referência a `a.pinned` restante).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/announcements/pages/AnnouncementDetailPage.tsx
git commit -m "feat(avisos): remove badge Fixado da pagina de detalhe"
```

---

## Phase C — Verificação final

### Task C1: Suítes completas

- [ ] **Step 1: Backend inteiro**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS (3 `RepositoryPostgresTest` skipped sem Docker é aceitável).

- [ ] **Step 2: Frontend inteiro**

Run: `cd frontend && npm test -- --run`
Expected: todos passam.

- [ ] **Step 3:** Se algo falhar, corrigir e re-rodar antes de seguir. Não prosseguir com testes vermelhos.

---

### Task C2: Verificação manual (opcional, recomendada)

Pré-requisito: stack local de dev (memória `local-dev-stack`), flags de avisos ligada. Login admin
dev: `paulobof@gmail.com`.

- [ ] **Step 1:** Logar como síndico; abrir **Mural de avisos**.
- [ ] **Step 2:** Criar dois avisos; confirmar que o mais novo aparece **no topo**.
- [ ] **Step 3:** Usar ↑/↓ para reordenar; recarregar e confirmar a ordem persistida.
- [ ] **Step 4:** Confirmar que **não há** mais badge/checkbox de "Fixado" (lista, formulário, detalhe).
- [ ] **Step 5:** Logar como morador comum → ver o mural sem setas e sem "Novo aviso".

---

### Task C3: Integração

- [ ] **Step 1: Revisão de código** (recomendado): `superpowers:requesting-code-review` ou `/code-review` no diff da branch.
- [ ] **Step 2: Finalizar a branch** com `superpowers:finishing-a-development-branch` (a migration V23 roda no deploy).

---

## Self-Review (cobertura do spec)

- **Remover pinned (entidade/DTO/form/detalhe/badge)** → A2 (back), B3 (form), B4 (detalhe), B2 (badge na lista). ✓
- **position + ordenação manual** → A1 (migration+backfill), A2 (entidade/repo/service/list). ✓
- **Novo aviso no topo (min−1)** → A2 (service.create) + teste. ✓
- **Endpoint de reorder protegido por ANNOUNCEMENT_MANAGE** → A3 (service+controller+web test 204/403/401). ✓
- **Setas inline ↑/↓ só para quem gerencia** → B2 (lista + testes com/sem permissão). ✓
- **API frontend (position, reorder, sem pinned)** → B1 + teste de contrato. ✓
- **Paginação mantida** → A2 (list por position via Pageable; lista usa page.content). ✓
- **Migration única (decisão do usuário)** → A1. ✓
