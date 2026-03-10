FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Copy the Maven wrapper and pom.xml first so that Docker can cache the
# dependency download layer. Dependencies are only re-downloaded when pom.xml
# changes, not on every source code change.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download all dependencies (fills the local Maven cache inside the image layer)
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline

# Copy application source and build the fat JAR, skipping tests because
# integration tests require a running PostgreSQL container (Testcontainers) which
# is not available during the Docker build phase.
COPY src src
RUN ./mvnw package -DskipTests

# ── Runtime stage ────────────────────────────────────────────────────────────
# Use a JRE-only image for the final stage to keep the image size small.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a dedicated directory for mounting external CSV files.
# In Docker Compose this is bind-mounted from the host:
#   volumes:
#     - ./data:/data
# The batch.csv-file-path property is set to "file:/data/employees.csv" in
# docker-compose.yml so Spring Batch reads the CSV from this mounted directory.
RUN mkdir -p /data

# Copy only the built JAR from the build stage (multi-stage keeps image lean)
COPY --from=build /workspace/app/target/*.jar app.jar

# Expose port 8080 (Spring Boot / Tomcat default)
EXPOSE 8080

# Launch the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
