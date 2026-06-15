# Aluguel de Vagas — anúncios de aluguel de vaga entre moradores

**Data:** 2026-06-14
**Status:** RASCUNHO — aguardando revisão do Paulo.
**Branch de referência:** `main`

---

## 1. Contexto e motivação

Moradores do HELBOR TRILOGY HOME que possuem vaga de garagem ociosa querem
alugá-la para outros moradores. Hoje isso acontece informalmente (grupos de
WhatsApp, cartazes). A feature **Aluguel de Vagas** dá um quadro de anúncios
autosserviço dentro do portal: quem tem vaga publica um anúncio com torre, andar,
numeração e valor mensal; quem procura encontra na lista e fala direto com o
anunciante pelo WhatsApp.

Esta feature é o primeiro de dois itens sob uma nova aba **"Vagas"** no menu:

- **Aluguel de Vagas** — *esta spec*.
- **Escolha de Vaga** — seleção interativa do catálogo de 599 vagas;
  **planejada em spec separada**. Aqui só reservamos o slot de menu.

### 1.1 Decisões tomadas no brainstorming

| Tema | Decisão |
|---|---|
| Identificação da vaga | **Texto livre** (torre/andar/numeração digitados). Sem vínculo com o catálogo de vagas — mantém a feature desacoplada da "Escolha de Vaga". |
| Contato | **Puxado do perfil** do anunciante (nome + telefone) + botão **"Falar no WhatsApp"** na tela de detalhe. Sem campos de contato no formulário. |
| Campos | **Mínimo**: torre, andar, numeração, valor mensal. Sem título, descrição, fotos ou tipo de vaga. |
| Gestão | **Autosserviço**, igual Classificados: morador cria/edita/encerra o próprio anúncio; síndico/admin modera. Sem aprovação prévia. |
| Menu | **Grupo expansível** "Vagas" na sidebar, com os dois sub-itens. |

### 1.2 Feature de referência: Classificados

Esta feature espelha de perto a de **Classificados** (`feature.classified`,
migration `V15`), que já implementa o mesmo formato de quadro de anúncios
moderado. Reusamos os padrões já estabelecidos lá:

- Entidade com soft delete (`@SQLDelete` + `@SQLRestriction`), `@Version`,
  `status` enum, `author_user_id`, `@PreUpdate` para `updated_at`.
- Controller sob `@ConditionalOnProperty(name = "app.feature.<nome>.enabled")`.
- Autorização `isAuthenticated()` para leitura/criação; ownership **ou**
  permissão de moderação para mutar anúncios alheios.
- Frontend em `frontend/src/features/<nome>/` com `api/`, `pages/`
  (List, Form, Detail) e testes espelhados.

A diferença principal: **não há fotos** (logo, nenhuma tabela `*_photo`, nenhum
upload, nenhum magic-bytes) e **não há título nem descrição** — os campos são só
os enumerados acima.

---

## 2. Navegação

### 2.1 Sidebar — grupo expansível (`Sidebar.tsx`)

Hoje a sidebar é uma **lista plana** de `NavItem`. Será refatorada para suportar
**grupos expansíveis** além de itens simples. O novo grupo:

```
Vagas  (ícone SquareParking)        ▸ / ▾   ← expande/recolhe
   ├─ Aluguel de Vagas   → /vagas/aluguel
   └─ Escolha de Vaga    → /vagas/escolha   (desabilitado, "Em breve")
```

Comportamento:

- O grupo expande/recolhe ao clicar; estado de aberto/fechado mantido em estado
  local do componente. Abre automaticamente quando a rota ativa pertence ao grupo.
- Sub-item ativo segue o mesmo destaque por `brand` dos itens atuais.
- Acessibilidade: o botão de grupo usa `aria-expanded`; sub-itens são `NavLink`.
  Touch target ≥44px mantido. Funciona igual no drawer mobile.
- **"Escolha de Vaga"** é renderizada como item desabilitado com selo "Em breve"
  enquanto a outra spec não entregar a rota `/vagas/escolha`.
  *(Ponto a confirmar — alternativa: só adicionar o sub-item quando a feature existir.)*

