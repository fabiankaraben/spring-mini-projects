# Apache Camel Pipes

A Spring Boot mini-project demonstrating the **Pipes and Filters** Enterprise Integration Pattern (EIP) using **Apache Camel 4**.

Messages (order payloads) are submitted via a REST API and routed through a sequential pipeline of independent, composable processing stages before being dispatched to a JMS broker (ActiveMQ Artemis) and persisted to the file system.

---

## What This Project Demonstrates

- **Pipes and Filters EIP** — a message travels through a chain of filter stages, each performing a single responsibility.
- **Apache Camel RouteBuilder Java DSL** — routes defined in pure Java using a fluent API.
- **Dead-letter channel** — invalid messages are intercepted by `onException` and routed to an asynchronous SEDA dead-letter queue.
- **Dynamic Router** — the dispatch stage sets a header at runtime to resolve the JMS destination dynamically.
- **Camel components used**: `direct`, `seda`, `jms`, `file`, `log`.
- **Testcontainers integration** — a real ActiveMQ Artemis broker is spun up in Docker for integration tests.

---

## Pipeline Architecture

```
POST /api/orders
       │
       ▼
┌──────────────────┐
│  OrderController │  (Spring MVC — hands off to Camel via ProducerTemplate)
└────────┬─────────┘
         │ direct:orders
         ▼
┌──────────────────────────────────────────────────────┐
│                   order-pipeline route               │
│                                                      │
│  [Stage 1] ValidationProcessor                       │
│      Rejects invalid orders → seda:dead-letter       │
│                   │                                  │
│  [Stage 2] EnrichmentProcessor                       │
│      Computes totalAmount, vatAmount, region         │
│                   │                                  │
│  [Stage 3] ClassificationProcessor                   │
│      Tags order as PRIORITY or STANDARD              │
│                   │                                  │
│  [Stage 4] DispatchProcessor                         │
│      Sets CamelJmsDestinationName header             │
│                   │                                  │
│  [Stage 5] PersistenceProcessor                      │
│      Stamps processedAt, sets CamelFileName          │
│                   │                                  │
│         marshal().json()                             │
│                   │                                  │
│         toD("jms:queue:<dynamic>")  ──────────────► Artemis
│                   │
│         to("seda:notifications")
│                   │
│         toD("file:<output-dir>")   ──────────────► JSON file
└──────────────────────────────────────────────────────┘

seda:dead-letter ──► dead-letter-handler route  (logs error)
seda:notifications ► notification-handler route (logs audit)
```

### JMS Queues

| Queue              | Condition                          |
|--------------------|------------------------------------|
| `orders.priority`  | `totalAmount >= priorityThreshold` |
| `orders.standard`  | `totalAmount < priorityThreshold`  |

### Region Derivation (Enrichment Stage)

| `customerId` prefix | Region assigned |
|---------------------|-----------------|
| `EU`                | `EU`            |
| `US`                | `US`            |
| `AP`                | `APAC`          |
| anything else       | `UNKNOWN`       |

---

## Requirements

| Requirement      | Version / Notes                         |
|------------------|-----------------------------------------|
| Java             | 21 or higher                            |
| Maven            | Provided via Maven Wrapper (`./mvnw`)   |
| Docker           | Required for Docker Compose and tests   |
| Docker Compose   | v2 (`docker compose` command)           |

---

## Running with Docker Compose

Docker Compose is the recommended way to run the full stack (application + Artemis broker).

### 1. Start the stack

```bash
docker compose up --build
```

This command:
1. Builds the Spring Boot application Docker image (multi-stage build).
2. Starts the ActiveMQ Artemis broker.
3. Waits for Artemis to be healthy, then starts the application.

### 2. Run in detached (background) mode

```bash
docker compose up --build -d
```

### 3. View logs

```bash
docker compose logs -f          # all services
docker compose logs -f app      # app only
docker compose logs -f artemis  # broker only
```

### 4. Stop the stack

```bash
docker compose down             # stop and remove containers
docker compose down -v          # also remove the output volume
```

### 5. Access the Artemis management console

Open [http://localhost:8161](http://localhost:8161) in your browser.
- **Username**: `artemis`
- **Password**: `artemis`

Navigate to **Queues** to inspect messages delivered to `orders.priority` and `orders.standard`.

### 6. Inspect written JSON files

```bash
docker compose exec app ls /app/output/orders
docker compose exec app cat /app/output/orders/<filename>.json
```

---

## API Reference

### Submit an Order

```
POST /api/orders
Content-Type: application/json
```

**Request body**

| Field         | Type    | Required | Description                         |
|---------------|---------|----------|-------------------------------------|
| `orderId`     | string  | yes      | Unique order identifier             |
| `customerId`  | string  | yes      | Customer ID (prefix drives region)  |
| `productName` | string  | yes      | Human-readable product name         |
| `unitPrice`   | number  | yes      | Unit price (must be > 0)            |
| `quantity`    | integer | yes      | Number of units (must be ≥ 1)       |

**Response**

| HTTP Status | Status field | Condition                        |
|-------------|--------------|----------------------------------|
| `202`       | `ACCEPTED`   | Order entered the pipeline       |
| `422`       | `REJECTED`   | Validation failed                |
| `500`       | —            | Unexpected server error          |

---

## curl Examples

### Submit a standard order (below threshold)

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "customerId": "EU-CUST-100",
    "productName": "Wireless Mouse",
    "unitPrice": 29.99,
    "quantity": 3
  }' | jq .
