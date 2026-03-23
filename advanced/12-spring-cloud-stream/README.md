# 12 — Spring Cloud Stream

A Spring Boot application demonstrating **Spring Cloud Stream** for building event-driven
microservices. This project shows how to abstract the underlying message broker (Apache Kafka)
behind a simple functional programming model — you write plain Java `Supplier`, `Function`,
and `Consumer` beans, and Spring Cloud Stream wires them to Kafka topics automatically.

---

## What Is Spring Cloud Stream?

Spring Cloud Stream is a framework built on top of Spring Integration and Spring Boot that
provides a **broker-agnostic abstraction** for message-driven microservices. Instead of
writing broker-specific code (`KafkaTemplate`, `@KafkaListener`, etc.), you define:

| Java Type | Role | Kafka mapping |
|---|---|---|
| `Supplier<T>` | Message **producer** | Outbound binding (topic writer) |
| `Consumer<T>` | Message **consumer** | Inbound binding (topic reader) |
| `Function<T,R>` | Message **processor** | Inbound + outbound bindings |

Spring Cloud Stream automatically:
- Discovers these beans in the application context.
- Creates Kafka topics and producer/consumer clients.
- Serializes/deserializes message payloads as JSON.
- Manages consumer group offsets, retries, and error channels.

---

## Domain Scenario — Order Notification Pipeline

This project implements an **order notification pipeline** for an e-commerce system.

```
REST Client
    │  POST /api/orders
    ▼
OrderController ──► OrderService ──► in-memory queue
                                          │
                                          ▼
                              orderSupplier (Supplier)
                              publishes OrderPlacedEvent
                                          │
                               ┌──────────▼──────────────┐
                               │   Kafka topic: orders   │
                               └──────────┬──────────────┘
                                          │
                              orderProcessor (Function)
                              validates & enriches event
                                          │
                        ┌─────────────────┴─────────────────┐
                        │                                   │
               ┌────────▼────────┐              ┌───────────▼──────────┐
               │ orders-processed│              │   orders-rejected    │
               └────────┬────────┘              └───────────┬──────────┘
                        │                                   │
               notificationConsumer              rejectionLogger
               (Consumer)                        (Consumer)
               logs confirmation                 logs rejection notice
               marks order NOTIFIED              
```

### Order lifecycle

```
PENDING → PROCESSING → NOTIFIED   (happy path)
PENDING → REJECTED                (validation failure)
```

---

## Project Structure

```
12-spring-cloud-stream/
├── src/main/java/com/example/cloudstream/
│   ├── SpringCloudStreamApplication.java   # Entry point
│   ├── domain/
│   │   ├── Order.java                      # Domain entity (in-memory)
│   │   └── OrderStatus.java                # Lifecycle enum
│   ├── events/
│   │   ├── OrderPlacedEvent.java           # Published to "orders"
│   │   ├── OrderProcessedEvent.java        # Published to "orders-processed"
│   │   └── OrderRejectedEvent.java         # Published to "orders-rejected"
│   ├── repository/
│   │   └── OrderRepository.java            # In-memory ConcurrentHashMap store
│   ├── service/
│   │   └── OrderService.java               # Domain logic + Supplier bridge queue
│   ├── stream/
│   │   └── OrderStreamFunctions.java       # Supplier, Function, Consumer beans
│   └── web/
│       ├── OrderController.java            # REST endpoints
│       ├── PlaceOrderRequest.java          # Request DTO (validated)
│       └── OrderResponse.java              # Response DTO
├── src/main/resources/
│   └── application.yml                     # Spring Cloud Stream binding config
├── src/test/java/com/example/cloudstream/
│   ├── domain/
│   │   └── OrderTest.java                  # Unit tests — domain entity
│   ├── service/
│   │   └── OrderServiceTest.java           # Unit tests — service layer (Mockito)
│   ├── stream/
│   │   └── OrderStreamFunctionsTest.java   # Unit tests — stream functions (Test Binder)
│   └── integration/
│       └── OrderPipelineIntegrationTest.java  # Integration tests (Testcontainers Kafka)
├── src/test/resources/
│   ├── docker-java.properties              # Docker API version for Docker Desktop 29+
│   └── testcontainers.properties           # Testcontainers Docker API config
├── Dockerfile                              # Multi-stage build
├── docker-compose.yml                      # ZooKeeper + Kafka + App stack
├── pom.xml
├── mvnw / mvnw.cmd
└── README.md
```

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | (via Maven Wrapper — no local install needed) |
| Docker | 20+ (for integration tests and Docker Compose) |
| Docker Desktop | 29+ supported via API version fix |

