# 07 - Environment Variable Config

## Description
This is a basic Spring Boot backend mini-project that reads and exposes configuration values from environment variables and properties files. It is an educational project that demonstrates how to effectively manage application configuration using the `@Value` annotation to inject specific properties, as well as accessing properties programmatically through the Spring `Environment` bean. Being able to read environment variables properly is crucial for running apps as 12-factor microservices.

## Requirements
*   **Java**: 21 or higher
*   **Dependency Manager**: Maven
*   **Spring Boot**: 3.4.x
*   **Testing**: JUnit 5, Mockito and Spring's Sliced Integration Testing with `@WebMvcTest`.

## Usage

First, you can run the application directly using the Maven Wrapper. To properly test environment variables, you can prepend them before running the server command, which maps to Spring properties via Relaxed Binding syntax.

```bash
# Provide environment variables on startup
APP_NAME="Production App" APP_VERSION="3.0.0" ENV_MODE="production" ./mvnw spring-boot:run
```

Or just run it with default fallback values (defined inside the code or inside `application.properties`):

```bash
./mvnw spring-boot:run
```

### Endpoints and Curl Examples

Once the server is running (usually on port 8080), verify the responses using `curl`:

**Fetch the configuration properties mapping:**

```bash
curl -X GET http://localhost:8080/api/config
```

**Expected JSON Response (When running with explicitly defined environment variables):**
```json
{
  "appName": "Production App",
  "appVersion": "3.0.0",
  "envMode": "production"
}
```

**Expected JSON Response (When running with defaults):**
```json
{
  "appName": "Local Environment Variable App",
  "appVersion": "1.0.0",
  "envMode": "development"
}
```

## Running the Tests
This project contains sliced integration tests that verify endpoints using injected environment attributes as `properties` arguments to the test context. Run them with:

```bash
./mvnw test
```
