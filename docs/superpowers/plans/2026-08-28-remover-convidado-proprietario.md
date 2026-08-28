# Remover Convidado, Proprietário e posse de unidade — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tirar do sistema as personas Convidado (`GUEST`) e Proprietário (`PROPRIETARIO`), a posse de unidade (`UnitOwnership`) e a maquinaria de comprovante, deixando o vínculo com a unidade ser apenas morar nela.

**Architecture:** Remoção em um PR só, com commits ordenados: frontend primeiro (fecha as portas visíveis), depois backend por camada (cadastros/posse → RBAC → comprovante), migration destrutiva por último, e limpeza do bucket MinIO como passo operacional pós-deploy. Cada etapa ajusta seus testes antes de remover o código, de modo que a suíte nunca fica vermelha por mais de um passo.

**Tech Stack:** Java 21 / Spring Boot 3 / Hibernate 6 / Flyway / PostgreSQL; React + TypeScript + Vite + vitest + Playwright; MinIO (S3).

**Spec:** `docs/superpowers/specs/2026-08-27-remover-convidado-proprietario-design.md`

## Global Constraints

- **Migrations Flyway nunca são editadas depois de aplicadas.** `V27`, `V29` e `V37` ficam como estão; a remoção vem numa migration nova (`V39`).
- **Próximo número livre de migration: `V39`** (última é `V38__seed_consent_v2.sql`).
- **Cabeçalho obrigatório da migration:** `-- flyway:transactional=true`.
- **Hard delete é permitido apenas** em `user_role`, `user_permission_grant`, `role_permission` e `proof_access_log` (M:N puros e logs imutáveis, conforme CLAUDE.md). Nenhuma outra tabela pode ser apagada por linha.
- **Português na UI, inglês no código e nos dados.**
- **Conventional Commits.** Proibido trailer `Co-Authored-By` e proibido mencionar agente de IA como autor.
- **Nunca usar `--no-verify`.** Os hooks rodam sempre.
- **`JAVA_HOME` obrigatório em qualquer comando Maven ou push:** `JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7"`. Sem isso o `java` do PATH é o 17 e o build morre com `release version 21 not supported`.
- **Rodar `npm run build` no frontend antes do push:** o vitest usa esbuild e não checa tipos; erro de tipo só apareceria no deploy.
- **Status de usuário existentes:** `PENDING_APPROVAL`, `ACTIVE`, `REJECTED`, `DISABLED`, `ANONYMIZED`.

---

### Task 0: Levantamento em HML e prod (bloqueia a migration)

Nenhum código. Produz os números que decidem se a `V39` é trivial ou se precisa desativar contas de verdade. **Não prosseguir para a Task 5 sem isto.**

**Files:** nenhum.

**Interfaces:**
- Produces: contagens que a Task 5 usa para confirmar o comportamento da migration; a lista de proprietários puros que vai para o síndico.

- [ ] **Step 1: Abrir sessão no servidor**

O Postgres não é acessível de fora (firewall); vai por SSH + `docker exec`.

```bash
ssh ubuntu@201.23.72.49
```

- [ ] **Step 2: Contar usuários por role removida**

Rodar em **HML e depois em prod** (o nome do container do Postgres de prod mudou na recriação de 2026-06-16 — listar com `docker ps | grep postgres` antes):

```sql
SELECT r.name, count(*) AS usuarios
FROM user_role ur JOIN role r ON r.id = ur.role_id
WHERE r.name IN ('GUEST','PROPRIETARIO')
GROUP BY r.name;
```

Esperado: `GUEST` = 0 (o endpoint está fechado e nunca teve tela).

- [ ] **Step 3: Contar proprietários puros (os que serão desativados)**

```sql
SELECT u.id, u.full_name, u.email, u.status
FROM "user" u
WHERE u.deleted_at IS NULL
  AND EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id
              WHERE ur.user_id = u.id AND r.name IN ('GUEST','PROPRIETARIO'))
  AND NOT EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id
                  WHERE ur.user_id = u.id AND r.name NOT IN ('GUEST','PROPRIETARIO'));
```

Guardar o resultado — é a lista que vai para o síndico tratar caso a caso.

