# 15 – Kafka Consumer

A Spring Boot backend that **consumes events from an Apache Kafka topic** using `@KafkaListener`.  
Consumed events are processed, enriched with consumer-side metadata (partition, offset, processed-at timestamp), stored in memory, and exposed through a read-only REST API.

---

## What this mini-project demonstrates

| Concept | Where to look |
|---|---|
| `@KafkaListener` consuming JSON events | `OrderEventListener` |
| Manual offset acknowledgement (`MANUAL_IMMEDIATE`) | `OrderEventListener`, `KafkaConsumerConfig` |
| Explicit `ConsumerFactory` / container factory configuration | `KafkaConsumerConfig` |
| Status-based domain routing (`switch` on enum) | `OrderEventProcessorService` |
| Thread-safe in-memory event store (`CopyOnWriteArrayList`) | `OrderEventProcessorService` |
| REST API for querying consumed events | `OrderEventController` |
| Unit tests with Mockito (no Spring, no Kafka) | `unit/` package |
| Integration tests with Testcontainers + Awaitility | `integration/` package |
| Docker Compose full-stack deployment | `docker-compose.yml` |

---

## Requirements

- **Java 21** or later
- **Maven 3.9+** (or use the included Maven Wrapper `./mvnw`)
- **Docker Desktop** (for Docker Compose and Testcontainers)

---

## Project structure

```
15-kafka-consumer/
├── src/
│   ├── main/
│   │   ├── java/com/example/kafkaconsumer/
│   │   │   ├── KafkaConsumerApplication.java       # Spring Boot entry point
│   │   │   ├── config/
│   │   │   │   └── KafkaConsumerConfig.java         # ConsumerFactory + container factory
│   │   │   ├── domain/
│   │   │   │   ├── OrderEvent.java                  # Incoming event record (from Kafka)
│   │   │   │   ├── OrderStatus.java                 # Enum: CREATED, CONFIRMED, SHIPPED, …
│   │   │   │   └── ProcessedOrderEvent.java         # Event + consumer metadata
│   │   │   ├── listener/
│   │   │   │   └── OrderEventListener.java          # @KafkaListener method
│   │   │   ├── service/
│   │   │   │   └── OrderEventProcessorService.java  # Domain logic + in-memory store
│   │   │   ├── controller/
│   │   │   │   └── OrderEventController.java        # GET /api/events, GET /api/events/count
│   │   │   └── exception/
│   │   │       └── GlobalExceptionHandler.java      # Centralised error responses
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/com/example/kafkaconsumer/
│       │   ├── unit/
│       │   │   ├── OrderEventProcessorServiceTest.java
│       │   │   └── OrderEventListenerTest.java
│       │   └── integration/
│       │       └── OrderEventConsumerIntegrationTest.java
│       └── resources/
│           ├── application-test.yml
│           ├── docker-java.properties
│           └── testcontainers.properties
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## Running with Docker Compose

Docker Compose starts **Kafka (KRaft mode)** and the **Spring Boot app** together.

### 1. Build and start all services

```bash
docker compose up --build
```

Compose will:
1. Start a single-broker Kafka cluster in KRaft mode (no ZooKeeper).
2. Wait for the Kafka broker health-check to pass.
3. Build and start the Spring Boot consumer app.

### 2. Verify the app is running

```bash
curl http://localhost:8080/api/events/count
# {"count":0}
```

### 3. Produce a test message

Use the Kafka CLI inside the running Kafka container to send a JSON event:

```bash
docker exec -it kafkaconsumer-kafka \
  kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic order-events
```

Then paste this JSON and press Enter:

```json
{"eventId":"evt-001","orderId":"order-001","customerId":"cust-1","product":"Laptop","quantity":1,"totalAmount":999.99,"status":"CREATED","occurredAt":"2024-01-15T10:00:00Z"}
```

Press `Ctrl+C` to exit the producer.

> **Note:** The Kafka CLI sends raw strings. For proper JSON deserialization the consumer
> is configured with `spring.json.use.type.headers: false` and a fixed value type,
> so type headers are not required.

### 4. Query the consumed events

```bash
# List all consumed events
curl http://localhost:8080/api/events

# Filter by status
curl "http://localhost:8080/api/events?status=CREATED"

# Get the total count
curl http://localhost:8080/api/events/count
```

### 5. Stop all services

```bash
docker compose down
```

---

## Running locally (without Docker Compose)

If you want to run the Spring Boot app on your host machine while Kafka runs in Docker:

### 1. Start Kafka only

```bash
docker compose up kafka
```

### 2. Run the app

```bash
./mvnw spring-boot:run
```

The app connects to `localhost:9092` by default (see `application.yml`).

---

## REST API reference

### `GET /api/events`

Returns all order events consumed from Kafka since the application started.

**Optional query parameter:** `?status=<STATUS>` — filter by order status.  
Valid values: `CREATED`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED` (case-insensitive).

