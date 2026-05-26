# Plano 2B — Registration + Unit Members + Moderation + MinIO upload

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute this plan task-by-task. Steps use checkbox (`- [ ]`) for tracking.

**Goal:** Adicionar o fluxo de auto-cadastro público de **master de unidade** com upload de comprovante de residência (PDF/JPG/PNG/WEBP até 5MB, com magic-bytes check no servidor + bucket isolado no MinIO), aprovação/rejeição por usuário com `REGISTRATION_APPROVE`, criação de sub-usuários pelo master, e endpoint de lookup de unidade. Inclui auditoria sensível (`proof_access_log`, `sensitive_access_log`), endpoint de presigned URL para o admin ver o comprovante, e frontend mínimo: `RegisterMasterPage` (wizard com selector torre→andar→posição + upload), `PendingApprovalPage`, e admin `PendingRegistrationsPage` (lista + approve/reject + view proof).

**Architecture:** Continua package-by-feature. Esta fase introduz `feature/registration/`, `feature/unit/` (expandida) e `storage/` (abstração FileStorage). MinIO acessado via service interna no Dokploy (`http://gestaocondominio-minio-iubdao:9000` em HML / `gestaocondominio-minio-exhgau` em prod). Buckets criados na inicialização via `MinioBootstrap` (idempotente). Upload multipart via Spring MultipartFile; magic-bytes check rejeita antes de subir para o MinIO. Presigned URL TTL 5 min retorna 302/200 com URL temporária para o admin baixar o arquivo direto do MinIO. Status do master: `PENDING_APPROVAL` → `ACTIVE` (approve) ou `REJECTED` (reject com `reason`). Transições guardadas em `User.approveAsMaster()`/`User.reject()` (já existem em P2A).

**Tech Stack:** Spring Boot 3.3.5 (mantido), MinIO Java SDK `io.minio:minio:8.5.13`, Apache Tika `org.apache.tika:tika-core:2.9.2` para magic-bytes detection (mais robusto que comparação manual), Spring Multipart 30MB max-request-size 5MB max-file-size.

**Spec base:** `docs/superpowers/specs/2026-05-24-gestor-condominio-design.md` seções 1, 2, 3.1 (user `residence_proof_*`), 4.2 (register-master), 4.3 (moderation), 4.8 (upload security), 11 (LGPD `proof_access_log`).

**Pré-requisito:** Plano 2A 100% (auth funcional em HML). Branch `main` limpa.

**Out-of-scope** (Plano 2C):
- Reset de senha via WhatsApp.
- Endpoints LGPD (`/privacy/me/export`, `/anonymize`, `/processing-activities`).
- WhatsApp notification client + outbox.

---

## File Structure

```
backend/
├── pom.xml                                            (Task 1 — deps minio+tika)
├── src/main/resources/
│   ├── application.yml                                (Task 1 — multipart limits + minio config)
│   ├── application-dev.yml                            (Task 1 — MinIO local)
│   └── db/migration/
│       └── V10__index_user_status_pending.sql         (Task 2 — index para listagem pending)
└── src/main/java/br/com/condominio/
    ├── storage/
    │   ├── FileStorage.java                            (Task 3 — interface)
    │   ├── MinioFileStorage.java                       (Task 4 — implementação)
    │   ├── MinioProperties.java                        (Task 4)
    │   ├── MinioBootstrap.java                         (Task 4 — cria buckets idempotente)
    │   └── MagicBytesValidator.java                    (Task 5 — Tika-based)
    └── feature/
        ├── unit/
        │   ├── UnitLookupController.java               (Task 6 — GET /units/lookup)
        │   ├── UnitService.java                        (Task 6)
        │   ├── dto/UnitLookupResponse.java              (Task 6)
        │   └── UnitRepository.java                     (modify — add findByMasterUserId)
        ├── registration/
        │   ├── RegisterMasterController.java            (Task 7)
        │   ├── RegistrationService.java                 (Task 7+8 — register + moderate)
        │   ├── RegistrationException.java               (Task 7)
        │   ├── RegistrationAdminController.java         (Task 8)
        │   ├── ConsentDocumentRepository.java           (Task 7)
        │   └── dto/
        │       ├── RegisterMasterRequest.java           (Task 7 — multipart fields)
        │       ├── RegistrationStatusResponse.java       (Task 7)
        │       ├── PendingRegistrationView.java          (Task 8)
        │       └── RejectRequest.java                    (Task 8)
        ├── user/
        │   ├── UnitMemberController.java                 (Task 9)
        │   ├── UnitMemberService.java                    (Task 9)
        │   └── dto/
        │       ├── CreateUnitMemberRequest.java          (Task 9)
        │       └── UnitMemberResponse.java               (Task 9)
        ├── consent/
        │   ├── ConsentController.java                    (Task 10 — GET /api/privacy/document/current)
        │   ├── ConsentService.java                       (Task 10)
        │   └── dto/ConsentDocumentView.java               (Task 10)
        └── audit/
            ├── ProofAccessLog.java                       (Task 11 — entity)
            ├── ProofAccessLogRepository.java             (Task 11)
            ├── SensitiveAccessLog.java                   (Task 11)
            └── SensitiveAccessLogRepository.java         (Task 11)
    └── src/test/java/br/com/condominio/
        ├── storage/MagicBytesValidatorTest.java          (Task 5 — TDD)
        ├── feature/registration/RegistrationServiceTest.java  (Task 7 — TDD)
        └── feature/unit/UnitServiceTest.java              (Task 6 — TDD)

frontend/
├── src/
│   ├── features/auth/pages/
│   │   ├── RegisterMasterPage.tsx                       (Task 12 — wizard)
│   │   └── PendingApprovalPage.tsx                       (Task 12)
│   ├── features/admin/
│   │   ├── pages/PendingRegistrationsPage.tsx           (Task 13)
│   │   └── api/adminApi.ts                              (Task 13)
│   ├── features/consent/
│   │   ├── ConsentBox.tsx                                (Task 12)
│   │   └── api/consentApi.ts                            (Task 12)
│   ├── components/UnitSelector.tsx                       (Task 12 — selector torre/andar/posição com debounce lookup)
│   ├── components/ProofUploader.tsx                      (Task 12 — drag-drop + preview + size check)
│   └── lib/api.ts                                        (modify — adicionar multipart helper)
```

---

## Convenções (mantidas do P2A)

- Branch: criar `feat/registration-master` a partir de `main`.
- TDD onde fizer sentido (services com lógica de domínio).
- Commits Conventional. Não usar `--no-verify`. Pre-push hook valida testes.
- Working dir: `D:/Projetos/gestor-condominio`.
- Lombok regras: nunca `@Data` em entidade JPA.
- `@Transactional` apenas em service.
- Lógica de domínio nos métodos `User.approveAsMaster()` e `User.reject()` (já criadas em P2A).
- **NÃO atualizar timestamps na entidade**: a lição de P2A é deixar `created_at`/`updated_at` com `DEFAULT now()` no banco e relaxar `NOT NULL` quando Hibernate causar problemas.

---

## Task 1: Setup branch + deps Maven + config Multipart/MinIO

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`

- [ ] **Step 1: Branch**

```bash
cd D:/Projetos/gestor-condominio
git checkout main
git pull --ff-only
git checkout -b feat/registration-master
```

- [ ] **Step 2: Adicionar deps em `backend/pom.xml`** (antes do `</dependencies>`)

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.13</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
```

- [ ] **Step 3: Adicionar config em `application.yml`**

Dentro do bloco `spring:` adicionar:

```yaml
  servlet:
    multipart:
      enabled: true
      max-file-size: 5MB
      max-request-size: 10MB
      file-size-threshold: 1MB
```

Dentro de `app:` adicionar:

```yaml
  storage:
    minio:
      endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
      access-key: ${MINIO_ACCESS_KEY:condominio}
      secret-key: ${MINIO_SECRET_KEY:condominio_dev}
      bucket-proofs: ${MINIO_BUCKET_PROOFS:residence-proofs}
      bucket-classifieds: ${MINIO_BUCKET_CLASSIFIEDS:classifieds}
      bucket-recommendations: ${MINIO_BUCKET_RECOMMENDATIONS:recommendations}
      presigned-ttl-proofs-seconds: ${MINIO_PRESIGNED_TTL_PROOFS_SECONDS:300}
      presigned-ttl-photos-seconds: ${MINIO_PRESIGNED_TTL_PHOTOS_SECONDS:600}
```

- [ ] **Step 4: `application-dev.yml`** — sem mudança necessária (default `localhost:9000` já cobre).

- [ ] **Step 5: Validar compile**

```bash
cd D:/Projetos/gestor-condominio/backend && ./mvnw -B -q clean compile 2>&1 | tail -5
```

Esperado: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
cd D:/Projetos/gestor-condominio
git add backend/pom.xml backend/src/main/resources/
git commit -m "build(backend): deps minio + tika; config multipart + minio"
```

---

## Task 2: V10 migration — index para listagem de pending

**Files:** Create `backend/src/main/resources/db/migration/V10__index_user_status_pending.sql`

- [ ] **Step 1: Criar migration**

```sql
-- flyway:transactional=true

-- Index covering para listagem rápida de PENDING_APPROVAL (admin queries)
CREATE INDEX IF NOT EXISTS idx_user_status_pending
    ON "user" (created_at DESC)
    WHERE status = 'PENDING_APPROVAL' AND deleted_at IS NULL;

