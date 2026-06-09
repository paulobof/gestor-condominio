# Design — A2 (parte 1): Contatos úteis com horário de funcionamento

**Data:** 2026-06-08
**Branch:** `feat/contatos`
**Feature flag:** `app.feature.contacts.enabled` (default `false`), espelhando `announcements`/`faq`.
**Âncora:** seções 6.5 / data model `contact` + `*_opening_hours` do design `2026-05-24`. Padrões: backend = `announcement`/`faq` + `recommendation` (opening hours); permissão `CONTACT_MANAGE` (id 6, já semeada na V6 — só MANAGER).

## Escopo

Telefones úteis do condomínio (portaria, zelador, emergência, gás, etc.): nome, categoria, telefone clicável, observações, e **horário de funcionamento por dia da semana** com indicador **"Aberto agora"** (fuso `America/Sao_Paulo`). Esta parte também **constrói os componentes reutilizáveis** `OpeningHoursEditor` e `OpeningHoursDisplay`, que a parte A2-Links reusará.

**Decisões (do usuário):** horário **por dia + "aberto agora"**; escopo desta entrega = **Contatos** (Links vêm no próximo PR).

## Backend

- **`Contact`** (tabela `contact`): `id`, `version`, `name` (varchar 120), `category` (varchar 60), `phone` (varchar 20), `notes` (text, nullable), `is24h` (boolean, col `is_24h`), soft delete (`deleted_at`) + audit (`created_at`/`updated_at`). `@SQLDelete`/`@SQLRestriction`, Lombok como nas demais entidades. Métodos: `create(...)`, `edit(...)`.
- **`ContactOpeningHours`** (tabela `contact_opening_hours`): espelha `RecommendationOpeningHours`. Campos: `id`, `ownerId` (col `owner_id`), `dayOfWeek` (smallint, col `day_of_week`), `opensAt` (time, `opens_at`), `closesAt` (time, `closes_at`), `notes` (varchar 120). **Sem soft delete** (CASCADE; está na lista de hard-delete permitido do CLAUDE.md). `create(ownerId, dayOfWeek, opensAt, closesAt, notes)`.
- **Migration `V20__contacts.sql`**:
  - `contact` (PK `gen_random_uuid()`, soft delete, índice por `category`).
  - `contact_opening_hours` (`owner_id uuid NOT NULL REFERENCES contact(id) ON DELETE CASCADE`, `day_of_week smallint NOT NULL CHECK (day_of_week BETWEEN 0 AND 6)`, `opens_at time`, `closes_at time`, `notes varchar(120)`, índice `(owner_id, day_of_week)`). **Sem** insert de permissão (CONTACT_MANAGE id 6 já existe + grant a MANAGER na V6).
- **Repositórios:** `ContactRepository` (`findAllByOrderByCategoryAscNameAsc()`); `ContactOpeningHoursRepository` (`findByOwnerIdOrderByDayOfWeekAsc(UUID)`, `deleteByOwnerId(UUID)` — `@Modifying`).
- **DTOs:** `OpeningHoursDto(@Min(0)@Max(6) int dayOfWeek, LocalTime opensAt, LocalTime closesAt, String notes)` (próprio do contact, espelhando o de recommendation); `CreateContactRequest(name, category, phone, notes, boolean is24h, List<OpeningHoursDto> openingHours)` com validações (`@NotBlank` name/category/phone; `@Size`); `UpdateContactRequest` (idem); `ContactView(id, name, category, phone, notes, is24h, List<OpeningHoursDto> openingHours, Instant updatedAt)`.
- **`ContactService`** (`@Transactional`): `list()` (carrega cada contact + seus opening hours via repo, monta `ContactView`); `create(req)` (salva contact, depois salva os `ContactOpeningHours` com `ownerId`); `update(id, req)` (edita contact, **substitui** opening hours: `deleteByOwnerId` + re-salva); `delete(id)` (soft delete do contact; as horas somem por CASCADE quando o registro físico sumir — como é soft delete, manter as horas é inócuo, mas o filtro de leitura ignora contatos deletados). Mirar `RecommendationService` no trato das opening hours. `ContactException("NOT_FOUND", ...)` + handler no `GlobalExceptionHandler` (mapear 404/400, como as outras features).
- **`ContactController`** (`/api/contacts`, `@ConditionalOnProperty(app.feature.contacts.enabled)`): `GET` (`isAuthenticated()`); `POST`/`PUT/{id}`/`DELETE/{id}` (`hasAuthority('CONTACT_MANAGE')`).
- **Flag:** `app.feature.contacts.enabled: ${APP_FEATURE_CONTACTS_ENABLED:false}` em `application.yml`; `APP_FEATURE_CONTACTS_ENABLED=true` no `dokploy-backend.env.example`.
- **Testes:** `ContactServiceTest` (unit, repos mockados: create salva contact + horas; update substitui horas; list monta view com horas; delete; NOT_FOUND). `ContactControllerWebTest` (`@WebMvcTest` flag on, MockAuth): GET autenticado 200; POST sem `CONTACT_MANAGE` 403; POST com auth + body válido 201; validação 400; NOT_FOUND 404; anônimo rejeitado.

