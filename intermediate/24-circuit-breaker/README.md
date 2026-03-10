# Circuit Breaker

A Spring Boot 3 application demonstrating the **Circuit Breaker** fault-tolerance
pattern using **Resilience4j**. The application exposes a product catalog API that
calls an external upstream inventory service and protects itself against failures
using a circuit breaker, automatic retry, and fallback responses.

---

## What is a Circuit Breaker?

A circuit breaker is a stability pattern that prevents cascading failures across
distributed systems. It wraps a potentially failing operation (e.g., an HTTP call
to an upstream service) and tracks the outcome of each call in a sliding window.

**State machine:**

```
         failures ≥ threshold
CLOSED ──────────────────────► OPEN
  ▲                               │
  │  probe calls succeed          │ wait-duration elapsed
  │                               ▼
  └──────────────────────── HALF_OPEN
         probe calls fail → OPEN
```

| State | Behaviour |
|---|---|
| **CLOSED** | All calls go through to the upstream service. Failures are counted. |
| **OPEN** | All calls are **immediately rejected** and the fallback is returned. No upstream calls are made. |
| **HALF_OPEN** | A limited number of probe calls are allowed through. If they succeed, the circuit closes; if they fail, it re-opens. |

---

## Requirements

- **Java 21+**
- **Maven 3.9+** (or use the included Maven Wrapper `./mvnw`)
- **Docker** (for running the app via Docker Compose)

---

## Project Structure

```
src/
├── main/java/com/example/circuitbreaker/
│   ├── CircuitBreakerApplication.java     # Spring Boot entry point
│   ├── client/
│   │   └── InventoryClient.java           # RestTemplate-based HTTP client (the integration boundary)
│   ├── config/
│   │   └── AppConfig.java                 # RestTemplate bean with timeouts
│   ├── controller/
│   │   ├── ProductController.java         # GET /api/products, GET /api/products/{id}
│   │   └── CircuitBreakerStatusController.java # GET /api/circuit-breaker/status
│   ├── domain/
│   │   ├── Product.java                   # Immutable product record
│   │   └── CircuitBreakerStatus.java      # CB runtime snapshot record
│   ├── exception/
│   │   └── GlobalExceptionHandler.java    # @RestControllerAdvice (RFC 7807 errors)
│   └── service/
│       ├── ProductService.java            # @CircuitBreaker + @Retry + fallback methods
│       └── CircuitBreakerMonitorService.java # Reads Resilience4j registry
└── test/java/com/example/circuitbreaker/
    ├── ProductControllerIntegrationTest.java  # Full integration tests (WireMock)
    ├── domain/
    │   └── ProductDomainTest.java             # Unit tests for Product record
    └── service/
        └── ProductServiceTest.java            # Unit tests for ProductService
```

---

## Running Locally (without Docker)

```bash
./mvnw spring-boot:run
```

The application starts on **http://localhost:8080**.

By default the `inventory.base-url` property is `http://localhost:9090`. To point
it at a real or mocked upstream service, override the environment variable:

```bash
INVENTORY_BASE_URL=http://my-inventory-service:8081 ./mvnw spring-boot:run
```

---

## Running with Docker Compose

Build and start the application container:

```bash
docker compose up --build
```

Stop and remove containers:

```bash
docker compose down
```

Override the upstream inventory URL:

```bash
INVENTORY_BASE_URL=http://my-real-inventory:8081 docker compose up --build
```

The application will be available at **http://localhost:8080**.

---

## API Endpoints

### Products

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/products` | List all products (fallback: empty array `[]`) |
| `GET` | `/api/products/{id}` | Get product by ID (fallback: placeholder product) |

### Circuit Breaker Status

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/circuit-breaker/status` | All circuit breaker states |
| `GET` | `/api/circuit-breaker/status/{name}` | Single circuit breaker state |

### Actuator (Spring Boot)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/actuator/health` | Overall health including CB state |
| `GET` | `/actuator/circuitbreakers` | Detailed Resilience4j metrics |
| `GET` | `/actuator/metrics` | Micrometer metrics |
| `GET` | `/actuator/prometheus` | Prometheus-format metrics scrape |

---

## curl Examples

### 1. Fetch all products (healthy upstream)

```bash
curl -s http://localhost:8080/api/products | jq .
```

Expected response (when upstream is reachable):
```json
[
  { "id": 1, "name": "Laptop Pro", "price": 1299.99, "available": true }
]
```

