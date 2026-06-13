# Convidado (Guest) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que qualquer pessoa se cadastre como **convidado** (login sem unidade, sem comprovante e sem aprovação), com acesso restrito apenas à leitura de Indicações e Classificados, barrando Avisos/Informações/FAQ e a criação de conteúdo.

**Architecture:** Role nova `GUEST` (auto-atribuída, `assignable=false`, `ACTIVE` direto). Restrição feita por **ausência de permission**: cria-se `GENERAL_AREAS_VIEW` (concedida a todas as roles atuais, não ao GUEST) que passa a gatear os GETs de Avisos/Info/FAQ, e `CONTENT_CREATE` (idem) que passa a gatear a criação em Indicações/Classificados. Leitura de Indicações/Classificados segue aberta (`isAuthenticated()`). Cadastro via `POST /api/auth/register-guest` (JSON, público), com rate-limit no `RateLimitFilter` e verificação de captcha plugável (no-op quando desligada). Frontend ganha uma tela de escolha "Morador principal vs Convidado" e a `RegisterGuestPage`.

**Tech Stack:** Spring Boot 3 + JPA/Hibernate 6 + Flyway + Spring Security (method security por permission) + bucket4j; React 18 + react-router 7 + axios + shadcn/ui + Tailwind. Testes back: JUnit 5 + Mockito + `@WebMvcTest` (+ helper `MockAuth`).

---

## ⚠️ Premissas e perguntas em aberto

Decisões já fechadas pelo Paulo (spec `docs/superpowers/specs/2026-06-12-convidado-design.md`, seção 0) e assumidas aqui: role `GUEST`; `ACTIVE` direto sem aprovação; convidado só **lê** Indicações/Classificados (criação gated); restringir = barrar o resto via `GENERAL_AREAS_VIEW`; anti-abuso (rate-limit + captcha) já nesta feature; tela de escolha "Convidado vs Morador principal" na entrada do cadastro.

Premissas adotadas neste plano (mudar exige ajuste pontual; estão isoladas):

1. **Telefone do convidado: OBRIGATÓRIO.** Mesma regra do master (`@NotBlank @Pattern(...)`), pois WhatsApp é o único canal outbound (CLAUDE.md). **Pergunta:** confirmar; se opcional, remover `@NotBlank` do campo `phone` no `RegisterGuestRequest` (Task 2) e relaxar `canSubmit` no front (Task 14).
2. **`mustChangePassword = false`** para convidado (ele define a própria senha no cadastro, igual ao master). Sem necessidade de troca no primeiro login.
3. **Consent mantido** no cadastro do convidado (coleta de PII: nome, e-mail, telefone). `consentVersion` obrigatório, validado contra `ConsentDocument`.
4. **Captcha — provider não decidido.** O plano introduz uma abstração `CaptchaVerifier` com implementação default no-op controlada por flag `app.security.captcha.enabled` (Prod default = depende de provider escolhido). **Pergunta:** provider (Cloudflare Turnstile recomendado — sem custo, sem cookies de terceiros; alternativa hCaptcha). A implementação concreta (chamada HTTP ao provider) fica num PR separado (PR 4) e **não bloqueia** o resto; enquanto desligada, o endpoint exige apenas rate-limit. O front envia o token num campo `captchaToken` (ignorado pelo backend quando flag off).
5. **UX da tela de escolha:** nova rota pública `/register` que apresenta dois cards ("Sou morador / proprietário" → `/register-master`; "Quero só ver Indicações e Classificados (convidado)" → `/register-guest`). `LoginPage` passa a linkar `/register` em vez de `/register-master` direto. **Pergunta:** o Paulo quer copy/identidade visual específica? (placeholders de texto neutros usados aqui).
6. **Pós-cadastro do convidado:** redirect para `/login` com toast "Cadastro concluído! Faça login." (não `/pending-approval`, pois já está `ACTIVE`). **Pergunta:** auto-login seria melhor UX, mas exige o endpoint devolver tokens — fora de escopo; fica `/login`.
7. **Sem feature flag dedicada para o cadastro de convidado.** O endpoint sobe ligado (é aditivo e público como `register-master`). As áreas Avisos/Info/Indicações/etc. já têm flags próprias (`app.feature.*.enabled`); este plano não as altera.
8. **Tela de acessos / badge "Convidado":** fora de escopo deste plano (spec, Pergunta 6, sem decisão). Convidado não aparece em listagens de moradores (sem `unit_id`, sem `RESIDENT`). Anotado como follow-up.

### Corte em PRs (proposto)

- **PR 1 — Migration + enums (base):** `V29` (role `GUEST`, permissions `GENERAL_AREAS_VIEW`=18 e `CONTENT_CREATE`=19 + grants a todas as roles menos GUEST), `RoleName.GUEST`, `PermissionCode.GENERAL_AREAS_VIEW`/`CONTENT_CREATE`. **Não troca nenhum `@PreAuthorize` ainda.** Tasks 1. (~80 linhas) — *deve ir para HML/Prod **antes** ou **junto** do PR 2, nunca depois; ver "Ordem de deploy".*
- **PR 2 — Backend cadastro do convidado:** `RegisterGuestRequest`, `RegistrationService.registerGuest`, `RegisterGuestController`, rota pública no `SecurityConfig`, rate-limit no `RateLimitFilter`, hook do `CaptchaVerifier` (interface + no-op). Tasks 2–6. (~280 linhas)
- **PR 3 — Backend restrição (gating):** trocar `isAuthenticated()` → `hasAuthority('GENERAL_AREAS_VIEW')` nos GETs de Avisos/Info/FAQ; criar de Indicação/Classificado → `hasAuthority('CONTENT_CREATE')`. Tests de contrato (403/200). Tasks 7–10. (~220 linhas) — **depende do PR 1 já estar aplicado em todos os ambientes.**
- **PR 4 — Frontend:** tela de escolha `/register`, `RegisterGuestPage`, `registerGuest` API, gating de nav por `GENERAL_AREAS_VIEW`, captcha no form. Tasks 11–16. (~330 linhas — pode dividir em 4a nav+escolha e 4b page+captcha se passar de 400)
- **(Opcional) PR 5 — Captcha provider real:** implementação concreta do `CaptchaVerifier` (HTTP ao Turnstile/hCaptcha) + flag. Só depois da decisão do provider. Não incluído em detalhe aqui (esqueleto entregue no PR 2).

### Ordem de deploy ↔ migration (CRÍTICO)

`GENERAL_AREAS_VIEW` e `CONTENT_CREATE` só passam a ser **exigidas** quando o código do PR 3 sobe. Se o PR 3 subir **antes** de a migration V29 ter concedido essas permissions às roles, **moradores perdem Avisos/Info/FAQ e a criação de conteúdo**. Regra: **PR 1 (migration) deve ser aplicado em cada ambiente antes do PR 3.** Como Flyway roda no boot antes de servir tráfego, se PR 1 e PR 3 forem no mesmo release a ordem é garantida; se forem releases separados, PR 1 vai primeiro. **Nunca** mergear/deployar PR 3 sem PR 1 já presente.

---

## File Structure

**Backend — criar:**
- `backend/src/main/java/br/com/condominio/feature/registration/dto/RegisterGuestRequest.java` — DTO JSON do cadastro de convidado.
- `backend/src/main/java/br/com/condominio/feature/registration/RegisterGuestController.java` — `POST /api/auth/register-guest`.
- `backend/src/main/java/br/com/condominio/shared/security/CaptchaVerifier.java` — interface de verificação de captcha.
- `backend/src/main/java/br/com/condominio/shared/security/NoopCaptchaVerifier.java` — impl default (no-op / sempre válido) ativa quando captcha desligado.
- `backend/src/main/resources/db/migration/V29__role_guest_general_areas.sql` — role + permissions + grants.
- Testes: `RegisterGuestControllerWebTest.java`, e novos testes em `RegistrationServiceTest.java`, e testes de contrato `*GatingWebTest` por controller.