```bash
# All events
curl http://localhost:8080/api/events

# Only CONFIRMED events
curl "http://localhost:8080/api/events?status=CONFIRMED"

# Invalid status → 400 Bad Request
curl "http://localhost:8080/api/events?status=UNKNOWN"
```

**Response (200 OK):**
```json
[
  {
    "eventId": "evt-001",
    "orderId": "order-001",
    "customerId": "cust-1",
    "product": "Laptop",
    "quantity": 1,
    "totalAmount": 999.99,
    "status": "CREATED",
    "occurredAt": "2024-01-15T10:00:00Z",
    "partition": 0,
    "offset": 0,
    "processedAt": "2024-01-15T10:00:01.123456Z"
  }
]
```

---

### `GET /api/events/count`

Returns the total number of events processed so far.

```bash
curl http://localhost:8080/api/events/count
```

**Response (200 OK):**
```json
{"count": 42}
```

---

## Running the tests

### All tests (unit + integration)

Integration tests require **Docker Desktop** to be running (Testcontainers spins up a real Kafka container automatically).

```bash
./mvnw clean test
```

### Unit tests only (no Docker required)

```bash
./mvnw test -pl . -Dtest="com.example.kafkaconsumer.unit.*"
```

### Integration tests only

```bash
./mvnw test -pl . -Dtest="com.example.kafkaconsumer.integration.*"
```

---

## Test coverage overview

### Unit tests (`unit/` package)

Tests run without Spring context, without Kafka, without Docker — pure Java + Mockito.

| Test class | What it covers |
|---|---|
| `OrderEventProcessorServiceTest` | Stores events, preserves all fields, filters by status, count, immutability |
| `OrderEventListenerTest` | Delegates to service, acknowledges on success, does NOT acknowledge on failure, re-throws exception |

### Integration tests (`integration/` package)

Tests use a real Kafka broker via Testcontainers + the full Spring Boot context.

| Test | What it covers |
|---|---|
| `consumer_shouldProcessProducedMessage` | End-to-end: produce → consume → verify stored event |
| `consumer_shouldRecordPartitionAndOffset` | Kafka metadata is captured correctly |
| `consumer_shouldHandleAllOrderStatuses` | All `OrderStatus` enum values are routed correctly |
| `getEvents_shouldReturn200WithJsonArray` | REST endpoint returns 200 with JSON array |
| `getCount_shouldReturn200WithCountField` | Count endpoint returns numeric field |
| `getEvents_shouldReturn400ForInvalidStatus` | Invalid status filter returns 400 |
| `getEvents_shouldIncludeConsumedEvents` | REST response includes consumed event |
| `getEvents_shouldFilterByStatus` | Status filter returns only matching events |

---

## Key concepts explained

### `@KafkaListener`

```java
@KafkaListener(
    topics = "${app.kafka.topic.orders}",
    groupId = "${spring.kafka.consumer.group-id}",
    containerFactory = "kafkaListenerContainerFactory"
)
public void onOrderEvent(ConsumerRecord<String, OrderEvent> record, Acknowledgment ack) { … }
```

Spring Kafka starts a poll loop on a background thread. For each record returned by `KafkaConsumer.poll()`, it deserialises the key/value and invokes this method. The `ConsumerRecord` parameter gives access to both the deserialized payload and the Kafka metadata (topic, partition, offset).

### Manual acknowledgement

With `AckMode.MANUAL_IMMEDIATE`, the consumer offset is only committed when `ack.acknowledge()` is called explicitly. This prevents silent message loss if the app crashes between fetching a record and finishing processing. The consumer implements **at-least-once** delivery semantics: messages may be redelivered after a crash but will never be silently skipped.

### Consumer groups

All instances of this app sharing the same `group-id` cooperate: Kafka assigns each partition to exactly one consumer in the group. Scale out by increasing the number of app replicas — Kafka rebalances partition assignments automatically.

### Awaitility in tests

Because Kafka consumption is asynchronous (the `@KafkaListener` runs on a separate thread), integration tests use **Awaitility** to poll for the expected condition instead of `Thread.sleep()`:

```java
await()
    .atMost(15, TimeUnit.SECONDS)
    .pollInterval(200, TimeUnit.MILLISECONDS)
    .untilAsserted(() -> assertThat(processorService.getProcessedEvents())
        .anyMatch(e -> e.orderId().equals("order-it-1")));
```
