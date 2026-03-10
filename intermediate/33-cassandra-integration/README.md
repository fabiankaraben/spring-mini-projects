# Cassandra Integration

A Spring Boot backend connecting to **Apache Cassandra** — a distributed wide-column NoSQL store — using **Spring Data Cassandra**. This mini-project demonstrates query-driven table design, composite primary keys, partition-aware access patterns, and full CRUD operations via a REST API.

---

## What is Apache Cassandra?

Apache Cassandra is a highly scalable, distributed wide-column NoSQL database designed for high availability and fault tolerance across multiple datacenters. Unlike relational databases, Cassandra organises data into **partitions**:

- **Partition key** — determines which node(s) in the cluster hold the data.
- **Clustering key** — sorts rows within a partition and contributes to row uniqueness.

Cassandra is optimised for **write-heavy workloads** and **partition-key-based reads**. Table schemas are designed around the queries you need to run, not the other way around.

---

## Domain: Product Catalog

The application exposes a REST API for a **product catalog** where:

- Products belong to a **category** (partition key — routes queries to the right node).
- Each product has a unique **UUID** within its category (clustering key — sorts rows within the partition).
- The composite primary key `(category, id)` ensures uniqueness across the whole table.

---

## Requirements

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ (or use `./mvnw`) |
| Docker | 20+ (with Docker Desktop or Docker Engine) |
| Docker Compose | v2 (`docker compose` command) |

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/cassandraintegration/
│   │   ├── CassandraIntegrationApplication.java   # Entry point
│   │   ├── config/
│   │   │   └── CassandraConfig.java               # Cassandra connection & schema config
│   │   ├── domain/
│   │   │   └── Product.java                       # @Table entity with composite primary key
│   │   ├── dto/
│   │   │   ├── CreateProductRequest.java           # POST request DTO
│   │   │   └── UpdateProductRequest.java           # PUT request DTO (partial update)
│   │   ├── repository/
│   │   │   └── ProductRepository.java             # CassandraRepository + custom @Query methods
│   │   ├── service/
│   │   │   └── ProductService.java                # Business logic layer
│   │   └── controller/
│   │       └── ProductController.java             # REST API controller
│   └── resources/
│       └── application.yml                        # Cassandra connection config
└── test/
    ├── java/com/example/cassandraintegration/
    │   ├── domain/ProductTest.java                # Unit tests: entity behaviour
    │   ├── service/ProductServiceTest.java        # Unit tests: service with Mockito
    │   └── integration/CassandraIntegrationTest.java # Integration tests: Testcontainers
    └── resources/
        ├── docker-java.properties                 # Docker API version fix for Docker Desktop 29+
        └── testcontainers.properties              # Testcontainers Docker API config
```

---

## Running with Docker Compose

The entire stack (Cassandra + Spring Boot app) runs via Docker Compose. **No local Cassandra installation needed.**

### How keyspace creation works

Apache Cassandra does **not** create keyspaces automatically. The Spring Boot Cassandra driver connects with the keyspace name already set — if the keyspace doesn't exist, it throws `InvalidKeyspaceException`. The project handles this in two layers:

- **Docker Compose** — a one-shot `cassandra-init` service runs `cqlsh` to create the `catalog` keyspace after Cassandra is healthy, before the app starts.
- **Integration tests** — `@DynamicPropertySource` opens a temporary raw `CqlSession` (with no keyspace) to create the `catalog_test` keyspace, then closes it before the Spring context starts.

Spring Boot's `schema-action: CREATE_IF_NOT_EXISTS` only creates **tables** inside the existing keyspace — it does not create the keyspace itself.

### Start the stack

```bash
docker compose up --build
```

> Cassandra takes ~60 seconds to become healthy. The `cassandra-init` service then creates the keyspace, and only after it completes successfully does the `app` service start. This is all handled automatically by `depends_on` conditions.

### Stop the stack

```bash
docker compose down
```

### Stop and remove all data volumes

```bash
docker compose down -v
```

### Check logs

```bash
# All services
docker compose logs -f

# Only the Cassandra node
docker compose logs -f cassandra

# Only the Spring Boot app
docker compose logs -f app
```

### Access the Cassandra shell (cqlsh) inside the running container

```bash
docker compose exec cassandra cqlsh
```

Then explore the keyspace:
```cql
USE catalog;
DESCRIBE TABLES;
SELECT * FROM products;
```

---

## API Reference & curl Examples

The API is available at `http://localhost:8080` after the stack is up.

> **Note:** Every read/update/delete endpoint requires `?category=...` because `category` is the Cassandra partition key — Cassandra cannot route a query without it.