---

## Running with Docker Compose

The entire stack (ZooKeeper + Kafka + Application) runs via Docker Compose.

### Start all services

```bash
docker compose up --build
```

This command:
1. Builds the Spring Boot application image using the multi-stage `Dockerfile`.
2. Starts ZooKeeper (Kafka coordination).
3. Starts Kafka (waits for ZooKeeper to be healthy).
4. Starts the Spring Cloud Stream application (waits for Kafka to be healthy).

### Start in background (detached mode)

```bash
docker compose up --build -d
```

### Check service health

```bash
docker compose ps
```

### Follow application logs

```bash
docker compose logs -f app
```

You will see the pipeline in action as you place orders — the `[orderSupplier]`,
`[orderProcessor]`, `[notificationConsumer]`, and `[rejectionLogger]` log entries
show each stage processing the event.

### Stop all services

```bash
docker compose down
```

---

## REST API Usage

### Place a valid order

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-001",
    "productId": "laptop-pro-x1",
    "quantity": 2,
    "totalPrice": 2499.98
  }' | jq .
```

Expected response (HTTP 201 Created):

```json
{
  "id": "3a1b2c3d-...",
  "customerId": "cust-001",
  "productId": "laptop-pro-x1",
  "quantity": 2,
  "totalPrice": 2499.98,
  "status": "PENDING",
  "rejectionReason": null,
  "createdAt": "2025-03-22T10:00:00Z",
  "updatedAt": "2025-03-22T10:00:00Z"
}
```

### Poll order status (observe pipeline progress)

After placing an order, poll the status endpoint to watch it progress through the pipeline:

```bash
# Replace <ORDER_ID> with the id from the POST response
curl -s http://localhost:8080/api/orders/<ORDER_ID> | jq .status
```

You will observe the status transition:
```
"PENDING" → "PROCESSING" → "NOTIFIED"
```

### List all orders

```bash
curl -s http://localhost:8080/api/orders | jq .
```

### Place an order that will be rejected (zero price — bypasses REST validation via service layer)

The processor validates that `totalPrice > 0`. To trigger a rejection via the REST API,
the REST layer's `@Positive` validation prevents zero prices. To observe rejection in
the pipeline, reduce price to a very small value and note: the rejection path is
exercised in integration tests using the service layer directly.

You can see the rejection flow in the **integration test** output or by calling the
service directly in a custom test.

### Actuator health check

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

---

## Running the Tests

> **Note:** Tests do NOT use `docker-compose.yml`. Testcontainers starts its own
> Docker containers automatically during the test run.

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests (no Docker required)

Unit tests use either plain Mockito (`OrderServiceTest`, `OrderTest`) or the
Spring Cloud Stream **Test Binder** (`OrderStreamFunctionsTest`) — no Docker containers.

```bash
./mvnw test -Dtest="OrderTest,OrderServiceTest,OrderStreamFunctionsTest"
```

### Run only integration tests (requires Docker)

```bash
./mvnw test -Dtest="OrderPipelineIntegrationTest"
```

---

## Test Architecture

### Unit Tests (no Docker)

| Test class | What it tests | Technology |
|---|---|---|
| `OrderTest` | Domain entity state transitions | Pure JUnit 5 |
| `OrderServiceTest` | Service layer logic, queue mechanics | JUnit 5 + Mockito |
| `OrderStreamFunctionsTest` | Stream functions (processor, consumers) | Spring Cloud Stream Test Binder |

**Spring Cloud Stream Test Binder** (`spring-cloud-stream-test-binder`) replaces
the real Kafka binder with an in-memory message bus during tests. You inject
`InputDestination` to send messages and `OutputDestination` to capture output.

### Integration Tests (requires Docker)

| Test class | What it tests | Technology |
|---|---|---|
| `OrderPipelineIntegrationTest` | Full pipeline end-to-end via real Kafka | Testcontainers + Awaitility |

**Testcontainers** starts a real `confluentinc/cp-kafka:7.6.1` container.
**Awaitility** polls the in-memory order store until the expected status transition
occurs, handling the asynchronous nature of the Kafka pipeline.

---

## Key Concepts Demonstrated

### 1. Functional Programming Model

Spring Cloud Stream 3.x+ uses plain Java functions instead of the legacy `@EnableBinding`:

```java
@Bean
public Supplier<Message<OrderPlacedEvent>> orderSupplier() { ... }

