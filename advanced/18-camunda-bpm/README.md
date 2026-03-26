# Camunda BPM — Spring Boot Order Fulfilment

A Spring Boot application that orchestrates an e-commerce order fulfilment pipeline using the **Camunda 7 BPM** workflow engine. Business process logic is defined as a **BPMN 2.0** diagram and executed by the embedded Camunda process engine.

## What This Project Demonstrates

- **Camunda 7 Community Edition** embedded in Spring Boot via `camunda-bpm-spring-boot-starter`
- **BPMN 2.0 process definition** with service tasks and exclusive gateways
- **Java Delegate pattern** — each process step is implemented as a Spring-managed `JavaDelegate`
- **Process variables** — data is passed between delegates via Camunda's execution context
- **Synchronous process execution** — the entire pipeline runs in a single HTTP request/response
- **Spring Data JPA** — order persistence with PostgreSQL
- **Flyway** — database schema migration
- **Camunda REST API** — inspect process instances and history via HTTP
- **JUnit 5 unit tests** — delegates and domain logic tested in isolation with Mockito
- **Testcontainers integration tests** — full pipeline tested against a real PostgreSQL container

## Business Process: Order Fulfilment

When a client submits an order, the Camunda engine starts a process instance and synchronously executes four service tasks:

```
Start
  │
  ▼
[Check Inventory] ──── inventoryAvailable=false ──→ [End: Order Failed]
  │
  │ inventoryAvailable=true
  ▼
[Process Payment] ──── paymentSuccess=false ──────→ [End: Order Failed]
  │
  │ paymentSuccess=true
  ▼
[Schedule Shipment]
  │
  ▼
[Send Notification]
  │
  ▼
[End: Order Fulfilled]
```

**Order status progression (happy path):**
```
PENDING → INVENTORY_CHECKED → PAYMENT_PROCESSED → SHIPPED → COMPLETED
```

**On any failure:** `FAILED`

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (via wrapper) |
| Docker | 24+ (for Docker Compose) |
| Docker Compose | V2 (`docker compose`) |

> **Tests only:** Docker must be running (Testcontainers pulls `postgres:16-alpine` automatically).

## Project Structure

```
src/
├── main/
│   ├── java/com/example/camundabpm/
│   │   ├── CamundaBpmApplication.java        # Spring Boot entry point
│   │   ├── controller/
│   │   │   └── OrderController.java          # REST API: POST/GET /api/orders
│   │   ├── delegate/
│   │   │   ├── InventoryCheckDelegate.java   # Service task 1: check stock
│   │   │   ├── PaymentProcessingDelegate.java # Service task 2: charge customer
│   │   │   ├── ShippingDelegate.java          # Service task 3: assign tracking
│   │   │   └── NotificationDelegate.java     # Service task 4: confirm order
│   │   ├── domain/
│   │   │   ├── Order.java                    # JPA entity
│   │   │   └── OrderStatus.java              # Lifecycle enum
│   │   ├── dto/
│   │   │   ├── CreateOrderRequest.java       # Validated request DTO
│   │   │   └── OrderResponse.java            # Response DTO
│   │   ├── repository/
│   │   │   └── OrderRepository.java          # Spring Data JPA repository
│   │   └── service/
│   │       └── OrderService.java             # Bridges REST layer ↔ Camunda
│   └── resources/
│       ├── application.yml                   # Main configuration
│       ├── order-fulfilment.bpmn             # BPMN 2.0 process definition
│       └── db/migration/
│           └── V1__create_orders_table.sql   # Flyway schema migration
└── test/
    ├── java/com/example/camundabpm/
    │   ├── delegate/
    │   │   ├── InventoryCheckDelegateTest.java    # Unit test (Mockito)
    │   │   └── PaymentProcessingDelegateTest.java # Unit test (Mockito)
    │   ├── domain/
    │   │   └── OrderTest.java                    # Unit test (pure Java)
    │   └── integration/
    │       └── OrderFulfilmentIntegrationTest.java # Integration test (Testcontainers)
    └── resources/
        ├── application-test.yml               # H2 config for unit tests
        ├── application-integration-test.yml   # PostgreSQL config for integration tests
        ├── docker-java.properties             # Docker API v1.44 fix
        └── testcontainers.properties          # Testcontainers Docker API fix
```

## Running with Docker Compose

The entire application stack (PostgreSQL + Spring Boot) runs via Docker Compose. **No local Java installation required** — Maven and the JDK are inside the Docker build stage.

### Start

