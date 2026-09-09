# syntax=docker/dockerfile:1.7
# Built from source inside the image: the previous Dockerfile copied a jar that had to
# already exist on the build host, so the image could not be reproduced from the repository
# alone and inherited whatever that host happened to have compiled.
# Build context is backend/, because this module depends on the reactor parent and on
# platform-security.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl api-gateway -am -DskipTests package \
    && cp api-gateway/target/api-gateway-*.jar /workspace/app.jar \
    && java -Djarmode=layertools -jar /workspace/app.jar extract --destination /workspace/layers

FROM eclipse-temurin:21-jre-jammy AS runtime
# An unprivileged user: a container process that does not need root should not have it.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app

# Spring Boot layers, ordered least- to most-frequently changed, so a code change does not
# invalidate the dependency layer.
COPY --from=build --chown=app:app /workspace/layers/dependencies/ ./
COPY --from=build --chown=app:app /workspace/layers/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/layers/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/layers/application/ ./

USER 10001:10001
EXPOSE 8080
# Heap follows the container memory limit instead of the host's, and an out-of-memory
# error kills the process so the orchestrator can replace it rather than limping on.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