Fallback response (when circuit is OPEN or upstream is down):
```json
[]
```

---

### 2. Fetch a single product

```bash
curl -s http://localhost:8080/api/products/1 | jq .
```

Fallback response (when circuit is OPEN):
```json
{
  "id": 1,
  "name": "Product Unavailable",
  "description": "The product catalog is temporarily unavailable. Please try again later.",
  "price": 0,
  "available": false
}
```

---

### 3. Inspect circuit breaker state

```bash
curl -s http://localhost:8080/api/circuit-breaker/status | jq .
```

Example response:
```json
[
  {
    "name": "inventoryService",
    "state": "CLOSED",
    "failureRate": -1.0,
    "slowCallRate": -1.0,
    "bufferedCalls": 0,
    "failedCalls": 0,
    "successfulCalls": 0,
    "notPermittedCalls": 0
  }
]
```

---

### 4. Check a specific circuit breaker

```bash
curl -s http://localhost:8080/api/circuit-breaker/status/inventoryService | jq .
```

---

### 5. Observe circuit breaker opening (simulate with curl loop)

When the upstream inventory service is down (or returning 5xx), trigger enough
failures to open the circuit. With the default config (`sliding-window-size=5`,
`failure-rate-threshold=50%`, `minimum-number-of-calls=3`):

```bash
# Trigger 3+ consecutive failures (each retries 3 times internally)
for i in {1..5}; do
  curl -s http://localhost:8080/api/products/$i | jq .name
done
```

After enough failures, the circuit opens. Check the state:
```bash
curl -s http://localhost:8080/api/circuit-breaker/status/inventoryService | jq .state
# "OPEN"
```

---

### 6. Spring Boot Actuator health

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

Includes circuit breaker health status:
```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "inventoryService": {
          "details": { "state": "CLOSED", "failureRate": "-1.0%" }
        }
      }
    }
  }
}
```

---

## Resilience4j Configuration (application.yml)

| Property | Default | Description |
|---|---|---|
| `sliding-window-type` | `COUNT_BASED` | Window measured in number of calls |
| `sliding-window-size` | `5` | Number of calls in the window |
| `minimum-number-of-calls` | `3` | Minimum calls before rates are computed |
| `failure-rate-threshold` | `50` | % failures to open the circuit |
| `slow-call-rate-threshold` | `80` | % slow calls to open the circuit |
| `slow-call-duration-threshold` | `2s` | Calls longer than this are "slow" |
| `wait-duration-in-open-state` | `10s` | Time the circuit stays OPEN |
| `permitted-number-of-calls-in-half-open-state` | `2` | Probe calls in HALF_OPEN |
| `max-attempts` (retry) | `3` | Total attempts including first call |
| `wait-duration` (retry) | `500ms` | Wait between retry attempts |

---

## Running the Tests

```bash
./mvnw clean test
```

### Test suite overview

| Test class | Type | What it tests |
|---|---|---|
| `ProductDomainTest` | Unit | `Product` record: constructors, equality, toString, sentinel values |
| `ProductServiceTest` | Unit | `ProductService`: happy paths, fallback methods, constant names |
| `ProductControllerIntegrationTest` | Integration | Full stack with WireMock simulating healthy and failing upstream |

### Integration test approach

Integration tests start the **full Spring Boot application context** (including
Resilience4j AOP proxies) and use an in-process **WireMock** server to simulate
the upstream inventory API. `@DynamicPropertySource` overrides `inventory.base-url`
to point at WireMock before the context starts.

**Testcontainers** is configured in the project (classpath + Docker API version
fix files) to demonstrate the full Testcontainers setup pattern. WireMock is run
in-process (not as a Docker container) because:

- It starts in milliseconds with no Docker overhead.
- Stub setup is a plain Java method call (no HTTP).
- The WireMock standalone JAR already ships the embedded server.

---

## Docker Compose — Dockerfile Details

The `Dockerfile` uses a **multi-stage build**:

1. **Build stage** (`eclipse-temurin:21-jdk-alpine`) — downloads dependencies,
   compiles, and packages the fat JAR.
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`) — copies only the JAR,
   resulting in a minimal image.

The `docker-compose.yml` runs only the application container. There is no
database or message broker — this project's only external dependency is the
upstream inventory service configured via `INVENTORY_BASE_URL`.
