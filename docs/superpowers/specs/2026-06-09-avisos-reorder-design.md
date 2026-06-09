# Reordenar avisos + remover "fixar no topo" — Design

**Data:** 2026-06-09
**Branch:** a criar (`feat/avisos-reorder`)

## Contexto

O mural de avisos (`feature/announcement`) hoje ordena por `pinned DESC, published_at DESC`:
um aviso "fixado" sobe ao topo, o resto vem do mais recente ao mais antigo. O síndico quer
**controle manual da ordem** (setas ↑/↓ em cada aviso) e a **remoção do conceito de fixado**.

Autorização permanece por permissão `ANNOUNCEMENT_MANAGE` (hoje das roles MANAGER e COUNCIL).
A leitura segue liberada a qualquer autenticado.

> Sub-projeto **B** de um par. O sub-projeto **A** (tela de gestão de acessos + role "Editor do
> Mural") é independente e será desenhado depois, em spec próprio. B compõe com A sem dependência:
> o reorder é protegido por `ANNOUNCEMENT_MANAGE`, que a role nova carregará.

## Objetivo

Substituir a ordenação por `pinned` por **posição manual** (`position`), com setas ↑/↓ inline na
lista para quem tem `ANNOUNCEMENT_MANAGE`, e remover o fixado de toda a stack (entidade, DTOs,
formulário, badge).

Não-objetivos: paginação infinita, drag-and-drop, agendamento, qualquer mudança em A (roles).

## Modelo de dados / ordenação

- Adiciona `position integer NOT NULL` em `announcement`; **remove** a coluna `pinned` e o índice
  `idx_announcement_feed (pinned DESC, published_at DESC)`.
- Migration (expand/contract, backward-compatible) faz **backfill** de `position` preservando a
  ordem atual: `ROW_NUMBER() OVER (ORDER BY pinned DESC, published_at DESC)` (apenas linhas com
  `deleted_at IS NULL`; soft-deletados recebem posição alta/qualquer, não aparecem na listagem).
- Novo índice `idx_announcement_position ON announcement (position) WHERE deleted_at IS NULL`.
- Ordenação da lista passa a ser `position ASC`. **Topo = menor `position`.**
- **Novo aviso entra no topo:** recebe `position = (menor position atual) − 1`, ou `0` se a
  tabela estiver vazia. Não reescreve as posições existentes.
- **Paginação mantida** (20/página, ordenada por `position ASC`). As setas trocam um aviso com o
  vizinho adjacente na ordem corrente; reordenar exatamente na fronteira entre páginas é caso raro
  e aceitável.

## Backend (`feature/announcement`)

**Entidade `Announcement`**
- Remove o campo `pinned` (e seu uso no `create`/`edit`).
- Adiciona `private int position;` e `public void moveTo(int position)`.
- `create(authorUserId, title, body)` — sem `pinned`; a posição é atribuída pelo service.
- `edit(title, body)` — sem `pinned`.

**DTOs**
- `CreateAnnouncementRequest` e `UpdateAnnouncementRequest`: removem o campo `pinned`.
- `AnnouncementView`: remove `pinned`; adiciona `int position`.
- Novo `ReorderAnnouncementsRequest(@NotEmpty List<Item> items)` com
  `Item(@NotNull UUID id, int position)` (espelha `ReorderInfoRequest`).

**Repository**
- Remove `findAllByOrderByPinnedDescPublishedAtDesc`.
- Adiciona `Page<Announcement> findAllByOrderByPositionAsc(Pageable)`.
- Adiciona `@Query("select min(a.position) from Announcement a") Integer findMinPosition();`.

**Service (`AnnouncementService`)**
- `list(pageable)` usa `findAllByOrderByPositionAsc`.
- `create`: `int top = (min == null ? 0 : min − 1)`; cria com essa posição.
- `update`: `edit(title, body)` (sem pinned).
- Novo `reorder(List<Item> items)` `@Transactional`: para cada item, `find(id).moveTo(position)`.

**Controller (`AnnouncementController`)**
- Novo `PUT /api/announcements/reorder` com `@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")`,
  declarado **antes** de `@PutMapping("/{id}")` para não ser capturado como path variable.
- `create`/`update` deixam de passar `pinned`.

## Frontend (`features/announcements`)

**`announcementsApi.ts`**
- Tipo `Announcement`: remove `pinned`, adiciona `position: number`.
- `AnnouncementBody`: remove `pinned`.
- Nova `reorderAnnouncements(items: { id: string; position: number }[])` →
  `PUT /announcements/reorder`.

**`AnnouncementsListPage.tsx`** (o feed)
- Remove o badge **"Fixado"**.
- Para `canManage` (`ANNOUNCEMENT_MANAGE`), cada card ganha setas **↑/↓** inline na lateral
  (touch ≥44px; ↑ desabilitada no primeiro, ↓ no último da página). Clicar troca a posição com o
  vizinho via `reorderAnnouncements([{id:a,position:b.position},{id:b,position:a.position}])` e
  recarrega. As setas não devem disparar a navegação do card (`preventDefault`/`stopPropagation`).

**`AnnouncementFormPage.tsx`**
- Remove o checkbox **"Fixar no topo"** e o campo `pinned` do estado/submit.

**`AnnouncementDetailPage.tsx`**
- Remove qualquer exibição de "Fixado", se existir.

## Testes (TDD)

**Backend**
- `AnnouncementServiceTest`: criar atribui `min − 1` (e `0` na tabela vazia); `reorder` aplica as
  posições; `list` delega a `findAllByOrderByPositionAsc`.
- `AnnouncementControllerWebTest`: `reorder` → 204 com `ANNOUNCEMENT_MANAGE`, 403 sem, 401 anônimo;
  remover asserções de `pinned`; `create`/`update` sem `pinned` no corpo.

**Frontend**
- `AnnouncementsListPage.test.tsx`: com permissão renderiza setas e chama `reorderAnnouncements`
  ao clicar ↓; sem permissão não há setas; sem badge "Fixado".
- `announcementsApi.test.ts`: contrato sem `pinned`, com `position` e `reorderAnnouncements`.

## Migration

`V23__announcement_position.sql` (cabeçalho `-- flyway:transactional=true`):
1. `ALTER TABLE announcement ADD COLUMN position integer;`
2. Backfill via `ROW_NUMBER()` na ordem `pinned DESC, published_at DESC`.
3. `ALTER COLUMN position SET NOT NULL;`
4. `DROP INDEX idx_announcement_feed;` e `CREATE INDEX idx_announcement_position ...`.
5. `ALTER TABLE announcement DROP COLUMN pinned;`

Observação: drop de coluna no mesmo migration que a adiciona é permitido aqui porque `pinned` e
`position` são campos distintos (não é rename); a regra do CLAUDE.md proíbe rename/remove **da
mesma coluna** recém-adicionada.

## Convenções

SOLID/KISS, TDD, soft delete intacto, Lombok sem `@Data`, `@Transactional` só no service,
autorização por permissão. Commits Conventional, hooks rodam, PR ≤400 linhas. Português na UI,
inglês no código.
