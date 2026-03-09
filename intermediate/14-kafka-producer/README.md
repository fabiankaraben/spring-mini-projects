# Kafka Producer

A Spring Boot application that publishes order events to an Apache Kafka topic
using `KafkaTemplate`. This mini-project demonstrates the producer side of an
event-driven architecture: a REST endpoint accepts order data, builds a typed
domain event, and sends it to a Kafka topic with the order ID used as the message
key to guarantee per-order ordering.

---

## What this mini-project demonstrates

- **`KafkaTemplate`** – Spring Kafka's high-level producer abstraction for
  sending typed messages to Kafka topics.
- **Key-based partitioning** – using `orderId` as the Kafka message key to
  guarantee all events for the same order land on the same partition (and
  therefore arrive at consumers in order).
- **`JsonSerializer`** – automatic Jackson-based serialisation of a Java record
  to a JSON Kafka message value.
- **`KafkaAdmin` / `NewTopic` bean** – automatic topic creation on application
  startup without needing the Kafka CLI.
- **Bean Validation** – `@Valid` on the request body so invalid payloads are
  rejected before reaching the service layer.
- **Docker Compose** – KRaft-mode Kafka (no ZooKeeper) + Spring Boot app wired
  together with a health-check dependency.
- **Unit tests** – domain logic tested in isolation with JUnit 5 and Mockito
  (no Spring context, no Docker).
- **Integration tests** – full end-to-end test with a real Kafka broker
  supplied by Testcontainers.

---

## Requirements

| Tool | Minimum version |
|------|----------------|
| Java | 21 |
| Maven | 3.9+ (or use `./mvnw`) |
| Docker | 24+ with Docker Compose v2 |

> The integration tests pull `confluentinc/cp-kafka:7.6.1` from Docker Hub
> automatically via Testcontainers. Make sure Docker is running before
> executing tests.

---

## Project structure

```
src/
├── main/java/com/example/kafkaproducer/
│   ├── KafkaProducerApplication.java     # Spring Boot entry point
│   ├── config/
│   │   └── KafkaTopicConfig.java         # NewTopic bean – auto-creates the topic
│   ├── controller/
│   │   └── OrderController.java          # POST /api/orders endpoint
│   ├── domain/
│   │   ├── OrderEvent.java               # Immutable event record + factory method
│   │   └── OrderStatus.java              # Enum: CREATED, CONFIRMED, SHIPPED, ...
│   ├── dto/
│   │   ├── PublishOrderRequest.java       # Validated request body
│   │   └── PublishOrderResponse.java      # Response with Kafka metadata
│   ├── exception/
│   │   └── GlobalExceptionHandler.java   # Centralised error responses
│   └── service/
│       └── OrderEventService.java        # KafkaTemplate.send() logic
└── test/java/com/example/kafkaproducer/
    ├── unit/
    │   ├── OrderEventTest.java           # Domain record unit tests
    │   └── OrderEventServiceTest.java    # Service unit tests (Mockito mock)
    └── integration/
        └── OrderControllerIntegrationTest.java  # Full stack + Testcontainers Kafka
```

---

## Running with Docker Compose

This is the recommended way to run the application. Docker Compose starts a
KRaft-mode Kafka broker and the Spring Boot app together.

### 1. Start all services

```bash
docker compose up --build
```

Compose starts the `kafka` service first and waits for its health check to pass
before starting the `app` service. The health check calls
`kafka-broker-api-versions` inside the container, which succeeds only once the
broker is fully ready.

### 2. Verify the application is running

```bash
curl http://localhost:8080/actuator/health 2>/dev/null || \
  curl -s http://localhost:8080/api/orders \
       -X POST -H "Content-Type: application/json" \
       -d '{"orderId":"ping","customerId":"c","product":"p","quantity":1,"totalAmount":"1.00","status":"CREATED"}'
```

### 3. Stop all services

```bash
docker compose down
```

---

## API usage

### Publish an order event

**Endpoint:** `POST /api/orders`

**Request body fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `orderId` | string | ✅ | Business identifier of the order |
| `customerId` | string | ✅ | Identifier of the customer |
| `product` | string | ✅ | Product name or SKU |
| `quantity` | integer | ✅ | Number of units (≥ 1) |
| `totalAmount` | decimal | ✅ | Total price (> 0.00) |
| `status` | enum | ✅ | `CREATED`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, or `CANCELLED` |

**Response body fields:**

