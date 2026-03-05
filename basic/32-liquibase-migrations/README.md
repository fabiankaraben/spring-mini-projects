# 32 — Liquibase Migrations

A **Spring Boot** mini-project that demonstrates automated database schema management
using **Liquibase**. Instead of writing raw `ALTER TABLE` statements and manually
tracking which ones have been applied, Liquibase manages an ordered set of
**changesets** and a `DATABASECHANGELOG` table to guarantee that each change is
applied exactly once — in every environment.

---

## What Is Liquibase?

Liquibase is a database-independent schema change management library. You describe
your database changes in **changelog files** (XML, YAML, JSON or SQL). Liquibase
records every applied changeset in a special `DATABASECHANGELOG` table. On the next
application start, Liquibase skips changesets that are already recorded and only runs
new ones.

Spring Boot auto-configures Liquibase when the `liquibase-core` dependency is present:
it runs migrations before the application context is fully initialised, ensuring the
schema is always up to date when the app starts serving requests.

---

## How This Project Differs From Flyway

| Feature | Flyway | Liquibase |
|---|---|---|
| Changelog format | Plain SQL files | XML / YAML / JSON / SQL |
| Rollback support | Pro feature | Built-in (via `<rollback>`) |
| Schema tracking table | `flyway_schema_history` | `databasechangelog` |
| Changeset identifier | File name (`V1__...sql`) | `id` + `author` attributes |
| Database-agnostic DDL | ❌ raw SQL | ✅ XML DSL translates DDL |

---

## Project Structure

```
32-liquibase-migrations/
├── Dockerfile                            # Multi-stage Docker image
├── docker-compose.yml                    # Runs app + PostgreSQL together
├── pom.xml                               # Maven build file
├── src/
│   ├── main/
│   │   ├── java/com/example/liquibasemigrations/
│   │   │   ├── LiquibaseMigrationsApplication.java  # Entry point
│   │   │   ├── controller/
│   │   │   │   └── ProductController.java           # REST endpoints
│   │   │   ├── entity/
│   │   │   │   └── Product.java                     # JPA entity
│   │   │   └── repository/
│   │   │       └── ProductRepository.java           # Spring Data JPA repository
│   │   └── resources/
│   │       ├── application.properties               # Main config (PostgreSQL)
│   │       └── db/changelog/
│   │           ├── db.changelog-master.xml          # Master changelog (includes others)
│   │           └── changes/
│   │               ├── 001-create-products-table.xml   # Changeset 1 – create table
│   │               ├── 002-insert-initial-products.xml # Changeset 2 – seed data
│   │               └── 003-add-category-column.xml     # Changeset 3 – add column
│   └── test/
│       ├── java/com/example/liquibasemigrations/
│       │   ├── LiquibaseMigrationsApplicationTests.java  # Context smoke test
│       │   ├── controller/
│       │   │   └── ProductControllerTest.java            # @WebMvcTest
│       │   └── repository/
│       │       └── ProductRepositoryTest.java            # @DataJpaTest
│       └── resources/
│           └── application.properties               # Test config (H2 in-memory)
```

---

## Liquibase Migration History

The three changesets demonstrate sequential schema evolution:

| File | Changeset ID | What It Does |
|---|---|---|
| `001-create-products-table.xml` | `001` | Creates the `products` table with `id`, `name`, `price` |
| `002-insert-initial-products.xml` | `002` | Inserts 3 seed products |
| `003-add-category-column.xml` | `003` | Adds `category` column via safe 3-step pattern (add nullable → backfill → NOT NULL) |

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (or use the included `./mvnw`) |
| Docker | 24+ |
| Docker Compose Plugin | v2+ (`docker compose` command) |

---

## Running the Project

The entire project runs within Docker Compose. A single command builds the app
image and starts both the application and the PostgreSQL database:

```bash
docker compose up --build
```

On startup, Spring Boot triggers Liquibase which:
1. Connects to `liquibasedb` on the PostgreSQL container.
2. Creates the `DATABASECHANGELOG` tracking table (if not present).
3. Applies changesets 001, 002, and 003 in order.
4. Skips any changeset that was already applied (idempotent).

Wait for the log line `Started LiquibaseMigrationsApplication` before calling the API.

To stop and remove containers:

```bash
docker compose down
```

To also remove the database volume (full reset):

```bash
docker compose down -v
```

---

## API Endpoints

Base URL: `http://localhost:8080`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/products` | Get all products |
| `GET` | `/api/products/{id}` | Get a product by ID |
| `GET` | `/api/products/category/{category}` | Get products by category |
| `POST` | `/api/products` | Create a new product |
| `DELETE` | `/api/products/{id}` | Delete a product by ID |

---

## curl Examples

### Get all products (includes seed data from changeset 002/003)

```bash
curl http://localhost:8080/api/products
```

### Get a product by ID

```bash
curl http://localhost:8080/api/products/1
```

### Get products by category

```bash
curl http://localhost:8080/api/products/category/Electronics
```

### Create a new product

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name": "Smartwatch", "price": 199.99, "category": "Wearables"}'
```

### Delete a product

```bash
curl -X DELETE http://localhost:8080/api/products/1
```

---

## Running the Tests

Tests use an **H2 in-memory database** (PostgreSQL compatibility mode) so no
Docker or external database is needed.

### Run all tests

```bash
./mvnw test
```

### Run only the controller (web layer) tests

```bash
./mvnw test -Dtest=ProductControllerTest
```

### Run only the repository (JPA layer) tests

```bash
./mvnw test -Dtest=ProductRepositoryTest
```

### Run the context smoke test

```bash
./mvnw test -Dtest=LiquibaseMigrationsApplicationTests
```

---

## Test Strategy

| Test Class | Annotation | What It Tests |
|---|---|---|
| `LiquibaseMigrationsApplicationTests` | `@SpringBootTest` | Full context loads; Liquibase runs on H2 without errors |
| `ProductControllerTest` | `@WebMvcTest` | HTTP layer only; repository is mocked with `@MockitoBean` |
| `ProductRepositoryTest` | `@DataJpaTest` | JPA layer + Liquibase migrations against H2; verifies seed data and derived queries |

---

## Key Concepts Illustrated

### Master Changelog Pattern

`db.changelog-master.xml` is the single entry point. It uses `<include>` to pull
in individual changeset files in order. Adding a new migration means creating a new
file and adding one `<include>` line.

### `id` + `author` Uniqueness

Each `<changeSet>` is identified by `id` + `author`. Liquibase records this pair in
`DATABASECHANGELOG`. Changing a changeset that was already applied will cause an MD5
checksum error — you must always add a NEW changeset, never modify an existing one.

### Safe NOT NULL Column Addition (Changeset 003)

Adding a NOT NULL column to a table that already has rows requires three steps:
1. Add the column as nullable.
2. Back-fill all existing rows.
3. Apply the NOT NULL constraint.

Doing it in a single step would fail if the database doesn't support inline defaults
with NOT NULL, or if the table is large enough to cause lock contention.

### Rollback Support

Each changeset defines a `<rollback>` block. This allows you to undo migrations:

```bash
# Rollback the last 1 changeset
liquibase rollback-count 1
```

### `validate` DDL Auto

`spring.jpa.hibernate.ddl-auto=validate` tells Hibernate to verify that JPA entity
fields match the database columns, but NEVER to create, alter, or drop tables.
Liquibase is the sole authority over the schema.
