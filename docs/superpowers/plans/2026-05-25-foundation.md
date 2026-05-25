# Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar o esqueleto funcional do monorepo gestor-condominio (HELBOR TRILOGY HOME): backend Spring Boot 3 "hello world" com actuator e logs JSON, frontend Vite/React/Tailwind com design tokens da marca, docker-compose local com Postgres+MinIO, hooks pre-commit (Husky+lint-staged+commitlint), CI no GitHub Actions e configurações de deploy para os dois environments Dokploy (HML + Prod) com stack de observabilidade (Prometheus/Grafana/Alertmanager).

**Architecture:** Monorepo com `backend/` (Maven, Java 21, Spring Boot 3) e `frontend/` (npm, Vite, React 18, TS). Tooling de qualidade na raiz via `package.json` mínimo. Deploy por serviços independentes no Dokploy via `Dockerfile` em cada subprojeto + arquivos compose para serviços auxiliares (MinIO, Prometheus, Grafana, Alertmanager). Trunk-based (push em `main` → deploy HML automático; promoção a Prod via workflow manual).

**Tech Stack:** Java 21, Spring Boot 3.3+, Maven, Lombok, springdoc-openapi 2.x, Micrometer + Prometheus, logstash-logback-encoder, Vite 5, React 18, TypeScript 5, Tailwind 3, shadcn/ui, Outfit+Work Sans (Google Fonts via @fontsource), Vitest, Husky 9, lint-staged 15, commitlint 19, ESLint 9 (flat), Prettier, google-java-format (Spotless), GitHub Actions, Dokploy.

**Spec base:** `docs/superpowers/specs/2026-05-24-gestor-condominio-design.md`.

---

## File Structure

Após este plano, o repo terá:

```
gestor-condominio/
├── .gitignore                                  (já existe)
├── .editorconfig                               (Task 1)
├── .lintstagedrc.json                          (Task 1)
├── commitlint.config.cjs                       (Task 1)
├── package.json                                (Task 1 — raiz, só devDeps de tooling)
├── package-lock.json                           (Task 1)
├── .husky/
│   ├── pre-commit                              (Task 1)
│   ├── commit-msg                              (Task 1)
│   └── pre-push                                (Task 1)
├── README.md                                   (Task 11)
├── CLAUDE.md                                   (Task 11)
├── docker-compose.dev.yml                      (Task 8)
├── .github/workflows/
│   ├── ci.yml                                  (Task 10)
│   └── promote-to-prod.yml                     (Task 10)
├── backend/
│   ├── pom.xml                                 (Task 2)
│   ├── Dockerfile                              (Task 4)
│   ├── .dockerignore                           (Task 4)
│   ├── mvnw, mvnw.cmd, .mvn/wrapper/...        (Task 2)
│   └── src/
│       ├── main/
│       │   ├── java/br/com/condominio/
│       │   │   ├── GestorCondominioApplication.java   (Task 2)
│       │   │   └── shared/observability/
│       │   │       └── MdcFilter.java                  (Task 3)
│       │   └── resources/
│       │       ├── application.yml                     (Task 2)
│       │       ├── application-dev.yml                 (Task 2)
│       │       ├── application-prod.yml               (Task 3)
│       │       ├── application-hml.yml                (Task 3)
│       │       └── logback-spring.xml                  (Task 3)
│       └── test/java/br/com/condominio/
│           └── GestorCondominioApplicationTests.java   (Task 2)
├── frontend/
│   ├── package.json                            (Task 5)
│   ├── package-lock.json                       (Task 5)
│   ├── vite.config.ts                          (Task 5)
│   ├── tsconfig.json, tsconfig.node.json       (Task 5)
│   ├── tailwind.config.ts                      (Task 6)
│   ├── postcss.config.js                       (Task 6)
│   ├── index.html                              (Task 5)
│   ├── components.json                         (Task 6 — shadcn)
│   ├── eslint.config.js                        (Task 5)
│   ├── .prettierrc.json                        (Task 5)
│   ├── vitest.config.ts                        (Task 5)
│   ├── Dockerfile                              (Task 7)
│   ├── nginx.conf                              (Task 7)
│   ├── .dockerignore                           (Task 7)
│   └── src/
│       ├── main.tsx                            (Task 5)
│       ├── App.tsx                             (Task 6)
│       ├── App.test.tsx                        (Task 6)
│       ├── vite-env.d.ts                       (Task 5)
│       ├── lib/utils.ts                        (Task 6 — shadcn cn helper)
│       └── design-system/tokens.css            (Task 6)
└── deploy/
    ├── README.md                               (Task 9)
    ├── dokploy-backend.env.example             (Task 9)
    ├── dokploy-frontend.env.example            (Task 9)
    ├── dokploy-minio-compose.yml               (Task 9)
    ├── dokploy-observability-compose.yml       (Task 9)
    ├── prometheus/prometheus.yml               (Task 9)
    ├── prometheus/alerts.yml                   (Task 9)
    ├── alertmanager/alertmanager.yml           (Task 9)
    └── grafana/provisioning/datasources/datasource.yml  (Task 9)
```

---

## Convenções deste plano

- **Diretório de trabalho** assumido em todos os comandos: `D:/Projetos/gestor-condominio` (use prefixo `cd backend &&` ou `cd frontend &&` quando precisar entrar num subprojeto).
- **PowerShell** no Windows: chaining usa `; if ($?) { ... }` não `&&`. Os comandos aqui usam Bash (Git Bash funciona); para PowerShell adapte a sintaxe.
- **Commits**: cada tarefa tem 1 commit final. Conventional Commits.
- **Sempre** rodar testes antes do commit (`pre-push` hook fica habilitado a partir da Task 2).

---

## Task 1: Tooling raiz (Husky + lint-staged + commitlint + editorconfig)

**Files:**
- Create: `package.json`
- Create: `.editorconfig`
- Create: `.lintstagedrc.json`
- Create: `commitlint.config.cjs`
- Create: `.husky/pre-commit`
- Create: `.husky/commit-msg`
- Create: `.husky/pre-push`

- [ ] **Step 1: Criar `package.json` raiz**

```json
{
  "name": "gestor-condominio",
  "private": true,
  "version": "0.0.0",
  "description": "Sistema de gestão do Condomínio HELBOR TRILOGY HOME",
  "scripts": {
    "prepare": "husky",
    "test:backend": "cd backend && ./mvnw test -q",
    "test:frontend": "cd frontend && npm test -- --run --passWithNoTests",
    "lint:backend": "cd backend && ./mvnw -q spotless:check",
    "lint:frontend": "cd frontend && npm run lint",
    "format:backend": "cd backend && ./mvnw -q spotless:apply"
  },
  "devDependencies": {
    "husky": "^9.1.6",
    "lint-staged": "^15.2.10",
    "@commitlint/cli": "^19.5.0",
    "@commitlint/config-conventional": "^19.5.0"
  }
}
```

- [ ] **Step 2: Criar `.editorconfig`**

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.{ts,tsx,js,jsx,json,md,yml,yaml,css,html}]
indent_style = space
indent_size = 2

[*.java]
indent_style = space
indent_size = 4

[*.{xml,pom}]
indent_style = space
indent_size = 2

