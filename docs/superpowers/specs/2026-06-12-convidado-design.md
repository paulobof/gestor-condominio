# Design: Convidado (guest)

**Data:** 2026-06-12
**Status:** RASCUNHO — decisões-chave fechadas pelo Paulo (abaixo); falta só revisão final + virar plano.
**Autor:** rascunho gerado por agente, decisões anexadas pelo orquestrador.

---

## 0. Decisões do Paulo (2026-06-12) — fechadas

Estas resolvem as principais perguntas em aberto; o resto do doc deve ser lido sob estas decisões:

1. **Entrada do cadastro:** há uma **tela de escolha "Convidado" vs "Morador principal"**. "Morador principal" = o fluxo de master atual (`isUnitMaster`, com comprovante + aprovação); "Convidado" = o fluxo novo sem comprovante. A `RegisterMasterPage` passa a ser alcançada *depois* dessa escolha; `RegisterGuestPage` é a outra ramificação.
2. **Sem aprovação:** convidado entra **ACTIVE direto** (sem fila do síndico).
3. **Somente leitura:** convidado **vê** Indicações e Classificados mas **NÃO cria** conteúdo. Implicação: os **endpoints de criação** de indicação/classificado (hoje abertos a qualquer autenticado) passam a exigir uma permission que o GUEST **não** tem (ex.: `CONTENT_CREATE`, concedida a todas as roles menos GUEST). Ver = aberto; criar = gated.
4. **Anti-abuso já nesta feature:** **rate-limit** no `register-guest` (usar o `RateLimitFilter` existente) + **captcha** no formulário. E-mail-verification fica como opção secundária (conflita com "ACTIVE direto").
5. **Modelo aprovado:** role nova **`GUEST`** + permission **`GENERAL_AREAS_VIEW`** (concedida a todas as roles existentes, **não** ao GUEST) para barrar Avisos/Informações/FAQ. Indicações/Classificados seguem abertos para leitura. (Restringir = barrar o resto, não só liberar.)

Pendência menor: telefone do convidado é obrigatório? (a confirmar)

---

## 1. Objetivo

Permitir que **qualquer pessoa** se cadastre como **convidado**: cria login
(e-mail + senha), **sem** comprovante de residência e **sem** aprovação do
síndico. O convidado opta explicitamente por esse tipo de cadastro. O acesso dele
fica **restrito a apenas duas áreas**: **Indicações** e **Classificados**. Nada
mais (sem Avisos, Informações, FAQ, áreas admin).

## 2. Problema / contexto

Hoje o único auto-cadastro é o de **morador master** (`registerMaster`,
`RegistrationService.java:42`): exige unidade, comprovante de residência
(magic-bytes + upload MinIO), termo de consentimento, entra em
`PENDING_APPROVAL` e só vira `ACTIVE` após aprovação do síndico
(`User.approveAsMaster`, `User.java:183`).

O convidado é o oposto: sem unidade, sem comprovante, sem aprovação. Precisa de um
fluxo de cadastro novo e mais leve.

### A nuance crítica: restringir, não liberar

As áreas "gerais" **não são gated por permission** hoje — qualquer autenticado lê.
Os controllers de leitura usam `@PreAuthorize("isAuthenticated()")`:

- Avisos — `AnnouncementController.java:32,42` (`isAuthenticated()`)
- Indicações — `RecommendationController.java:32,45` (`isAuthenticated()`)
- Classificados — `ClassifiedController` (mesmo padrão)
- FAQ — `FaqController` (mesmo padrão)
- Informações — `InfoController` (mesmo padrão)

No frontend, `App.tsx:24` e `Sidebar.tsx:23` tratam `requires` como **opcional**:
item sem `requires` aparece para qualquer autenticado. Logo Avisos/Informações/FAQ
estão hoje visíveis a todos.

Portanto **"restringir o convidado a Indicações + Classificados" não é liberar duas
áreas — é barrar todas as outras** para um perfil que, por enquanto, teria acesso
total como qualquer autenticado. É o ponto central deste design (seção 6).

## 3. Quem é o convidado (modelo de identidade)

Atributos do convidado:

- **Sem unidade** (`unit_id = NULL`). Já existe precedente: o admin seed roda com
  `unit_id NULL` e `status ACTIVE` (`V8__seed_admin.sql:9`).
