# Base image containing a JDK for building
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy the wrapper scripts and the pom
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Give execute permission for the wrapper and download dependencies
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B

# Copy the application source code
COPY src src

# Package the application skipping tests (tests are run via docker-compose usually, or in CI)
RUN ./mvnw package -DskipTests

# Run stage based on a lighter JRE image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Expose port and start
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
