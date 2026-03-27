# ============================================================================
# Multi-stage Dockerfile for the Products Service
#
# Stage 1 (build): compiles the module using Maven and produces the fat-jar.
# Stage 2 (runtime): copies only the jar into a slim JRE image.
#
# Why multi-stage?
#   - The build stage needs the full JDK + Maven to compile.
#   - The runtime stage only needs a JRE to run the compiled jar.
#   - Separating them keeps the production image small and secure.
# ============================================================================

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Copy Maven wrapper and pom.xml first — Docker layer-caches dependency
# downloads separately from source code, speeding up iterative rebuilds.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download all dependencies (cached as long as pom.xml does not change).
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline

# Copy source and build the executable fat-jar.
# Tests are skipped because integration tests need a running gateway/subgraphs.
# Run tests separately with: ./mvnw clean test
COPY src src
RUN ./mvnw package -DskipTests

# ---- Runtime stage ----
# Use a JRE (not JDK) image to keep the final image smaller.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the fat-jar from the build stage.
COPY --from=build /workspace/app/target/*.jar app.jar

# The products-service listens on port 8081.
EXPOSE 8081

# Run the application. Exec form ensures the JVM receives OS signals (SIGTERM)
# directly, enabling graceful shutdown.
ENTRYPOINT ["java", "-jar", "app.jar"]