**Backend — modificar:**
- `backend/src/main/java/br/com/condominio/feature/role/RoleName.java:3-10` — adicionar `GUEST`.
- `backend/src/main/java/br/com/condominio/feature/role/PermissionCode.java:3-19` — adicionar `GENERAL_AREAS_VIEW`, `CONTENT_CREATE`.
- `backend/src/main/java/br/com/condominio/feature/registration/RegistrationService.java` — método `registerGuest`.
- `backend/src/main/java/br/com/condominio/shared/security/SecurityConfig.java:46-53` — rota pública `register-guest`.
- `backend/src/main/java/br/com/condominio/shared/security/RateLimitFilter.java` — bucket para `register-guest`.
- `backend/src/main/java/br/com/condominio/shared/security/RateLimitProperties.java` — propriedade `registerGuestPerMinPerIp`.
- `AnnouncementController.java:32,42`, `FaqController.java:28`, `InfoSectionController.java:30` — GET → `GENERAL_AREAS_VIEW`.
- `RecommendationController.java:52`, `ClassifiedController.java:52` — POST create → `CONTENT_CREATE`.

**Frontend — criar:**
- `frontend/src/features/auth/pages/RegisterChoicePage.tsx` — tela de escolha.
- `frontend/src/features/auth/pages/RegisterGuestPage.tsx` — formulário do convidado.

**Frontend — modificar:**
- `frontend/src/router.tsx` — rotas `/register` e `/register-guest`.
- `frontend/src/features/consent/api/consentApi.ts` — função `registerGuest`.
- `frontend/src/App.tsx` (NAV) e `frontend/src/components/layout/Sidebar.tsx` (ITEMS) — `requires: 'GENERAL_AREAS_VIEW'` em Avisos/Informações/FAQ.
- `frontend/src/features/auth/pages/LoginPage.tsx` — link para `/register`.

---

## PR 1 — Migration + enums (base)

### Task 1: Migration V29, RoleName.GUEST, PermissionCode (GENERAL_AREAS_VIEW, CONTENT_CREATE)

**Files:**
- Create: `backend/src/main/resources/db/migration/V29__role_guest_general_areas.sql`
- Modify: `backend/src/main/java/br/com/condominio/feature/role/RoleName.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/role/PermissionCode.java`

Contexto verificado:
- Maior `role.id` atual = 6 (`MURAL_EDITOR`, `V25`). Próximo livre = **7**.
- Maior `permission.id` atual = 17 (`RESIDENT_MANAGE`, `V28`). Próximos livres = **18, 19**.
- Roles existentes que devem manter acesso geral: `MANAGER, COUNCIL, STAFF, RESIDENT, DOORMAN, MURAL_EDITOR`.
- `role`/`role_permission` têm hard-delete permitido (CLAUDE.md) — sem `@SQLDelete`.

- [ ] **Step 1: Escrever a migration V29**

Create `backend/src/main/resources/db/migration/V29__role_guest_general_areas.sql`:

```sql
-- flyway:transactional=true

-- Role nova "Convidado": sem unidade, ACTIVE direto, não atribuível pela tela de acessos.
INSERT INTO role (id, name, label, max_holders, assignable)
VALUES (7, 'GUEST', 'Convidado', NULL, false);

-- Permission para ver áreas gerais (Avisos/Informações/FAQ).
-- Permission para criar conteúdo (indicações/classificados).
INSERT INTO permission (id, code, label) VALUES
    (18, 'GENERAL_AREAS_VIEW', 'Ver avisos, informações e FAQ'),
    (19, 'CONTENT_CREATE',     'Criar indicações e classificados');

-- Concede AMBAS a todas as roles EXCETO GUEST (aditivo: ninguém perde acesso).
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE p.code IN ('GENERAL_AREAS_VIEW', 'CONTENT_CREATE')
  AND r.name <> 'GUEST';
```

- [ ] **Step 2: Adicionar GUEST ao enum RoleName**

Modify `backend/src/main/java/br/com/condominio/feature/role/RoleName.java`:

```java
package br.com.condominio.feature.role;

public enum RoleName {
  MANAGER,
  COUNCIL,
  STAFF,
  RESIDENT,
  DOORMAN,
  MURAL_EDITOR,
  GUEST
}
```

- [ ] **Step 3: Adicionar as permissions ao enum PermissionCode**

Modify `backend/src/main/java/br/com/condominio/feature/role/PermissionCode.java`:

```java
package br.com.condominio.feature.role;

public enum PermissionCode {
  USER_VIEW,
  USER_MANAGE,
  REGISTRATION_VIEW,
  REGISTRATION_APPROVE,
  RESIDENCE_PROOF_VIEW,
  LINK_MANAGE,
  FAQ_MANAGE,
  INFO_MANAGE,
  TAG_MANAGE,
  RECOMMENDATION_MODERATE,
  CLASSIFIED_MODERATE,
  ROLE_ASSIGN,
  PERMISSION_GRANT,
  AUDIT_VIEW,
  RESIDENT_MANAGE,
  GENERAL_AREAS_VIEW,
  CONTENT_CREATE
}
```

- [ ] **Step 4: Compilar e rodar a migration contra um banco limpo**

Run: `cd backend && ./mvnw -q -DskipTests compile && ./mvnw -q -Dtest='*FlywayMigration*,*Repository*' test`
Expected: PASS (compila; se houver teste de boot/Flyway, a V29 aplica sem erro). Se não houver teste de Flyway, rode ao menos `./mvnw -q -DskipTests compile` (PASS) e valide a sintaxe SQL com a subida local descrita na memória `local-dev-stack`.

> Nota dev (memória `dev-boot-citext-validate`): o boot dev pode falhar na validação Hibernate de colunas citext; isso é pré-existente e não relacionado a esta migration. Use `ddl-auto=none` se precisar subir local.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V29__role_guest_general_areas.sql \
        backend/src/main/java/br/com/condominio/feature/role/RoleName.java \
        backend/src/main/java/br/com/condominio/feature/role/PermissionCode.java
git commit -m "feat(guest): role GUEST e permissions GENERAL_AREAS_VIEW/CONTENT_CREATE (V29)"
```

---

## PR 2 — Backend: cadastro do convidado

### Task 2: DTO RegisterGuestRequest

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/registration/dto/RegisterGuestRequest.java`

Espelha `RegisterMasterRequest` (`dto/RegisterMasterRequest.java`) **sem** `unitCode`, **com** `captchaToken` opcional.

- [ ] **Step 1: Criar o DTO**

Create `backend/src/main/java/br/com/condominio/feature/registration/dto/RegisterGuestRequest.java`:

```java
package br.com.condominio.feature.registration.dto;

import br.com.condominio.shared.validation.StrongPassword;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RegisterGuestRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Size(max = 60) String greetingName,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
    @Size(max = 20) String gender,
    LocalDate birthDate,
    @NotBlank @StrongPassword String password,
    @NotBlank String consentVersion,
    boolean whatsappOptIn,
    String captchaToken) {}
```

- [ ] **Step 2: Compilar**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/registration/dto/RegisterGuestRequest.java
git commit -m "feat(guest): RegisterGuestRequest (DTO JSON sem unidade)"
```

### Task 3: CaptchaVerifier (interface + no-op default)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/shared/security/CaptchaVerifier.java`
- Create: `backend/src/main/java/br/com/condominio/shared/security/NoopCaptchaVerifier.java`

Abstração plugável: o `registerGuest` chama `captcha.verify(token, ip)`; quando o provider real não está configurado, o `NoopCaptchaVerifier` (ativo por padrão) aprova tudo. O provider real (PR 5) substitui via `@ConditionalOnProperty`.

- [ ] **Step 1: Escrever o teste do no-op**

Create `backend/src/test/java/br/com/condominio/shared/security/NoopCaptchaVerifierTest.java`:

```java
package br.com.condominio.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoopCaptchaVerifierTest {

  private final CaptchaVerifier verifier = new NoopCaptchaVerifier();

  @Test
  void verify_anyToken_returnsTrue() {
    assertThat(verifier.verify(null, "127.0.0.1")).isTrue();
    assertThat(verifier.verify("anything", "127.0.0.1")).isTrue();
  }
}
```