[Makefile]
indent_style = tab
```

- [ ] **Step 3: Criar `.lintstagedrc.json`**

```json
{
  "frontend/**/*.{ts,tsx,js,jsx}": [
    "bash -c 'cd frontend && npx eslint --fix'",
    "bash -c 'cd frontend && npx prettier --write'"
  ],
  "frontend/**/*.{json,md,css,html}": [
    "bash -c 'cd frontend && npx prettier --write'"
  ],
  "backend/**/*.java": [
    "bash -c 'cd backend && ./mvnw -q spotless:apply'"
  ]
}
```

- [ ] **Step 4: Criar `commitlint.config.cjs`**

```js
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'subject-case': [2, 'never', ['upper-case']],
    'header-max-length': [2, 'always', 100],
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'chore', 'docs', 'refactor', 'test', 'style', 'perf', 'ci', 'build']
    ]
  }
};
```

- [ ] **Step 5: Instalar dependências e inicializar Husky**

```bash
npm install
npx husky init
```

Expected: cria `.husky/pre-commit` (placeholder), adiciona script `prepare` (já presente).

- [ ] **Step 6: Configurar hook `pre-commit`**

Sobrescrever `.husky/pre-commit`:

```bash
#!/usr/bin/env sh
npx lint-staged
```

Tornar executável (em Git Bash / Linux):

```bash
chmod +x .husky/pre-commit
```

- [ ] **Step 7: Criar hook `commit-msg`**

Conteúdo de `.husky/commit-msg`:

```bash
#!/usr/bin/env sh
npx --no -- commitlint --edit "$1"
```

```bash
chmod +x .husky/commit-msg
```

- [ ] **Step 8: Criar hook `pre-push`**

Conteúdo de `.husky/pre-push`:

```bash
#!/usr/bin/env sh
echo "[pre-push] rodando testes backend + frontend..."
npm run test:backend
npm run test:frontend
```

```bash
chmod +x .husky/pre-push
```

- [ ] **Step 9: Verificar hooks instalados**

```bash
ls -la .husky/
git config core.hooksPath
```

Expected: 3 hooks executáveis; `core.hooksPath` retorna `.husky`.

- [ ] **Step 10: Commit**

```bash
git add package.json package-lock.json .editorconfig .lintstagedrc.json commitlint.config.cjs .husky/
git commit -m "chore: bootstrap repo tooling (husky, lint-staged, commitlint, editorconfig)"
```

Expected: commit aceito (pre-push roda mas falha porque ainda não há backend/frontend — neste primeiro commit pode contornar com `git commit --no-verify` **apenas neste primeiro commit**).

⚠ Comando alternativo se `pre-push` bloquear o commit (não é o caso aqui, hook é pre-push não pre-commit, mas se algum lint-staged falhar):

```bash
git commit --no-verify -m "chore: bootstrap repo tooling (husky, lint-staged, commitlint, editorconfig)"
```

---

## Task 2: Backend Maven skeleton + Application + healthcheck

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`
- Create: `backend/src/main/java/br/com/condominio/GestorCondominioApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-dev.yml`
- Create: `backend/src/test/java/br/com/condominio/GestorCondominioApplicationTests.java`

- [ ] **Step 1: Criar `backend/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>br.com.condominio</groupId>
    <artifactId>gestor-condominio</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>gestor-condominio</name>
    <description>Sistema de gestao do Condominio HELBOR TRILOGY HOME</description>

    <properties>
        <java.version>21</java.version>
        <logstash-logback.version>7.4</logstash-logback.version>
        <springdoc.version>2.6.0</springdoc.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
            <version>${logstash-logback.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>gestor-condominio</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>2.43.0</version>
                <configuration>
                    <java>
                        <googleJavaFormat>
                            <version>1.22.0</version>
                            <style>GOOGLE</style>
                        </googleJavaFormat>
                        <removeUnusedImports/>
                        <importOrder/>
                        <trimTrailingWhitespace/>
                        <endWithNewline/>
                    </java>
                </configuration>
                <executions>
                    <execution>
                        <goals><goal>check</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Gerar Maven wrapper**

```bash
cd backend && mvn -N wrapper:wrapper -Dmaven=3.9.9
```

Expected: cria `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`. Se o Maven não estiver instalado globalmente, baixar manualmente o wrapper de https://github.com/apache/maven-wrapper/releases (ver `deploy/README.md` na Task 9).

- [ ] **Step 3: Criar `GestorCondominioApplication.java`**

`backend/src/main/java/br/com/condominio/GestorCondominioApplication.java`:

```java
package br.com.condominio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GestorCondominioApplication {

  public static void main(String[] args) {
    SpringApplication.run(GestorCondominioApplication.class, args);
  }
}
```

- [ ] **Step 4: Criar `application.yml` base**

`backend/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: gestor-condominio
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

server:
  port: 8080
  shutdown: graceful

management:
  endpoints:
    web:
      exposure:
        include: ${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE:health,info,metrics,prometheus}
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
  info:
    git:
      mode: simple

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```

- [ ] **Step 5: Criar `application-dev.yml`**

`backend/src/main/resources/application-dev.yml`:

```yaml
logging:
  level:
    root: INFO
    br.com.condominio: DEBUG
```

- [ ] **Step 6: Criar teste de smoke do contexto**

`backend/src/test/java/br/com/condominio/GestorCondominioApplicationTests.java`:

```java
package br.com.condominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
class GestorCondominioApplicationTests {

  @Autowired private ApplicationContext context;

  @Test
  void contextLoads() {
    assertThat(context).isNotNull();
    assertThat(context.getBean(GestorCondominioApplication.class)).isNotNull();
  }
}
```

- [ ] **Step 7: Rodar teste — esperado falhar (verde no contextLoads, mas ainda confirmar)**

```bash
cd backend && ./mvnw test -q
```

Expected: BUILD SUCCESS; `GestorCondominioApplicationTests.contextLoads` passa.

- [ ] **Step 8: Subir app local e verificar healthcheck**

```bash
cd backend && ./mvnw spring-boot:run
```

Em outro terminal:

```bash
curl -s http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`. Parar o app com `Ctrl+C`.

- [ ] **Step 9: Verificar Prometheus endpoint**

Subir o app de novo e:

```bash
curl -s http://localhost:8080/actuator/prometheus | head -20
```

Expected: linhas no formato Prometheus (`# HELP jvm_*`, `# TYPE jvm_*`, etc.).

- [ ] **Step 10: Spotless check**

```bash
cd backend && ./mvnw -q spotless:check
```

Expected: BUILD SUCCESS. Se falhar, rodar `./mvnw spotless:apply` e re-verificar.

- [ ] **Step 11: Commit**

```bash
git add backend/
git commit -m "feat(backend): skeleton Spring Boot 3 com actuator e endpoint Prometheus"
```

Expected: commit aceito; hook `pre-push` ainda não dispara (só em push).

---

## Task 3: Logs JSON + MdcFilter + Profile prod/hml

**Files:**
- Create: `backend/src/main/resources/logback-spring.xml`
- Create: `backend/src/main/java/br/com/condominio/shared/observability/MdcFilter.java`
- Create: `backend/src/main/resources/application-prod.yml`
- Create: `backend/src/main/resources/application-hml.yml`
- Create: `backend/src/test/java/br/com/condominio/shared/observability/MdcFilterTest.java`

- [ ] **Step 1: Escrever teste do filtro MDC**

`backend/src/test/java/br/com/condominio/shared/observability/MdcFilterTest.java`:

```java
package br.com.condominio.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MdcFilterTest {

  @Test
  void populatesRequestIdFromHeaderAndEchoesIt() throws ServletException, IOException {
    MdcFilter filter = new MdcFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    String[] capturedRequestIdHolder = new String[1];
    doAnswer(invocation -> {
      capturedRequestIdHolder[0] = MDC.get("requestId");
      return null;
    }).when(chain).doFilter(any(), any());

    filter.doFilterInternal(request, response, chain);

    assertThat(capturedRequestIdHolder[0]).isEqualTo("abc-123");
    assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-123");
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void generatesRequestIdWhenHeaderAbsent() throws ServletException, IOException {
    MdcFilter filter = new MdcFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    String[] capturedRequestIdHolder = new String[1];
    doAnswer(invocation -> {
      capturedRequestIdHolder[0] = MDC.get("requestId");
      return null;
    }).when(chain).doFilter(any(), any());

    filter.doFilterInternal(request, response, chain);

    assertThat(capturedRequestIdHolder[0]).isNotBlank();
    assertThat(response.getHeader("X-Request-Id")).isEqualTo(capturedRequestIdHolder[0]);
  }
}
```

