# RabbitMQ Consumer

A Spring Boot backend that listens for messages on a RabbitMQ queue using `@RabbitListener`.
This mini-project is the consumer counterpart to [12-rabbitmq-producer](../12-rabbitmq-producer).

## What this mini-project demonstrates

- **`@RabbitListener`** – wires a plain Java method to a RabbitMQ queue; every message
  published to that queue automatically triggers the annotated method.
- **JSON deserialization** – `Jackson2JsonMessageConverter` converts the raw JSON AMQP
  message body back into a Java `OrderMessage` object before the listener method is called.
- **Message acknowledgement (AUTO mode)** – Spring AMQP sends `basic.ack` when the listener
  returns normally, or `basic.nack` when it throws an exception.
- **Dead Letter Queue (DLQ)** – messages that fail processing are forwarded to a DLQ
  (`orders.queue.dlq`) instead of being discarded, preventing message loss.
- **Observability REST API** – two endpoints expose the consumer's runtime state so you can
  verify consumption without connecting to RabbitMQ directly.
- **Idiomatic service/controller separation** – the `@RabbitListener` lives in the service
  layer, keeping the controller focused on HTTP concerns only.

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (or use the included Maven Wrapper) |
| Docker & Docker Compose | any recent version |

> **Note:** Docker is required to run the full application stack (RabbitMQ + app) and to
> execute the integration tests (Testcontainers spins up a real RabbitMQ container).

## Project structure

```
src/
├── main/
│   ├── java/com/example/rabbitmqconsumer/
│   │   ├── RabbitmqConsumerApplication.java   # Spring Boot entry point
│   │   ├── config/
│   │   │   └── RabbitMQConfig.java            # Exchange, queue, DLQ, and converter beans
│   │   ├── controller/
│   │   │   └── MessageController.java         # GET /api/messages/stats and /processed
│   │   ├── domain/
│   │   │   └── OrderMessage.java              # AMQP message payload (JSON ↔ Java)
│   │   └── service/
│   │       └── MessageConsumerService.java    # @RabbitListener + processing logic
│   └── resources/
│       └── application.yml
└── test/
    ├── java/com/example/rabbitmqconsumer/
    │   ├── MessageConsumerIntegrationTest.java # Full integration tests (Testcontainers)
    │   ├── domain/
    │   │   └── OrderMessageTest.java          # Unit tests for the domain model
    │   └── service/
    │       └── MessageConsumerServiceTest.java # Unit tests for the service layer
    └── resources/
        ├── application-test.yml
        ├── docker-java.properties              # docker-java API version override
        └── testcontainers.properties           # Testcontainers Docker API config
```

## Running with Docker Compose (recommended)

This is the simplest way to start both RabbitMQ and the consumer application together.

```bash
# Build the image and start both services in the background
docker compose up --build -d

# Follow the consumer application logs
docker compose logs -f app

# Stop and remove the containers (keep the RabbitMQ volume)
docker compose down

# Stop and remove everything including the volume (fresh start)
docker compose down -v
```

Once running, the consumer is available at `http://localhost:8080` and the RabbitMQ
Management UI is available at `http://localhost:15672` (credentials: `guest` / `guest`).

## Publishing test messages

The consumer listens on `orders.queue`. You can publish messages to it using the
RabbitMQ Management UI or via its HTTP API:

### Publish via RabbitMQ Management HTTP API (curl)

```bash
# Publish a valid order message to the exchange
curl -s -u guest:guest \
  -H "Content-Type: application/json" \
  -X POST http://localhost:15672/api/exchanges/%2F/orders.exchange/publish \
  -d '{
    "properties": {"content_type": "application/json", "delivery_mode": 2},
    "routing_key": "orders.routing.key",
    "payload": "{\"messageId\":\"msg-001\",\"orderId\":\"ORD-2024-001\",\"product\":\"Wireless Keyboard\",\"quantity\":3,\"createdAt\":\"2024-01-15T10:00:00Z\"}",
    "payload_encoding": "string"
  }'
```

### Verify consumption via the REST API

```bash
# Check how many messages have been processed
curl -s http://localhost:8080/api/messages/stats | python3 -m json.tool

# View the list of recently processed messages
curl -s http://localhost:8080/api/messages/processed | python3 -m json.tool
```

#### Example responses

**`GET /api/messages/stats`**
```json
{
  "totalProcessed": 3
}
```

**`GET /api/messages/processed`**
```json
[
  {
    "messageId": "msg-001",
    "orderId": "ORD-2024-001",
    "product": "Wireless Keyboard",
    "quantity": 3,
    "processedAt": "2024-01-15T10:00:05.123456Z"
  }
]
```

## Running locally (without Docker)

If you have a RabbitMQ instance running locally on `localhost:5672`, you can run the
application directly with the Maven Wrapper:

```bash
./mvnw spring-boot:run
```

To point the app at a different RabbitMQ host, set environment variables:

```bash
RABBITMQ_HOST=my-rabbit-host RABBITMQ_PORT=5672 ./mvnw spring-boot:run
```

## Running the tests

### All tests (unit + integration)

The integration tests use Testcontainers, which automatically pulls and starts a
RabbitMQ Docker container. Docker must be running before executing the tests.

```bash
./mvnw clean test
```

### Unit tests only (no Docker required)

```bash
./mvnw test -Dgroups=""
```

The unit tests (`OrderMessageTest`, `MessageConsumerServiceTest`) do not load a Spring
context and do not require Docker. They run in milliseconds.

### Test summary

| Test class | Type | What it covers |
|---|---|---|
| `OrderMessageTest` | Unit | Domain model construction, getters/setters, toString |
| `MessageConsumerServiceTest` | Unit | processOrder logic, consumeOrder validation, reset, counter, log |
| `MessageConsumerIntegrationTest` | Integration | @RabbitListener end-to-end, REST API, DLQ routing |

## RabbitMQ topology

```
[orders.exchange]   ← Direct Exchange
      │  routing key = "orders.routing.key"
      ▼
[orders.queue]      ← Durable Queue  ──→  @RabbitListener (MessageConsumerService)
      │
      │  (on listener exception → basic.nack → dead-letter)
      ▼
[orders.exchange.dlx]  ← Dead Letter Exchange
      │  routing key = "orders.queue.dlq"
      ▼
[orders.queue.dlq]  ← Dead Letter Queue (failed messages land here)
```

## Docker Compose services

| Service | Image | Ports | Purpose |
|---|---|---|---|
| `rabbitmq` | `rabbitmq:3.13-management-alpine` | 5672, 15672 | AMQP broker + Management UI |
| `app` | built from `Dockerfile` | 8080 | Spring Boot consumer |

The `app` service uses `depends_on: condition: service_healthy` to wait for RabbitMQ's
healthcheck to pass before starting, preventing connection-refused errors at startup.
