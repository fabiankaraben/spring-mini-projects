# CQRS with Axon Framework

A Spring Boot mini-project demonstrating **Command Query Responsibility Segregation (CQRS)** and **Event Sourcing** using the [Axon Framework](https://www.axoniq.io/axon-framework).

---

## What is this project?

This project implements an **Order Management** backend that separates the write model (commands) from the read model (queries) using the CQRS pattern. All state changes are captured as immutable domain events stored in a PostgreSQL-backed Axon event store — this is **Event Sourcing**.

### Key concepts demonstrated

| Concept | Description |
|---|---|
| **Command** | Expresses intent to change state (`PlaceOrderCommand`, `ConfirmOrderCommand`, `CancelOrderCommand`) |
| **Event** | An immutable fact that something happened (`OrderPlacedEvent`, `OrderConfirmedEvent`, `OrderCancelledEvent`) |
| **Aggregate** | Enforces business rules; rebuilt from its event history on each load (Event Sourcing) |
| **Projection** | Listens to events and builds a read-optimised view (`OrderSummary` in PostgreSQL) |
| **Query** | Read-only requests dispatched to query handlers (`FindOrderByIdQuery`, `FindAllOrdersQuery`) |
| **Event Store** | The write-side truth — all events persisted in `domain_event_entry` table |
| **Read Model** | Denormalised `order_summaries` table — optimised for fast queries |

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        REST API layer                           │
│  POST /api/orders   PUT /api/orders/{id}/confirm                │
│  PUT /api/orders/{id}/cancel   GET /api/orders/{id}            │
└──────────┬────────────────────────────────┬───────────────────┘
           │ Commands                        │ Queries
           ▼                                 ▼
┌──────────────────────┐       ┌────────────────────────────┐
│    Command side       │       │        Query side          │
│                      │       │                            │
│  OrderAggregate      │       │  OrderProjection           │
│  (event sourced)     │       │  (reads OrderSummary JPA)  │
│                      │       │                            │
│  @CommandHandler     │       │  @QueryHandler             │
│  @EventSourcingHandler       │  @EventHandler             │
└──────────┬───────────┘       └────────────────────────────┘
           │ Events (persisted)        ▲ Events (in-memory bus)
           ▼                           │
┌──────────────────────────────────────┴─────────────────────┐
│            Axon event store (PostgreSQL / JPA)             │
└────────────────────────────────────────────────────────────┘
```

### Order lifecycle

```
(none) ──PlaceOrderCommand──► PLACED
PLACED ──ConfirmOrderCommand─► CONFIRMED  (terminal)
PLACED ──CancelOrderCommand──► CANCELLED  (terminal)
```

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (or use the included Maven Wrapper `./mvnw`) |
| Docker | 24+ (for Docker Compose and Testcontainers) |
| Docker Compose | v2 (`docker compose` command) |

---

## Running with Docker Compose

This is the recommended way to run the project. Docker Compose starts PostgreSQL and the Spring Boot application.

### Start everything

```bash
docker compose up --build
```

Or in the background:

```bash
docker compose up --build -d
```

### Stop and clean up

```bash
# Stop containers (data is preserved in the named volume)
docker compose down

# Stop containers AND remove all data volumes
docker compose down -v
```

### View logs

```bash
docker compose logs -f app
```

The application starts on **http://localhost:8080**.

---

## API Usage (curl examples)

All examples assume the app is running on `localhost:8080`.

### Place a new order

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "PROD-42",
    "quantity": 3,
    "unitPrice": 29.99
  }' | jq .
```

Example response:
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Order placed successfully"
}
```

### Get an order by ID (query side)

```bash
# Replace ORDER_ID with the UUID from the place-order response
curl -s http://localhost:8080/api/orders/550e8400-e29b-41d4-a716-446655440000 | jq .
```

Example response:
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "productId": "PROD-42",
  "quantity": 3,
  "unitPrice": 29.99,
  "status": "PLACED",
  "placedAt": "2024-01-15T10:30:00Z",
  "updatedAt": null
}
```

### Get all orders

```bash
curl -s http://localhost:8080/api/orders | jq .
```

### Filter orders by status

```bash
# Available statuses: PLACED, CONFIRMED, CANCELLED
curl -s "http://localhost:8080/api/orders?status=PLACED" | jq .
```

### Confirm an order

```bash
curl -s -X PUT http://localhost:8080/api/orders/550e8400-e29b-41d4-a716-446655440000/confirm
```

### Cancel an order

```bash
curl -s -X PUT http://localhost:8080/api/orders/550e8400-e29b-41d4-a716-446655440000/cancel \
  -H "Content-Type: application/json" \
  -d '{"reason": "Customer changed their mind"}'
```

