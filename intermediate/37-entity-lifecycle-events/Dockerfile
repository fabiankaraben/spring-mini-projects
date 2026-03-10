# ============================================================================
# Multi-stage Dockerfile for the Entity Lifecycle Events Spring Boot application
#
# Stage 1 (build): compiles the project and produces the fat-jar using Maven.
# Stage 2 (runtime): copies only the jar into a slim JRE image, keeping the
#                    final image as small as possible.
# ============================================================================

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Copy Maven wrapper and pom.xml first.
# Docker caches each layer separately — by copying dependency descriptors before
# source code, the expensive `dependency:go-offline` layer is only re-run when
# pom.xml changes, not on every source-code change.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download all dependencies (cached unless pom.xml changes)
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline

# Copy the application source code and build the executable fat-jar.
# Tests are skipped here because they require a running Docker daemon (Testcontainers).
COPY src src
RUN ./mvnw package -DskipTests

# ---- Runtime stage ----
# Use a JRE (not JDK) image to keep the final image smaller — we only need to
# run the compiled jar, not compile Java source code.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the fat-jar produced in the build stage
COPY --from=build /workspace/app/target/*.jar app.jar

# The Spring Boot application listens on port 8080
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