## Frontend

**Componentes reutilizáveis novos** (`src/components/openinghours/`):
- **`OpeningHoursEditor.tsx`** — props `value: OpeningHoursDto[]`, `is24h: boolean`, `onChange`. 7 linhas (Dom→Sáb, 0..6), cada uma com inputs `time` abre/fecha ou marcação "Fechado" (sem horário = fechado). Checkbox global "Aberto 24 horas" que, quando ligado, desabilita a grade e zera as horas. Produz `OpeningHoursDto[]` (só dias com horário). Acessível (labels por dia, alvos ≥44px).
- **`OpeningHoursDisplay.tsx`** — props `openingHours: OpeningHoursDto[]`, `is24h: boolean`. Mostra um chip por dia (hoje destacado) e calcula **"Aberto agora" / "Fechado"** com `date-fns-tz` no fuso fixo `America/Sao_Paulo` (independe do fuso do browser). Colapsado por padrão (expande). Se `is24h`, mostra "24 horas".
- **`openingHours.ts`** (util) — tipo `OpeningHoursDto`, labels dos dias (PT-BR), e `isOpenNow(openingHours, is24h, nowZoned): boolean` (puro, testável sem relógio real — recebe o "agora" já no fuso). Verificar/instalar `date-fns-tz` (sancionado pelo CLAUDE.md) se ausente.

**Contatos:**
- **`contactsApi.ts`**: tipos `Contact`, `ContactBody`; `listContacts`, `createContact`, `updateContact`, `deleteContact`.
- **`/contatos` → `ContactsPage`**: cards (nome, categoria, telefone como `tel:`, badge "Aberto agora"/"Fechado", `OpeningHoursDisplay`). "Gerenciar" → `/contatos/gerenciar` só com `CONTACT_MANAGE`. Estado vazio amigável.
- **`/contatos/gerenciar` → `ContactsAdminPage`**: lista + form (nome, categoria, telefone, notes, checkbox 24h, `OpeningHoursEditor`); criar/editar/excluir; toasts.
- **Sidebar:** item **"Contatos"** → `/contatos` (todos).
- **Rotas** em `router.tsx`.
- **Testes:** `openingHours.test.ts` (isOpenNow: dentro/fora do horário, dia fechado, 24h, virada de meia-noite simples); `OpeningHoursEditor.test.tsx` (24h desabilita grade; editar um dia emite onChange); `ContactsPage.test.tsx` (lista contatos, telefone `tel:`, gate Gerenciar); `ContactsAdminPage.test.tsx` (cria contato chamando createContact).

## Fora de escopo

Links úteis (próximo PR, reusa os componentes de horário); notificação; histórico de horários.

## Riscos / notas

- **PR grande** (entidade + child table + editor/display + 2 páginas). Provavelmente > 400 linhas — aceitável por ser feature coesa; se necessário, separar `ContactsAdminPage` num PR seguinte.
- **"Aberto agora" sem flakiness:** a lógica de fuso fica numa função pura `isOpenNow(..., nowZoned)`; os testes passam um "agora" fixo. O componente injeta o `now` real só na borda.
- `OpeningHoursDto` é duplicado (contact vs recommendation) para isolar as features; consolidar num módulo compartilhado é um refactor futuro (fora de escopo).
