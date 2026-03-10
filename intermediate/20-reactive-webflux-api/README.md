# Reactive WebFlux API

A Spring Boot mini-project demonstrating **non-blocking reactive endpoints** using **Spring WebFlux** and Project Reactor's `Mono`/`Flux` types, backed by the **MongoDB reactive driver**.

---

## What is this project?

Traditional Spring MVC uses a **thread-per-request** model: one thread blocks while waiting for a database response. Under high load this exhausts the thread pool.

Spring WebFlux uses an **event-loop** model (Netty): a small number of threads handle thousands of concurrent requests by never blocking. Instead, operations return `Mono<T>` (0 or 1 result) or `Flux<T>` (0..N results) — lazy publishers that execute only when subscribed to.

This project implements a simple **Article management API** to demonstrate:

| Concept | Where to look |
|---|---|
| Non-blocking REST endpoints | `ArticleController.java` |
| Reactive service logic with operators (`flatMap`, `map`, `switchIfEmpty`) | `ArticleService.java` |
| `ReactiveMongoRepository` with derived + `@Query` methods | `ArticleRepository.java` |
| Reactive error handling returning `Mono<ResponseEntity>` | `GlobalExceptionHandler.java` |
| Unit tests with `StepVerifier` (reactor-test) | `ArticleServiceTest.java` |
| Unit tests for domain entity | `ArticleTest.java` |
| Integration tests with `WebTestClient` + Testcontainers | `ArticleIntegrationTest.java` |

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | Included via Maven Wrapper (`./mvnw`) |
| Docker | Required (for MongoDB and Docker Compose) |
| Docker Compose | V2 (`docker compose` command) |

---

## Project structure

```
src/
├── main/
│   ├── java/com/example/reactivewebfluxapi/
│   │   ├── ReactiveWebfluxApiApplication.java  ← entry point
│   │   ├── controller/
│   │   │   └── ArticleController.java          ← HTTP endpoints (Mono/Flux returns)
│   │   ├── domain/
│   │   │   └── Article.java                    ← MongoDB document entity
│   │   ├── dto/
│   │   │   └── ArticleRequest.java             ← validated request body DTO
│   │   ├── exception/
│   │   │   └── GlobalExceptionHandler.java     ← centralised error handling
│   │   ├── repository/
│   │   │   └── ArticleRepository.java          ← ReactiveMongoRepository
│   │   └── service/
│   │       └── ArticleService.java             ← reactive business logic
│   └── resources/
│       └── application.yml
└── test/
    ├── java/com/example/reactivewebfluxapi/
    │   ├── ArticleIntegrationTest.java          ← WebTestClient + Testcontainers
    │   ├── domain/
    │   │   └── ArticleTest.java                 ← entity unit tests
    │   └── service/
    │       └── ArticleServiceTest.java          ← service unit tests (StepVerifier)
    └── resources/
        ├── application-test.yml
        ├── docker-java.properties
        └── testcontainers.properties
```

---

## Running with Docker Compose (recommended)

Docker Compose starts both MongoDB and the Spring Boot app (Netty server).

```bash
# Build the image and start all services
docker compose up --build

# Run in background
docker compose up --build -d

# Follow logs
docker compose logs -f app

# Stop and remove containers
docker compose down

# Stop and remove containers + data volume
docker compose down -v
```

The API will be available at **http://localhost:8080**.

---

## Running locally (without Docker)

Requires a running MongoDB instance on `localhost:27017`.

```bash
# Start MongoDB only via Docker Compose
docker compose up mongodb -d

# Run the application
./mvnw spring-boot:run
```

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/articles` | List all articles |
| GET | `/api/articles/{id}` | Get article by ID |
| GET | `/api/articles/category/{category}` | Articles by category |
| GET | `/api/articles/search?keyword=` | Search by title (case-insensitive) |
| GET | `/api/articles/published` | Published articles only |
| GET | `/api/articles/author/{author}` | Articles by author |
| GET | `/api/articles/author/{author}/count` | Count articles by author |
| POST | `/api/articles` | Create article |
| PUT | `/api/articles/{id}` | Update article (full replacement) |
| DELETE | `/api/articles/{id}` | Delete article |

---

## curl Examples

### Create an article

```bash
curl -s -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Getting Started with Spring WebFlux",
    "content": "Spring WebFlux is a reactive-stack web framework...",
    "author": "Jane Doe",
    "category": "technology",
    "published": true
  }' | jq .