A refatoração introduz no tipo de navegação a noção de grupo, por exemplo:

```ts
type NavEntry =
  | { kind: 'item'; to: string; label: string; icon; brand; requires? }
  | { kind: 'group'; label: string; icon; brand; children: NavItem[] };
```

Os itens existentes permanecem como `kind: 'item'` — mudança aditiva, sem alterar
o comportamento atual dos demais itens.

### 2.2 Home (`App.tsx`)

Adicionar um card **"Vagas"** ao grid da home, levando a `/vagas/aluguel`
(ou a uma página `/vagas` de entrada — ver abaixo), para manter consistência com
os demais atalhos. *(Ponto a confirmar — alternativa: acesso só pela sidebar.)*

### 2.3 Rotas (frontend)

| Rota | Página |
|---|---|
| `/vagas/aluguel` | Lista de anúncios |
| `/vagas/aluguel/novo` | Formulário de criação |
| `/vagas/aluguel/:id` | Detalhe do anúncio |
| `/vagas/aluguel/:id/editar` | Formulário de edição |
| `/vagas/escolha` | *(outra spec)* |

Não há página `/vagas` própria nesta spec; o grupo da sidebar leva direto aos
sub-itens. Se o card da home apontar para `/vagas`, redirecionar para
`/vagas/aluguel` por ora.

---

## 3. Modelo de dados

### 3.1 Migration `V32__parking_rental.sql`

> Próxima versão livre: a última migration é `V31__recommendation_links_owner.sql`.

```sql
-- flyway:transactional=true

CREATE TABLE parking_rental (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint        NOT NULL DEFAULT 0,
    tower           varchar(40)   NOT NULL,
    floor           varchar(20)   NOT NULL,
    spot_number     varchar(40)   NOT NULL,
    monthly_price   numeric(12,2) NOT NULL,
    status          varchar(20)   NOT NULL,
    author_user_id  uuid          NOT NULL REFERENCES "user" (id) ON DELETE RESTRICT,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz,
    deleted_at      timestamptz
);

CREATE INDEX idx_parking_rental_status
    ON parking_rental (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_parking_rental_author
    ON parking_rental (author_user_id) WHERE deleted_at IS NULL;
```

Notas:

- `tower`, `floor`, `spot_number` são **texto livre** — `floor` é varchar (não
  inteiro) para aceitar `-1`, `Térreo`, etc.
- `monthly_price` `numeric(12,2)`, igual ao `price` de `classified`.
- A FK `author_user_id` referencia a tabela de usuários `"user"` (palavra
  reservada — sempre entre aspas no SQL), igual a `classified.author_user_id`.
- Soft delete via colunas; `@SQLRestriction("deleted_at IS NULL")` na entidade.
  Índices parciais filtram `deleted_at IS NULL`.

### 3.2 Migration de permissão `V33__permission_parking_rental_moderate.sql`

Seed da permissão `PARKING_RENTAL_MODERATE` e atribuição ao(s) papel(éis) que já
têm `CLASSIFIED_MODERATE` (síndico/admin). Espelhar a migration que criou
`CLASSIFIED_MODERATE`. Tabelas de junção `role_permission` permitem hard delete
(M:N puro), conforme CLAUDE.md.

---

## 4. Backend

### 4.1 Pacote e entidade

Pacote `br.com.condominio.feature.parkingrental`. Entidade `ParkingRental`
espelhando `Classified` (Lombok `@Getter`, `@Setter(PROTECTED)`,
`@EqualsAndHashCode(of="id")`, `@ToString(of={"id","status"})`,
`@SQLDelete`/`@SQLRestriction`, `@Version`, `@PreUpdate`).

```java
public static ParkingRental create(
    UUID authorUserId, String tower, String floor,
    String spotNumber, BigDecimal monthlyPrice) { ... status = ACTIVE; }

public void edit(String tower, String floor, String spotNumber, BigDecimal monthlyPrice) { ... }

public void markRented()  { /* só ACTIVE -> RENTED */ }
public void archive()     { /* != ARCHIVED -> ARCHIVED */ }
public void reactivate()  { /* != ACTIVE -> ACTIVE */ }
```

