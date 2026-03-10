# Optimistic Locking with JPA `@Version`

A Spring Boot mini-project demonstrating how to prevent **concurrent modification issues** (the "lost update" problem) using JPA's `@Version` annotation for optimistic locking.

## What is Optimistic Locking?

Optimistic locking is a concurrency control strategy that assumes conflicts are **rare** and does not hold database locks during the entire operation. Instead, it detects conflicts at commit time using a version counter:

```
Thread A: reads Product(id=1, version=0, stock=10)
Thread B: reads Product(id=1, version=0, stock=10)

Thread A: sets stock=8, saves →
    UPDATE products SET stock=8, version=1 WHERE id=1 AND version=0  ✅ (1 row updated)

Thread B: sets stock=5, saves →
    UPDATE products SET stock=5, version=1 WHERE id=1 AND version=0  ❌ (0 rows – version=0 is stale!)
    → ObjectOptimisticLockingFailureException → HTTP 409 Conflict
```

Without optimistic locking, Thread B's update would silently overwrite Thread A's changes — the classic **lost update** bug. With `@Version`, Thread B receives a clear conflict signal and can retry with fresh data.

## Key Concepts

| Concept | Description |
|---|---|
| `@Version` on `Product.version` | Hibernate initialises it to `0` on INSERT, increments it on every UPDATE, and adds `WHERE version = ?` to the UPDATE SQL |
| `ObjectOptimisticLockingFailureException` | Thrown by Spring when the version WHERE clause matches 0 rows |
| HTTP 409 Conflict | The REST response returned to a client that sent a stale version |
| Client responsibility | Clients must include the current `version` in every PUT request |

## Requirements

- **Java 21+**
- **Maven 3.9+** (or use the included Maven Wrapper `./mvnw`)
- **Docker** and **Docker Compose** (for running with a real PostgreSQL database)

## Project Structure

```
src/
├── main/java/com/example/optimisticlocking/
│   ├── OptimisticLockingApplication.java   # Spring Boot entry point
│   ├── controller/ProductController.java   # REST endpoints
│   ├── service/ProductService.java         # Business logic + locking flow
│   ├── domain/Product.java                 # JPA entity with @Version field
│   ├── repository/ProductRepository.java   # Spring Data JPA repository
│   ├── dto/
│   │   ├── ProductRequest.java             # DTO for POST (no version)
│   │   ├── ProductUpdateRequest.java       # DTO for PUT (version required)
│   │   └── ProductResponse.java            # DTO for responses (includes version)
│   └── exception/
│       ├── GlobalExceptionHandler.java     # Maps exceptions to HTTP responses
│       └── ProductNotFoundException.java   # 404 exception
└── test/java/com/example/optimisticlocking/
    ├── unit/ProductServiceTest.java        # Unit tests (Mockito, no DB)
    └── integration/
        └── OptimisticLockingIntegrationTest.java  # Integration tests (Testcontainers)
```

## Running with Docker Compose

This is the recommended way to run the application. Docker Compose starts both PostgreSQL and the Spring Boot app.

**Start the application:**

```bash
docker compose up --build
```

**Start in detached mode (background):**

```bash
docker compose up --build -d
```

**Stop the application:**

```bash
docker compose down
```

**Stop and remove the data volume (clean slate):**

```bash
docker compose down -v
```

The API will be available at `http://localhost:8080`.

## API Endpoints

| Method | URL | Description |
|---|---|---|
| `GET` | `/api/products` | List all products |
| `GET` | `/api/products/{id}` | Get product by ID (includes `version`) |
| `GET` | `/api/products/search?name=` | Search by name |
| `GET` | `/api/products/category/{category}` | Filter by category |
| `POST` | `/api/products` | Create a new product |
| `PUT` | `/api/products/{id}` | Update product (**version required**) |
| `DELETE` | `/api/products/{id}` | Delete product |

## Usage Examples (curl)

### Create a product

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Wireless Headphones",
    "description": "Noise-cancelling over-ear headphones",
    "price": 149.99,
    "stock": 50,
    "category": "Audio"
  }' | jq .
