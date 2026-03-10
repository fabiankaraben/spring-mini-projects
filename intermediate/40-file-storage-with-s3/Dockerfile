# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build
# Uses a full JDK image to compile the project and produce the fat JAR.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Copy Maven wrapper and POM first (enables Docker layer caching for dependencies)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download all dependencies (this layer is cached as long as pom.xml doesn't change)
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B

# Copy the full source tree and build the application JAR (skip tests during Docker build)
COPY src src
RUN ./mvnw package -DskipTests -B

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime
# Uses a smaller JRE-only image – no compiler tools needed at runtime.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the fat JAR produced in the build stage
COPY --from=build /workspace/app/target/*.jar app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