`ParkingRentalStatus`: `ACTIVE`, `RENTED`, `ARCHIVED`.

### 4.2 Controller e endpoints

`@RequestMapping("/api/parking-rentals")`,
`@ConditionalOnProperty(name = "app.feature.parkingrental.enabled", havingValue = "true")`.

| Método | Rota | Autorização | Descrição |
|---|---|---|---|
| GET | `/api/parking-rentals?status=&page=&size=` | `isAuthenticated()` | Lista paginada (default size 20, máx 100) |
| GET | `/api/parking-rentals/{id}` | `isAuthenticated()` | Detalhe |
| POST | `/api/parking-rentals` | `isAuthenticated()` | Cria (autor = logado) |
| PUT | `/api/parking-rentals/{id}` | dono **ou** `PARKING_RENTAL_MODERATE` | Edita campos **e** status (`status` no corpo) |
| DELETE | `/api/parking-rentals/{id}` | dono **ou** moderador | Soft delete |

> **Mudança de status** segue o padrão de `ClassifiedController`: não há endpoint
> dedicado — o `status` viaja no corpo do `PUT` (`UpdateParkingRentalRequest`) e o
> service aplica a transição (`markRented`/`archive`/`reactivate`) se mudou.

Ownership e moderação verificados no **service** (igual `ClassifiedService`):
o controller passa `me.userId()` e `canModerate(me)`.

### 4.3 DTOs

- `CreateParkingRentalRequest(tower, floor, spotNumber, monthlyPrice)` — todos
  obrigatórios. `@NotBlank` nos textos (`@Size` máximos coerentes com as colunas),
  `@NotNull` + `@DecimalMin("0.00")` (ou `@Positive`) no valor.
- `UpdateParkingRentalRequest` — mesmos campos.
- `UpdateStatusRequest(status)` — enum alvo (ou endpoints/ações dedicadas; decidir
  na implementação — `PATCH .../status` com corpo `{ "status": "RENTED" }`).
- `ParkingRentalView(id, tower, floor, spotNumber, monthlyPrice, status,
  authorUserId, authorName, authorPhone, createdAt)`.

### 4.4 Resolução de contato (nome + telefone do autor)

`authorName` e `authorPhone` no `ParkingRentalView` são resolvidos a partir do
**perfil do autor** (projeção via `UserRepository`), não armazenados em
`parking_rental`. O service popula esses campos ao montar a view.

- O telefone retornado é exibido como botão **"Falar no WhatsApp"** no frontend
  (`https://wa.me/<telefone>`), com o número **normalizado para DDI** via
  `PhoneNumberNormalizer` (mesmo utilitário do envio WhatsApp).
- **PII:** o telefone do anunciante fica visível a qualquer morador logado. É uma
  escolha deliberada (o anunciante quer ser contatado). Nunca logar `authorPhone`
  nem `authorName` (`LogSanitizer` se aparecerem em trace). *(Confirmado no
  brainstorming.)*
- Se o autor não tiver telefone no perfil, `authorPhone = null` e o botão de
  WhatsApp não é renderizado.

### 4.5 Service

`@Transactional` nos métodos de escrita; leitura `@Transactional(readOnly = true)`.
Sem upload, sem chamadas externas dentro de transação. `AuditorAware` padrão.
Erros de domínio → `ParkingRentalException` mapeada para 4xx (espelhar
`ClassifiedException`).

---

## 5. Frontend

`frontend/src/features/parking-rentals/` espelhando `classifieds/`:

```
parking-rentals/
  api/parkingRentalsApi.ts        (+ .test.ts)
  pages/ParkingRentalsListPage.tsx   (+ .test.tsx)
  pages/ParkingRentalFormPage.tsx    (+ .test.tsx)
  pages/ParkingRentalDetailPage.tsx  (+ .test.tsx)
```

