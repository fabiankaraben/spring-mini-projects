# ActiveMQ JMS

A Spring Boot backend that demonstrates **producing and consuming messages** using
**Java Message Service (JMS)** and **Apache ActiveMQ Classic**.

Messages are sent to a queue via a REST endpoint and consumed asynchronously by a
`@JmsListener`. The full round-trip (HTTP → JMS producer → ActiveMQ broker → JMS
consumer → domain processing) is verified by integration tests using **Testcontainers**.

---

## Table of Contents

- [What this project demonstrates](#what-this-project-demonstrates)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Project structure](#project-structure)
- [Running with Docker Compose](#running-with-docker-compose)
- [Running locally (without Docker)](#running-locally-without-docker)
- [API reference](#api-reference)
- [curl examples](#curl-examples)
- [Running the tests](#running-the-tests)
- [ActiveMQ Web Console](#activemq-web-console)

---

## What this project demonstrates

| Concept | Where to look |
|---|---|
| JMS producer using `JmsTemplate` | `MessageProducerService` |
| JMS consumer using `@JmsListener` | `MessageConsumerService` |
| JSON serialisation of JMS messages | `JmsConfig` → `MappingJackson2MessageConverter` |
| Queue (point-to-point) vs Topic (pub-sub) | `application.yml` → `spring.jms.pub-sub-domain: false` |
| Asynchronous processing separation | `MessageConsumerService` delegates to `OrderProcessingService` |
| Bean Validation on REST input | `OrderRequest` (DTO) + `@Valid` in controller |
| Testcontainers integration testing | `ActiveMqJmsIntegrationTest` |
| Unit testing without Spring context | `MessageProducerServiceTest`, `OrderProcessingServiceTest` |

---

## Architecture

```
HTTP Client
    │  POST /api/messages/orders
    ▼
[MessageController]
    │  delegates to
    ▼
[MessageProducerService]
    │  JmsTemplate.convertAndSend(queue, payload)
    ▼
[Apache ActiveMQ Classic Broker]  ←  queue: orders.queue
    │
    ▼
[MessageConsumerService]  ←  @JmsListener(destination = "orders.queue")
    │  delegates to
    ▼
[OrderProcessingService]  ←  domain logic / in-memory store

HTTP Client
    │  GET /api/messages/orders
    ▼
[MessageController]
    │  reads from
    ▼
[OrderProcessingService]  ←  getProcessedOrders()
```

---

## Requirements

| Requirement | Minimum version |
|---|---|
| Java | 21 |
| Maven | 3.9+ (or use the included Maven Wrapper) |
| Docker | 24+ (for running via Docker Compose and for Testcontainers) |
| Docker Compose | v2 (`docker compose` command) |

> **Note**: Docker must be running for both `docker compose up` and `./mvnw clean test`
> (Testcontainers spins up ActiveMQ automatically during tests).

---

## Project structure

```
src/
├── main/
│   ├── java/com/example/activemqjms/
│   │   ├── ActiveMqJmsApplication.java      ← Spring Boot entry point
│   │   ├── config/
│   │   │   └── JmsConfig.java               ← JmsTemplate, MessageConverter, ListenerFactory
│   │   ├── controller/
│   │   │   └── MessageController.java        ← POST/GET /api/messages/orders
│   │   ├── domain/
│   │   │   └── OrderMessage.java            ← JMS message payload (domain model)
│   │   ├── dto/
│   │   │   └── OrderRequest.java            ← REST request body (DTO + validation)
│   │   ├── exception/
│   │   │   └── GlobalExceptionHandler.java  ← Bean Validation error formatting
│   │   └── service/
│   │       ├── MessageProducerService.java  ← publishes to ActiveMQ queue
│   │       ├── MessageConsumerService.java  ← @JmsListener consumer
│   │       └── OrderProcessingService.java  ← domain logic layer
│   └── resources/
│       └── application.yml
└── test/
    ├── java/com/example/activemqjms/
    │   ├── ActiveMqJmsIntegrationTest.java  ← full end-to-end (Testcontainers)
    │   └── service/
    │       ├── MessageProducerServiceTest.java  ← unit test (Mockito)
    │       ├── MessageConsumerServiceTest.java  ← unit test (Mockito)
    │       └── OrderProcessingServiceTest.java  ← unit test (pure JUnit 5)
    └── resources/
        ├── application-test.yml
        ├── docker-java.properties           ← Docker API version fix for Testcontainers
        ├── testcontainers.properties
        └── mockito-extensions/
            └── org.mockito.plugins.MockMaker
```

---

## Running with Docker Compose

This is the **recommended way** to run the project. Docker Compose starts both
**Apache ActiveMQ Classic** and the **Spring Boot application** in isolated containers.

### 1. Build and start all services

```bash
docker compose up --build
```

This will:
1. Build the Spring Boot application into a Docker image (multi-stage build).
2. Pull the `apache/activemq-classic:5.18.3` image if not already cached.
3. Start the ActiveMQ broker and wait for its healthcheck to pass.
4. Start the Spring Boot application once the broker is ready.

### 2. Verify the application is running

```bash
curl http://localhost:8080/api/messages/orders
```

Expected response: `[]` (empty array — no orders processed yet).

### 3. Stop all services

```bash
docker compose down
```

To also remove the named volume (clears all queued messages):

```bash
docker compose down -v
```

---

## Running locally (without Docker)

You need a running ActiveMQ Classic broker accessible on `localhost:61616`.

### Option A – Run ActiveMQ via Docker (broker only)

```bash
docker run -d \
  --name activemq \
  -p 61616:61616 \
  -p 8161:8161 \
  apache/activemq-classic:5.18.3
```

### Option B – Install ActiveMQ locally

Download from [activemq.apache.org](https://activemq.apache.org/components/classic/download/)
and run `bin/activemq start`.

### Start the Spring Boot application

```bash
./mvnw spring-boot:run
```

---

## API reference

### `POST /api/messages/orders`

Publishes an order message to the ActiveMQ queue.

**Request body** (JSON):

| Field | Type | Required | Constraints |
|---|---|---|---|
| `orderId` | string | yes | not blank |
| `product` | string | yes | not blank |
| `quantity` | integer | yes | ≥ 1 |

**Responses**:

| Status | Description |
|---|---|
| `202 Accepted` | Message published successfully; response body contains the `OrderMessage` |
| `400 Bad Request` | Validation failed; response body contains field-level error details |

---

### `GET /api/messages/orders`

Returns a list of all order messages that have been consumed and processed.

**Response**: `200 OK` with a JSON array of `OrderMessage` objects.

---

## curl examples

### Publish a valid order

```bash
curl -s -X POST http://localhost:8080/api/messages/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-2024-001", "product": "Wireless Keyboard", "quantity": 2}' \
  | jq .
```

Expected response (`202 Accepted`):
```json
{
  "messageId": "a3f7c1b2-...",
  "orderId": "ORD-2024-001",
  "product": "Wireless Keyboard",
  "quantity": 2,
  "createdAt": "2024-03-15T10:15:30Z"
}
```

### Check processed orders (after a moment)

```bash
curl -s http://localhost:8080/api/messages/orders | jq .
```

Expected response (the order has been consumed):
```json
[
  {
    "messageId": "a3f7c1b2-...",
    "orderId": "ORD-2024-001",
    "product": "Wireless Keyboard",
    "quantity": 2,
    "createdAt": "2024-03-15T10:15:30Z"
  }
]
```

### Publish multiple orders

```bash
curl -s -X POST http://localhost:8080/api/messages/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-2024-002", "product": "Laptop Stand", "quantity": 1}' | jq .

curl -s -X POST http://localhost:8080/api/messages/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-2024-003", "product": "USB-C Hub", "quantity": 3}' | jq .
```

### Validation error – blank orderId

```bash
curl -s -X POST http://localhost:8080/api/messages/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "", "product": "Widget", "quantity": 1}' \
  | jq .
```

Expected response (`400 Bad Request`):
```json
{
  "timestamp": "2024-03-15T10:16:00Z",
  "status": 400,
  "errors": {
    "orderId": "orderId must not be blank"
  }
}
```

### Validation error – quantity below minimum

```bash
curl -s -X POST http://localhost:8080/api/messages/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-999", "product": "Widget", "quantity": 0}' \
  | jq .
```

Expected response (`400 Bad Request`):
```json
{
  "timestamp": "2024-03-15T10:16:00Z",
  "status": 400,
  "errors": {
    "quantity": "quantity must be at least 1"
  }
}
```

---

## Running the tests

> **Prerequisites**: Docker must be running. Testcontainers automatically pulls and
> starts an `apache/activemq-classic:5.18.3` container for the integration tests.

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests (no Docker required)

```bash
./mvnw clean test -Dtest="OrderProcessingServiceTest,MessageProducerServiceTest,MessageConsumerServiceTest"
```

### Run only the integration test

```bash
./mvnw clean test -Dtest="ActiveMqJmsIntegrationTest"
```

### Test suite summary

| Test class | Type | What it tests |
|---|---|---|
| `OrderProcessingServiceTest` | Unit (pure JUnit 5) | Domain logic: accumulates processed orders, count, unmodifiable snapshot |
| `MessageProducerServiceTest` | Unit (Mockito) | Producer: correct destination, DTO→domain mapping, auto-generated fields |
| `MessageConsumerServiceTest` | Unit (Mockito) | Consumer: delegates to `OrderProcessingService` exactly once |
| `ActiveMqJmsIntegrationTest` | Integration (Testcontainers) | Full end-to-end: HTTP → JMS → broker → consumer → domain |

---

## ActiveMQ Web Console

When running with Docker Compose, the ActiveMQ management console is available at:

```
http://localhost:8161
```

Default credentials: `admin` / `admin`

From the console you can:
- Browse the `orders.queue` and inspect pending/consumed messages.
- View message counts, broker statistics, and connection details.
- Manually send or purge test messages.
