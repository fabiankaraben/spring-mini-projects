# gRPC Server Integration

A Spring Boot mini-project that exposes highly efficient, strongly-typed RPC endpoints
using **gRPC** and **Protocol Buffers**. The domain demonstrates a **Product Catalog service**
for an e-commerce platform, with unary and server-streaming RPC methods.

---

## What Is gRPC?

**gRPC** (Google Remote Procedure Call) is a modern, high-performance RPC framework. It uses:

| Feature | Detail |
|---|---|
| **Protocol Buffers** | Binary serialization format — much smaller and faster than JSON |
| **HTTP/2** | Transport layer — enables multiplexing, header compression, bidirectional streams |
| **Code generation** | `protoc` generates client stubs and server base classes from `.proto` files |
| **Strongly typed** | Contracts are enforced at compile time — no more runtime JSON schema surprises |

### Why gRPC over REST?

- **Compact payloads** — binary encoding is typically 3–10× smaller than JSON
- **Fast serialization** — protobuf marshalling is significantly faster
- **Streaming** — native server/client/bidirectional streaming (not possible with standard REST)
- **Type safety** — generated code prevents mismatched field names or types
- **Ideal for microservices** — internal service-to-service communication

---

## Domain: Product Catalog Service

The service manages products in an e-commerce catalog. Products have:

| Field | Type | Description |
|---|---|---|
| `id` | `int64` | Auto-assigned by the server |
| `name` | `string` | Product name |
| `description` | `string` | Short description |
| `category` | `string` | e.g., `electronics`, `furniture`, `books` |
| `price` | `double` | Unit price in USD |
| `stock_quantity` | `int32` | Current stock count |
| `status` | `ProductStatus` | `ACTIVE`, `OUT_OF_STOCK`, or `DISCONTINUED` |

### Product Lifecycle (Status Transitions)

```
ACTIVE ──── UpdateStock(qty=0) ────► OUT_OF_STOCK
OUT_OF_STOCK ── UpdateStock(qty>0) ─► ACTIVE
ACTIVE / OUT_OF_STOCK ── DeleteProduct ──► DISCONTINUED (soft-delete)
```

### gRPC Service Methods

| Method | Type | Description |
|---|---|---|
| `GetProduct` | Unary | Retrieve one product by ID |
| `ListProducts` | Server-streaming | Stream all products (optional category filter) |
| `CreateProduct` | Unary | Create a new product |
| `UpdateStock` | Unary | Update stock quantity (with auto status transition) |
| `DeleteProduct` | Unary | Soft-delete a product (sets status → DISCONTINUED) |

---

## Project Structure

```
14-grpc-server-integration/
├── src/
│   ├── main/
│   │   ├── proto/
│   │   │   └── product_catalog.proto       # gRPC/protobuf service definition
│   │   ├── java/com/example/grpc/
│   │   │   ├── GrpcServerApplication.java  # Spring Boot entry point
│   │   │   ├── config/
│   │   │   │   └── DataInitializer.java    # Seeds H2 with sample products
│   │   │   ├── domain/
│   │   │   │   ├── Product.java            # JPA entity
│   │   │   │   └── ProductStatus.java      # Domain enum
│   │   │   ├── repository/
│   │   │   │   └── ProductRepository.java  # Spring Data JPA repository
│   │   │   ├── service/
│   │   │   │   └── ProductService.java     # Business logic (no gRPC dependency)
│   │   │   ├── mapper/
│   │   │   │   └── ProductMapper.java      # JPA entity ↔ protobuf message converter
│   │   │   └── grpc/
│   │   │       └── ProductCatalogGrpcService.java  # @GrpcService implementation
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/com/example/grpc/
│       │   ├── service/
│       │   │   └── ProductServiceTest.java          # Unit tests (Mockito, no Spring)
│       │   ├── mapper/
│       │   │   └── ProductMapperTest.java           # Unit tests (plain JUnit 5)
│       │   └── integration/
│       │       └── ProductCatalogIntegrationTest.java  # Full gRPC integration tests
│       └── resources/
│           ├── application-test.yml           # Test profile (gRPC port 19090)
│           ├── docker-java.properties         # Docker Desktop 29+ fix
│           └── testcontainers.properties      # Docker API version config
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

Generated protobuf/gRPC Java classes are placed in:
```
target/generated-sources/protobuf/java/
```

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (or use `./mvnw`) |
| Docker | 24+ (for Docker Compose) |
| Docker Desktop | 29+ (for Testcontainers) |
| **grpcurl** | Latest (for CLI testing — optional) |

### Installing grpcurl

`grpcurl` is a command-line tool for interacting with gRPC servers (like `curl` for REST).

```bash
# macOS (Homebrew)
brew install grpcurl

