# Backend image. Two stages so the runtime carries a JRE and a jar, not a JDK,
# a Maven repository and the source tree.
#
# Tests are not run here: the integration tests start PostgreSQL through
# Testcontainers, which needs a Docker daemon the build does not have. Run
# `./mvnw test` before building an image.

# A plain JDK, not the maven image: the wrapper pins the Maven version by checksum, so
# the image build uses exactly the Maven every developer uses. A pre-installed Maven in
# the base image only gets in the wrapper's way.
FROM eclipse-temurin:21-jdk AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build

# Dependencies resolve in their own layer, so a source change does not re-download them.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar


FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# curl is here only so the container has a real health check.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 fapp

COPY --from=build /build/app.jar /app/app.jar
USER 10001

EXPOSE 8080

# MaxRAMPercentage lets the JVM size its heap from the container limit rather than
# from the host, which is what makes a memory limit on the container meaningful.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS "http://localhost:${FAPP_SERVER_PORT:-8080}/api/health" || exit 1