| Field | Type | Description |
|-------|------|-------------|
| `eventId` | string | Auto-generated UUID for this event |
| `orderId` | string | Echoed from the request |
| `status` | string | Order status that was published |
| `topic` | string | Kafka topic the event was sent to |
| `partition` | integer | Kafka partition that received the event |
| `offset` | integer | Offset within the partition |
| `timestamp` | string | ISO-8601 UTC timestamp of the event |

### curl examples

#### Publish a new order (status: CREATED)

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "order-001",
    "customerId": "customer-42",
    "product": "Wireless Keyboard",
    "quantity": 2,
    "totalAmount": "79.98",
    "status": "CREATED"
  }' | jq .
```

Expected response (`202 Accepted`):

```json
{
  "eventId": "a3f8c2d1-...",
  "orderId": "order-001",
  "status": "CREATED",
  "topic": "order-events",
  "partition": 1,
  "offset": 0,
  "timestamp": "2024-06-01T12:00:00.000Z"
}
```

#### Publish a confirmed order

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "order-002",
    "customerId": "customer-7",
    "product": "Mechanical Mouse",
    "quantity": 1,
    "totalAmount": "45.00",
    "status": "CONFIRMED"
  }' | jq .
```

#### Publish a cancelled order

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "order-003",
    "customerId": "customer-7",
    "product": "USB Hub",
    "quantity": 1,
    "totalAmount": "25.00",
    "status": "CANCELLED"
  }' | jq .
```

#### Validation error example (blank orderId → 400)

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "",
    "customerId": "customer-1",
    "product": "Widget",
    "quantity": 1,
    "totalAmount": "10.00",
    "status": "CREATED"
  }' | jq .
```

Expected response (`400 Bad Request`):

```json
{
  "status": 400,
  "error": "Validation failed",
  "fields": {
    "orderId": "orderId must not be blank"
  },
  "timestamp": "..."
}
```

### Consuming messages from the topic (optional)

To read the events published by the app using the Kafka CLI:

```bash
docker exec -it kafkaproducer-kafka \
  kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --from-beginning
```

---

## Running tests

### All tests (unit + integration)

```bash
./mvnw clean test
```

The integration tests pull the `confluentinc/cp-kafka:7.6.1` Docker image and
start a real Kafka broker via Testcontainers. Make sure Docker is running.

### Unit tests only (no Docker required)

```bash
./mvnw test -Dtest="com.example.kafkaproducer.unit.*"
```

### Integration tests only

```bash
./mvnw test -Dtest="com.example.kafkaproducer.integration.*"
```

---

## Test overview

### Unit tests (`src/test/java/.../unit/`)

| Test class | What it tests |
|------------|--------------|
| `OrderEventTest` | `OrderEvent.create()` factory (UUID generation, timestamp, field mapping, record equality) |
| `OrderEventServiceTest` | `OrderEventService.publish()` with a mocked `KafkaTemplate` (happy path, failure, interaction verification) |

**Technology:** JUnit 5, AssertJ, Mockito – no Spring context, no Docker.

### Integration tests (`src/test/java/.../integration/`)

| Test class | What it tests |
|------------|--------------|
| `OrderControllerIntegrationTest` | Full HTTP stack via MockMvc against a real Kafka broker in Docker |

**Scenarios covered:**
- `POST /api/orders` returns `202 Accepted` with correct JSON fields
- All `OrderStatus` enum values are accepted
- Blank `orderId`, zero `quantity`, zero `totalAmount`, missing `status` → `400 Bad Request`
- End-to-end delivery: event is readable from the Kafka topic via a raw `KafkaConsumer`

**Technology:** Spring Boot Test, MockMvc, Testcontainers (`KafkaContainer`),
`@DynamicPropertySource`, raw `KafkaConsumer` for assertion.

---

## Key concepts explained

### Why `orderId` as the Kafka message key?

Kafka partitions messages by key. All messages with the same key are guaranteed
to land on the same partition and are therefore delivered to consumers in the
order they were produced. Using `orderId` as the key ensures that a consumer
reading events for a given order always sees them in chronological sequence
(`CREATED → CONFIRMED → SHIPPED → DELIVERED`).

### Why `acks=all`?

`acks=all` (or `-1`) means the broker leader waits for all in-sync replicas to
acknowledge the write before returning success to the producer. This provides
the strongest durability guarantee: even if the leader crashes immediately after
acknowledging, at least one follower has a copy of the message.

### Why `202 Accepted` instead of `200 OK`?

The HTTP `202 Accepted` status signals that the request has been accepted and
forwarded for processing, but the work is not yet complete. Publishing to Kafka
is asynchronous by nature: the HTTP response confirms the event reached the
broker, not that downstream consumers have processed it. `202` is therefore
semantically more accurate than `200 OK`.