- [ ] **Step 2: Rodar o teste e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=NoopCaptchaVerifierTest test`
Expected: FAIL — não compila (`CaptchaVerifier`/`NoopCaptchaVerifier` não existem).

- [ ] **Step 3: Criar a interface**

Create `backend/src/main/java/br/com/condominio/shared/security/CaptchaVerifier.java`:

```java
package br.com.condominio.shared.security;

/** Verifica um token de captcha emitido pelo provider (ex.: Turnstile/hCaptcha). */
public interface CaptchaVerifier {

  /**
   * @param token token enviado pelo cliente (pode ser null/blank quando captcha está desligado).
   * @param clientIp IP do cliente, usado por alguns providers na verificação.
   * @return true se o token é válido (ou se a verificação está desligada).
   */
  boolean verify(String token, String clientIp);
}
```

- [ ] **Step 4: Criar o no-op default**

Create `backend/src/main/java/br/com/condominio/shared/security/NoopCaptchaVerifier.java`:

```java
package br.com.condominio.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Verificador de captcha padrão: aprova qualquer token. Ativo quando {@code
 * app.security.captcha.enabled} é falso/ausente (default). O provider real (Turnstile/hCaptcha)
 * substitui esta bean via {@code @ConditionalOnProperty(havingValue = "true")} num PR posterior.
 */
@Component
@ConditionalOnProperty(name = "app.security.captcha.enabled", havingValue = "false", matchIfMissing = true)
public class NoopCaptchaVerifier implements CaptchaVerifier {

  @Override
  public boolean verify(String token, String clientIp) {
    return true;
  }
}
```

- [ ] **Step 5: Rodar o teste e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=NoopCaptchaVerifierTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/com/condominio/shared/security/CaptchaVerifier.java \
        backend/src/main/java/br/com/condominio/shared/security/NoopCaptchaVerifier.java \
        backend/src/test/java/br/com/condominio/shared/security/NoopCaptchaVerifierTest.java
git commit -m "feat(guest): CaptchaVerifier plugavel com no-op default"
```

### Task 4: RegistrationService.registerGuest

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/registration/RegistrationService.java`
- Test: `backend/src/test/java/br/com/condominio/feature/registration/RegistrationServiceTest.java`

`registerGuest` reusa os helpers privados existentes (`newInstance`, `setField`, `setEmail`) e o `encoder`/`consentRepo`/`roleRepo`. Cria `User` com `unitId=null`, `isUnitMaster=false`, `status=ACTIVE`, `mustChangePassword=false`, proof nulo; `UserEmail` primário; `UserRole` com a role `GUEST`. Adiciona `CaptchaVerifier` como dependência do service.

- [ ] **Step 1: Escrever o teste do happy-path do registerGuest**

Add to `backend/src/test/java/br/com/condominio/feature/registration/RegistrationServiceTest.java` (novo campo + injeção no `setUp` + teste). Primeiro, adicione o mock e passe-o ao construtor.

No bloco de campos (junto aos demais `private ... ;`), adicione:

```java
  private br.com.condominio.shared.security.CaptchaVerifier captcha;
```

No `setUp()`, antes da linha `service = new RegistrationService(`, adicione:

```java
    captcha = mock(br.com.condominio.shared.security.CaptchaVerifier.class);
    when(captcha.verify(any(), any())).thenReturn(true);
```

E altere a construção do service para incluir `captcha` como último argumento:

```java
    service =
        new RegistrationService(
            unitRepo,
            userRepo,
            emailRepo,
            roleRepo,
            userRoleRepo,
            consentRepo,
            storage,
            magicBytes,
            encoder,
            props,
            permissionRepo,
            grantRepo,
            captcha);
```

Adicione o método de teste:

```java
  @Test
  void registersGuestSuccessfully_activeNoUnitNoProof() {
    when(emailRepo.findActiveByEmailIgnoreCase("guest@x.com")).thenReturn(java.util.Optional.empty());
    when(consentRepo.findByVersion("1.0.0")).thenReturn(java.util.Optional.of(newConsent("1.0.0")));
    when(encoder.encode(any())).thenReturn("hashed");
    Role guestRole = newInstance(Role.class);
    setField(guestRole, "id", (short) 7);
    when(roleRepo.findByName(RoleName.GUEST)).thenReturn(java.util.Optional.of(guestRole));
    when(userRepo.save(any()))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              setField(u, "id", UUID.randomUUID());
              return u;
            });

    var req =
        new br.com.condominio.feature.registration.dto.RegisterGuestRequest(
            "Convidado Teste",
            "Convidado",
            "guest@x.com",
            "+5511988887777",
            "NOT_INFORMED",
            LocalDate.of(1995, 5, 5),
            "Senha@1234",
            "1.0.0",
            true,
            "captcha-token");

    var resp = service.registerGuest(req, "127.0.0.1");

    assertThat(resp.status()).isEqualTo("ACTIVE");
    verify(emailRepo).save(any());
    verify(userRoleRepo).save(any());
    // Nunca toca em storage/unit (sem comprovante, sem unidade).
    verify(storage, never()).upload(any(), any(), anyLong(), any());
    verify(unitRepo, never()).findByCode(any());
  }

  @Test
  void registerGuest_rejectsWhenEmailTaken() {
    when(emailRepo.findActiveByEmailIgnoreCase("guest@x.com"))
        .thenReturn(java.util.Optional.of(newInstance(UserEmail.class)));
    var req =
        new br.com.condominio.feature.registration.dto.RegisterGuestRequest(
            "Convidado", "Convidado", "guest@x.com", "+5511988887777", null, null,
            "Senha@1234", "1.0.0", false, null);
    assertThatThrownBy(() -> service.registerGuest(req, "127.0.0.1"))
        .isInstanceOf(RegistrationException.class)
        .hasMessageContaining("e-mail");
  }

  @Test
  void registerGuest_rejectsWhenCaptchaInvalid() {
    when(captcha.verify(any(), any())).thenReturn(false);
    var req =
        new br.com.condominio.feature.registration.dto.RegisterGuestRequest(
            "Convidado", "Convidado", "guest@x.com", "+5511988887777", null, null,
            "Senha@1234", "1.0.0", false, "bad-token");
    assertThatThrownBy(() -> service.registerGuest(req, "127.0.0.1"))
        .isInstanceOf(RegistrationException.class)
        .hasMessageContaining("captcha");
  }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=RegistrationServiceTest test`
Expected: FAIL — não compila (`registerGuest` e o param `captcha` do construtor não existem).

- [ ] **Step 3: Adicionar a dependência CaptchaVerifier ao service**

Modify `backend/src/main/java/br/com/condominio/feature/registration/RegistrationService.java`. Adicione o import e o campo final (logo após `private final UserPermissionGrantRepository grantRepo;`):

```java
  private final br.com.condominio.shared.security.CaptchaVerifier captcha;
