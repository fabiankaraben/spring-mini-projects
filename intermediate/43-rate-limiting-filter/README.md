# Rate Limiting Filter

A Spring Boot backend that implements a **token-bucket rate limiter** via a
Spring `HandlerInterceptor`. Per-client bucket state is stored in **Redis**,
making the limiter correct in horizontally-scaled (multi-instance) deployments.

---

## What this mini-project demonstrates

| Concept | Where to look |
|---|---|
| Token-bucket algorithm (pure domain) | `domain/TokenBucket.java` |
| Spring `HandlerInterceptor` | `interceptor/RateLimitInterceptor.java` |
| Registering an interceptor | `config/WebMvcConfig.java` |
| Externalised configuration (`@ConfigurationProperties`) | `config/RateLimitProperties.java` |
| Redis-backed state management | `service/RateLimiterService.java` |
| Standard rate-limit HTTP headers | `interceptor/RateLimitInterceptor.java` |
| Unit tests without Spring context | `test/…/domain/TokenBucketTest.java` |
| Integration tests with Testcontainers (real Redis) | `test/…/integration/RateLimitingIntegrationTest.java` |

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21 or later |
| Maven | 3.9+ (or use the included Maven Wrapper `./mvnw`) |
| Docker & Docker Compose | Required to run the full stack |
| Redis | Provided by Docker Compose (no local installation needed) |

---

## Architecture overview

```
HTTP Request
     │
     ▼
DispatcherServlet
     │
     ▼
RateLimitInterceptor (preHandle)
  ├─ resolves client IP (or X-Forwarded-For)
  ├─ calls RateLimiterService.tryConsume(clientKey)
  │      └─ reads/writes token count + timestamp in Redis
  ├─ if allowed  → sets X-RateLimit-* headers, returns true
  └─ if rejected → returns HTTP 429 with Retry-After header
     │
     ▼ (only if allowed)
ApiController
```

### Token-bucket algorithm

1. Each client starts with a bucket holding **N tokens** (configured via `app.rate-limit.capacity`).
2. Every request consumes **one token**.
3. If the bucket is empty the request is **rejected** with `HTTP 429 Too Many Requests`.
4. After `app.rate-limit.refill-period-seconds` the bucket is **fully refilled**.

### Response headers

| Header | Meaning |
|---|---|
| `X-RateLimit-Limit` | Configured bucket capacity |
| `X-RateLimit-Remaining` | Tokens remaining after this request |
| `Retry-After` | Seconds until the bucket refills (429 responses only) |

---

## Running with Docker Compose (recommended)

Docker Compose starts both Redis and the Spring Boot app.

```bash
# Build the image and start all services
docker compose up --build

# Run in the background
docker compose up --build -d

# Stop and remove containers
docker compose down
```

The application will be available at `http://localhost:8080`.

---

## Running locally (without Docker)

You still need a running Redis instance:

```bash
# Start Redis with Docker (single container, no Compose)
docker run -d -p 6379:6379 redis:7-alpine

# Build and run the Spring Boot app
./mvnw spring-boot:run
```

---

## Configuration

All rate-limit parameters live in `src/main/resources/application.yml` and can
be overridden via environment variables:

| Property | Env variable | Default | Description |
|---|---|---|---|
| `app.rate-limit.capacity` | `APP_RATE_LIMIT_CAPACITY` | `10` | Max requests per window |
| `app.rate-limit.refill-period-seconds` | `APP_RATE_LIMIT_REFILL_PERIOD_SECONDS` | `60` | Window length in seconds |
| `spring.data.redis.host` | `SPRING_DATA_REDIS_HOST` | `localhost` | Redis hostname |
| `spring.data.redis.port` | `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |

---

## curl examples

### Normal request (within rate limit)

```bash
curl -i http://localhost:8080/api/ping
```

Expected response:

```
HTTP/1.1 200 OK
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 9

{"message":"pong","timestamp":"2024-01-01T00:00:00Z"}
```

### Fetch sample data

```bash
curl -i http://localhost:8080/api/data
```

### Simulate a different client via X-Forwarded-For

```bash
curl -i -H "X-Forwarded-For: 203.0.113.42" http://localhost:8080/api/ping
```

### Exhaust the rate limit (runs 11 requests; last one returns 429)

```bash
for i in $(seq 1 11); do
  echo "Request $i:"
  curl -s -o /dev/null -w "  HTTP %{http_code}  X-RateLimit-Remaining: %header{X-RateLimit-Remaining}\n" \
    http://localhost:8080/api/ping
done
```

### Rate-limited response

When the bucket is empty you receive:

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
Retry-After: 60

{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please retry after 60 seconds."
}
```

### Health check (excluded from rate limiting)

```bash
curl -i http://localhost:8080/actuator/health
```

### Inspect Redis state directly

```bash
# List all rate-limit keys
redis-cli keys "rate_limit:*"

# Inspect a specific client bucket
redis-cli get "rate_limit:127.0.0.1:tokens"
redis-cli get "rate_limit:127.0.0.1:ts"
```

---

## Running the tests

### Unit tests only (no Docker required)

```bash
./mvnw test -Dtest="TokenBucketTest"
```

### All tests (unit + integration – Docker required for Testcontainers)

```bash
./mvnw clean test
```

The integration tests spin up a real Redis container automatically via
**Testcontainers**. Docker must be running on the host machine.

### Test coverage overview

| Test class | Type | What is tested |
|---|---|---|
| `TokenBucketTest` | Unit | Token-bucket algorithm, refill windows, isolation |
| `RateLimitingIntegrationTest` | Integration (Testcontainers) | HTTP 200/429 responses, rate-limit headers, client isolation, actuator exclusion |

---

## Project structure

```
43-rate-limiting-filter/
├── src/
│   ├── main/
│   │   ├── java/com/example/ratelimitingfilter/
│   │   │   ├── RateLimitingFilterApplication.java   # Spring Boot entry point
│   │   │   ├── config/
│   │   │   │   ├── RateLimitProperties.java          # @ConfigurationProperties
│   │   │   │   └── WebMvcConfig.java                 # Registers the interceptor
│   │   │   ├── controller/
│   │   │   │   └── ApiController.java               # Sample REST endpoints
│   │   │   ├── domain/
│   │   │   │   └── TokenBucket.java                 # Pure domain model
│   │   │   ├── interceptor/
│   │   │   │   └── RateLimitInterceptor.java        # Core rate-limiting logic
│   │   │   └── service/
│   │   │       └── RateLimiterService.java          # Redis-backed token bucket
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/com/example/ratelimitingfilter/
│       │   ├── domain/
│       │   │   └── TokenBucketTest.java             # Unit tests (no Spring)
│       │   └── integration/
│       │       └── RateLimitingIntegrationTest.java # Full stack + Testcontainers
│       └── resources/
│           ├── application-test.yml
│           ├── docker-java.properties               # Testcontainers Docker API fix
│           └── testcontainers.properties
├── .gitignore
├── Dockerfile                                       # Multi-stage Docker build
├── docker-compose.yml                               # Redis + App services
├── mvnw / mvnw.cmd                                  # Maven Wrapper scripts
├── pom.xml
└── README.md
```
