# Zipkin Server Integration

A Spring Boot backend that exports distributed tracing spans to a **Zipkin server** so you can visualise microservice latency in the Zipkin UI.

## What this project demonstrates

- **Micrometer Tracing (Brave bridge)** – instruments every incoming HTTP request automatically. Each request gets a unique 128-bit `traceId` and a `spanId` that propagate through the call stack.
- **Multi-level span tree** – the `POST /api/orders` endpoint produces a three-level trace:
  1. Root span: `POST /api/orders` (created automatically by Spring MVC instrumentation)
  2. Child span: `order-service.createOrder` (created manually with the `Tracer` API)
  3. Grandchild span: `inventory.checkAvailability` (created inside `InventoryService`)
- **Span tags** – structured key/value annotations (e.g. `product`, `order.status`) that appear in the Zipkin UI and enable filtering.
- **B3 propagation headers** – `X-B3-TraceId`, `X-B3-SpanId`, and `X-B3-Sampled` are injected into every outgoing HTTP call so downstream services join the same trace.
- **MDC log correlation** – every log line includes `[traceId, spanId]` so you can cross-reference logs and traces.
- **Trace ID in response** – every API response includes the `traceId` so you can click directly into the Zipkin UI.

## Requirements

| Tool | Minimum version |
|------|----------------|
| Java | 21 |
| Maven (wrapper included) | 3.9+ |
| Docker | 20.10+ |
| Docker Compose | v2 (use `docker compose`, not `docker-compose`) |

## Project structure

```
src/
├── main/java/com/example/zipkinintegration/
│   ├── ZipkinIntegrationApplication.java   # Spring Boot entry point
│   ├── controller/
│   │   └── OrderController.java            # REST API (GET/POST/PATCH /api/orders)
│   ├── domain/
│   │   ├── Order.java                      # Domain object
│   │   └── OrderStatus.java                # Enum: PENDING, CONFIRMED, SHIPPED, …
│   ├── dto/
│   │   ├── CreateOrderRequest.java         # Validated request body
│   │   └── OrderResponse.java              # Response body (includes traceId)
│   └── service/
│       ├── OrderService.java               # Business logic + child spans
│       └── InventoryService.java           # Stock check + grandchild spans
├── main/resources/
│   └── application.yml                     # Zipkin endpoint, sampling, logging
└── test/
    ├── java/com/example/zipkinintegration/
    │   ├── unit/
    │   │   ├── InventoryServiceTest.java    # JUnit 5 unit tests (no Docker)
    │   │   └── OrderServiceTest.java        # JUnit 5 unit tests (no Docker)
    │   └── integration/
    │       └── OrderIntegrationTest.java    # Testcontainers + real Zipkin
    └── resources/
        ├── application-test.yml
        ├── docker-java.properties
        └── testcontainers.properties
```

## Running with Docker Compose

Docker Compose starts both the Spring Boot application and the Zipkin server. This is the recommended way to run the full project.

```bash
# Build the image and start all services
docker compose up --build

# Run in the background
docker compose up --build -d

# Stop and remove containers
docker compose down
```

Once running:
- **API base URL**: `http://localhost:8080`
- **Zipkin UI**: `http://localhost:9411/zipkin/`

## API usage (curl examples)

### Create an order

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"product": "laptop-pro-15", "quantity": 2}' | jq .
```

Expected response (`201 Created`):
```json
{
  "id": 1,
  "product": "laptop-pro-15",
  "quantity": 2,
  "status": "CONFIRMED",
  "traceId": "6b221d4c3e4e7a1f8c9d3b2a1e4f5c6d"
}
```

> Copy the `traceId` and open `http://localhost:9411/zipkin/traces/<traceId>` to see the full trace tree.

### Create an out-of-stock order

Products whose names start with `unavailable` are always rejected by the inventory service:

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"product": "unavailable-item-xyz", "quantity": 1}' | jq .
```

Expected response (`201 Created`, status `CANCELLED`):
```json
{
  "id": 2,
  "product": "unavailable-item-xyz",
  "quantity": 1,
  "status": "CANCELLED",
  "traceId": "..."
}
```

### List all orders

```bash
curl -s http://localhost:8080/api/orders | jq .
```

### Get a single order by ID

```bash
curl -s http://localhost:8080/api/orders/1 | jq .
```

Returns `404 Not Found` if the ID does not exist.

### Update order status

Valid status values: `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`

```bash
curl -s -X PATCH "http://localhost:8080/api/orders/1/status?status=SHIPPED" | jq .
```

### Validation errors

```bash
# Blank product name → 400 Bad Request
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"product": "", "quantity": 1}' | jq .