- **`is_unit_master = false`**.
- **Sem comprovante** (`residence_proof_* = NULL`).
- **Status inicial `ACTIVE`** (proposto — sem aprovação; ver Pergunta 2).
- **Não é morador**: não recebe a role `RESIDENT`. A contagem de moradores é
  keyed em `unit_id` + role `RESIDENT` (`UnitMemberService.java:31-34,49`); um
  convidado sem unidade e sem `RESIDENT` **não entra** em nenhuma listagem de
  moradores de unidade automaticamente. (Bom: impacto zero na contagem.)

### Como "convidado" é representado: role nova `GUEST`

Proposta (alinhada à preferência do Paulo por "via role"): criar uma **`RoleName`
nova `GUEST`** (`RoleName.java`), label "Convidado", `assignable = false`
(auto-atribuída no cadastro, não pela tela de acessos).

Trade-off de modelagem — duas opções (ver seção 6 para a decisão de gating):

- **(A) Role como rótulo + gating por permission nas áreas gerais.** `GUEST`
  praticamente não carrega permissions; o que muda é que Avisos/Info/FAQ passam a
  exigir uma permission (`GENERAL_AREAS_VIEW`) que `GUEST` não tem mas
  `RESIDENT`/`MANAGER`/etc. têm.
- **(B) Role como decisão de allow-list explícita.** Indicações/Classificados
  ganham um `@PreAuthorize` próprio que aceita `RESIDENT`/`GUEST`/moderadores; as
  outras áreas barram `ROLE_GUEST`.

A seção 6 recomenda a abordagem (A) por ser aditiva e menos arriscada.

## 4. Fluxo de cadastro do convidado

### Dados fornecidos

`nome completo`, `como prefere ser chamado` (greetingName), `e-mail`, `telefone
(WhatsApp)`, `senha (forte)`, opt-in WhatsApp, e **aceite do termo de
privacidade**. **Sem** unidade, **sem** comprovante. (Manter o consent — é
auto-cadastro com coleta de PII; ver Pergunta 4.)

### Backend

- Novo endpoint **`POST /api/auth/register-guest`** (público em
  `SecurityConfig.java:46-53`, junto de `register-master`). **JSON**, não
  multipart (não há upload).
- DTO `RegisterGuestRequest` (espelha `RegisterMasterRequest` sem `unitCode`):
  `fullName`, `greetingName`, `email`, `phone`, `gender?`, `birthDate?`,
  `password (@StrongPassword)`, `consentVersion`, `whatsappOptIn`.
- `RegistrationService.registerGuest(req, clientIp)`:
  - rejeita e-mail já usado (`EMAIL_TAKEN`, reusa `findActiveByEmailIgnoreCase`);
  - valida `consentVersion`;
  - cria `User` com `unitId=NULL`, `isUnitMaster=false`, `status=ACTIVE`,
    `mustChangePassword=false`, campos de proof nulos;
  - cria `UserEmail` primário;
  - cria `UserRole(userId, GUEST_role_id)`;
  - retorna `RegistrationStatusResponse(userId, "ACTIVE")` (sem rota
    `/pending-approval`).
- Fora de escopo do fluxo: nada de MinIO, magic-bytes, unidade.

### Frontend

- Página de cadastro mestre (`RegisterMasterPage.tsx`) hoje é a única. Proposta de
  UX para "optar por ser convidado" (ver Pergunta 1):
  - **Opção preferida:** uma **tela de escolha** antes do formulário — "Sou morador
    / proprietário" (vai pro fluxo master com unidade+comprovante) vs **"Quero só
    acessar Indicações e Classificados (convidado)"** (vai pro fluxo leve).
  - Nova página `RegisterGuestPage.tsx` + rota pública `/register-guest`
    (`router.tsx:29` ao lado de `/register-master`). Reaproveita
    `PasswordInput`/`PasswordChecklist`/`ConsentBox`; **sem** `UnitSelector` nem
    `ProofUploader`.
  - Após sucesso: login direto ou redirect para `/login` (não `/pending-approval`,
    pois já está `ACTIVE`).

## 5. Autorização e endpoints

### O que o convidado PODE acessar

- **Indicações** (`/api/recommendations/**`) — leitura e, possivelmente, criação
  (ver Pergunta 5).
- **Classificados** (`/api/classifieds/**`) — idem.

### O que o convidado NÃO pode acessar (backend tem que barrar)

Não basta esconder no menu. Os endpoints de leitura de Avisos/Info/FAQ usam
`isAuthenticated()`, então **um convidado com token válido hoje conseguiria
chamá-los** mesmo sem item no menu. O backend precisa barrar de fato (seção 6).

