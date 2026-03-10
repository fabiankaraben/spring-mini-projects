# JPA Auditing

A Spring Boot mini-project demonstrating how to automatically populate `createdAt` and `updatedAt` timestamps on JPA entities using Spring Data's auditing infrastructure — zero manual timestamp code in service or repository layers.

## What Is JPA Auditing?

JPA Auditing is a Spring Data feature that intercepts JPA lifecycle callbacks (`@PrePersist` and `@PreUpdate`) to automatically fill in audit-related fields on your entities. The key components are:

| Component | Role |
|---|---|
| `@EnableJpaAuditing` | Activates the auditing infrastructure in the Spring context |
| `@EntityListeners(AuditingEntityListener.class)` | Registers the listener on the entity class hierarchy |
| `@CreatedDate` | Marks the field set once on INSERT |
| `@LastModifiedDate` | Marks the field refreshed on every INSERT and UPDATE |

## Project Structure

```
src/
├── main/java/com/example/jpaauditing/
│   ├── JpaAuditingApplication.java       # Spring Boot entry point
│   ├── config/
│   │   └── JpaAuditingConfig.java        # @EnableJpaAuditing configuration
│   ├── entity/
│   │   ├── Auditable.java                # @MappedSuperclass with @CreatedDate / @LastModifiedDate
│   │   └── Article.java                  # JPA entity extending Auditable
│   ├── repository/
│   │   └── ArticleRepository.java        # Spring Data JPA repository
│   ├── dto/
│   │   ├── ArticleRequest.java           # Inbound DTO with Bean Validation
│   │   └── ArticleResponse.java          # Outbound DTO exposing audit timestamps
│   ├── service/
│   │   └── ArticleService.java           # Business logic layer
│   ├── controller/
│   │   └── ArticleController.java        # REST controller (CRUD endpoints)
│   └── exception/
│       ├── ArticleNotFoundException.java # 404 domain exception
│       └── GlobalExceptionHandler.java   # @RestControllerAdvice error handler
└── test/java/com/example/jpaauditing/
    ├── service/
    │   └── ArticleServiceTest.java               # Unit tests (Mockito, no DB)
    └── repository/
        └── ArticleRepositoryIntegrationTest.java # Integration tests (Testcontainers + real PostgreSQL)
```

## Requirements

- **Java 21+**
- **Maven 3.9+** (or use the included Maven Wrapper `./mvnw`)
- **Docker** and **Docker Compose** (Docker Desktop 4.x+ recommended)

## Running the Application

The application requires a PostgreSQL database. The full stack (app + database) is managed by Docker Compose.

### Start with Docker Compose

```bash
docker compose up --build
```

This command:
1. Builds the Spring Boot application Docker image (multi-stage build, no local Java needed).
2. Starts a `postgres:16-alpine` container with a health check.
3. Starts the application container once PostgreSQL is healthy.
4. Hibernate creates the `articles` table automatically on first run (`ddl-auto: update`).

The API is available at `http://localhost:8080`.

### Stop and remove containers

```bash
docker compose down
```

To also remove the persistent database volume:

```bash
docker compose down -v
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/articles` | List all articles |
| `GET` | `/api/articles?author={name}` | List articles by author |
| `GET` | `/api/articles/{id}` | Get article by ID |
| `POST` | `/api/articles` | Create a new article |
| `PUT` | `/api/articles/{id}` | Update an existing article |
| `DELETE` | `/api/articles/{id}` | Delete an article |

## curl Examples

### Create an article

```bash
curl -X POST http://localhost:8080/api/articles \
     -H "Content-Type: application/json" \
     -d '{
       "title": "Introduction to JPA Auditing",
       "content": "JPA Auditing automatically populates createdAt and updatedAt timestamps.",
       "author": "Alice"
     }'
```

Response — notice `createdAt` and `updatedAt` are automatically set:

```json
{
  "id" : 1,
  "title" : "Introduction to JPA Auditing",
  "content" : "JPA Auditing automatically populates createdAt and updatedAt timestamps.",
  "author" : "Alice",
  "createdAt" : "2024-06-01T10:00:00.123456Z",
  "updatedAt" : "2024-06-01T10:00:00.123456Z"
}
```

