# Escolha de vagas na garagem — Design

**Data:** 2026-06-11
**Status:** aprovado (design)
**Contexto:** nova feature do HELBOR TRILOGY HOME. Permite que cada unidade escolha **online** sua(s) vaga(s) de garagem, na ordem de um **sorteio já realizado** (importado), com **mapa interativo fiel à planta** e **aviso por WhatsApp** quando chega a vez. O sorteio em si **não** faz parte desta feature — entra pronto.

## Problema

Hoje a escolha de vagas seria feita presencialmente/manualmente, com risco de erro, conflito de vagas e baixa transparência. Queremos um processo:

- guiado pela **ordem sorteada** (importada), unidade por unidade;
- **self-service**: o Proprietário escolhe a própria vaga num mapa que mostra o que está livre;
- **paralelo** entre os diferentes grupos de vagas, para encurtar o tempo total;
- **auditável** e operável por um síndico (monitorar, pular quem não responde, atribuir manualmente, encerrar).

## Domínio físico (do projeto da garagem)

- **3 torres**: A, B, C. Na planta da garagem aparecem como **TORRE 1 = A, TORRE 2 = B, TORRE 3 = C**.
- **4 pisos**: **−1, 0, 1, 2**.
- Cada vaga tem **código próprio** (ex.: `2232`), **tamanho** (P/M/G), pode ser **coberta ou descoberta**, e é **colorida por torre + categoria**:
  - **1 vaga** (apto 77 m², finais **3,4**) → tom claro da torre.
  - **2 vagas** (apto 94 m², finais **1,2,5,6**) → tom escuro da torre.
- **Vaga dupla**: um único número atende **2 posições** (ex.: vaga `2232` = posições `190A` + `190B`). Unidade de 2 vagas escolhe **uma vaga dupla** (par fixo), não duas avulsas.
- **Vagas de moto**: existem em número **limitado** (não há para todas as unidades) → processo/sorteio **separado**.
- **Vagas PCD**: demarcadas; ficam **reservadas só para unidades PCD** durante a rodada e **liberam como comuns ao encerrar**.

> A entitlement deriva da `position` da `unit` (já modelada): finais 1,2,5,6 → 2 vagas; finais 3,4 → 1 vaga.

## As 7 filas

A escolha roda em **7 filas independentes e paralelas**, cada uma com sua própria ordem sorteada:

| Fila | Torre | Categoria | Cor da planta |
|------|-------|-----------|---------------|
| A1 | A | 1 vaga | verde claro |
| A2 | A | 2 vagas | verde escuro |
| B1 | B | 1 vaga | laranja |
| B2 | B | 2 vagas | vermelho |
| C1 | C | 1 vaga | rosa |
| C2 | C | 2 vagas | roxo |
| Moto | (todas) | moto | — |

Uma unidade está em **exatamente uma fila de carro** (definida por torre + categoria) e **opcionalmente na fila de moto** (se sorteada). Como cada fila de carro mapeia para um **pool de vagas exclusivo** (tower × category), não há disputa de vaga entre filas distintas.

## Decisões de produto (confirmadas)

- **Quem escolhe:** o **Proprietário** (unit master, `isUnitMaster=true`) da unidade, em self-service. Unidades **sem proprietário cadastrado** são atribuídas pelo síndico (ação "Atribuir").
- **Restrição de escolha:** a unidade só enxerga/escolhe vagas onde `spot.tower == unit.tower` **E** `spot.category` casa com sua entitlement **E** `status = LIVRE` **E** (`spot.pcd == false` **OU** `unit.pcd == true`).
- **2 vagas = uma vaga dupla.** 1 vaga = uma vaga simples.
- **PCD:** atributo persistente no **cadastro da unidade** (`unit.pcd`). A **prioridade na ordem é resolvida pelo operador** ao montar o arquivo de sorteio (coloca os PCDs primeiro) — **sem lógica de "furar fila" no software**. O software mantém apenas a regra de **vaga PCD reservada** (só unidade PCD seleciona vaga PCD até o encerramento da rodada).
- **Vez não atendida:** a fila **fica parada** na unidade da vez até ela escolher. Só o **síndico pula** manualmente; a unidade pulada vai para o **fim da fila** e é reavisada quando chegar a vez de novo. **Sem timeout automático.**
- **Avanço da fila:** **automático no envio** (Abordagem 1). Ao submeter, a próxima unidade vira "da vez" e o WhatsApp dispara sozinho.
- **Moto:** fila separada, **arquivo de ordem próprio**, **iniciada manualmente** pelo síndico (tipicamente após as filas de carro).
- **Mapa fiel à planta (overlay):** a imagem da planta de cada piso é o **fundo**, e cada vaga clicável fica **posicionada na coordenada real**. Estados de cor:
  - 🔵 **azul** = disponível · 🔴 **vermelho** = já escolhida · ⚪ **cinza** = não é da torre/categoria da unidade · 🟢 **verde** = selecionada (antes de confirmar).

