# Remover Convidado, Proprietário e a posse de unidade

**Data:** 2026-08-27
**Status:** aprovado (design), pendente plano de implementação
**Decisão do controlador dos dados (Paulo):** "não existe mais convidado e não existe mais proprietário".

## 1. Objetivo

Tirar do sistema duas personas que não fazem mais parte do produto — **Convidado** (`GUEST`) e
**Proprietário** (`PROPRIETARIO`) — junto com tudo que existe só para servi-las: a **posse de
unidade** (`UnitOwnership`) e a maquinaria de **comprovante**.

O vínculo com a unidade passa a ser um só: **morar nela**. Sobram o morador master e os moradores
da unidade.

## 2. Contexto: por que agora

- O Convidado **nunca teve tela** e seu endpoint já está fechado: `/api/auth/register-guest` ficou
  fora do `permitAll` e responde 401 a anônimo (`RegisterGuestClosedWebTest`). É quase certo que
  não exista nenhum usuário `GUEST`.
- O Proprietário **está com a porta aberta**: `/api/auth/register-owner` está no `permitAll` e a
  flag `unitownership` passou a vir ligada por padrão desde a inversão das flags (`447c475`).
  Pode haver conta real.
- Em 2026-08-27 (commit `2455e4c`) as permissions `GENERAL_AREAS_VIEW` e `CONTENT_CREATE` passaram
  a ser aplicadas nos controllers, justamente para tornar o Proprietário read-only e restringir o
  Convidado. Sem as duas personas, **esses gates ficam sem dono** e voltam atrás nesta mudança.
- O comprovante só é produzido por `RegisterOwnerPage` e `RegisterExtraUnitPage`. O cadastro de
  master deixou de pedi-lo em 2026-08-20 (`6d55291`). Removendo as duas telas, **nada no app volta
  a produzir um comprovante**.

## 3. Decisões

| Questão | Decisão |
|---|---|
| Escopo | Arrancar do sistema: papéis, permissions, controllers, telas, migrations |
| Posse de unidade (`unit_ownership`, "Registrar unidade", "Pedidos de unidade") | Sai junto |
| Comprovante (colunas, permission, log de acesso, retenção, uploader, arquivos) | Sai junto, incluindo os arquivos |
| Fatiamento | **Um PR só** (decisão do Paulo, ciente de que foge do limite de 400 linhas do CLAUDE.md) |
| Contas de proprietário puro | **Desativar e listar** para o síndico tratar caso a caso |
| Gates de hoje (`GENERAL_AREAS_VIEW`, `CONTENT_CREATE`) | Revertidos para `isAuthenticated()` |

**Ressalva registrada:** o PR único concentra num só deploy uma mudança de RBAC e o apagamento de
dados pessoais. Mitigação aceita: commits ordenados dentro do PR, com a parte destrutiva por
último, e levantamento + dump do banco antes de rodar a migration.

## 4. O que sai

### 4.1 Backend

**Cadastros**
- `RegisterGuestController`, `RegisterOwnerController` e seus DTOs (`RegisterGuestRequest`,
  `RegisterOwnerRequest`).
- Os métodos `registerGuest` / `registerOwner` e o que só eles usam em `RegistrationService`.
- `permitAll` de `/api/auth/register-owner` no `SecurityConfig`; entradas de rate-limit
  correspondentes em `RateLimitProperties` / `application.yml`.

**Posse de unidade**
- `UnitOwnership`, `UnitOwnershipRepository`, `UnitOwnershipService`, `UnitOwnershipException`,
  `MyUnitClaimController`, `OwnershipAdminController`.
- Mapeamento de `UnitOwnershipException` no `GlobalExceptionHandler`.

**RBAC**
- `RoleName`: valores `GUEST` e `PROPRIETARIO`.
- `PermissionCode`: `GENERAL_AREAS_VIEW`, `CONTENT_CREATE`, `RESIDENCE_PROOF_VIEW`.
- `AnnouncementController` (GET list e detalhe) e `FaqController` (GET) voltam a
  `@PreAuthorize("isAuthenticated()")`.
- `ClassifiedController` e `RecommendationController` (POST create) voltam a `isAuthenticated()`.

