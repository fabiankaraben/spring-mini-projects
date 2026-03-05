# Stage 1: Build the application
# ================================
# Use the full JDK image to compile and package the application.
# The Maven Wrapper (mvnw) is used to avoid requiring Maven to be installed.
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy the Maven wrapper files first so the dependency cache layer is reused
# when only source code changes (not dependencies).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download all dependencies offline before copying source code.
# This Docker layer is cached as long as pom.xml doesn't change.
RUN ./mvnw dependency:go-offline

# Copy the rest of the application source code
COPY src ./src

# Package the application, skipping tests (tests run via Docker Compose
# or locally, not inside the build image).
RUN ./mvnw clean package -DskipTests

# Stage 2: Run the application
# ================================
# Use a smaller JRE-only image for the final runtime container.
# This significantly reduces the image size compared to keeping the JDK.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy only the packaged JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
