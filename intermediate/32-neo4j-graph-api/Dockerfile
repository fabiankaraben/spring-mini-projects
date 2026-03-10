FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Copy maven wrapper and pom.xml first (these layers are cached separately)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download all dependencies offline (cached unless pom.xml changes)
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline

# Copy source code and build the fat-jar (skip tests — they require Docker/Testcontainers)
COPY src src
RUN ./mvnw package -DskipTests

# ---- Runtime stage (smaller final image) ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the built jar from the build stage
COPY --from=build /workspace/app/target/*.jar app.jar

# Neo4j Bolt port is 7687 (accessed internally by the app via Spring Data Neo4j)
# The app itself listens on port 8080
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