## Catálogo de vagas (produzido em dev time)

A digitalização **não é feita no software**. A partir dos **4 PDFs** das plantas (o PDF não tem texto extraível), o catálogo — **metadados + geometria de cada vaga** — é produzido **durante o desenvolvimento** e entregue como:

- **seed/migração** dos registros de `parking_spot` (incl. coordenadas), e
- **assets de frontend**: as 4 imagens de planta (fundo do overlay).

Fluxo de entrega: o usuário envia os 4 PDFs → o front é preparado já com as vagas reais → **testado localmente** → só então sobe. Não há tela admin de "desenhar vagas".

## Arquitetura

### Dados (novas tabelas, soft delete via `@SQLDelete` + `@SQLRestriction`)

**`parking_spot`** — catálogo (1 linha por vaga selecionável; uma dupla = 1 linha):
`id`, `version`, `floor` (smallint −1/0/1/2), `code`, `tower` (A/B/C, null p/ moto comum), `category` (`ONE`/`TWO`/`MOTO`), `kind` (`CAR`/`MOTO`), `size` (varchar P/M/G), `covered` (bool), `pcd` (bool), `is_double` (bool), `sub_positions` (varchar, ex.: `"190A,190B"`), geometria `img_x,img_y,img_w,img_h` (relativo à imagem do piso), `status` (`AVAILABLE`/`TAKEN`/`BLOCKED`), `assigned_unit_id` (uuid null) + colunas de auditoria/soft-delete.

**`parking_round`** — a rodada de escolha: `id`, `name`, `status` (`DRAFT`/`OPEN`/`CLOSED`), timestamps.

**`parking_queue`** — uma das 7 filas, por rodada: `id`, `round_id`, `tower` (null p/ moto), `category` (`ONE`/`TWO`/`MOTO`), `kind`, `status` (`OPEN`/`PAUSED`/`DONE`).

**`parking_queue_entry`** — unidade na fila: `id`, `queue_id`, `unit_id`, `order_index`, `state` (`WAITING`/`CURRENT`/`DONE`/`SKIPPED`), `notified_at`, `chosen_at`.

**`parking_assignment`** — resultado: `id`, `round_id`, `unit_id`, `spot_id`, `chosen_by_user_id`, `chosen_at`.

**`unit.pcd`** (boolean, default false) — atributo da unidade (migração backward-compatible, expand).

> `parking_queue_entry` e `parking_assignment` são **logs operacionais**; mas, por consistência com o resto do projeto, usam soft delete (exceto se enquadrados como log imutável puro — a confirmar no plano).

### Backend (`feature/parking`)

**Domínio rico** (`@Getter`, `@Setter(PROTECTED)`, sem `@Data`):
- `ParkingSpot` com métodos de mutação: `reserveFor(unit)`, `release()`, `block()`.
- `ParkingRound` / `ParkingQueue` com `open()`, `pause()`, `close()`, `advance()`.
- `ParkingQueueEntry` com `markCurrent()`, `markDone()`, `skipToEnd()`.

**`ParkingCatalogService`** — consulta do mapa: `floorPlan(roundId, unitId, floor)` retorna a imagem do piso + a lista de vagas com **estado calculado para aquela unidade** (AVAILABLE/TAKEN/OTHER/SELECTABLE), respeitando torre/categoria/PCD.

