# ============================================================================
# Multi-stage Dockerfile for the Order Service
# ============================================================================
# Stage 1 — Build
# Uses the full JDK to compile and package the application.
# Tests are skipped here because they require Docker (Testcontainers) and
# should run in CI/CD, not inside the Docker image build.
# ============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper first (layer-cached until wrapper changes)
COPY mvnw .
COPY .mvn .mvn

# Copy POM and download dependencies (layer-cached until pom.xml changes)
COPY pom.xml .
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -q

# Copy source code and build
COPY src src
RUN ./mvnw package -DskipTests -q

# ============================================================================
# Stage 2 — Runtime
# Copies only the fat JAR into a minimal JRE image.
# ============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