```

### List all articles

```bash
curl -s http://localhost:8080/api/articles | jq .
```

### Get article by ID

```bash
curl -s http://localhost:8080/api/articles/<ID> | jq .
```

### Search articles by title keyword

```bash
curl -s "http://localhost:8080/api/articles/search?keyword=webflux" | jq .
```

### Get articles by category

```bash
curl -s http://localhost:8080/api/articles/category/technology | jq .
```

### Get published articles only

```bash
curl -s http://localhost:8080/api/articles/published | jq .
```

### Get articles by author

```bash
curl -s "http://localhost:8080/api/articles/author/Jane%20Doe" | jq .
```

### Count articles by author

```bash
curl -s "http://localhost:8080/api/articles/author/Jane%20Doe/count"
```

### Update an article

```bash
curl -s -X PUT http://localhost:8080/api/articles/<ID> \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Updated Title",
    "content": "Updated content body.",
    "author": "Jane Doe",
    "category": "technology",
    "published": true
  }' | jq .
```

### Delete an article

```bash
curl -s -X DELETE http://localhost:8080/api/articles/<ID> -w "%{http_code}\n"
# Returns 204 No Content on success, 404 if not found
```

### Validation error example (blank title → 400)

```bash
curl -s -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{"title":"","content":"Body","author":"Author","category":"tech","published":true}' | jq .
```

---

## Running the tests

### Requirements

- Docker must be running (Testcontainers spins up a real MongoDB container)

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests

```bash
./mvnw test -Dtest="ArticleTest,ArticleServiceTest"
```

### Run only integration tests

```bash
./mvnw test -Dtest="ArticleIntegrationTest"
```

### What each test class covers

| Test class | Type | Framework | What it verifies |
|---|---|---|---|
| `ArticleTest` | Unit | JUnit 5 | Entity constructor, setters, timestamp initialisation |
| `ArticleServiceTest` | Unit | JUnit 5 + Mockito + StepVerifier | Service reactive pipelines with mocked repository |
| `ArticleIntegrationTest` | Integration | JUnit 5 + Testcontainers + WebTestClient | Full HTTP stack against a real MongoDB container |

### StepVerifier (reactor-test)

`StepVerifier` is the standard tool for testing `Mono`/`Flux` pipelines without blocking the test thread manually. It subscribes to a publisher and asserts step-by-step:

```java
StepVerifier.create(articleService.findAll())
    .expectNextMatches(a -> "Technology".equals(a.getCategory()))
    .verifyComplete();
```

### WebTestClient

`WebTestClient` is the reactive-aware HTTP test client used in integration tests. It drives real HTTP requests against the running Netty server:

```java
webTestClient.post()
    .uri("/api/articles")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(request)
    .exchange()
    .expectStatus().isCreated()
    .expectBody(Article.class)
    .value(saved -> assertThat(saved.getId()).isNotNull());
```

---

## Key concepts demonstrated

### Mono and Flux

| Type | Emits | Use case |
|---|---|---|
| `Mono<T>` | 0 or 1 item | Find by ID, create, update, delete |
| `Flux<T>` | 0..N items | Find all, find by category/author |

### Reactive operators used

| Operator | Purpose |
|---|---|
| `map` | Synchronous transformation of each item |
| `flatMap` | Transform each item into a new publisher, then flatten |
| `switchIfEmpty` | Emit a fallback publisher when upstream is empty (used for 404 responses) |
| `thenReturn` | After a `Mono<Void>` completes, emit a value |
| `filter` | Pass items downstream only when a predicate is true |

### Why non-blocking matters

With Spring MVC (blocking), under 1000 concurrent requests each waiting 100ms for MongoDB, you need ≥1000 threads. With WebFlux (non-blocking), the same load can be handled by a handful of event-loop threads — dramatically reducing memory and context-switching overhead.
