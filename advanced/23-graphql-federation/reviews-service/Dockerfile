# ============================================================================
# Multi-stage Dockerfile for the Reviews Service
#
# Stage 1 (build): compiles the module using Maven and produces the fat-jar.
# Stage 2 (runtime): copies only the jar into a slim JRE image.
# ============================================================================

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Copy Maven wrapper and pom.xml first for dependency layer caching.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline

# Copy source and build the executable fat-jar (tests skipped in Docker build).
COPY src src
RUN ./mvnw package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /workspace/app/target/*.jar app.jar

# The reviews-service listens on port 8082.
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
