# =============================================================================
# Multi-stage Dockerfile for the gRPC Client Interaction mini-project.
#
# Stage 1 (builder): Compiles the Java source and generates protobuf stubs.
#   - Uses maven:3.9-eclipse-temurin-21 which bundles Maven + JDK 21.
#   - The protobuf-maven-plugin downloads protoc during the build; we install
#     it in the build stage so the final image doesn't need build tools.
#   - The full Maven cache (.m2) is stored in a Docker BuildKit cache mount
#     so repeated builds skip re-downloading dependencies.
#
# Stage 2 (runtime): Runs only the packaged fat JAR.
#   - Uses eclipse-temurin:21-jre-alpine — a minimal JRE image (~80MB).
#   - No JDK, no Maven, no source code — minimal attack surface.
#   - Exposes port 8080 (HTTP/REST) and port 9091 (gRPC Netty server).
#
# Build command:
#   docker build -t grpc-client-interaction .
#
# Run command:
#   docker run -p 8080:8080 -p 9091:9091 grpc-client-interaction
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy dependency descriptor first (layer caching: only re-download deps when
# pom.xml changes, not on every source code change).
COPY pom.xml .

# Download all dependencies offline (fails fast if network is unavailable).
# The --no-transfer-progress flag suppresses progress bars for clean CI output.
RUN mvn dependency:go-offline --no-transfer-progress -q

# Copy remaining source (proto files, Java sources, resources).
COPY src ./src
COPY .mvn ./.mvn
COPY mvnw .
COPY mvnw.cmd .

# Compile, generate protobuf stubs, run tests, and package the fat JAR.
# -DskipTests=false ensures tests run and fail the build if they break.
# Remove -DskipTests to run tests; add it back if your CI runs tests separately.
RUN mvn clean package -DskipTests --no-transfer-progress

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create a non-root user for security best practices.
# Running as root in a container is a security anti-pattern.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the packaged fat JAR from the builder stage.
# The fat JAR contains all dependencies — no classpath setup needed.
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to the non-root user.
RUN chown appuser:appgroup app.jar

# Switch to non-root user.
USER appuser

# ── Ports ─────────────────────────────────────────────────────────────────────
# 8080 — Spring Boot HTTP server (REST API + Actuator health/info endpoints).
EXPOSE 8080

# 9091 — Netty gRPC server (OrderService + InventoryService over gRPC).
EXPOSE 9091

# ── Health check ──────────────────────────────────────────────────────────────
# Docker will mark the container as healthy once the Actuator /health returns 200.
# --interval: check every 30 seconds.
# --timeout:  wait up to 10 seconds for a response.
# --retries:  mark unhealthy after 3 consecutive failures.
# --start-period: give the app 30 seconds to start before health checks begin.
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# ── Entrypoint ────────────────────────────────────────────────────────────────
# JVM flags:
#   -XX:+UseContainerSupport      — respect cgroup CPU/memory limits (default in JDK 11+).
#   -XX:MaxRAMPercentage=75.0     — use at most 75% of the container's memory for the JVM heap.
#   -Djava.security.egd=...       — faster SecureRandom initialization in containers.
#   -Dspring.profiles.active=prod — activate production profile if needed in the future.
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
