# Gateway Rate Limiting

A Spring Boot mini-project demonstrating **Redis-backed rate limiting at the API Gateway layer** using Spring Cloud Gateway and the `RequestRateLimiter` filter.

## What this project does

This application is an **API Gateway** built on Spring Cloud Gateway (reactive, Netty-based). Every incoming request passes through a rate limiting filter before being forwarded to a downstream service. The rate limiting logic is implemented using the **Token Bucket algorithm**, executed atomically on Redis as a Lua script.

### How the Token Bucket algorithm works

Each client gets a virtual "bucket" of tokens stored in Redis:

- **replenishRate** — tokens added to the bucket every second (the sustained allowed request rate).
- **burstCapacity** — maximum tokens the bucket can hold (allows short bursts above the sustained rate).

Each request consumes one token. When the bucket empties, the gateway returns `HTTP 429 Too Many Requests` without ever forwarding the request to the downstream service.

### Routes and rate limits

| Route | Path pattern | Replenish rate | Burst capacity | Key resolver |
|---|---|---|---|---|
| `product-route` | `/api/products/**` | 5 req/s | 10 | IP Address |
| `order-route` | `/api/orders/**` | 3 req/s | 5 | IP Address |
| `user-route` | `/api/users/**` | 10 req/s | 20 | IP Address |

### Key resolvers

Two key resolvers are configured (in `RateLimiterConfig.java`):

- **`ipKeyResolver`** (default/primary) — identifies clients by their remote IP address. Each IP gets its own Token Bucket in Redis.
- **`apiKeyResolver`** — identifies clients by the `X-API-Key` request header. Falls back to `"anonymous"` if the header is absent.

### Why Redis?

Storing Token Bucket state in Redis allows **multiple gateway instances** to share the same counters. Without Redis, each gateway pod would have independent counters, and a client could bypass the limit by distributing requests across pods.

---

## Requirements

- **Java 21** or higher
- **Maven 3.9+** (or use the included Maven Wrapper: `./mvnw`)
- **Docker** (required for running the application and for integration tests)
- **Docker Compose** (included with Docker Desktop)

---

## Running with Docker Compose

The entire application stack runs in Docker. The stack includes:

1. **Redis** (`redis:7-alpine`) — stores the Token Bucket state.
2. **product-service** (`wiremock/wiremock:3.9.2`) — stub downstream product API.
3. **order-service** (`wiremock/wiremock:3.9.2`) — stub downstream order API.
4. **user-service** (`wiremock/wiremock:3.9.2`) — stub downstream user API.
5. **api-gateway** (built from `Dockerfile`) — the Spring Cloud Gateway with rate limiting.

### Start the stack

```bash
# Build the gateway image and start all services
docker compose up --build

# Or run in detached (background) mode
docker compose up --build -d
```

### Stop the stack

```bash
docker compose down
```

### View logs

```bash
# Follow logs from all services
docker compose logs -f

# Follow logs only from the gateway (to see rate limit events)
docker compose logs -f api-gateway
```

---

## Available endpoints

Once the stack is running (`docker compose up --build`):

| Endpoint | Description |
|---|---|
| `GET http://localhost:8080/api/products` | Products (via gateway, rate limited 5 req/s) |
| `GET http://localhost:8080/api/orders` | Orders (via gateway, rate limited 3 req/s) |
| `GET http://localhost:8080/api/users` | Users (via gateway, rate limited 10 req/s) |
| `GET http://localhost:8080/gateway/status` | Gateway operational status |
| `GET http://localhost:8080/gateway/rate-limits` | Rate limit configuration per route |
| `GET http://localhost:8080/actuator/health` | Spring Boot Actuator health (includes Redis) |
| `GET http://localhost:8080/actuator/gateway/routes` | All configured routes (Actuator) |

---

## curl examples

### Normal requests (within rate limit)

```bash
# Get product list — rate limited to 5 req/s (burst 10)
curl -i http://localhost:8080/api/products

# Get order list — rate limited to 3 req/s (burst 5)
curl -i http://localhost:8080/api/orders

# Get user list — rate limited to 10 req/s (burst 20)
curl -i http://localhost:8080/api/users
```

### Inspect rate limit response headers

```bash
curl -si http://localhost:8080/api/products | grep -i "x-ratelimit"
# X-RateLimit-Remaining: 9
# X-RateLimit-Burst-Capacity: 10
# X-RateLimit-Replenish-Rate: 5
# X-RateLimit-Requested-Tokens: 1
```

### Trigger HTTP 429 (Too Many Requests)

Send requests rapidly to exceed the burst capacity of the order route (burst = 5):

```bash
for i in $(seq 1 10); do
  curl -si http://localhost:8080/api/orders | head -1
done
```

You will see `HTTP/1.1 200 OK` for the first few, then `HTTP/1.1 429 Too Many Requests` once the bucket empties.

### Use API key header (alternative key resolver)

The `apiKeyResolver` bean is available but routes use `ipKeyResolver` by default. To demonstrate the header-based key concept, you can observe the resolved key in the gateway logs:

```bash
curl -H "X-API-Key: my-client-123" http://localhost:8080/api/products
```

### Gateway info endpoints