# Linux
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
```

---

## Running with Docker Compose

The entire application runs inside Docker via Docker Compose. No local Java installation is needed for running — only for development.

```bash
# Build the Docker image and start the container
docker compose up --build

# Run in detached (background) mode
docker compose up --build -d

# View logs
docker compose logs -f

# Stop and remove containers
docker compose down
```

Two ports are exposed after startup:

| Port | Protocol | Purpose |
|---|---|---|
| `8080` | HTTP | Spring Actuator (`/actuator/health`, `/actuator/info`) |
| `9090` | gRPC (HTTP/2) | `ProductCatalogService` — all RPC methods |

---

## Interacting with the gRPC Server

All interactions use **grpcurl**, which communicates via gRPC (not HTTP/REST).
The server has **gRPC reflection** enabled, so no `.proto` file copy is needed.

> **Note:** The database is seeded with 8 sample products at startup (electronics, furniture, books).

### Discover Available Services

```bash
# List all services registered on the gRPC server
grpcurl -plaintext localhost:9090 list

# Describe the ProductCatalogService and all its methods
grpcurl -plaintext localhost:9090 describe product.ProductCatalogService

# Describe the Product message type
grpcurl -plaintext localhost:9090 describe product.Product
```

### CreateProduct (unary RPC)

```bash
grpcurl -plaintext \
  -d '{
    "name": "Wireless Mouse",
    "description": "Ergonomic 3-button wireless mouse, 2.4GHz USB dongle",
    "category": "electronics",
    "price": 29.99,
    "stock_quantity": 200
  }' \
  localhost:9090 product.ProductCatalogService/CreateProduct
```

Expected response:
```json
{
  "id": "9",
  "name": "Wireless Mouse",
  "description": "Ergonomic 3-button wireless mouse, 2.4GHz USB dongle",
  "category": "electronics",
  "price": 29.99,
  "stockQuantity": 200,
  "status": "PRODUCT_STATUS_ACTIVE"
}
```

### GetProduct (unary RPC)

```bash
# Retrieve product with ID 1
grpcurl -plaintext \
  -d '{"id": 1}' \
  localhost:9090 product.ProductCatalogService/GetProduct
```

```bash
# Attempt to get a non-existent product (returns NOT_FOUND error)
grpcurl -plaintext \
  -d '{"id": 999}' \
  localhost:9090 product.ProductCatalogService/GetProduct
```

### ListProducts — All Products (server-streaming RPC)

```bash
# Stream all active/out-of-stock products (DISCONTINUED excluded)
grpcurl -plaintext \
  -d '{}' \
  localhost:9090 product.ProductCatalogService/ListProducts
```

### ListProducts — Filter by Category (server-streaming RPC)

```bash
# Stream only "electronics" products
grpcurl -plaintext \
  -d '{"category": "electronics"}' \
  localhost:9090 product.ProductCatalogService/ListProducts

# Stream only "books" products
grpcurl -plaintext \
  -d '{"category": "books"}' \
  localhost:9090 product.ProductCatalogService/ListProducts
```

### UpdateStock (unary RPC)

```bash
# Set product 1 stock to 0 → status automatically becomes OUT_OF_STOCK
grpcurl -plaintext \
  -d '{"id": 1, "new_stock_quantity": 0}' \
  localhost:9090 product.ProductCatalogService/UpdateStock