### List all articles

```bash
curl http://localhost:8080/api/articles
```

### List articles by author

```bash
curl "http://localhost:8080/api/articles?author=Alice"
```

### Get article by ID

```bash
curl http://localhost:8080/api/articles/1
```

### Update an article

```bash
curl -X PUT http://localhost:8080/api/articles/1 \
     -H "Content-Type: application/json" \
     -d '{
       "title": "JPA Auditing — Updated",
       "content": "After this update, updatedAt will be refreshed automatically.",
       "author": "Alice"
     }'
```

Response — `createdAt` is unchanged, `updatedAt` is refreshed:

```json
{
  "id" : 1,
  "title" : "JPA Auditing — Updated",
  "content" : "After this update, updatedAt will be refreshed automatically.",
  "author" : "Alice",
  "createdAt" : "2024-06-01T10:00:00.123456Z",
  "updatedAt" : "2024-06-01T10:05:30.654321Z"
}
```

### Delete an article

```bash
curl -X DELETE http://localhost:8080/api/articles/1
```

### Validation error example

```bash
curl -X POST http://localhost:8080/api/articles \
     -H "Content-Type: application/json" \
     -d '{"title": "", "content": "Some content", "author": "Bob"}'
```

Returns HTTP 400:

```json
{
  "status" : 400,
  "detail" : "Validation failed",
  "errors" : {
    "title" : "Title must not be blank"
  }
}
```

## Running the Tests

Tests require Docker to be running (Testcontainers spins up a real PostgreSQL container for integration tests).

### Run all tests

```bash
./mvnw clean test
```

### Test categories

| Test class | Type | What it tests |
|---|---|---|
| `ArticleServiceTest` | **Unit test** | Service logic using Mockito mocks — no DB, no Spring context |
| `ArticleRepositoryIntegrationTest` | **Integration test** | Real JPA Auditing behaviour against a Testcontainers PostgreSQL instance |

### Key integration test assertions

The `ArticleRepositoryIntegrationTest` verifies the core auditing guarantees:

- `createdAt` is **non-null** after INSERT — the `AuditingEntityListener` populated it automatically.
- `updatedAt` is **non-null** after INSERT and is **strictly after** `createdAt` after UPDATE.
- `createdAt` is **unchanged** after UPDATE (`updatable = false` column constraint).

## How It Works — Technical Detail

```
POST /api/articles
      │
      ▼
ArticleController.create()
      │  calls
      ▼
ArticleService.create()
      │  new Article(title, content, author)
      │  articleRepository.save(article)
      │        │
      │        ▼
      │   JPA @PrePersist fires
      │   AuditingEntityListener intercepts
      │   → article.createdAt = Instant.now()
      │   → article.updatedAt = Instant.now()
      │        │
      │        ▼
      │   Hibernate INSERT INTO articles (...)
      │
      ▼
ArticleResponse.from(saved)   ← createdAt and updatedAt are already set here
```

On **UPDATE**:
```
PUT /api/articles/{id}
      │
      ▼
ArticleService.update()
      │  article.setTitle(...)
      │  articleRepository.save(article)
      │        │
      │        ▼
      │   JPA @PreUpdate fires
      │   AuditingEntityListener intercepts
      │   → article.updatedAt = Instant.now()   ← only updatedAt changes
      │   → article.createdAt unchanged          ← updatable = false
      │        │
      │        ▼
      │   Hibernate UPDATE articles SET ...
```

## Docker Compose Details

The `docker-compose.yml` defines two services:

- **`postgres`** — `postgres:16-alpine` with a named volume (`postgres_data`) for data persistence and a health check so the app only starts after the database is ready.
- **`app`** — built from the `Dockerfile` using a multi-stage build; environment variables override `application.yml` datasource settings to use Docker Compose's internal DNS.

### Useful Docker commands

```bash
# View live logs
docker compose logs -f

# View only app logs
docker compose logs -f app

# Connect to PostgreSQL directly
docker exec -it jpa-auditing-db psql -U articles_user -d articlesdb

# Rebuild the app image after code changes
docker compose up --build app
```
