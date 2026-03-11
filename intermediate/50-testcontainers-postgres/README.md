# Testcontainers Postgres

A Spring Boot mini-project demonstrating how to write **integration tests backed by a real PostgreSQL Docker container** using [Testcontainers](https://testcontainers.com/), alongside **unit tests for domain logic** using JUnit 5 and Mockito.

The application exposes a simple **Product REST API** (CRUD + search + stock/price filters). Its primary purpose is educational — showing the difference between unit tests (mock the database, run fast) and integration tests (real database, verify SQL behaviour).

---

## What You Will Learn

- **Unit tests** with JUnit 5 + Mockito: test the service layer in complete isolation (no database, no Spring context, millisecond execution).
- **Integration tests** with Testcontainers: Testcontainers automatically starts a real `postgres:16-alpine` Docker container before tests run and stops it afterwards — no manual setup required.
- `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` — minimal Spring JPA slice that skips the web layer.
- `@DynamicPropertySource` — how to bridge Testcontainers' random dynamic port into Spring's DataSource configuration.
- Custom Spring Data JPA derived query methods and `@Query` JPQL queries, all verified against a real database.

---

## Requirements

| Tool | Minimum Version |
|------|----------------|
| Java | 21 |
| Maven | 3.9+ (or use the included Maven Wrapper) |
| Docker | Desktop 4.x / Engine 24+ (must be running when executing tests) |

> **Docker must be running** when you execute `./mvnw test` because Testcontainers pulls and starts a PostgreSQL container automatically.

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/testcontainerspostgres/
│   │   ├── TestcontainersPostgresApplication.java  # Spring Boot entry point
│   │   ├── controller/
│   │   │   └── ProductController.java              # REST endpoints
│   │   ├── dto/
│   │   │   ├── ProductRequest.java                 # Incoming request DTO (validated)
│   │   │   └── ProductResponse.java                # Outgoing response DTO
│   │   ├── entity/
│   │   │   └── Product.java                        # JPA entity (mapped to "products" table)
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java         # @RestControllerAdvice — maps exceptions to HTTP
│   │   │   └── ProductNotFoundException.java       # Domain exception → HTTP 404
│   │   ├── repository/
│   │   │   └── ProductRepository.java              # Spring Data JPA repository + custom queries
│   │   └── service/
│   │       └── ProductService.java                 # Business logic layer
│   └── resources/
│       └── application.yml                         # DataSource, JPA, server config
└── test/
    ├── java/com/example/testcontainerspostgres/
    │   ├── repository/
    │   │   └── ProductRepositoryIntegrationTest.java  # Integration tests (real PostgreSQL via Testcontainers)
    │   └── service/
    │       └── ProductServiceTest.java                # Unit tests (Mockito, no database)
    └── resources/
        ├── docker-java.properties                     # Forces Docker API v1.44 (Docker Desktop 29+)
        └── testcontainers.properties                  # Complementary Testcontainers config
```

---

## Running the Application with Docker Compose

The full stack (Spring Boot app + PostgreSQL) is orchestrated with Docker Compose.

### Start the stack

```bash
docker compose up --build
```

This will:
1. Build the Spring Boot application image from the `Dockerfile` (multi-stage build).
2. Start a `postgres:16-alpine` container.
3. Wait for PostgreSQL to be healthy, then start the Spring Boot container.
4. Expose the API at `http://localhost:8080`.

### Start in detached mode (background)

```bash
docker compose up --build -d
```

### Stop the stack

```bash
docker compose down
```

### Stop and remove the database volume (wipes all data)

```bash
docker compose down -v
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/products` | List all products |
| `GET` | `/api/products/{id}` | Get product by ID |
| `GET` | `/api/products/search?keyword=...` | Search by name (partial, case-insensitive) |
| `GET` | `/api/products/in-stock` | List in-stock products (quantity > 0) |
| `GET` | `/api/products/price-range?min=...&max=...` | List products within a price range |
| `POST` | `/api/products` | Create a new product |
| `PUT` | `/api/products/{id}` | Update an existing product |
| `DELETE` | `/api/products/{id}` | Delete a product |

---

## curl Examples

> Make sure the stack is running (`docker compose up --build -d`) before executing these commands.

### Create a product

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop Pro",
    "description": "High-performance laptop",
    "price": 999.99,
    "stockQuantity": 10
  }' | jq
```

### Create another product

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Smartphone X",
    "description": "Latest Android flagship",
    "price": 499.99,
    "stockQuantity": 25
  }' | jq
```

