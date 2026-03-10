# Reactive MongoDB API

A backend built with **Spring Boot**, **Spring WebFlux**, and **Spring Data Reactive MongoDB**,
demonstrating a fully non-blocking stack from the HTTP layer all the way down to the database.

---

## What This Mini-Project Demonstrates

| Concept | Implementation |
|---|---|
| Non-blocking HTTP server | **Netty** via Spring WebFlux (replaces Tomcat) |
| Reactive programming model | **Project Reactor** — `Mono<T>` and `Flux<T>` |
| Non-blocking database I/O | **Reactive MongoDB driver** (MongoDB Reactive Streams) |
| Repository abstraction | `ReactiveMongoRepository` / `ReactiveCrudRepository` |
| Automatic auditing | `@CreatedDate` / `@LastModifiedDate` via `@EnableReactiveMongoAuditing` |
| MongoDB-specific features | Array fields (`genres`), `@Indexed`, `@Document`, `$regex`, `$gte/$lte` |
| Bean validation | Jakarta Validation on DTOs (`@NotBlank`, `@Min`, `@DecimalMin`) |
| Global error handling | `@RestControllerAdvice` with structured JSON error responses |
| Unit tests | **JUnit 5** + **Mockito** + **StepVerifier** (no Spring context, no Docker) |
| Integration tests | **Testcontainers** MongoDB 7 + **WebTestClient** (full HTTP stack) |
| Containerisation | Multi-stage **Dockerfile** + **Docker Compose** |

### Why Reactive MongoDB?

The traditional Spring Data MongoDB driver uses **blocking I/O** — every query blocks the
calling thread until MongoDB responds. The Reactive MongoDB driver returns `Publisher` types
(`Mono`/`Flux`) so **the calling thread is never blocked**, enabling much higher concurrency
with a small, fixed thread pool. Combined with WebFlux/Netty, this project achieves a fully
non-blocking pipeline from HTTP request to MongoDB wire and back.

---

## Requirements

- **Java 21** or higher
- **Docker** with **Docker Compose** (for running the full stack)
- **Docker Desktop 29+** or any Docker Engine with a MongoDB image available

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/reactivemongodbapi/
│   │   ├── ReactiveMongodbApiApplication.java  # Entry point
│   │   ├── config/
│   │   │   └── MongoConfig.java                # @EnableReactiveMongoAuditing
│   │   ├── domain/
│   │   │   └── Book.java                       # @Document entity (MongoDB collection)
│   │   ├── dto/
│   │   │   └── BookRequest.java                # Request DTO with Bean Validation
│   │   ├── repository/
│   │   │   └── BookRepository.java             # ReactiveMongoRepository + custom queries
│   │   ├── service/
│   │   │   └── BookService.java                # Business logic (reactive pipelines)
│   │   ├── controller/
│   │   │   └── BookController.java             # REST endpoints (Mono/Flux returns)
│   │   └── exception/
│   │       └── GlobalExceptionHandler.java     # @RestControllerAdvice
│   └── resources/
│       └── application.yml
└── test/
    ├── java/com/example/reactivemongodbapi/
    │   ├── BookIntegrationTest.java            # Full integration tests (Testcontainers)
    │   ├── domain/
    │   │   └── BookDomainTest.java             # Pure domain unit tests
    │   └── service/
    │       └── BookServiceTest.java            # Service unit tests (Mockito + StepVerifier)
    └── resources/
        ├── application-integration-test.yml
        ├── docker-java.properties              # Docker API version fix for Docker Desktop 29+
        └── testcontainers.properties
```

---

## Running with Docker Compose

This is the recommended way to run the project. Docker Compose starts both MongoDB and
the Spring Boot application with a single command.

### Start

```bash
docker compose up --build
```

This command:
1. Builds the Spring Boot application image using the multi-stage `Dockerfile`.
2. Starts a **MongoDB 7** container and waits for its health check to pass.
3. Starts the **Spring Boot / Netty** application container on port `8080`.

### Stop

```bash
docker compose down
```

### Stop and remove all data (wipe the MongoDB volume)

```bash
docker compose down -v
```

### Rebuild after code changes

```bash
docker compose up --build
```

---

## API Endpoints

Base URL: `http://localhost:8080/api/books`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/books` | List all books |
| `GET` | `/api/books/{id}` | Get a book by MongoDB ObjectId |
| `GET` | `/api/books/isbn/{isbn}` | Get a book by ISBN |
| `GET` | `/api/books/author/{author}` | List books by author |
| `GET` | `/api/books/available` | List all available books |
| `GET` | `/api/books/year/{year}` | List books by publication year |
| `GET` | `/api/books/genre/{genre}` | List books by genre (array field query) |
| `GET` | `/api/books/search?keyword=...` | Search titles (case-insensitive, regex) |
| `GET` | `/api/books/price-range?min=...&max=...` | Filter books by price range |
| `GET` | `/api/books/author/{author}/count` | Count books by author |
| `POST` | `/api/books` | Create a new book |
| `PUT` | `/api/books/{id}` | Update a book (full replacement) |
| `DELETE` | `/api/books/{id}` | Delete a book |

