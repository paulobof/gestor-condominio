# Escolha de vagas na garagem — Design

**Data:** 2026-06-11
**Status:** aprovado (design)
**Contexto:** nova feature do HELBOR TRILOGY HOME. Permite que cada unidade escolha **online** sua(s) vaga(s) de garagem, na ordem de um sorteio, com **mapa interativo fiel à planta** e **aviso por WhatsApp** quando chega a vez. A escolha acontece dentro de uma **campanha** (com nome) criada por quem tem a gestão, em um de **dois modos**: **com sorteio** (o sistema sorteia a ordem) ou **sem sorteio** (a ordem vem de um sorteio externo e é importada).

## Problema

Hoje a escolha de vagas seria feita presencialmente/manualmente, com risco de erro, conflito de vagas e baixa transparência. Queremos um processo:

- guiado por uma **ordem sorteada** (sorteada pelo sistema ou importada de um sorteio externo), unidade por unidade;
- **self-service**: o Proprietário escolhe a própria vaga num mapa que mostra o que está livre;
- **paralelo** entre os diferentes grupos de vagas, para encurtar o tempo total;
- **auditável** e operável por um síndico (monitorar, pular quem não responde, atribuir manualmente, encerrar).

## Domínio físico (do projeto da garagem)

- **3 torres**: A, B, C. Na planta da garagem aparecem como **TORRE 1 = A, TORRE 2 = B, TORRE 3 = C**.
- **4 pisos**: **−1, 0, 1, 2**.
- **Rótulos (confirmado nas plantas REV 05, com texto extraível):** vaga de carro = `T{torre} {seq}` (ex.: `T2 2263`); o número é **ID sequencial da torre** (não codifica categoria). Posição/tamanho = `{G|M|P}{num}{A|B}` (ex.: `G364A`/`G364B`); moto = `MOTO ###`; PCD = `PNE ####`. Tamanho ∈ **P/M/G**. **Não há distinção coberta/descoberta** (as áreas "VAGAS DESCOBERTAS" na planta não viram regra).
- **3 categorias de carro** (legenda dos pisos +1/+2): **1 vaga**, **2 vagas** e **3 vagas** — definidas pela **cor** do retângulo na planta (não pelo número).
- **Vaga dupla**: um único número atende **2 posições** (ex.: `T2 2232` = `190A` + `190B`). **Não existe "vaga tripla"** física (os rótulos só têm duplas `A/B` e simples). Entitlement: **1 vaga** = uma simples; **2 vagas** = uma dupla; **3 vagas** = **uma simples + uma dupla** (2 seleções no pool de 3 vagas, somando 3 posições).
- **Vagas de moto**: **pool comum** (não preso a torre — `MOTO ###`), em número **limitado** → processo/sorteio **separado**.
- **Vagas PCD**: demarcadas (`PNE ####`); ficam **reservadas só para unidades PCD** e **liberam como comuns assim que não houver mais unidade PCD aguardando** na fila.

> **Entitlement** = **regra do final** (`unit.position`): finais 1,2,5,6 → 2 vagas; 3,4 → 1 vaga; **+ exceções de 3 vagas** = coberturas **3201 e 3202 das torres A, B e C** (6 unidades), via override por unidade — a regra do final **não** cobre as de 3 vagas.

## As filas (até 10)

A escolha roda em **filas independentes e paralelas**, cada uma com sua própria ordem sorteada: **3 torres × 3 categorias (1/2/3 vagas) = até 9 filas de carro + 1 de moto = até 10**. Filas sem nenhuma unidade elegível (ex.: uma torre sem unidades de 3 vagas) são **omitidas**.

| Fila | Torre | Categoria |
|------|-------|-----------|
| A1 / A2 / A3 | A | 1 / 2 / 3 vagas |
| B1 / B2 / B3 | B | 1 / 2 / 3 vagas |
| C1 / C2 / C3 | C | 1 / 2 / 3 vagas |
| Moto | (todas) | moto |