```

Response (`version` starts at `0`):

```json
{
  "id": 1,
  "version": 0,
  "name": "Wireless Headphones",
  "description": "Noise-cancelling over-ear headphones",
  "price": 149.99,
  "stock": 50,
  "category": "Audio",
  "createdAt": "2024-01-15T10:00:00",
  "updatedAt": "2024-01-15T10:00:00"
}
```

### Get a product (obtain the current version)

```bash
curl -s http://localhost:8080/api/products/1 | jq .
```

### Update a product (with the current version)

Always include the `version` field you received from the last GET or POST:

```bash
curl -s -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "version": 0,
    "name": "Wireless Headphones Pro",
    "description": "Updated noise-cancelling headphones",
    "price": 179.99,
    "stock": 45,
    "category": "Audio"
  }' | jq .
```

Response (`version` is now `1`):

```json
{
  "id": 1,
  "version": 1,
  "name": "Wireless Headphones Pro",
  ...
}
```

### Simulate an optimistic locking conflict (HTTP 409)

1. Create a product and note `version: 0`.
2. Update it once (version goes `0 → 1`).
3. Try to update again using the **old** `version: 0`:

```bash
# This will fail with 409 Conflict because version=0 is now stale
curl -s -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "version": 0,
    "name": "Stale Update",
    "description": "This should be rejected",
    "price": 99.99,
    "stock": 30,
    "category": "Audio"
  }' | jq .
```

Response:

```json
{
  "timestamp": "2024-01-15T10:05:00",
  "status": 409,
  "error": "Conflict",
  "message": "Concurrent modification detected: the product was updated by another request since you last fetched it. Please re-fetch the latest version and retry."
}
```

**Recovery flow:** re-fetch the product to get `version: 1`, then retry the update with `"version": 1`.

### Update with missing version (HTTP 400)

```bash
curl -s -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Missing Version",
    "price": 99.99,
    "stock": 10
  }' | jq .
```

Response:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": {
    "version": "Version must not be null – include the current version from the GET response"
  }
}
```

### Delete a product

```bash
curl -s -X DELETE http://localhost:8080/api/products/1 -w "%{http_code}"
# → 204
```

### List all products

```bash
curl -s http://localhost:8080/api/products | jq .
```

### Search by name

```bash
curl -s "http://localhost:8080/api/products/search?name=wireless" | jq .
```

### Filter by category

```bash
curl -s http://localhost:8080/api/products/category/Audio | jq .
```

## How to Run the Tests

The test suite includes unit tests (no database required) and full integration tests powered by **Testcontainers** (requires Docker to be running).

**Run all tests:**

```bash
./mvnw clean test
```

**Run only unit tests:**

```bash
./mvnw test -pl . -Dtest="**/unit/**"
```

**Run only integration tests:**

```bash
./mvnw test -pl . -Dtest="**/integration/**"
```

### Test structure

| Test class | Type | What it tests |
|---|---|---|
| `ProductServiceTest` | Unit (Mockito) | Service logic, version propagation, exception handling |
| `OptimisticLockingIntegrationTest` | Integration (Testcontainers) | Real `@Version` SQL generation, HTTP 409 conflict flow |

### What the integration tests verify

- `version=0` is set on a newly created product (Hibernate INSERT)
- A successful update increments `version` by 1 (Hibernate UPDATE)
- An update with a **stale version** returns **HTTP 409 Conflict**
- Multiple sequential updates increment the version monotonically (0→1→2→3)
- The full conflict-recovery lifecycle: create → update → stale-conflict → re-fetch → retry succeeds
- The `version` column is present and correct directly in the database

### Prerequisites for integration tests

- Docker must be running (Testcontainers will automatically pull and start a `postgres:16-alpine` container)
- No manual database setup is needed — Testcontainers manages the lifecycle entirely

## How the `@Version` Annotation Works Internally

When Hibernate generates SQL for the `Product` entity:

**INSERT** (version initialised to 0):
```sql
INSERT INTO products (name, description, price, stock, category, version, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, 0, ?, ?)
```

**UPDATE** (version checked and incremented):
```sql
UPDATE products
SET name=?, description=?, price=?, stock=?, category=?, version=?, updated_at=?
WHERE id=? AND version=?
--           ^^^^^^^^^^^^ This is the optimistic lock check
```

If the `WHERE id=? AND version=?` clause matches **0 rows** (because another transaction already incremented the version), Hibernate detects the conflict and throws `ObjectOptimisticLockingFailureException`.