- [ ] **Step 4: Contar posses e comprovantes**

```sql
SELECT count(*) FROM unit_ownership;
SELECT count(*) FROM "user" WHERE residence_proof_object_key IS NOT NULL;
SELECT count(*) FROM proof_access_log;
```

- [ ] **Step 5: Dump do banco antes de qualquer coisa destrutiva**

Prod já foi perdida uma vez sem backup (2026-06-16). Não repetir com uma mudança que dropa colunas.

```bash
docker exec <container-postgres> pg_dump -U <user> <db> | gzip > ~/backup-pre-remocao-$(date +%F).sql.gz
```

- [ ] **Step 6: CHECKPOINT — reportar ao Paulo**

Mostrar os números. Se `GUEST > 0` ou se houver proprietário puro real, confirmar o tratamento antes de seguir. Se tudo zero, seguir direto.

---

### Task 1: Frontend — remover telas, rotas, menu e uploader

**Files:**
- Delete: `frontend/src/features/auth/pages/RegisterOwnerPage.tsx`
- Delete: `frontend/src/features/auth/pages/RegisterOwnerPage.test.tsx`
- Delete: `frontend/src/features/admin/pages/OwnershipClaimsPage.tsx`
- Delete: `frontend/src/features/admin/pages/OwnershipClaimsPage.test.tsx`
- Delete: `frontend/src/features/admin/api/ownershipClaimsApi.ts`
- Delete: `frontend/src/features/units/pages/RegisterExtraUnitPage.tsx`
- Delete: `frontend/src/features/units/pages/RegisterExtraUnitPage.test.tsx`
- Delete: `frontend/src/features/units/api/unitClaimsApi.ts`
- Delete: `frontend/src/components/ProofUploader.tsx`
- Modify: `frontend/src/router.tsx` (imports + rotas `/register-owner`, `/admin/ownership-claims`, `/minha-unidade/registrar`)
- Modify: `frontend/src/components/layout/Sidebar.tsx` (itens "Pedidos de unidade" e "Registrar unidade", e o ícone `Building2` se ficar sem uso)
- Modify: `frontend/src/components/layout/Sidebar.test.tsx`
- Modify: `frontend/src/features/auth/LoginForm.tsx` (link "Sou proprietário")
- Modify: `frontend/src/features/admin/pages/PendingRegistrationsPage.tsx` e `.test.tsx` (bloco do comprovante)
- Modify: `frontend/src/features/admin/api/adminApi.ts` (funções de comprovante)
- Modify: `frontend/src/features/consent/api/consentApi.ts` (campos de comprovante)
- Modify: `frontend/src/features/privacy/pages/PrivacyPage.tsx` (texto sobre comprovante)

**Interfaces:**
- Consumes: nada de tarefas anteriores.
- Produces: nenhuma rota `/register-owner`, `/admin/ownership-claims` ou `/minha-unidade/registrar` no `router.tsx`; nenhum item de menu com esses destinos.

- [ ] **Step 1: CHECKPOINT — decidir o consentimento antes de mexer no texto**

Perguntar ao Paulo: o app deixa de coletar comprovante de residência e a `PrivacyPage` descreve essa coleta. Sobe uma nova versão de consentimento (padrão das seeds `V7__seed_consent_v1` / `V38__seed_consent_v2`), o que força re-aceite e um popup para todos os usuários ativos no próximo acesso? Ou ajusta só o texto sem versionar?

Se a resposta for "sobe versão", adicionar a seed na Task 5 junto da `V39`. Se for "só o texto", seguir.

- [ ] **Step 2: Escrever o teste que exige o menu sem os dois itens**

Em `frontend/src/components/layout/Sidebar.test.tsx`, substituir o teste `'esconde "Registrar unidade" e "Pedidos de unidade" com unitownership desligada'` (a flag deixa de existir) por:

