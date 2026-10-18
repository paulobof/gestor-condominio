# Chamados (reclamações de manutenção do prédio) — Design

**Data:** 2026-10-17
**Status:** aprovado em conversa; aguardando revisão do spec escrito
**Flag:** `app.feature.complaints.enabled` (default `false`; prod off)

## 1. Objetivo

Moradores registram problemas do prédio (ex.: Manutenção → Pintura) com tipo, subtipo, local, descrição e até 3 fotos. A administração recebe, analisa, padroniza (edita) e, **semanalmente**, encaminha um lote à síndica. O app **só gera o texto** do lote — o envio é manual (copiar/colar). Depois o admin registra o **prazo informado pela síndica** e marca como resolvido. Moradores veem um **mural** com tudo que já foi encaminhado, para dar transparência e evitar duplicatas.

### Decisões tomadas com o usuário

| Tema | Decisão |
|---|---|
| Visibilidade | Mural público (todos os moradores) com chamados **Encaminhados/Resolvidos**, sem autor/unidade; cada morador vê os **próprios** chamados em qualquer status |
| Tipo/subtipo | Cadastráveis pelo admin (tela de catálogo) |
| Local | Cadastrável pelo admin + complemento livre opcional |
| Encaminhamento | Lote semanal; app gera texto consolidado; envio manual |
| Resposta da síndica | **Uma** resposta por chamado (texto livre, editável pelo admin a qualquer momento após encaminhar), visível aos moradores no detalhe e no mural |
| Notificação WhatsApp ao autor | **Fora do escopo** (fase 2) |

### Fora do escopo (YAGNI)

- WhatsApp automático (autor ou síndica).
- Login próprio da síndica.
- Fotos no texto do lote.
- Comentários/conversa no chamado.
- Votos "também tenho esse problema".
- Extração de componente de fotos compartilhado com classificados.

## 2. Fluxo de status

```
ABERTO ──► EM_ANALISE ──► PRONTO ──► ENCAMINHADO ──► RESOLVIDO
   │           │            │
   └───────────┴────────────┴──► RECUSADO (motivo obrigatório)
```

| Status (enum `ComplaintStatus`) | Quem move | Regras |
|---|---|---|
| `OPEN` | morador cria | autor pode editar e cancelar (soft delete) |
| `IN_REVIEW` | admin `startReview()` | admin pode editar campos |
| `READY` | admin `markReady()` | elegível a lote; admin ainda pode editar ou voltar para `IN_REVIEW` (`reopenReview()`) |
| `FORWARDED` | admin via lote `forward(batch)` | aparece no mural; prazo pode ser definido/alterado |
| `RESOLVED` | admin `resolve(note?)` | a partir de `FORWARDED`; terminal |
| `REJECTED` | admin `reject(reason)` | a partir de `OPEN`/`IN_REVIEW`/`READY`; motivo obrigatório (≤500), visível ao autor; terminal |

Transição inválida → `ComplaintStateException` → **409**.

## 3. Modelo de dados (migration `V41__complaints.sql`, expand-only)

Confirmar o número da migration na implementação (pode haver outra em paralelo). `-- flyway:transactional=true`. PKs `uuid default gen_random_uuid()`. Todas as entidades (exceto `complaint_event`) com soft delete (`deleted_at` + `@SQLDelete`/`@SQLRestriction`) e colunas de auditoria padrão do projeto.

- **`complaint_type`**: `id`, `name varchar(80)` (único entre não-deletados), `active boolean default true`, `sort_order int`.
- **`complaint_subtype`**: `id`, `type_id → complaint_type`, `name varchar(80)` (único por tipo), `active`, `sort_order`.
- **`complaint_location`**: `id`, `name varchar(80)` (único), `active`, `sort_order`.
- **`complaint`**:
  - `id`, `code bigint` (sequence `complaint_code_seq`, exibido `#123`)
  - `author_user_id`, `author_unit_id`
  - `type_id`, `subtype_id`, `location_id`
  - `location_detail varchar(200) null`, `description varchar(2000) not null`
  - `status varchar(20)`, `due_date date null`
  - `rejection_reason varchar(500) null`, `resolution_note varchar(500) null`
  - `syndic_response varchar(2000) null`, `syndic_response_at timestamptz null` (resposta da síndica redigida pelo admin; vazio = sem resposta)
  - `batch_id → complaint_batch null`, `forwarded_at`, `resolved_at`, timestamps.
  - Índices: `(status, created_at)`, `(author_user_id)`.
- **`complaint_photo`**: `id`, `complaint_id`, `object_key`, `content_type`, `size_bytes`, `sort_order`. Máx. 3 por chamado (regra no domínio).
- **`complaint_batch`**: `id`, `sent_by_user_id`, `sent_at`, `rendered_text text`, `complaint_count int`.
- **`complaint_event`** (log imutável; hard delete permitido → adicionar à lista de exceções do CLAUDE.md): `id`, `complaint_id`, `actor_user_id`, `kind varchar(30)` (`CREATED`, `EDITED`, `STATUS_CHANGED`, `DUE_DATE_SET`, `RESPONSE_SET`, `PHOTO_ADDED`, `PHOTO_REMOVED`), `from_value`, `to_value`, `created_at`. Nunca guardar PII em `from/to`.

