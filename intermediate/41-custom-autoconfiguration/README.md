# Custom Auto-Configuration — Spring Boot Starter

A Spring Boot backend that demonstrates how to build a **custom Spring Boot Starter** with auto-configured beans, `@ConfigurationProperties`, conditional registration, and an Actuator health indicator — all the core mechanisms that official starters (like `spring-boot-starter-data-redis`) use internally.

---

## What This Mini-Project Demonstrates

| Concept | Where to look |
|---|---|
| `@AutoConfiguration` class | `GreetingAutoConfiguration.java` |
| `AutoConfiguration.imports` registration file | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `@ConfigurationProperties` binding | `GreetingProperties.java` |
| `@ConditionalOnMissingBean` back-off | `GreetingAutoConfiguration#greetingService(...)` |
| `@ConditionalOnProperty` opt-out | `@ConditionalOnProperty(prefix="greeting", name="enabled", matchIfMissing=true)` |
| `@ConditionalOnClass` optional dependency | `GreetingAutoConfiguration#greetingHealthIndicator(...)` |
| Custom `HealthIndicator` for Actuator | `GreetingHealthIndicator.java` |
| `ApplicationContextRunner` auto-config tests | `GreetingAutoConfigurationTest.java` |
| Testcontainers integration tests | `GreetingLogRepositoryIntegrationTest.java` |

### How a Custom Starter Works (Spring Boot 3.x)

```
Your JAR / classpath
└── META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
        └── com.example.autoconfiguration.starter.GreetingAutoConfiguration  ← listed here

SpringApplication starts
  → AutoConfigurationImportSelector reads every .imports file on the classpath
  → For each class, evaluates @Conditional* annotations
  → If conditions pass → beans are registered (e.g. GreetingService)
  → If greeting.enabled=false → entire class is skipped
  → If app declares its own GreetingService @Bean → auto-configured one is skipped (@ConditionalOnMissingBean)
```

---

## Requirements

- **Java 21** or higher
- **Maven** (provided via Maven Wrapper — no local install needed)
- **Docker** and **Docker Compose** (required to run the full application stack)

---

## Project Structure

```
src/main/java/com/example/autoconfiguration/
├── CustomAutoConfigurationApplication.java   ← Spring Boot entry point
│
├── starter/                                  ← Custom starter library code
│   ├── GreetingProperties.java               ← @ConfigurationProperties (greeting.*)
│   ├── GreetingService.java                  ← The auto-configured service bean
│   ├── GreetingHealthIndicator.java          ← Actuator health contributor
│   └── GreetingAutoConfiguration.java        ← @AutoConfiguration class
│
├── entity/
│   └── GreetingLog.java                      ← JPA entity (persists each greeting)
├── repository/
│   └── GreetingLogRepository.java            ← Spring Data JPA repository
├── service/
│   └── GreetingLogService.java               ← App service (uses GreetingService)
├── controller/
│   └── GreetingController.java               ← REST API
├── dto/
│   ├── GreetingRequest.java
│   └── GreetingResponse.java
└── exception/
    ├── GreetingLogNotFoundException.java
    └── GlobalExceptionHandler.java

src/main/resources/
├── application.yml
└── META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  ← IMPORTANT

src/test/java/com/example/autoconfiguration/
├── starter/
│   ├── GreetingServiceTest.java              ← Unit tests (no Spring context)
│   └── GreetingAutoConfigurationTest.java    ← Auto-config tests (ApplicationContextRunner)
└── repository/
    └── GreetingLogRepositoryIntegrationTest.java  ← Integration tests (Testcontainers)
```

---

## Running with Docker Compose

The entire stack (PostgreSQL database + Spring Boot application) is managed by Docker Compose. No local Java or database installation is required.

### Start the full stack

```bash
docker compose up --build
```

The first run downloads images and builds the fat-jar inside Docker. Subsequent runs use cached layers and are much faster.

### Stop the stack

```bash
docker compose down
```

### Stop and remove all data (volumes)

```bash
docker compose down -v
```

### Rebuild after code changes

```bash
docker compose up --build
```

The application will be available at `http://localhost:8080`.

---

## Configuring the Custom Starter

The starter exposes four properties under the `greeting.*` prefix, all with sensible defaults:

```yaml
greeting:
  enabled: true          # false → disables the starter entirely (no GreetingService bean)
  prefix: "Hello"        # word/phrase before the name
  suffix: "!"            # punctuation after the name
  default-name: "World"  # used when no name is provided
```