- [ ] **Step 2: Rodar teste — esperado falhar (classe não existe)**

```bash
cd backend && ./mvnw -q -Dtest=MdcFilterTest test
```

Expected: FAIL com erro de compilação.

- [ ] **Step 3: Criar `MdcFilter.java`**

`backend/src/main/java/br/com/condominio/shared/observability/MdcFilter.java`:

```java
package br.com.condominio.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

  static final String HEADER_REQUEST_ID = "X-Request-Id";
  static final String MDC_REQUEST_ID = "requestId";
  static final String MDC_CLIENT_IP = "clientIp";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = request.getHeader(HEADER_REQUEST_ID);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }
    response.setHeader(HEADER_REQUEST_ID, requestId);
    MDC.put(MDC_REQUEST_ID, requestId);
    MDC.put(MDC_CLIENT_IP, resolveClientIp(request));
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
```

- [ ] **Step 4: Rodar teste — esperado passar**

```bash
cd backend && ./mvnw -q -Dtest=MdcFilterTest test
```

Expected: BUILD SUCCESS, 2 testes passam.

- [ ] **Step 5: Criar `logback-spring.xml` com appender JSON em prod/hml**

`backend/src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

  <springProfile name="dev">
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} %-5level [%X{requestId:-}] %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="STDOUT"/>
    </root>
    <logger name="br.com.condominio" level="DEBUG"/>
  </springProfile>

  <springProfile name="hml,prod">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>requestId</includeMdcKeyName>
        <includeMdcKeyName>userId</includeMdcKeyName>
        <includeMdcKeyName>clientIp</includeMdcKeyName>
        <fieldNames>
          <timestamp>@timestamp</timestamp>
          <level>level</level>
          <thread>thread</thread>
          <logger>logger</logger>
          <message>message</message>
          <stackTrace>stack_trace</stackTrace>
        </fieldNames>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="JSON"/>
    </root>
  </springProfile>

</configuration>
```

- [ ] **Step 6: Criar `application-prod.yml`**

`backend/src/main/resources/application-prod.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: never

logging:
  level:
    root: INFO
    br.com.condominio: INFO

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

- [ ] **Step 7: Criar `application-hml.yml`**

`backend/src/main/resources/application-hml.yml`:

```yaml
logging:
  level:
    root: INFO
    br.com.condominio: DEBUG

springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
```

- [ ] **Step 8: Subir app em modo prod e verificar log JSON**

```bash
cd backend && SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

Em outro terminal:

```bash
curl -s -H "X-Request-Id: smoke-1" http://localhost:8080/actuator/health
```

Expected output do log no console do app contém uma linha JSON com `"requestId":"smoke-1"`.

Parar o app.

- [ ] **Step 9: Rodar suite completa**

```bash
cd backend && ./mvnw -q test
```

Expected: BUILD SUCCESS, 3+ testes passam.

- [ ] **Step 10: Commit**

```bash
git add backend/
git commit -m "feat(backend): logs JSON com MDC requestId/userId em prod/hml + profile prod/hml"
```

---

## Task 4: Backend Dockerfile

**Files:**
- Create: `backend/Dockerfile`
- Create: `backend/.dockerignore`

- [ ] **Step 1: Criar `backend/Dockerfile` multi-stage**

```dockerfile
# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src ./src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /workspace/target/gestor-condominio.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

- [ ] **Step 2: Criar `backend/.dockerignore`**

```
target/
.idea/
*.iml
.git/
.mvn/wrapper/maven-wrapper.jar
HELP.md
docs/
```

- [ ] **Step 3: Build da imagem**

```bash
cd backend && docker build -t gestor-condominio-backend:dev .
```

Expected: BUILD SUCCESS; imagem ~250MB.

- [ ] **Step 4: Rodar container e testar healthcheck**

```bash
docker run --rm -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod gestor-condominio-backend:dev
```

Em outro terminal:

```bash
sleep 15 && curl -s http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`. Parar container.

- [ ] **Step 5: Commit**

```bash
git add backend/Dockerfile backend/.dockerignore
git commit -m "feat(backend): Dockerfile multi-stage com JDK 21 build + JRE 21 runtime"
```

---

## Task 5: Frontend Vite + TS + ESLint + Prettier + Vitest

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`, `frontend/tsconfig.node.json`
- Create: `frontend/vitest.config.ts`
- Create: `frontend/eslint.config.js`
- Create: `frontend/.prettierrc.json`
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/vite-env.d.ts`

- [ ] **Step 1: Criar `frontend/package.json`**

```json
{
  "name": "gestor-condominio-frontend",
  "private": true,
  "version": "0.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "lint": "eslint .",
    "typecheck": "tsc --noEmit",
    "format": "prettier --write .",
    "test": "vitest"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.3",
    "typescript": "~5.6.3",
    "vite": "^5.4.10",
    "eslint": "^9.13.0",
    "@eslint/js": "^9.13.0",
    "typescript-eslint": "^8.11.0",
    "eslint-plugin-react": "^7.37.2",
    "eslint-plugin-react-hooks": "^5.0.0",
    "eslint-plugin-jsx-a11y": "^6.10.2",
    "eslint-plugin-import": "^2.31.0",
    "eslint-config-prettier": "^9.1.0",
    "prettier": "^3.3.3",
    "vitest": "^2.1.4",
    "@testing-library/react": "^16.0.1",
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/user-event": "^14.5.2",
    "jsdom": "^25.0.1",
    "@axe-core/react": "^4.10.0"
  }
}
```

- [ ] **Step 2: Criar `tsconfig.json` e `tsconfig.node.json`**

`frontend/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "baseUrl": ".",
    "paths": { "@/*": ["src/*"] },
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src", "vitest.config.ts"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

`frontend/tsconfig.node.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2023"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "noEmit": true
  },
  "include": ["vite.config.ts", "vitest.config.ts"]
}
```

- [ ] **Step 3: Criar `vite.config.ts`**

`frontend/vite.config.ts`:

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
```

- [ ] **Step 4: Criar `vitest.config.ts`**

`frontend/vitest.config.ts`:

```ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    css: true,
  },
});
```

- [ ] **Step 5: Criar `eslint.config.js`**

`frontend/eslint.config.js`:

```js
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import prettier from 'eslint-config-prettier';

export default tseslint.config(
  { ignores: ['dist', 'node_modules'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommended,
      react.configs.flat.recommended,
      reactHooks.configs.recommended,
      jsxA11y.flatConfigs.recommended,
      prettier,
    ],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { browser: true },
    },
    rules: {
      'react/react-in-jsx-scope': 'off',
    },
    settings: { react: { version: 'detect' } },
  }
);
```

- [ ] **Step 6: Criar `.prettierrc.json`**

`frontend/.prettierrc.json`:

```json
{
  "semi": true,
  "singleQuote": true,
  "trailingComma": "es5",
  "printWidth": 100,
  "tabWidth": 2,
  "arrowParens": "always"
}
```

- [ ] **Step 7: Criar `index.html`**

`frontend/index.html`:

```html
<!doctype html>
<html lang="pt-BR">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>HELBOR TRILOGY HOME</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 8: Criar `src/main.tsx` e `src/vite-env.d.ts`**

`frontend/src/main.tsx`:

```tsx
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './design-system/tokens.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
```

`frontend/src/vite-env.d.ts`:

```ts
/// <reference types="vite/client" />
```

- [ ] **Step 9: Criar `src/test-setup.ts`**

`frontend/src/test-setup.ts`:

```ts
import '@testing-library/jest-dom';
```

- [ ] **Step 10: Instalar dependências**

```bash
cd frontend && npm install
```

Expected: `node_modules` criado, sem erros.