```tsx
it('não oferece mais posse de unidade a ninguém', () => {
  renderSidebar(['REGISTRATION_VIEW', 'ROLE_ASSIGN', 'RESIDENT_MANAGE']);
  expect(screen.queryByRole('link', { name: /registrar unidade/i })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /registrar unidade/i })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: /pedidos de unidade/i })).not.toBeInTheDocument();
  // a fila de excecao do admin continua
  expect(screen.getAllByRole('link', { name: /cadastros pendentes/i })[0]).toBeInTheDocument();
});
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/components/layout/Sidebar.test.tsx`
Expected: FAIL — os itens ainda estão no menu.

- [ ] **Step 4: Remover os itens do menu**

Em `Sidebar.tsx`, apagar as duas entradas de `ENTRIES` cujos `to` são `/admin/ownership-claims` e `/minha-unidade/registrar`. Remover o import de `Building2` se nenhum outro item usar.

- [ ] **Step 5: Rodar e ver passar**

Run: `cd frontend && npx vitest run src/components/layout/Sidebar.test.tsx`
Expected: PASS

- [ ] **Step 6: Apagar telas, APIs e o uploader**

```bash
cd frontend/src
rm features/auth/pages/RegisterOwnerPage.tsx features/auth/pages/RegisterOwnerPage.test.tsx
rm features/admin/pages/OwnershipClaimsPage.tsx features/admin/pages/OwnershipClaimsPage.test.tsx
rm features/admin/api/ownershipClaimsApi.ts
rm features/units/pages/RegisterExtraUnitPage.tsx features/units/pages/RegisterExtraUnitPage.test.tsx
rm features/units/api/unitClaimsApi.ts
rm components/ProofUploader.tsx
```

- [ ] **Step 7: Remover rotas e imports órfãos**

Em `router.tsx`, apagar os imports de `RegisterOwnerPage`, `OwnershipClaimsPage` e `RegisterExtraUnitPage` e as três entradas de rota correspondentes.

- [ ] **Step 8: Remover o link do Proprietário e o bloco do comprovante**

- `LoginForm.tsx`: apagar o link "Sou proprietário (não moro no condomínio)" que aponta para `/register-owner`.
- `PendingRegistrationsPage.tsx`: apagar o bloco que exibe/baixa o comprovante e o que ele usa de `adminApi.ts`; ajustar `PendingRegistrationsPage.test.tsx` na mesma passada.
- `adminApi.ts` e `consentApi.ts`: apagar as funções e campos de comprovante.
- `PrivacyPage.tsx`: ajustar o texto conforme decidido no Step 1.

- [ ] **Step 9: Suíte + build**

Run: `cd frontend && npx vitest run && npm run build`
Expected: PASS nos dois. O build é o que pega import órfão e tipo quebrado.

- [ ] **Step 10: Commit**

```bash
git add frontend/src
git commit -m "refactor(acesso): remove telas de proprietario, posse de unidade e comprovante"
```

---

### Task 2: Backend — cadastros e posse de unidade

**Files:**
- Delete: `backend/src/main/java/br/com/condominio/feature/registration/RegisterGuestController.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/registration/RegisterOwnerController.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/registration/dto/RegisterGuestRequest.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/registration/dto/RegisterOwnerRequest.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/unit/UnitOwnership.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/unit/UnitOwnershipRepository.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/unit/UnitOwnershipService.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/unit/UnitOwnershipException.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/unit/MyUnitClaimController.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/unit/OwnershipAdminController.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/unit/OwnershipStatus.java`
- Delete os testes: `RegisterGuestControllerWebTest`, `RegisterGuestClosedWebTest`, `RegisterOwnerControllerWebTest`, `RegisterOwnerFeatureFlagOffWebTest`, `MyUnitClaimControllerWebTest`, `OwnershipAdminControllerWebTest`, `OwnershipAdminFeatureFlagOffWebTest`, `UnitOwnershipServiceTest`, `UnitOwnershipTest`
- Create: `backend/src/test/java/br/com/condominio/feature/registration/RegistrationClosedRoutesTest.java`
- Modify: `RegistrationService.java` (métodos `registerOwner` linha ~136 e `registerGuest` linha ~230)
- Modify: `SecurityConfig.java` (`/api/auth/register-owner` do `permitAll`)
- Modify: `GlobalExceptionHandler.java` (mapeamento de `UnitOwnershipException`)
- Modify: `FeatureFlags.java` (campo `unitownership` e entrada do mapa)
- Modify: `RateLimitProperties.java` e `application.yml` (entradas de register-owner/register-guest, flag `unitownership`)
- Modify: `FeatureControllerWebTest.java`, `RateLimitFilterTest.java`, `RegistrationServiceTest.java`, `RepositoryPostgresTest.java`, `UnitMemberServiceTest.java`