Uma unidade está em **exatamente uma fila de carro** (definida por torre + categoria) e **opcionalmente na fila de moto** (se sorteada). Como cada fila de carro mapeia para um **pool de vagas exclusivo** (tower × category), não há disputa de vaga entre filas distintas.

## Decisões de produto (confirmadas)

- **Quem escolhe:** o **Proprietário** (unit master, `isUnitMaster=true`) da unidade, em self-service. Unidades **sem proprietário cadastrado** são atribuídas pelo síndico (ação "Atribuir").
- **Restrição de escolha (carro):** a unidade só enxerga/escolhe vagas onde `spot.tower == unit.tower` **E** `spot.category` casa com sua entitlement **E** `status = AVAILABLE` **E** (`spot.pcd == false` **OU** `unit.pcd == true`).
- **Restrição de escolha (moto):** na fila de moto a torre **não** se aplica (`spot.tower` é null / pool comum). Filtro: `spot.kind == MOTO` **E** `status = AVAILABLE` **E** (`spot.pcd == false` **OU** `unit.pcd == true`). A unidade escolhe **1 vaga de moto**.
- **Seleção por categoria:** **1 vaga** = 1 vaga simples (capacity 1); **2 vagas** = 1 vaga dupla (capacity 2); **3 vagas** = **1 simples + 1 dupla** (2 vagas do pool de 3 vagas, somando 3 posições); **moto** = 1 vaga de moto. O sistema valida a combinação exata.
- **PCD:** atributo persistente no **cadastro da unidade** (`unit.pcd`). A **prioridade na ordem** é resolvida **pelo operador** (modo externo, posiciona os PCDs no arquivo) **ou pelo sistema** (modo interno, toggle `pcd_first` põe PCDs no topo do sorteio). O software mantém a regra de **vaga PCD reservada** (só unidade PCD seleciona vaga PCD) e **libera essas vagas como comuns assim que a fila não tiver mais unidade PCD aguardando** (não espera o encerramento).
- **Vez não atendida:** a fila **fica parada** na unidade da vez até ela escolher. O **síndico pula** manualmente; a unidade pulada vai para o **fim da fila** e é reavisada — **até 3 vezes** (`skip_count`). No **3º pulo**, a unidade sai do rodízio (`PARKED`) e **só o síndico escolhe** por ela (`assignManually`). **Sem timeout automático.**
- **Avanço da fila:** **automático no envio** (Abordagem 1). Ao submeter, a próxima unidade vira "da vez" e o WhatsApp dispara sozinho.
- **Moto:** fila separada, **arquivo de ordem próprio**, **iniciada manualmente** pelo síndico (tipicamente após as filas de carro).
- **Mapa fiel à planta (overlay):** a imagem da planta de cada piso é o **fundo**, e cada vaga clicável fica **posicionada na coordenada real**. Estados de cor:
  - 🔵 **azul** = disponível · 🔴 **vermelho** = já escolhida · ⚪ **cinza** = não é da torre/categoria da unidade · 🟢 **verde** = selecionada (antes de confirmar).

## Catálogo de vagas (produzido em dev time)

A digitalização **não é feita no software**. A partir dos **4 PDFs** das plantas, o catálogo — **metadados + geometria de cada vaga** — é produzido **durante o desenvolvimento** e entregue como:

- **seed/migração** dos registros de `parking_spot` (incl. coordenadas), e
- **assets de frontend**: as 4 imagens de planta (fundo do overlay).

**Sobre os fontes:** os **4 pisos** estão disponíveis em **"AGRUPAMENTO REV 05"** (−1, 0, +1, +2), todos com **texto vetorial extraível** (rótulos `T2 2263`, `MOTO ###`, `PNE ####`, `G364A/B`…) e as 3 categorias na legenda — base ideal para extrair o catálogo. Anotações a tratar na extração: **`VAGA NÃO EXISTE`** (não cria a vaga) e **`VAGA MENOR COMPRIMENTO`** (registra em `size`/observação). Fluxo: PDFs → front preparado com as vagas reais → **testado localmente** → sobe. Não há tela admin de "desenhar vagas".