```

(Por usar `@RequiredArgsConstructor`, o Lombok inclui esse campo no construtor gerado — daí o teste passar `captcha` como último argumento.)

- [ ] **Step 4: Implementar registerGuest**

Add no `RegistrationService` (logo após o método `registerMaster`, antes de `setUserFields`):

```java
  @Transactional
  public RegistrationStatusResponse registerGuest(RegisterGuestRequest req, String clientIp) {

    if (!captcha.verify(req.captchaToken(), clientIp)) {
      throw new RegistrationException("CAPTCHA_INVALID", "Falha na verificação do captcha.");
    }

    if (emailRepo.findActiveByEmailIgnoreCase(req.email()).isPresent()) {
      throw new RegistrationException("EMAIL_TAKEN", "Este e-mail já está cadastrado.");
    }

    ConsentDocument consent =
        consentRepo
            .findByVersion(req.consentVersion())
            .orElseThrow(
                () ->
                    new RegistrationException(
                        "CONSENT_VERSION_INVALID", "Versão do termo de privacidade inválida."));

    Role guestRole =
        roleRepo
            .findByName(RoleName.GUEST)
            .orElseThrow(() -> new IllegalStateException("GUEST role missing"));

    User user = newInstance(User.class);
    setGuestFields(user, req, consent, clientIp);
    user = userRepo.save(user);

    UserEmail userEmail = newInstance(UserEmail.class);
    setEmail(userEmail, user.getId(), req.email());
    emailRepo.save(userEmail);

    UserRole userRole =
        new UserRole(new UserRoleId(user.getId(), guestRole.getId()), Instant.now(), null);
    userRoleRepo.save(userRole);

    log.info("Guest registered: userId={} ip={}", user.getId(), clientIp);

    return new RegistrationStatusResponse(user.getId(), user.getStatus().name());
  }

  private void setGuestFields(
      User user, RegisterGuestRequest req, ConsentDocument consent, String clientIp) {
    try {
      setField(user, "unitId", null);
      setField(user, "isUnitMaster", false);
      setField(user, "fullName", req.fullName());
      setField(user, "greetingName", req.greetingName());
      setField(user, "phone", req.phone());
      if (req.gender() != null && !req.gender().isBlank()) {
        setField(user, "gender", Gender.valueOf(req.gender()));
      }
      setField(user, "birthDate", req.birthDate());
      setField(user, "passwordHash", encoder.encode(req.password()));
      setField(user, "passwordPepperVersion", (short) 1);
      setField(user, "mustChangePassword", false);
      setField(user, "status", UserStatus.ACTIVE);
      setField(user, "consentDocumentVersion", consent.getVersion());
      setField(user, "consentAcceptedAt", Instant.now());
      setField(user, "consentAcceptedIp", clientIp);
      setField(user, "whatsappOptIn", req.whatsappOptIn());
      if (req.whatsappOptIn()) setField(user, "whatsappOptInAt", Instant.now());
    } catch (Exception e) {
      throw new IllegalStateException("Failed setting guest User fields", e);
    }
  }
```

Adicione o import do DTO no topo (junto aos demais imports `dto.*`):

```java
import br.com.condominio.feature.registration.dto.RegisterGuestRequest;
```

> Nota: `Gender.valueOf("NOT_INFORMED")` — confirme que o enum `Gender` tem `NOT_INFORMED` (o front envia esse valor; o master só envia `MALE/FEMALE/OTHER`). Se `Gender` **não** tiver `NOT_INFORMED`, o controller/serviço deve tratar `"NOT_INFORMED"` como "não setar gênero": troque a condição para `if (req.gender() != null && !req.gender().isBlank() && !"NOT_INFORMED".equals(req.gender()))`. **Verifique `Gender.java` antes deste step e use a forma segura se necessário.**

- [ ] **Step 5: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=RegistrationServiceTest test`
Expected: PASS (todos os testes, incluindo os 3 novos do guest e os pré-existentes do master).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/registration/RegistrationService.java \
        backend/src/test/java/br/com/condominio/feature/registration/RegistrationServiceTest.java
git commit -m "feat(guest): RegistrationService.registerGuest (ACTIVE, sem unidade/comprovante)"
```

### Task 5: RegisterGuestController + rota pública

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/registration/RegisterGuestController.java`
- Modify: `backend/src/main/java/br/com/condominio/shared/security/SecurityConfig.java`
- Test: `backend/src/test/java/br/com/condominio/feature/registration/RegisterGuestControllerWebTest.java`

- [ ] **Step 1: Escrever o teste de contrato do controller**

Create `backend/src/test/java/br/com/condominio/feature/registration/RegisterGuestControllerWebTest.java`:

```java
package br.com.condominio.feature.registration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link RegisterGuestController}: cadastro de convidado é público, JSON, retorna
 * 202 ACTIVE e valida campos obrigatórios.
 */
@WebMvcTest(controllers = RegisterGuestController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class RegisterGuestControllerWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private RegistrationService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private static final String VALID_JSON =
      """
      {"fullName":"Convidado Teste","greetingName":"Convidado","email":"%s",
       "phone":"11999998888","gender":"NOT_INFORMED","password":"Senha@1234",
       "consentVersion":"v3","whatsappOptIn":true,"captchaToken":"tok"}
      """;

  @Test
  void registerGuest_returns202_active() throws Exception {
    when(service.registerGuest(any(), any()))
        .thenReturn(new RegistrationStatusResponse(UUID.randomUUID(), "ACTIVE"));

    mvc.perform(
            post("/api/auth/register-guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_JSON.formatted("guest@test.com")))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(service).registerGuest(any(), any());
  }

  @Test
  void registerGuest_invalidEmail_returns400() throws Exception {
    mvc.perform(
            post("/api/auth/register-guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_JSON.formatted("naoEhEmail")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verify(service, never()).registerGuest(any(), any());
  }

  @Test
  void registerGuest_weakPassword_returns400() throws Exception {
    String weak =
        """
        {"fullName":"Convidado","greetingName":"Convidado","email":"guest@test.com",
         "phone":"11999998888","password":"senha12345","consentVersion":"v3",
         "whatsappOptIn":true}
        """;
    mvc.perform(
            post("/api/auth/register-guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(weak))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verify(service, never()).registerGuest(any(), any());
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=RegisterGuestControllerWebTest test`
Expected: FAIL — `RegisterGuestController` não existe (não compila).

- [ ] **Step 3: Criar o controller**

Create `backend/src/main/java/br/com/condominio/feature/registration/RegisterGuestController.java`:

```java
package br.com.condominio.feature.registration;

import br.com.condominio.feature.registration.dto.RegisterGuestRequest;
import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RegisterGuestController {

  private final RegistrationService service;

  @PostMapping(value = "/register-guest", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RegistrationStatusResponse> registerGuest(
      @Valid @RequestBody RegisterGuestRequest req, HttpServletRequest request) {
    String ip = resolveClientIp(request);
    RegistrationStatusResponse resp = service.registerGuest(req, ip);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
  }

  private String resolveClientIp(HttpServletRequest request) {
    String fwd = request.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
    return request.getRemoteAddr();
  }
}
```

- [ ] **Step 4: Liberar a rota no SecurityConfig**

Modify `backend/src/main/java/br/com/condominio/shared/security/SecurityConfig.java`, no bloco `requestMatchers(HttpMethod.POST, ...)` (linhas 46-53), adicione `"/api/auth/register-guest"` à lista:

```java
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/register-master",
                        "/api/auth/register-guest",
                        "/api/auth/password/request-reset",
                        "/api/auth/password/consume-reset")
                    .permitAll()
```

- [ ] **Step 5: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=RegisterGuestControllerWebTest test`
Expected: PASS (3 testes).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/registration/RegisterGuestController.java \
        backend/src/main/java/br/com/condominio/shared/security/SecurityConfig.java \
        backend/src/test/java/br/com/condominio/feature/registration/RegisterGuestControllerWebTest.java
git commit -m "feat(guest): POST /api/auth/register-guest publico (JSON)"
```

### Task 6: Rate-limit no register-guest

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/shared/security/RateLimitFilter.java`
- Modify: `backend/src/main/java/br/com/condominio/shared/security/RateLimitProperties.java`
- Test: `backend/src/test/java/br/com/condominio/shared/security/RateLimitFilterTest.java`

O `RateLimitFilter` já trata `login` e `refresh` por IP. Adicionamos um terceiro path: `/api/auth/register-guest`.

- [ ] **Step 1: Ler a estrutura de RateLimitProperties**

Run: `cat backend/src/main/java/br/com/condominio/shared/security/RateLimitProperties.java`
Expected: ver os campos existentes `loginPerMinPerIp`, `refreshPerMinPerIp` (com prefixo `@ConfigurationProperties`). Use-os como modelo.

- [ ] **Step 2: Adicionar a propriedade registerGuestPerMinPerIp**

Modify `backend/src/main/java/br/com/condominio/shared/security/RateLimitProperties.java`: adicione um campo `registerGuestPerMinPerIp` com getter/setter (ou, se for record/`@Data`, no mesmo estilo dos demais), default sugerido `5`:

```java
  private int registerGuestPerMinPerIp = 5;