**Interfaces:**
- Consumes: nada da Task 1 (camadas independentes).
- Produces: `POST /api/auth/register-owner` e `POST /api/auth/register-guest` deixam de existir (404). `FeatureFlags.asMap()` sem a chave `unitownership`.

- [ ] **Step 1: Escrever o teste que exige as rotas mortas**

Criar `backend/src/test/java/br/com/condominio/feature/registration/RegistrationClosedRoutesTest.java`:

```java
package br.com.condominio.feature.registration;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Convidado e Proprietário saíram do produto (2026-08-27): as rotas de criação de conta desses
 * tipos não podem mais existir.
 *
 * <p>Guarda <b>estrutural</b>, e não de status HTTP, de propósito. Um {@code @WebMvcTest} fatiado
 * registra apenas o controller nomeado, então responderia 404 para essas rotas mesmo com o
 * controller ainda no código — o teste passaria de imediato e não provaria nada. Sem a classe não
 * há mapeamento possível, e é isso que se verifica aqui.
 */
class RegistrationClosedRoutesTest {

  @Test
  void naoExisteMaisControllerDeProprietario() {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("br.com.condominio.feature.registration.RegisterOwnerController"));
  }

  @Test
  void naoExisteMaisControllerDeConvidado() {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("br.com.condominio.feature.registration.RegisterGuestController"));
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q -Dtest=RegistrationClosedRoutesTest`
Expected: FAIL nos dois testes — as duas classes ainda existem, entao `Class.forName` resolve em vez de lancar `ClassNotFoundException`.

- [ ] **Step 3: Apagar controllers, DTOs, posse e seus testes**

```bash
cd backend/src
rm main/java/br/com/condominio/feature/registration/RegisterGuestController.java
rm main/java/br/com/condominio/feature/registration/RegisterOwnerController.java
rm main/java/br/com/condominio/feature/registration/dto/RegisterGuestRequest.java
rm main/java/br/com/condominio/feature/registration/dto/RegisterOwnerRequest.java
rm main/java/br/com/condominio/feature/unit/UnitOwnership*.java
rm main/java/br/com/condominio/feature/unit/MyUnitClaimController.java
rm main/java/br/com/condominio/feature/unit/OwnershipAdminController.java
rm main/java/br/com/condominio/feature/unit/OwnershipStatus.java
rm test/java/br/com/condominio/feature/registration/RegisterGuest*.java
rm test/java/br/com/condominio/feature/registration/RegisterOwner*.java
rm test/java/br/com/condominio/feature/unit/MyUnitClaimControllerWebTest.java
rm test/java/br/com/condominio/feature/unit/OwnershipAdmin*.java
rm test/java/br/com/condominio/feature/unit/UnitOwnership*.java
```

- [ ] **Step 4: Limpar o que referenciava**

- `RegistrationService.java`: apagar `registerOwner` e `registerGuest` e os helpers privados que só eles usam.
- `SecurityConfig.java`: tirar `"/api/auth/register-owner"` da lista de `permitAll` do `POST`.
- `GlobalExceptionHandler.java`: apagar o `@ExceptionHandler` de `UnitOwnershipException`.
- `FeatureFlags.java`: apagar o campo `unitownership`, seu `@Value` e a linha `m.put("unitownership", unitownership)` de `asMap()`.
- `RateLimitProperties.java` + `application.yml`: apagar as entradas de rate-limit de register-owner/register-guest e o bloco `feature.unitownership`.
- `FeatureControllerWebTest.java`: tirar `unitownership` das asserções do mapa.
- `RateLimitFilterTest.java`, `RegistrationServiceTest.java`, `RepositoryPostgresTest.java`, `UnitMemberServiceTest.java`: remover casos que exercitam posse/convidado/proprietário.