-- Index para lookup de unidade por master
CREATE INDEX IF NOT EXISTS idx_unit_master_user
    ON unit (master_user_id)
    WHERE deleted_at IS NULL;
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/db/migration/V10__index_user_status_pending.sql
git commit -m "feat(db): V10 index para listagem PENDING_APPROVAL e unit.master_user_id"
```

---

## Task 3: FileStorage interface

**Files:** Create `backend/src/main/java/br/com/condominio/storage/FileStorage.java`

- [ ] **Step 1: Criar interface**

```java
package br.com.condominio.storage;

import java.io.InputStream;
import java.time.Duration;

public interface FileStorage {

  /** Upload byte stream to bucket. Returns the storage key (UUID-based). */
  String upload(String bucket, InputStream content, long contentLength, String contentType);

  /** Generate a presigned GET URL valid for ttl seconds. */
  String presignedGetUrl(String bucket, String objectKey, Duration ttl);

  /** Delete an object (used for LGPD anonymization). */
  void delete(String bucket, String objectKey);

  /** Ensure bucket exists; create if not. Idempotent. */
  void ensureBucketExists(String bucket);
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/br/com/condominio/storage/FileStorage.java
git commit -m "feat(storage): interface FileStorage (upload presigned delete ensure)"
```

---

## Task 4: MinioFileStorage + MinioProperties + MinioBootstrap

**Files:**
- Create: `backend/src/main/java/br/com/condominio/storage/MinioProperties.java`
- Create: `backend/src/main/java/br/com/condominio/storage/MinioFileStorage.java`
- Create: `backend/src/main/java/br/com/condominio/storage/MinioBootstrap.java`

- [ ] **Step 1: `MinioProperties.java`**

```java
package br.com.condominio.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage.minio")
public class MinioProperties {
  private String endpoint;
  private String accessKey;
  private String secretKey;
  private String bucketProofs = "residence-proofs";
  private String bucketClassifieds = "classifieds";
  private String bucketRecommendations = "recommendations";
  private int presignedTtlProofsSeconds = 300;
  private int presignedTtlPhotosSeconds = 600;
}
```

- [ ] **Step 2: `MinioFileStorage.java`**

```java
package br.com.condominio.storage;

import io.minio.*;
import io.minio.http.Method;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioFileStorage implements FileStorage {

  private final MinioClient client;
  private final MinioProperties props;

  @Override
  public String upload(String bucket, InputStream content, long contentLength, String contentType) {
    String key = UUID.randomUUID().toString();
    try {
      client.putObject(
          PutObjectArgs.builder()
              .bucket(bucket)
              .object(key)
              .stream(content, contentLength, -1)
              .contentType(contentType)
              .build());
      return key;
    } catch (Exception e) {
      throw new IllegalStateException("MinIO upload failed for bucket " + bucket, e);
    }
  }

  @Override
  public String presignedGetUrl(String bucket, String objectKey, Duration ttl) {
    try {
      return client.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(bucket)
              .object(objectKey)
              .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
              .build());
    } catch (Exception e) {
      throw new IllegalStateException("MinIO presigned URL failed", e);
    }
  }