### Confirm an already-confirmed order (409 Conflict)

```bash
# Attempting to confirm a CONFIRMED order returns 409
curl -s -X PUT http://localhost:8080/api/orders/550e8400-e29b-41d4-a716-446655440000/confirm
# → HTTP 409 Conflict: "Cannot confirm order in status: CONFIRMED"
```

### Health check

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

---

## Project Structure

```
src/main/java/com/example/cqrs/
├── CqrsWithAxonApplication.java          # Spring Boot entry point
├── command/
│   ├── aggregate/
│   │   ├── OrderAggregate.java           # Domain aggregate (command handlers + event sourcing)
│   │   └── OrderStatus.java              # PLACED / CONFIRMED / CANCELLED enum
│   └── api/
│       ├── PlaceOrderCommand.java        # Command: create order
│       ├── ConfirmOrderCommand.java      # Command: confirm order
│       ├── CancelOrderCommand.java       # Command: cancel order
│       ├── OrderPlacedEvent.java         # Event: order was placed
│       ├── OrderConfirmedEvent.java      # Event: order was confirmed
│       └── OrderCancelledEvent.java      # Event: order was cancelled
├── query/
│   ├── api/
│   │   ├── FindOrderByIdQuery.java       # Query: fetch single order
│   │   └── FindAllOrdersQuery.java       # Query: fetch all orders (optionally filtered)
│   ├── handler/
│   │   └── OrderProjection.java         # Event handlers (update read model) + query handlers
│   └── model/
│       ├── OrderSummary.java             # JPA entity — the read model
│       └── OrderSummaryRepository.java  # Spring Data JPA repository
├── rest/
│   ├── OrderCommandController.java       # POST/PUT endpoints (write side)
│   ├── OrderQueryController.java         # GET endpoints (read side)
│   └── GlobalExceptionHandler.java      # Maps exceptions to HTTP responses
└── config/
    └── AxonConfig.java                   # Axon embedded event store configuration
```

---

## Running Tests

### Unit tests only (no Docker required)

The unit tests use Axon's `AggregateTestFixture` — no Spring context, no database:

```bash
./mvnw test -Dtest="OrderAggregateTest"
```

### All tests (unit + integration)

Integration tests use Testcontainers to spin up a real PostgreSQL container. **Docker must be running.**

```bash
./mvnw clean test
```

### Test coverage summary

| Test class | Type | What it tests |
|---|---|---|
| `OrderAggregateTest` | Unit | Aggregate command handlers, business rules, event sourcing reconstruction |
| `OrderIntegrationTest` | Integration (Testcontainers) | Full pipeline: REST → command → event → projection → query |

---

## How Event Sourcing Works in This Project

1. **Place order**: `POST /api/orders` → `PlaceOrderCommand` → `OrderAggregate` constructor → `AggregateLifecycle.apply(OrderPlacedEvent)` → event stored in `domain_event_entry` table → `OrderProjection.on(OrderPlacedEvent)` → row created in `order_summaries`

2. **Load aggregate**: When the next command arrives (e.g. `ConfirmOrderCommand`), Axon loads all events for that `orderId` from `domain_event_entry` and replays them through `@EventSourcingHandler` methods to reconstruct the aggregate's in-memory state — **no snapshot of the object is ever stored directly**.

3. **Read model**: The `order_summaries` table is a denormalised view that is always up-to-date with the last event processed. Queries never touch the event store — they only read from `order_summaries`.

---

## Docker Compose Services

| Service | Container | Port | Description |
|---|---|---|---|
| `postgres` | `axon-postgres` | `5432` | PostgreSQL database (event store + read model) |
| `app` | `axon-app` | `8080` | Spring Boot CQRS application |

### Database tables (auto-created by Hibernate)

| Table | Purpose |
|---|---|
| `domain_event_entry` | Axon event store — all domain events |
| `snapshot_event_entry` | Aggregate snapshots (not used, created automatically) |
| `saga_entry` | Saga state (not used, created automatically) |
| `association_value_entry` | Saga associations (not used, created automatically) |
| `order_summaries` | Read model — denormalised order projection |

---

## Technology Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Language |
| Spring Boot | 3.4.3 | Application framework |
| Axon Framework | 4.10.3 | CQRS / Event Sourcing framework |
| Spring Data JPA | (Boot-managed) | Read model persistence |
| PostgreSQL | 16 | Event store + read model database |
| JUnit 5 | (Boot-managed) | Unit and integration testing |
| Axon Test | 4.10.3 | `AggregateTestFixture` for unit tests |
| Testcontainers | 1.21.3 | Real PostgreSQL for integration tests |
| Maven | 3.9+ | Build tool |
| Docker | 24+ | Container runtime |