- [ ] **Step 5: Rodar a suíte inteira**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q`
Expected: PASS, incluindo `RegistrationClosedRoutesTest`.

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "refactor(acesso): remove cadastros de convidado/proprietario e a posse de unidade"
```

---

### Task 3: Backend — RBAC (reverter os gates e limpar os enums)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/role/RoleName.java` (valores `GUEST`, `PROPRIETARIO`)
- Modify: `backend/src/main/java/br/com/condominio/feature/role/PermissionCode.java` (`GENERAL_AREAS_VIEW`, `CONTENT_CREATE`, `RESIDENCE_PROOF_VIEW`)
- Modify: `feature/announcement/AnnouncementController.java:35` e `:44`
- Modify: `feature/faq/FaqController.java:31`
- Modify: `feature/classified/ClassifiedController.java:55`
- Modify: `feature/recommendation/RecommendationController.java:58`
- Modify: `AnnouncementControllerWebTest`, `FaqControllerWebTest`, `ClassifiedControllerWebTest`, `RecommendationControllerWebTest`
- Modify: `frontend/src/components/layout/Sidebar.tsx`, `Sidebar.test.tsx`, `App.tsx`, `App.test.tsx`
- Delete: `frontend/src/components/auth/CreateContentButton.tsx` e `.test.tsx`
- Modify: `frontend/src/features/classifieds/pages/ClassifiedsListPage.tsx`, `frontend/src/features/recommendations/pages/RecommendationsListPage.tsx`

**Interfaces:**
- Consumes: nada.
- Produces: `RoleName` sem `GUEST`/`PROPRIETARIO`; `PermissionCode` sem as três permissions; leitura de avisos/FAQ e criação de indicação/classificado de volta a `isAuthenticated()`.

- [ ] **Step 1: Ajustar os testes de backend para a expectativa nova**

Em `AnnouncementControllerWebTest`: apagar o teste `list_semGeneralAreasView_returns403`, a constante `VIEW` e trocar as duas chamadas `MockAuth.user(UID, VIEW)` por `MockAuth.user(UID)`.
Em `FaqControllerWebTest`: apagar `listPublished_semGeneralAreasView_returns403`, a constante `VIEW`, e trocar `MockAuth.user(UID, VIEW)` por `MockAuth.user(UID)`.
Em `ClassifiedControllerWebTest`: apagar `create_semContentCreate_returns403`, a constante `CREATE`, e trocar `MockAuth.user(UID, CREATE)` por `MockAuth.user(UID)`.
Em `RecommendationControllerWebTest`: apagar `create_semContentCreate_returns403`, a constante `CREATE`, trocar `MockAuth.user(UID, CREATE)` por `MockAuth.user(UID)` e `MockAuth.userWithUnit(UID, unitId, CREATE)` por `MockAuth.userWithUnit(UID, unitId)`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q -Dtest='AnnouncementControllerWebTest,FaqControllerWebTest,ClassifiedControllerWebTest,RecommendationControllerWebTest'`
Expected: FAIL com `Status expected:<200> but was:<403>` — os gates ainda exigem as permissions.

- [ ] **Step 3: Reverter os quatro `@PreAuthorize`**

Trocar `@PreAuthorize("hasAuthority('GENERAL_AREAS_VIEW')")` por `@PreAuthorize("isAuthenticated()")` nas duas linhas do `AnnouncementController` e na do `FaqController`; trocar `@PreAuthorize("hasAuthority('CONTENT_CREATE')")` por `@PreAuthorize("isAuthenticated()")` no `POST` de `ClassifiedController` e de `RecommendationController`.

- [ ] **Step 4: Limpar os enums**

Remover `GUEST` e `PROPRIETARIO` de `RoleName`; remover `GENERAL_AREAS_VIEW`, `CONTENT_CREATE` e `RESIDENCE_PROOF_VIEW` de `PermissionCode`. O compilador aponta qualquer uso restante — resolver todos.

- [ ] **Step 5: Rodar backend e ver passar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q`
Expected: PASS

- [ ] **Step 6: Reverter o gate no frontend**