---

## curl Examples

### Create a book

```bash
curl -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Nineteen Eighty-Four",
    "author": "George Orwell",
    "isbn": "978-0-452-28423-4",
    "price": 12.99,
    "publishedYear": 1949,
    "genres": ["fiction", "dystopia"],
    "description": "A dystopian novel set in a totalitarian surveillance state.",
    "language": "English",
    "pageCount": 328,
    "available": true
  }'
```

### List all books

```bash
curl http://localhost:8080/api/books
```

### Get a book by ID

```bash
curl http://localhost:8080/api/books/<id>
```

### Get a book by ISBN

```bash
curl http://localhost:8080/api/books/isbn/978-0-452-28423-4
```

### List books by author

```bash
curl "http://localhost:8080/api/books/author/George%20Orwell"
```

### List available books

```bash
curl http://localhost:8080/api/books/available
```

### List books by year

```bash
curl http://localhost:8080/api/books/year/1949
```

### List books by genre

```bash
curl http://localhost:8080/api/books/genre/dystopia
```

### Search books by title keyword (case-insensitive)

```bash
curl "http://localhost:8080/api/books/search?keyword=eighty"
```

### Filter books by price range

```bash
curl "http://localhost:8080/api/books/price-range?min=10.00&max=20.00"
```

### Count books by author

```bash
curl "http://localhost:8080/api/books/author/George%20Orwell/count"
```

### Update a book

```bash
curl -X PUT http://localhost:8080/api/books/<id> \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Nineteen Eighty-Four — Revised Edition",
    "author": "George Orwell",
    "isbn": "978-0-452-28423-4",
    "price": 14.99,
    "publishedYear": 1949,
    "genres": ["fiction", "dystopia", "classic"],
    "description": "Updated description.",
    "language": "English",
    "pageCount": 340,
    "available": true
  }'
```

### Delete a book

```bash
curl -X DELETE http://localhost:8080/api/books/<id>
```

---

## Running the Tests

Tests require **Docker** to be running (for Testcontainers integration tests).

### Run all tests

```bash
./mvnw clean test
```

### Test categories

| Test class | Type | Dependencies |
|---|---|---|
| `BookDomainTest` | Unit — pure Java, no Spring, no Docker | None |
| `BookServiceTest` | Unit — Mockito mocks, no Spring, no Docker | None |
| `BookIntegrationTest` | Integration — Testcontainers MongoDB 7 | Docker |

### What the tests verify

- **`BookDomainTest`** — constructor correctness, field defaults, getter/setter contracts,
  MongoDB-specific behaviour (ObjectId starts as `null`, audit timestamps `null` before save,
  `genres` array handling).

- **`BookServiceTest`** — all service methods are tested with `StepVerifier` against a Mockito
  mock repository. Covers: `findAll`, `findById`, `findByIsbn`, `findByAuthor`, `findAvailable`,
  `findByPriceRange`, `searchByTitle`, `findByGenre`, `countByAuthor`, `create` (including
  duplicate ISBN rejection), `update` (found / not-found), `deleteById` (found / not-found).

- **`BookIntegrationTest`** — spins up a real MongoDB 7 container via Testcontainers and tests
  every HTTP endpoint with `WebTestClient`. Covers happy paths, 400/404/409 error cases, and
  service-layer persistence verification.

---

## Connecting to MongoDB Directly

When running via Docker Compose, you can connect to the MongoDB instance with `mongosh`:

```bash
docker exec -it reactivemongodb-mongodb mongosh \
  --username mongouser --password mongopass booksdb
```

Or from the host machine (port 27017 is exposed):

```bash
mongosh "mongodb://mongouser:mongopass@localhost:27017/booksdb"
```

Useful queries inside `mongosh`:

```javascript
// List all books
db.books.find().pretty()

// Find books by author
db.books.find({ author: "George Orwell" })

// Find books by genre (array field query)
db.books.find({ genres: "dystopia" })

// Count all documents
db.books.countDocuments()
```