```

(Espelhe exatamente o estilo dos campos `loginPerMinPerIp`/`refreshPerMinPerIp` — getter/setter ou Lombok conforme o arquivo. Não troque o estilo.)

- [ ] **Step 3: Escrever/atualizar o teste do filtro**

Add to (ou crie) `backend/src/test/java/br/com/condominio/shared/security/RateLimitFilterTest.java` um teste que dispara mais que o limite de POSTs em `/api/auth/register-guest` do mesmo IP e espera 429 no excedente. Modele pelo teste existente de login (se houver). Esqueleto:

```java
  @Test
  void registerGuest_exceedingLimit_returns429() throws Exception {
    RateLimitProperties props = new RateLimitProperties();
    props.setRegisterGuestPerMinPerIp(2);
    RateLimitFilter filter = new RateLimitFilter(props, new com.fasterxml.jackson.databind.ObjectMapper());

    for (int i = 0; i < 2; i++) {
      MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/register-guest");
      req.setRemoteAddr("9.9.9.9");
      MockHttpServletResponse res = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();
      filter.doFilter(req, res, chain);
      org.assertj.core.api.Assertions.assertThat(res.getStatus()).isNotEqualTo(429);
    }

    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/register-guest");
    req.setRemoteAddr("9.9.9.9");
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(req, res, chain);
    org.assertj.core.api.Assertions.assertThat(res.getStatus()).isEqualTo(429);
  }
```

(Imports: `org.springframework.mock.web.MockHttpServletRequest`, `MockHttpServletResponse`, `MockFilterChain`. Use `getServletPath()` — `MockHttpServletRequest` precisa do servletPath setado: chame `req.setServletPath("/api/auth/register-guest")` se o filtro usa `getServletPath()`. **O filtro usa `request.getServletPath()`**, então **set `setServletPath` em vez de só a URI**.)

Ajuste do esqueleto: setar servletPath:

```java
      req.setServletPath("/api/auth/register-guest");
```

- [ ] **Step 4: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=RateLimitFilterTest test`
Expected: FAIL — `setRegisterGuestPerMinPerIp` não existe e/ou o filtro ainda não trata o path (status nunca 429).

- [ ] **Step 5: Tratar o path no filtro**

Modify `backend/src/main/java/br/com/condominio/shared/security/RateLimitFilter.java`. Adicione um terceiro mapa de buckets (junto a `loginBuckets`/`refreshBuckets`):

```java
  private final Map<String, Bucket> registerGuestBuckets = new ConcurrentHashMap<>();
```

E adicione o ramo no `doFilterInternal`, após o `else if` do `/api/auth/refresh`:

```java
    } else if ("/api/auth/register-guest".equals(path)) {
      bucket =
          registerGuestBuckets.computeIfAbsent(
              clientIp(request),
              k -> newBucket(props.getRegisterGuestPerMinPerIp(), Duration.ofMinutes(1)));
    }
```

- [ ] **Step 6: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=RateLimitFilterTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/condominio/shared/security/RateLimitFilter.java \
        backend/src/main/java/br/com/condominio/shared/security/RateLimitProperties.java \
        backend/src/test/java/br/com/condominio/shared/security/RateLimitFilterTest.java
git commit -m "feat(guest): rate-limit por IP no register-guest"
```

---

## PR 3 — Backend: restrição das áreas gerais

> **Pré-requisito de deploy:** a migration V29 (PR 1) DEVE já estar aplicada em todos os ambientes antes deste PR ir a Prod/HML. Ver "Ordem de deploy ↔ migration".

### Task 7: Gate de Avisos por GENERAL_AREAS_VIEW

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/announcement/AnnouncementController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/announcement/AnnouncementGatingWebTest.java`

- [ ] **Step 1: Escrever o teste de contrato (guest 403, morador 200)**

Create `backend/src/test/java/br/com/condominio/feature/announcement/AnnouncementGatingWebTest.java`:

```java
package br.com.condominio.feature.announcement;

import static br.com.condominio.support.MockAuth.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AnnouncementController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
@TestPropertySource(properties = "app.feature.announcements.enabled=true")
class AnnouncementGatingWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private AnnouncementService service;
  @MockBean private JwtService jwtService;

  @Test
  void list_withGeneralAreasView_returns200() throws Exception {
    when(service.list(any())).thenReturn(new PageImpl<>(java.util.List.of()));
    mvc.perform(get("/api/announcements").with(user(UUID.randomUUID(), "GENERAL_AREAS_VIEW")))
        .andExpect(status().isOk());
  }

  @Test
  void list_guestWithoutGeneralAreasView_returns403() throws Exception {
    mvc.perform(get("/api/announcements").with(user(UUID.randomUUID())))
        .andExpect(status().isForbidden());
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=AnnouncementGatingWebTest test`
Expected: FAIL no segundo teste — hoje o GET usa `isAuthenticated()`, então o guest sem authority recebe **200** (esperado 403).

- [ ] **Step 3: Trocar o gate nos GETs**

Modify `backend/src/main/java/br/com/condominio/feature/announcement/AnnouncementController.java`: nos dois métodos GET (`list` linha 32 e `get` linha 42), troque `@PreAuthorize("isAuthenticated()")` por:

```java
  @PreAuthorize("hasAuthority('GENERAL_AREAS_VIEW')")
```

(Os métodos de escrita já usam `ANNOUNCEMENT_MANAGE` — não mexer.)

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=AnnouncementGatingWebTest test`
Expected: PASS (2 testes).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/announcement/AnnouncementController.java \
        backend/src/test/java/br/com/condominio/feature/announcement/AnnouncementGatingWebTest.java
git commit -m "feat(guest): Avisos exigem GENERAL_AREAS_VIEW (guest 403)"
```

### Task 8: Gate de FAQ e Informações por GENERAL_AREAS_VIEW

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/faq/FaqController.java`
- Modify: `backend/src/main/java/br/com/condominio/feature/info/InfoSectionController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/faq/FaqGatingWebTest.java`
- Test: `backend/src/test/java/br/com/condominio/feature/info/InfoSectionGatingWebTest.java`

- [ ] **Step 1: Escrever os testes de gating de FAQ e Info**

Create `backend/src/test/java/br/com/condominio/feature/faq/FaqGatingWebTest.java`:

```java
package br.com.condominio.feature.faq;

import static br.com.condominio.support.MockAuth.user;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FaqController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
@TestPropertySource(properties = "app.feature.faq.enabled=true")
class FaqGatingWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private FaqService service;
  @MockBean private JwtService jwtService;

  @Test
  void listPublished_withGeneralAreasView_returns200() throws Exception {
    when(service.listPublished()).thenReturn(List.of());
    mvc.perform(get("/api/faq").with(user(UUID.randomUUID(), "GENERAL_AREAS_VIEW")))
        .andExpect(status().isOk());
  }

  @Test
  void listPublished_guest_returns403() throws Exception {
    mvc.perform(get("/api/faq").with(user(UUID.randomUUID())))
        .andExpect(status().isForbidden());
  }
}
```

Create `backend/src/test/java/br/com/condominio/feature/info/InfoSectionGatingWebTest.java`:

```java
package br.com.condominio.feature.info;

