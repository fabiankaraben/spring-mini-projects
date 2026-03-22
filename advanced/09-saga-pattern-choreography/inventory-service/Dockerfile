# ============================================================================
# Multi-stage Dockerfile for the Inventory Service
# ============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn

COPY pom.xml .
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -q

COPY src src
RUN ./mvnw package -DskipTests -q

# ============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