## Arquitetura

### Dados (novas tabelas, soft delete via `@SQLDelete` + `@SQLRestriction`)

**`parking_spot`** — catálogo (1 linha por vaga selecionável; uma dupla = 1 linha com `capacity=2`). O pool de **3 vagas** (`category=THREE`) contém vagas **simples e duplas** misturadas; a unidade pega 1 de cada:
`id`, `version`, `floor` (smallint −1/0/1/2), `code` (ex.: `T2 2263`), `tower` (A/B/C, null p/ moto), `category` (`ONE`/`TWO`/`THREE`, null p/ moto), `kind` (`CAR`/`MOTO`), `size` (varchar P/M/G), `pcd` (bool), `capacity` (smallint 1/2/3 — nº de posições), `sub_positions` (varchar, ex.: `"190A,190B"`), geometria `img_x,img_y,img_w,img_h` (relativo à imagem do piso), `status` (`AVAILABLE`/`TAKEN`/`BLOCKED`), `assigned_unit_id` (uuid null) + colunas de auditoria/soft-delete.

**`parking_round`** — a **campanha** de escolha: `id`, `name` (nome da campanha), `draw_mode` (`INTERNAL` = sistema sorteia / `EXTERNAL` = ordem importada), `pcd_first` (bool, default true — só aplica em `INTERNAL`), `status` (`DRAFT`/`OPEN`/`CLOSED`), `created_by_user_id`, timestamps.

**`parking_order_draw_log`** — auditoria do sorteio interno: `id`, `queue_id`, `seed`/resultado, `drawn_by_user_id`, `drawn_at` (registra cada vez que uma fila é sorteada/re-sorteada em DRAFT).

**`parking_queue`** — uma fila (até 10), por campanha: `id`, `round_id`, `tower` (null p/ moto), `category` (`ONE`/`TWO`/`THREE`, null p/ moto), `kind` (`CAR`/`MOTO`), `status` (`PENDING` = criada, sem ordem ou aguardando abrir / `OPEN` / `PAUSED` / `DONE`).

**`parking_queue_entry`** — unidade na fila: `id`, `queue_id`, `unit_id`, `order_index`, `state` (`WAITING`/`CURRENT`/`DONE`/`PARKED`), `skip_count` (smallint, default 0), `pcd` (bool — snapshot da unidade no momento), `notified_at`, `chosen_at`. `PARKED` = pulada 3× → sai do rodízio, aguardando o síndico atribuir.

**`parking_assignment`** — resultado: `id`, `round_id`, `unit_id`, `spot_id`, `chosen_by_user_id`, `chosen_at`.

**`unit.pcd`** (boolean, default false) e **`unit.parking_spots_override`** (smallint null) — atributos da unidade (migração backward-compatible, expand). `parking_spots_override` null ⇒ deriva o nº de vagas da `position` (1,2,5,6→2; 3,4→1); preenchido ⇒ usa o valor (ex.: **3** para coberturas). Define a categoria/fila da unidade.

> `parking_queue_entry` e `parking_assignment` são **logs operacionais**; mas, por consistência com o resto do projeto, usam soft delete (exceto se enquadrados como log imutável puro — a confirmar no plano).

### Backend (`feature/parking`)

**Domínio rico** (`@Getter`, `@Setter(PROTECTED)`, sem `@Data`):
- `ParkingSpot` com métodos de mutação: `reserveFor(unit)`, `release()`, `block()`.
- `ParkingRound` / `ParkingQueue` com `open()`, `pause()`, `close()`, `advance()`.
- `ParkingQueueEntry` com `markCurrent()`, `markDone()`, `skipToEnd()`.

**`ParkingCatalogService`** — consulta do mapa: `floorPlan(roundId, unitId, floor)` retorna a imagem do piso + a lista de vagas com **estado calculado para aquela unidade** (AVAILABLE/TAKEN/OTHER/SELECTABLE), respeitando torre/categoria/PCD.

