# 21 — Reactive R2DBC

A Spring Boot mini-project that demonstrates **non-blocking, asynchronous access to a relational (PostgreSQL) database** using **Spring Data R2DBC** and **Spring WebFlux**.

## What this mini-project covers

| Concept | Details |
|---|---|
| **R2DBC** | Reactive Relational Database Connectivity — the non-blocking, async alternative to JDBC |
| **Spring Data R2DBC** | `ReactiveCrudRepository` with derived queries and `@Query`-annotated raw SQL |
| **Spring WebFlux** | Reactive REST controllers returning `Mono<T>` and `Flux<T>` |
| **Flyway** | SQL schema migration applied at startup (uses JDBC; the app itself uses R2DBC) |
| **Spring Data Auditing** | `@CreatedDate` / `@LastModifiedDate` auto-populated via `@EnableR2dbcAuditing` |
| **Bean Validation** | Request DTOs validated with `@Valid`, `@NotBlank`, `@DecimalMin`, `@Min` |
| **Global Exception Handling** | `@RestControllerAdvice` with structured JSON error responses |
| **Unit tests** | Domain object tests and service tests with Mockito + `StepVerifier` (no Spring context) |
| **Integration tests** | Full end-to-end tests against a real PostgreSQL container via Testcontainers |

### Key difference: R2DBC vs JDBC

```
JDBC (blocking):
  HTTP request → thread blocked → SQL query → wait... → result → response
  Thread is occupied the entire time the database is working.

R2DBC (non-blocking):
  HTTP request → SQL query issued → thread released → (DB works in background)
                → callback → result → response
  Thread handles other requests while the database is working.
```

## Requirements

- **Java 21+**
- **Docker** (Docker Desktop or Docker Engine) — required for running the full stack and for integration tests (Testcontainers)
- **Maven Wrapper** included (`./mvnw`) — no local Maven installation needed

## Project structure

```
src/
├── main/
│   ├── java/com/example/reactiver2dbc/
│   │   ├── ReactiveR2dbcApplication.java   # Spring Boot entry point
│   │   ├── config/
│   │   │   └── R2dbcConfig.java            # @EnableR2dbcAuditing
│   │   ├── domain/
│   │   │   └── Product.java                # @Table entity (R2DBC, not JPA)
│   │   ├── dto/
│   │   │   └── ProductRequest.java         # Validated request DTO
│   │   ├── repository/
│   │   │   └── ProductRepository.java      # ReactiveCrudRepository + custom queries
│   │   ├── service/
│   │   │   └── ProductService.java         # Business logic, Mono/Flux operators
│   │   ├── controller/
│   │   │   └── ProductController.java      # REST endpoints
│   │   └── exception/
│   │       └── GlobalExceptionHandler.java # @RestControllerAdvice
│   └── resources/
│       ├── application.yml                 # R2DBC + Flyway + logging config
│       └── db/migration/
│           └── V1__create_products_table.sql  # Flyway migration (schema + seed data)
└── test/
    ├── java/com/example/reactiver2dbc/
    │   ├── domain/
    │   │   └── ProductDomainTest.java       # Pure domain unit tests
    │   ├── service/
    │   │   └── ProductServiceTest.java      # Service unit tests (Mockito + StepVerifier)
    │   └── ProductIntegrationTest.java      # Full integration tests (Testcontainers PostgreSQL)
    └── resources/
        ├── application-test.yml            # H2 in-memory profile (slice tests)
        ├── application-integration-test.yml # Integration test logging profile
        ├── docker-java.properties          # Testcontainers Docker API version fix
        └── testcontainers.properties       # Testcontainers Docker API version fix
```

## Running with Docker Compose (recommended)

This is the recommended way to run the complete project. Docker Compose starts both PostgreSQL and the Spring Boot application.

**Build and start all services:**

```bash
docker compose up --build
```

**Start in detached mode (background):**

```bash
docker compose up --build -d
```

**View application logs:**

```bash
docker compose logs -f app
```

**Stop and remove containers:**

```bash
docker compose down
```

**Stop and remove containers AND the database volume (wipes all data):**

```bash
docker compose down -v
```

Once running, the API is available at `http://localhost:8080`.

## API endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/products` | List all products |
| `GET` | `/api/products/{id}` | Get product by ID |
| `GET` | `/api/products/category/{category}` | List products by category |
| `GET` | `/api/products/active` | List active products |
| `GET` | `/api/products/active/category/{category}` | List active products in a category |
| `GET` | `/api/products/search?keyword=...` | Search products by name (case-insensitive) |
| `GET` | `/api/products/price-range?min=...&max=...` | List products within a price range |
| `GET` | `/api/products/low-stock?threshold=5` | List products with low stock |
| `GET` | `/api/products/category/{category}/count` | Count products in a category |
| `POST` | `/api/products` | Create a new product |
| `PUT` | `/api/products/{id}` | Update an existing product |
| `DELETE` | `/api/products/{id}` | Delete a product |

