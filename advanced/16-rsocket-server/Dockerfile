# ============================================================================
# Multi-stage Dockerfile for the RSocket Server application.
#
# Stage 1 (build): compiles the project and produces the fat-jar using Maven.
# Stage 2 (runtime): copies only the jar into a slim JRE image, keeping the
#                    final image as small as possible.
#
# Why multi-stage?
#   - The build stage needs the full JDK + Maven to compile.
#   - The runtime stage only needs a JRE to execute the compiled jar.
#   - Separating them prevents build tools and source code from ending up
#     in the production image, reducing the attack surface.
#
# Port layout:
#   7000 — RSocket TCP server (all four interaction model routes)
#   8080 — Spring Boot HTTP  (Actuator /health, /info)
# ============================================================================

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Copy Maven wrapper and pom.xml first.
# Docker caches each layer separately — by copying dependency descriptors before
# source code, the expensive `dependency:go-offline` layer is only invalidated
# when pom.xml changes, not on every source-code change.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download all dependencies into the local Maven cache.
# This layer is cached as long as pom.xml does not change.
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline

# Copy the application source code.
COPY src src

# Build the fat-jar, skipping tests (tests are run separately via ./mvnw clean test).
RUN ./mvnw package -DskipTests

# ---- Runtime stage ----
# Use a JRE (not JDK) image to keep the final image smaller.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the fat-jar produced in the build stage.
COPY --from=build /workspace/app/target/*.jar app.jar

# Expose both ports:
#   7000 — RSocket TCP (all interaction models)
#   8080 — HTTP (Spring Actuator health/info endpoints)
EXPOSE 7000
EXPOSE 8080

# Run the application.
# Using exec form (JSON array) so the JVM receives OS signals (SIGTERM) directly,
# enabling graceful shutdown of both the RSocket and HTTP servers.
ENTRYPOINT ["java", "-jar", "app.jar"]