**`ParkingSelectionService`** (`@Transactional`):
- `currentSelection(unitId)` — em qual fila a unidade está e se é a vez dela.
- `submit(actorUserId, unitId, spotIds[])` — aceita 1 ou 2 vagas (3 vagas = 2):
  - valida **é a vez** da unidade (`entry.state == CURRENT`) → senão `NOT_YOUR_TURN`;
  - valida actor é **master** da unidade (ou tem `PARKING_MANAGE`) → senão `FORBIDDEN`;
  - valida a **combinação** bate com a entitlement → senão `INVALID_SELECTION`: ONE = 1 spot cap 1; TWO = 1 spot cap 2; **THREE = exatamente 1 cap 1 + 1 cap 2**; MOTO = 1 moto;
  - valida cada vaga é **selecionável** pela unidade → senão `SPOT_NOT_SELECTABLE`;
  - **trava otimista** (`version`) em cada vaga; se alguma já OCUPADA → `SPOT_TAKEN` (transação inteira aborta, nada é reservado);
  - cria os `parking_assignment` (1 ou 2), marca as vagas `TAKEN`, entry `DONE`;
  - **avança a fila**: próxima `WAITING` (menor `order_index`) vira `CURRENT` → evento `AFTER_COMMIT + @Async` enfileira WhatsApp.

**`ParkingAdminService`** (`@Transactional`, `PARKING_MANAGE`):
- `createCampaign(name, drawMode, pcdFirst)` — cria a campanha (DRAFT) e deriva as filas (até 10) com a elegibilidade de carro.
- `drawOrder(queueRef)` / `drawAll(roundId)` — modo `INTERNAL`: sorteia a ordem (PCD no topo se `pcd_first`), grava `order_index`, registra em `parking_order_draw_log`. Só em DRAFT; re-sorteável.
- `generateTemplates(roundId)` — modo `EXTERNAL`: gera um CSV por fila (carro pré-preenchido, moto em branco).
- `importOrder(queueRef, csv)` — importa/valida a ordem de uma fila (ver validações na seção da campanha). Substitui enquanto DRAFT.
- `openRound` / `openQueue` / `pauseQueue` / `reopenQueue`.
- `skipCurrent(queueId)` → incrementa `skip_count`; se `< 3`, recoloca no fim (`order_index = max+1`, `WAITING`) e avança; se `== 3`, marca `PARKED` (sai do rodízio) e avança.
- `assignManually(queueId, unitId, spotIds[])` — atribui por uma unidade (1 ou 2 vagas, mesma validação de combinação; cobre `PARKED` e sem-proprietário).
- `releasePcdSpots(queueId)` — chamado automaticamente quando a fila não tem mais entry PCD aguardando (`WAITING`/`CURRENT`): `pcd → false` nas vagas PCD ainda `AVAILABLE` daquele pool, tornando-as comuns.
- `closeRound()` → encerra a campanha e **congela** o resultado.
- Histórico/auditoria de **pulos e atribuições manuais** (quem, quando, qual unidade/vaga).

**`ParkingNotificationRenderer`** — texto renderizado **no backend** (`WhatsAppMessageRenderer`), com **link profundo** para a tela de seleção. Envio via **outbox** existente (`WhatsAppOutboxService` / retry). Telefone normalizado (`PhoneNumberNormalizer`). Sem PII em log.

