# gRPC Client Interaction

A Spring Boot backend demonstrating the **client side** of gRPC communication.

The application embeds two gRPC microservices (Order and Inventory) and exposes a REST API gateway that consumes them internally via `@GrpcClient`-injected stubs — demonstrating unary RPCs, server-streaming RPCs, multi-service orchestration, and gRPC error propagation.

---

## What This Project Demonstrates

| Concept | Where to Look |
|---|---|
| `@GrpcClient` stub injection | `OrderGatewayService.java` |
| Unary gRPC call (client → server → response) | `getOrder()`, `checkStock()`, `reserveStock()` |
| Server-streaming gRPC (Iterator pattern) | `listOrders()`, `listInventory()` |
| Multi-service orchestration with compensation | `createOrderWithInventoryCheck()` |
| gRPC error propagation (StatusRuntimeException → HTTP) | `OrderController.handleGrpcException()` |
| Proto file design (proto3) | `src/main/proto/*.proto` |
| Protobuf ↔ JPA entity mapping | `OrderMapper.java`, `InventoryMapper.java` |
| gRPC server implementation (`@GrpcService`) | `OrderGrpcService.java`, `InventoryGrpcService.java` |

---

## Architecture

```
HTTP Client (curl / browser)
        │  JSON  (port 8080)
        ▼
  OrderController  (REST layer)
        │
  OrderGatewayService  (@GrpcClient stubs)
        │
        ├──gRPC──► OrderGrpcService      (port 9091)
        │              │
        │          OrderService  →  H2 DB (orders table)
        │
        └──gRPC──► InventoryGrpcService  (port 9091)
                       │
                   InventoryService  →  H2 DB (inventory_items table)
```

Both gRPC services run on the **same Netty server** (port 9091). The `net.devh` grpc-server-spring-boot-starter registers all `@GrpcService` beans on a single server. The gRPC client connects to `localhost:9091` for both services.

---

## Requirements

- **Java 21+**
- **Maven 3.9+** (or use the included `./mvnw` wrapper — no installation required)
- **Docker** (optional — only needed to run via Docker Compose)

---

## Running the Application

### Option 1 — Maven (recommended for development)

```bash
./mvnw spring-boot:run
```

The application starts:
- REST API at `http://localhost:8080`
- gRPC server at `localhost:9091`
- H2 console at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:grpcclientdb`)

### Option 2 — Docker Compose

```bash
docker compose up --build
```

This builds the Docker image and starts the container. The application is available at the same ports.

```bash
# Stop the container
docker compose down
```

---

## API Usage Examples

> **Prerequisite**: The application must be running (`./mvnw spring-boot:run` or `docker compose up`).
> Inventory is pre-seeded with sample SKUs on startup.

### Inventory Endpoints

**List all inventory items:**
```bash
curl http://localhost:8080/api/inventory
```

**List only items with available stock:**
```bash
curl "http://localhost:8080/api/inventory?onlyAvailable=true"
```

**Check stock for a specific SKU:**
```bash
curl http://localhost:8080/api/inventory/SKU-LAPTOP-001
```

**Reserve stock:**
```bash
curl -X POST http://localhost:8080/api/inventory/SKU-LAPTOP-001/reserve \
  -H "Content-Type: application/json" \
  -d '{"quantity": 2, "orderId": "my-order-001"}'
```

**Release reserved stock:**
```bash
curl -X POST http://localhost:8080/api/inventory/SKU-LAPTOP-001/release \
  -H "Content-Type: application/json" \
  -d '{"quantity": 2, "orderId": "my-order-001"}'
```

---

### Order Endpoints

**Create an order** (with automatic inventory check and reservation):
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-001",
    "items": [
      {
        "sku": "SKU-LAPTOP-001",
        "productName": "Laptop Pro 15",
        "quantity": 1,
        "unitPrice": 999.99
      },
      {
        "sku": "SKU-MOUSE-001",
        "productName": "Wireless Mouse",
        "quantity": 2,
        "unitPrice": 29.99
      }
    ]
  }'
```

> The `createOrder` endpoint calls `InventoryService.ReserveStock` (gRPC) for each item before calling `OrderService.CreateOrder` (gRPC). If any reservation fails, all reservations are released (compensation pattern).

**Get a specific order by ID:**
```bash
curl http://localhost:8080/api/orders/{orderId}
```

**List all orders for a customer:**
```bash
curl "http://localhost:8080/api/orders?customerId=customer-001"
```

---

### Testing Insufficient Stock (Error Scenario)

The pre-seeded SKU `SKU-CONSOLE-001` has all 10 units reserved (0 available). Ordering it demonstrates the 422 Unprocessable Entity response:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-002",
    "items": [{"sku": "SKU-CONSOLE-001", "productName": "Gaming Console", "quantity": 1, "unitPrice": 399.99}]
  }'
```

Expected response (HTTP 422):
```json
{"error": "Insufficient stock for 1 item(s)"}
```

---

### Actuator

```bash
# Application health
curl http://localhost:8080/actuator/health

# Application info
curl http://localhost:8080/actuator/info
```

---

### Using grpcurl (optional — direct gRPC calls)

If you have [grpcurl](https://github.com/fullstorydev/grpcurl) installed, you can call the gRPC services directly:

```bash
# List all available gRPC services (reflection must be enabled)
grpcurl -plaintext localhost:9091 list

