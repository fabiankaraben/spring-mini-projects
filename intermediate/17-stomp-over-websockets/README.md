# STOMP over WebSockets

A Spring Boot backend implementing **pub/sub message routing** using the STOMP
protocol over WebSocket. This mini-project goes beyond a simple chat application
to demonstrate the full power of topic-based pub/sub routing, including named
channels, message history, and private (direct) user-to-user messaging.

---

## What is STOMP?

**STOMP** (Simple Text Oriented Messaging Protocol) is a sub-protocol layered on
top of raw WebSocket. It adds messaging semantics familiar from systems like
ActiveMQ or RabbitMQ:

| Concept | Description |
|---------|-------------|
| **CONNECT** | Establishes a STOMP session over the WebSocket connection |
| **SUBSCRIBE** | Registers interest in a named destination (topic or queue) |
| **SEND** | Publishes a message to a destination |
| **UNSUBSCRIBE** | Cancels a subscription |
| **DISCONNECT** | Ends the STOMP session |

Spring Boot's `@EnableWebSocketMessageBroker` activates a full STOMP broker
with an in-memory message router, so no external broker (RabbitMQ, ActiveMQ)
is needed for this mini-project.

---

## Architecture

```
HTTP client  ─── REST /api/topics ───────────────► TopicRestController
                                                           │
WS client ─── STOMP SEND /app/topic/{name} ────► TopicMessageController
          └── STOMP SEND /app/private ──────────► PrivateMessageController
                                                           │
                                               In-memory STOMP broker
                                                           │
          ┌── STOMP SUBSCRIBE /topic/{name} ◄─────────────┘  (fan-out pub/sub)
          └── STOMP SUBSCRIBE /user/queue/private ◄──────────  (private routing)
```

### Destination prefixes

| Prefix | Role |
|--------|------|
| `/app` | Incoming client messages – routed to `@MessageMapping` controller methods |
| `/topic` | Broadcast destinations – all subscribers receive every message (pub/sub) |
| `/user` | User-specific destinations – messages delivered only to the named user's session |

### Source structure

```
src/main/java/com/example/stompwebsockets/
├── StompOverWebSocketsApplication.java   # Entry point
├── config/
│   └── WebSocketConfig.java              # STOMP broker + endpoint configuration
├── domain/
│   ├── TopicMessage.java                 # Broadcast message domain model
│   └── PrivateMessage.java               # Private message domain model
├── dto/
│   ├── PublishRequest.java               # Incoming publish payload (validated)
│   └── PrivateMessageRequest.java        # Incoming private message payload (validated)
├── service/
│   ├── TopicService.java                 # Topic registry, message building, history
│   └── PrivateMessageService.java        # Private message dispatch
└── controller/
    ├── TopicMessageController.java       # STOMP: /app/topic/{name} → /topic/{name}
    ├── PrivateMessageController.java     # STOMP: /app/private → /user/queue/private
    └── TopicRestController.java          # REST: /api/topics CRUD + history
```

---

## Requirements

- **Java 21** or later
- **Maven** (or use the included Maven Wrapper `./mvnw`)
- **Docker** with **Docker Desktop** (for running via Docker Compose and for
  Testcontainers integration tests)

---

## Running the application

### Option 1 – Maven Wrapper (local development)

```bash
./mvnw spring-boot:run
```

The server starts at **http://localhost:8080**.

### Option 2 – Docker Compose (recommended)

Build the image and start the container:

```bash
docker compose up --build
```

Stop and remove the container:

```bash
docker compose down
```

> **Note:** The `docker compose up --build` command builds the Docker image from
> the `Dockerfile` and starts it as a container. The application exposes port
> `8080` on the host machine.

---

## REST API – Topic Management

These HTTP endpoints allow you to manage topics and retrieve message history.

### Create a topic

```bash
curl -X POST "http://localhost:8080/api/topics?name=news"
```

**Response** (`201 Created`):
```json
{
  "topic": "news",
  "message": "Topic registered successfully"
}
```

Topics are created automatically the first time a message is published to them,
but you can also pre-register them with this endpoint.

### List all topics

```bash
curl http://localhost:8080/api/topics
```

**Response** (`200 OK`):
```json
["finance", "news", "sports"]
```

### Get message history for a topic

```bash
curl http://localhost:8080/api/topics/news/history
```

**Response** (`200 OK`):
```json
[
  {
    "topic": "news",
    "sender": "alice",
    "payload": "Breaking news headline",
    "timestamp": "2024-06-01T10:30:00.123456Z"
  }
]
```