```

Expected response (`totalAmount = 89.97 < 300` → STANDARD queue):
```json
{
  "orderId": "ORD-001",
  "status": "ACCEPTED",
  "message": "Order accepted and entering the processing pipeline."
}
```

---

### Submit a priority order (above threshold)

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-002",
    "customerId": "US-CORP-200",
    "productName": "Enterprise SSD",
    "unitPrice": 199.00,
    "quantity": 5
  }' | jq .
```

Expected response (`totalAmount = 995.00 >= 300` → PRIORITY queue):
```json
{
  "orderId": "ORD-002",
  "status": "ACCEPTED",
  "message": "Order accepted and entering the processing pipeline."
}
```

---

### Submit an APAC region order

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-003",
    "customerId": "AP-DIST-300",
    "productName": "Network Switch",
    "unitPrice": 85.00,
    "quantity": 2
  }' | jq .
```

---

### Submit an invalid order (missing orderId) — triggers dead-letter

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "EU-CUST-999",
    "productName": "Broken Widget",
    "unitPrice": 10.00,
    "quantity": 1
  }' | jq .
```

Expected response (HTTP 422):
```json
{
  "orderId": null,
  "status": "REJECTED",
  "message": "REJECTED: orderId must not be null or blank; received: 'null'"
}
```

---

### Submit an order with negative price — triggers dead-letter

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-BAD-001",
    "customerId": "EU-CUST-999",
    "productName": "Negative Widget",
    "unitPrice": -5.00,
    "quantity": 1
  }' | jq .
```

---

### Check application health

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

---

## Running Tests

Tests do **not** require the Docker Compose stack to be running. Testcontainers starts its own isolated ActiveMQ Artemis container automatically.

### Run all tests

```bash
./mvnw clean test
```

### Test structure

| Test class                        | Type        | Description                                              |
|-----------------------------------|-------------|----------------------------------------------------------|
| `OrderTest`                       | Unit        | Domain object getters/setters and constructor            |
| `ValidationProcessorTest`         | Unit        | All validation rules (happy path + failure cases)        |
| `EnrichmentProcessorTest`         | Unit        | Total/VAT calculation and region derivation              |
| `ClassificationProcessorTest`     | Unit        | PRIORITY/STANDARD classification at/above/below threshold|
| `OrderPipelineIntegrationTest`    | Integration | Full pipeline via REST API with real Artemis container   |

### Unit tests

Pure JUnit 5, no Spring context. A lightweight `DefaultCamelContext` is created to construct `DefaultExchange` objects used to drive individual processors. Tests run in milliseconds.

### Integration tests

`@SpringBootTest` + `@Testcontainers` starts:
- Full Spring Boot application context (including Camel context + all routes)
- A real ActiveMQ Artemis Docker container

`@DynamicPropertySource` injects the container's broker URL into Spring's environment before context creation.

> **Requirements for integration tests:**
> - Docker must be running
> - Docker Desktop 29+ users: Docker API v1.44 is configured automatically via `src/test/resources/docker-java.properties`

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/camel/
│   │   ├── ApacheCamelPipesApplication.java   # Spring Boot entry point
│   │   ├── config/
│   │   │   └── AppProperties.java             # Typed config (@ConfigurationProperties)
│   │   ├── domain/
│   │   │   ├── Order.java                     # Core domain object
│   │   │   ├── OrderPriority.java             # PRIORITY / STANDARD enum
│   │   │   └── OrderResult.java               # REST response record
│   │   ├── processor/
│   │   │   ├── ValidationProcessor.java       # Stage 1: validate fields
│   │   │   ├── EnrichmentProcessor.java       # Stage 2: compute total/VAT/region
│   │   │   ├── ClassificationProcessor.java   # Stage 3: assign priority
│   │   │   ├── DispatchProcessor.java         # Stage 4: set JMS destination header
│   │   │   └── PersistenceProcessor.java      # Stage 5: stamp timestamp + filename
│   │   ├── route/
│   │   │   └── OrderPipelineRoute.java        # Camel RouteBuilder — all 3 routes
│   │   └── web/
│   │       └── OrderController.java           # REST endpoint → ProducerTemplate
│   └── resources/
│       └── application.yml
└── test/
    ├── java/com/example/camel/
    │   ├── domain/
    │   │   └── OrderTest.java
    │   ├── processor/
    │   │   ├── ValidationProcessorTest.java
    │   │   ├── EnrichmentProcessorTest.java
    │   │   └── ClassificationProcessorTest.java
    │   └── integration/
    │       └── OrderPipelineIntegrationTest.java
    └── resources/
        ├── application-test.yml
        ├── docker-java.properties             # Docker API v1.44 for Docker Desktop 29+
        └── testcontainers.properties
```

---

## Configuration Reference

All values can be overridden via environment variables (Spring Boot relaxed binding).

| Property                              | Default              | Env variable override                    | Description                                      |
|---------------------------------------|----------------------|------------------------------------------|--------------------------------------------------|
| `spring.artemis.broker-url`           | `tcp://localhost:61616` | `SPRING_ARTEMIS_BROKER_URL`           | Artemis broker URL                               |
| `spring.artemis.user`                 | `artemis`            | `SPRING_ARTEMIS_USER`                    | Broker username                                  |
| `spring.artemis.password`             | `artemis`            | `SPRING_ARTEMIS_PASSWORD`                | Broker password                                  |
| `app.pipeline.priority-threshold`     | `300`                | `APP_PIPELINE_PRIORITY_THRESHOLD`        | Orders ≥ this amount go to the PRIORITY queue    |
| `app.pipeline.vat-rate`              | `0.21`               | `APP_PIPELINE_VAT_RATE`                  | VAT rate applied in the enrichment stage (21%)   |
| `app.output.dir`                     | `./output/orders`    | `APP_OUTPUT_DIR`                         | Directory where JSON order files are written     |
