# Distributed Tracing Sleuth

A Spring Boot mini-project demonstrating **distributed tracing** with
**Micrometer Tracing** (the Spring Boot 3 successor to Spring Cloud Sleuth).
The application generates trace IDs and span IDs for every incoming HTTP request,
propagates them across simulated inter-service HTTP calls using a Feign client, and
exports the complete multi-span traces to a **Zipkin** server.

---

## What This Project Demonstrates

| Concept | Where to look |
|---|---|
| Automatic HTTP request instrumentation | `OrderController`, `ProductController` |
| Manual child span creation | `OrderService`, `InventoryService` |
| Span tagging (custom key-value pairs) | `ProductService`, `OrderService`, `InventoryService` |
| Span events (timestamped annotations) | `ProductService`, `InventoryService` |
| Cross-service trace context propagation via Feign | `InventoryClient` → `InventoryController` |
| MDC integration (traceId/spanId in log lines) | `application.yml` logging pattern |
| Reading current trace context from application code | `TraceInfoController` |
| Async span export to Zipkin | `application.yml` → `management.zipkin.tracing.endpoint` |

### Span hierarchy for `POST /orders`

```
[POST /orders]              traceId=abc  spanId=111   ← root, auto by Spring MVC
  └─ [process-order]        traceId=abc  spanId=222   ← child, manual in OrderService
       └─ [GET /inventory]  traceId=abc  spanId=333   ← grandchild, auto by Feign+Brave
            └─ [inventory-check] traceId=abc spanId=444 ← great-grandchild, manual in InventoryService
```

All four spans share `traceId=abc`. In Zipkin this appears as a 4-level waterfall.

---

## Requirements

- **Java 21** or higher
- **Maven** (via the included Maven Wrapper — no system Maven needed)
- **Docker** and **Docker Compose** (for running the full stack or integration tests)

---

## Running with Docker Compose (recommended)

This is the easiest way to run the full stack — the Spring Boot application and
Zipkin server are both started with a single command.

```bash
# Build the application image and start both services
docker compose up --build

# Run in the background (detached mode)
docker compose up --build -d

# Stop and remove containers
docker compose down

# Follow logs from all services
docker compose logs -f

# Follow logs only from the application
docker compose logs -f app
```

After startup, the following URLs are available:

| URL | Description |
|---|---|
| `http://localhost:8080/actuator/health` | Spring Boot health check |
| `http://localhost:8080/products` | List all products |
| `http://localhost:8080/products/{id}` | Get a product by ID |
| `http://localhost:8080/inventory/{productId}` | Check inventory stock |
| `http://localhost:8080/orders` | Place an order (POST) |
| `http://localhost:8080/trace/current` | Inspect current trace context |
| `http://localhost:9411` | Zipkin UI |

---

## Running Locally (without Docker)

If you have Java 21+ installed and want to run without Docker, you need a local
Zipkin instance. The quickest way is to run Zipkin in Docker while keeping the
application on your host:

```bash
# Start Zipkin only
docker run -d -p 9411:9411 openzipkin/zipkin:3

# Run the application locally
./mvnw spring-boot:run
```

The application connects to `http://localhost:9411` by default.

---

## curl Examples

### Inspect current trace context

```bash
curl -s http://localhost:8080/trace/current | jq .
```

**Response:**
```json
{
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId":  "00f067aa0ba902b7",
  "parentId": "none",
  "sampled":  true,
  "service":  "distributed-tracing-sleuth"
}
```

Copy the `traceId` and search for it in the Zipkin UI at `http://localhost:9411`.

---

### List all products

```bash
curl -s http://localhost:8080/products | jq .
```

**Response (excerpt):**
```json
[
  {
    "id": "PROD-001",
    "name": "Laptop Pro 15",
    "price": 1299.99,
    "category": "Electronics",
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
    "spanId":  "00f067aa0ba902b7"
  }
]
```

---

### Get a single product (observe span tags in Zipkin)

```bash
curl -s http://localhost:8080/products/PROD-001 | jq .
```