**`ParkingSelectionService`** (`@Transactional`):
- `currentSelection(unitId)` — em qual fila a unidade está e se é a vez dela.
- `submit(actorUserId, unitId, spotId)`:
  - valida **é a vez** da unidade (`entry.state == CURRENT`) → senão `NOT_YOUR_TURN`;
  - valida actor é **master** da unidade (ou tem `PARKING_MANAGE`) → senão `FORBIDDEN`;
  - valida a vaga é **selecionável** pela unidade → senão `SPOT_NOT_SELECTABLE`;
  - **trava otimista** (`version`) na vaga; se já OCUPADA → `SPOT_TAKEN`;
  - cria `parking_assignment`, marca vaga `TAKEN`, entry `DONE`;
  - **avança a fila**: próxima `WAITING` (menor `order_index`) vira `CURRENT` → evento `AFTER_COMMIT + @Async` enfileira WhatsApp.

**`ParkingAdminService`** (`@Transactional`, `PARKING_MANAGE`):
- `importOrder(queueRef, csv)` — importa a ordem (template CSV) de uma das 7 filas.
- `openRound` / `openQueue` / `pauseQueue` / `reopenQueue`.
- `skipCurrent(queueId)` → entry `SKIPPED`, recoloca no fim (`order_index = max+1`, `WAITING`), avança.
- `assignManually(queueId, unitId, spotId)` — atribui por uma unidade (cobre sem-proprietário).
- `closeRound()` → libera vagas PCD não usadas (`pcd → false` nas livres), congela resultado.
- Histórico/auditoria de **pulos e atribuições manuais** (quem, quando, qual unidade/vaga).

**`ParkingNotificationRenderer`** — texto renderizado **no backend** (`WhatsAppMessageRenderer`), com **link profundo** para a tela de seleção. Envio via **outbox** existente (`WhatsAppOutboxService` / retry). Telefone normalizado (`PhoneNumberNormalizer`). Sem PII em log.

**`ParkingController`**:
- `GET /api/parking/me` (`PARKING_SELECT`) — estado da minha vez (fila, é-minha-vez, entitlement).
- `GET /api/parking/floor/{floor}` (`PARKING_SELECT`) — mapa do piso filtrado para a minha unidade (imagem + vagas + estado).
- `POST /api/parking/select` (`PARKING_SELECT`) — body `{ spotId }`; `200` com confirmação.
- `GET /api/parking/admin/queues` (`PARKING_MANAGE`) — painel (progresso das 7 filas, unidade da vez, tempo de espera).
- `POST /api/parking/admin/queues/{id}/skip` · `/assign` · `/open` · `/pause` · `/reopen` (`PARKING_MANAGE`).
- `POST /api/parking/admin/order/import` (`PARKING_MANAGE`) — upload do CSV (fora da transação se houver storage; aqui é parse em memória).
- `POST /api/parking/admin/round/close` (`PARKING_MANAGE`).
- `GET /api/parking/admin/report` (`PARKING_MANAGE`) — alocação por torre/piso; export CSV/PDF.

### Permissions (RBAC por permission, nunca por role)

- **`PARKING_SELECT`** — Proprietário escolhe a própria vaga (concedida via role do morador).
- **`PARKING_MANAGE`** — síndico/admin: importar, operar filas, pular, atribuir, encerrar, relatório.

Endpoints com `@PreAuthorize("hasAuthority('PARKING_SELECT')")` / `('PARKING_MANAGE')`. Seed das permissions + vínculo às roles via migração.

### Frontend (`features/parking`)

- **`/vagas`** (rota do morador, gated por `PARKING_SELECT` + feature flag): tela de seleção com **overlay fiel** — imagem do piso de fundo, vagas posicionadas, abas de piso (−1/0/1/2), legenda de cores, contador de seleção, botão **Confirmar** (habilita só com a seleção completa: 1 simples ou 1 dupla). Mobile-first, touch ≥44px, WCAG AA. Deep link do WhatsApp cai aqui (exige login).
- **`/admin/vagas`** (gated por `PARKING_MANAGE`): painel das 7 filas (progresso, unidade da vez, alerta de espera longa), **Pular/Atribuir**, abrir fila de moto, painel **PCD** (reservadas/liberar ao encerrar), **importar ordem**, **relatório**.
- Overlay renderizado com as 4 imagens de planta como asset; vagas desenhadas por coordenada (`img_x/y/w/h`), cor por estado.

