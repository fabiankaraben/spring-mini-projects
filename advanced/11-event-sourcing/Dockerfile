# ============================================================================
# Dockerfile — Event Sourcing with Axon Framework
# ============================================================================
# Multi-stage build:
#   Stage 1 (builder): compiles the application with Maven
#   Stage 2 (runtime): runs the compiled fat-jar on a minimal JRE image
#
# Multi-stage keeps the final image small (~200 MB) by discarding the
# Maven cache and JDK tooling after compilation.
# ============================================================================

# ----------------------------------------------------------------------------
# Stage 1: Build
# Uses the official Eclipse Temurin JDK 21 image with Maven pre-installed.
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy Maven wrapper and POM first — Docker caches this layer until they change.
# This means subsequent builds only re-download dependencies when pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./

# Download all dependencies (cached as a separate layer for faster rebuilds)
RUN ./mvnw dependency:go-offline -q

# Copy the rest of the source code
COPY src/ src/

# Package the application, skipping tests (tests run via Maven, not Docker build)
RUN ./mvnw package -DskipTests -q

# ----------------------------------------------------------------------------
# Stage 2: Runtime
# Uses the minimal JRE image (no JDK tooling) for a smaller production image.
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Create a non-root user for security best practice
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

# Copy the fat-jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# JVM flags:
#   -XX:+UseContainerSupport       — makes the JVM respect Docker's CPU/memory limits
#   -XX:MaxRAMPercentage=75.0      — use 75% of the container's RAM as heap
#   -Djava.security.egd=...        — speeds up startup on Linux containers
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]
