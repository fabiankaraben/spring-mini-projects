# Saga Pattern — Choreography

A complete, runnable example of the **Saga Pattern (Choreography style)** using three independent Spring Boot microservices communicating exclusively via Apache Kafka events. No central orchestrator exists — each service reacts to events and publishes its own, forming a distributed transaction through cooperative choreography.

---

## Architecture

```
 Client
   │  POST /api/orders
   ▼
┌─────────────────┐   order.created   ┌──────────────────┐
│  Order Service  │──────────────────▶│ Payment Service  │
│   (port 8081)   │                   │   (port 8082)    │
│                 │◀──────────────────│                  │
│                 │  payment.processed│                  │
│                 │  payment.failed   └──────────────────┘
│                 │
│                 │  payment.processed ┌──────────────────┐
│                 │───────────────────▶│Inventory Service │
│                 │                    │   (port 8083)    │
│                 │◀───────────────────│                  │
│                 │  inventory.reserved│                  │
└─────────────────┘  inventory.failed  └──────────────────┘
```

### Kafka Topics

| Topic                  | Publisher         | Consumers                        |
|------------------------|-------------------|----------------------------------|
| `order.created`        | Order Service     | Payment Service                  |
| `payment.processed`    | Payment Service   | Order Service, Inventory Service |
| `payment.failed`       | Payment Service   | Order Service                    |
| `inventory.reserved`   | Inventory Service | Order Service                    |
| `inventory.failed`     | Inventory Service | Order Service                    |
| `payment.refund`       | Order Service     | Payment Service                  |

### Saga Happy Path

1. Client `POST /api/orders` → Order Service creates order, publishes `order.created`.
2. Payment Service consumes `order.created`, charges the customer, publishes `payment.processed`.
3. Inventory Service consumes `payment.processed`, reserves stock, publishes `inventory.reserved`.
4. Order Service consumes `inventory.reserved` → marks order **COMPLETED**.

### Compensation Flow (Inventory Fails)

1. Steps 1–2 succeed as above.
2. Inventory Service has insufficient stock → publishes `inventory.failed`.
3. Order Service consumes `inventory.failed` → publishes `payment.refund`.
4. Payment Service consumes `payment.refund` → refunds the charge.
5. Order Service marks the order **CANCELLED**.

### Compensation Flow (Payment Fails)

1. Step 1 succeeds as above.
2. Payment Service declines the charge → publishes `payment.failed`.
3. Order Service consumes `payment.failed` → marks order **PAYMENT_FAILED**.

---

## Payment Simulation Rule

To make the saga fully deterministic (no external payment gateway required):

- **Even** integer part of `totalPrice` → payment **succeeds** (e.g., $30.00, $10.50)
- **Odd** integer part of `totalPrice` → payment **fails** (e.g., $29.99, $15.00)

This rule lets you predict saga outcomes from the request, making integration tests straightforward.

---

## Project Structure

```
09-saga-pattern-choreography/
├── pom.xml                      ← Root multi-module POM
├── mvnw / mvnw.cmd              ← Maven wrapper
├── docker-compose.yml           ← Full stack (Kafka + 3× PostgreSQL + 3 services)
├── order-service/               ← Saga initiator
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/example/saga/order/
│       │   ├── domain/          Order, OrderStatus
│       │   ├── events/          OrderCreatedEvent, PaymentProcessedEvent, …
│       │   ├── repository/      OrderRepository
│       │   ├── service/         OrderService
│       │   ├── kafka/           OrderEventConsumer
│       │   ├── web/             OrderController
│       │   └── config/          KafkaConfig
│       └── test/
│           └── …                Unit + integration tests (Testcontainers)
├── payment-service/             ← Saga participant: payment processing
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/example/saga/payment/
│       │   ├── domain/          Payment, PaymentStatus
│       │   ├── events/          OrderCreatedEvent, PaymentProcessedEvent, …
│       │   ├── repository/      PaymentRepository
│       │   ├── service/         PaymentService
│       │   ├── kafka/           PaymentEventConsumer
│       │   ├── web/             PaymentController
│       │   └── config/          KafkaConfig
│       └── test/
│           └── …                Unit + integration tests (Testcontainers)
└── inventory-service/           ← Saga participant: stock reservation
    ├── Dockerfile
    └── src/
        ├── main/java/com/example/saga/inventory/
        │   ├── domain/          ProductStock, Reservation
        │   ├── events/          PaymentProcessedEvent, InventoryReservedEvent, …
        │   ├── repository/      ProductStockRepository, ReservationRepository
        │   ├── service/         InventoryService
        │   ├── kafka/           InventoryEventConsumer
        │   ├── web/             InventoryController
        │   └── config/          KafkaConfig
        └── test/
            └── …                Unit + integration tests (Testcontainers)
```

---

## Prerequisites

- Java 21+
- Maven (or use the included `./mvnw` wrapper)
- Docker Desktop (for `docker compose` and for running Testcontainers tests)

---

## Running Tests

Tests use **Testcontainers** — Docker must be running before executing them.

```bash
# Run all tests for all modules
./mvnw clean test

# Run tests for a specific module
./mvnw clean test -pl order-service
./mvnw clean test -pl payment-service
./mvnw clean test -pl inventory-service
```