  @Override
  public void delete(String bucket, String objectKey) {
    try {
      client.removeObject(
          RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception e) {
      throw new IllegalStateException("MinIO delete failed", e);
    }
  }

  @Override
  public void ensureBucketExists(String bucket) {
    try {
      boolean exists =
          client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
      if (!exists) {
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        log.info("Created MinIO bucket: {}", bucket);
      }
    } catch (Exception e) {
      throw new IllegalStateException("MinIO bucket check/create failed for " + bucket, e);
    }
  }

  @Configuration
  @EnableConfigurationProperties(MinioProperties.class)
  static class MinioClientConfig {
    @Bean
    public MinioClient minioClient(MinioProperties props) {
      return MinioClient.builder()
          .endpoint(props.getEndpoint())
          .credentials(props.getAccessKey(), props.getSecretKey())
          .build();
    }
  }
}
```

- [ ] **Step 3: `MinioBootstrap.java`**

```java
package br.com.condominio.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MinioBootstrap {

  private final FileStorage storage;
  private final MinioProperties props;

  @Bean
  public ApplicationRunner ensureBuckets() {
    return args -> {
      for (String bucket :
          new String[] {
            props.getBucketProofs(), props.getBucketClassifieds(), props.getBucketRecommendations()
          }) {
        try {
          storage.ensureBucketExists(bucket);
        } catch (Exception e) {
          log.error("MinioBootstrap: falhou criando/verificando bucket {}", bucket, e);
        }
      }
    };
  }
}
```

- [ ] **Step 4: Validar compile**

```bash
cd backend && ./mvnw -B -q clean compile 2>&1 | tail -5
```

- [ ] **Step 5: Spotless + commit**

```bash
cd backend && ./mvnw -q spotless:apply
cd D:/Projetos/gestor-condominio
git add backend/src/main/java/br/com/condominio/storage/
git commit -m "feat(storage): MinioFileStorage + MinioBootstrap (cria buckets idempotente)"
```

---

## Task 5: MagicBytesValidator TDD (Apache Tika)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/storage/MagicBytesValidator.java`
- Create: `backend/src/test/java/br/com/condominio/storage/MagicBytesValidatorTest.java`

- [ ] **Step 1: Escrever teste primeiro**

```java
package br.com.condominio.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MagicBytesValidatorTest {

  private final MagicBytesValidator validator = new MagicBytesValidator();

  @Test
  void detectsPdfFromMagicBytes() {
    byte[] pdf = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // %PDF-1.4
    String detected = validator.detect(new ByteArrayInputStream(pdf));
    assertThat(detected).isEqualTo("application/pdf");
  }

  @Test
  void detectsJpegFromMagicBytes() {
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
    String detected = validator.detect(new ByteArrayInputStream(jpeg));
    assertThat(detected).isEqualTo("image/jpeg");
  }

  @Test
  void detectsPngFromMagicBytes() {
    byte[] png = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };
    String detected = validator.detect(new ByteArrayInputStream(png));
    assertThat(detected).isEqualTo("image/png");
  }

  @Test
  void acceptsValidContentTypes() {
    assertThat(validator.isAcceptedForProof("application/pdf")).isTrue();
    assertThat(validator.isAcceptedForProof("image/jpeg")).isTrue();
    assertThat(validator.isAcceptedForProof("image/png")).isTrue();
    assertThat(validator.isAcceptedForProof("image/webp")).isTrue();
  }

  @Test
  void rejectsInvalidContentTypes() {
    assertThat(validator.isAcceptedForProof("text/html")).isFalse();
    assertThat(validator.isAcceptedForProof("application/zip")).isFalse();
    assertThat(validator.isAcceptedForProof("application/x-executable")).isFalse();
  }

  @Test
  void rejectsBytesPretendingToBePdfWithHtmlPrefix() {
    byte[] htmlMasqueradingAsPdf = "<html><body>fake</body></html>".getBytes();
    String detected = validator.detect(new ByteArrayInputStream(htmlMasqueradingAsPdf));
    assertThat(detected).startsWith("text/html");
  }
}
```

- [ ] **Step 2: Rodar teste (FAIL)**

```bash
cd backend && ./mvnw -B -q -Dtest=MagicBytesValidatorTest test
```

- [ ] **Step 3: Implementar**

```java
package br.com.condominio.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

@Component
public class MagicBytesValidator {

  static final Set<String> ALLOWED_PROOF_TYPES =
      Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");

  static final Set<String> ALLOWED_PHOTO_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");

  private final Tika tika = new Tika();

  /** Detect actual MIME type from bytes. Does NOT close the stream. Reads enough to identify. */
  public String detect(InputStream input) {
    try {
      return tika.detect(input);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to detect MIME from stream", e);
    }
  }

  public boolean isAcceptedForProof(String contentType) {
    return contentType != null && ALLOWED_PROOF_TYPES.contains(contentType);
  }

  public boolean isAcceptedForPhoto(String contentType) {
    return contentType != null && ALLOWED_PHOTO_TYPES.contains(contentType);
  }
}
```

- [ ] **Step 4: Rodar teste (PASS)** + spotless + commit

```bash
cd backend && ./mvnw -B -q -Dtest=MagicBytesValidatorTest test
cd backend && ./mvnw -q spotless:apply
cd D:/Projetos/gestor-condominio
git add backend/src/main/java/br/com/condominio/storage/MagicBytesValidator.java backend/src/test/java/br/com/condominio/storage/MagicBytesValidatorTest.java
git commit -m "feat(storage): MagicBytesValidator TDD com Apache Tika (6 testes)"
```

---

## Task 6: Unit lookup endpoint (público)

**Files:**
- Modify: `backend/src/main/java/br/com/condominio/feature/unit/UnitRepository.java`
- Create: `backend/src/main/java/br/com/condominio/feature/unit/UnitService.java`
- Create: `backend/src/main/java/br/com/condominio/feature/unit/UnitLookupController.java`
- Create: `backend/src/main/java/br/com/condominio/feature/unit/dto/UnitLookupResponse.java`
- Create: `backend/src/test/java/br/com/condominio/feature/unit/UnitServiceTest.java`

- [ ] **Step 1: Atualizar `UnitRepository.java`**

Adicione método (mantém o `findByCode` existente):

```java
Optional<Unit> findByMasterUserId(UUID masterUserId);
```

- [ ] **Step 2: TDD — teste**

```java
package br.com.condominio.feature.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnitServiceTest {

  private UnitRepository repo;
  private UnitService service;

  @BeforeEach
  void setUp() {
    repo = mock(UnitRepository.class);
    service = new UnitService(repo);
  }

  @Test
  void lookupReturnsHasMasterTrueWhenAlreadyAssigned() {
    Unit unit = new Unit();
    setField(unit, "code", "702C");
    setField(unit, "masterUserId", UUID.randomUUID());
    when(repo.findByCode("702C")).thenReturn(Optional.of(unit));

    var resp = service.lookupByCode("702C");

    assertThat(resp).isPresent();
    assertThat(resp.get().code()).isEqualTo("702C");
    assertThat(resp.get().hasActiveMaster()).isTrue();
  }

  @Test
  void lookupReturnsHasMasterFalseWhenNotAssigned() {
    Unit unit = new Unit();
    setField(unit, "code", "402A");
    when(repo.findByCode("402A")).thenReturn(Optional.of(unit));

    var resp = service.lookupByCode("402A");

    assertThat(resp.get().hasActiveMaster()).isFalse();
  }

  @Test
  void lookupReturnsEmptyWhenUnknownCode() {
    when(repo.findByCode("999Z")).thenReturn(Optional.empty());
    assertThat(service.lookupByCode("999Z")).isEmpty();
  }

  // Helper because Unit has protected setters
  private static void setField(Object target, String name, Object value) {
    try {
      var f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
```

- [ ] **Step 3: `UnitLookupResponse.java`**

```java
package br.com.condominio.feature.unit.dto;

import java.util.UUID;

public record UnitLookupResponse(UUID id, String code, boolean hasActiveMaster) {}
```

- [ ] **Step 4: `UnitService.java`**

```java
package br.com.condominio.feature.unit;

import br.com.condominio.feature.unit.dto.UnitLookupResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnitService {

  private final UnitRepository repo;

  @Transactional(readOnly = true)
  public Optional<UnitLookupResponse> lookupByCode(String code) {
    return repo.findByCode(code)
        .map(u -> new UnitLookupResponse(u.getId(), u.getCode(), u.getMasterUserId() != null));
  }
}
```

- [ ] **Step 5: `UnitLookupController.java`**

```java
package br.com.condominio.feature.unit;

import br.com.condominio.feature.unit.dto.UnitLookupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/units")
@RequiredArgsConstructor
public class UnitLookupController {

  private final UnitService service;

  @GetMapping("/lookup")
  public ResponseEntity<UnitLookupResponse> lookup(@RequestParam("code") String code) {
    return service.lookupByCode(code).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }
}
```

⚠ Lembre de ajustar `SecurityConfig.java` em P2A para liberar `GET /api/units/lookup` sem auth. Adicione na lista `.requestMatchers(HttpMethod.GET, "/api/units/lookup").permitAll()`.

- [ ] **Step 6: Atualizar SecurityConfig**

Em `backend/src/main/java/br/com/condominio/shared/security/SecurityConfig.java`, dentro do `.authorizeHttpRequests(...)`, antes de `.anyRequest().authenticated()`:

```java
.requestMatchers(HttpMethod.GET, "/api/units/lookup", "/api/privacy/document/current")
.permitAll()
```

(privacy/document é para o consent que vem na Task 10)

- [ ] **Step 7: Rodar testes + commit**

```bash
cd backend && ./mvnw -B -q -Dtest=UnitServiceTest test
cd backend && ./mvnw -q spotless:apply
cd D:/Projetos/gestor-condominio
git add backend/src/main/java/br/com/condominio/feature/unit/ backend/src/main/java/br/com/condominio/shared/security/SecurityConfig.java backend/src/test/java/br/com/condominio/feature/unit/
git commit -m "feat(unit): GET /api/units/lookup publico (TDD UnitService 3 testes)"
```

---

## Task 7: RegisterMasterController + RegistrationService (multipart upload)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/registration/dto/RegisterMasterRequest.java`
- Create: `backend/src/main/java/br/com/condominio/feature/registration/dto/RegistrationStatusResponse.java`
- Create: `backend/src/main/java/br/com/condominio/feature/registration/RegistrationException.java`
- Create: `backend/src/main/java/br/com/condominio/feature/registration/RegistrationService.java`
- Create: `backend/src/main/java/br/com/condominio/feature/registration/RegisterMasterController.java`
- Create: `backend/src/main/java/br/com/condominio/feature/registration/ConsentDocumentRepository.java`
- Create: `backend/src/test/java/br/com/condominio/feature/registration/RegistrationServiceTest.java`

- [ ] **Step 1: DTOs**

`RegisterMasterRequest.java`:
```java
package br.com.condominio.feature.registration.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RegisterMasterRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Size(max = 60) String greetingName,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
    @Size(max = 20) String gender,
    LocalDate birthDate,
    @NotBlank String unitCode,
    @NotBlank @Size(min = 8) String password,
    @NotBlank String consentVersion,
    boolean whatsappOptIn) {}
```

`RegistrationStatusResponse.java`:
```java
package br.com.condominio.feature.registration.dto;

import java.util.UUID;

public record RegistrationStatusResponse(UUID userId, String status) {}
```

- [ ] **Step 2: `RegistrationException.java`** (exception única para o feature)

```java
package br.com.condominio.feature.registration;

public class RegistrationException extends RuntimeException {
  private final String code;

  public RegistrationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
```

- [ ] **Step 3: `ConsentDocumentRepository.java`** (em `feature.registration`)

```java
package br.com.condominio.feature.registration;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.condominio.feature.consent.ConsentDocument;

public interface ConsentDocumentRepository extends JpaRepository<ConsentDocument, UUID> {
  Optional<ConsentDocument> findByVersion(String version);

  @Query("SELECT c FROM ConsentDocument c ORDER BY c.publishedAt DESC LIMIT 1")
  Optional<ConsentDocument> findLatest();
}
```

⚠ `ConsentDocument` entity será criado na Task 10. Aqui só referenciamos. Crie um stub:

`backend/src/main/java/br/com/condominio/feature/consent/ConsentDocument.java`:

```java
package br.com.condominio.feature.consent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "consent_document")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class ConsentDocument {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, length = 20)
  private String version;

  @Column(columnDefinition = "text", nullable = false)
  private String body;

  @Column(name = "published_at", nullable = false)
  private Instant publishedAt;
}
```

- [ ] **Step 4: Teste TDD `RegistrationServiceTest.java`**

```java
package br.com.condominio.feature.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.consent.ConsentDocument;
import br.com.condominio.feature.registration.dto.RegisterMasterRequest;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.*;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationServiceTest {

  private UnitRepository unitRepo;
  private UserRepository userRepo;
  private UserEmailRepository emailRepo;
  private RoleRepository roleRepo;
  private UserRoleRepository userRoleRepo;
  private ConsentDocumentRepository consentRepo;
  private FileStorage storage;
  private MagicBytesValidator magicBytes;
  private PasswordEncoder encoder;
  private MinioProperties props;
  private RegistrationService service;

  @BeforeEach
  void setUp() {
    unitRepo = mock(UnitRepository.class);
    userRepo = mock(UserRepository.class);
    emailRepo = mock(UserEmailRepository.class);
    roleRepo = mock(RoleRepository.class);
    userRoleRepo = mock(UserRoleRepository.class);
    consentRepo = mock(ConsentDocumentRepository.class);
    storage = mock(FileStorage.class);
    magicBytes = mock(MagicBytesValidator.class);
    encoder = mock(PasswordEncoder.class);
    props = new MinioProperties();
    props.setBucketProofs("residence-proofs");
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
            props);
  }

  @Test
  void registersMasterSuccessfully() throws Exception {
    when(emailRepo.findActiveByEmailIgnoreCase("paulo@x.com")).thenReturn(Optional.empty());
    when(magicBytes.detect(any())).thenReturn("application/pdf");
    when(magicBytes.isAcceptedForProof("application/pdf")).thenReturn(true);
    Unit unit = new Unit();
    setField(unit, "id", UUID.randomUUID());
    setField(unit, "code", "702C");
    when(unitRepo.findByCode("702C")).thenReturn(Optional.of(unit));
    when(consentRepo.findByVersion("1.0.0"))
        .thenReturn(Optional.of(newConsent("1.0.0")));
    when(encoder.encode(any())).thenReturn("hashed");
    when(storage.upload(eq("residence-proofs"), any(), anyLong(), eq("application/pdf")))
        .thenReturn("object-key-uuid");
    when(userRepo.save(any())).thenAnswer(inv -> {
      User u = inv.getArgument(0);
      setField(u, "id", UUID.randomUUID());
      return u;
    });

    var req =
        new RegisterMasterRequest(
            "Paulo Teste",
            "Paulo",
            "paulo@x.com",
            "+5511999999999",
            "MALE",
            LocalDate.of(1990, 1, 1),
            "702C",
            "Senha@1234",
            "1.0.0",
            true);
    byte[] pdf = {0x25, 0x50, 0x44, 0x46};
    MockMultipartFile file =
        new MockMultipartFile("proof", "comprovante.pdf", "application/pdf", pdf);

    var resp = service.registerMaster(req, file, "127.0.0.1");

    assertThat(resp.status()).isEqualTo("PENDING_APPROVAL");
    verify(storage).upload(eq("residence-proofs"), any(), eq((long) pdf.length), eq("application/pdf"));
    verify(emailRepo).save(any());
    verify(userRoleRepo).save(any());
  }

  @Test
  void rejectsWhenEmailAlreadyExists() {
    when(emailRepo.findActiveByEmailIgnoreCase("paulo@x.com"))
        .thenReturn(Optional.of(new UserEmail()));
    var req = baseReq();
    var file = new MockMultipartFile("proof", "f.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});

    assertThatThrownBy(() -> service.registerMaster(req, file, "127.0.0.1"))
        .isInstanceOf(RegistrationException.class)
        .hasMessageContaining("e-mail");
  }

  @Test
  void rejectsWhenUnitAlreadyHasMaster() {
    when(emailRepo.findActiveByEmailIgnoreCase(any())).thenReturn(Optional.empty());
    Unit unit = new Unit();
    setField(unit, "id", UUID.randomUUID());
    setField(unit, "masterUserId", UUID.randomUUID());
    when(unitRepo.findByCode("702C")).thenReturn(Optional.of(unit));

    var req = baseReq();
    var file = new MockMultipartFile("proof", "f.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});
    when(magicBytes.detect(any())).thenReturn("application/pdf");
    when(magicBytes.isAcceptedForProof(any())).thenReturn(true);

    assertThatThrownBy(() -> service.registerMaster(req, file, "127.0.0.1"))
        .isInstanceOf(RegistrationException.class)
        .hasMessageContaining("master");
  }

  @Test
  void rejectsWhenFileTypeNotAccepted() {
    when(emailRepo.findActiveByEmailIgnoreCase(any())).thenReturn(Optional.empty());
    when(magicBytes.detect(any())).thenReturn("application/zip");
    when(magicBytes.isAcceptedForProof("application/zip")).thenReturn(false);

    var req = baseReq();
    var file = new MockMultipartFile("proof", "f.zip", "application/zip", new byte[]{0x50, 0x4B});

    assertThatThrownBy(() -> service.registerMaster(req, file, "127.0.0.1"))
        .isInstanceOf(RegistrationException.class)
        .hasMessageContaining("comprovante");
  }

  // helpers
  private RegisterMasterRequest baseReq() {
    return new RegisterMasterRequest(
        "Paulo", "Paulo", "paulo@x.com", "+5511999999999", "MALE",
        LocalDate.of(1990, 1, 1), "702C", "Senha@1234", "1.0.0", false);
  }

  private ConsentDocument newConsent(String v) {
    ConsentDocument c = new ConsentDocument();
    setField(c, "version", v);
    setField(c, "publishedAt", Instant.now());
    return c;
  }

  static void setField(Object target, String name, Object value) {
    try {
      var f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
```

- [ ] **Step 5: `RegistrationService.java`** — implementação

```java
package br.com.condominio.feature.registration;

import br.com.condominio.feature.consent.ConsentDocument;
import br.com.condominio.feature.registration.dto.RegisterMasterRequest;
import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import br.com.condominio.feature.role.*;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.*;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

  private final UnitRepository unitRepo;
  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final RoleRepository roleRepo;
  private final UserRoleRepository userRoleRepo;
  private final ConsentDocumentRepository consentRepo;
  private final FileStorage storage;
  private final MagicBytesValidator magicBytes;
  private final PasswordEncoder encoder;
  private final MinioProperties props;

  @Transactional
  public RegistrationStatusResponse registerMaster(
      RegisterMasterRequest req, MultipartFile proof, String clientIp) {

    if (emailRepo.findActiveByEmailIgnoreCase(req.email()).isPresent()) {
      throw new RegistrationException("EMAIL_TAKEN", "Este e-mail já está cadastrado.");
    }

    Unit unit =
        unitRepo
            .findByCode(req.unitCode())
            .orElseThrow(() -> new RegistrationException("UNIT_NOT_FOUND", "Unidade não encontrada."));

    if (unit.getMasterUserId() != null) {
      throw new RegistrationException(
          "UNIT_HAS_MASTER", "Esta unidade já possui um master ativo.");
    }

    ConsentDocument consent =
        consentRepo
            .findByVersion(req.consentVersion())
            .orElseThrow(
                () ->
                    new RegistrationException(
                        "CONSENT_VERSION_INVALID", "Versão do termo de privacidade inválida."));

    String detectedMime;
    try {
      detectedMime = magicBytes.detect(proof.getInputStream());
    } catch (IOException e) {
      throw new RegistrationException("PROOF_READ_FAILED", "Falha ao ler comprovante.");
    }

    if (!magicBytes.isAcceptedForProof(detectedMime)) {
      throw new RegistrationException(
          "PROOF_TYPE_INVALID", "Tipo de comprovante inválido. Aceitamos PDF, JPG, PNG ou WEBP.");
    }

    String objectKey;
    try {
      objectKey =
          storage.upload(
              props.getBucketProofs(), proof.getInputStream(), proof.getSize(), detectedMime);
    } catch (IOException e) {
      throw new RegistrationException("PROOF_UPLOAD_FAILED", "Falha ao enviar comprovante.");
    }

    Role residentRole =
        roleRepo
            .findByName(RoleName.RESIDENT)
            .orElseThrow(() -> new IllegalStateException("RESIDENT role missing"));

    User user = new User();
    setUserFields(user, req, unit, objectKey, proof.getOriginalFilename(), detectedMime, consent, clientIp);
    user = userRepo.save(user);

    UserEmail userEmail = new UserEmail();
    setEmail(userEmail, user.getId(), req.email());
    emailRepo.save(userEmail);

    UserRole userRole = new UserRole(new UserRoleId(user.getId(), residentRole.getId()), Instant.now(), null);
    userRoleRepo.save(userRole);

    log.info(
        "Master registered: userId={} unitCode={} ip={}", user.getId(), unit.getCode(), clientIp);

    return new RegistrationStatusResponse(user.getId(), user.getStatus().name());
  }

  // -- field setters (bypass protected setters via reflection-light) ----

  private void setUserFields(
      User user,
      RegisterMasterRequest req,
      Unit unit,
      String objectKey,
      String originalFilename,
      String contentType,
      ConsentDocument consent,
      String clientIp) {
    try {
      setField(user, "unitId", unit.getId());
      setField(user, "isUnitMaster", true);
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
      setField(user, "status", UserStatus.PENDING_APPROVAL);
      setField(user, "residenceProofObjectKey", objectKey);
      setField(user, "residenceProofFilename", originalFilename);
      setField(user, "residenceProofContentType", contentType);
      setField(user, "residenceProofUploadedAt", Instant.now());
      setField(user, "consentDocumentVersion", consent.getVersion());
      setField(user, "consentAcceptedAt", Instant.now());
      setField(user, "consentAcceptedIp", clientIp);
      setField(user, "whatsappOptIn", req.whatsappOptIn());
      if (req.whatsappOptIn()) setField(user, "whatsappOptInAt", Instant.now());
    } catch (Exception e) {
      throw new IllegalStateException("Failed setting User fields", e);
    }
  }

  private void setEmail(UserEmail e, java.util.UUID userId, String email) {
    try {
      setField(e, "userId", userId);
      setField(e, "email", email);
      setField(e, "isPrimary", true);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    var f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }
}
```

- [ ] **Step 6: `RegisterMasterController.java`**

```java
package br.com.condominio.feature.registration;

import br.com.condominio.feature.registration.dto.RegisterMasterRequest;
import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RegisterMasterController {

  private final RegistrationService service;

  @PostMapping(value = "/register-master", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<RegistrationStatusResponse> registerMaster(
      @Valid @ModelAttribute RegisterMasterRequest req,
      @RequestPart("proof") MultipartFile proof,
      HttpServletRequest request) {
    String ip = resolveClientIp(request);
    RegistrationStatusResponse resp = service.registerMaster(req, proof, ip);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
  }

  private String resolveClientIp(HttpServletRequest request) {
    String fwd = request.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
    return request.getRemoteAddr();
  }
}
```

- [ ] **Step 7: Atualizar SecurityConfig**

Adicione `.requestMatchers(HttpMethod.POST, "/api/auth/register-master").permitAll()` na lista de permits.

- [ ] **Step 8: Mapear RegistrationException no GlobalExceptionHandler**

Em `GlobalExceptionHandler.java`, adicione:

```java
@ExceptionHandler(RegistrationException.class)
public ResponseEntity<ApiError> handleRegistration(RegistrationException ex) {
  return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(ApiError.of(400, "Bad Request", ex.getCode(), ex.getMessage(), requestId()));
}
```

- [ ] **Step 9: Rodar testes + spotless + commit**

```bash
cd backend && ./mvnw -B -q -Dtest=RegistrationServiceTest test
cd backend && ./mvnw -q spotless:apply
cd D:/Projetos/gestor-condominio
git add backend/
git commit -m "feat(registration): POST /register-master multipart com magic-bytes (TDD 4 testes)"
```

---

## Task 8: Moderation endpoints (list/approve/reject/proof-url)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/registration/RegistrationAdminController.java`
- Modify: `RegistrationService.java` (adicionar approve/reject/listPending/proofUrl)
- Create: `backend/src/main/java/br/com/condominio/feature/registration/dto/PendingRegistrationView.java`
- Create: `backend/src/main/java/br/com/condominio/feature/registration/dto/RejectRequest.java`

- [ ] **Step 1: DTOs**

`PendingRegistrationView.java`:
```java
package br.com.condominio.feature.registration.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PendingRegistrationView(
    UUID userId,
    String fullName,
    String email,
    String phone,
    String unitCode,
    String gender,
    LocalDate birthDate,
    String residenceProofFilename,
    Instant residenceProofUploadedAt,
    Instant createdAt) {}
```

`RejectRequest.java`:
```java
package br.com.condominio.feature.registration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(@NotBlank @Size(max = 500) String reason) {}
```

- [ ] **Step 2: Atualizar `UserRepository.java`** — adicionar query paginada

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

@Query(
    "SELECT u FROM User u WHERE u.status = br.com.condominio.feature.user.UserStatus.PENDING_APPROVAL "
        + "AND u.isUnitMaster = true ORDER BY u.createdAt DESC")
Page<User> findPendingMasters(Pageable pageable);
```

- [ ] **Step 3: Métodos no `RegistrationService.java`** (adicionar ao final da classe)

```java
@Transactional(readOnly = true)
public Page<PendingRegistrationView> listPending(Pageable pageable) {
  return userRepo.findPendingMasters(pageable).map(this::toView);
}

@Transactional
public void approve(UUID userId, UUID approverId) {
  User user = userRepo.findById(userId).orElseThrow(
      () -> new RegistrationException("USER_NOT_FOUND", "Usuário não encontrado"));
  user.approveAsMaster(approverId);

  Unit unit = unitRepo.findById(user.getUnitId()).orElseThrow();
  unit.assignMaster(user.getId());
  log.info("Master approved userId={} by approverId={}", userId, approverId);
}

@Transactional
public void reject(UUID userId, UUID approverId, String reason) {
  User user = userRepo.findById(userId).orElseThrow(
      () -> new RegistrationException("USER_NOT_FOUND", "Usuário não encontrado"));
  user.reject(approverId, reason);
  // remove o comprovante do MinIO (LGPD: retenção mínima)
  if (user.getResidenceProofObjectKey() != null) {
    try {
      storage.delete(props.getBucketProofs(), user.getResidenceProofObjectKey());
    } catch (Exception e) {
      log.warn("Failed to delete proof for rejected user {}: {}", userId, e.getMessage());
    }
  }
  log.info("Master rejected userId={} by approverId={} reason='{}'", userId, approverId, reason);
}

@Transactional(readOnly = true)
public String getProofPresignedUrl(UUID userId) {
  User user = userRepo.findById(userId).orElseThrow(
      () -> new RegistrationException("USER_NOT_FOUND", "Usuário não encontrado"));
  if (user.getResidenceProofObjectKey() == null) {
    throw new RegistrationException("NO_PROOF", "Usuário não tem comprovante.");
  }
  return storage.presignedGetUrl(
      props.getBucketProofs(),
      user.getResidenceProofObjectKey(),
      java.time.Duration.ofSeconds(props.getPresignedTtlProofsSeconds()));
}

private PendingRegistrationView toView(User u) {
  String email =
      emailRepo.findByUserId(u.getId()).stream()
          .filter(UserEmail::isPrimary)
          .findFirst()
          .map(UserEmail::getEmail)
          .orElse(null);
  String unitCode =
      u.getUnitId() == null ? null : unitRepo.findById(u.getUnitId()).map(Unit::getCode).orElse(null);
  return new PendingRegistrationView(
      u.getId(),
      u.getFullName(),
      email,
      u.getPhone(),
      unitCode,
      u.getGender() == null ? null : u.getGender().name(),
      u.getBirthDate(),
      u.getResidenceProofFilename(),
      u.getResidenceProofUploadedAt(),
      u.getCreatedAt());
}
```

- [ ] **Step 4: `RegistrationAdminController.java`**

```java
package br.com.condominio.feature.registration;

import br.com.condominio.feature.registration.dto.*;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
public class RegistrationAdminController {