- [ ] **Step 11: Commit (sem `App.tsx` ainda — vem na próxima task)**

```bash
git add frontend/
git commit -m "feat(frontend): scaffold Vite + React + TS + ESLint + Prettier + Vitest"
```

⚠ Falta `App.tsx` e `tokens.css` — esses entram na Task 6 e quebrarão temporariamente o `npm run build`. Aceitável neste commit isolado.

---

## Task 6: Frontend Tailwind + shadcn + design tokens + smoke test

**Files:**
- Create: `frontend/tailwind.config.ts`
- Create: `frontend/postcss.config.js`
- Create: `frontend/components.json`
- Create: `frontend/src/design-system/tokens.css`
- Create: `frontend/src/lib/utils.ts`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/App.test.tsx`

- [ ] **Step 1: Instalar Tailwind + deps de design**

```bash
cd frontend && npm install -D tailwindcss postcss autoprefixer && npm install clsx tailwind-merge class-variance-authority @fontsource/outfit @fontsource/work-sans lucide-react
```

- [ ] **Step 2: Criar `tailwind.config.ts`**

`frontend/tailwind.config.ts`:

```ts
import type { Config } from 'tailwindcss';

const config: Config = {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    container: {
      center: true,
      padding: '1rem',
      screens: { '2xl': '1280px' },
    },
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--border))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        info: 'hsl(var(--info))',
        success: 'hsl(var(--success))',
        warning: 'hsl(var(--warning))',
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground, 0 0% 100%))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
      },
      fontFamily: {
        sans: ['Work Sans', 'system-ui', 'sans-serif'],
        heading: ['Outfit', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 4px)',
        sm: 'calc(var(--radius) - 6px)',
      },
    },
  },
  plugins: [],
};

export default config;
```

- [ ] **Step 3: Criar `postcss.config.js`**

`frontend/postcss.config.js`:

```js
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

- [ ] **Step 4: Criar `components.json` (config shadcn)**

`frontend/components.json`:

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "default",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "tailwind.config.ts",
    "css": "src/design-system/tokens.css",
    "baseColor": "slate",
    "cssVariables": true,
    "prefix": ""
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  }
}
```

- [ ] **Step 5: Criar `tokens.css` com a paleta HELBOR TRILOGY HOME**

`frontend/src/design-system/tokens.css`:

```css
@import '@fontsource/outfit/300.css';
@import '@fontsource/outfit/400.css';
@import '@fontsource/outfit/500.css';
@import '@fontsource/outfit/600.css';
@import '@fontsource/outfit/700.css';
@import '@fontsource/work-sans/300.css';
@import '@fontsource/work-sans/400.css';
@import '@fontsource/work-sans/500.css';
@import '@fontsource/work-sans/600.css';

@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root {
    --background: 0 0% 100%;
    --foreground: 222 47% 11%;
    --card: 210 20% 98%;
    --card-foreground: 222 47% 11%;
    --primary: 210 78% 47%;
    --primary-foreground: 0 0% 100%;
    --accent: 42 90% 54%;
    --accent-foreground: 222 47% 11%;
    --info: 188 75% 45%;
    --success: 82 56% 49%;
    --warning: 42 90% 54%;
    --destructive: 354 75% 56%;
    --destructive-foreground: 0 0% 100%;
    --muted: 210 16% 93%;
    --muted-foreground: 215 16% 47%;
    --border: 214 32% 91%;
    --ring: 210 78% 47%;
    --radius: 0.75rem;
  }

  .dark {
    --background: 0 0% 6%;
    --foreground: 0 0% 96%;
    --card: 0 0% 10%;
    --card-foreground: 0 0% 96%;
    --primary: 210 78% 60%;
    --primary-foreground: 0 0% 100%;
    --accent: 42 90% 54%;
    --accent-foreground: 222 47% 11%;
    --info: 188 75% 45%;
    --success: 82 56% 49%;
    --warning: 42 90% 54%;
    --destructive: 354 75% 56%;
    --destructive-foreground: 0 0% 100%;
    --muted: 0 0% 16%;
    --muted-foreground: 0 0% 65%;
    --border: 0 0% 18%;
    --ring: 210 78% 60%;
  }

  * {
    @apply border-border;
  }

  body {
    @apply bg-background text-foreground font-sans antialiased;
    font-feature-settings: 'rlig' 1, 'calt' 1;
  }

  h1, h2, h3, h4, h5, h6 {
    @apply font-heading;
  }
}
```

- [ ] **Step 6: Criar `lib/utils.ts`**

`frontend/src/lib/utils.ts`:

```ts
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
```

- [ ] **Step 7: Escrever teste do App**

`frontend/src/App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import App from './App';

describe('App', () => {
  it('renderiza o nome do condominio', () => {
    render(<App />);
    expect(screen.getByText(/helbor trilogy home/i)).toBeInTheDocument();
  });

  it('mostra status do backend (placeholder)', () => {
    render(<App />);
    expect(screen.getByTestId('app-status')).toBeInTheDocument();
  });
});
```

- [ ] **Step 8: Rodar teste — esperado falhar**

```bash
cd frontend && npm test -- --run
```

Expected: FAIL (App.tsx ainda não existe).

- [ ] **Step 9: Criar `App.tsx` mínimo**

`frontend/src/App.tsx`:

```tsx
import { Home } from 'lucide-react';

export default function App() {
  return (
    <main className="min-h-dvh bg-background text-foreground">
      <header className="border-b border-border">
        <div className="container flex items-center gap-3 py-6">
          <Home className="text-primary" aria-hidden="true" />
          <h1 className="text-2xl md:text-3xl font-heading font-semibold tracking-tight">
            HELBOR TRILOGY HOME
          </h1>
        </div>
      </header>
      <section className="container py-10 space-y-4">
        <p className="text-muted-foreground max-w-prose">
          Bem-vindo ao portal de gestão do condomínio. O sistema está em fase de
          implantação.
        </p>
        <p
          data-testid="app-status"
          className="inline-flex items-center gap-2 rounded-md bg-success/10 px-3 py-1.5 text-sm font-medium text-success"
        >
          Sistema disponível
        </p>
      </section>
    </main>
  );
}
```

- [ ] **Step 10: Rodar teste — esperado passar**

```bash
cd frontend && npm test -- --run
```

Expected: PASS, 2 testes verdes.

- [ ] **Step 11: Lint + typecheck + build**

```bash
cd frontend && npm run lint && npm run typecheck && npm run build
```

Expected: todos verdes; `dist/` gerado.

- [ ] **Step 12: Subir dev server e validar manualmente**

```bash
cd frontend && npm run dev
```

Abrir `http://localhost:5173`. Esperado: cabeçalho com ícone Home azul, título "HELBOR TRILOGY HOME" em Outfit, parágrafo em Work Sans, badge "Sistema disponível" verde. Encerrar com `Ctrl+C`.

- [ ] **Step 13: Commit**

```bash
git add frontend/
git commit -m "feat(frontend): design tokens HELBOR + Tailwind + shadcn config + App shell"
```

---

## Task 7: Frontend Dockerfile + nginx config

**Files:**
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Create: `frontend/.dockerignore`

- [ ] **Step 1: Criar `frontend/Dockerfile` multi-stage**

```dockerfile
# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
RUN apk add --no-cache curl
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -fs http://localhost/healthz || exit 1
```

- [ ] **Step 2: Criar `nginx.conf` com SPA fallback + security headers + CSP**

`frontend/nginx.conf`:

