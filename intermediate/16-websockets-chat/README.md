# 16 – WebSockets Chat

A Spring Boot backend that broadcasts chat messages to all connected clients using
**Spring WebSocket** with the **STOMP** sub-protocol and **SockJS** fallback.

---

## What this mini-project demonstrates

| Feature | Details |
|---------|---------|
| **WebSocket endpoint** | `/ws` – clients connect here (SockJS-enabled) |
| **STOMP messaging** | Structured pub/sub on top of raw WebSocket frames |
| **In-memory broker** | Spring's built-in simple broker routes messages to `/topic/**` subscribers |
| **Broadcast** | Every message sent to `/app/chat.send` is relayed to **all** subscribers of `/topic/messages` |
| **Lifecycle events** | JOIN / LEAVE system messages when users enter or exit the chat |
| **Chat history** | Last 100 messages kept in memory; accessible via REST |
| **REST endpoint** | `GET /api/chat/history` – returns recent messages as JSON |

---

## Architecture overview

```
Client                  Server
  |                        |
  |-- CONNECT ws:/ws -----> WebSocketConfig (SockJS endpoint)
  |                        |
  |-- SUBSCRIBE /topic/messages
  |                        |
  |-- SEND /app/chat.send ->  ChatController.handleSend()
  |                               └─> ChatService.buildAndStoreMessage()
  |                               └─> @SendTo /topic/messages
  |<-- BROADCAST ChatMessage ──────── In-memory broker (all subscribers)
```

---

## Requirements

- **Java 21+**
- **Maven 3.9+** (or use the included Maven Wrapper `./mvnw`)
- **Docker Desktop** (for running via Docker Compose)

---

## Running locally (without Docker)

```bash
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## Running with Docker Compose

Build the image and start the container:

```bash
docker compose up --build
```

Stop and remove the container:

```bash
docker compose down
```

The application is available at **http://localhost:8080** (same port).

> **Note:** The Docker image is built with a multi-stage Dockerfile.
> The build stage uses `eclipse-temurin:21-jdk-alpine` and the runtime
> stage uses the leaner `eclipse-temurin:21-jre-alpine`.

---

## Using the REST API with curl

### Fetch recent chat history

```bash
curl -s http://localhost:8080/api/chat/history | jq .
```

Expected response when no messages have been sent yet:

```json
[]
```

Expected response after some activity:

```json
[
  {
    "sender": "Alice",
    "content": "Alice joined the chat",
    "type": "JOIN",
    "timestamp": "2024-01-01T12:00:00.000Z"
  },
  {
    "sender": "Alice",
    "content": "Hello everyone!",
    "type": "CHAT",
    "timestamp": "2024-01-01T12:00:05.000Z"
  }
]
```

---

## Using the WebSocket endpoint

The WebSocket endpoint requires a STOMP-capable client.
The server listens at `ws://localhost:8080/ws` (SockJS: `http://localhost:8080/ws`).

### STOMP destinations

| Direction | Destination | Description |
|-----------|-------------|-------------|
| Client → Server | `/app/chat.send` | Send a chat message |
| Client → Server | `/app/chat.join` | Announce a user joining |
| Client → Server | `/app/chat.leave` | Announce a user leaving |
| Server → Client | `/topic/messages` | Subscribe to receive all broadcasts |

### Message payload format

All messages sent **to** the server use this JSON structure:

```json
{
  "sender": "Alice",
  "content": "Hello!"
}
```

For JOIN and LEAVE events the `content` field can be empty – the server
generates the human-readable text automatically.

### Quick test with websocat