### 5.1 Lista (`/vagas/aluguel`)

- Cards: **"Torre {tower} · Andar {floor} · Vaga {spotNumber}"** + **"R$ {valor}/mês"**
  (formatação BRL via `Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })`).
- Selo de status (Ativa / Alugada / Arquivada). Filtro por status.
- Botão "Anunciar vaga" → `/vagas/aluguel/novo`.
- Estado vazio amigável.

### 5.2 Formulário (`/novo` e `/:id/editar`)

- 4 campos: Torre, Andar, Numeração, Valor mensal.
- Valor com máscara/format BRL na exibição; envia número ao backend.
- Validação client-side espelhando o backend (obrigatórios; valor > 0).
- Sem upload de foto.

### 5.3 Detalhe (`/:id`)

- Exibe torre, andar, numeração, valor mensal, status e data.
- **Botão "Falar no WhatsApp"** quando há telefone do autor (`wa.me`). Mostra o
  nome do anunciante.
- Ações para dono/moderador: Editar, Marcar como alugada, Arquivar/Reativar, Excluir.

### 5.4 Convenções de UI

Mobile-first, touch targets ≥44px, WCAG AA, shadcn/ui + Tailwind, fontes Outfit /
Work Sans — conforme CLAUDE.md.

---

## 6. Testes (TDD — testes antes da implementação)

### 6.1 Backend

- `ParkingRentalTest`: factory `create` inicia `ACTIVE`; guards de
  `markRented`/`archive`/`reactivate` (transições inválidas lançam).
- `ParkingRentalServiceTest`: CRUD; ownership (não-dono sem moderação → erro);
  moderador pode mutar anúncio alheio; resolução de `authorName`/`authorPhone`
  com `UserRepository` mockado (com e sem telefone).
- `ParkingRentalControllerWebTest` (`@WebMvcTest` + `MockAuth`): validações
  → 400; não-dono → 403; lista/detalhe felizes; `PATCH /status`.
- `ParkingRentalFeatureFlagOffWebTest`: com a flag off, rotas retornam 404
  (bean do controller ausente).

### 6.2 Frontend

- `parkingRentalsApi.test.ts`: tipos e chamadas.
- `ParkingRentalFormPage.test.tsx`: campos presentes, validação.
- `ParkingRentalDetailPage.test.tsx`: botão WhatsApp aparece com telefone,
  some sem; ações de dono/moderador.
- `ParkingRentalsListPage.test.tsx`: render dos cards, filtro de status, estado vazio.
- `Sidebar.test.tsx`: grupo "Vagas" expande e mostra os sub-itens; item
  "Escolha de Vaga" desabilitado.

---

## 7. Convenções e entrega

- **Feature flag** `app.feature.parkingrental.enabled` (default Prod=`false`).
  Ligar em HML via env do app no Dokploy.
- **Migrations** backward-compatible (expand/contract); `gen_random_uuid()`;
  cabeçalho `-- flyway:transactional=true`.
- **Sem `@Data`** em entidade; `@Transactional` só no service; nunca PII em log.
- **Trunk-based**, Conventional Commits, squash merge. PR ≤400 linhas — provável
  divisão em PRs (1: backend + migrations; 2: frontend + refatoração da sidebar).

---

## 8. Pontos em aberto (para a revisão do Paulo)

1. **Card "Vagas" na home (`App.tsx`)** — adicionar (default desta spec) ou
   acesso só pela sidebar?
2. **"Escolha de Vaga" no menu agora** — item "Em breve" desabilitado (default)
   ou só adicionar quando a outra spec entregar?
3. **Telefone do autor exposto** a qualquer morador logado via WhatsApp —
   confirmado no brainstorming; registrado aqui por ser PII.
4. ~~Nome da tabela de usuários~~ — **resolvido:** é `"user"` (igual classifieds).
5. ~~PATCH de status vs. ações dedicadas~~ — **resolvido:** status via corpo do
   `PUT`, sem endpoint dedicado, igual `ClassifiedController`.