```nginx
server {
  listen 80;
  server_name _;

  root /usr/share/nginx/html;
  index index.html;

  # Healthcheck endpoint
  location = /healthz {
    add_header Content-Type text/plain;
    return 200 "ok\n";
  }

  # Security headers
  add_header X-Content-Type-Options "nosniff" always;
  add_header X-Frame-Options "DENY" always;
  add_header Referrer-Policy "no-referrer" always;
  add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
  # CSP estrito; ajustar API_BASE em build time quando publicar em prod
  add_header Content-Security-Policy "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; connect-src 'self'; script-src 'self'; frame-ancestors 'none'; base-uri 'self'" always;

  # Cache de assets versionados
  location /assets/ {
    expires 1y;
    add_header Cache-Control "public, immutable";
    try_files $uri =404;
  }

  # SPA fallback
  location / {
    try_files $uri $uri/ /index.html;
  }

  gzip on;
  gzip_types text/plain text/css application/javascript application/json image/svg+xml;
  gzip_min_length 256;
}
```

- [ ] **Step 3: Criar `frontend/.dockerignore`**

```
node_modules/
dist/
.git/
.idea/
.vscode/
*.log
```

- [ ] **Step 4: Build da imagem**

```bash
cd frontend && docker build -t gestor-condominio-frontend:dev .
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Rodar container e testar**

```bash
docker run --rm -p 8081:80 gestor-condominio-frontend:dev
```

Em outro terminal:

```bash
curl -s http://localhost:8081/healthz
curl -sI http://localhost:8081/ | grep -E "(content-security-policy|x-frame-options|referrer-policy)"
```

Expected: `ok`; headers de segurança presentes.

Parar container.

- [ ] **Step 6: Commit**

```bash
git add frontend/Dockerfile frontend/nginx.conf frontend/.dockerignore
git commit -m "feat(frontend): Dockerfile multi-stage (Node build + Nginx alpine) com CSP estrito"
```

---

## Task 8: docker-compose.dev.yml — Postgres + MinIO locais

**Files:**
- Create: `docker-compose.dev.yml`

- [ ] **Step 1: Criar `docker-compose.dev.yml`**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: condominio-postgres-dev
    environment:
      POSTGRES_DB: gestor_condominio
      POSTGRES_USER: condominio
      POSTGRES_PASSWORD: condominio_dev
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U condominio -d gestor_condominio"]
      interval: 5s
      timeout: 5s
      retries: 10

  minio:
    image: minio/minio:RELEASE.2024-10-13T13-34-11Z
    container_name: condominio-minio-dev
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: condominio
      MINIO_ROOT_PASSWORD: condominio_dev
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/data
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres-data:
  minio-data:
```

- [ ] **Step 2: Subir serviços e verificar**

```bash
docker compose -f docker-compose.dev.yml up -d
sleep 10
docker compose -f docker-compose.dev.yml ps
```

Expected: ambos serviços `running (healthy)`.

- [ ] **Step 3: Testar Postgres**

```bash
docker exec condominio-postgres-dev psql -U condominio -d gestor_condominio -c "SELECT version();"
```

Expected: versão PostgreSQL 16.x.

- [ ] **Step 4: Testar MinIO**

```bash
curl -sI http://localhost:9000/minio/health/live
```

Expected: `200 OK`.

Console MinIO em http://localhost:9001 (login `condominio` / `condominio_dev`).

- [ ] **Step 5: Parar serviços**

```bash
docker compose -f docker-compose.dev.yml down
```

- [ ] **Step 6: Commit**

```bash
git add docker-compose.dev.yml
git commit -m "feat(infra): docker-compose.dev.yml com Postgres 16 e MinIO para desenvolvimento local"
```

---

## Task 9: Deploy configs — Dokploy compose, env templates, observabilidade

**Files:**
- Create: `deploy/README.md`
- Create: `deploy/dokploy-backend.env.example`
- Create: `deploy/dokploy-frontend.env.example`
- Create: `deploy/dokploy-minio-compose.yml`
- Create: `deploy/dokploy-observability-compose.yml`
- Create: `deploy/prometheus/prometheus.yml`
- Create: `deploy/prometheus/alerts.yml`
- Create: `deploy/alertmanager/alertmanager.yml`
- Create: `deploy/grafana/provisioning/datasources/datasource.yml`

- [ ] **Step 1: Criar `deploy/dokploy-backend.env.example`**

```bash
# Spring profile (prod ou hml)
SPRING_PROFILES_ACTIVE=prod

# Admin inicial (gerado pelo seed Flyway no Plano 2)
APP_ADMIN_EMAIL=paulobof@gmail.com
APP_ADMIN_NAME=Paulo
APP_ADMIN_INITIAL_PASSWORD=trocar-no-primeiro-login

# Seguranca de senha (Plano 2)
APP_PASSWORD_PEPPER=GERAR_BASE64_32_BYTES
APP_BCRYPT_STRENGTH=12

# JWT (Plano 2)
APP_JWT_KEYS=v1:GERAR_BASE64_32_BYTES
APP_JWT_ACTIVE_KID=v1
APP_JWT_ISSUER=gestor-condominio
APP_JWT_AUDIENCE=gestor-condominio-web
APP_JWT_ACCESS_TTL=PT15M
APP_JWT_REFRESH_TTL=P7D

# CORS / Cookie
APP_CORS_ALLOWED_ORIGINS=https://app.helbor.exemplo
APP_COOKIE_DOMAIN=helbor.exemplo
APP_COOKIE_SECURE=true

# Postgres (Dokploy resolve nome do servico)
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=gestor_condominio
POSTGRES_USER=condominio
POSTGRES_PASSWORD=GERAR_SENHA_FORTE

# MinIO (compose)
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=condominio
MINIO_SECRET_KEY=GERAR_SENHA_FORTE
MINIO_BUCKET_PROOFS=residence-proofs
MINIO_BUCKET_CLASSIFIEDS=classifieds
MINIO_BUCKET_RECOMMENDATIONS=recommendations
MINIO_PRESIGNED_TTL_PROOFS_SECONDS=300
MINIO_PRESIGNED_TTL_PHOTOS_SECONDS=600

# WhatsApp Bot (Plano 2)
APP_WHATSAPP_WEBHOOK_URL=https://bot.paulobof.com.br/send-message
APP_WHATSAPP_HMAC_KEYS=v1:GERAR_BASE64_32_BYTES
APP_WHATSAPP_HMAC_ACTIVE_KID=v1
APP_WHATSAPP_TIMEOUT_MS=5000

# Reset de senha
APP_PASSWORD_RESET_TTL=PT30M
APP_PASSWORD_RESET_BASE_URL=https://app.helbor.exemplo/reset

# LGPD
APP_DPO_EMAIL=privacidade@helbor.exemplo
APP_CONTROLLER_NAME=Condominio HELBOR TRILOGY HOME
APP_CONTROLLER_CNPJ=00.000.000/0001-00
APP_PROOF_RETENTION_DAYS=180

# Alertas
APP_ALERT_EMAIL=paulobof@gmail.com

# Observabilidade
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus
```

- [ ] **Step 2: Criar `deploy/dokploy-frontend.env.example`**

```bash
# Build arg consumido pelo Vite
VITE_API_BASE_URL=https://api.helbor.exemplo
```

- [ ] **Step 3: Criar `deploy/dokploy-minio-compose.yml`**

```yaml
services:
  minio:
    image: minio/minio:RELEASE.2024-10-13T13-34-11Z
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    volumes:
      - minio-data:/data
    ports:
      - "9000"
      - "9001"
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 30s
      timeout: 5s
      retries: 3

volumes:
  minio-data:
```

- [ ] **Step 4: Criar `deploy/dokploy-observability-compose.yml`**

