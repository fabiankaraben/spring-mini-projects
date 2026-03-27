# Multi-Tenancy DB Pattern

A Spring Boot backend that demonstrates the **schema-per-tenant** multi-tenancy strategy. Each tenant is isolated in its own PostgreSQL schema. An HTTP header (`X-Tenant-ID`) identifies the current tenant; Hibernate's `MultiTenantConnectionProvider` SPI executes `SET search_path` to route every database connection to the correct schema dynamically per request.

## What This Project Demonstrates

- **Schema-per-tenant isolation**: Each tenant's data lives in its own PostgreSQL schema (`tenant_alpha`, `tenant_beta`, …). A product created for `tenant_alpha` is completely invisible to `tenant_beta`, even though both schemas live in the same database.
- **Hibernate multi-tenancy SPI**: Custom implementations of `MultiTenantConnectionProvider` and `CurrentTenantIdentifierResolver` integrate with Spring Data JPA without any repository-level changes.
- **Thread-local tenant context**: `TenantContext` binds the active tenant to the current thread via `ThreadLocal`, ensuring zero cross-tenant leakage in a multi-threaded servlet environment.
- **SQL injection prevention**: Tenant IDs from HTTP headers are validated against an allowlist regex before being embedded in `SET search_path` SQL (which cannot use JDBC parameter binding).
- **Dynamic schema initialization**: `TenantSchemaInitializer` creates each tenant's PostgreSQL schema and tables at startup — idempotently, using `IF NOT EXISTS`.

---

## Requirements

| Requirement       | Version       |
|-------------------|---------------|
| Java              | 21+           |
| Maven             | 3.9+ (wrapper included) |
| Docker            | 20+           |
| Docker Compose    | v2 (plugin)   |

---

## Architecture Overview

```
HTTP Request
  │  GET /api/products
  │  X-Tenant-ID: tenant_alpha
  ▼
TenantInterceptor          ← reads X-Tenant-ID header
  │  TenantContext.set("tenant_alpha")
  ▼
ProductController
  │  productService.findAll()
  ▼
ProductRepository (Spring Data JPA)
  │  Hibernate opens a Session
  ▼
TenantIdentifierResolver   ← reads from TenantContext
  │  returns "tenant_alpha"
  ▼
TenantConnectionProvider   ← obtains connection from shared HikariCP pool
  │  SET search_path TO tenant_alpha, public
  ▼
PostgreSQL — executes SELECT * FROM products
             (resolves to tenant_alpha.products)
```

### Key Components

| Class | Role |
|---|---|
| `TenantContext` | Thread-local holder for the current tenant ID |
| `TenantInterceptor` | Spring MVC interceptor — reads `X-Tenant-ID` header, sets and clears `TenantContext` |
| `TenantIdentifierResolver` | Hibernate SPI — resolves the active tenant for each Hibernate session |
| `TenantConnectionProvider` | Hibernate SPI — executes `SET search_path` to switch the active schema |
| `DataSourceConfig` | Spring `@Configuration` — wires the custom Hibernate SPI implementations into the `EntityManagerFactory` |
| `TenantSchemaInitializer` | `ApplicationRunner` — creates/verifies tenant schemas and tables at startup |
| `ProductController` | REST API — CRUD endpoints for the `products` resource |
| `ProductService` | Business logic — coordinates between controller and repository |
| `ProductRepository` | Spring Data JPA repository — transparently tenant-scoped |

---

## Running with Docker Compose

All infrastructure (PostgreSQL + the Spring Boot app) runs via Docker Compose. No local PostgreSQL installation is needed.

### Start the stack

```bash
docker compose up --build
```

This will:
1. Build the application image (multi-stage Docker build).
2. Start PostgreSQL (`postgres:16-alpine`).
3. Start the Spring Boot app once PostgreSQL is healthy.
4. `TenantSchemaInitializer` creates schemas `tenant_alpha` and `tenant_beta` with the `products` table in each.

### Start in the background

```bash
docker compose up --build -d
```

### Follow logs

```bash
docker compose logs -f          # all services
docker compose logs -f app      # app only
docker compose logs -f postgres # PostgreSQL only
```

### Stop the stack

```bash
docker compose down         # stop containers (data volume preserved)
docker compose down -v      # stop containers AND delete data volume
```

### Health check

```bash
curl http://localhost:8080/actuator/health
```

---

## API Usage — curl Examples

The API base URL is `http://localhost:8080/api/products`. **Every request must include the `X-Tenant-ID` header.**

---

### List all products for a tenant

```bash
curl -s http://localhost:8080/api/products \
  -H "X-Tenant-ID: tenant_alpha" | jq
```

### Search products by keyword

```bash
curl -s "http://localhost:8080/api/products?search=laptop" \
  -H "X-Tenant-ID: tenant_alpha" | jq
```

---

### Create a product for tenant_alpha

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "X-Tenant-ID: tenant_alpha" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop Pro",
    "description": "High-performance laptop",
    "price": 1299.99,
    "stockQuantity": 10
  }' | jq
```

### Create a product for tenant_beta

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "X-Tenant-ID: tenant_beta" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Budget Tablet",
    "description": "Entry-level tablet",
    "price": 199.99,
    "stockQuantity": 50
  }' | jq
```

---

### Demonstrate schema isolation

Create a product for `tenant_alpha`, then prove it is invisible to `tenant_beta`:

```bash
# Step 1: Create a product for tenant_alpha
curl -s -X POST http://localhost:8080/api/products \
  -H "X-Tenant-ID: tenant_alpha" \
  -H "Content-Type: application/json" \
  -d '{"name": "Alpha-Only Widget", "price": 9.99, "stockQuantity": 100}' | jq

# Step 2: List products for tenant_alpha — "Alpha-Only Widget" appears
curl -s http://localhost:8080/api/products \
  -H "X-Tenant-ID: tenant_alpha" | jq

# Step 3: List products for tenant_beta — "Alpha-Only Widget" does NOT appear
curl -s http://localhost:8080/api/products \
  -H "X-Tenant-ID: tenant_beta" | jq
```