### Motor de filas (Abordagem 1)

Abrir fila → menor `order_index` vira `CURRENT` → WhatsApp. Submit válido → assignment + vaga `TAKEN` + entry `DONE` → próxima `CURRENT` + WhatsApp. Síndico pula → `SKIPPED` para o fim + avança. Concorrência tratada por **trava otimista na vaga** e pela invariante de **uma única `CURRENT` por fila**.

## Imports (templates)

- **Ordem (por fila):** template **CSV** fornecido pelo sistema com colunas `codigo_unidade, ordem`. O síndico preenche com o sorteio já feito (PCDs no topo) e importa por fila. **Moto** é arquivo à parte.
- Validação na importação: unidade existe, pertence à torre/categoria da fila, sem duplicidade de `ordem`, todas as unidades elegíveis presentes (aviso se faltar).

## Notificações (WhatsApp)

- Dispara quando a unidade vira **`CURRENT`** (texto no backend + deep link). Reenvio/lembrete = **botão manual** do síndico no painel. **Sem agendamento automático** no v1.

## Tratamento de erros

`NOT_YOUR_TURN` (409), `SPOT_NOT_SELECTABLE` (422), `SPOT_TAKEN` (409, corrida), `FORBIDDEN` (403), `ORDER_IMPORT_INVALID` (422, com detalhe de linha), `ROUND_NOT_OPEN` (409). Frontend mostra `message` em toast; na corrida de vaga, recarrega o mapa.

## Testes (TDD)

**Backend**
- `ParkingSelectionServiceTest`: vez certa/errada, filtro de selecionabilidade (torre/categoria/PCD), dupla vs simples, corrida de vaga (trava otimista), avanço de fila + evento de notificação.
- `ParkingAdminServiceTest`: import (válido/duplicado/torre errada), skip-para-o-fim, assign manual, close libera PCD, reopen.
- `ParkingControllerWebTest`: `PARKING_SELECT` vs `PARKING_MANAGE` (403 sem); contratos dos endpoints (200/409/422); flag off → 404.
- `RepositoryPostgresTest`: consultas do mapa e do progresso contra Postgres real.

**Frontend**
- `parkingApi.test.ts`: `getMyTurn`, `getFloor`, `select`, admin (queues/skip/assign/import/report).
- `ParkingSelectionPage.test.tsx`: estados de cor, troca de piso, confirmar habilita só com seleção completa, erro de corrida recarrega.
- `ParkingAdminPage.test.tsx`: progresso das filas, pular/atribuir, importar ordem, abrir moto, encerrar.

## Feature flag

`app.feature.parkingselection.enabled` (default prod = **false**). Frontend esconde rotas/menu quando off; backend retorna 404 nos endpoints.

## Entrega

Feature grande → **decompor em sub-PRs** ≤400 linhas (override consciente se necessário), provavelmente: (1) dados + domínio + catálogo/seed; (2) seleção do morador + mapa overlay; (3) motor de filas + notificação; (4) painel admin + import + relatório. TDD por tarefa. Validar em HML atrás da flag antes de prod.

## Fora de escopo (YAGNI) no v1

- O **sorteio** em si (entra já sorteado, via import).
- **Cobrança/financeiro** de vagas.
- **Troca/permuta** de vagas após a rodada.
- **Lembretes automáticos** agendados (só reenvio manual).
- Tela admin de **digitalização** de vagas (feito em dev time a partir dos PDFs).

## Pressupostos a confirmar (no plano/implementação)

1. **Todo o pool "2 vagas" é composto de vagas duplas** (unidade de 2 vagas escolhe 1 dupla). Validar na digitalização dos 4 PDFs — se houver 2-vaga como duas simples, ajustar a regra de seleção (escolher 2 simples no mesmo pool).
2. **Vagas de moto não são presas a torre** (pool único "todas"). Confirmar pelas plantas; se forem por torre, vira fila de moto por torre.
3. **Momento exato de liberar vagas PCD** = no `closeRound`. Confirmar se há fase de "sobras" antes do encerramento.
4. **Unidade pulada volta para o fim da mesma fila** (vs. lista de pendências). Confirmado "fim da fila"; rever se o síndico prefere uma aba de pendências.