**`ParkingController`**:
- `GET /api/parking/me` (`PARKING_SELECT`) — estado da minha vez (fila, é-minha-vez, entitlement).
- `GET /api/parking/floor/{floor}` (`PARKING_SELECT`) — mapa do piso filtrado para a minha unidade (imagem + vagas + estado).
- `POST /api/parking/select` (`PARKING_SELECT`) — body `{ spotIds: [...] }` (1 ou 2 vagas); `200` com confirmação.
- `POST /api/parking/admin/campaigns` (`PARKING_MANAGE`) — cria campanha `{ name, drawMode, pcdFirst }`.
- `POST /api/parking/admin/campaigns/{id}/draw` (`PARKING_MANAGE`) — sorteio interno (`{ queueRef }` ou todas).
- `GET /api/parking/admin/campaigns/{id}/templates` (`PARKING_MANAGE`) — baixa os CSVs por fila (modo externo).
- `POST /api/parking/admin/order/import` (`PARKING_MANAGE`) — upload do CSV de uma fila (parse em memória).
- `POST /api/parking/admin/campaigns/{id}/open` (`PARKING_MANAGE`) — abre a campanha (exige todas as filas com ordem).
- `GET /api/parking/admin/queues` (`PARKING_MANAGE`) — painel (progresso de cada fila, unidade da vez, tempo de espera).
- `POST /api/parking/admin/queues/{id}/skip` · `/assign` · `/open` · `/pause` · `/reopen` (`PARKING_MANAGE`) — `/open` é o que **abre a fila de moto** manualmente.
- `POST /api/parking/admin/campaigns/{id}/close` (`PARKING_MANAGE`).
- `GET /api/parking/admin/report` (`PARKING_MANAGE`) — alocação por torre/piso; export CSV/PDF.

### Permissions (RBAC por permission, nunca por role)

- **`PARKING_SELECT`** — Proprietário escolhe a própria vaga (concedida via role do morador).
- **`PARKING_MANAGE`** — síndico/admin: importar, operar filas, pular, atribuir, encerrar, relatório.

Endpoints com `@PreAuthorize("hasAuthority('PARKING_SELECT')")` / `('PARKING_MANAGE')`. Seed das permissions + vínculo às roles via migração.

### Frontend (`features/parking`)

- **`/vagas`** (rota do morador, gated por `PARKING_SELECT` + feature flag): tela de seleção com **overlay fiel** — imagem do piso de fundo, vagas posicionadas, abas de piso (−1/0/1/2), legenda de cores, contador de seleção, botão **Confirmar** (habilita só com a seleção completa conforme a fila: 1 simples / 1 dupla / **1 simples + 1 dupla** (3 vagas) / 1 moto). Mobile-first, touch ≥44px, WCAG AA. Deep link do WhatsApp cai aqui (exige login).
- **`/admin/vagas`** (gated por `PARKING_MANAGE`):
  - **Criar campanha** (wizard): nome + modo (**com sorteio** / **sem sorteio**) + toggle "PCD primeiro" (modo interno).
  - **Definir ordem**: modo interno → **Sortear** por fila / todas, com **preview** e **re-sortear**; modo externo → **baixar os templates** (um por fila), preencher e **importar fila a fila** (com feedback de validação).
  - **Abrir campanha** (habilita só com todas as filas prontas).
  - **Painel das filas** (progresso, unidade da vez, alerta de espera longa, contador de pulos), **Pular/Atribuir**, lista de unidades **`PARKED`** (3 pulos) para atribuir, abrir fila de moto, painel **PCD** (reservadas; liberam sozinhas quando acabam os PCDs), **relatório**.
- Overlay renderizado com as 4 imagens de planta como asset; vagas desenhadas por coordenada (`img_x/y/w/h`), cor por estado.

### Motor de filas (Abordagem 1)

Abrir fila → menor `order_index` vira `CURRENT` → WhatsApp. Submit válido → assignment + vaga `TAKEN` + entry `DONE` → próxima `CURRENT` + WhatsApp. Síndico pula → `skip_count++`: para o fim (`WAITING`) se `< 3`, senão `PARKED`; avança. Quando a fila fica sem PCD aguardando → `releasePcdSpots`. Concorrência tratada por **trava otimista na vaga** e pela invariante de **uma única `CURRENT` por fila**.

## Campanha e modos de sorteio (início + input)