---

### Get a product by ID

```bash
curl -s http://localhost:8080/api/products/1 \
  -H "X-Tenant-ID: tenant_alpha" | jq
```

### Update a product

```bash
curl -s -X PUT http://localhost:8080/api/products/1 \
  -H "X-Tenant-ID: tenant_alpha" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop Pro Max",
    "description": "Updated model",
    "price": 1499.99,
    "stockQuantity": 5
  }' | jq
```

### Delete a product

```bash
curl -s -X DELETE http://localhost:8080/api/products/1 \
  -H "X-Tenant-ID: tenant_alpha" -v
```

---

### Error cases

**Missing X-Tenant-ID header → 400 Bad Request:**

```bash
curl -s http://localhost:8080/api/products
# HTTP 400 Bad Request
```

**Product not found in tenant's schema → 404 Not Found:**

```bash
curl -s http://localhost:8080/api/products/999 \
  -H "X-Tenant-ID: tenant_alpha"
# HTTP 404 Not Found
```

**Product ID from tenant_alpha is invisible to tenant_beta:**

```bash
# Assuming product with id=1 exists only in tenant_alpha
curl -s http://localhost:8080/api/products/1 \
  -H "X-Tenant-ID: tenant_beta"
# HTTP 404 Not Found — schema isolation confirmed
```

---

## Adding a New Tenant

1. Add the new tenant ID to `docker-compose.yml`:
   ```yaml
   APP_TENANTS: tenant_alpha,tenant_beta,tenant_gamma
   ```
2. Restart the app:
   ```bash
   docker compose up -d app
   ```
   `TenantSchemaInitializer` creates the new schema and tables automatically.

Or, for the standalone app, add it to `application.yml`:
```yaml
app:
  tenants:
    - tenant_alpha
    - tenant_beta
    - tenant_gamma
```

---

## Running the Tests

Tests do **not** require Docker Compose to be running. Integration tests start their own PostgreSQL container automatically via Testcontainers.

### Prerequisites

- Docker must be running (Testcontainers needs it to start the PostgreSQL container).
- Java 21+.

### Run all tests

```bash
./mvnw clean test
```

### Test structure

| Test Class | Type | What it tests |
|---|---|---|
| `TenantContextTest` | Unit | Thread-local lifecycle: set, get, clear, thread isolation |
| `TenantIdentifierResolverTest` | Unit | Tenant resolution from context, default fallback |
| `TenantConnectionProviderTest` | Unit | Schema name validation and SQL injection prevention |
| `ProductServiceTest` | Unit | Service CRUD logic with mocked repository |
| `MultitenancyIntegrationTest` | Integration | Full HTTP → PostgreSQL flow; schema isolation proof |

### Integration test infrastructure

`MultitenancyIntegrationTest` uses:
- **Testcontainers** (`PostgreSQLContainer`) — starts a real `postgres:16-alpine` container.
- **`@DynamicPropertySource`** — injects the container's JDBC URL into the Spring context before startup.
- **Spring Boot Test + MockMvc** — tests the full HTTP request/response cycle including tenant routing.

The integration tests prove schema isolation: a product created for `tenant_alpha` returns 404 when fetched via `tenant_beta`.

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/multitenancy/
│   │   ├── MultitenancyDbPatternApplication.java   ← Entry point
│   │   ├── config/
│   │   │   ├── DataSourceConfig.java               ← JPA EntityManagerFactory + Hibernate multi-tenancy setup
│   │   │   ├── TenantSchemaInitializer.java         ← Creates schemas and tables at startup
│   │   │   └── WebMvcConfig.java                   ← Registers TenantInterceptor
│   │   ├── domain/
│   │   │   ├── Product.java                        ← JPA entity
│   │   │   ├── ProductRepository.java              ← Spring Data JPA repository
│   │   │   └── ProductService.java                 ← Business logic service
│   │   ├── tenant/
│   │   │   ├── TenantConnectionProvider.java       ← Hibernate SPI: SET search_path per tenant
│   │   │   ├── TenantContext.java                  ← Thread-local tenant ID holder
│   │   │   └── TenantIdentifierResolver.java       ← Hibernate SPI: resolve tenant from context
│   │   └── web/
│   │       ├── controller/
│   │       │   └── ProductController.java          ← REST API (CRUD)
│   │       ├── dto/
│   │       │   └── ProductRequest.java             ← Request DTO (record)
│   │       └── interceptor/
│   │           └── TenantInterceptor.java          ← Reads X-Tenant-ID header
│   └── resources/
│       ├── application.yml                         ← Application configuration
│       └── db/
│           └── schema.sql                          ← DDL applied to each tenant schema
└── test/
    ├── java/com/example/multitenancy/
    │   ├── domain/
    │   │   └── ProductServiceTest.java             ← Unit tests (Mockito)
    │   ├── integration/
    │   │   └── MultitenancyIntegrationTest.java    ← Integration tests (Testcontainers)
    │   └── tenant/
    │       ├── TenantConnectionProviderTest.java   ← Unit tests
    │       ├── TenantContextTest.java              ← Unit tests
    │       └── TenantIdentifierResolverTest.java   ← Unit tests
    └── resources/
        ├── application-test.yml                    ← Test profile config
        ├── docker-java.properties                  ← Docker API version fix for Docker Desktop 29+
        └── testcontainers.properties               ← Testcontainers Docker API version
```
