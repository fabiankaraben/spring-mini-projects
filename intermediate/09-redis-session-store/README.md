# Redis Session Store

A Spring Boot backend that stores HTTP sessions in **Redis** using **Spring Session**.

Instead of keeping session data in the JVM heap (Tomcat's default), every session
attribute is transparently written to Redis and read back on subsequent requests —
even across server restarts and multiple application instances.

The project exposes a shopping-cart REST API to demonstrate session persistence: items
added to a cart in one request are still there in the next, because they live in Redis
rather than in memory.

---

## Table of Contents

- [Key Concepts](#key-concepts)
- [Requirements](#requirements)
- [Project Structure](#project-structure)
- [Running with Docker Compose (recommended)](#running-with-docker-compose-recommended)
- [Running Locally (without Docker)](#running-locally-without-docker)
- [API Reference with curl Examples](#api-reference-with-curl-examples)
- [Inspecting Sessions in Redis](#inspecting-sessions-in-redis)
- [Running the Tests](#running-the-tests)

---

## Key Concepts

| Concept | Description |
|---|---|
| **Spring Session** | Replaces the servlet container's in-memory session store with a pluggable backend (Redis here). |
| **SessionRepositoryFilter** | A servlet filter registered automatically by Spring Session that intercepts every request and delegates session reads/writes to Redis. |
| **SESSION cookie** | Spring Session replaces the default `JSESSIONID` cookie with `SESSION`. The value is the Redis key suffix. |
| **Indexed sessions** | `@EnableRedisIndexedHttpSession` stores extra Redis keys so sessions can be looked up by principal or attribute. |
| **JSON serialisation** | Session attributes are stored as JSON (not Java serialisation) for human readability in Redis. |
| **Automatic TTL** | Redis expires sessions after the configured `maxInactiveIntervalInSeconds` (30 minutes by default). |

---

## Requirements

- **Java 21** or later
- **Docker** and **Docker Compose** (for the recommended Docker workflow)
- **Redis 7+** (only needed for local development without Docker)
- **Maven 3.9+** (included via the Maven Wrapper — no separate installation needed)

---

## Project Structure

```
09-redis-session-store/
├── src/
│   ├── main/
│   │   ├── java/com/example/redissessionstore/
│   │   │   ├── RedisSessionStoreApplication.java   # @SpringBootApplication entry point
│   │   │   ├── config/
│   │   │   │   └── SessionConfig.java              # @EnableRedisIndexedHttpSession + JSON serialiser
│   │   │   ├── controller/
│   │   │   │   └── CartController.java             # REST endpoints for the shopping cart
│   │   │   ├── domain/
│   │   │   │   └── CartItem.java                   # Domain model stored in the session
│   │   │   ├── dto/
│   │   │   │   └── CartItemRequest.java             # Validated request DTO
│   │   │   └── service/
│   │   │       └── CartService.java                # Business logic (reads/writes HttpSession)
│   │   └── resources/
│   │       └── application.yml                     # Redis, session, and logging config
│   └── test/
│       ├── java/com/example/redissessionstore/
│       │   ├── CartIntegrationTest.java            # Full integration tests (Testcontainers)
│       │   ├── domain/
│       │   │   └── CartItemTest.java               # Domain unit tests
│       │   └── service/
│       │       └── CartServiceTest.java            # Service unit tests (Mockito)
│       └── resources/
│           ├── application-test.yml                # Test-profile config
│           ├── docker-java.properties              # Docker API version fix for Testcontainers
│           ├── testcontainers.properties           # Testcontainers Docker API config
│           └── mockito-extensions/
│               └── org.mockito.plugins.MockMaker   # Mockito subclass mock-maker for Java 21
├── Dockerfile                                      # Multi-stage Docker build
├── docker-compose.yml                              # Redis + Spring Boot services
├── pom.xml
└── mvnw / mvnw.cmd                                 # Maven Wrapper
```

---

## Running with Docker Compose (recommended)

Docker Compose starts both the Redis container and the Spring Boot application,
wiring them together automatically.

### Start all services

```bash
docker compose up --build
```

The `--build` flag rebuilds the application image if any source files changed.
On the first run this will also download the `redis:7-alpine` base image.

### Stop all services

```bash
docker compose down
```

### Stop and remove session data (Redis volume)

```bash
docker compose down -v
```

The `-v` flag removes the named `redis_data` volume, clearing all stored sessions.

### Check service logs

```bash
# All services
docker compose logs -f

# Only the Spring Boot app
docker compose logs -f app

# Only Redis
docker compose logs -f redis
```

Once the services are running, the API is available at **`http://localhost:8080`**.

---

## Running Locally (without Docker)

You still need Redis running locally. The easiest way is a standalone Redis container:

```bash
docker run -d -p 6379:6379 --name local-redis redis:7-alpine
```

Then run the Spring Boot application:

```bash
./mvnw spring-boot:run
```

---

## API Reference with curl Examples

All endpoints live under `/api/cart`. Session state is maintained via the `SESSION`
cookie that the server sets on the first request.

> **Tip:** pass `-c cookies.txt -b cookies.txt` to curl to automatically save and
> resend the `SESSION` cookie across multiple requests — just like a browser would.

---

### Get the current cart

```bash
curl -s -c cookies.txt -b cookies.txt \
  http://localhost:8080/api/cart | jq .
```

Response (new session, empty cart):
```json
[]
```

---

### Add an item to the cart

```bash
curl -s -c cookies.txt -b cookies.txt \
  -X POST http://localhost:8080/api/cart/items \
  -H "Content-Type: application/json" \
  -d '{"productId":"p1","productName":"Laptop Pro","price":999.99,"quantity":1}' | jq .
```

Response:
```json
[
  {
    "productId": "p1",
    "productName": "Laptop Pro",
    "price": 999.99,
    "quantity": 1
  }
]
```

---

### Add a second item

```bash
curl -s -c cookies.txt -b cookies.txt \
  -X POST http://localhost:8080/api/cart/items \
  -H "Content-Type: application/json" \
  -d '{"productId":"p2","productName":"Wireless Mouse","price":29.99,"quantity":2}' | jq .
```

---

### Add the same product again (quantity is incremented, no duplicate entry)

```bash
curl -s -c cookies.txt -b cookies.txt \
  -X POST http://localhost:8080/api/cart/items \
  -H "Content-Type: application/json" \
  -d '{"productId":"p1","productName":"Laptop Pro","price":999.99,"quantity":1}' | jq .
```

Response (quantity of `p1` is now 2):
```json
[
  {"productId": "p1", "productName": "Laptop Pro", "price": 999.99, "quantity": 2},
  {"productId": "p2", "productName": "Wireless Mouse", "price": 29.99, "quantity": 2}
]
```

---

### Get the cart total

```bash
curl -s -c cookies.txt -b cookies.txt \
  http://localhost:8080/api/cart/total | jq .
```

Response:
```json
{
  "total": 2059.96
}
```

---

### Get session metadata

```bash
curl -s -c cookies.txt -b cookies.txt \
  http://localhost:8080/api/cart/session-info | jq .
```

Response:
```json
{
  "sessionId": "b3f2a1...",
  "cartSize": 2,
  "cartTotal": 2059.96,
  "maxInactiveIntervalSeconds": 1800
}
```

---

### Remove a specific item from the cart

```bash
curl -s -c cookies.txt -b cookies.txt \
  -X DELETE http://localhost:8080/api/cart/items/p2 | jq .
```

---

### Clear the entire cart (session stays active)

```bash
curl -s -c cookies.txt -b cookies.txt \
  -X DELETE http://localhost:8080/api/cart
```

Response: `204 No Content`

---

### Invalidate the session entirely (equivalent to "log out")

```bash
curl -s -c cookies.txt -b cookies.txt \
  -X DELETE http://localhost:8080/api/cart/session
```

Response: `204 No Content`  
After this call the old `SESSION` cookie is no longer valid in Redis. The next request
with the same cookie creates a fresh session.

---

### Demonstrate session isolation (two independent clients)

Open two terminals and use different cookie files to prove each client gets its own cart:

```bash
# Terminal 1 – Client A adds a laptop
curl -s -c /tmp/client-a.txt -b /tmp/client-a.txt \
  -X POST http://localhost:8080/api/cart/items \
  -H "Content-Type: application/json" \
  -d '{"productId":"p1","productName":"Laptop","price":999.99,"quantity":1}'

# Terminal 2 – Client B adds a mouse (completely separate session)
curl -s -c /tmp/client-b.txt -b /tmp/client-b.txt \
  -X POST http://localhost:8080/api/cart/items \
  -H "Content-Type: application/json" \
  -d '{"productId":"p2","productName":"Mouse","price":29.99,"quantity":1}'

# Client A's cart only has the laptop
curl -s -c /tmp/client-a.txt -b /tmp/client-a.txt \
  http://localhost:8080/api/cart | jq .

# Client B's cart only has the mouse
curl -s -c /tmp/client-b.txt -b /tmp/client-b.txt \
  http://localhost:8080/api/cart | jq .
```

---

## Inspecting Sessions in Redis

While the application is running, connect to Redis directly to observe the stored sessions:

```bash
# Open an interactive Redis CLI (if using Docker Compose)
docker exec -it redissessionstore-redis redis-cli

# List all Spring Session keys
KEYS spring:session:*

# Inspect a specific session (replace <session-id> with an actual ID from the API)
HGETALL spring:session:sessions:<session-id>

# See the session TTL (remaining seconds)
TTL spring:session:sessions:<session-id>
```

---

## Running the Tests

The test suite has two layers:

| Layer | What it tests | Tools |
|---|---|---|
| **Unit tests** | `CartItem` domain logic, `CartService` business logic with a mocked `HttpSession` | JUnit 5, Mockito |
| **Integration tests** | Full HTTP → Spring Session → Redis round-trip | JUnit 5, Testcontainers, Spring Boot Test |

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests

```bash
./mvnw test -Dtest="CartItemTest,CartServiceTest"
```

### Run only integration tests

```bash
./mvnw test -Dtest="CartIntegrationTest"
```

> **Note:** Integration tests require Docker to be running because Testcontainers
> automatically pulls and starts a `redis:7-alpine` container for the duration of
> the test run. No manual Redis setup is needed.