[websocat](https://github.com/vi/websocat) is a command-line WebSocket client.
Because the endpoint uses SockJS/STOMP, it is easier to use a dedicated STOMP
client (e.g., the browser console with `@stomp/stompjs`). However, you can
observe SockJS negotiation with:

```bash
# Observe the SockJS info endpoint (standard HTTP)
curl -s http://localhost:8080/ws/info
```

### Quick test with a browser console

Open your browser's developer console while the server is running and paste:

```javascript
// Load SockJS and STOMP from CDN (run in browser console)
const script1 = document.createElement('script');
script1.src = 'https://cdn.jsdelivr.net/npm/sockjs-client/dist/sockjs.min.js';
document.head.appendChild(script1);

const script2 = document.createElement('script');
script2.src = 'https://cdn.jsdelivr.net/npm/@stomp/stompjs/bundles/stomp.umd.min.js';
document.head.appendChild(script2);

// After scripts load (~1 second), connect and send a message:
setTimeout(() => {
  const client = new StompJs.Client({
    webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
    onConnect: () => {
      // Subscribe to receive all messages
      client.subscribe('/topic/messages', msg => {
        console.log('Received:', JSON.parse(msg.body));
      });
      // Join the chat
      client.publish({ destination: '/app/chat.join', body: JSON.stringify({ sender: 'TestUser', content: '' }) });
      // Send a chat message
      client.publish({ destination: '/app/chat.send', body: JSON.stringify({ sender: 'TestUser', content: 'Hello from browser!' }) });
    }
  });
  client.activate();
}, 1000);
```

---

## Running the tests

### All tests (unit + integration)

```bash
./mvnw clean test
```

### Unit tests only

```bash
./mvnw test -pl . -Dtest="com.example.websocketschat.unit.*"
```

### Integration tests only

```bash
./mvnw test -pl . -Dtest="com.example.websocketschat.integration.*"
```

---

## Test coverage

### Unit tests (`src/test/java/.../unit/`)

| Test class | What it tests |
|------------|---------------|
| `ChatMessageTest` | Domain model: defaults, constructors, getters/setters, enum values, `toString` |
| `ChatServiceTest` | Service logic: message building, JOIN/LEAVE events, history storage, eviction at MAX_HISTORY_SIZE |

These tests run **in-process** with no Spring context, no Docker, and no external
dependencies – they exercise pure Java domain logic.

### Integration tests (`src/test/java/.../integration/`)

| Test class | What it tests |
|------------|---------------|
| `ChatHistoryIntegrationTest` | Full Spring Boot context + MockMvc: `GET /api/chat/history` returns correct JSON for empty, CHAT, JOIN, LEAVE, and mixed histories |
| `WebSocketIntegrationTest` | Real embedded server on a random port: STOMP client connects, subscribes, sends messages, and verifies broadcasts arrive; two-client broadcast; history persistence |

Integration tests start the **full Spring Boot application** (including the
WebSocket STOMP infrastructure) on a random port. No external Docker containers
are required because this application has no external dependencies (no database,
no message broker).

---

## Project structure

```
src/
├── main/java/com/example/websocketschat/
│   ├── WebSocketsChatApplication.java   # @SpringBootApplication entry point
│   ├── config/
│   │   ├── WebSocketConfig.java         # STOMP + SockJS endpoint configuration
│   │   └── WebSocketChannelInterceptor.java  # Inbound channel interceptor (logs CONNECT)
│   ├── controller/
│   │   ├── ChatController.java          # @MessageMapping handlers for /app/chat.*
│   │   └── HistoryController.java       # GET /api/chat/history REST endpoint
│   ├── domain/
│   │   └── ChatMessage.java             # Domain model (CHAT / JOIN / LEAVE)
│   ├── dto/
│   │   └── SendMessageRequest.java      # Inbound DTO with Bean Validation
│   ├── listener/
│   │   └── WebSocketEventListener.java  # Handles SessionDisconnectEvent
│   └── service/
│       └── ChatService.java             # Business logic + in-memory history
├── main/resources/
│   └── application.yml
└── test/java/com/example/websocketschat/
    ├── integration/
    │   ├── ChatHistoryIntegrationTest.java
    │   └── WebSocketIntegrationTest.java
    └── unit/
        ├── ChatMessageTest.java
        └── ChatServiceTest.java
```