## curl examples

The Flyway migration seeds 10 sample products on first startup, so you can immediately try GET requests.

### List all products
```bash
curl -s http://localhost:8080/api/products | jq .
```

### Get a single product by ID
```bash
curl -s http://localhost:8080/api/products/1 | jq .
```

### List products by category
```bash
curl -s http://localhost:8080/api/products/category/electronics | jq .
```

### List active products only
```bash
curl -s http://localhost:8080/api/products/active | jq .
```

### Search products by name (case-insensitive)
```bash
curl -s "http://localhost:8080/api/products/search?keyword=keyboard" | jq .
```

### Filter products by price range
```bash
curl -s "http://localhost:8080/api/products/price-range?min=10&max=100" | jq .
```

### List low-stock products (default threshold = 5)
```bash
curl -s "http://localhost:8080/api/products/low-stock" | jq .
```

### List low-stock products with a custom threshold
```bash
curl -s "http://localhost:8080/api/products/low-stock?threshold=20" | jq .
```

### Count products in a category
```bash
curl -s http://localhost:8080/api/products/category/electronics/count
```

### Create a new product
```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "4K Monitor",
    "description": "27-inch IPS panel, 144Hz, USB-C, HDR400",
    "price": 499.99,
    "category": "electronics",
    "stockQuantity": 15,
    "active": true
  }' | jq .
```

### Update a product (full replacement — PUT semantics)
```bash
curl -s -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Wireless Mechanical Keyboard v2",
    "description": "Updated model with RGB backlight and longer battery",
    "price": 139.99,
    "category": "electronics",
    "stockQuantity": 30,
    "active": true
  }' | jq .
```

### Delete a product
```bash
curl -s -X DELETE http://localhost:8080/api/products/1 -w "\nHTTP status: %{http_code}\n"
```

### Validation error example (missing required fields)
```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name": "", "description": "Desc", "price": -5, "category": "", "stockQuantity": -1, "active": true}' | jq .
```

## Running the tests

The test suite has two layers:

### 1. Unit tests (no Docker required)
Run the domain and service unit tests in isolation (Mockito mocks, no Spring context, no database):

```bash
./mvnw test -Dtest="ProductDomainTest,ProductServiceTest"
```

### 2. Full test suite (Docker required)
Run all tests including the Testcontainers integration tests. Docker must be running.

```bash
./mvnw clean test
```

Testcontainers will automatically:
1. Pull the `postgres:16-alpine` Docker image (first run only).
2. Start a PostgreSQL container on a random port.
3. Run Flyway migrations against the container.
4. Execute all integration tests.
5. Stop and remove the container.

### Expected output
```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0  (ProductDomainTest)
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0  (ProductServiceTest)
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0  (ProductIntegrationTest)
[INFO] BUILD SUCCESS
```

## Connecting to PostgreSQL directly

When running via Docker Compose, you can connect to the database with `psql`:

```bash
# From your host machine (requires psql installed)
psql -h localhost -U postgres -d productsdb

# Or via Docker Compose exec (no local psql needed)
docker compose exec postgres psql -U postgres -d productsdb
```

Useful SQL queries:
```sql
-- List all products
SELECT id, name, price, category, stock_quantity, active FROM products ORDER BY id;

-- Check the Flyway migration history
SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;

-- Count products by category
SELECT category, COUNT(*) FROM products GROUP BY category ORDER BY count DESC;
```

## How it works

### Request flow
```
HTTP Request
    │
    ▼
ProductController  (@RestController, returns Mono/Flux)
    │
    ▼
ProductService     (business logic, Mono/Flux operators)
    │
    ▼
ProductRepository  (ReactiveCrudRepository, derived queries + @Query)
    │
    ▼  (non-blocking via R2DBC driver)
PostgreSQL
```

### Schema migration flow (at startup)
```
Spring Boot starts
    │
    ▼
Flyway checks flyway_schema_history table
    │
    ▼
Applies pending V1__create_products_table.sql (via JDBC)
    │
    ▼
R2DBC ConnectionFactory ready for reactive queries
```

### Why both JDBC (Flyway) and R2DBC in the same app?
Flyway is a schema migration tool that requires synchronous JDBC access to reliably apply DDL scripts. R2DBC is used for all application runtime queries (reactive, non-blocking). Having both is a standard and accepted pattern: Flyway runs once at startup via JDBC, then the application switches entirely to R2DBC for its reactive workload.