@Bean
public Function<OrderPlacedEvent, Message<?>> orderProcessor() { ... }

@Bean
public Consumer<OrderProcessedEvent> notificationConsumer() { ... }
```

### 2. Binding Configuration

Functional beans are wired to Kafka topics in `application.yml`:

```yaml
spring:
  cloud:
    stream:
      function:
        definition: orderSupplier;orderProcessor;notificationConsumer;rejectionLogger
      bindings:
        orderSupplier-out-0:
          destination: orders
        orderProcessor-in-0:
          destination: orders
          group: order-processor-group
        orderProcessor-out-0:
          destination: orders-processed
        notificationConsumer-in-0:
          destination: orders-processed
          group: notification-consumer-group
```

### 3. Dynamic Routing (Function → multiple topics)

The `orderProcessor` Function routes to different topics based on validation:

```java
// Valid order → orders-processed (default outbound binding)
return MessageBuilder.withPayload(processed).build();

// Invalid order → orders-rejected (dynamic routing via header)
return MessageBuilder
    .withPayload(rejection)
    .setHeader("spring.cloud.stream.sendto.destination", "orders-rejected")
    .build();
```

### 4. Demand-Driven Supplier (REST → Kafka)

A `LinkedBlockingQueue` bridges REST-triggered events to the polled `Supplier`:

```java
// REST handler enqueues:
pendingEvents.offer(OrderPlacedEvent.from(order));

// Supplier drains (polled by Spring Cloud Stream):
return () -> pendingEvents.poll(); // returns null → skip cycle
```

### 5. Consumer Groups

Consumer groups ensure exactly-once delivery when multiple instances run:

```yaml
group: order-processor-group   # All instances share this group
```

With `n` instances and `n` partitions, each message is consumed by exactly one instance.

---

## Docker Compose Services

| Service | Image | Port | Purpose |
|---|---|---|---|
| `zookeeper` | `confluentinc/cp-zookeeper:7.6.1` | 2181 | Kafka coordination |
| `kafka` | `confluentinc/cp-kafka:7.6.1` | 9092 | Message broker |
| `app` | Built from `Dockerfile` | 8080 | Spring Cloud Stream app |

### Kafka topics (auto-created)

| Topic | Producer | Consumer |
|---|---|---|
| `orders` | `orderSupplier` | `orderProcessor` |
| `orders-processed` | `orderProcessor` | `notificationConsumer` |
| `orders-rejected` | `orderProcessor` (dynamic) | `rejectionLogger` |

### Inspecting Kafka topics from the host

With the stack running, you can use Kafka CLI tools on the host:

```bash
# List topics
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Consume from the orders topic
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic orders \
  --from-beginning

# Consume from orders-processed
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic orders-processed \
  --from-beginning
```
