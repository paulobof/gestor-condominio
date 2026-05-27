# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src ./src
RUN ./mvnw -q -B clean package -DskipTests
# TESTE DEFINITIVO TEMPORARIO: exit 1 incondicional.
# Se o deploy AINDA der "done", o Dokploy nao esta buildando este Dockerfile (H1 checkout stale).
# Se der "error", o Dokploy usa este Dockerfile (logo H2 container/imagem nao trocados). Remover apos.
RUN echo "FORCED_FAIL_PROBE_afe7f23" && exit 1

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