```yaml
services:
  prometheus:
    image: prom/prometheus:v2.54.1
    command:
      - --config.file=/etc/prometheus/prometheus.yml
      - --storage.tsdb.path=/prometheus
      - --storage.tsdb.retention.time=14d
      - --web.enable-lifecycle
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ./prometheus/alerts.yml:/etc/prometheus/alerts.yml:ro
      - prometheus-data:/prometheus
    ports:
      - "9090"

  alertmanager:
    image: prom/alertmanager:v0.27.0
    command:
      - --config.file=/etc/alertmanager/alertmanager.yml
    volumes:
      - ./alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
    ports:
      - "9093"

  grafana:
    image: grafana/grafana:11.3.0
    environment:
      GF_SECURITY_ADMIN_USER: ${GRAFANA_ADMIN_USER:-admin}
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-trocar}
      GF_USERS_ALLOW_SIGN_UP: "false"
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - grafana-data:/var/lib/grafana
    ports:
      - "3000"

volumes:
  prometheus-data:
  grafana-data:
```

- [ ] **Step 5: Criar `deploy/prometheus/prometheus.yml`**

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - alertmanager:9093

rule_files:
  - /etc/prometheus/alerts.yml

scrape_configs:
  - job_name: 'backend'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['backend:8080']
    relabel_configs:
      - source_labels: [__address__]
        target_label: service
        replacement: gestor-condominio-backend
