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
# integration tests require Docker-in-Docker to spin up Testcontainers.
COPY src src
RUN ./mvnw package -DskipTests

# ── Runtime stage ────────────────────────────────────────────────────────────
# Use a JRE-only image for the final stage to keep the image size small.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the built JAR from the build stage (multi-stage keeps image lean)
COPY --from=build /workspace/app/target/*.jar app.jar

# Expose port 8080 (Spring Boot / Netty default)
EXPOSE 8080

# Launch the Spring Boot application (Netty starts non-blocking event loop)
ENTRYPOINT ["java", "-jar", "app.jar"]