**Comprovante**
- Campos `residence_proof_*` em `User` e as queries que os usam em `UserRepository`.
- `ProofAccessLog`, `ProofAccessLogRepository` e o trecho correspondente do `AuditWriter`.
- `ProofRetentionScheduler` e a propriedade `app.proof-retention-days`.
- Config de MinIO exclusiva de comprovante: `bucket-proofs`, `presigned-ttl-proofs-seconds`.
- Campos de comprovante em `PendingRegistrationView` e o endpoint de streaming do comprovante em
  `RegistrationAdminController`.
- Campos de comprovante no export de dados pessoais (`PersonalDataExportResponse`) e na
  anonimização (`UserAnonymizedEvent`, `UserAnonymizedListener`).

**Feature flag**
- Campo `unitownership` em `FeatureFlags`, sua entrada no mapa e no `application.yml`.

### 4.2 Frontend

- Páginas: `RegisterOwnerPage`, `OwnershipClaimsPage`, `RegisterExtraUnitPage` (com testes).
- APIs: `ownershipClaimsApi`, `unitClaimsApi`, e os trechos de comprovante em `adminApi` e
  `consentApi`.
- Componente `ProofUploader`.
- Link "Sou proprietário (não moro no condomínio)" na `LoginForm`.
- Rotas `/register-owner`, `/admin/ownership-claims`, `/minha-unidade/registrar` no `router.tsx`.
- Itens de menu "Pedidos de unidade" e "Registrar unidade" na `Sidebar`.
- Bloco de comprovante em `PendingRegistrationsPage`.
- Trecho sobre comprovante de residência na `PrivacyPage`.

### 4.3 Banco (migration nova, V39)

Ordem dentro da migration:

1. `DELETE FROM user_role WHERE role_id IN (7, 9)` — vínculos das roles GUEST e PROPRIETARIO.
2. `DELETE FROM role_permission` das roles 7 e 9 e das três permissions removidas.
3. `DELETE FROM user_permission_grant` das três permissions removidas.
4. `DELETE FROM permission WHERE code IN ('GENERAL_AREAS_VIEW','CONTENT_CREATE','RESIDENCE_PROOF_VIEW')`.
5. `DELETE FROM role WHERE id IN (7, 9)`.
6. `UPDATE "user" SET status = 'DISABLED'` para quem ficou sem nenhuma role (ver 5.2).
7. `DROP TABLE unit_ownership`.
8. `DROP TABLE proof_access_log`.
9. `ALTER TABLE "user" DROP COLUMN` das colunas `residence_proof_*`.

`user_role`, `role_permission`, `user_permission_grant` e `proof_access_log` são exatamente as
tabelas que o CLAUDE.md lista como exceção ao soft delete (M:N puros e logs imutáveis), então o
`DELETE`/`DROP` aqui está dentro da convenção.

## 5. Dados

### 5.1 Levantar antes de destruir

Antes de rodar a migration, em **HML e prod** (SSH `ubuntu@201.23.72.49` + `docker exec` no
Postgres, já que a porta externa é bloqueada por firewall):

- Quantos usuários têm role `GUEST` (esperado: zero) e quantos têm `PROPRIETARIO`.
- Quantos desses têm **só** `PROPRIETARIO` (proprietário puro) — são os que serão desativados.
- Quantas linhas em `unit_ownership` e quantos usuários com `residence_proof_*` preenchido.
- **Dump do banco** antes da migration. Prod já foi perdida uma vez sem backup (2026-06-16); não
  repetir com uma mudança que dropa colunas.

Se der zero em tudo, a migration é trivial e o risco desaparece.

### 5.2 Contas órfãs

Quem é dono **e** morador tem `RESIDENT` + `PROPRIETARIO` e apenas perde o papel extra — segue
normal. Quem tem **só** `PROPRIETARIO` fica sem papel nenhum.

Decisão: a migration move essas contas para `UserStatus.DISABLED` (sem soft delete, sem apagar
nada) e o levantamento produz a lista de quem são, para o síndico tratar caso a caso. Ninguém é
apagado e ninguém fica logando num app vazio.

Critério exato: usuário `ACTIVE` que, depois do passo 1 da migration, não tem **nenhuma** linha em
`user_role`. Isso pega o proprietário puro sem tocar em quem também é morador.

### 5.3 Arquivos no MinIO

Os comprovantes ficam num **bucket dedicado** (`MINIO_BUCKET_PROOFS`, default `residence-proofs`),
separado das fotos. A limpeza é portanto esvaziar/remover esse bucket — não há necessidade de
exportar chaves antes de dropar as colunas, e não existe risco de objeto órfão em bucket
compartilhado.