Seed: permissões novas + vínculo ao role admin; catálogo inicial sugerido (editável depois):

- Tipos: Manutenção (Pintura, Elétrica, Hidráulica, Alvenaria, Elevador, Portões), Limpeza (Áreas comuns, Garagem), Segurança (Portaria, Câmeras, Iluminação), Convivência (Barulho, Uso indevido), Outros (Outros).
- Locais: Torre 1, Torre 2, Torre 3, Garagem, Área de lazer, Piscina, Academia, Salão de festas, Portaria, Fachada, Jardim.

Desativar tipo/subtipo/local **não altera** chamados existentes; só remove a opção no formulário.

## 4. Permissões

- `COMPLAINT_MANAGE` — fila admin, análise, edição, recusa, lote, prazo, resolução.
- `COMPLAINT_CATALOG_MANAGE` — CRUD de tipos/subtipos/locais.
- Abrir chamado, "meus chamados" e mural: usuário autenticado **com unidade vinculada** (sem unidade → 403).
- Sempre `hasAuthority(...)`, nunca `hasRole`.

## 5. API

### Morador — `/api/complaints`

| Método | Rota | Descrição |
|---|---|---|
| GET | `/catalog` | tipos (com subtipos) e locais **ativos** |
| POST | `/` | cria (`typeId`, `subtypeId`, `locationId`, `locationDetail?`, `description`) → `OPEN` |
| POST | `/{id}/photos` | multipart, ≤1MB, JPEG/PNG/WebP por magic-bytes, máx. 3 |
| DELETE | `/{id}/photos/{photoId}` | só autor, só `OPEN` |
| GET | `/{id}/photos/{photoId}/url` | URL assinada; autor, admin, ou qualquer morador se chamado está no mural |
| GET | `/mine` | chamados do autor, todos os status, com motivo/prazo/nota |
| PUT | `/{id}` | edita; só autor e só `OPEN` |
| DELETE | `/{id}` | cancela (soft delete); só autor e só `OPEN` |
| GET | `/board` | mural: `FORWARDED` + `RESOLVED`, paginado, filtros `typeId`, `locationId`, `status`; encaminhados primeiro (por `forwarded_at desc`), depois resolvidos |
| GET | `/board/{id}` | detalhe no mural |

O DTO do mural (`ComplaintBoardView`) **não contém** autor nem unidade. Chamado alheio fora do mural → 404 (não vazar existência).

### Admin — `/api/admin/complaints` (`COMPLAINT_MANAGE`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/` | fila paginada, filtro por status (default: `OPEN`,`IN_REVIEW`,`READY`); inclui autor e unidade |
| GET | `/{id}` | detalhe + histórico de eventos |
| POST | `/{id}/start-review` | `OPEN → IN_REVIEW` |
| PUT | `/{id}` | edita tipo/subtipo/local/detalhe/descrição (`IN_REVIEW` ou `READY`); gera `EDITED` |
| POST | `/{id}/ready` | `IN_REVIEW → READY` |
| POST | `/{id}/reopen-review` | `READY → IN_REVIEW` |
| POST | `/{id}/reject` | `{reason}` |
| POST | `/batches/preview` | `{ids}` → texto renderizado (não persiste) |
| POST | `/batches` | `{ids}` → cria lote, todos `READY → FORWARDED` numa transação; algum id fora de `READY` → 409 e nada muda |
| GET | `/batches` | histórico de lotes (data, quem, qtde, texto) |
| PUT | `/{id}/due-date` | `{dueDate}`; só `FORWARDED`; não pode ser no passado; gera `DUE_DATE_SET` |
| PUT | `/{id}/response` | `{text}` (≤2000; vazio limpa); só `FORWARDED`/`RESOLVED`; atualiza `syndic_response_at`; gera `RESPONSE_SET` (sem o conteúdo no evento) |
| POST | `/{id}/resolve` | `{note?}`; `FORWARDED → RESOLVED` |

### Catálogo — `/api/admin/complaint-catalog` (`COMPLAINT_CATALOG_MANAGE`)

CRUD de tipos, subtipos e locais: listar (incluindo inativos), criar, renomear, reordenar, ativar/desativar. Nome duplicado → 409.

### Erros

- Validação → 400.
- Subtipo não pertence ao tipo ou item inativo → 422.
- Transição inválida → 409.
- Não encontrado ou sem acesso → 404.
- Mapeados no `GlobalExceptionHandler`.

## 6. Backend — componentes

Pacote `feature/complaint` (+ subpacote `catalog`):