Quem tem **`PARKING_MANAGE`** cria uma **campanha** (DRAFT) informando **nome** e **modo** (`INTERNAL` / `EXTERNAL`); em `INTERNAL`, o toggle **"PCD primeiro"** (default ligado). As **filas** (até 10: 3 torres × 3 categorias + moto) são **derivadas automaticamente** das unidades elegíveis; filas vazias são omitidas. A campanha só pode ser **aberta (OPEN)** quando todas as filas têm ordem definida.

**Elegibilidade (carro):** cada unidade cai numa fila por **torre + categoria**, onde a categoria vem do nº de vagas: `unit.parking_spots_override` se preenchido (ex.: **3** nas coberturas), senão a **regra do final** (`unit.position`: 1,2,5,6 → 2 vagas; 3,4 → 1 vaga). **Moto não é derivável** — a lista de ganhadores vem sempre de fora (ver abaixo).

### Caso A — Com sorteio (`INTERNAL`)

- **`drawOrder(queueRef)`** — por fila (ou "sortear todas"): o sistema embaralha as unidades elegíveis e grava `order_index`. Se `pcd_first`, as unidades **PCD** (do cadastro) vão ao topo e o sorteio só ordena **entre elas**; o restante é sorteado depois.
- **Preview + re-sortear** enquanto a campanha está em DRAFT. Cada sorteio é registrado em `parking_order_draw_log` (auditoria).
- **Moto (vale para qualquer modo):** **sempre importada com a ordem já inclusa** — o sistema **nunca sorteia a moto**. Mesmo numa campanha `INTERNAL`, a fila de moto só fica pronta após o import da lista de ganhadores **já ordenada**.

### Caso B — Sem sorteio (`EXTERNAL`)

- **`generateTemplates()`** — o sistema gera **um CSV por fila**. As **filas de carro** vêm **pré-preenchidas** com as unidades elegíveis (`codigo_unidade, torre, categoria, pcd`) + coluna **`ordem`** em branco. O arquivo de **moto** vem **em branco** (o operador lista os ganhadores + `ordem`, pois vêm do sorteio externo).
- O operador preenche a `ordem` (PCDs onde quiser — a prioridade é decisão dele) e **importa fila a fila**.
- **`importOrder(queueRef, csv)`** valida: arquivo da fila certa; unidades existem; (carro) pertencem à torre/categoria da fila; `ordem` sem duplicata nem buraco; **aviso** se faltar unidade elegível (carro). Reimportar substitui a ordem da fila enquanto DRAFT.

### Abrir a campanha

`openRound()` exige **todas as filas com ordem definida**, muda a campanha para `OPEN` e **abre as filas de carro**: cada uma marca seu menor `order_index` como `CURRENT` e dispara o WhatsApp; o **motor de filas** (Abordagem 1) assume daí. A **fila de moto fica em `PENDING`** e é aberta **manualmente** pelo síndico (`POST /queues/{moto}/open`), tipicamente após as filas de carro.

## Invariantes e casos de borda

- **Uma campanha `OPEN` por vez.** Criar/abrir uma segunda campanha enquanto há uma aberta é bloqueado (`CAMPAIGN_ALREADY_OPEN`). Por isso os endpoints do morador (`/me`, `/floor`, `/select`) resolvem implicitamente a **campanha ativa**.
- **Unidade pode ter duas vezes.** Uma unidade pode estar numa fila de carro **e** na de moto — são turnos independentes. `/me` retorna **a vez ativa** (ou as duas, se ambas estiverem `CURRENT`); a tela deixa claro qual escolha está sendo feita.
- **Unidade da vez sem proprietário/telefone.** Se a unidade que vira `CURRENT` não tem proprietário cadastrado ou telefone válido, **não há WhatsApp**: a fila **não avança sozinha** e a unidade aparece **sinalizada** no painel para o síndico **atribuir** (`assignManually`) ou pular. Sem destinatário, nunca trava silenciosamente.
- **Moto não-derivável.** Como a moto depende de lista externa, uma campanha só fica "pronta para abrir" depois que a fila de moto também recebeu sua ordem (import).

