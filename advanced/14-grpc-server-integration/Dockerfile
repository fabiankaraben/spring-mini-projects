# ============================================================================
# Multi-stage Dockerfile for the gRPC Server Integration application.
#
# Stage 1 (build): compiles the project and produces the fat-jar using Maven.
#                  Also runs protoc to generate Java code from .proto files.
# Stage 2 (runtime): copies only the jar into a slim JRE image, keeping the
#                    final image as small as possible.
#
# Why multi-stage?
#   - The build stage needs the full JDK + Maven + protoc to compile.
#   - The runtime stage only needs a JRE to execute the compiled jar.
#   - Separating them prevents build tools and source code from ending up
#     in the production image.
#
# Port layout:
#   8080 — Spring Boot HTTP (Actuator /health, /info)
#   9090 — gRPC server (ProductCatalogService, Netty)
# ============================================================================

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Copy Maven wrapper and pom.xml first.
# Docker caches each layer separately — by copying dependency descriptors before
# the source code, the expensive `dependency:go-offline` layer is only invalidated
# when pom.xml changes, not on every source-code change.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download all dependencies into the local Maven cache.
# The protobuf-maven-plugin and os-maven-plugin will also be resolved here.
# This layer is cached as long as pom.xml does not change.
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline

# Copy the application source code (including .proto files).
# The protobuf-maven-plugin invokes protoc here, generating Java stubs from
# src/main/proto/product_catalog.proto before the main compilation starts.
COPY src src
RUN ./mvnw package -DskipTests

# ---- Runtime stage ----
# Use a JRE (not JDK) image to keep the final image smaller.
# The generated protobuf/gRPC stubs are already compiled into the fat-jar.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the fat-jar produced in the build stage.
COPY --from=build /workspace/app/target/*.jar app.jar

# Expose both ports:
#   8080 — HTTP (Spring Actuator health/info endpoints)
#   9090 — gRPC (Netty, ProductCatalogService)
EXPOSE 8080
EXPOSE 9090

# Run the application.
# Using exec form (JSON array) so the JVM receives OS signals (SIGTERM) directly,
# enabling graceful shutdown of both the HTTP and gRPC servers.
ENTRYPOINT ["java", "-jar", "app.jar"]
