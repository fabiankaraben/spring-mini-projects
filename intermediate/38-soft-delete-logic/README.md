# Soft Delete Logic

A Spring Boot mini-project demonstrating **logical (soft) deletions** using Hibernate's
`@SQLDelete` and `@SQLRestriction` annotations.

Instead of physically removing a row from the database, a soft delete flips a `deleted`
boolean column to `true`.  A `@SQLRestriction` clause transparently hides deleted rows from
all normal JPA queries so the rest of the application never sees them.

---

## Concepts demonstrated

| Annotation | Role |
|---|---|
| `@SQLDelete` | Overrides `DELETE FROM products WHERE id = ?` with a custom `UPDATE products SET deleted = true, deleted_at = NOW() WHERE id = ?` |
| `@SQLRestriction` | Appends `WHERE deleted = false` to every Hibernate query for this entity automatically |
| `@Modifying` + native query | Used in the restore operation to flip `deleted` back to `false`, bypassing the `@SQLRestriction` filter |

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (or use the included Maven Wrapper) |
| Docker + Docker Compose | Required for running the app and integration tests |

---

## Project structure

```
src/main/java/com/example/softdelete/
├── SoftDeleteApplication.java          # Spring Boot entry point
├── controller/
│   └── ProductController.java          # REST endpoints
├── domain/
│   └── Product.java                    # Entity with @SQLDelete + @SQLRestriction
├── dto/
│   ├── ProductRequest.java             # Input DTO (record)
│   └── ProductResponse.java           # Output DTO (record)
├── exception/
│   ├── GlobalExceptionHandler.java    # @RestControllerAdvice
│   └── ProductNotFoundException.java
├── repository/
│   └── ProductRepository.java          # Spring Data + native restore queries
└── service/
    └── ProductService.java             # Business logic

src/test/java/com/example/softdelete/
├── unit/
│   └── ProductServiceTest.java         # JUnit 5 + Mockito unit tests
└── integration/
    └── SoftDeleteIntegrationTest.java  # Testcontainers integration tests
```

---

## Running with Docker Compose

The entire application (Spring Boot + PostgreSQL) runs inside Docker Compose.

### Start

```bash
docker compose up --build
```

The app is available at `http://localhost:8080`.

### Stop

```bash
docker compose down
```

### Stop and remove volumes (wipes database data)

```bash
docker compose down -v
```

---

## API usage (curl examples)

### Create a product

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop","description":"A powerful laptop","price":999.99,"category":"Electronics"}' | jq .
```

### List all active (non-deleted) products

```bash
curl -s http://localhost:8080/api/products | jq .
```

### Get a single product by ID

```bash
curl -s http://localhost:8080/api/products/1 | jq .
```

### Search products by name (case-insensitive fragment)

```bash
curl -s "http://localhost:8080/api/products/search?name=lap" | jq .
```

### Filter products by category

```bash
curl -s http://localhost:8080/api/products/category/Electronics | jq .
```

### Update a product

```bash
curl -s -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Gaming Laptop","description":"Updated","price":1299.99,"category":"Electronics"}' | jq .
```

### Soft-delete a product

The row is **not** physically removed. Hibernate executes:
`UPDATE products SET deleted = true, deleted_at = NOW() WHERE id = ?`

```bash
curl -s -X DELETE http://localhost:8080/api/products/1
# Returns 204 No Content
```

After this, `GET /api/products/1` returns **404** because `@SQLRestriction` hides it.

### List soft-deleted products (admin view)

Uses a native SQL query that bypasses `@SQLRestriction`:

```bash
curl -s http://localhost:8080/api/products/deleted | jq .
```

### Count soft-deleted products

```bash
curl -s http://localhost:8080/api/products/deleted/count
```

### Restore a soft-deleted product

Clears `deleted = false` and `deleted_at = NULL`. The product becomes visible again
through all normal queries.

```bash
curl -s -X POST http://localhost:8080/api/products/1/restore | jq .
```

---

## Observing the soft-delete SQL in action

Set `show-sql: true` in `application.yml` (already enabled by default) and watch the
application logs. When you call `DELETE /api/products/1`, you will see:

```sql
-- @SQLDelete intercepts the DELETE and replaces it with:
UPDATE products
SET deleted = true, deleted_at = NOW()
WHERE id = ?

-- @SQLRestriction appends "deleted = false" to every SELECT:
SELECT p1_0.id, p1_0.category, p1_0.created_at, p1_0.deleted,
       p1_0.deleted_at, p1_0.description, p1_0.name, p1_0.price, p1_0.updated_at
FROM products p1_0
WHERE p1_0.deleted=false
```

---

## Running the tests

Tests require Docker to be running (Testcontainers spins up a PostgreSQL container).

```bash
./mvnw clean test
```

### Test structure

| Test class | Type | What it covers |
|---|---|---|
| `ProductServiceTest` | Unit | Service logic with mocked repository; no database needed |
| `SoftDeleteIntegrationTest` | Integration | Full HTTP + real PostgreSQL via Testcontainers |

### Integration test highlights

- **`@SQLDelete` effect** – DELETE request returns 204 and the row stays in the DB
- **`@SQLRestriction` transparency** – GET after DELETE returns 404 (row is hidden)
- **Admin view** – `GET /deleted` returns soft-deleted products via native SQL
- **Restore flow** – POST `/restore` re-activates the product and makes it visible again
- **Full lifecycle** – create → soft-delete → restore → verify

---

## Docker Compose reference

### Services

| Service | Image | Port |
|---|---|---|
| `db` | `postgres:16-alpine` | `5432` |
| `app` | Built from `Dockerfile` | `8080` |

### Environment variables (app service)

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/softdeletedb` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | DB password |

### Dockerfile

The `Dockerfile` uses a **multi-stage build**:
1. **Build stage** (`eclipse-temurin:21-jdk-alpine`) – downloads dependencies and packages the fat JAR.
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`) – copies only the JAR, keeping the final image small.
