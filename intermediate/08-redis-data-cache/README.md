# Redis Data Cache

A Spring Boot mini-project that demonstrates **method-level caching** with an external
Redis instance using Spring's cache abstraction annotations:
`@Cacheable`, `@CachePut`, and `@CacheEvict`.

The application exposes a simple Product catalogue REST API. The repository layer
includes a **simulated 200 ms delay** to make the performance difference between a
cache-miss and a cache-hit clearly observable.

---

## What This Project Demonstrates

| Concept | Where |
|---|---|
| `@EnableCaching` — activate Spring's cache AOP | `RedisDataCacheApplication` |
| `@Cacheable` — skip method body on cache hit | `ProductService.findById`, `findAll` |
| `@CachePut` — always update the cache after a write | `ProductService.update` |
| `@CacheEvict` — remove stale entries on delete/create | `ProductService.create`, `deleteById` |
| `@Caching` — combine multiple cache operations | `ProductService.update`, `deleteById` |
| Per-cache TTL and JSON serialisation | `CacheConfig` |
| Testcontainers Redis for integration tests | `ProductIntegrationTest` |

---

## Requirements

- **Java 21** or higher
- **Docker** (Docker Desktop 4+ or Docker Engine 24+) — needed to run the full stack
  via Docker Compose, and also needed to run the Testcontainers integration tests

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/redisdatacache/
│   │   ├── RedisDataCacheApplication.java   # Entry point + @EnableCaching
│   │   ├── config/
│   │   │   └── CacheConfig.java             # RedisCacheManager, JSON serialisation, TTLs
│   │   ├── controller/
│   │   │   └── ProductController.java       # REST endpoints
│   │   ├── domain/
│   │   │   └── Product.java                 # Domain model (Serializable)
│   │   ├── dto/
│   │   │   └── ProductRequest.java          # Request DTO with Bean Validation
│   │   ├── repository/
│   │   │   └── ProductRepository.java       # In-memory store with simulated delay
│   │   └── service/
│   │       └── ProductService.java          # Business logic + cache annotations
│   └── resources/
│       └── application.yml                  # App configuration
└── test/
    ├── java/com/example/redisdatacache/
    │   ├── ProductIntegrationTest.java       # Full integration tests (Testcontainers)
    │   ├── domain/
    │   │   └── ProductTest.java             # Domain model unit tests
    │   └── service/
    │       └── ProductServiceTest.java      # Service unit tests (Mockito)
    └── resources/
        ├── application-test.yml             # Test profile configuration
        ├── docker-java.properties           # Docker API version fix (Desktop 29+)
        └── testcontainers.properties        # Testcontainers Docker API config
```

---

## Running with Docker Compose (Recommended)

This is the simplest way to run the full stack (application + Redis) without installing
anything beyond Docker.

### 1. Start all services

```bash
docker compose up --build
```

This command:
1. Builds the Spring Boot application image using the `Dockerfile` (multi-stage build).
2. Pulls the `redis:7-alpine` image.
3. Starts Redis first (waits for its healthcheck to pass).
4. Starts the Spring Boot application connected to Redis.

### 2. Verify both services are running

```bash
docker compose ps
```

### 3. Stop all services

```bash
docker compose down
```

To also remove the Redis data volume (start fresh on next run):

```bash
docker compose down -v
```

---

## Running Locally (Without Docker Compose)

If you want to run the application outside Docker you still need a local Redis instance.

### Start Redis locally with Docker

```bash
docker run -d --name redis-local -p 6379:6379 redis:7-alpine
```

### Run the Spring Boot application

```bash
./mvnw spring-boot:run
```

---

## REST API

The API is available at `http://localhost:8080/api/products`.

### List all products

```bash
curl -s http://localhost:8080/api/products | jq
```

> **Tip:** Run this command twice in quick succession. The first call takes ~200 ms
> (cache miss, simulated data-store delay). The second call returns instantly (cache hit).

### Get a single product

```bash
curl -s http://localhost:8080/api/products/1 | jq
```

### Create a product

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mechanical Keyboard",
    "description": "TKL layout with Cherry MX Brown switches",
    "price": 129.99,
    "category": "electronics"
  }' | jq
```

### Update a product

```bash
curl -s -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop Pro 15 (Updated)",
    "description": "Now with 64 GB RAM",
    "price": 1499.99,
    "category": "electronics"
  }' | jq
```

### Delete a product

```bash
curl -s -X DELETE http://localhost:8080/api/products/1 -w "\nHTTP Status: %{http_code}\n"
```

---

## Observing the Cache in Action

### Watch Spring cache log messages

The application is configured with `logging.level.org.springframework.cache: DEBUG`.
When running locally, look for lines like:

```
DEBUG ... - Cache entry for key '1' found in cache 'products'
DEBUG ... - No cache entry for key '2' in cache 'products'
```

### Inspect Redis keys directly

While the application is running, connect to Redis and list the cached keys:

```bash
# Connect to the Redis container
docker exec -it redisdatacache-redis redis-cli

# List all cache keys
KEYS *

# Get the value of a specific key (JSON-encoded Product)
GET "products::1"
```

---

## Running the Tests

### Unit tests only (no Docker required)

```bash
./mvnw test -Dtest="ProductTest,ProductServiceTest"
```

### All tests (unit + integration)

Integration tests use **Testcontainers** to spin up a real Redis Docker container
automatically. Docker must be running.

```bash
./mvnw clean test
```

### Test coverage

| Test class | Type | What is tested |
|---|---|---|
| `ProductTest` | Unit | `Product` domain model (constructors, getters, setters, Serializable) |
| `ProductServiceTest` | Unit | `ProductService` business logic with a Mockito-mocked repository |
| `ProductIntegrationTest` | Integration | Full HTTP + AOP cache + real Redis (Testcontainers) |
