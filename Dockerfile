FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /workspace
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle .
RUN ./gradlew dependencies --no-daemon -q || true
COPY src src
RUN ./gradlew bootJar --no-daemon -x test && \
    java -Djarmode=layertools -jar build/libs/*.jar extract --destination extracted

# ── CI path: pre-built layers extracted by GHA (skips Gradle inside Docker) ───
FROM eclipse-temurin:25-jre-noble AS ci
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
ARG EXTRACTED=build/extracted
COPY ${EXTRACTED}/dependencies ./
COPY ${EXTRACTED}/spring-boot-loader ./
COPY ${EXTRACTED}/snapshot-dependencies ./
COPY ${EXTRACTED}/application ./
USER app
EXPOSE 8080 9090
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "org.springframework.boot.loader.launch.JarLauncher"]

# ── Local path: full build inside Docker ──────────────────────────────────────
FROM eclipse-temurin:25-jre-noble AS local
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=builder /workspace/extracted/dependencies ./
COPY --from=builder /workspace/extracted/spring-boot-loader ./
COPY --from=builder /workspace/extracted/snapshot-dependencies ./
COPY --from=builder /workspace/extracted/application ./
USER app
EXPOSE 8080 9090
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "org.springframework.boot.loader.launch.JarLauncher"]