import static br.com.condominio.support.MockAuth.user;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InfoSectionController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
@TestPropertySource(properties = "app.feature.generalinfo.enabled=true")
class InfoSectionGatingWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private InfoSectionService service;
  @MockBean private JwtService jwtService;

  @Test
  void list_withGeneralAreasView_returns200() throws Exception {
    when(service.list()).thenReturn(List.of());
    mvc.perform(get("/api/info-sections").with(user(UUID.randomUUID(), "GENERAL_AREAS_VIEW")))
        .andExpect(status().isOk());
  }

  @Test
  void list_guest_returns403() throws Exception {
    mvc.perform(get("/api/info-sections").with(user(UUID.randomUUID())))
        .andExpect(status().isForbidden());
  }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest='FaqGatingWebTest,InfoSectionGatingWebTest' test`
Expected: FAIL nos testes `_guest_returns403` (hoje retornam 200).

- [ ] **Step 3: Trocar os gates dos GETs de leitura**

Modify `FaqController.java`: no método `listPublished` (linha 28), troque `@PreAuthorize("isAuthenticated()")` por `@PreAuthorize("hasAuthority('GENERAL_AREAS_VIEW')")`. (Os demais já usam `FAQ_MANAGE` — não mexer; em especial `/all` continua `FAQ_MANAGE`.)

Modify `InfoSectionController.java`: no método `list` (linha 30), troque `@PreAuthorize("isAuthenticated()")` por `@PreAuthorize("hasAuthority('GENERAL_AREAS_VIEW')")`.

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest='FaqGatingWebTest,InfoSectionGatingWebTest' test`
Expected: PASS (4 testes).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/faq/FaqController.java \
        backend/src/main/java/br/com/condominio/feature/info/InfoSectionController.java \
        backend/src/test/java/br/com/condominio/feature/faq/FaqGatingWebTest.java \
        backend/src/test/java/br/com/condominio/feature/info/InfoSectionGatingWebTest.java
git commit -m "feat(guest): FAQ e Informacoes exigem GENERAL_AREAS_VIEW (guest 403)"
```

### Task 9: Gate de criação de Indicação por CONTENT_CREATE

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/recommendation/RecommendationController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationCreateGatingWebTest.java`

Leitura (GET list/get) **continua** `isAuthenticated()` (guest pode ver). Só o **POST create** passa a exigir `CONTENT_CREATE`.

- [ ] **Step 1: Escrever o teste de gating de criação**

Create `backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationCreateGatingWebTest.java`:

```java
package br.com.condominio.feature.recommendation;

import static br.com.condominio.support.MockAuth.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RecommendationController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
@TestPropertySource(properties = "app.feature.recommendations.enabled=true")
class RecommendationCreateGatingWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private RecommendationService service;
  @MockBean private JwtService jwtService;

  private static final String BODY =
      """
      {"title":"Eletricista","description":"Bom profissional","tags":[]}
      """;

  @Test
  void list_guestCanRead_returns200() throws Exception {
    when(service.list(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any()))
        .thenReturn(new PageImpl<>(java.util.List.of()));
    mvc.perform(get("/api/recommendations").with(user(UUID.randomUUID())))
        .andExpect(status().isOk());
  }

  @Test
  void create_guestWithoutContentCreate_returns403() throws Exception {
    mvc.perform(
            post("/api/recommendations")
                .with(user(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  void create_withContentCreate_returns201() throws Exception {
    when(service.create(any(), any()))
        .thenReturn(null); // corpo não importa para o status de autorização
    mvc.perform(
            post("/api/recommendations")
                .with(user(UUID.randomUUID(), "CONTENT_CREATE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isCreated());
  }
}
```

> **Nota:** ajuste o JSON `BODY` aos campos reais de `CreateRecommendationRequest` (verifique `recommendation/dto/CreateRecommendationRequest.java`). Se houver `@NotBlank`/`@NotNull`, preencha-os para que o teste de `create_withContentCreate_returns201` passe pela validação (senão dá 400 em vez de 201). O foco é o **403 vs 201**; mantenha o body válido.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=RecommendationCreateGatingWebTest test`
Expected: FAIL em `create_guestWithoutContentCreate_returns403` (hoje o create é `isAuthenticated()` → 201/400, não 403).

- [ ] **Step 3: Trocar o gate do POST create**

Modify `backend/src/main/java/br/com/condominio/feature/recommendation/RecommendationController.java`: no método `create` (linha 52), troque `@PreAuthorize("isAuthenticated()")` por:

```java
  @PreAuthorize("hasAuthority('CONTENT_CREATE')")
```

(GET `list`/`get`, `update`, `delete`, `photos` **permanecem** `isAuthenticated()` — guest pode ler; editar/excluir já é restrito ao dono via service. Não alterar.)

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=RecommendationCreateGatingWebTest test`
Expected: PASS (3 testes).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/recommendation/RecommendationController.java \
        backend/src/test/java/br/com/condominio/feature/recommendation/RecommendationCreateGatingWebTest.java
git commit -m "feat(guest): criar indicacao exige CONTENT_CREATE (leitura aberta)"
```

### Task 10: Gate de criação de Classificado por CONTENT_CREATE

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/classified/ClassifiedController.java`
- Test: `backend/src/test/java/br/com/condominio/feature/classified/ClassifiedCreateGatingWebTest.java`

- [ ] **Step 1: Escrever o teste de gating de criação**

Create `backend/src/test/java/br/com/condominio/feature/classified/ClassifiedCreateGatingWebTest.java`:

```java
package br.com.condominio.feature.classified;

import static br.com.condominio.support.MockAuth.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ClassifiedController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
@TestPropertySource(properties = "app.feature.classifieds.enabled=true")
class ClassifiedCreateGatingWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private ClassifiedService service;
  @MockBean private JwtService jwtService;

  private static final String BODY =
      """
      {"title":"Bicicleta","description":"Seminova","priceCents":50000}
      """;

  @Test
  void list_guestCanRead_returns200() throws Exception {
    when(service.list(any(), any())).thenReturn(new PageImpl<>(java.util.List.of()));
    mvc.perform(get("/api/classifieds").with(user(UUID.randomUUID())))
        .andExpect(status().isOk());
  }

  @Test
  void create_guestWithoutContentCreate_returns403() throws Exception {
    mvc.perform(
            post("/api/classifieds")
                .with(user(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  void create_withContentCreate_returns201() throws Exception {
    when(service.create(any(), any())).thenReturn(null);
    mvc.perform(
            post("/api/classifieds")
                .with(user(UUID.randomUUID(), "CONTENT_CREATE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isCreated());
  }
}
```

> **Nota:** ajuste `BODY` a `CreateClassifiedRequest` real (verifique `classified/dto/CreateClassifiedRequest.java`) para o teste de 201 não cair em 400 de validação.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q -Dtest=ClassifiedCreateGatingWebTest test`
Expected: FAIL em `create_guestWithoutContentCreate_returns403`.

- [ ] **Step 3: Trocar o gate do POST create**

Modify `backend/src/main/java/br/com/condominio/feature/classified/ClassifiedController.java`: no método `create` (linha 52), troque `@PreAuthorize("isAuthenticated()")` por:

```java
  @PreAuthorize("hasAuthority('CONTENT_CREATE')")
```

(Demais métodos permanecem `isAuthenticated()`.)

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -q -Dtest=ClassifiedCreateGatingWebTest test`
Expected: PASS (3 testes).

- [ ] **Step 5: Rodar a suíte de testes web inteira (regressão)**

Run: `cd backend && ./mvnw -q test`
Expected: PASS — confirmar que nenhum teste pré-existente (ex.: testes de criação de indicação/classificado por morador comum sem `CONTENT_CREATE`) quebrou. **Se algum teste existente cria indicação/classificado com `MockAuth.user(id)` sem authority e espera 201, ele agora dá 403 — adicione `"CONTENT_CREATE"` ao `user(...)` desse teste** (morador real tem a permission via role). Liste e ajuste esses testes neste mesmo step.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/com/condominio/feature/classified/ClassifiedController.java \
        backend/src/test/java/br/com/condominio/feature/classified/ClassifiedCreateGatingWebTest.java
git commit -m "feat(guest): criar classificado exige CONTENT_CREATE (leitura aberta)"
```

---

## PR 4 — Frontend

### Task 11: API registerGuest

**Files:**
- Modify: `frontend/src/features/consent/api/consentApi.ts`
- Test: (sem teste unitário dedicado; coberto manualmente / via E2E fora de escopo)

- [ ] **Step 1: Adicionar a função registerGuest (JSON)**

Modify `frontend/src/features/consent/api/consentApi.ts`. Após `registerMaster`, adicione:

```typescript
export interface RegisterGuestPayload {
  fullName: string;
  greetingName: string;
  email: string;
  phone: string;
  gender?: string;
  birthDate?: string;
  password: string;
  consentVersion: string;
  whatsappOptIn: boolean;
  captchaToken?: string;
}

