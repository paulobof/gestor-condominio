# Design — Informações Gerais (substitui Contatos)

**Data:** 2026-06-09
**Branch:** `feat/informacoes-gerais`
**Feature flag:** `app.feature.generalinfo.enabled` (default `false`), espelhando `announcements`/`faq`/`contacts`.
**Contexto:** a feature **Contatos** (`feat/contatos`, commit `2db9aa2`, mergeada) foi um mal-entendido. O que o síndico precisa é uma aba de **Informações Gerais**: conteúdo livre como horário da portaria, administração, regras básicas, instruções (ex.: como ligar para outro apartamento). Este design **substitui** Contatos por Informações Gerais.

## Decisões (do usuário)

- **Substituir** Contatos por Informações Gerais (remover o módulo de contatos).
- Conteúdo = **seções livres** (título + texto), não campos fixos.
- Corpo de cada seção = **editor rich text** (visual, sem markdown).
- **Ordem manual** das seções (síndico reordena).

## Conceito

Uma aba **"Informações Gerais"** (`/informacoes`) com uma lista de **seções**, cada uma com **título** e **corpo formatado** (rich text). O síndico cria/edita/exclui/reordena; qualquer usuário autenticado visualiza (somente leitura). Sem horário de funcionamento, sem telefone estruturado — tudo é conteúdo livre dentro das seções.

## Backend (novo módulo `feature/info`, remove `feature/contact`)

### Entidade
- **`InfoSection`** (tabela `info_section`): `id` (uuid, `gen_random_uuid()`), `version`, `title` (varchar 120, not null), `body` (text, not null — HTML sanitizado), `position` (int, not null — ordem de exibição), `created_at`/`updated_at` (audit), `deleted_at` (soft delete). `@SQLDelete`/`@SQLRestriction("deleted_at IS NULL")`, Lombok como nas demais entidades (`@Getter`, `@Setter(PROTECTED)`, `@EqualsAndHashCode(of="id")`, `@ToString(onlyExplicitlyIncluded)`). Métodos de domínio: `create(title, body, position)`, `edit(title, body)`, `moveTo(position)`.

### Migration `V22__general_info.sql`
- Cria `info_section` (PK `gen_random_uuid()`, soft delete, índice por `position`).
- **Dropa** `contact_opening_hours` e `contact` (a feature de Contatos é recém-criada; flag off em prod ⇒ sem janela de rolling deploy quebrada; em HML usa seed sintético).
- Adiciona permissão **`INFO_MANAGE`** (próximo id livre) + grant a **MANAGER** (`role_permission` — hard delete permitido pelo CLAUDE.md).
- Remove o grant de **`CONTACT_MANAGE`** (e opcionalmente a permissão), já que o módulo sai.
- Cabeçalho `-- flyway:transactional=true`.

### Repositório
- **`InfoSectionRepository`**: `findAllByOrderByPositionAsc()`; `findTopByOrderByPositionDesc()` (para calcular próxima `position` ao criar).

### DTOs
- `CreateInfoSectionRequest(@NotBlank @Size(max=120) String title, @NotBlank String body)`.
- `UpdateInfoSectionRequest(@NotBlank @Size(max=120) String title, @NotBlank String body)`.
- `ReorderRequest(@NotEmpty List<UUID> orderedIds)`.
- `InfoSectionView(UUID id, String title, String body, int position, Instant updatedAt)`.

### Segurança — sanitização do HTML (STRIDE)
- O `body` chega como HTML do editor. **Sanitização primária no servidor**, na escrita (`create`/`update`), com **jsoup `Safelist`** (nova dependência) restrita a tags seguras de formatação (`b`, `strong`, `i`, `em`, `u`, `p`, `br`, `ul`, `ol`, `li`, `a[href]` com protocolos `http/https/tel/mailto`). Remove `<script>`, handlers `on*`, `style` perigoso, etc.
- O frontend ainda sanitiza no render (**DOMPurify**) como defesa em profundidade.

### Service `InfoSectionService` (`@Transactional`)
- `list()` → ordenado por `position`, monta `InfoSectionView`.
- `create(req)` → sanitiza `body`, calcula `position` (último + 1), salva.
- `update(id, req)` → sanitiza `body`, `edit(...)`; `NOT_FOUND` se ausente.
- `delete(id)` → soft delete.
- `reorder(orderedIds)` → aplica `position` conforme a ordem da lista; ids ausentes/extras geram `400`.
- `InfoException("NOT_FOUND", ...)` + mapeamento no `GlobalExceptionHandler` (404/400), como nas outras features.