```bash
# Build the app image and start all services (foreground)
docker compose up --build

# Or in detached (background) mode
docker compose up --build -d
```

Docker Compose starts two services:
- **postgres** — PostgreSQL 16 on port `5432`
- **app** — Spring Boot on port `8080` (waits for postgres to be healthy)

### Stop

```bash
docker compose down
```

### Follow logs

```bash
# All services
docker compose logs -f

# Only the app
docker compose logs -f app
```

### Check health

```bash
curl http://localhost:8080/actuator/health
```

## REST API

The application exposes port `8080`. All endpoints are under `/api/orders`.

### Submit a new order

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Alice",
    "productName": "Laptop Pro 15",
    "quantity": 2,
    "unitPrice": 1299.99
  }' | jq .
```

**Response (HTTP 201 Created):**
```json
{
  "id": 1,
  "customerName": "Alice",
  "productName": "Laptop Pro 15",
  "quantity": 2,
  "unitPrice": 1299.99,
  "totalAmount": 2599.98,
  "status": "COMPLETED",
  "processInstanceId": "a1b2c3d4-e5f6-...",
  "trackingNumber": "TRK-F3A2B1C0-...",
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:00Z",
  "errorMessage": null
}
```

> The response already shows `COMPLETED` because all four service tasks execute synchronously before the HTTP response is returned.

### Get all orders

```bash
curl -s http://localhost:8080/api/orders | jq .
```

### Get orders filtered by status

```bash
# Completed orders
curl -s "http://localhost:8080/api/orders?status=COMPLETED" | jq .

# Failed orders
curl -s "http://localhost:8080/api/orders?status=FAILED" | jq .
```

### Get a single order by ID

```bash
curl -s http://localhost:8080/api/orders/1 | jq .
```

### Validation error example

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "",
    "productName": "Laptop",
    "quantity": 0,
    "unitPrice": -1
  }' | jq .
```

**Response (HTTP 400 Bad Request)** — returns validation constraint violations.

### Camunda REST API

The embedded Camunda REST API is available at `/engine-rest`:

```bash
# List deployed process definitions
curl -s http://localhost:8080/engine-rest/process-definition | jq .

# List all completed process instances (history)
curl -s "http://localhost:8080/engine-rest/history/process-instance?state=COMPLETED" | jq .

# Get process variables for a specific instance (replace with actual ID)
curl -s "http://localhost:8080/engine-rest/history/variable-instance?processInstanceId=<id>" | jq .
```

## Running the Tests

Tests require Docker to be running (for Testcontainers in the integration test).

```bash
./mvnw clean test
```

### Test categories

| Test class | Type | Infrastructure |
|---|---|---|
| `OrderTest` | Unit | None (pure Java) |
| `InventoryCheckDelegateTest` | Unit | None (Mockito) |
| `PaymentProcessingDelegateTest` | Unit | None (Mockito) |
| `OrderFulfilmentIntegrationTest` | Integration | Testcontainers PostgreSQL |

### What the integration test verifies

- Happy path: order submitted → process completes → status is `COMPLETED`
- Tracking number is assigned (`TRK-...` prefix)
- Total amount is correctly computed (`quantity × unitPrice`)
- Camunda process instance ID is stored on the order
- Orders can be retrieved by ID and filtered by status

### Run only unit tests (no Docker required)

```bash
./mvnw test -Dtest="OrderTest,InventoryCheckDelegateTest,PaymentProcessingDelegateTest"
```

### Run only integration tests

```bash
./mvnw test -Dtest="OrderFulfilmentIntegrationTest"
```

## Key Concepts

### Java Delegate Pattern
Each BPMN service task is linked to a Spring bean by name:
```xml
<serviceTask camunda:delegateExpression="${inventoryCheckDelegate}"/>
```
Camunda looks up the bean in the Spring context and calls `execute(DelegateExecution)`.

### Process Variables
Data flows between delegates via Camunda's execution context:
```java
// Write a variable (in a delegate)
execution.setVariable("inventoryAvailable", true);

// Read a variable (in a subsequent delegate)
boolean available = (Boolean) execution.getVariable("inventoryAvailable");
```

### Exclusive Gateways
The BPMN diagram uses exclusive gateways (XOR) to branch based on delegate results:
```xml
<conditionExpression>${inventoryAvailable == true}</conditionExpression>
```

### Synchronous Execution
Because all service tasks have no `camunda:asyncBefore/asyncAfter` markers, the entire process runs synchronously in the calling thread. `startProcessInstanceByKey()` returns only after all tasks complete.