export async function registerGuest(payload: RegisterGuestPayload) {
  const r = await axios.post(`${baseUrl()}/auth/register-guest`, payload);
  return r.data;
}
```

(Use o mesmo `baseUrl()`/`axios` já importados no arquivo. Não usar `multipart/form-data` — é JSON.)

- [ ] **Step 2: Type-check do front**

Run: `cd frontend && npm run build`
Expected: PASS (sem erros TS). Se o projeto tiver `npm run typecheck`, use-o.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/consent/api/consentApi.ts
git commit -m "feat(guest): api registerGuest (JSON)"
```

### Task 12: RegisterGuestPage

**Files:**
- Create: `frontend/src/features/auth/pages/RegisterGuestPage.tsx`

Espelha `RegisterMasterPage.tsx` **sem** `UnitSelector`/`ProofUploader`/`unitCode`/`hasMaster`, usa `registerGuest` (JSON), e inclui o campo de captcha (placeholder até o provider real — Task 15). Sucesso → `/login`.

- [ ] **Step 1: Criar a página**

Create `frontend/src/features/auth/pages/RegisterGuestPage.tsx`:

```tsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ConsentBox } from '@/features/consent/ConsentBox';
import { registerGuest } from '@/features/consent/api/consentApi';
import { PasswordInput } from '@/components/ui/password-input';
import { PasswordChecklist } from '@/components/auth/PasswordChecklist';
import { isStrongPassword } from '@/features/auth/passwordPolicy';
import { CaptchaField } from '@/features/auth/CaptchaField';

export function RegisterGuestPage() {
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [greetingName, setGreetingName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [gender, setGender] = useState('NOT_INFORMED');
  const [birthDate, setBirthDate] = useState('');
  const [password, setPassword] = useState('');
  const [consentVersion, setConsentVersion] = useState<string | null>(null);
  const [whatsappOptIn, setWhatsappOptIn] = useState(true);
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const canSubmit =
    !!fullName &&
    !!greetingName &&
    !!email &&
    !!phone &&
    isStrongPassword(password) &&
    !!consentVersion &&
    !!captchaToken;

  const submit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    try {
      await registerGuest({
        fullName,
        greetingName,
        email,
        phone,
        gender,
        birthDate: birthDate || undefined,
        password,
        consentVersion: consentVersion!,
        whatsappOptIn,
        captchaToken: captchaToken ?? undefined,
      });
      toast.success('Cadastro concluído! Faça login para acessar.');
      navigate('/login', { replace: true });
    } catch (e) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Erro ao cadastrar. Tente novamente.';
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="min-h-dvh flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-2xl my-8">
        <CardHeader>
          <CardTitle>Cadastro de convidado</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <p className="text-sm text-muted-foreground">
            O cadastro de convidado dá acesso apenas às áreas de Indicações e Classificados,
            para leitura. Não requer comprovante nem aprovação.
          </p>
          <section className="space-y-3">
            <h3 className="font-semibold">Seus dados</h3>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor="fullName">Nome completo</Label>
                <Input id="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} />
              </div>
              <div>
                <Label htmlFor="greetingName">Como prefere ser chamado</Label>
                <Input
                  id="greetingName"
                  value={greetingName}
                  onChange={(e) => setGreetingName(e.target.value)}
                />
              </div>
              <div>
                <Label htmlFor="email">E-mail</Label>
                <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
              </div>
              <div>
                <Label htmlFor="phone">Telefone (WhatsApp)</Label>
                <Input
                  id="phone"
                  type="tel"
                  placeholder="+5511..."
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                />
              </div>
              <div>
                <Label>Data de nascimento</Label>
                <Input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} />
              </div>
              <div>
                <Label>Gênero (opcional)</Label>
                <select
                  className="w-full rounded-md border border-input bg-background px-3 py-2"
                  value={gender}
                  onChange={(e) => setGender(e.target.value)}
                >
                  <option value="NOT_INFORMED">Prefiro não informar</option>
                  <option value="MALE">Masculino</option>
                  <option value="FEMALE">Feminino</option>
                  <option value="OTHER">Outro</option>
                </select>
              </div>
              <div className="col-span-2">
                <Label htmlFor="password">Senha</Label>
                <PasswordInput
                  id="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <PasswordChecklist value={password} />
              </div>
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={whatsappOptIn}
                onChange={(e) => setWhatsappOptIn(e.target.checked)}
              />
              Aceito receber comunicações operacionais via WhatsApp neste número.
            </label>
          </section>
          <section>
            <h3 className="font-semibold mb-3">Termo de privacidade</h3>
            <ConsentBox
              accepted={!!consentVersion}
              onChange={(a, v) => setConsentVersion(a ? v : null)}
            />
          </section>
          <section>
            <CaptchaField onChange={setCaptchaToken} />
          </section>
          <Button onClick={submit} disabled={!canSubmit || submitting} className="w-full">
            {submitting ? 'Enviando...' : 'Criar cadastro de convidado'}
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
```

> Depende de `CaptchaField` (Task 15). Para fazer esta task compilar e testar isoladamente sem o provider real, a Task 15 entrega um `CaptchaField` no-op que emite um token fixo. **Faça a Task 15 antes desta** ou crie o `CaptchaField` no-op primeiro.

- [ ] **Step 2: Adicionar a rota e o build (feito na Task 13/15)**

(Esta task só cria a página; a rota e o `CaptchaField` vêm nas Tasks 13 e 15. Commit conjunto ao final da Task 13.)

### Task 13: Tela de escolha + rotas + link no login

**Files:**
- Create: `frontend/src/features/auth/pages/RegisterChoicePage.tsx`
- Modify: `frontend/src/router.tsx`
- Modify: `frontend/src/features/auth/pages/LoginPage.tsx`

- [ ] **Step 1: Criar a tela de escolha**

Create `frontend/src/features/auth/pages/RegisterChoicePage.tsx`:

```tsx
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export function RegisterChoicePage() {
  const navigate = useNavigate();
  return (
    <main className="min-h-dvh flex items-center justify-center bg-background p-4">
      <div className="w-full max-w-3xl grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Sou morador / proprietário</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-sm text-muted-foreground">
              Acesso completo ao condomínio. Requer unidade, comprovante de residência e
              aprovação do síndico.
            </p>
            <Button className="w-full" onClick={() => navigate('/register-master')}>
              Continuar como morador
            </Button>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Quero só ver Indicações e Classificados</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-sm text-muted-foreground">
              Cadastro de convidado: rápido, sem comprovante e sem aprovação. Dá acesso de
              leitura às áreas de Indicações e Classificados.
            </p>
            <Button variant="secondary" className="w-full" onClick={() => navigate('/register-guest')}>
              Continuar como convidado
            </Button>
          </CardContent>
        </Card>
      </div>
    </main>
  );
}
```

- [ ] **Step 2: Adicionar as rotas públicas**

Modify `frontend/src/router.tsx`: nas rotas públicas (junto de `/register-master`, ~linha 30), importe e adicione:

```tsx
import { RegisterChoicePage } from '@/features/auth/pages/RegisterChoicePage';
import { RegisterGuestPage } from '@/features/auth/pages/RegisterGuestPage';
```

E nas entradas de rotas públicas:

```tsx
{ path: '/register', element: <RegisterChoicePage /> },
{ path: '/register-guest', element: <RegisterGuestPage /> },
```

(Mantenha `/register-master` como está.)

- [ ] **Step 3: Apontar o link do login para /register**

Modify `frontend/src/features/auth/pages/LoginPage.tsx`: o link existente que aponta para `/register-master` (~linhas 100-113) passa a apontar para `/register` com texto neutro, ex.:

```tsx
<a href="/register" className="text-primary hover:underline">
  Criar conta
</a>
```

(Se o link usa `<Link to="...">` do react-router, troque o `to` para `/register`. Mantenha o estilo existente.)

- [ ] **Step 4: Build do front**