# Replenish stock → status automatically restores to ACTIVE
grpcurl -plaintext \
  -d '{"id": 1, "new_stock_quantity": 50}' \
  localhost:9090 product.ProductCatalogService/UpdateStock
```

### DeleteProduct — Soft Delete (unary RPC)

```bash
# Soft-delete product 1 (sets status to DISCONTINUED)
grpcurl -plaintext \
  -d '{"id": 1}' \
  localhost:9090 product.ProductCatalogService/DeleteProduct
```

Expected response:
```json
{
  "success": true,
  "message": "Product with id 1 has been discontinued."
}
```

```bash
# After soft-delete, the product is excluded from ListProducts but still retrievable:
grpcurl -plaintext \
  -d '{"id": 1}' \
  localhost:9090 product.ProductCatalogService/GetProduct
# Returns the product with status = PRODUCT_STATUS_DISCONTINUED
```

### Actuator Health Check

```bash
# Verify the application is running (HTTP, not gRPC)
curl http://localhost:8080/actuator/health
```

---

## Running Locally (without Docker)

```bash
# Compile and generate protobuf Java stubs, then start the app
./mvnw spring-boot:run
```

The application starts two servers:
- HTTP Actuator: `http://localhost:8080`
- gRPC server: `localhost:9090`

---

## Running the Tests

```bash
./mvnw clean test
```

### Test Structure

#### Unit Tests (no Spring context, no Docker)

| Test Class | What it tests |
|---|---|
| `ProductServiceTest` | All business logic in `ProductService` — status transitions, stock rules, error cases |
| `ProductMapperTest` | All field mappings in `ProductMapper` — JPA entity ↔ protobuf message, enum conversions |

These tests use **Mockito** (for `ProductServiceTest`) and plain JUnit 5 (for `ProductMapperTest`).
They run in milliseconds because they don't start any Spring context.

#### Integration Tests (full Spring Boot context + live gRPC server)

| Test Class | What it tests |
|---|---|
| `ProductCatalogIntegrationTest` | Full gRPC stack: client stub → gRPC service → Spring service → H2 database |

The integration test:
1. Starts the full Spring Boot application context (HTTP + gRPC servers).
2. Creates a real `ManagedChannel` connecting to `localhost:19090` (test profile port).
3. Calls each gRPC method via a blocking stub — exactly as a real client would.
4. Verifies responses, status transitions, streaming behaviour, and error codes.
5. Uses `@Testcontainers` to ensure Docker is available and Testcontainers lifecycle hooks are active.

The test profile (`application-test.yml`) uses gRPC port `19090` to avoid clashing with a
running dev server on port `9090`.

---

## Key Technologies

| Technology | Role |
|---|---|
| **Spring Boot 3.4** | Application framework, Actuator |
| **net.devh:grpc-server-spring-boot-starter** | Auto-configures Netty gRPC server, `@GrpcService` scanner |
| **Protocol Buffers 3** | Binary serialization format; `.proto` file is the service contract |
| **protobuf-maven-plugin** | Invokes `protoc` at build time to generate Java stubs from `.proto` |
| **gRPC Java 1.68** | gRPC runtime (Netty transport, stub generation) |
| **Spring Data JPA + H2** | Persistence layer (in-memory database for demo) |
| **JUnit 5** | Test framework |
| **Mockito** | Mocking framework for unit tests |
| **Testcontainers** | Integration test infrastructure |
| **Docker / Docker Compose** | Container runtime for production-like deployments |

---

## How gRPC Code Generation Works

1. You write `src/main/proto/product_catalog.proto` — the service contract.
2. Running `./mvnw compile` triggers `protobuf-maven-plugin`, which:
   - Downloads the correct `protoc` binary for your OS/architecture (via `os-maven-plugin`).
   - Runs `protoc` to generate `Product.java`, `CreateProductRequest.java`, etc.
   - Runs `protoc-gen-grpc-java` to generate `ProductCatalogServiceGrpc.java` (stubs + base class).
3. Generated files appear in `target/generated-sources/protobuf/java/` and are automatically compiled.
4. Your service implementation (`ProductCatalogGrpcService`) extends the generated `ImplBase`.