- `Sidebar.tsx`: apagar `requires: 'GENERAL_AREAS_VIEW'` dos itens Avisos e Perguntas Frequentes.
- `App.tsx`: apagar `requires: 'GENERAL_AREAS_VIEW'` dos cards Mural de avisos e Perguntas Frequentes.
- `Sidebar.test.tsx`: voltar o default de `renderSidebar` para `[]`, trocar `renderSidebar(['GENERAL_AREAS_VIEW'], '/avisos')` por `renderSidebar([], '/avisos')` nas três ocorrências, e apagar o teste `'convidado (sem GENERAL_AREAS_VIEW) não vê Avisos nem Perguntas Frequentes'` e o `'proprietário (só leitura) não vê itens de escrita/admin'`.
- `App.test.tsx`: voltar o default de `renderApp` para `['USER_VIEW']` e apagar o teste `'convidado (sem GENERAL_AREAS_VIEW) não vê Mural nem Perguntas Frequentes'`.

- [ ] **Step 7: Devolver os botões de publicar ao estado simples**

Apagar `CreateContentButton.tsx` e `CreateContentButton.test.tsx`. Em `ClassifiedsListPage.tsx` e `RecommendationsListPage.tsx`, voltar ao botão-link direto:

```tsx
<Button asChild className="min-h-[44px]">
  <Link to="/classificados/novo">Novo anúncio</Link>
</Button>
```

```tsx
<Button asChild className="min-h-[44px]">
  <Link to="/indicacoes/nova">Nova indicação</Link>
</Button>
```

Remover o `AuthContext.Provider` que envolveu `renderPage` em `ClassifiedsListPage.test.tsx` e `RecommendationsListPage.test.tsx` (as páginas voltam a não depender de sessão) e o import de `AuthContext`.

- [ ] **Step 8: Suíte + build do frontend**

Run: `cd frontend && npx vitest run && npm run build`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add backend/src frontend/src
git commit -m "refactor(acesso): remove as permissions das personas e reverte seus gates"
```

---

### Task 4: Backend — maquinaria de comprovante

**Files:**
- Delete: `backend/src/main/java/br/com/condominio/feature/audit/ProofAccessLog.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/audit/ProofAccessLogRepository.java`
- Delete: `backend/src/main/java/br/com/condominio/feature/retention/ProofRetentionScheduler.java`
- Modify: `feature/user/User.java:70-83` (cinco campos) e os métodos que setam `proofVerifiedAt` (linhas ~194 e ~223) e o `anonymize()` (~261-273)
- Modify: `feature/user/UserRepository.java:31-38` (`findApprovedWithProofBefore`)
- Modify: `feature/audit/AuditWriter.java` (`logProofAccess`)
- Modify: `feature/registration/RegistrationAdminController.java:57-91` (endpoints `/{id}/proof-url` e `/{id}/proof`)
- Modify: `feature/registration/RegistrationService.java:401-432` (`getProofPresignedUrl`, `getProofContent`, record `ProofContent`)
- Modify: `feature/registration/dto/PendingRegistrationView.java` (campos `residenceProofFilename`, `residenceProofUploadedAt`)
- Modify: `feature/privacy/dto/PersonalDataExportResponse.java`, `feature/privacy/event/UserAnonymizedEvent.java`, `feature/privacy/UserAnonymizedListener.java`
- Modify: `backend/src/main/resources/application.yml` (`bucket-proofs`, `presigned-ttl-proofs-seconds`, `proof-retention-days`)
- Modify: `RegistrationAdminControllerWebTest`, `PrivacyServiceTest`

**Interfaces:**
- Consumes: `PendingRegistrationView` da Task 2 (inalterado por ela).
- Produces: `PendingRegistrationView` sem os dois campos de comprovante — o frontend já parou de lê-los na Task 1.

- [ ] **Step 1: Ajustar os testes para a expectativa nova**

Em `RegistrationAdminControllerWebTest`: apagar os testes dos endpoints `/{id}/proof-url` e `/{id}/proof` e ajustar as asserções de `PendingRegistrationView` que citam os campos removidos.
Em `PrivacyServiceTest`: remover as asserções sobre campos de comprovante no export e na anonimização.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q -Dtest='RegistrationAdminControllerWebTest,PrivacyServiceTest'`
Expected: FAIL de compilação — os testes não batem mais com os records.