Run: `cd frontend && npm run build`
Expected: PASS. (Requer `CaptchaField` da Task 15 já existir — faça a Task 15 antes ou em conjunto.)

- [ ] **Step 5: Commit (páginas + rotas + login + page do guest)**

```bash
git add frontend/src/features/auth/pages/RegisterChoicePage.tsx \
        frontend/src/features/auth/pages/RegisterGuestPage.tsx \
        frontend/src/router.tsx \
        frontend/src/features/auth/pages/LoginPage.tsx
git commit -m "feat(guest): tela de escolha morador/convidado e RegisterGuestPage"
```

### Task 14: Esconder Avisos/Informações/FAQ do convidado no nav

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`

O filtro `can`/`filter` já existe: item com `requires` só aparece se `user.authorities.includes(requires)`. Basta marcar os 3 itens. Indicações e Classificados continuam **sem** `requires` (guest vê).

- [ ] **Step 1: Marcar os itens no App.tsx (NAV)**

Modify `frontend/src/App.tsx`: nos itens **Avisos** (~linha 30-35), **Informações** (~37-42) e **FAQ** (~44-49) do array `NAV`, adicione `requires: 'GENERAL_AREAS_VIEW'`. Exemplo para Avisos:

```tsx
  {
    to: '/announcements',
    label: 'Avisos',
    icon: Megaphone,
    requires: 'GENERAL_AREAS_VIEW',
  },
```

(Replique a adição de `requires: 'GENERAL_AREAS_VIEW'` nos itens Informações e FAQ. **Não** adicione `requires` em Indicações nem Classificados.)

- [ ] **Step 2: Marcar os itens no Sidebar.tsx (ITEMS)**

Modify `frontend/src/components/layout/Sidebar.tsx`: idem — adicione `requires: 'GENERAL_AREAS_VIEW'` aos itens Avisos, Informações e FAQ do array `ITEMS`.

- [ ] **Step 3: Build do front**

Run: `cd frontend && npm run build`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/App.tsx frontend/src/components/layout/Sidebar.tsx
git commit -m "feat(guest): nav esconde Avisos/Info/FAQ para quem nao tem GENERAL_AREAS_VIEW"
```

### Task 15: CaptchaField (placeholder no-op até provider)

**Files:**
- Create: `frontend/src/features/auth/CaptchaField.tsx`

Componente que expõe `onChange(token | null)`. Enquanto o provider não estiver decidido (Premissa 4), entrega um token fixo após o usuário marcar uma caixa "Não sou um robô" simples — mantém o fluxo testável e o contrato pronto para trocar pelo widget real (Turnstile/hCaptcha) sem mudar a página.

- [ ] **Step 1: Criar o componente placeholder**

Create `frontend/src/features/auth/CaptchaField.tsx`:

```tsx
import { useState } from 'react';

interface Props {
  onChange: (token: string | null) => void;
}

/**
 * Placeholder de captcha. Substituir pelo widget real (Cloudflare Turnstile / hCaptcha) quando o
 * provider for decidido — manter a mesma prop {@code onChange(token)}. O backend só valida o token
 * quando {@code app.security.captcha.enabled=true}; enquanto desligado, qualquer token é aceito.
 */
export function CaptchaField({ onChange }: Props) {
  const [checked, setChecked] = useState(false);
  return (
    <label className="flex items-center gap-2 text-sm">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => {
          setChecked(e.target.checked);
          onChange(e.target.checked ? 'placeholder-captcha-token' : null);
        }}
      />
      Confirmo que não sou um robô.
    </label>
  );
}
```

- [ ] **Step 2: Build do front**

Run: `cd frontend && npm run build`
Expected: PASS (resolve o import usado por `RegisterGuestPage`).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/auth/CaptchaField.tsx
git commit -m "feat(guest): CaptchaField placeholder (contrato p/ provider real)"
```

### Task 16: Verificação manual do fluxo (smoke)

**Files:** (nenhum — validação)

- [ ] **Step 1: Subir stack local e exercitar o fluxo**

Siga a memória `local-dev-stack` para subir Docker + backend + frontend. Então:
1. Acesse `/register` → veja os dois cards.
2. Clique "Continuar como convidado" → `/register-guest`.
3. Preencha, marque consent + captcha, envie → toast de sucesso, volta a `/login`.
4. Faça login com o convidado → no nav **não** aparecem Avisos/Informações/FAQ; **aparecem** Indicações e Classificados.
5. Em Indicações/Classificados, o convidado consegue **ler**; a ação de **criar** deve falhar com 403 (botão pode aparecer — defesa real é o backend; opcionalmente esconder o botão de criar quando `!authorities.includes('CONTENT_CREATE')` num follow-up).

Expected: comportamento acima. Anote qualquer divergência.

- [ ] **Step 2: Commit (se houver ajuste de smoke)**

```bash
# Apenas se algum ajuste pequeno foi necessário durante o smoke.
git add -A && git commit -m "fix(guest): ajustes do smoke do fluxo de convidado"
```

---

## Self-Review

**Spec coverage:**
- Decisão 1 (tela de escolha): Tasks 13 (RegisterChoicePage + rotas + link no login). ✓
- Decisão 2 (ACTIVE direto, sem aprovação): Task 4 (`status=ACTIVE`, sem fila). ✓
- Decisão 3 (só leitura; criar gated por CONTENT_CREATE): Tasks 9, 10 (+ Task 1 cria a permission). ✓
- Decisão 4 (anti-abuso: rate-limit + captcha): Task 6 (rate-limit), Tasks 3/15 (captcha back/front). ✓
- Decisão 5 (role GUEST + GENERAL_AREAS_VIEW barrando Avisos/Info/FAQ): Tasks 1, 7, 8, 14. ✓
- Modelo de identidade (sem unidade, isUnitMaster=false, sem proof): Task 4. ✓
- Endpoint público JSON `/api/auth/register-guest`: Tasks 2, 5. ✓
- Frontend (page, API, nav): Tasks 11–15. ✓
- Ordem de deploy ↔ migration: documentada em "Ordem de deploy" e no header do PR 3. ✓
- Impacto em moradores (contagem): inalterado (guest sem unit_id/RESIDENT); anotado. ✓

**Placeholder scan:** Todos os steps de código contêm o código real. Pontos que exigem verificação local (não placeholders, mas validações): (a) estilo de `RateLimitProperties` (getter/setter vs Lombok) — Task 6 Step 1 manda ler primeiro; (b) `Gender.NOT_INFORMED` existir — Task 4 Step 4 dá a forma segura alternativa; (c) campos reais de `CreateRecommendationRequest`/`CreateClassifiedRequest` para o body do teste de 201 — Tasks 9/10 indicam verificar o DTO; (d) forma do link no `LoginPage` (`<a>` vs `<Link>`) — Task 13 Step 3 cobre ambos. Estes são checagens pontuais, não conteúdo faltante.

**Type consistency:**
- `registerGuest(req, clientIp)` no service (Task 4) ↔ chamado pelo controller (Task 5) com `(req, ip)`. ✓
- `RegisterGuestRequest` campos (Task 2) ↔ usados no service `setGuestFields` (Task 4) e no JSON dos testes de controller (Task 5) e no payload do front (Task 11). ✓
- `CaptchaVerifier.verify(token, ip)` (Task 3) ↔ chamado no service (Task 4) ↔ mockado no teste (Task 4). ✓
- `RegistrationStatusResponse(userId, status)` reusado (já existe) com `status="ACTIVE"`. ✓
- Permission strings `GENERAL_AREAS_VIEW`/`CONTENT_CREATE` idênticas em: enum (Task 1), `@PreAuthorize` (Tasks 7–10), `MockAuth.user(..., "GENERAL_AREAS_VIEW")` nos testes, e `requires` no front (Task 14). ✓
- `RoleName.GUEST` (Task 1) ↔ `roleRepo.findByName(RoleName.GUEST)` (Task 4). ✓
- Property `app.security.captcha.enabled` consistente entre `NoopCaptchaVerifier` (Task 3) e nota do front (Task 15). ✓

Sem inconsistências de assinatura encontradas. Plano pronto.
