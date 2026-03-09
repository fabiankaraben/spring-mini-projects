# RabbitMQ Producer

A Spring Boot backend that publishes asynchronous messages to a RabbitMQ exchange using Spring AMQP.

## What this mini-project demonstrates

- Configuring a **Direct Exchange**, a durable **Queue**, and a **Binding** with Spring AMQP.
- Publishing JSON messages with `RabbitTemplate.convertAndSend` (fire-and-forget / async).
- Separating the HTTP layer (controller) from the messaging layer (service).
- Using `Jackson2JsonMessageConverter` so messages are human-readable JSON in the broker.
- **Unit tests** with JUnit 5 + Mockito (no broker needed, runs in milliseconds).
- **Integration tests** with Testcontainers that spin up a real RabbitMQ Docker container.
- Running the entire stack (app + broker) with Docker Compose.

---

## Requirements

| Tool | Minimum version |
|------|----------------|
| Java | 21 |
| Maven | 3.9+ (or use the included `./mvnw` wrapper) |
| Docker Desktop | 4.x (required for Docker Compose and Testcontainers) |

---

## Project structure

```
src/main/java/com/example/rabbitmqproducer/
├── RabbitmqProducerApplication.java   # Spring Boot entry point
├── config/
│   └── RabbitMQConfig.java            # Exchange, queue, binding, JSON converter
├── controller/
│   └── MessageController.java         # POST /api/messages/orders endpoint
├── domain/
│   └── OrderMessage.java              # Domain model (the AMQP message payload)
└── service/
    └── MessageProducerService.java    # Business logic + RabbitTemplate publish

src/test/java/com/example/rabbitmqproducer/
├── MessageProducerIntegrationTest.java        # Full integration tests (Testcontainers)
├── domain/
│   └── OrderMessageTest.java                  # Unit tests for the domain model
└── service/
    └── MessageProducerServiceTest.java        # Unit tests for the service layer
```

---

## RabbitMQ topology

```
HTTP Client
    │  POST /api/messages/orders
    ▼
[Spring Boot Producer]
    │  RabbitTemplate.convertAndSend(exchange, routingKey, message)
    ▼
[orders.exchange]  ← Direct Exchange (durable)
    │  routing key = "orders.routing.key"
    ▼
[orders.queue]     ← Durable Queue
    │
    ▼
(a future consumer reads messages from here)
```

---

## Running with Docker Compose (recommended)

This starts both the Spring Boot application and RabbitMQ together.

```bash
# Build the app image and start all services
docker compose up --build

# Run in the background
docker compose up --build -d

# Stop and remove containers (data volume is preserved)
docker compose down

# Stop and also remove the RabbitMQ data volume (start fresh)
docker compose down -v
```

The application will be available at **http://localhost:8080**.

The **RabbitMQ Management UI** will be available at **http://localhost:15672**
(login: `guest` / `guest`). You can inspect the `orders.exchange`, `orders.queue`,
and watch messages flow in real time.

---

## Running locally (without Docker Compose)

You still need a running RabbitMQ broker. The quickest way is:

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3.13-management-alpine
```

Then start the Spring Boot application:

```bash
./mvnw spring-boot:run
```

---

## API reference

### POST `/api/messages/orders`

Publishes an order message to the `orders.exchange` RabbitMQ exchange.

**Request body (JSON)**

| Field     | Type    | Required | Validation         | Description                      |
|-----------|---------|----------|--------------------|----------------------------------|
| `orderId` | string  | yes      | not blank          | Business order identifier        |
| `product` | string  | yes      | not blank          | Name of the product ordered      |
| `quantity`| integer | yes      | min 1              | Number of units ordered          |

**Response: `202 Accepted`**

```json
{
  "messageId": "c3a4b1d2-...",
  "orderId": "ORD-20240315-001",
  "product": "Wireless Keyboard",
  "quantity": 3,
  "createdAt": "2024-03-15T10:30:00.000000Z"
}
```

| Field       | Description                                                        |
|-------------|--------------------------------------------------------------------|
| `messageId` | Auto-generated UUID for idempotency tracking by consumers          |
| `createdAt` | UTC timestamp when the message was created                         |

---

## curl examples

### Publish a valid order

```bash
curl -s -X POST http://localhost:8080/api/messages/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","product":"Wireless Keyboard","quantity":3}' | jq .
```

Expected response (HTTP 202):

```json
{
  "messageId": "c3a4b1d2-8f21-4e3a-b9c0-1234567890ab",
  "orderId": "ORD-001",
  "product": "Wireless Keyboard",
  "quantity": 3,
  "createdAt": "2024-03-15T10:30:00.000000Z"
}
```

### Publish another order

```bash
curl -s -X POST http://localhost:8080/api/messages/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-002","product":"Mechanical Mouse","quantity":1}' | jq .
```

### Validation error – blank orderId (HTTP 400)

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/messages/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"","product":"Keyboard","quantity":1}'
# → 400
```

### Validation error – zero quantity (HTTP 400)

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/messages/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-003","product":"Mouse","quantity":0}'
# → 400
```

---

## Running the tests

### All tests (unit + integration)

Integration tests require Docker Desktop to be running (Testcontainers pulls the
`rabbitmq:3.13-management-alpine` image automatically).

```bash
./mvnw clean test
```

### Unit tests only (no Docker required)

Unit tests use Mockito to mock `RabbitTemplate` so they run without any Docker container.

```bash
./mvnw test -Dtest="OrderMessageTest,MessageProducerServiceTest"
```

### Integration tests only

```bash
./mvnw test -Dtest="MessageProducerIntegrationTest"
```

---

## Test coverage summary

| Test class | Type | What it covers |
|---|---|---|
| `OrderMessageTest` | Unit | Domain model construction, auto-generated UUID/timestamp, getter/setter round-trip |
| `MessageProducerServiceTest` | Unit | Service publishes to correct exchange/routing-key, maps DTO fields, generates unique message IDs |
| `MessageProducerIntegrationTest` | Integration | HTTP 202 response, Bean Validation (400 on bad input), message actually arrives in the real RabbitMQ queue, queue message count, multiple messages |

---

## Environment variables

These can be set to override defaults when running outside Docker Compose.

| Variable | Default | Description |
|---|---|---|
| `RABBITMQ_HOST` | `localhost` | RabbitMQ broker hostname |
| `RABBITMQ_PORT` | `5672` | AMQP port |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `RABBITMQ_VHOST` | `/` | RabbitMQ virtual host |