O Flyway não fala com S3, então este é um **passo operacional**, feito depois que a migration subir
e o app já não referenciar mais o bucket.

### 5.4 Consentimento e privacidade

O app deixa de coletar comprovante de residência, e o texto de privacidade descreve essa coleta.
Provável necessidade de uma **nova versão de consentimento**, no padrão das seeds `V7__seed_consent_v1`
e `V38__seed_consent_v2`. A confirmar com o Paulo durante a implementação: se a nova versão força
re-aceite de todos os usuários ativos, o custo é um popup para todo mundo no próximo acesso.

## 6. O que NÃO sai (fronteira)

- Cadastro de **morador master** e a fila de exceção `/admin/registrations` — perde só o bloco do
  comprovante.
- **Moradores da unidade** (`UnitMemberService`, `/minha-unidade/moradores`), unidades e
  `RESIDENT_MANAGE`.
- RBAC restante: Síndico, Conselheiro, Administração, Morador, Porteiro, Editor do Mural,
  Editor de Documentos.
- Auditoria geral (`audit_log`), consentimento e o resto do LGPD (export e anonimização seguem,
  só sem os campos de comprovante).
- MinIO: o bucket de fotos e todo o restante do storage.
- Migrations históricas `V27`, `V29`, `V37` **não são editadas** — migration aplicada não se
  reescreve; a remoção vem numa migration nova.

## 7. Testes

**Saem** os testes das features removidas: `RegisterGuestControllerWebTest`,
`RegisterGuestClosedWebTest`, `RegisterOwnerControllerWebTest`,
`RegisterOwnerFeatureFlagOffWebTest`, `MyUnitClaimControllerWebTest`,
`OwnershipAdminControllerWebTest`, `OwnershipAdminFeatureFlagOffWebTest`,
`UnitOwnershipServiceTest`, `UnitOwnershipTest`, e no frontend
`RegisterOwnerPage.test`, `OwnershipClaimsPage.test`, `RegisterExtraUnitPage.test`.

**Mudam**: `AnnouncementControllerWebTest` e `FaqControllerWebTest` (voltam a `isAuthenticated()`),
os testes de criação em `ClassifiedControllerWebTest` e `RecommendationControllerWebTest`,
`FeatureControllerWebTest` (sem `unitownership`), `RegistrationAdminControllerWebTest` e
`RegistrationServiceTest` (sem comprovante), `PrivacyServiceTest`, `RepositoryPostgresTest`,
`RateLimitFilterTest`, `UnitMemberServiceTest`, e no frontend `Sidebar.test` e `App.test`
(baseline volta a não exigir `GENERAL_AREAS_VIEW`).

**Entram**, e são os que provam o objetivo:

- `/api/auth/register-owner` e `/api/auth/register-guest` respondem **404** — a porta fechou de
  verdade, não só sumiu do menu.
- O menu não oferece mais "Pedidos de unidade" nem "Registrar unidade" para nenhum papel.

**Verificação final:** o `pre-push` completo (backend, vitest, build de produção, Playwright).
Lembrar do `JAVA_HOME=C:/Users/paulo/.jdks/corretto-21.0.7`, senão o passo do Maven falha com
`release version 21 not supported`.

## 8. Riscos

| Risco | Mitigação |
|---|---|
| Apagar comprovante de quem ainda precisa dele | Levantamento + dump antes; o cadastro de master já não pede comprovante desde 2026-08-20 |
| Proprietário puro real perder acesso sem aviso | Conta desativada (não apagada) + lista para o síndico |
| PR único quebrar prod de uma vez | Commits ordenados, destrutivo por último, suíte completa no pre-push, autodeploy só depois do push |
| Migration dropar coluna que outro código ainda lê | Remoção do código Java vai no mesmo PR da migration; o boot valida o mapeamento Hibernate |

## 9. Sequência dentro do PR

1. Frontend: telas, rotas, menu, APIs, `ProofUploader`.
2. Backend: controllers de cadastro, posse, flag.
3. Backend: RBAC (enums + reversão dos `@PreAuthorize` de hoje).
4. Backend: comprovante (entidade, log, retenção, admin, LGPD).
5. Testes: remoções, ajustes e os dois testes novos.
6. Migration V39 (destrutiva) — por último.
7. Passo operacional pós-deploy: esvaziar o bucket `residence-proofs`.
