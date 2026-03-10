# Docker Compose Support

A Spring Boot 3.1+ backend that demonstrates **Spring Boot's first-class Docker Compose integration** for automatic dev-service lifecycle management.

When the `spring-boot-docker-compose` dependency is on the classpath and a `compose.yml` file is present, Spring Boot automatically starts the declared services (PostgreSQL) before the application boots and stops them when it shuts down — no manual `docker compose up` required during development.

---

## What this mini-project demonstrates

| Feature | Description |
|---------|-------------|
| `spring-boot-docker-compose` | Auto-starts and stops Docker services on app start/stop |
| `compose.yml` | Dev-only compose file consumed exclusively by Spring Boot |
| `docker-compose.yml` | Full production compose (app + database together) |
| Automatic datasource wiring | Spring Boot reads container credentials and overrides `DataSource` properties |
| JPA + PostgreSQL | Book catalogue with CRUD, search, and filtering |
| Testcontainers | Integration tests use a real PostgreSQL container, independent of the dev compose setup |

### How it works (Spring Boot Docker Compose Support)

```
./mvnw spring-boot:run
        │
        ▼
Spring Boot detects compose.yml
        │
        ▼
Runs: docker compose -f compose.yml up
        │
        ▼
PostgreSQL container starts
        │
        ▼
Spring Boot reads container host:port + credentials
        │
        ▼
Overrides spring.datasource.* automatically
        │
        ▼
Application starts and connects to PostgreSQL
        │
        ▼ (on shutdown)
Runs: docker compose -f compose.yml stop
```

---

## Requirements

- **Java 21+**
- **Maven 3.9+** (or use the included Maven Wrapper)
- **Docker Desktop** (for development mode and running via Docker Compose)

---

## Project structure

```
35-docker-compose-support/
├── compose.yml                          # Dev-only compose file (used by spring-boot-docker-compose)
├── docker-compose.yml                   # Production compose (app + PostgreSQL)
├── Dockerfile                           # Multi-stage Docker build for the app
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/example/dockercomposesupport/
│   │   │   ├── DockerComposeSupportApplication.java   # Entry point
│   │   │   ├── controller/BookController.java         # REST endpoints
│   │   │   ├── service/BookService.java               # Business logic
│   │   │   ├── repository/BookRepository.java         # Spring Data JPA
│   │   │   ├── domain/Book.java                       # JPA entity
│   │   │   ├── dto/BookRequest.java                   # Incoming DTO
│   │   │   ├── dto/BookResponse.java                  # Outgoing DTO
│   │   │   └── exception/                             # Custom exceptions + global handler
│   │   └── resources/application.yml
│   └── test/
│       ├── java/com/example/dockercomposesupport/
│       │   ├── unit/BookServiceTest.java              # Unit tests (Mockito, no DB)
│       │   └── integration/BookIntegrationTest.java   # Integration tests (Testcontainers)
│       └── resources/
│           ├── docker-java.properties
│           └── testcontainers.properties
```

---

## Running in development mode (Spring Boot Docker Compose integration)

This is the key feature. Docker starts automatically — no manual commands needed.

```bash
./mvnw spring-boot:run
```

Spring Boot will:
1. Detect `compose.yml` in the project root.
2. Run `docker compose up` to start PostgreSQL.
3. Override the datasource connection to point to the container.
4. Start the application.
5. On `Ctrl+C`: run `docker compose stop` to stop PostgreSQL.

The API will be available at `http://localhost:8080`.

---

## Running in Docker (full production deployment)

The `docker-compose.yml` bundles the Spring Boot application and PostgreSQL together.

### Start

```bash
docker compose up --build
```

### Start in background

```bash
docker compose up --build -d
```

### Stop

```bash
docker compose down
```

### Stop and remove all data (volumes)

```bash
docker compose down -v
```

---

## API Reference — curl examples

### Create a book

```bash
curl -s -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Clean Code",
    "author": "Robert C. Martin",
    "isbn": "978-0132350884",
    "publicationYear": 2008,
    "description": "A handbook of agile software craftsmanship"
  }' | jq
```

### List all books

```bash
curl -s http://localhost:8080/api/books | jq
```

### Filter by author (partial, case-insensitive)

```bash
curl -s "http://localhost:8080/api/books?author=Martin" | jq
```

### Filter by publication year

```bash
curl -s "http://localhost:8080/api/books?year=2008" | jq
```

### Full-text keyword search (title + author + description)

```bash
curl -s "http://localhost:8080/api/books?q=agile" | jq
```

### Get a single book by ID

```bash
curl -s http://localhost:8080/api/books/1 | jq
```

### Update a book (full replacement)

```bash
curl -s -X PUT http://localhost:8080/api/books/1 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Clean Code (2nd Edition)",
    "author": "Robert C. Martin",
    "isbn": "978-0132350884",
    "publicationYear": 2024,
    "description": "Updated edition with modern practices"
  }' | jq
```

### Delete a book

```bash
curl -s -X DELETE http://localhost:8080/api/books/1 -w "%{http_code}\n"
```

### Error responses

**400 Bad Request (validation failure):**
```bash
curl -s -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{"title":"","author":"Author","isbn":"ISBN"}' | jq
```

**409 Conflict (duplicate title or ISBN):**
```bash
curl -s -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{"title":"Clean Code","author":"Other","isbn":"OTHER-ISBN"}' | jq
```

**404 Not Found:**
```bash
curl -s http://localhost:8080/api/books/9999 | jq
```

---

## Running the tests

### All tests (unit + integration)

```bash
./mvnw clean test
```

### Unit tests only (no Docker needed)

```bash
./mvnw test -Dtest="**/unit/**"
```

### Integration tests only (Docker must be running)

```bash
./mvnw test -Dtest="**/integration/**"
```

---

## Test overview

### Unit tests — `BookServiceTest`

- **No Spring context, no database** — uses Mockito mocks only.
- Tests `BookService` in complete isolation: create, read, update, delete, search.
- Verifies that `DuplicateBookException` is thrown on title/ISBN conflicts.
- Verifies that `BookNotFoundException` is thrown for missing IDs.
- Checks that duplicate-check is skipped when the field hasn't changed (update path).

### Integration tests — `BookIntegrationTest`

- **Full Spring context** with a real PostgreSQL database via Testcontainers.
- Uses `MockMvc` for HTTP-level testing (no real network socket required).
- Spring Boot Docker Compose integration is **disabled** during tests; Testcontainers provides the database instead.
- Covers all CRUD endpoints, filtering, search, validation errors, conflict errors, and a full round-trip scenario.

---

## Key files explained

### `compose.yml` (dev services file)

Used **exclusively** by Spring Boot's Docker Compose integration during local development. Contains only the PostgreSQL service. Spring Boot auto-discovers this file and manages its lifecycle.

### `docker-compose.yml` (full production deployment)

Used for deploying the entire application stack via `docker compose up --build`. Contains both the Spring Boot application (built from `Dockerfile`) and PostgreSQL with a named volume for data persistence.

### `application.yml`

```yaml
spring:
  docker:
    compose:
      enabled: true
      file: compose.yml
      lifecycle-management: start-and-stop
      skip:
        in-tests: true   # Testcontainers handles DB during tests
```

The `skip.in-tests: true` option is critical: it prevents Spring Boot from trying to start `compose.yml` during test runs, where Testcontainers is already providing a PostgreSQL instance.