Endpoints admin (REGISTRATION_VIEW, ROLE_ASSIGN, etc.) já são gated por
permission e o `GUEST` não as terá — nenhuma mudança ali.

## 6. Como restringir as áreas gerais (a parte difícil)

### Recomendação: opção (A) — nova permission `GENERAL_AREAS_VIEW`

1. Criar permission **`GENERAL_AREAS_VIEW`** ("Ver avisos, informações e FAQ").
2. Conceder a **todas as roles existentes** (MANAGER, COUNCIL, STAFF, RESIDENT,
   DOORMAN, MURAL_EDITOR) — assim ninguém perde acesso. **Não** conceder a `GUEST`.
3. Trocar o gate dos endpoints de leitura **só** dessas três áreas:
   - `AnnouncementController` GET list/get: `isAuthenticated()` →
     `hasAuthority('GENERAL_AREAS_VIEW')`.
   - `FaqController` GET: idem.
   - `InfoController` GET: idem.
   - (Escrita já é gated por `ANNOUNCEMENT_MANAGE`/`FAQ_MANAGE`/`INFO_MANAGE`.)
4. **Indicações e Classificados ficam como estão** (`isAuthenticated()`), então
   `GUEST` continua acessando — é exatamente o recorte desejado.
5. Frontend: marcar os itens Avisos/Informações/FAQ com
   `requires: 'GENERAL_AREAS_VIEW'` em `App.tsx` (NAV) e `Sidebar.tsx` (ITEMS). O
   filtro `can`/`filter` já existente some com eles para o convidado. Indicações e
   Classificados continuam sem `requires`.

**Por que (A):** é **aditiva** (ninguém perde acesso porque a permission é dada a
todas as roles atuais), o mecanismo de gating (permission + `requires`) **já
existe** e é usado, e o convidado é definido por *ausência* de permission — não
por uma exceção espalhada. Custo: precisa garantir que toda role atual receba a
permission no mesmo migration (senão um morador perde Avisos).

### Alternativa: opção (B) — gating por role `ROLE_GUEST`

Deixar as áreas gerais como `isAuthenticated()` e adicionar
`@PreAuthorize("!hasRole('GUEST')")` (deny-list) ou converter Indicações/
Classificados para uma allow-list explícita. **Contra:** CLAUDE.md proíbe
autorização por role para *liberar* acesso ("`hasRole('STAFF')` proibido"); usar
role para *negar* é menos pior mas ainda foge do padrão "por permission". Mais
frágil: cada endpoint novo de área geral precisa lembrar de excluir o guest.
**Não recomendada.**

### Defesa em profundidade (vale para qualquer opção)

O gate de menu (frontend) é só UX. A garantia real é o `@PreAuthorize` no backend.
Tests de contrato (`@WebMvcTest` + MockAuth, padrão do repo) devem cobrir:
convidado recebe **403** em GET de Avisos/Info/FAQ e **200** em Indicações/
Classificados.

## 7. Migrations necessárias (Flyway, backward-compatible, expand/contract)

Próximo número livre: **V29** (último é `V28__permission_resident_manage.sql`).

- **V29 — role GUEST + permission GENERAL_AREAS_VIEW** (seguindo o padrão do
  `V25__role_mural_editor.sql`):
  1. `INSERT INTO role (id, name, label, max_holders, assignable) VALUES
     (7, 'GUEST', 'Convidado', NULL, false);`
  2. `INSERT INTO permission (id, code, label) VALUES (N, 'GENERAL_AREAS_VIEW',
     'Ver avisos, informações e FAQ');` (próximo id de permission livre — conferir;
     V6 vai até 14 e há permissions adicionadas em migrations posteriores como
     `ANNOUNCEMENT_MANAGE`, `RESIDENT_MANAGE`; usar `MAX(id)+1` explícito).
  3. `INSERT INTO role_permission (role_id, permission_id) SELECT r.id, p.id FROM
     role r CROSS JOIN permission p WHERE p.code='GENERAL_AREAS_VIEW' AND r.name <>
     'GUEST';` — concede a todas as roles **exceto** GUEST.
  - Backward-compatible: só adiciona linhas; `GENERAL_AREAS_VIEW` só passa a ser
    *exigida* quando o código novo (com o `@PreAuthorize` trocado) subir. Como
    Flyway roda no boot antes de servir, migration + deploy do código andam juntos.
  - **Atenção de ordem:** se o código que exige a permission subir **antes** da
    permission ser concedida às roles, moradores perdem Avisos. Garantir que V29
    rode no mesmo release do código.

