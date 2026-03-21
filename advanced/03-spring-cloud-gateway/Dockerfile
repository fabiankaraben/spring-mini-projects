# ============================================================================
# Multi-stage Dockerfile for the Spring Cloud Gateway application
#
# Stage 1 (build): compiles the project and produces the fat-jar using Maven.
# Stage 2 (runtime): copies only the jar into a slim JRE image, keeping the
#                    final image as small as possible.
#
# Why multi-stage?
#   - The build stage needs the full JDK and Maven to compile Java source code.
#   - The runtime stage only needs a JRE to run the compiled jar.
#   - Separating them prevents the JDK, Maven and source code from ending up in
#     the production image, reducing its size significantly.
# ============================================================================

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Copy Maven wrapper and pom.xml first.
# Docker caches each layer separately — by copying dependency descriptors before
# the source code, the expensive `dependency:go-offline` layer is only invalidated
# when pom.xml changes, not on every source-code change. This greatly speeds up
# iterative builds.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download all dependencies into the local Maven cache.
# This layer is cached as long as pom.xml does not change.
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline

# Copy the application source code and build the executable fat-jar.
# Tests are skipped here because integration tests require a running Docker daemon
# (Testcontainers), which is not available during the Docker image build step.
# Run tests separately with: ./mvnw clean test
COPY src src
RUN ./mvnw package -DskipTests

# ---- Runtime stage ----
# Use a JRE (not JDK) image to keep the final image smaller.
# We only need to run the compiled jar — no need for the Java compiler.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the fat-jar produced in the build stage.
# The COPY --from=build syntax copies from the named stage, not the host filesystem.
COPY --from=build /workspace/app/target/*.jar app.jar

# The API Gateway listens on port 8080.
# This is the single public-facing port that all client traffic enters through.
EXPOSE 8080

# Run the application.
# Using exec form (JSON array) rather than shell form to ensure the JVM receives
# OS signals (SIGTERM) directly, enabling graceful shutdown.
ENTRYPOINT ["java", "-jar", "app.jar"]