Returns `404 Not Found` if the topic does not exist.

---

## STOMP WebSocket API

The STOMP endpoint is available at:

```
ws://localhost:8080/ws/websocket
```

> **SockJS** fallback (for browser clients) is also available at:
> `http://localhost:8080/ws`

### Connect (JavaScript / SockJS client example)

```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, frame => {
  console.log('Connected:', frame);
});
```

### Publish to a topic

Send a STOMP SEND frame to `/app/topic/{name}`:

```javascript
stompClient.send('/app/topic/news', {}, JSON.stringify({
  sender: 'alice',
  payload: 'Breaking news!'
}));
```

### Subscribe to a topic

```javascript
stompClient.subscribe('/topic/news', message => {
  const body = JSON.parse(message.body);
  console.log(`[${body.topic}] ${body.sender}: ${body.payload}`);
});
```

### Send a private message

Send to `/app/private`; the server routes it exclusively to the recipient:

```javascript
stompClient.send('/app/private', {}, JSON.stringify({
  sender: 'alice',
  recipient: 'bob',
  content: 'Hey Bob, this is private!'
}));
```

### Receive private messages

Subscribe to `/user/queue/private` — Spring automatically routes messages
addressed to you here:

```javascript
stompClient.subscribe('/user/queue/private', message => {
  const body = JSON.parse(message.body);
  console.log(`Private from ${body.sender}: ${body.content}`);
});
```

---

## Message Formats

### TopicMessage (broadcast)

Sent to all subscribers of `/topic/{name}` after a STOMP publish:

```json
{
  "topic":     "news",
  "sender":    "alice",
  "payload":   "Breaking news headline",
  "timestamp": "2024-06-01T10:30:00.123456Z"
}
```

### PrivateMessage (direct)

Delivered only to the recipient's `/user/queue/private` subscription:

```json
{
  "sender":    "alice",
  "recipient": "bob",
  "content":   "Hey Bob!",
  "timestamp": "2024-06-01T10:30:01.456789Z"
}
```

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **In-memory STOMP broker** | No external broker dependency; simpler for the mini-project. Replace with `enableStompBrokerRelay` for production. |
| **Per-topic bounded history** | Last 50 messages per topic kept in memory. Late-joining clients can fetch history via REST. |
| **Server-assigned timestamps** | Clients cannot spoof delivery time; consistent ordering. |
| **Auto-registration of topics** | A topic is created implicitly on first publish; no setup required. |
| **`/user` prefix for private msgs** | Spring's built-in user-destination feature routes to specific STOMP sessions. |

---

## Running the Tests

### Run all tests

```bash
./mvnw clean test
```

### Unit tests only

```bash
./mvnw test -Dtest="com.example.stompwebsockets.unit.*"
```

### Integration tests only (in-process Spring Boot)

```bash
./mvnw test -Dtest="TopicPubSubIntegrationTest,TopicRestIntegrationTest"
```

### Testcontainers black-box test

The `ContainerIntegrationTest` starts the full application inside a Docker
container and tests it as a black box. The Docker image must be built first:

```bash
# Build the image (required before running ContainerIntegrationTest)
docker build -t stomp-over-websockets:latest .

# Run only the container integration test
./mvnw test -Dtest="ContainerIntegrationTest"
```

### Test structure

| Test class | Type | What it covers |
|------------|------|----------------|
| `TopicServiceTest` | Unit | Topic registry, message building, history eviction |
| `PrivateMessageServiceTest` | Unit | Private message dispatch; mocks `SimpMessageSendingOperations` |
| `TopicPubSubIntegrationTest` | Integration (in-process) | Real STOMP pub/sub, fan-out, topic isolation, history |
| `TopicRestIntegrationTest` | Integration (MockMvc) | REST endpoints: create topic, list, history CRUD |
| `ContainerIntegrationTest` | Integration (Testcontainers) | Black-box: REST + STOMP against a real Docker container |

---

## Docker

### Build the image manually

```bash
docker build -t stomp-over-websockets:latest .
```

### Run with Docker Compose

```bash
# Start
docker compose up --build

# Stop
docker compose down
```

### What the Dockerfile does

The `Dockerfile` uses a **two-stage build**:

1. **Build stage** (`eclipse-temurin:21-jdk-alpine`) – downloads dependencies
   and builds the fat JAR with `./mvnw package -DskipTests`.
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`) – copies only the JAR
   into a minimal JRE image, keeping the final image small.