To override them via Docker Compose, add environment variables to `docker-compose.yml`:

```yaml
environment:
  - GREETING_PREFIX=Hi
  - GREETING_SUFFIX=.
  - GREETING_DEFAULT_NAME=Spring
  - GREETING_ENABLED=true
```

---

## API Usage (curl Examples)

### Generate a greeting (with a name)

```bash
curl -s -X POST http://localhost:8080/api/greetings \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice"}' | jq
```

Expected response (`201 Created`):
```json
{
  "id": 1,
  "name": "Alice",
  "message": "Hello, Alice!",
  "createdAt": "2024-01-01T10:00:00Z"
}
```

### Generate a greeting (using the default name)

```bash
curl -s -X POST http://localhost:8080/api/greetings \
  -H "Content-Type: application/json" \
  -d '{}' | jq
```

Expected response (`201 Created`):
```json
{
  "id": 2,
  "name": "World",
  "message": "Hello, World!",
  "createdAt": "2024-01-01T10:01:00Z"
}
```

### List all greeting logs (newest first)

```bash
curl -s http://localhost:8080/api/greetings | jq
```

### Get a greeting log by ID

```bash
curl -s http://localhost:8080/api/greetings/1 | jq
```

### Search greeting logs by name

```bash
curl -s "http://localhost:8080/api/greetings/search?name=Alice" | jq
```

### Delete a greeting log

```bash
curl -s -X DELETE http://localhost:8080/api/greetings/1
```

Returns `204 No Content` on success, `404 Not Found` if the ID does not exist.

### View current auto-configuration properties

This diagnostic endpoint shows exactly what the custom starter has been configured with:

```bash
curl -s http://localhost:8080/api/greetings/config | jq
```

Expected response:
```json
{
  "enabled": true,
  "prefix": "Hello",
  "suffix": "!",
  "defaultName": "World",
  "sampleGreeting": "Hello, World!"
}
```

### Check Actuator health (includes custom GreetingHealthIndicator)

```bash
curl -s http://localhost:8080/actuator/health | jq
```

Expected response (shows the custom `greeting` component contributed by the starter):
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "greeting": {
      "status": "UP",
      "details": {
        "configuration": "prefix='Hello', suffix='!', defaultName='World'",
        "sample": "Hello, World!"
      }
    },
    "ping": { "status": "UP" }
  }
}
```

---

## Running the Tests

Tests do **not** require a running database — Testcontainers starts a real PostgreSQL container automatically for integration tests.

### Run all tests

```bash
./mvnw clean test
```

### Test categories

| Test class | Type | What it verifies |
|---|---|---|
| `GreetingServiceTest` | Unit | `GreetingService.greet()` logic, null/blank fallback, custom properties |
| `GreetingAutoConfigurationTest` | Auto-config unit (no DB) | `@ConditionalOnMissingBean`, `@ConditionalOnProperty`, property binding, health indicator |
| `GreetingLogRepositoryIntegrationTest` | Integration (Testcontainers) | JPA repository CRUD, derived queries, ordering against real PostgreSQL |

The `GreetingAutoConfigurationTest` uses Spring Boot's `ApplicationContextRunner` — the standard tool for testing auto-configurations without a full application context. It verifies:

- The `GreetingService` bean is registered with defaults.
- `@ConditionalOnMissingBean` backs off when a user provides their own bean.
- `@ConditionalOnProperty(greeting.enabled=false)` disables the entire auto-configuration.
- `@ConfigurationProperties` correctly binds custom values from property overrides.
- The `GreetingHealthIndicator` is registered and reports `UP`.

---

## How the Auto-Configuration Discovery Works

Spring Boot 3.x replaced the `spring.factories` mechanism with the `.imports` file:

```
src/main/resources/
└── META-INF/
    └── spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

This file contains one line:
```
com.example.autoconfiguration.starter.GreetingAutoConfiguration
```

At startup, `AutoConfigurationImportSelector` reads **every** `AutoConfiguration.imports` file found in every JAR on the classpath, evaluates each class's `@Conditional*` annotations, and only registers the ones whose conditions pass.

In a real-world multi-module starter, this file (and the `starter/` package classes) would live in their own Maven module (e.g. `greeting-spring-boot-starter`) published as a separate JAR. Here they are co-located in the same module for simplicity.