- **`Complaint`** (domínio rico): métodos de transição, `addPhoto()` (limite 3), `editByAuthor()`, `editByAdmin()`, `setDueDate()`; cada um registra `ComplaintEvent`.
- **`ComplaintService`** (morador), **`ComplaintAdminService`**, **`ComplaintBatchService`**, **`ComplaintCatalogService`** — `@Transactional` só aqui.
- **`ComplaintPhotoService`**: upload ao MinIO **fora** da transação (mesmo padrão de `ClassifiedService`: valida magic-bytes → sobe objeto → persiste metadados; falha ao persistir → remove objeto).
- **`ComplaintBatchTextRenderer`**: puro, testável. Formato:
  ```
  Chamados — HELBOR TRILOGY HOME
  Lote de 17/10/2026 (5 chamados)

  MANUTENÇÃO
  1. #123 Pintura — Torre 2 (hall do 5º andar)
     Parede descascando próximo ao elevador.
  2. #127 Elétrica — Garagem
     Lâmpada queimada na vaga G1-045.

  LIMPEZA
  3. #130 ...
  ```
  Ordena por tipo (`sort_order`) e depois `code`. Sem nome/unidade do autor. Data em `America/Sao_Paulo`.
- Controllers com `@ConditionalOnProperty`/guarda da flag (flag off → 404, como as demais).
- Logs: nunca descrição nem dados do autor; usar ids.

## 7. Frontend — `features/complaints`

Rotas e páginas (mobile-first, shadcn/ui, touch ≥44px, WCAG AA):

- **`/chamados`** — abas **Mural** e **Meus chamados**; botão "Abrir chamado".
  - Mural: cards com tipo › subtipo, local, trecho da descrição, miniatura, badge de status, "Prazo: dd/MM" quando houver; filtros por tipo/local/status.
  - Detalhe e card mostram **"Resposta da síndica"** (com data da última atualização) quando houver.
  - Meus chamados: todos os status; recusado mostra o motivo; `OPEN` permite editar/cancelar.
- **`/chamados/novo`** e **`/chamados/:id/editar`** — selects encadeados tipo → subtipo, select de local + complemento, descrição com contador (2000), até 3 fotos comprimidas com `browser-image-compression` (alvo ≤1MB, recusa no cliente se ainda >1MB), preview e remoção.
- **`/chamados/:id`** — detalhe com galeria.
- **`/admin/chamados`** — fila com abas por status; detalhe com edição inline, ações (Iniciar análise, Pronto, Recusar com motivo, Definir prazo, Resposta da síndica, Resolver) e histórico.
- **`/admin/chamados/encaminhar`** — lista `READY` com checkboxes (todos marcados por padrão), "Gerar texto" → textarea somente-leitura + **Copiar**; "Confirmar envio" (diálogo de confirmação) → cria lote. Abaixo, histórico de lotes com texto.
- **`/admin/chamados/catalogo`** — tipos (com subtipos aninhados) e locais; criar, renomear, ativar/desativar, reordenar.
- Itens de menu condicionados à flag e às permissões.
- Datas com `date-fns-tz` em `America/Sao_Paulo`.

## 8. Testes (TDD)

- **Domínio** (`ComplaintTest`): toda transição válida/inválida, limite de 3 fotos, editar só em `OPEN` pelo autor, prazo só em `FORWARDED` e não passado, motivo obrigatório na recusa, eventos registrados.
- **`ComplaintBatchTextRendererTest`**: agrupamento, ordenação, ausência de dados do autor, data no fuso de SP.
- **Services** (Mockito): lote atômico (um id inválido → nada muda), subtipo de outro tipo → 422, item inativo → 422, autor ≠ usuário → 404, usuário sem unidade → 403.
- **Web** (`@WebMvcTest` + `MockAuth`): permissões de cada endpoint admin/catálogo; mural sem campos de autor; flag off → 404.
- **Repository** (Testcontainers, quando disponível): query do mural com ordenação e filtros; unicidade do catálogo com soft delete.
- **Frontend** (vitest + RTL): formulário (subtipo depende do tipo, limite de fotos, contador), mural, tela de encaminhar (gerar/copiar/confirmar).
- **E2E** (Playwright, API mockada): morador abre chamado com foto; admin analisa, encaminha lote e define prazo; mural mostra prazo.
- Rodar `npm run build` antes do push.

## 9. Entrega (trunk-based, PRs ≤400 linhas, flag off)

1. Migration + entidades do catálogo + CRUD do catálogo (backend).
2. Entidade `Complaint` + domínio + criação/edição/"meus" (backend).
3. Fotos (backend).
4. Fila admin + transições + prazo (backend).
5. Lote: renderer + preview + criação (backend).
6. Mural (backend).
7. Frontend morador (formulário, meus, mural).
8. Frontend admin (fila, detalhe, encaminhar, catálogo).
9. E2E + ligar flag em HML.

## 10. Segurança (STRIDE resumido)

- **Spoofing/Elevation**: autoria vem do token, nunca do body; ações admin por permission.
- **Tampering**: autor só edita em `OPEN`; edições admin geram evento auditável.
- **Repudiation**: `complaint_event` + `complaint_batch.sent_by_user_id`.
- **Information disclosure**: mural sem autor/unidade; 404 para chamados alheios; URL de foto assinada e curta; fotos podem mostrar áreas privadas → o formulário avisa "não inclua pessoas ou documentos".
- **DoS**: limite de 3 fotos × 1MB; validação de tamanho antes do upload; rate limit existente.