The `ProductService` adds these span tags (visible in Zipkin's span detail):
- `product.id = PROD-001`
- `product.name = Laptop Pro 15`
- `product.category = Electronics`
- `product.found = true`

```bash
# Non-existent product — tags product.found=false
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/products/PROD-UNKNOWN
# 404
```

---

### Check inventory stock

```bash
curl -s http://localhost:8080/inventory/PROD-001 | jq .
```

**Response:**
```json
{
  "productId": "PROD-001",
  "available": 50,
  "reserved":  5,
  "traceId":   "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId":    "a3ce929d0e0e4736"
}
```

---

### Place an order (multi-span trace)

This is the most interesting endpoint from a tracing perspective. It generates a
4-span waterfall in Zipkin.

```bash
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId":   "ORD-001",
    "productId": "PROD-001",
    "quantity":  2,
    "customer":  "Alice"
  }' | jq .
```

**Response (order accepted):**
```json
{
  "orderId":  "ORD-001",
  "status":   "ACCEPTED",
  "traceId":  "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId":   "b7a45f3b8cd12e90",
  "message":  "Reserved 2 unit(s) from stock."
}
```

Copy the `traceId` and open `http://localhost:9411` → search by trace ID to see
the complete 4-span waterfall.

**Order with insufficient stock (backorder):**
```bash
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId":   "ORD-002",
    "productId": "PROD-003",
    "quantity":  100,
    "customer":  "Bob"
  }' | jq .
```

**Response (backorder):**
```json
{
  "orderId":  "ORD-002",
  "status":   "BACKORDER",
  "traceId":  "...",
  "spanId":   "...",
  "message":  "Insufficient stock. Available: 30. Order placed on backorder."
}
```

**Invalid order (validation error):**
```bash
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-BAD",
    "quantity": 0
  }' -w "\nHTTP %{http_code}\n"
# HTTP 400
```

---

### Propagate an existing trace context (B3 headers)

You can inject your own trace ID to continue an external trace:

```bash
curl -s http://localhost:8080/products/PROD-002 \
  -H "X-B3-TraceId: aabbccddeeff0011" \
  -H "X-B3-SpanId:  aabbccddeeff0022" \
  -H "X-B3-Sampled: 1" | jq .traceId
# "aabbccddeeff0011"
```

---

## How Tracing Works

### 1. Automatic instrumentation (Spring MVC)

Spring Boot auto-configures Micrometer Tracing via `micrometer-tracing-bridge-brave`.
Every inbound HTTP request is automatically wrapped in a root span. No code changes
are needed for this — it is enabled by the presence of the dependency on the classpath.

### 2. Manual span creation

```java
Span mySpan = tracer.nextSpan().name("my-operation");
try (Tracer.SpanInScope ws = tracer.withSpan(mySpan.start())) {
    mySpan.tag("key", "value");
    mySpan.event("something.happened");
    // ... do work ...
} finally {
    mySpan.end(); // ALWAYS end in finally to avoid span leaks
}
```

### 3. Context propagation via Feign

Brave's Feign integration automatically injects B3 headers into every outbound
Feign request:
```
X-B3-TraceId:      <current trace ID>
X-B3-SpanId:       <new child span ID>
X-B3-ParentSpanId: <current span ID>
X-B3-Sampled:      1
```

The receiving controller extracts these headers and continues the same trace.

### 4. MDC integration

Every log line emitted during a traced request automatically includes the trace
context via SLF4J MDC:
```
INFO [distributed-tracing-sleuth,4bf92f3577b34da6a,00f067aa0ba902b7] - Processing order...
                              ─────────────────── ────────────────
                              traceId              spanId
```

---

## Running the Tests

### Unit tests only (no Docker required)

```bash
./mvnw test -pl . -Dgroups='!integration'
```

Or run all tests (unit + integration — Docker required for Testcontainers):

```bash
./mvnw clean test
```

### What the tests cover

**Unit tests** (no Spring context, no Docker):
- `OrderTest` — Bean Validation constraint tests for the `Order` record
- `ProductServiceTest` — verifies product lookup, span tag application, traceId embedding
- `InventoryServiceTest` — verifies stock check, manual child span creation/ending, span tags
- `OrderServiceTest` — verifies order processing logic, ACCEPTED/BACKORDER status, span tags

**Integration tests** (full Spring context + Testcontainers Zipkin):
- `DistributedTracingIntegrationTest` — end-to-end HTTP tests via `MockMvc`/`WebTestClient`:
  - Actuator health UP
  - Product list and detail endpoints
  - Inventory endpoint
  - Order placement (201 Created, 400 Bad Request for invalid input)
  - `/trace/current` returns a real traceId
  - **Zipkin export verification** — after a traced request, queries the Zipkin API to
    confirm the service name appears (proves spans were actually exported)

---

## Project Structure

```
05-distributed-tracing-sleuth/
├── src/
│   ├── main/
│   │   ├── java/com/example/tracing/
│   │   │   ├── DistributedTracingApplication.java   # @SpringBootApplication + @EnableFeignClients
│   │   │   ├── client/
│   │   │   │   └── InventoryClient.java             # Feign client — propagates trace context
│   │   │   ├── controller/
│   │   │   │   ├── InventoryController.java         # GET /inventory/{productId}
│   │   │   │   ├── OrderController.java             # POST /orders
│   │   │   │   ├── ProductController.java           # GET /products, GET /products/{id}
│   │   │   │   └── TraceInfoController.java         # GET /trace/current
│   │   │   ├── model/
│   │   │   │   ├── InventoryResponse.java           # Inventory stock record
│   │   │   │   ├── Order.java                       # Order request record (with validation)
│   │   │   │   ├── OrderResult.java                 # Order response record (with traceId)
│   │   │   │   ├── Product.java                     # Product record (with traceId)
│   │   │   │   └── TraceInfo.java                   # Current trace context record
│   │   │   └── service/
│   │   │       ├── InventoryService.java            # Manual child span + span tags
│   │   │       ├── OrderService.java                # Manual child span + Feign call
│   │   │       └── ProductService.java              # Span tag enrichment
│   │   └── resources/
│   │       └── application.yml                      # Micrometer/Zipkin/logging config
│   └── test/
│       ├── java/com/example/tracing/
│       │   ├── integration/
│       │   │   └── DistributedTracingIntegrationTest.java  # Testcontainers + Zipkin
│       │   ├── model/
│       │   │   └── OrderTest.java                   # Validation unit tests
│       │   └── service/
│       │       ├── InventoryServiceTest.java        # Mocked Tracer unit tests
│       │       ├── OrderServiceTest.java            # Mocked Tracer + Feign unit tests
│       │       └── ProductServiceTest.java          # Mocked Tracer unit tests
│       └── resources/
│           ├── application.yml                      # Test overrides (port 0, Zipkin placeholder)
│           ├── docker-java.properties               # Docker API v1.44 for Docker Desktop 29+
│           └── testcontainers.properties            # Testcontainers Docker API config
├── .gitignore
├── Dockerfile                                       # Multi-stage build (JDK build → JRE runtime)
├── docker-compose.yml                               # app + zipkin stack
├── mvnw / mvnw.cmd                                  # Maven Wrapper
├── pom.xml                                          # Spring Boot 3.4.3, Micrometer Tracing, Feign
└── README.md
```

---

## Docker Compose Details

The `docker-compose.yml` file defines two services:

| Service | Image | Port | Description |
|---|---|---|---|
| `zipkin` | `openzipkin/zipkin:3` | 9411 | Zipkin tracing backend (UI + API) |
| `app` | Built from `Dockerfile` | 8080 | Spring Boot application |

The `app` service depends on `zipkin` being healthy before starting. Inside the
Docker network, the application sends spans to `http://zipkin:9411/api/v2/spans`
(resolved by Docker Compose DNS).

**Viewing traces in Zipkin:**

1. Start the stack: `docker compose up --build`
2. Make some requests (e.g., `curl http://localhost:8080/products/PROD-001`)
3. Open `http://localhost:9411`
4. Click **"Run Query"** to see recent traces
5. Click a trace to see the waterfall of spans

---

## Key Dependencies

| Dependency | Purpose |
|---|---|
| `micrometer-tracing-bridge-brave` | Wires Micrometer Tracing API to Brave/Zipkin backend |
| `zipkin-reporter-brave` | Exports completed spans to Zipkin over HTTP |
| `spring-cloud-starter-openfeign` | Declarative HTTP client with automatic trace propagation |
| `spring-boot-starter-actuator` | `/actuator/health` and `/actuator/metrics` endpoints |
| `testcontainers` | Starts a real Zipkin Docker container for integration tests |