### Create an out-of-stock product

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Limited Edition Watch",
    "description": "Sold out collector item",
    "price": 1299.99,
    "stockQuantity": 0
  }' | jq
```

### List all products

```bash
curl -s http://localhost:8080/api/products | jq
```

### Get product by ID

```bash
curl -s http://localhost:8080/api/products/1 | jq
```

### Search products by name keyword

```bash
curl -s "http://localhost:8080/api/products/search?keyword=phone" | jq
```

### List in-stock products

```bash
curl -s http://localhost:8080/api/products/in-stock | jq
```

### List products within a price range

```bash
curl -s "http://localhost:8080/api/products/price-range?min=100.00&max=600.00" | jq
```

### Update a product

```bash
curl -s -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop Pro Max",
    "description": "Upgraded high-performance laptop",
    "price": 1199.99,
    "stockQuantity": 5
  }' | jq
```

### Delete a product

```bash
curl -s -X DELETE http://localhost:8080/api/products/1 -w "\nHTTP Status: %{http_code}\n"
```

### Validation error example (missing required fields)

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name": ""}' | jq
```

### Not found error example

```bash
curl -s http://localhost:8080/api/products/9999 | jq
```

---

## Running the Tests

### Prerequisites

- Docker must be running (Testcontainers needs it to start the PostgreSQL container).

### Run all tests (unit + integration)

```bash
./mvnw clean test
```

Testcontainers will automatically:
1. Pull `postgres:16-alpine` (first run only — cached afterwards).
2. Start the container on a random free host port.
3. Configure the Spring DataSource to point to it via `@DynamicPropertySource`.
4. Run all integration tests against the real PostgreSQL instance.
5. Stop and remove the container when the tests finish.

### What the tests cover

#### Unit Tests — `ProductServiceTest`

Tests the `ProductService` class in isolation using Mockito mocks. **No database or Spring context** is involved.

- `findAll` — returns mapped DTOs; returns empty list when no data.
- `findById` — returns correct DTO; throws `ProductNotFoundException` for missing ID.
- `search` — returns filtered products by keyword; returns empty list on no match.
- `findInStock` — returns only in-stock products.
- `findByPriceRange` — returns products within price bounds.
- `create` — saves the product and returns the DTO with the generated ID.
- `update` — modifies the product; throws not-found when ID missing.
- `delete` — calls `deleteById`; throws not-found when ID missing.

#### Integration Tests — `ProductRepositoryIntegrationTest`

Tests the `ProductRepository` queries against a **real PostgreSQL Docker container** started by Testcontainers.

- Basic CRUD: save, findById, findAll, deleteById, update (saveAndFlush).
- `findByName` — exact match.
- `findByPriceLessThanEqual` — price filter.
- `findByNameContainingIgnoreCase` — partial case-insensitive name search.
- `existsByName` — existence check.
- `findAllInStock` — custom JPQL query (stockQuantity > 0).
- `findByPriceBetween` — custom JPQL query (inclusive range).
- `count` and `existsById`.

---

## Key Concepts

### Why Testcontainers?

Traditional integration tests often use H2 (an in-memory database) as a substitute for PostgreSQL. This is fast but unreliable — H2 does not support all PostgreSQL features, syntax, or constraints. Testcontainers solves this by running the **real PostgreSQL image** inside Docker, giving you full production parity.

### `@DynamicPropertySource`

Testcontainers assigns a **random host port** to the container to avoid conflicts. `@DynamicPropertySource` registers the container's JDBC URL, username and password into the Spring context *before* it initialises, so the DataSource configuration is always correct at runtime.

### `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)`

`@DataJpaTest` loads a minimal Spring context (only JPA components — no web layer). By default it replaces the configured DataSource with H2. Adding `replace = NONE` prevents this substitution so Testcontainers' PostgreSQL is used instead.

### Shared Container Pattern

The `@Container static` declaration makes Testcontainers start the PostgreSQL container **once per test class** (not once per test method). Test isolation is maintained by `@BeforeEach deleteAll()`. This is much faster than per-method containers.

---

## Docker Compose Details

The `docker-compose.yml` defines two services:

- **`postgres`** — `postgres:16-alpine` with a named volume (`postgres_data`) for persistence. Includes a healthcheck so the app waits for the database to be ready.
- **`app`** — Built from the multi-stage `Dockerfile`. Receives the database connection via environment variables that override `application.yml`.