# Zero quantity → 400 Bad Request
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"product": "widget", "quantity": 0}' | jq .
```

## Viewing traces in Zipkin

1. Open `http://localhost:9411/zipkin/` in your browser.
2. Click **"RUN QUERY"** to list recent traces.
3. Click any trace to open the waterfall diagram.
4. You will see the three-level span tree for `POST /api/orders`:
   - `post /api/orders` (root – Spring MVC)
   - `order-service.createOrder` (child – `OrderService`)
   - `inventory.checkAvailability` (grandchild – `InventoryService`)
5. Click any span to see its tags (e.g. `product`, `order.status`, `available`).

You can also navigate directly to a trace using the `traceId` from the API response:

```
http://localhost:9411/zipkin/traces/<traceId>
```

## Log correlation

Every log line produced by the application includes the current `traceId` and `spanId`:

```
INFO [zipkin-integration,6b221d4c3e4e7a1f,8c9d3b2a1e4f5c6d] Creating order for product='laptop-pro-15', quantity=2
```

Cross-reference the `traceId` between your logs and the Zipkin UI to pinpoint exactly which log lines belong to a given request.

## Running tests

### All tests (unit + integration)

The integration tests automatically start a Zipkin container using Testcontainers. **Docker must be running** before executing the integration tests.

```bash
./mvnw clean test
```

### Unit tests only (no Docker required)

```bash
./mvnw test -Dtest="com.example.zipkinintegration.unit.*"
```

### Integration tests only

```bash
./mvnw test -Dtest="com.example.zipkinintegration.integration.*"
```

## Test overview

### Unit tests (`src/test/java/.../unit/`)

Run in milliseconds with no external dependencies. The `Tracer` and `InventoryService` collaborators are mocked with Mockito.

| Class | What is tested |
|-------|----------------|
| `InventoryServiceTest` | Availability logic (happy path, out-of-stock), span lifecycle (span.end() always called), span tags |
| `OrderServiceTest` | Order creation (CONFIRMED/CANCELLED), getAllOrders, getOrderById, updateOrderStatus, getCurrentTraceId |

### Integration tests (`src/test/java/.../integration/`)

Start a real Zipkin Docker container via Testcontainers and exercise the full Spring MVC stack with MockMvc.

| Test method | What is verified |
|-------------|-----------------|
| `createOrder_shouldReturn201_forValidRequest` | 201 + correct order fields |
| `createOrder_shouldIncludeTraceId` | traceId present in response |
| `createOrder_shouldReturnCancelled_forUnavailableProduct` | CANCELLED status |
| `createOrder_shouldReturn400_forBlankProduct` | Bean Validation rejection |
| `createOrder_shouldReturn400_forZeroQuantity` | Bean Validation rejection |
| `createOrder_shouldReturn400_forNegativeQuantity` | Bean Validation rejection |
| `getAllOrders_shouldReturn200` | 200 + JSON array |
| `getAllOrders_shouldContainCreatedOrder` | List grows after insert |
| `getOrderById_shouldReturn200_forExistingOrder` | 200 + correct fields |
| `getOrderById_shouldReturn404_forNonExistentId` | 404 for unknown ID |
| `updateStatus_shouldReturn200_withUpdatedStatus` | Status updated to SHIPPED |
| `updateStatus_shouldReturn404_forNonExistentOrder` | 404 for unknown ID |
| `getOrderById_shouldIncludeTraceId` | traceId non-blank in GET response |
| `zipkinContainer_shouldBeHealthy` | Zipkin container started successfully |

## Key dependencies

| Dependency | Purpose |
|-----------|---------|
| `spring-boot-starter-web` | REST controllers, Jackson serialisation |
| `spring-boot-starter-actuator` | `/actuator/health`, Micrometer meter registry |
| `micrometer-tracing-bridge-brave` | Bridges Micrometer's tracing abstraction to Brave/Zipkin |
| `zipkin-reporter-brave` | Serialises spans and sends them to the Zipkin HTTP collector |
| `spring-boot-starter-validation` | Bean Validation on request DTOs |
| `testcontainers` | Starts a real Zipkin container for integration tests |