```bash
# Gateway operational status
curl http://localhost:8080/gateway/status

# Per-route rate limit configuration
curl http://localhost:8080/gateway/rate-limits

# Spring Boot Actuator health (shows Redis connectivity)
curl http://localhost:8080/actuator/health

# All gateway routes (Actuator)
curl http://localhost:8080/actuator/gateway/routes
```

### Inspect Redis rate limit keys directly

```bash
# List all rate limit keys currently in Redis
docker exec -it redis redis-cli keys "request_rate_limiter*"

# Inspect token count for a specific IP (replace 127.0.0.1 with your IP)
docker exec -it redis redis-cli get "request_rate_limiter.{127.0.0.1}.tokens"
```

---

## Running the tests

Tests require **Docker** to be running (Testcontainers spins up Redis and WireMock containers automatically).

```bash
./mvnw clean test
```

### Test structure

| Test class | Type | What it tests |
|---|---|---|
| `RateLimitInfoTest` | Unit | `RateLimitInfo` record equality, fields, toString |
| `GatewayStatusTest` | Unit | `GatewayStatus` record equality, fields, toString |
| `RateLimiterConfigTest` | Unit | IP and API key resolver logic with mock exchanges |
| `RateLimitLoggingGlobalFilterTest` | Unit | Filter order, chain delegation, 429 passthrough |
| `GatewayRateLimitingIntegrationTest` | Integration | Full gateway with real Redis + WireMock containers |

### Integration test containers

The integration test (`GatewayRateLimitingIntegrationTest`) uses Testcontainers to start:

- **Redis** (`redis:7-alpine`) — real Redis for the rate limiter.
- **3× WireMock** (`wiremock/wiremock:3.9.2`) — real HTTP stubs for downstream services.

The Spring context is started pointing at these containers via `@DynamicPropertySource`.

---

## Project structure

```
04-gateway-rate-limiting/
├── src/
│   ├── main/
│   │   ├── java/com/example/gatewaylimiting/
│   │   │   ├── GatewayRateLimitingApplication.java   # Spring Boot entry point
│   │   │   ├── config/
│   │   │   │   ├── RateLimiterConfig.java            # KeyResolver beans (IP + API key)
│   │   │   │   └── GatewayRoutesConfig.java          # Route definitions with rate limits
│   │   │   ├── filter/
│   │   │   │   └── RateLimitLoggingGlobalFilter.java # Global filter: logs all requests + 429s
│   │   │   ├── controller/
│   │   │   │   └── GatewayInfoController.java        # /gateway/status and /gateway/rate-limits
│   │   │   └── model/
│   │   │       ├── RateLimitInfo.java                # Per-route rate limit config record
│   │   │       └── GatewayStatus.java                # Gateway status record
│   │   └── resources/
│   │       └── application.yml                       # Main configuration
│   └── test/
│       ├── java/com/example/gatewaylimiting/
│       │   ├── config/RateLimiterConfigTest.java      # Unit tests for key resolvers
│       │   ├── filter/RateLimitLoggingGlobalFilterTest.java # Unit tests for global filter
│       │   ├── model/
│       │   │   ├── RateLimitInfoTest.java             # Unit tests for RateLimitInfo record
│       │   │   └── GatewayStatusTest.java             # Unit tests for GatewayStatus record
│       │   └── integration/
│       │       └── GatewayRateLimitingIntegrationTest.java # Full integration tests
│       └── resources/
│           ├── application.yml                        # Test-specific config (port 0, etc.)
│           ├── docker-java.properties                 # Docker API v1.44 (Docker Desktop 29+)
│           ├── testcontainers.properties              # Testcontainers Docker API config
│           └── wiremock/
│               ├── product/mappings/products-stub.json
│               ├── order/mappings/orders-stub.json
│               └── user/mappings/users-stub.json
├── docker/
│   └── wiremock/                                      # WireMock stubs for Docker Compose
│       ├── product/mappings/products-stub.json
│       ├── order/mappings/orders-stub.json
│       └── user/mappings/users-stub.json
├── docker-compose.yml                                 # Full stack (Redis + WireMock + Gateway)
├── Dockerfile                                         # Multi-stage build for the gateway
├── pom.xml
└── README.md
```

---

## Key concepts explained

### RequestRateLimiter filter

Spring Cloud Gateway's built-in `RequestRateLimiter` filter integrates with Redis via the `RedisRateLimiter` implementation. It runs a Lua script on Redis atomically to check and decrement the token bucket. The Lua script ensures correctness even under concurrent access from multiple gateway instances.

### Token Bucket parameters

```java
new RedisRateLimiter(
    5,   // replenishRate: tokens added per second
    10   // burstCapacity: maximum tokens in the bucket
)
```

A replenishRate of 5 with burstCapacity of 10 means:
- Steady state: 5 requests per second pass through.
- Initial burst: up to 10 requests can be served immediately (if the bucket is full).
- After a burst: the bucket refills at 5 tokens/second.

### HTTP 429 response

When the bucket is empty, the gateway returns:

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Remaining: 0
X-RateLimit-Burst-Capacity: 10
X-RateLimit-Replenish-Rate: 5
X-RateLimit-Requested-Tokens: 1
```

The downstream service never receives the rejected request.
