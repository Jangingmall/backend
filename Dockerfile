FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /workspace
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle .
RUN ./gradlew dependencies --no-daemon -q || true
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-noble
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

COPY --from=builder /workspace/build/libs/*.jar app.jar

USER app

EXPOSE 8080 9090

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
