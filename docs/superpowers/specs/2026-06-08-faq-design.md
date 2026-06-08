# Design — A1: FAQ (Perguntas frequentes)

**Data:** 2026-06-08
**Branch:** `feat/faq`
**Feature flag:** `app.feature.faq.enabled` (default `false` em prod), espelhando `announcements`.
**Âncora:** seção 6.4 do design `2026-05-24-gestor-condominio-design.md`; padrão de implementação = feature `announcement`.

## Problema / objetivo

Hoje só existe o **mural** (avisos com data). Falta uma seção **fixa de referência** com as informações que os moradores mais perguntam (regras, horários, procedimentos), em formato **pergunta → resposta**, agrupadas por categoria, gerenciadas pelo síndico/conselho.

Esta é a parte **A1 (FAQ)**. A parte **A2 (Contatos/Links com horário, seção 6.5)** é uma feature separada (outro PR).

## Decisões

- **Categoria:** texto livre por item (string). O síndico cria as categorias que quiser; a tela agrupa por categoria.
- **Resposta:** texto simples (`text`, quebras de linha preservadas). Sem markdown/HTML → sem risco de XSS.
- **Publicação:** flag `published`; moradores veem só publicados; admin vê todos.
- **Ordenação:** campo `ordering` (int) por item; reordenação via setas ↑/↓ (sem drag-and-drop).
- **Autorização:** leitura = qualquer autenticado; escrita = `FAQ_MANAGE` (já semeado na V6 para MANAGER e COUNCIL).

## Backend

Espelha a feature `announcement` (`@SQLDelete`/`@SQLRestriction`, service `@Transactional`, controller com `@PreAuthorize`, `@ConditionalOnProperty`).

- **Entidade `Faq`** (tabela `faq`): `id` (uuid), `version` (bigint), `question` (varchar 200), `answer` (text), `category` (varchar 80), `published` (boolean), `ordering` (int), `created_at`, `updated_at`, `deleted_at`. Soft delete. Métodos de domínio: `create(...)`, `edit(question, answer, category)`, `setPublished(bool)`, `setOrdering(int)`.
- **Migration `V19__faq.sql`**: cria `faq` + índice de leitura. **Não** insere permissão (FAQ_MANAGE id 8 já existe + grants na V6).
- **`FaqRepository`** (Spring Data): `findAllByPublishedTrueOrderByCategoryAscOrderingAsc()`, `findAllByOrderByCategoryAscOrderingAsc()`.
- **`FaqService`** (`@Transactional`): `listPublished()` (morador), `listAll()` (admin), `getById`, `create`, `update`, `setPublished(id, bool)`, `reorder(List<{id, ordering}>)`, `delete` (soft). `FaqException("NOT_FOUND", ...)`.
- **`FaqController`** (`/api/faq`, `@ConditionalOnProperty(app.feature.faq.enabled=true)`):
  - `GET /api/faq` — `isAuthenticated()` — publicados, ordenados por categoria+ordering.
  - `GET /api/faq/all` — `FAQ_MANAGE` — todos (admin).
  - `POST /api/faq` — `FAQ_MANAGE`.
  - `PUT /api/faq/{id}` — `FAQ_MANAGE`.
  - `PUT /api/faq/{id}/publish` `{published}` — `FAQ_MANAGE`.
  - `PUT /api/faq/reorder` `{items:[{id, ordering}]}` — `FAQ_MANAGE`.
  - `DELETE /api/faq/{id}` — `FAQ_MANAGE`.
- **DTOs**: `CreateFaqRequest` (question @NotBlank≤200, answer @NotBlank, category @NotBlank≤80), `UpdateFaqRequest` (idem), `PublishFaqRequest` (boolean published), `ReorderFaqRequest` (lista de `{UUID id, int ordering}`), `FaqView` (todos os campos).
- **Testes**: `FaqServiceTest` (unit, repo mockado: create/update/publish/reorder/delete/list); `FaqControllerWebTest` (`@WebMvcTest` com `app.feature.faq.enabled=true`): GET público 200; POST sem `FAQ_MANAGE` → 403; POST com `FAQ_MANAGE` → 201; validação inválida → 400. Usa o helper `MockAuth` do projeto.

## Frontend

Espelha `features/announcements`.

- **`faqApi.ts`**: tipos `Faq`, `FaqBody`; `listFaq()` (publicados), `listAllFaq()` (admin), `createFaq`, `updateFaq`, `setFaqPublished`, `reorderFaq`, `deleteFaq`.
- **`/informacoes` → `InformacoesPage`**: busca `listFaq()`, agrupa por `category`, renderiza **Accordion** (shadcn) por categoria; cada item = pergunta (trigger) + resposta (content). Estado vazio amigável. Visível a qualquer autenticado.
- **`/informacoes/gerenciar` → `FaqAdminPage`** (só com `FAQ_MANAGE`): lista todos (publicados e não), com ações editar / publicar-despublicar / excluir / mover ↑↓ (chama `reorderFaq`); form (pergunta, resposta `textarea`, categoria, switch publicado) para criar/editar.
- **Sidebar**: item **"Informações"** → `/informacoes` (todos). Dentro da `InformacoesPage`, botão "Gerenciar" → `/informacoes/gerenciar` só aparece com `FAQ_MANAGE` (padrão do projeto: checar `authorities` do `useAuth`, como a Sidebar faz com `REGISTRATION_VIEW`).
- **Rotas**: adicionar as duas em `router.tsx` dentro da casca autenticada.
- **Testes** (vitest): `InformacoesPage` agrupa por categoria e renderiza itens; `FaqAdminPage` mostra ações de gestão e o botão Gerenciar respeita `FAQ_MANAGE`.

## Fora de escopo (A1)

Contatos/Links com horário (A2); markdown; drag-and-drop; notificação de novo FAQ.

## Arquivos (estimativa)

Backend novos: `Faq`, `FaqRepository`, `FaqService`, `FaqException`, `FaqController`, `dto/{CreateFaqRequest,UpdateFaqRequest,PublishFaqRequest,ReorderFaqRequest,FaqView}`, `V19__faq.sql`, 2 testes.
Frontend novos: `features/faq/api/faqApi.ts`, `features/faq/pages/{InformacoesPage,FaqAdminPage}.tsx`, 2 testes. Editados: `router.tsx`, `Sidebar.tsx`, `application.yml` (flag), `dokploy-backend.env.example` (flag).

PR coeso. Pode passar de 400 linhas por ser CRUD completo back+front — se necessário, separar o `FaqAdminPage` num PR seguinte; alvo é manter focado.