`role`/`role_permission`/`user_role` são tabelas de junção com hard-delete
permitido (CLAUDE.md), então sem `@SQLDelete` aqui.

## 8. Frontend — resumo das mudanças

- `RegisterGuestPage.tsx` (nova) + rota `/register-guest` em `router.tsx`.
- Tela/escolha de tipo de cadastro (morador vs convidado) — local a definir
  (Pergunta 1): no `/login`, numa landing, ou em `/register`.
- `App.tsx` / `Sidebar.tsx`: adicionar `requires: 'GENERAL_AREAS_VIEW'` aos itens
  Avisos, Informações, FAQ.
- `api/authApi` (ou consentApi): função `registerGuest(json)`.
- A saudação de `App.tsx:98` ("Olá, ... morador") pode soar errada para convidado
  — usar `greetingName` neutro (já é o fallback).

## 9. Impacto em "moradores"

Nenhum automático: contagem de moradores é por `unit_id` + role `RESIDENT`
(`UnitMemberService.java`). Convidado tem `unit_id NULL` e role `GUEST`, então não
aparece como morador de unidade nem em telas de membros. Verificar se alguma
listagem global de usuários (tela de acessos, `AccessService`) precisa de filtro/
badge "Convidado" para não confundir o admin (Pergunta 6).

## 10. Fora de escopo

- Convidado virar morador depois (upgrade de conta com unidade + comprovante).
  Possível futuro; por ora cadastros são independentes.
- Qualquer área além de Indicações e Classificados.
- Limites anti-spam / verificação de e-mail / captcha no cadastro aberto
  (mencionar como risco — ver Pergunta 7).
- Moderação específica de conteúdo postado por convidado.
- E-mail: comunicação outbound segue só WhatsApp (CLAUDE.md).

## 11. Riscos

- **Cadastro aberto = superfície de abuso.** Sem aprovação, qualquer um cria conta.
  Spam em Indicações/Classificados, enumeração de e-mail, contas descartáveis.
  Mitigações possíveis: rate-limit no endpoint, verificação de e-mail/telefone,
  captcha. (Pergunta 7.)
- **Ordem de deploy da V29** vs código (seção 7) — moradores podem perder Avisos se
  invertido.
- **PII de quem nunca foi aprovado** no banco (LGPD) — convidado já aceita consent;
  confirmar retenção/anonimização.

---

## Perguntas em aberto para o Paulo

1. **Onde o usuário "opta por ser convidado"?** Tela de escolha dedicada
   (morador vs convidado) antes do formulário? Um botão/aba no `/login`? Ou um link
   discreto "só quero ver indicações e classificados"? Isso define a UX de entrada.

2. **Status inicial `ACTIVE` direto, certo?** Sem nenhuma aprovação — o convidado
   loga e usa na hora. (Assumi que sim. Se quiser um pente fino, viraria
   `PENDING_APPROVAL`, mas aí perde a graça do "sem aprovação".)

3. **Role nova `GUEST` (preferência "via role") + permission
   `GENERAL_AREAS_VIEW` concedida a todas as outras roles** — aprova essa
   modelagem (opção A da seção 6)? Ou prefere a deny-list por role (opção B)?

4. **Manter o aceite do termo de privacidade no cadastro do convidado?** É coleta
   de PII (nome, e-mail, telefone), então faz sentido manter o consent. Confirmar.

5. **Convidado pode CRIAR conteúdo ou só LER?** Hoje Indicações/Classificados
   permitem qualquer autenticado criar/editar (`isAuthenticated()`). O convidado
   deve poder **postar** indicação/classificado, ou só **visualizar**? Se for só
   ler, precisamos gate de escrita também (mais trabalho).

6. **Como o convidado aparece na tela de acessos / lista de usuários do admin?**
   Badge "Convidado", filtro próprio, ou misturado? Precisa o síndico gerenciar
   (desativar) convidados?

7. **Anti-abuso no cadastro aberto:** quer rate-limit / verificação de e-mail /
   captcha já nesta entrega, ou fica para depois (aceitando o risco de spam)?

8. **Telefone obrigatório?** O master exige `phone`. Para convidado, telefone +
   opt-in WhatsApp fazem sentido (é o único canal outbound), mas confirme se é
   obrigatório ou opcional.