### Create a product

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "category": "Electronics",
    "name": "Laptop Pro",
    "description": "A high-end 15-inch laptop",
    "price": 1299.99,
    "stock": 20
  }' | jq .
```

### Create another product (same category)

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "category": "Electronics",
    "name": "Wireless Mouse",
    "description": "Ergonomic Bluetooth mouse",
    "price": 49.99,
    "stock": 150
  }' | jq .
```

### Create a product in a different category

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "category": "Books",
    "name": "Clean Code",
    "description": "A handbook of agile software craftsmanship",
    "price": 39.99,
    "stock": 200
  }' | jq .
```

### List all products in a category (partition key query)

```bash
curl -s "http://localhost:8080/api/products?category=Electronics" | jq .
```

### Get a product by composite key (category + UUID)

Replace `<UUID>` with the `id` from the create response:

```bash
curl -s "http://localhost:8080/api/products/<UUID>?category=Electronics" | jq .
```

### Filter by maximum price

```bash
curl -s "http://localhost:8080/api/products/filter/price?category=Electronics&maxPrice=100" | jq .
```

### Filter by minimum stock

```bash
curl -s "http://localhost:8080/api/products/filter/stock?category=Electronics&minStock=50" | jq .
```

### Update a product (partial update — only supply fields you want to change)

```bash
curl -s -X PUT "http://localhost:8080/api/products/<UUID>?category=Electronics" \
  -H "Content-Type: application/json" \
  -d '{
    "price": 999.99,
    "stock": 15
  }' | jq .
```

### Delete a product

```bash
curl -s -X DELETE "http://localhost:8080/api/products/<UUID>?category=Electronics" -v
```

Expected response: `HTTP 204 No Content`

---

## Validation Rules

| Field | Rule |
|-------|------|
| `category` | Not blank |
| `name` | Not blank |
| `price` | Not null, >= 0.0 |
| `stock` | >= 0 |

Invalid requests return `HTTP 400 Bad Request` with error details.

---

## Running the Tests

Tests run **without** a running Docker Compose stack. The integration tests use **Testcontainers** to spin up a real Cassandra Docker container automatically.

> Docker must be running (Docker Desktop or Docker Engine) before executing the tests.

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests (no Docker required)

```bash
./mvnw clean test -Dtest="ProductTest,ProductServiceTest"
```

### Run only integration tests

```bash
./mvnw clean test -Dtest="CassandraIntegrationTest"
```

---

## Test Structure

### Unit Tests (no Spring context, no Docker)

| Test Class | What it tests |
|------------|---------------|
| `ProductTest` | Domain entity constructors, getters/setters, toString |
| `ProductServiceTest` | Service logic with Mockito-mocked repository |

### Integration Tests (full Spring context + Testcontainers Cassandra)

| Test Class | What it tests |
|------------|---------------|
| `CassandraIntegrationTest` | Full HTTP → Service → Repository → Cassandra round-trip |

The integration test covers:
- CRUD via REST endpoints with real Cassandra persistence
- Validation (400 Bad Request for invalid input)
- 404 Not Found for missing resources
- Partition isolation (queries only return data from the requested category)
- Price and stock filters using `ALLOW FILTERING` CQL queries
- Partial update (only non-null fields are changed)

---

## Key Cassandra Concepts in This Project

| Concept | Where to look |
|---------|---------------|
| **Partition key** | `@PrimaryKeyColumn(type = PARTITIONED)` in `Product.java` |
| **Clustering key** | `@PrimaryKeyColumn(type = CLUSTERED)` in `Product.java` |
| **Schema auto-creation** | `schema-action: CREATE_IF_NOT_EXISTS` in `application.yml` / `CassandraConfig.java` |
| **Keyspace creation** | `keyspace-creation.*` in `application.yml` |
| **Custom CQL queries** | `@Query(...)` annotations in `ProductRepository.java` |
| **ALLOW FILTERING** | Price and stock filter queries in `ProductRepository.java` |
| **Cassandra upsert** | `save()` in `ProductService.updateProduct()` — Cassandra INSERT is an upsert |
| **Tombstones** | `delete()` in `ProductService.deleteProduct()` — Cassandra deletes via tombstone markers |

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.4.3 |
| ORM | Spring Data Cassandra |
| Database | Apache Cassandra 4.1 |
| Driver | DataStax Java Driver (via Spring Boot auto-config) |
| Validation | Jakarta Bean Validation (Hibernate Validator) |
| Unit Tests | JUnit 5 + Mockito + AssertJ |
| Integration Tests | Testcontainers (cassandra module) + MockMvc |
| Build | Maven + Maven Wrapper |
| Containerisation | Docker + Docker Compose |