## Notificações (WhatsApp)

- Dispara quando a unidade vira **`CURRENT`** (texto no backend + deep link), **se houver proprietário com telefone**. Reenvio/lembrete = **botão manual** do síndico no painel. **Sem agendamento automático** no v1.

## Tratamento de erros

`NOT_YOUR_TURN` (409), `INVALID_SELECTION` (422, combinação não bate com a entitlement), `SPOT_NOT_SELECTABLE` (422), `SPOT_TAKEN` (409, corrida), `FORBIDDEN` (403), `ORDER_IMPORT_INVALID` (422, com detalhe de linha), `CAMPAIGN_NOT_OPEN` (409), `CAMPAIGN_ALREADY_OPEN` (409, ao abrir uma segunda). Frontend mostra `message` em toast; na corrida de vaga, recarrega o mapa.

## Testes (TDD)

**Backend**
- `ParkingSelectionServiceTest`: vez certa/errada, filtro de selecionabilidade (torre/categoria/PCD; moto ignora torre), combinação por categoria (1 simples / 1 dupla / **3 vagas = 1 simples + 1 dupla** / moto) incl. `INVALID_SELECTION`, entitlement por `position` vs `parking_spots_override` (3 vagas), corrida de vaga (trava otimista, 2 vagas atômicas), avanço de fila + evento de notificação, unidade `CURRENT` sem proprietário/telefone (não avança nem notifica, sinaliza).
- `ParkingAdminServiceTest`: criar campanha (deriva as filas, até 10; omite vazias), sorteio interno (PCD no topo com `pcd_first`, re-sortear, log de auditoria), gerar templates (carro pré-preenchido/moto em branco), import (válido/duplicado/torre errada/buraco na ordem), abrir só com todas as filas prontas (abre carro, moto fica `PENDING`), bloquear segunda campanha `OPEN`, skip (fim até 3×, depois `PARKED`), assign manual (inclusive `PARKED`), liberação automática de PCD quando some o último PCD aguardando, reopen.
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

- **Definição de ganhadores de moto** pelo sistema (sempre vem de lista externa, mesmo em campanha `INTERNAL`).
- **Inscrição prévia** de interesse (em moto ou em geral) — não há etapa de inscrição.
- **Cobrança/financeiro** de vagas.
- **Troca/permuta** de vagas após a rodada.
- **Lembretes automáticos** agendados (só reenvio manual).
- Tela admin de **digitalização** de vagas (feito em dev time a partir dos PDFs).

### Resolvido pelas plantas REV 05 (4 pisos)

- ✅ **4 pisos em REV 05 vetorial** (−1, 0, +1, +2), com texto extraível — base do catálogo.
- ✅ **Moto = pool comum** (não preso a torre) — rótulos `MOTO ###`.
- ✅ **Existem 3 categorias** de carro (1/2/3 vagas) → modelo com `category` ONE/TWO/THREE e até 10 filas; entitlement de 3 vagas via `unit.parking_spots_override`.
- ✅ **PCD demarcada** (`PNE ####`); **liberam quando acaba o PCD na fila** (não no encerramento).
- ✅ **Sem distinção coberta/descoberta** (campo `covered` removido).
- ✅ **Unidades de 3 vagas:** `3201` e `3202` das torres **A, B, C** (6 coberturas) → `parking_spots_override = 3`.
- ✅ **Pular:** fim da fila até **3×**; no 3º vira `PARKED` e só o síndico atribui.
- ✅ **3 vagas = 1 simples + 1 dupla** (não existe vaga tripla física; pool `THREE` mistura simples e duplas, a unidade pega 1 de cada).

### Ainda a confirmar (só na extração do catálogo)

1. Mapear, por piso, a **cor → categoria** de cada vaga (a categoria não está no número, e os pisos são raster quanto à cor) e o **agrupamento posição↔número** (quais `A/B` pertencem a cada vaga). É trabalho de digitalização, sem decisão de produto pendente.