- [ ] **Step 3: Apagar log de acesso e agendador de retenção**

```bash
cd backend/src/main/java/br/com/condominio
rm feature/audit/ProofAccessLog.java feature/audit/ProofAccessLogRepository.java
rm feature/retention/ProofRetentionScheduler.java
```

- [ ] **Step 4: Limpar entidade, repositório e serviços**

- `User.java`: apagar os cinco campos (`residenceProofObjectKey`, `residenceProofFilename`, `residenceProofContentType`, `residenceProofUploadedAt`, `proofVerifiedAt`), as atribuições de `proofVerifiedAt` nos métodos de aprovação, e o trecho do `anonymize()` que limpa e devolve `objectKeyToPurge`.
- `UserRepository.java`: apagar `findApprovedWithProofBefore` e seu `@Query`.
- `AuditWriter.java`: apagar `logProofAccess` e a dependência de `ProofAccessLogRepository`.
- `RegistrationService.java`: apagar `getProofPresignedUrl`, `getProofContent` e o record `ProofContent`.
- `RegistrationAdminController.java`: apagar os dois endpoints de comprovante e os imports órfãos.
- `PendingRegistrationView.java`: apagar os dois campos de comprovante e ajustar quem constrói o record.
- `PersonalDataExportResponse`, `UserAnonymizedEvent`, `UserAnonymizedListener`: remover os campos e o fluxo de purga do objeto no MinIO.
- `application.yml`: apagar `bucket-proofs`, `presigned-ttl-proofs-seconds` e `proof-retention-days`.

- [ ] **Step 5: Rodar a suíte inteira**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "refactor(lgpd): remove a maquinaria de comprovante, que ficou sem produtor"
```

---

### Task 5: Migration V39 (destrutiva — só depois da Task 0)

**Files:**
- Create: `backend/src/main/resources/db/migration/V39__remove_guest_owner_ownership_proof.sql`

**Interfaces:**
- Consumes: as contagens da Task 0; o código Java das Tasks 2-4 já não referencia nada disto.
- Produces: schema sem `unit_ownership`, sem `proof_access_log`, sem as colunas de comprovante e sem as duas roles / três permissions.

- [ ] **Step 1: Escrever a migration**

```sql
-- flyway:transactional=true

-- Convidado e Proprietário saíram do produto (decisão do controlador dos dados, 2026-08-27).
-- Sai junto tudo que existia só para servi-los: posse de unidade e comprovante.
-- Ordem: vínculos → permissions → roles → contas órfãs → tabelas → colunas.

-- 1. Vínculos das roles removidas (user_role é M:N puro: hard delete permitido).
DELETE FROM user_role
WHERE role_id IN (SELECT id FROM role WHERE name IN ('GUEST', 'PROPRIETARIO'));

-- 2. Grants das roles e das permissions removidas (role_permission é M:N puro).
DELETE FROM role_permission
WHERE role_id IN (SELECT id FROM role WHERE name IN ('GUEST', 'PROPRIETARIO'))
   OR permission_id IN (SELECT id FROM permission
                        WHERE code IN ('GENERAL_AREAS_VIEW', 'CONTENT_CREATE',
                                       'RESIDENCE_PROOF_VIEW'));

-- 3. Grants individuais das permissions removidas (log/M:N: hard delete permitido).
DELETE FROM user_permission_grant
WHERE permission_id IN (SELECT id FROM permission
                        WHERE code IN ('GENERAL_AREAS_VIEW', 'CONTENT_CREATE',
                                       'RESIDENCE_PROOF_VIEW'));

DELETE FROM permission
WHERE code IN ('GENERAL_AREAS_VIEW', 'CONTENT_CREATE', 'RESIDENCE_PROOF_VIEW');

DELETE FROM role WHERE name IN ('GUEST', 'PROPRIETARIO');

-- 4. Proprietário puro ficou sem papel nenhum: desativa (NÃO apaga) para o síndico tratar.
--    Quem também é morador manteve RESIDENT e não é tocado aqui.
UPDATE "user"
SET status = 'DISABLED', updated_at = now()
WHERE status = 'ACTIVE'
  AND deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id = "user".id);