> **Note for Docker Desktop 29+**: The `docker-java.properties` and `testcontainers.properties`
> files in each module's `src/test/resources/` override the Docker API version to `1.44`,
> fixing an incompatibility where Docker Desktop 29+ rejects the legacy `/v1.24/info` path.

---

## Running with Docker Compose

```bash
# Build images and start the full stack
docker compose up --build

# Or start in detached mode
docker compose up --build -d

# Watch logs
docker compose logs -f

# Stop and remove containers + volumes
docker compose down -v
```

Services will be available at:

| Service           | URL                              |
|-------------------|----------------------------------|
| Order Service     | http://localhost:8081            |
| Payment Service   | http://localhost:8082            |
| Inventory Service | http://localhost:8083            |

---

## REST API Endpoints

### Order Service — `http://localhost:8081`

#### Create an Order
```http
POST /api/orders
Content-Type: application/json

{
  "customerId": "customer-123",
  "productId": "product-A",
  "quantity": 2,
  "unitPrice": 15.00
}
```

Response `201 Created`:
```json
{
  "orderId": "...",
  "status": "PENDING",
  "customerId": "customer-123",
  "productId": "product-A",
  "quantity": 2,
  "totalPrice": 30.00
}
```

> **Tip:** `unitPrice × quantity = totalPrice`. Use an even `totalPrice` integer
> part (e.g., `15.00 × 2 = 30.00`) to trigger the success flow.
> Use an odd integer part (e.g., `15.00 × 1 = 15.00`) to trigger the failure/compensation flow.

#### Get Order by ID
```http
GET /api/orders/{orderId}
```

### Payment Service — `http://localhost:8082`

#### Get Payment by Order ID
```http
GET /api/payments/order/{orderId}
```

### Inventory Service — `http://localhost:8083`

#### Get Stock Level for a Product
```http
GET /api/inventory/{productId}
```

#### Seed/Reset Stock for a Product (10 units)
```http
POST /api/inventory/{productId}/seed
```

---

## Demo Walkthrough

### 1. Success scenario (even total price)

```bash
# Create order — totalPrice = 15.00 × 2 = 30.00 (even → payment succeeds)
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","productId":"laptop","quantity":2,"unitPrice":15.00}'

# After a moment, check the order status (should be COMPLETED)
curl http://localhost:8081/api/orders/<orderId>

# Check payment
curl http://localhost:8082/api/payments/order/<orderId>

# Check inventory (should be 10 - 2 = 8)
curl http://localhost:8083/api/inventory/laptop
```

### 2. Payment failure (odd total price)

```bash
# Create order — totalPrice = 15.00 × 1 = 15.00 (odd → payment fails)
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-2","productId":"laptop","quantity":1,"unitPrice":15.00}'

# Order should be PAYMENT_FAILED
curl http://localhost:8081/api/orders/<orderId>
```

### 3. Inventory failure (drain stock first)

```bash
# Drain all stock for "laptop" by placing a large order (10 units, even total)
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-3","productId":"tablet","quantity":10,"unitPrice":2.00}'

# Wait for COMPLETED, then place another order for more than 0 remaining
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-4","productId":"tablet","quantity":1,"unitPrice":2.00}'

# Second order should be CANCELLED (inventory failed, payment was refunded)
curl http://localhost:8081/api/orders/<orderId>
```

---

## Key Design Decisions

### Why Choreography (not Orchestration)?

In **Orchestration**, a central coordinator tells each service what to do. In **Choreography**, each service knows what to publish and what to react to. This project demonstrates choreography because:

- No single point of failure
- Each service is independently deployable
- Event contracts are explicit and versionable
- Simpler for this scale; orchestration adds value in more complex flows

### Idempotency

Every event handler checks for duplicates before processing:
- Payment Service: `findByOrderId()` before creating a new `Payment`
- Inventory Service: `findByOrderId()` before creating a new `Reservation`

This prevents double-charging or double-reserving if Kafka redelivers a message.

### Service Isolation

Each service owns its own copy of events it consumes. There are no shared libraries between services. This is intentional — it prevents compile-time coupling and allows each service to evolve its schema independently as long as JSON field names remain compatible.

### Pessimistic Locking

The Inventory Service uses `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) when loading a `ProductStock` row. This prevents two concurrent reservation requests for the same product from both reading the same available quantity and both succeeding (overselling).

---

## Technologies

| Technology               | Version  | Purpose                              |
|--------------------------|----------|--------------------------------------|
| Java                     | 21       | Language                             |
| Spring Boot              | 3.4.x    | Application framework                |
| Spring Kafka             | 3.x      | Kafka producer/consumer              |
| Spring Data JPA          | 3.x      | Database persistence                 |
| PostgreSQL               | 16       | Relational database (one per service)|
| Apache Kafka             | 7.6.1    | Event bus (via Confluent image)      |
| Testcontainers           | 1.20.x   | Integration test infrastructure      |
| JUnit 5                  | 5.x      | Test framework                       |
| Mockito                  | 5.x      | Unit test mocking                    |
| Docker / Docker Compose  | 29+      | Containerization                     |