```

- [ ] **Step 6: Criar `deploy/prometheus/alerts.yml`**

```yaml
groups:
  - name: gestor-condominio
    interval: 1m
    rules:
      - alert: BackendDown
        expr: up{job="backend"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Backend gestor-condominio fora do ar"

      - alert: HighLoginFailureRate
        expr: increase(auth_login_failure_total[5m]) > 50
        labels:
          severity: warning
        annotations:
          summary: "Mais de 50 falhas de login em 5 min"

      - alert: WhatsAppDeliveryDegraded
        expr: |
          (increase(whatsapp_send_success_total[1h]) /
           clamp_min(increase(whatsapp_send_attempt_total[1h]), 1)) < 0.8
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Taxa de sucesso do WhatsApp < 80% em 1h"
```

- [ ] **Step 7: Criar `deploy/alertmanager/alertmanager.yml`**

```yaml
global:
  resolve_timeout: 5m

route:
  receiver: 'email-admin'
  group_by: ['alertname']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  routes:
    - match:
        severity: critical
      receiver: 'email-admin'
      repeat_interval: 1h

receivers:
  - name: 'email-admin'
    email_configs:
      - to: 'paulobof@gmail.com'
        from: 'alerts@helbor.exemplo'
        smarthost: 'smtp.exemplo:587'
        auth_username: 'alerts@helbor.exemplo'
        auth_password: 'CHANGEME'
        require_tls: true
```

⚠ Configurar SMTP real ou trocar por receiver Slack/webhook conforme decisão final.

- [ ] **Step 8: Criar `deploy/grafana/provisioning/datasources/datasource.yml`**

```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
```

- [ ] **Step 9: Criar `deploy/README.md`**

```markdown
# Deploy — gestor-condominio

Configurações para Dokploy em `panel.paulobof.com.br`. Dois *environments*:

- **prod** — `app.helbor.exemplo` / `api.helbor.exemplo`.
- **hml** — `hml.app.helbor.exemplo` / `hml.api.helbor.exemplo`.

## Serviços por environment

| Serviço | Tipo Dokploy | Build path | Notas |
|---|---|---|---|
| backend | application | `./backend` | Dockerfile multi-stage |
| frontend | application | `./frontend` | Dockerfile + nginx |
| postgres | postgres | (gerado) | Backup diário configurar |
| minio | compose | `./deploy/dokploy-minio-compose.yml` | Buckets criados pelo backend |
| observabilidade | compose | `./deploy/dokploy-observability-compose.yml` | Prometheus + Grafana + Alertmanager — apenas em **prod** |

## Variáveis de ambiente

Templates em `dokploy-backend.env.example` e `dokploy-frontend.env.example`. Copiar valores para o painel Dokploy de cada serviço.

**Geração de segredos:**

```bash
# Pepper, JWT, HMAC do WhatsApp — 32 bytes base64
openssl rand -base64 32
```

## Ordem de deploy

1. Postgres (já provisionado).
2. MinIO (compose).
3. Backend (espera Postgres e MinIO em UP).
4. Frontend.
5. Observabilidade (só em prod, último).

## Promoção HML → Prod

Workflow GitHub Actions `promote-to-prod.yml` aciona webhook do environment **prod** após:
- Soak ≥ 30 min em HML.
- Aprovação manual no GitHub Environments.

## Backup

- **Postgres**: cron diário no host: `pg_dump | gzip | mc cp - minio-backups/postgres/<date>.sql.gz`. Retenção 7 dias + 1 mensal.
- **MinIO**: cron diário `mc mirror minio/<bucket> minio-backups/<bucket>`.
- **Restore drill**: ver `docs/runbooks/restore-postgres.md` (criado em Plano 2).
```

- [ ] **Step 10: Commit**

```bash
git add deploy/
git commit -m "chore(deploy): configs Dokploy (env templates, compose MinIO/Prometheus/Grafana/Alertmanager) e README"
```

---

## Task 10: CI workflow + Promote workflow

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/promote-to-prod.yml`

- [ ] **Step 1: Criar `.github/workflows/ci.yml`**

```yaml
name: ci

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
          cache: maven
      - name: Spotless check
        run: cd backend && ./mvnw -B -q spotless:check
      - name: Tests
        run: cd backend && ./mvnw -B verify

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - name: Install
        run: cd frontend && npm ci
      - name: Lint
        run: cd frontend && npm run lint
      - name: Typecheck
        run: cd frontend && npm run typecheck
      - name: Tests
        run: cd frontend && npm test -- --run --passWithNoTests
      - name: Build
        run: cd frontend && npm run build

  deploy-hml:
    needs: [backend, frontend]
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment:
      name: hml
      url: https://hml.app.helbor.exemplo
    steps:
      - name: Trigger Dokploy HML deploy
        run: |
          curl -sf -X POST "${{ secrets.DOKPLOY_HML_WEBHOOK }}" \
            -H "Authorization: Bearer ${{ secrets.DOKPLOY_HML_TOKEN }}"
      - name: Wait for HML readiness
        run: |
          for i in $(seq 1 18); do
            if curl -fs https://hml.api.helbor.exemplo/actuator/health/readiness; then
              echo "HML está UP após ${i} tentativas."
              exit 0
            fi
            echo "Tentativa $i — aguardando 10s"
            sleep 10
          done
          echo "HML não ficou UP em 3 minutos."
          exit 1
      - name: Record deployment timestamp
        run: |
          mkdir -p deployments
          echo "${{ github.sha }} $(date -u +%FT%TZ)" >> deployments/hml.log
          echo "deployment_sha=${{ github.sha }}" >> "$GITHUB_OUTPUT"
```

- [ ] **Step 2: Criar `.github/workflows/promote-to-prod.yml`**

```yaml
name: promote-to-prod

on:
  workflow_dispatch:
    inputs:
      version:
        description: 'SHA ou tag a promover para produção'
        required: true
        type: string

permissions:
  contents: write

jobs:
  promote:
    runs-on: ubuntu-latest
    environment:
      name: production
      url: https://app.helbor.exemplo
    env:
      MIN_SOAK_MINUTES: 30
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ inputs.version }}

      - name: Verify soak time in HML
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          run_json=$(gh api -X GET \
            repos/${{ github.repository }}/actions/workflows/ci.yml/runs \
            -f branch=main -f event=push -f status=success --jq \
            '.workflow_runs | map(select(.head_sha == "${{ inputs.version }}")) | .[0]')
          if [ -z "$run_json" ] || [ "$run_json" = "null" ]; then
            echo "Versao ${{ inputs.version }} nao foi deployada em HML via CI."
            exit 1
          fi
          deployed_at=$(echo "$run_json" | jq -r '.updated_at')
          deployed_epoch=$(date -d "$deployed_at" +%s)
          now_epoch=$(date +%s)
          elapsed=$(( (now_epoch - deployed_epoch) / 60 ))
          echo "Versao em HML ha ${elapsed} min."
          if [ "$elapsed" -lt "$MIN_SOAK_MINUTES" ]; then
            echo "Soak insuficiente (min ${MIN_SOAK_MINUTES} min)."
            exit 1
          fi

      - name: Trigger Dokploy Prod deploy
        run: |
          curl -sf -X POST "${{ secrets.DOKPLOY_PROD_WEBHOOK }}" \
            -H "Authorization: Bearer ${{ secrets.DOKPLOY_PROD_TOKEN }}"

      - name: Wait for Prod readiness
        run: |
          for i in $(seq 1 18); do
            if curl -fs https://api.helbor.exemplo/actuator/health/readiness; then
              echo "Prod UP."
              exit 0
            fi
            sleep 10
          done
          exit 1

      - name: Tag release
        run: |
          tag="v$(date -u +%Y.%m.%d-%H%M)"
          git tag "$tag" "${{ inputs.version }}"
          git push origin "$tag"
```

- [ ] **Step 3: Validar sintaxe dos workflows**

```bash
ls .github/workflows/
```

Sintaxe é validada quando o push acontece. Para validação local opcional, instalar [`actionlint`](https://github.com/rhysd/actionlint/releases) e rodar:

```bash
actionlint .github/workflows/*.yml
```

- [ ] **Step 4: Commit**

```bash
git add .github/
git commit -m "ci: workflows ci.yml (HML auto-deploy) e promote-to-prod.yml (manual + soak time)"
```

---

## Task 11: README.md + CLAUDE.md

**Files:**
- Create: `README.md`
- Create: `CLAUDE.md`

- [ ] **Step 1: Criar `README.md`**

```markdown
# gestor-condominio

Sistema de gestão do **Condomínio HELBOR TRILOGY HOME** — 3 torres (A/B/C), andares 4–32, 522 unidades.

Monorepo com backend Spring Boot 3 + Java 21 e frontend Vite/React/TypeScript.

## Estrutura

- `backend/` — API Spring Boot 3.
- `frontend/` — SPA Vite + React.
- `deploy/` — configs Dokploy (Prod + HML).
- `docs/superpowers/` — specs e planos de implementação.

## Desenvolvimento local

Pré-requisitos: JDK 21, Node 22, Docker.

```bash
# Subir Postgres + MinIO locais
docker compose -f docker-compose.dev.yml up -d

# Backend
cd backend && ./mvnw spring-boot:run

# Frontend (outro terminal)
cd frontend && npm run dev
```

Frontend em http://localhost:5173 com proxy `/api` → backend `:8080`.

## Comandos

| Comando | Ação |
|---|---|
| `npm run test:backend` | Roda testes Maven |
| `npm run test:frontend` | Roda Vitest |
| `npm run lint:backend` | Spotless check |
| `npm run lint:frontend` | ESLint |
| `npm run format:backend` | Spotless apply |

## Workflow

**Trunk-Based Development**. Branch única `main`; feature branches ≤2 dias; PR ≤400 linhas. Push em `main` → deploy HML automático. Promoção a Prod via workflow manual `promote-to-prod.yml`.

## Documentação

Especificações em `docs/superpowers/specs/`. Planos em `docs/superpowers/plans/`. Convenções de código em `CLAUDE.md`.
```

- [ ] **Step 2: Criar `CLAUDE.md`**

```markdown
# CLAUDE.md — Convenções do projeto gestor-condominio

> Lido automaticamente por agentes de IA (Claude Code etc.) ao iniciar sessões neste repositório.

## Visão geral

Sistema de gestão do **HELBOR TRILOGY HOME**. Spec: `docs/superpowers/specs/2026-05-24-gestor-condominio-design.md`.

## Princípios obrigatórios

- **SOLID, KISS, STRIDE, POO** (domínio rico, não DTOs anêmicos).
- **TDD**: testes primeiro, depois implementação mínima.
- Português (UI), Inglês (código/dados).
- **Trunk-Based**: PR ≤400 linhas, branch ≤2 dias, feature flag para WIP.

## Soft delete

**Sempre** soft delete via `@SQLDelete` + `@SQLRestriction` (Hibernate 6). Hard delete **proibido**, exceto: `user_role`, `user_permission_grant_log`, `role_permission`, `*_opening_hours`, `recommendation_tag`, `sensitive_access_log`, `proof_access_log` (M:N puros e logs imutáveis).

Limitações: `@SQLRestriction` **não filtra** queries nativas nem `JdbcTemplate` — incluir `WHERE deleted_at IS NULL` manualmente. **Nunca** `CascadeType.REMOVE`.

## Lombok em entidades JPA

- **Proibido `@Data`** — causa `LazyInitializationException` e loops em relações.
- Usar `@Getter`, `@Setter(AccessLevel.PROTECTED)`, `@EqualsAndHashCode(of="id")`, `@ToString(onlyExplicitlyIncluded=true)`.

## Spring Security

- Autorização **por permission** com `@PreAuthorize("hasAuthority('<PERMISSION>')")`.
- **Proibido** `hasRole('STAFF')` — STAFF não tem permissions default; uso por role vaza acesso.

## Spring patterns

- `@Transactional` apenas em service; nunca em controller, nunca em entidade.
- Upload S3/MinIO **fora** da transação.
- Eventos: `@TransactionalEventListener(phase=AFTER_COMMIT) + @Async`.
- `AuditorAware` retorna `Optional.empty()` em contexto público.

## Logs

- JSON em prod/hml; texto em dev.
- **Nunca** PII em log (`full_name`, `email`, telefone, comprovante). Usar `LogSanitizer`.
- MDC populado por `MdcFilter`: `requestId`, `userId`, `unitId`, `clientIp`.

## Comunicação outbound

**Apenas via WhatsApp** através do bot do Paulo. **Nunca** e-mail.

## Uploads

- Compressão **sempre client-side** antes do envio.
- Comprovante de residência ≤5MB. Fotos ≤1MB.
- Magic-bytes check server-side obrigatório.

## Database

- Migrations Flyway sempre **backward-compatible** (expand/contract).
- Cabeçalho `-- flyway:transactional=true` quando compatível.
- Nunca rename/remove no mesmo migration que adiciona.
- `gen_random_uuid()` (pgcrypto) para PKs.

## Frontend

- npm (não yarn/pnpm/bun).
- shadcn/ui + Tailwind + Outfit + Work Sans.
- Mobile-first; touch targets ≥44px; WCAG AA.
- `date-fns-tz` com `America/Sao_Paulo` para qualquer "horário de funcionamento".
- Imagens via `browser-image-compression` antes do upload.

## Feature flags

`@Value("${app.feature.<nome>.enabled:false}")`. Padrão Prod=`false`. Mudança em prod **registrada em issue do GitHub** com `actor`, `flag`, `from`, `to`, `motivo`.

## HML

**Proibido** restaurar dump de prod em HML. HML usa seed sintético (`R__seed_hml_fake_*.sql` + `APP_SEED_FAKE_DATA=true`).

## Commits

Conventional Commits. Squash merge. Hooks: `pre-commit` (lint-staged) + `commit-msg` (commitlint) + `pre-push` (testes back+front). Não usar `--no-verify`.
```

- [ ] **Step 3: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: README e CLAUDE.md com convencoes do projeto"
```

---

## Task 12: Smoke test local end-to-end + push para o remote

**Files:** (nenhum)

- [ ] **Step 1: Subir tudo localmente**

```bash
docker compose -f docker-compose.dev.yml up -d
cd backend && ./mvnw spring-boot:run &
sleep 30
cd frontend && npm run dev &
sleep 10
```

- [ ] **Step 2: Smoke checks**

```bash
curl -sf http://localhost:8080/actuator/health | grep '"status":"UP"'
curl -sf http://localhost:5173 | grep -i "helbor"
curl -sf http://localhost:8080/actuator/prometheus | head -5
```

Expected: cada um produz output não-vazio.

- [ ] **Step 3: Encerrar processos locais**

```bash
# Encerrar backend e frontend (Ctrl+C nas janelas)
docker compose -f docker-compose.dev.yml down
```

- [ ] **Step 4: Configurar remote (se ainda não estiver)**

```bash
git remote -v
```

Se não houver `origin`:

```bash
git remote add origin git@github.com:paulobof/gestor-condominio.git
```

- [ ] **Step 5: Push**

```bash
git push -u origin main
```

Expected: pre-push hook roda testes back+front; ambos verdes; push aceito.

- [ ] **Step 6: Verificar CI**

Abrir https://github.com/paulobof/gestor-condominio/actions e confirmar que o workflow `ci` rodou e ficou verde.

⚠ Job `deploy-hml` falhará neste ponto porque os secrets `DOKPLOY_HML_WEBHOOK`, `DOKPLOY_HML_TOKEN`, `DOKPLOY_PROD_WEBHOOK`, `DOKPLOY_PROD_TOKEN` ainda não foram cadastrados. Isso é esperado e será resolvido na Task 13.

---

## Task 13: Configurar Dokploy (ação manual com checklist)

**Files:** (configuração no painel web — sem arquivos no repo)

⚠ Esta tarefa é executada no painel `panel.paulobof.com.br`. Cada item é um check humano. Anotar os resultados em `docs/runbooks/dokploy-setup.md` (criar arquivo a posteriori).

- [ ] **Step 1: Login no Dokploy** — confirmar acesso a `panel.paulobof.com.br`.

- [ ] **Step 2: Verificar environment `prod` existente**

Identificar (IDs já conhecidos do projeto `wTGVAmU9KA1zf02p2mse-`):
- Backend: `2_XeVTFMJHdlSxulXUb8t`
- Frontend: `MgQYN-8HQSnst0ayuCQkj`
- Postgres: `MlkOyRA-hKUI-bgnPkAcp`
- MinIO compose: `af8M8nbBi2Z_9xUdNHZG8`

- [ ] **Step 3: Configurar Backend (prod)**

- Source: GitHub `paulobof/gestor-condominio`, branch `main`, build path `./backend`, Dockerfile `Dockerfile`.
- Domain: `api.helbor.exemplo` (substituir pelo domínio real do usuário).
- Env vars: copiar de `deploy/dokploy-backend.env.example`, preencher segredos.
- Healthcheck: GET `/actuator/health/readiness`.
- Webhook deploy: gerar e anotar.

- [ ] **Step 4: Configurar Frontend (prod)**

- Build path `./frontend`, build arg `VITE_API_BASE_URL=https://api.helbor.exemplo`.
- Domain: `app.helbor.exemplo`.

- [ ] **Step 5: Configurar MinIO compose (prod)**

- Cole o conteúdo de `deploy/dokploy-minio-compose.yml`.
- Env vars: `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`.

- [ ] **Step 6: Configurar Postgres (prod)**

- Database `gestor_condominio`. Backup automático: ativar diário.

- [ ] **Step 7: Criar environment `hml`**

- Duplicar a estrutura: backend-hml, frontend-hml, postgres-hml, minio-hml.
- Domains: `hml.api.helbor.exemplo`, `hml.app.helbor.exemplo`.
- Env vars com prefixo HML; `SPRING_PROFILES_ACTIVE=hml`.
- Webhook deploy de cada serviço: anotar.

- [ ] **Step 8: Criar compose de observabilidade (apenas prod)**

- Source: pasta `deploy/dokploy-observability-compose.yml`.
- Env vars: `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`.
- Configurar domínio interno para Grafana atrás de auth.

- [ ] **Step 9: Cadastrar secrets no GitHub**

`Settings → Secrets and variables → Actions → New repository secret`:

- `DOKPLOY_HML_WEBHOOK` — URL do webhook de deploy do backend HML (Dokploy oferece um único webhook por serviço; se múltiplos, ver Step 10).
- `DOKPLOY_HML_TOKEN` — token bearer se aplicável.
- `DOKPLOY_PROD_WEBHOOK`, `DOKPLOY_PROD_TOKEN`.

- [ ] **Step 10: Avaliar deploy multi-serviço**

Se o Dokploy expõe 1 webhook por serviço, o workflow precisa chamar 2 webhooks (backend + frontend) por environment. Ajustar `ci.yml` se necessário (adicionar steps).

- [ ] **Step 11: Criar GitHub Environments**

- `Settings → Environments`:
  - `hml`: sem proteção.
  - `production`: required reviewers = `paulobof`.

- [ ] **Step 12: Documentar**

Criar `docs/runbooks/dokploy-setup.md` com:
- IDs dos serviços (mascarar tokens).
- URLs dos webhooks (mascarados).
- Procedimento de rotação de segredos.

Commit:

```bash
git add docs/runbooks/dokploy-setup.md
git commit -m "docs(runbook): documentar setup Dokploy (HML + Prod + Observabilidade)"
git push
```

---

## Task 14: Verificação final do deploy HML

**Files:** (nenhum)

- [ ] **Step 1: Trigger manual de deploy**

No painel Dokploy, "Redeploy" do backend-hml e frontend-hml.

- [ ] **Step 2: Verificar URLs**

```bash
curl -sf https://hml.api.helbor.exemplo/actuator/health
curl -sf https://hml.app.helbor.exemplo/ | grep -i "helbor"
```

Expected: `{"status":"UP"}`; HTML do frontend.

- [ ] **Step 3: Verificar logs JSON**

No painel Dokploy → Backend HML → Logs. Confirmar formato JSON com campo `"requestId"`.

- [ ] **Step 4: Verificar Prometheus scrape (apenas em prod, mas validar config)**

Se observabilidade já estiver subida em prod (mesmo sem dados ainda): http://grafana-prod/dashboards.

- [ ] **Step 5: Fazer um pequeno push para acionar o workflow CI**

```bash
echo "" >> README.md   # whitespace change só pra acionar
git commit -am "ci: smoke do pipeline HML"
git push
```

Expected: CI verde; `deploy-hml` aciona webhook; smoke `wait for HML readiness` passa.

- [ ] **Step 6: Tag final do Foundation**

```bash
git tag v0.1.0-foundation
git push origin v0.1.0-foundation
```

Expected: tag criada; Foundation oficialmente entregue.

---

## Critérios de aceite do Plano 1

- [ ] `docker compose -f docker-compose.dev.yml up -d` sobe Postgres + MinIO healthy.
- [ ] `cd backend && ./mvnw spring-boot:run` responde `UP` em `/actuator/health`.
- [ ] `cd frontend && npm run dev` exibe "HELBOR TRILOGY HOME" em http://localhost:5173.
- [ ] `npm run test:backend` e `npm run test:frontend` ambos verdes.
- [ ] `git push` aciona pre-push (testes rodam).
- [ ] Commit fora do Conventional Commits é rejeitado pelo `commit-msg`.
- [ ] CI no GitHub Actions: jobs `backend` e `frontend` verdes; `deploy-hml` aciona o webhook Dokploy.
- [ ] `https://hml.api.helbor.exemplo/actuator/health` retorna `UP`.
- [ ] `https://hml.app.helbor.exemplo/` exibe a página inicial.
- [ ] Logs do backend em HML aparecem em JSON com `requestId`.
- [ ] Tag `v0.1.0-foundation` no remote.

---

## Próximo plano

**Plano 2 — Auth + Users + Units + LGPD core**: schema base (user×master/membros, unit×522, RBAC com permissions, refresh_token, password_history, password_reset_token, user_email, consent_document, sensitive_access_log), fluxo de registro do master com comprovante, login, refresh em cookie HttpOnly, reset de senha via WhatsApp, política de senha rigorosa, cadastro de membros pelo master, endpoints LGPD de export/anonymize/processing-activities.