-- 5. Posse de unidade e log de acesso a comprovante.
DROP TABLE IF EXISTS unit_ownership;
DROP TABLE IF EXISTS proof_access_log;

-- 6. Colunas de comprovante no usuário.
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_object_key;
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_filename;
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_content_type;
ALTER TABLE "user" DROP COLUMN IF EXISTS residence_proof_uploaded_at;
ALTER TABLE "user" DROP COLUMN IF EXISTS proof_verified_at;
```

- [ ] **Step 2: Verificar contra o levantamento da Task 0**

Conferir que o número de contas que o passo 4 desativaria bate com a contagem de proprietários puros do Step 3 da Task 0. Se não bater, **parar** e investigar antes de rodar em prod.

- [ ] **Step 3: Rodar a suíte de persistência**

Run: `cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q`
Expected: PASS. `RepositoryPostgresTest` sobe Postgres via Testcontainers e roda o Flyway inteiro — é a prova de que a migration aplica sobre o schema real.

Se o Docker não estiver rodando na máquina, esse teste é pulado; nesse caso subir o Docker antes, senão a migration só seria validada no deploy.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V39__remove_guest_owner_ownership_proof.sql
git commit -m "feat(db): V39 remove roles, posse de unidade e comprovante"
```

---

### Task 6: Verificação de ponta a ponta e push

**Files:** nenhum novo.

**Interfaces:**
- Consumes: tudo das Tasks 1-5.

- [ ] **Step 1: Buscar referências órfãs**

```bash
cd /d/Projetos/gestor-condominio
grep -rn "GUEST\|PROPRIETARIO\|UnitOwnership\|unitownership\|register-owner\|register-guest\|ProofUploader\|residenceProof\|GENERAL_AREAS_VIEW\|CONTENT_CREATE\|RESIDENCE_PROOF_VIEW" backend/src frontend/src frontend/e2e
```

Expected: nenhum resultado fora de comentários históricos e das migrations antigas (`V27`, `V29`, `V37`), que não se editam.

- [ ] **Step 2: Rodar tudo o que o pre-push roda**

```bash
cd backend && JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" ./mvnw test -q
cd ../frontend && npx vitest run && npm run build && npm run test:e2e
```

Expected: PASS nos quatro.

- [ ] **Step 3: Push**

```bash
JAVA_HOME="C:/Users/paulo/.jdks/corretto-21.0.7" git push
```

O `pre-push` roda a suíte inteira de novo; autodeploy leva para HML e prod.

- [ ] **Step 4: Conferir o deploy**

Abrir HML e confirmar: menu sem "Pedidos de unidade" e "Registrar unidade"; `/register-owner` não carrega; login e navegação normais; `/admin/registrations` abre sem o bloco de comprovante.

---

### Task 7: Limpeza do bucket de comprovantes (operacional, pós-deploy)

Só depois de confirmar que a aplicação subiu sem referência ao bucket. Irreversível.

**Files:** nenhum.

- [ ] **Step 1: Confirmar que o app não usa mais o bucket**

O `application.yml` já não tem `bucket-proofs` depois da Task 4; conferir nos logs do backend em HML que não há erro de MinIO no boot.

- [ ] **Step 2: Listar o que será apagado**

```bash
mc ls --recursive <alias>/residence-proofs | tee ~/comprovantes-apagados-$(date +%F).txt
```

Guardar o arquivo como registro do que foi removido.

- [ ] **Step 3: Esvaziar e remover o bucket, em HML primeiro**

```bash
mc rb --force <alias>/residence-proofs
```

- [ ] **Step 4: Repetir em prod**

Mesmo comando apontando para o MinIO de prod (alias do `minio-prod-svc`).

- [ ] **Step 5: Remover a variável de ambiente órfã**

Tirar `MINIO_BUCKET_PROOFS` (e `MINIO_PRESIGNED_TTL_PROOFS_SECONDS`, `APP_PROOF_RETENTION_DAYS`, `APP_FEATURE_UNITOWNERSHIP_ENABLED` se existirem) do env do backend no Dokploy, em HML e prod.