# List methods of OrderService
grpcurl -plaintext localhost:9091 list order.OrderService

# Call GetOrder
grpcurl -plaintext -d '{"order_id": "<your-order-uuid>"}' \
  localhost:9091 order.OrderService/GetOrder

# Call CheckStock
grpcurl -plaintext -d '{"sku": "SKU-LAPTOP-001"}' \
  localhost:9091 inventory.InventoryService/CheckStock

# Call ListInventory (server-streaming)
grpcurl -plaintext -d '{"only_available": false}' \
  localhost:9091 inventory.InventoryService/ListInventory
```

---

## Pre-seeded Inventory

| SKU | Product | Total | Reserved | Available |
|---|---|---|---|---|
| SKU-LAPTOP-001 | Laptop Pro 15 | 50 | 0 | 50 |
| SKU-LAPTOP-002 | Laptop Air 13 | 30 | 0 | 30 |
| SKU-MOUSE-001 | Wireless Mouse | 200 | 0 | 200 |
| SKU-KEYBOARD-001 | Mechanical Keyboard | 150 | 0 | 150 |
| SKU-MONITOR-001 | 4K Monitor 27" | 40 | 0 | 40 |
| SKU-HEADPHONES-001 | ANC Headphones | 80 | 0 | 80 |
| SKU-WEBCAM-001 | HD Webcam 1080p | 60 | 0 | 60 |
| SKU-GPU-001 | Graphics Card RTX | 5 | 0 | 5 |
| SKU-CONSOLE-001 | Gaming Console | 10 | 10 | 0 ← out of stock |

---

## Running Tests

### Unit Tests + Integration Tests (all tests)

```bash
./mvnw clean test
```

Unit tests run without Docker or network access. Integration tests start the full Spring Boot application context with an embedded gRPC server and use a real `ManagedChannel` to make gRPC calls.

### Unit Tests Only

```bash
./mvnw test -Dtest="OrderServiceTest,InventoryServiceTest"
```

### Integration Tests Only

```bash
./mvnw test -Dtest="GrpcClientIntegrationTest"
```

> **Note**: Integration tests require Docker to be running (for Testcontainers Docker availability checks). The tests themselves use H2 in-memory database — no actual container is started for the database.

---

## Project Structure

```
src/
├── main/
│   ├── proto/
│   │   ├── order_service.proto         # gRPC service definition for OrderService
│   │   └── inventory_service.proto     # gRPC service definition for InventoryService
│   ├── java/com/example/grpcclient/
│   │   ├── GrpcClientInteractionApplication.java  # Spring Boot entry point
│   │   ├── client/
│   │   │   ├── OrderGatewayService.java  # @GrpcClient stubs — the core demo
│   │   │   └── GrpcServiceException.java # Application exception wrapper
│   │   ├── config/
│   │   │   └── DataInitializer.java      # Seeds sample inventory on startup
│   │   ├── domain/
│   │   │   ├── Order.java               # JPA entity
│   │   │   ├── OrderLineItem.java        # JPA entity
│   │   │   ├── OrderStatus.java          # Enum (mirrors protobuf enum)
│   │   │   └── InventoryItem.java        # JPA entity
│   │   ├── grpc/
│   │   │   ├── OrderGrpcService.java     # @GrpcService — OrderService server stub
│   │   │   └── InventoryGrpcService.java # @GrpcService — InventoryService server stub
│   │   ├── mapper/
│   │   │   ├── OrderMapper.java          # JPA entity ↔ protobuf message
│   │   │   └── InventoryMapper.java      # JPA entity ↔ protobuf message
│   │   ├── repository/
│   │   │   ├── OrderRepository.java      # Spring Data JPA
│   │   │   └── InventoryItemRepository.java
│   │   ├── service/
│   │   │   ├── OrderService.java         # Business logic
│   │   │   └── InventoryService.java     # Business logic
│   │   └── web/
│   │       └── OrderController.java      # REST API (JSON ↔ gRPC translation)
│   └── resources/
│       └── application.yml               # Server ports, gRPC client channels, H2
└── test/
    ├── java/com/example/grpcclient/
    │   ├── integration/
    │   │   └── GrpcClientIntegrationTest.java  # Full gRPC call stack tests
    │   └── service/
    │       ├── OrderServiceTest.java            # Unit tests (Mockito)
    │       └── InventoryServiceTest.java        # Unit tests (Mockito)
    └── resources/
        ├── application-test.yml    # Test profile (port 19091, separate H2 DB)
        ├── docker-java.properties  # Docker API version fix for Docker Desktop 29+
        └── testcontainers.properties
```

---

## Key Dependencies

| Dependency | Purpose |
|---|---|
| `net.devh:grpc-server-spring-boot-starter` | Auto-configures Netty gRPC server, `@GrpcService` registration |
| `net.devh:grpc-client-spring-boot-starter` | Auto-configures gRPC channels, `@GrpcClient` stub injection |
| `io.grpc:grpc-protobuf` | gRPC + protobuf runtime |
| `com.google.protobuf:protobuf-java` | Protobuf message generation |
| `org.springframework.boot:spring-boot-starter-data-jpa` | JPA repositories and Hibernate ORM |
| `com.h2database:h2` | In-memory database (no external DB needed) |
| `org.testcontainers:testcontainers` | Integration test infrastructure |