### Controller `InfoSectionController` (`/api/info-sections`)
- `@ConditionalOnProperty(app.feature.generalinfo.enabled)`.
- `GET` → `isAuthenticated()`.
- `POST` / `PUT /{id}` / `DELETE /{id}` / `PUT /reorder` → `hasAuthority('INFO_MANAGE')`.

### Flag
- `app.feature.generalinfo.enabled: ${APP_FEATURE_GENERALINFO_ENABLED:false}` em `application.yml`.
- `APP_FEATURE_GENERALINFO_ENABLED=true` no `dokploy-backend.env.example` (remover `APP_FEATURE_CONTACTS_ENABLED`).

### Remoções
- Todo `feature/contact` (entidades, repos, service, controller, DTOs, exception, testes) e `app.feature.contacts` do `application.yml`.
- `CONTACT_MANAGE` do enum `PermissionCode`.

## Frontend (novo `features/generalinfo`, remove `features/contacts`)

### Editor rich text (reutilizável)
- **`components/richtext/RichTextEditor.tsx`** — TipTap (`@tiptap/react` + `@tiptap/starter-kit` + `@tiptap/extension-link`), toolbar mínima: **negrito, itálico, lista (• e numerada), link**. Props `value: string` (HTML), `onChange`. Acessível, alvos ≥44px.
- **`components/richtext/RichTextView.tsx`** — recebe HTML, sanitiza com **DOMPurify** e renderiza. Usado na página pública.

### Feature
- **`features/generalinfo/api/generalInfoApi.ts`**: tipos `InfoSection`, `InfoSectionBody`; `listSections`, `createSection`, `updateSection`, `deleteSection`, `reorderSections`.
- **`/informacoes` → `InfoPage`**: lista as seções (título + `RichTextView`), somente leitura. Botão "Gerenciar" → `/informacoes/gerenciar` apenas com `INFO_MANAGE`. Estado vazio amigável.
- **`/informacoes/gerenciar` → `InfoAdminPage`**: lista com **arrastar para reordenar** (chama `reorderSections`); criar/editar via `RichTextEditor`; excluir; toasts.
- **Sidebar:** item **"Informações Gerais"** → `/informacoes` (substitui "Contatos"). Atualiza `router.tsx`.
- Remove `features/contacts` (páginas, api, testes). **Mantém** `components/openinghours/` (usado pelas Indicações).

### Dependências novas (frontend)
- `@tiptap/react`, `@tiptap/starter-kit`, `@tiptap/extension-link`, `dompurify` (+ `@types/dompurify`). npm.

## Testes (TDD — testes primeiro)

### Backend
- **`InfoSectionServiceTest`** (unit, repo mockado): create calcula position e sanitiza (`<script>` removido); update edita + sanitiza; list ordenado por position; reorder reaplica positions; delete; `NOT_FOUND`.
- **`InfoSectionControllerWebTest`** (`@WebMvcTest`, flag on, helper `MockAuth`): GET autenticado 200; POST sem `INFO_MANAGE` 403; POST válido 201; validação 400; `NOT_FOUND` 404; anônimo barrado.

### Frontend
- **`RichTextView.test.tsx`**: sanitiza HTML perigoso (remove `<script>`/handlers), preserva formatação básica.
- **`RichTextEditor.test.tsx`**: formatar emite HTML em `onChange`.
- **`InfoPage.test.tsx`**: lista seções; gate do botão "Gerenciar" por permissão.
- **`InfoAdminPage.test.tsx`**: cria seção (chama `createSection`); reordenar chama `reorderSections`.

## Fora de escopo

- Horário de funcionamento / "aberto agora" (era da feature de Contatos, descartado).
- Telefones estruturados/clicáveis como entidade (podem virar links dentro do texto da seção).
- Anexos/imagens nas seções; versionamento/histórico de edições.
- Upload de mídia no editor.

## Riscos / notas

- **PR grande** (novo módulo back+front + remoção de Contatos). Aceitável por ser feature coesa e por Contatos ser recém-criado; se passar muito de 400 linhas, separar a remoção de Contatos num commit/PR anterior.
- **Drop de tabelas em migration:** `contact`/`contact_opening_hours` são dropadas. Seguro porque a flag de Contatos está off em prod e HML usa seed sintético; ainda assim, é uma quebra de compatibilidade com a versão anterior do app — fazer o deploy do app novo junto com a migration.
- **Rich text = vetor de XSS:** mitigado por sanitização dupla (jsoup no servidor na escrita + DOMPurify no render). Editor é restrito a MANAGER, mas defesa em profundidade é obrigatória.
- **Permissão:** troca de `CONTACT_MANAGE` por `INFO_MANAGE`; conferir que nenhum outro ponto referencia `CONTACT_MANAGE`.