  private final RegistrationService service;

  @GetMapping
  @PreAuthorize("hasAuthority('REGISTRATION_VIEW')")
  public Page<PendingRegistrationView> listPending(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.listPending(PageRequest.of(page, Math.min(size, 100)));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAuthority('REGISTRATION_APPROVE')")
  public ResponseEntity<Void> approve(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.approve(id, me.userId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAuthority('REGISTRATION_APPROVE')")
  public ResponseEntity<Void> reject(
      @PathVariable UUID id,
      @Valid @RequestBody RejectRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.reject(id, me.userId(), body.reason());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/proof-url")
  @PreAuthorize("hasAuthority('RESIDENCE_PROOF_VIEW')")
  public ResponseEntity<Map<String, Object>> proofUrl(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    String url = service.getProofPresignedUrl(id);
    return ResponseEntity.ok()
        .header("Referrer-Policy", "no-referrer")
        .body(Map.of("url", url, "ttlSeconds", 300));
  }
}
```

- [ ] **Step 5: Commit**

```bash
cd backend && ./mvnw -B -q compile && ./mvnw -q spotless:apply
cd D:/Projetos/gestor-condominio
git add backend/
git commit -m "feat(registration): endpoints admin list approve reject proof-url"
```

---

## Task 9: Unit Members — master cria sub-usuários

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/user/UnitMemberController.java`
- Create: `backend/src/main/java/br/com/condominio/feature/user/UnitMemberService.java`
- Create: `backend/src/main/java/br/com/condominio/feature/user/dto/CreateUnitMemberRequest.java`
- Create: `backend/src/main/java/br/com/condominio/feature/user/dto/UnitMemberResponse.java`

- [ ] **Step 1: DTOs**

`CreateUnitMemberRequest.java`:
```java
package br.com.condominio.feature.user.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreateUnitMemberRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Size(max = 60) String greetingName,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
    @Size(max = 20) String gender,
    LocalDate birthDate,
    @NotBlank @Size(min = 8) String password,
    boolean whatsappOptIn) {}
```

`UnitMemberResponse.java`:
```java
package br.com.condominio.feature.user.dto;

import java.util.UUID;

public record UnitMemberResponse(
    UUID id, String fullName, String greetingName, String email, String phone, String status) {}
```

- [ ] **Step 2: `UnitMemberService.java`**

```java
package br.com.condominio.feature.user;

import br.com.condominio.feature.role.*;
import br.com.condominio.feature.user.dto.*;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnitMemberService {

  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final UserRoleRepository userRoleRepo;
  private final RoleRepository roleRepo;
  private final PasswordEncoder encoder;

  @Transactional(readOnly = true)
  public List<UnitMemberResponse> listMyUnitMembers(UUID masterUserId) {
    User master =
        userRepo.findById(masterUserId).orElseThrow(() -> new IllegalStateException("master missing"));
    UUID unitId = master.getUnitId();
    return userRepo.findAll().stream()
        .filter(u -> unitId.equals(u.getUnitId()) && !u.getId().equals(masterUserId))
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public UnitMemberResponse createMember(UUID masterUserId, CreateUnitMemberRequest req) {
    User master = userRepo.findById(masterUserId).orElseThrow();
    if (!master.isUnitMaster()) {
      throw new IllegalStateException("Only masters can create members.");
    }
    if (emailRepo.findActiveByEmailIgnoreCase(req.email()).isPresent()) {
      throw new IllegalStateException("E-mail já cadastrado.");
    }

    Role residentRole = roleRepo.findByName(RoleName.RESIDENT).orElseThrow();

    User member = new User();
    try {
      setField(member, "unitId", master.getUnitId());
      setField(member, "isUnitMaster", false);
      setField(member, "fullName", req.fullName());
      setField(member, "greetingName", req.greetingName());
      setField(member, "phone", req.phone());
      if (req.gender() != null && !req.gender().isBlank()) {
        setField(member, "gender", Gender.valueOf(req.gender()));
      }
      setField(member, "birthDate", req.birthDate());
      setField(member, "passwordHash", encoder.encode(req.password()));
      setField(member, "passwordPepperVersion", (short) 1);
      setField(member, "mustChangePassword", true);
      setField(member, "status", UserStatus.ACTIVE);
      setField(member, "whatsappOptIn", req.whatsappOptIn());
      if (req.whatsappOptIn()) setField(member, "whatsappOptInAt", Instant.now());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    member = userRepo.save(member);

    UserEmail e = new UserEmail();
    try {
      setField(e, "userId", member.getId());
      setField(e, "email", req.email());
      setField(e, "isPrimary", true);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    emailRepo.save(e);

    UserRole ur = new UserRole(new UserRoleId(member.getId(), residentRole.getId()), Instant.now(), masterUserId);
    userRoleRepo.save(ur);

    log.info("Master {} created member {}", masterUserId, member.getId());
    return toResponse(member);
  }

  @Transactional
  public void disableMember(UUID masterUserId, UUID memberId) {
    User master = userRepo.findById(masterUserId).orElseThrow();
    User member = userRepo.findById(memberId).orElseThrow();
    if (!member.getUnitId().equals(master.getUnitId())) {
      throw new IllegalStateException("Member not in your unit.");
    }
    if (member.isUnitMaster()) {
      throw new IllegalStateException("Cannot disable master via this endpoint.");
    }
    member.disable();
    log.info("Master {} disabled member {}", masterUserId, memberId);
  }

  private UnitMemberResponse toResponse(User u) {
    String email =
        emailRepo.findByUserId(u.getId()).stream()
            .filter(UserEmail::isPrimary)
            .findFirst()
            .map(UserEmail::getEmail)
            .orElse(null);
    return new UnitMemberResponse(
        u.getId(), u.getFullName(), u.getGreetingName(), email, u.getPhone(), u.getStatus().name());
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    var f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }
}
```

- [ ] **Step 3: `UnitMemberController.java`**

```java
package br.com.condominio.feature.user;

import br.com.condominio.feature.user.dto.*;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/units/me/members")
@RequiredArgsConstructor
public class UnitMemberController {

  private final UnitMemberService service;

  @GetMapping
  public List<UnitMemberResponse> listMy(@AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    requireMaster(me);
    return service.listMyUnitMembers(me.userId());
  }

  @PostMapping
  public ResponseEntity<UnitMemberResponse> create(
      @Valid @RequestBody CreateUnitMemberRequest req,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    requireMaster(me);
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createMember(me.userId(), req));
  }

  @PutMapping("/{id}/disable")
  public ResponseEntity<Void> disable(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    requireMaster(me);
    service.disableMember(me.userId(), id);
    return ResponseEntity.noContent().build();
  }

  private void requireMaster(AuthenticatedUserPrincipal me) {
    if (me == null || !me.isUnitMaster()) {
      throw new AccessDeniedException("Apenas o master da unidade pode realizar esta ação.");
    }
  }
}
```

- [ ] **Step 4: Compile + commit**

```bash
cd backend && ./mvnw -B -q compile && ./mvnw -q spotless:apply
cd D:/Projetos/gestor-condominio
git add backend/src/main/java/br/com/condominio/feature/user/
git commit -m "feat(unit): /api/units/me/members CRUD pelo master da unidade"
```

---

## Task 10: Consent endpoint

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/consent/ConsentService.java`
- Create: `backend/src/main/java/br/com/condominio/feature/consent/ConsentController.java`
- Create: `backend/src/main/java/br/com/condominio/feature/consent/dto/ConsentDocumentView.java`

(Entity `ConsentDocument` foi criado em Task 7 stub.)

- [ ] **Step 1: DTO**

```java
package br.com.condominio.feature.consent.dto;

import java.time.Instant;

public record ConsentDocumentView(String version, String body, Instant publishedAt) {}
```

- [ ] **Step 2: Service**

```java
package br.com.condominio.feature.consent;

import br.com.condominio.feature.consent.dto.ConsentDocumentView;
import br.com.condominio.feature.registration.ConsentDocumentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsentService {

  private final ConsentDocumentRepository repo;

  @Transactional(readOnly = true)
  public Optional<ConsentDocumentView> current() {
    return repo.findLatest()
        .map(d -> new ConsentDocumentView(d.getVersion(), d.getBody(), d.getPublishedAt()));
  }
}
```

- [ ] **Step 3: Controller**

```java
package br.com.condominio.feature.consent;

import br.com.condominio.feature.consent.dto.ConsentDocumentView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/privacy/document")
@RequiredArgsConstructor
public class ConsentController {

  private final ConsentService service;

  @GetMapping("/current")
  public ResponseEntity<ConsentDocumentView> current() {
    return service.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }
}
```

- [ ] **Step 4: Commit**

```bash
cd backend && ./mvnw -B -q compile && ./mvnw -q spotless:apply
cd D:/Projetos/gestor-condominio
git add backend/src/main/java/br/com/condominio/feature/consent/
git commit -m "feat(consent): GET /api/privacy/document/current publico"
```

---

## Task 11: Audit log writers (proof + sensitive access)

**Files:**
- Create: `backend/src/main/java/br/com/condominio/feature/audit/ProofAccessLog.java`
- Create: `backend/src/main/java/br/com/condominio/feature/audit/ProofAccessLogRepository.java`
- Create: `backend/src/main/java/br/com/condominio/feature/audit/SensitiveAccessLog.java`
- Create: `backend/src/main/java/br/com/condominio/feature/audit/SensitiveAccessLogRepository.java`
- Create: `backend/src/main/java/br/com/condominio/feature/audit/AuditWriter.java`
- Modify: `RegistrationAdminController.java` (chamar AuditWriter no proof-url e list)

- [ ] **Step 1: Entities + Repos** (simples, sem TDD)

`ProofAccessLog.java`:
```java
package br.com.condominio.feature.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "proof_access_log")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ProofAccessLog {
  @Id @GeneratedValue private UUID id;

  @Column(name = "admin_user_id", nullable = false)
  private UUID adminUserId;

  @Column(name = "target_user_id", nullable = false)
  private UUID targetUserId;

  @Column(name = "accessed_at", nullable = false)
  private Instant accessedAt;

  @Column(name = "ip", columnDefinition = "inet")
  private String ip;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @Column(name = "presigned_url_ttl_seconds")
  private Integer presignedUrlTtlSeconds;

  @Column(name = "request_id", length = 40)
  private String requestId;
}
```

`SensitiveAccessLog.java`:
```java
package br.com.condominio.feature.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "sensitive_access_log")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SensitiveAccessLog {
  @Id @GeneratedValue private UUID id;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(name = "target_user_id")
  private UUID targetUserId;

  @Column(name = "action", nullable = false, length = 40)
  private String action;

  @Column(name = "acted_at", nullable = false)
  private Instant actedAt;

  @Column(name = "client_ip", columnDefinition = "inet")
  private String clientIp;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @Column(name = "request_id", length = 40)
  private String requestId;
}
```

Repos:
```java
package br.com.condominio.feature.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProofAccessLogRepository extends JpaRepository<ProofAccessLog, UUID> {}
```

```java
package br.com.condominio.feature.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensitiveAccessLogRepository extends JpaRepository<SensitiveAccessLog, UUID> {}
```

- [ ] **Step 2: `AuditWriter.java`** (helper service)

```java
package br.com.condominio.feature.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditWriter {

  private final ProofAccessLogRepository proofRepo;
  private final SensitiveAccessLogRepository sensitiveRepo;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void logProofAccess(
      UUID adminUserId, UUID targetUserId, HttpServletRequest request, int ttlSeconds) {
    ProofAccessLog log =
        new ProofAccessLog(
            null,
            adminUserId,
            targetUserId,
            Instant.now(),
            resolveIp(request),
            shortenUa(request.getHeader("User-Agent")),
            ttlSeconds,
            MDC.get("requestId"));
    proofRepo.save(log);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void logSensitiveAccess(
      UUID actorUserId, UUID targetUserId, String action, HttpServletRequest request) {
    SensitiveAccessLog log =
        new SensitiveAccessLog(
            null,
            actorUserId,
            targetUserId,
            action,
            Instant.now(),
            resolveIp(request),
            shortenUa(request.getHeader("User-Agent")),
            MDC.get("requestId"));
    sensitiveRepo.save(log);
  }

  private String resolveIp(HttpServletRequest r) {
    String fwd = r.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
    return r.getRemoteAddr();
  }

  private String shortenUa(String ua) {
    if (ua == null) return null;
    return ua.length() > 250 ? ua.substring(0, 250) : ua;
  }
}
```

- [ ] **Step 3: Atualizar `RegistrationAdminController.java`** para chamar `AuditWriter`

Adicione injection do `AuditWriter auditWriter` e:
- Em `proofUrl` chame `auditWriter.logProofAccess(me.userId(), id, request, 300)`. Adicione `HttpServletRequest request` no parâmetro do método.
- Em `listPending` (opcional, mas recomendado) chame `auditWriter.logSensitiveAccess(me.userId(), null, "REGISTRATION_VIEW", request)`.

- [ ] **Step 4: Commit**

```bash
cd backend && ./mvnw -B -q compile && ./mvnw -q spotless:apply
cd D:/Projetos/gestor-condominio
git add backend/src/main/java/br/com/condominio/feature/audit/ backend/src/main/java/br/com/condominio/feature/registration/RegistrationAdminController.java
git commit -m "feat(audit): ProofAccessLog SensitiveAccessLog AuditWriter REQUIRES_NEW"
```

---

## Task 12: Frontend — RegisterMasterPage wizard + PendingApprovalPage

**Files:**
- Create: `frontend/src/features/auth/pages/RegisterMasterPage.tsx`
- Create: `frontend/src/features/auth/pages/PendingApprovalPage.tsx`
- Create: `frontend/src/components/UnitSelector.tsx`
- Create: `frontend/src/components/ProofUploader.tsx`
- Create: `frontend/src/features/consent/api/consentApi.ts`
- Create: `frontend/src/features/consent/ConsentBox.tsx`
- Modify: `frontend/src/router.tsx`

- [ ] **Step 1: API consent**

`frontend/src/features/consent/api/consentApi.ts`:
```ts
import { api } from '@/lib/api';
import axios from 'axios';

export interface ConsentDoc { version: string; body: string; publishedAt: string; }

export async function fetchCurrent(): Promise<ConsentDoc> {
  const base = (import.meta.env.VITE_API_BASE_URL ?? '/api') as string;
  // endpoint público — usar axios direto, sem auth interceptor
  const r = await axios.get<ConsentDoc>(`${base}/privacy/document/current`);
  return r.data;
}

export async function registerMaster(form: FormData) {
  const base = (import.meta.env.VITE_API_BASE_URL ?? '/api') as string;
  const r = await axios.post(`${base}/auth/register-master`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}

export async function lookupUnit(code: string) {
  const base = (import.meta.env.VITE_API_BASE_URL ?? '/api') as string;
  const r = await axios.get(`${base}/units/lookup`, { params: { code } });
  return r.data as { id: string; code: string; hasActiveMaster: boolean };
}
```

- [ ] **Step 2: UnitSelector** (3 selects encadeados)

`frontend/src/components/UnitSelector.tsx`:
```tsx
import { useEffect, useState } from 'react';
import { Label } from '@/components/ui/label';
import { lookupUnit } from '@/features/consent/api/consentApi';

interface Props {
  value: string | null;
  onChange: (code: string | null, hasActiveMaster: boolean | null) => void;
}

const TOWERS = ['A', 'B', 'C'];
const FLOORS = Array.from({ length: 32 - 4 + 1 }, (_, i) => i + 4);
const POSITIONS = [1, 2, 3, 4, 5, 6];

export function UnitSelector({ value, onChange }: Props) {
  const [tower, setTower] = useState<string>('');
  const [floor, setFloor] = useState<number | ''>('');
  const [position, setPosition] = useState<number | ''>('');
  const [hasActiveMaster, setHasActiveMaster] = useState<boolean | null>(null);

  useEffect(() => {
    if (tower && floor && position) {
      const code = `${floor}${String(position).padStart(2, '0')}${tower}`;
      lookupUnit(code)
        .then((r) => {
          setHasActiveMaster(r.hasActiveMaster);
          onChange(code, r.hasActiveMaster);
        })
        .catch(() => onChange(null, null));
    } else {
      onChange(null, null);
    }
  }, [tower, floor, position]);

  return (
    <div className="grid grid-cols-3 gap-3">
      <div>
        <Label>Torre</Label>
        <select
          className="w-full rounded-md border border-input bg-background px-3 py-2"
          value={tower}
          onChange={(e) => setTower(e.target.value)}
        >
          <option value="">—</option>
          {TOWERS.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
      </div>
      <div>
        <Label>Andar</Label>
        <select
          className="w-full rounded-md border border-input bg-background px-3 py-2"
          value={floor}
          onChange={(e) => setFloor(e.target.value ? parseInt(e.target.value, 10) : '')}
        >
          <option value="">—</option>
          {FLOORS.map((f) => (
            <option key={f} value={f}>{f}</option>
          ))}
        </select>
      </div>
      <div>
        <Label>Apto</Label>
        <select
          className="w-full rounded-md border border-input bg-background px-3 py-2"
          value={position}
          onChange={(e) => setPosition(e.target.value ? parseInt(e.target.value, 10) : '')}
        >
          <option value="">—</option>
          {POSITIONS.map((p) => (
            <option key={p} value={p}>{String(p).padStart(2, '0')}</option>
          ))}
        </select>
      </div>
      {value && hasActiveMaster && (
        <p className="col-span-3 text-sm text-destructive" role="alert">
          Esta unidade já possui um master. Procure o síndico se você é o morador.
        </p>
      )}
      {value && hasActiveMaster === false && (
        <p className="col-span-3 text-sm text-success">Unidade disponível: <strong>{value}</strong></p>
      )}
    </div>
  );
}
```

- [ ] **Step 3: ProofUploader**

`frontend/src/components/ProofUploader.tsx`:
```tsx
import { useRef, useState } from 'react';
import { Label } from '@/components/ui/label';

interface Props {
  value: File | null;
  onChange: (f: File | null) => void;
}

const MAX_SIZE = 5 * 1024 * 1024; // 5MB
const ACCEPTED = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'];

export function ProofUploader({ value, onChange }: Props) {
  const ref = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);

  const handleFile = (f: File | null) => {
    setError(null);
    if (!f) {
      onChange(null);
      return;
    }
    if (!ACCEPTED.includes(f.type)) {
      setError('Tipo de arquivo inválido. Aceitamos PDF, JPG, PNG ou WEBP.');
      return;
    }
    if (f.size > MAX_SIZE) {
      setError('Arquivo maior que 5MB.');
      return;
    }
    onChange(f);
  };

  return (
    <div>
      <Label htmlFor="proof">Comprovante de residência</Label>
      <input
        id="proof"
        ref={ref}
        type="file"
        accept=".pdf,.jpg,.jpeg,.png,.webp"
        className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
        onChange={(e) => handleFile(e.target.files?.[0] ?? null)}
      />
      {value && <p className="text-sm text-muted-foreground mt-2">Selecionado: {value.name} ({(value.size / 1024).toFixed(0)} KB)</p>}
      {error && <p className="text-sm text-destructive mt-2" role="alert">{error}</p>}
    </div>
  );
}
```

- [ ] **Step 4: ConsentBox**

`frontend/src/features/consent/ConsentBox.tsx`:
```tsx
import { useEffect, useState } from 'react';
import { fetchCurrent } from './api/consentApi';

interface Props {
  accepted: boolean;
  onChange: (accepted: boolean, version: string | null) => void;
}

export function ConsentBox({ accepted, onChange }: Props) {
  const [doc, setDoc] = useState<{ version: string; body: string } | null>(null);

  useEffect(() => {
    fetchCurrent().then(setDoc).catch(() => null);
  }, []);

  if (!doc) return <p className="text-sm text-muted-foreground">Carregando termo...</p>;

  return (
    <div className="space-y-3">
      <div className="max-h-48 overflow-y-auto rounded-md border border-border p-3 text-sm whitespace-pre-wrap bg-muted/30">
        {doc.body}
      </div>
      <label className="flex items-start gap-2 text-sm cursor-pointer">
        <input
          type="checkbox"
          checked={accepted}
          onChange={(e) => onChange(e.target.checked, doc.version)}
          className="mt-1"
        />
        <span>Li e aceito o termo de privacidade (versão {doc.version}).</span>
      </label>
    </div>
  );
}
```

- [ ] **Step 5: RegisterMasterPage** (wizard simplificado em 1 página com todas as seções)

`frontend/src/features/auth/pages/RegisterMasterPage.tsx`:
```tsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { UnitSelector } from '@/components/UnitSelector';
import { ProofUploader } from '@/components/ProofUploader';
import { ConsentBox } from '@/features/consent/ConsentBox';
import { registerMaster } from '@/features/consent/api/consentApi';

export function RegisterMasterPage() {
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [greetingName, setGreetingName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [gender, setGender] = useState('NOT_INFORMED');
  const [birthDate, setBirthDate] = useState('');
  const [unitCode, setUnitCode] = useState<string | null>(null);
  const [hasMaster, setHasMaster] = useState<boolean | null>(null);
  const [password, setPassword] = useState('');
  const [consentVersion, setConsentVersion] = useState<string | null>(null);
  const [whatsappOptIn, setWhatsappOptIn] = useState(true);
  const [proof, setProof] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const canSubmit =
    !!fullName && !!greetingName && !!email && !!phone && !!unitCode && hasMaster === false &&
    password.length >= 8 && !!consentVersion && !!proof;

  const submit = async () => {
    if (!canSubmit || !proof) return;
    setSubmitting(true);
    try {
      const fd = new FormData();
      fd.append('fullName', fullName);
      fd.append('greetingName', greetingName);
      fd.append('email', email);
      fd.append('phone', phone);
      fd.append('gender', gender);
      if (birthDate) fd.append('birthDate', birthDate);
      fd.append('unitCode', unitCode!);
      fd.append('password', password);
      fd.append('consentVersion', consentVersion!);
      fd.append('whatsappOptIn', whatsappOptIn ? 'true' : 'false');
      fd.append('proof', proof);
      await registerMaster(fd);
      toast.success('Cadastro enviado! Aguarde aprovação do síndico.');
      navigate('/pending-approval', { replace: true });
    } catch (e) {
      const msg = (e as any)?.response?.data?.message ?? 'Erro ao cadastrar. Tente novamente.';
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="min-h-dvh flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-2xl">
        <CardHeader>
          <CardTitle>Cadastro de morador (master)</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <section>
            <h3 className="font-semibold mb-3">1. Identificação da unidade</h3>
            <UnitSelector value={unitCode} onChange={(c, h) => { setUnitCode(c); setHasMaster(h); }} />
          </section>
          <section className="space-y-3">
            <h3 className="font-semibold">2. Seus dados</h3>
            <div className="grid grid-cols-2 gap-3">
              <div><Label>Nome completo</Label><Input value={fullName} onChange={(e) => setFullName(e.target.value)} /></div>
              <div><Label>Como prefere ser chamado</Label><Input value={greetingName} onChange={(e) => setGreetingName(e.target.value)} /></div>
              <div><Label>E-mail</Label><Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} /></div>
              <div><Label>Telefone (WhatsApp)</Label><Input type="tel" placeholder="+5511..." value={phone} onChange={(e) => setPhone(e.target.value)} /></div>
              <div><Label>Data de nascimento</Label><Input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} /></div>
              <div>
                <Label>Gênero (opcional)</Label>
                <select className="w-full rounded-md border border-input bg-background px-3 py-2" value={gender} onChange={(e) => setGender(e.target.value)}>
                  <option value="NOT_INFORMED">Prefiro não informar</option>
                  <option value="MALE">Masculino</option>
                  <option value="FEMALE">Feminino</option>
                  <option value="OTHER">Outro</option>
                </select>
              </div>
              <div className="col-span-2"><Label>Senha (mínimo 8 caracteres)</Label><Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></div>
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={whatsappOptIn} onChange={(e) => setWhatsappOptIn(e.target.checked)} />
              Aceito receber comunicações operacionais via WhatsApp neste número.
            </label>
          </section>
          <section>
            <h3 className="font-semibold mb-3">3. Comprovante de residência</h3>
            <ProofUploader value={proof} onChange={setProof} />
          </section>
          <section>
            <h3 className="font-semibold mb-3">4. Termo de privacidade</h3>
            <ConsentBox accepted={!!consentVersion} onChange={(a, v) => setConsentVersion(a ? v : null)} />
          </section>
          <Button onClick={submit} disabled={!canSubmit || submitting} className="w-full">
            {submitting ? 'Enviando...' : 'Enviar cadastro'}
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
```

- [ ] **Step 6: PendingApprovalPage**

```tsx
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Home, Clock } from 'lucide-react';

export function PendingApprovalPage() {
  return (
    <main className="min-h-dvh flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-lg">
        <CardHeader className="space-y-3 text-center">
          <div className="flex justify-center gap-2 items-center">
            <Home className="text-primary" />
            <span className="font-heading font-semibold">HELBOR TRILOGY HOME</span>
          </div>
          <CardTitle>Cadastro recebido</CardTitle>
        </CardHeader>
        <CardContent className="text-center space-y-4">
          <Clock className="mx-auto w-12 h-12 text-accent" aria-hidden="true" />
          <p>
            Seu cadastro foi enviado e está em análise pelo síndico.
            Você receberá uma confirmação assim que for aprovado.
          </p>
          <a href="/login" className="text-primary hover:underline text-sm">Voltar para o login</a>
        </CardContent>
      </Card>
    </main>
  );
}
```

- [ ] **Step 7: Atualizar router**

Em `frontend/src/router.tsx` adicione as rotas públicas:

```tsx
{ path: '/register-master', element: <RegisterMasterPage /> },
{ path: '/pending-approval', element: <PendingApprovalPage /> },
```

Importe as páginas.

- [ ] **Step 8: Lint + build + commit**

```bash
cd D:/Projetos/gestor-condominio/frontend && npm run lint && npm run typecheck && npm run build
cd D:/Projetos/gestor-condominio
git add frontend/
git commit -m "feat(frontend): RegisterMasterPage wizard + PendingApprovalPage + UnitSelector + ProofUploader"
```

---

## Task 13: Frontend — Admin PendingRegistrationsPage

**Files:**
- Create: `frontend/src/features/admin/api/adminApi.ts`
- Create: `frontend/src/features/admin/pages/PendingRegistrationsPage.tsx`
- Modify: `frontend/src/router.tsx`

- [ ] **Step 1: adminApi**

```ts
import { api } from '@/lib/api';

export interface PendingRegistration {
  userId: string; fullName: string; email: string; phone: string;
  unitCode: string; gender: string | null; birthDate: string | null;
  residenceProofFilename: string; residenceProofUploadedAt: string;
  createdAt: string;
}

export async function listPending(page = 0, size = 20) {
  const r = await api.get('/registrations', { params: { page, size } });
  return r.data as { content: PendingRegistration[]; totalElements: number };
}

export async function approveRegistration(userId: string) {
  await api.post(`/registrations/${userId}/approve`);
}

export async function rejectRegistration(userId: string, reason: string) {
  await api.post(`/registrations/${userId}/reject`, { reason });
}

export async function getProofUrl(userId: string): Promise<string> {
  const r = await api.get(`/registrations/${userId}/proof-url`);
  return (r.data as { url: string }).url;
}
```

- [ ] **Step 2: PendingRegistrationsPage**

```tsx
import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { approveRegistration, getProofUrl, listPending, rejectRegistration, type PendingRegistration } from '../api/adminApi';
import { Check, X, FileText } from 'lucide-react';

export function PendingRegistrationsPage() {
  const [items, setItems] = useState<PendingRegistration[]>([]);
  const [loading, setLoading] = useState(true);
  const [rejecting, setRejecting] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const reload = async () => {
    setLoading(true);
    try {
      const data = await listPending();
      setItems(data.content);
    } catch (e) {
      toast.error('Erro ao carregar pendentes.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { reload(); }, []);

  const handleApprove = async (id: string) => {
    try {
      await approveRegistration(id);
      toast.success('Cadastro aprovado.');
      reload();
    } catch { toast.error('Erro ao aprovar.'); }
  };

  const handleReject = async (id: string) => {
    if (!rejectReason.trim()) return;
    try {
      await rejectRegistration(id, rejectReason);
      toast.success('Cadastro rejeitado.');
      setRejecting(null); setRejectReason('');
      reload();
    } catch { toast.error('Erro ao rejeitar.'); }
  };

  const handleViewProof = async (id: string) => {
    try {
      const url = await getProofUrl(id);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch { toast.error('Erro ao gerar URL.'); }
  };

  return (
    <main className="container py-8 space-y-6">
      <h1 className="text-2xl font-heading font-semibold">Cadastros pendentes</h1>
      {loading && <p className="text-muted-foreground">Carregando...</p>}
      {!loading && items.length === 0 && <p className="text-muted-foreground">Nenhum cadastro pendente.</p>}
      <div className="grid gap-4">
        {items.map((it) => (
          <Card key={it.userId}>
            <CardHeader><CardTitle className="text-lg">{it.fullName} — Unidade {it.unitCode}</CardTitle></CardHeader>
            <CardContent className="space-y-3">
              <div className="text-sm grid grid-cols-2 gap-2">
                <div><strong>E-mail:</strong> {it.email}</div>
                <div><strong>Telefone:</strong> {it.phone}</div>
                <div><strong>Nascimento:</strong> {it.birthDate ?? '—'}</div>
                <div><strong>Comprovante:</strong> {it.residenceProofFilename}</div>
              </div>
              <div className="flex gap-2 flex-wrap">
                <Button variant="outline" onClick={() => handleViewProof(it.userId)} aria-label="Ver comprovante">
                  <FileText className="w-4 h-4 mr-2" />Ver comprovante
                </Button>
                <Button onClick={() => handleApprove(it.userId)} aria-label="Aprovar">
                  <Check className="w-4 h-4 mr-2" />Aprovar
                </Button>
                <Button variant="destructive" onClick={() => setRejecting(it.userId)} aria-label="Rejeitar">
                  <X className="w-4 h-4 mr-2" />Rejeitar
                </Button>
              </div>
              {rejecting === it.userId && (
                <div className="space-y-2">
                  <Label>Motivo da rejeição</Label>
                  <Input value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} placeholder="Ex.: comprovante ilegível" />
                  <div className="flex gap-2">
                    <Button onClick={() => handleReject(it.userId)} disabled={!rejectReason.trim()}>Confirmar rejeição</Button>
                    <Button variant="outline" onClick={() => { setRejecting(null); setRejectReason(''); }}>Cancelar</Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </main>
  );
}
```

- [ ] **Step 3: Roteamento** — adicionar em `router.tsx`:

```tsx
{
  path: '/admin/registrations',
  element: (
    <ProtectedRoute>
      <PendingRegistrationsPage />
    </ProtectedRoute>
  ),
},
```

- [ ] **Step 4: Commit**

```bash
cd D:/Projetos/gestor-condominio/frontend && npm run lint && npm run typecheck && npm run build
cd D:/Projetos/gestor-condominio
git add frontend/
git commit -m "feat(frontend): admin PendingRegistrationsPage (approve reject view-proof)"
```

---

## Task 14: PR + deploy HML + smoke E2E

- [ ] **Step 1: Push branch + PR**

```bash
git push -u origin feat/registration-master
gh pr create --base main --head feat/registration-master \
  --title "feat: plano 2B - registration master + units members + moderation" \
  --body "Schema V10, FileStorage MinIO, MagicBytesValidator, register-master multipart, approve/reject, unit members CRUD, audit logs, frontend pages."
```

- [ ] **Step 2: Aguardar CI verde + merge**

```bash
until status=$(gh pr checks PR_NUM --watch=false 2>/dev/null); echo "$status" | grep -qE "(fail|success|pass)" && ! echo "$status" | grep -q "pending"; do sleep 15; done
gh pr merge PR_NUM --squash --delete-branch
```

- [ ] **Step 3: Smoke E2E em HML**

Após auto-deploy do HML:

```bash
# 1) Lookup de unidade disponível
curl https://hml.api.helbor.paulobof.com.br/api/units/lookup?code=702C

# 2) Cadastrar master (multipart)
curl -X POST https://hml.api.helbor.paulobof.com.br/api/auth/register-master \
  -F "fullName=Teste Morador" \
  -F "greetingName=Teste" \
  -F "email=teste@x.com" \
  -F "phone=+5511999999999" \
  -F "unitCode=702C" \
  -F "password=Teste@1234" \
  -F "consentVersion=1.0.0" \
  -F "whatsappOptIn=true" \
  -F "proof=@/path/to/comprovante.pdf"

# 3) Login como admin Paulo → lista pendentes
curl ... /api/auth/login
curl -H "Authorization: Bearer ..." /api/registrations

# 4) Aprovar
curl -X POST -H "Authorization: Bearer ..." /api/registrations/{id}/approve

# 5) Verificar status do morador via /api/auth/me após relogar
```

- [ ] **Step 4: Tag**

Quando smoke passar:

```bash
git tag v0.2.0-registration
git push origin v0.2.0-registration
```

---

## Critérios de aceite Plano 2B

- [ ] V10 migration aplica.
- [ ] MinIO buckets criados pelo `MinioBootstrap` no startup do backend HML.
- [ ] `MagicBytesValidatorTest`: 6 testes verdes.
- [ ] `RegistrationServiceTest`: 4 testes verdes.
- [ ] `UnitServiceTest`: 3 testes verdes.
- [ ] `POST /api/auth/register-master` aceita multipart, salva comprovante no MinIO, cria user PENDING_APPROVAL com role RESIDENT + master flag, e retorna 202.
- [ ] Tentar cadastrar com unit já ocupada retorna 400 `UNIT_HAS_MASTER`.
- [ ] Tentar enviar `.zip` no campo proof retorna 400 `PROOF_TYPE_INVALID`.
- [ ] `GET /api/registrations` autenticado com permissão `REGISTRATION_VIEW` retorna paginado.
- [ ] `POST /api/registrations/{id}/approve` muda status para ACTIVE e seta `unit.master_user_id`.
- [ ] `POST /api/registrations/{id}/reject` muda status para REJECTED e remove comprovante do MinIO.
- [ ] `GET /api/registrations/{id}/proof-url` retorna URL pré-assinada com TTL 5min + grava em `proof_access_log`.
- [ ] `POST /api/units/me/members` permite master criar sub-usuários ACTIVE direto.
- [ ] `PUT /api/units/me/members/{id}/disable` desativa membro (soft delete).
- [ ] Frontend `RegisterMasterPage` permite cadastrar master ponta a ponta em HML.
- [ ] Frontend `PendingRegistrationsPage` exibe lista, aprova/rejeita, abre comprovante em nova aba.
- [ ] PR mergeada, CI verde, deploy HML automático, tag `v0.2.0-registration` no remote.

---

## Próximo plano

**Plano 2C — WhatsApp reset + LGPD endpoints + frontend completion**:
- WhatsApp notification client (HMAC + Resilience4j + outbox)
- Password reset request/consume via WhatsApp
- LGPD endpoints (export, anonymize, processing-activities)
- Frontend: ChangePasswordPage, PasswordResetRequest/Consume, Privacy pages, UnitMembersPage
- Scheduled jobs (token cleanup, proof retention)
