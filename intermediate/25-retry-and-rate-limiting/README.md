# Retry and Rate Limiting

A Spring Boot mini-project demonstrating two complementary **Resilience4j** fault-tolerance patterns applied to outbound HTTP calls:

- **Retry** — automatically re-invokes a failing call up to N times with configurable back-off before reporting failure.
- **RateLimiter** — limits how many calls can be made to an upstream service per time window; excess calls are rejected immediately (or queued for a short timeout).

Both patterns are applied to a `WeatherService` that calls an imaginary upstream weather API.

---

## Architecture

```
Client ──► WeatherController ──► WeatherService ──► WeatherClient ──► Upstream API
                                       │                  │
                                @RateLimiter          @Retry
                               (outermost gate)   (inner retry loop)
```

**Decorator order:** `RateLimiter → Retry → actual HTTP call`

1. **RateLimiter** checks the permit budget first. If no permit is available within `timeout-duration`, the call is rejected immediately with a fallback response.
2. **Retry** takes over if the permit is granted. On transient errors (5xx, connection refused), it re-invokes the method up to `max-attempts` times with `wait-duration` between attempts.
3. If all retries are exhausted, the **fallback** method is invoked.

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (or use the included Maven Wrapper) |
| Docker + Docker Compose | Any recent version (for running via Docker) |

---

## Project Structure

```
src/main/java/com/example/retryratelimiting/
├── RetryRateLimitingApplication.java      # Spring Boot entry point
├── client/
│   └── WeatherClient.java                 # HTTP client for the upstream weather API
├── config/
│   └── AppConfig.java                     # RestTemplate bean with timeouts
├── controller/
│   ├── WeatherController.java             # GET /api/weather?city=...
│   └── ResilienceStatusController.java    # GET /api/resilience/status/*
├── domain/
│   ├── WeatherReport.java                 # Immutable result record
│   └── ResilienceStatus.java              # Metrics snapshot record
├── exception/
│   └── GlobalExceptionHandler.java        # RFC 7807 Problem Detail error responses
└── service/
    ├── WeatherService.java                # @Retry + @RateLimiter applied here
    └── ResilienceMonitorService.java      # Queries Resilience4j registries

src/main/resources/
└── application.yml                        # Resilience4j + Actuator configuration

src/test/java/com/example/retryratelimiting/
├── WeatherControllerIntegrationTest.java  # Full stack integration tests (WireMock)
├── domain/
│   └── WeatherReportDomainTest.java       # Domain record unit tests
└── service/
    └── WeatherServiceTest.java            # Service layer unit tests (Mockito)
```

---

## Configuration Overview

Key settings in `application.yml`:

```yaml
resilience4j:
  retry:
    instances:
      weatherService:
        max-attempts: 3          # 1 initial + 2 retries
        wait-duration: 500ms
        retry-exceptions:
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException

  ratelimiter:
    instances:
      weatherService:
        limit-for-period: 10     # 10 calls allowed
        limit-refresh-period: 10s # per 10-second window
        timeout-duration: 0      # reject immediately when limit exceeded
```

---

## Running Locally (without Docker)

You need a running upstream weather API at `http://localhost:9090` that responds to `GET /weather?city={city}` with a JSON body matching `WeatherReport`. You can use WireMock standalone or any mock server.

```bash
./mvnw spring-boot:run
```

Override the upstream URL:

```bash
WEATHER_BASE_URL=http://my-weather-api:8081 ./mvnw spring-boot:run
```

---

## Running with Docker Compose

This project ships a `Dockerfile` (multi-stage build) and a `docker-compose.yml`.

### Build and start

```bash
docker compose up --build
```

### Start with a custom upstream URL

```bash
WEATHER_BASE_URL=http://my-weather-api:8081 docker compose up --build
```

### Stop and remove containers

```bash
docker compose down
```

> **Note:** This project has no external infrastructure (no database, no broker). The only container is the Spring Boot application itself. Point `WEATHER_BASE_URL` at your real or mock weather service.

---

## API Reference

### `GET /api/weather?city={city}`

Fetches the current weather for a city, protected by Retry and RateLimiter.

**Parameters:**

| Parameter | Type   | Required | Description         |
|-----------|--------|----------|---------------------|
| `city`    | string | yes      | City name to query  |

**Success response (live data):**

```json
{
  "city": "London",
  "description": "Partly cloudy",
  "temperatureC": 15.5,
  "humidity": 72,
  "windSpeedKmh": 14.0,
  "cached": false,
  "retrievedAt": "2024-06-01T09:00:00Z"
}
```

**Fallback response (rate-limited or upstream failed after retries):**

```json
{
  "city": "London",
  "description": "Weather data temporarily unavailable",
  "cached": true,
  "retrievedAt": "2024-06-01T09:00:05Z"
}
```

> When `cached: true`, the fields `temperatureC`, `humidity`, and `windSpeedKmh` are omitted (null values are suppressed by Jackson).

---

### `GET /api/resilience/status`

Returns a combined status snapshot for all registered Retry and RateLimiter instances.

### `GET /api/resilience/status/retry/{name}`

Returns metrics for a single named Retry instance.

### `GET /api/resilience/status/rate-limiter/{name}`

Returns metrics for a single named RateLimiter instance.

---

## curl Examples

### Fetch weather for a city

```bash
curl -s "http://localhost:8080/api/weather?city=London" | jq .
```

### Fetch weather — observe retry on upstream failure

Start the app, make sure the upstream is returning 5xx, then:

```bash
curl -s "http://localhost:8080/api/weather?city=Berlin" | jq .
# Returns: {"city":"Berlin","description":"Weather data temporarily unavailable","cached":true,...}
```

### Exhaust the rate limiter (10 calls per 10 s by default)

```bash
for i in $(seq 1 12); do
  echo -n "Call $i: "
  curl -s "http://localhost:8080/api/weather?city=Tokyo" | jq -r '.cached'
done
# First 10 calls return: false  (live data)
# Calls 11+ return:      true   (rate-limited fallback)
```

### Check Resilience4j status

```bash
# All instances
curl -s "http://localhost:8080/api/resilience/status" | jq .

# Retry instance
curl -s "http://localhost:8080/api/resilience/status/retry/weatherService" | jq .

# RateLimiter instance (availablePermissions shows remaining permits)
curl -s "http://localhost:8080/api/resilience/status/rate-limiter/weatherService" | jq .
```

### Missing city parameter — returns 400

```bash
curl -s "http://localhost:8080/api/weather" | jq .
```

### Actuator health

```bash
curl -s "http://localhost:8080/actuator/health" | jq .
```

### Actuator — retry events

```bash
curl -s "http://localhost:8080/actuator/retryevents" | jq .
```

### Actuator — rate limiter info

```bash
curl -s "http://localhost:8080/actuator/ratelimiters" | jq .
```

---

## Running the Tests

### Unit + Integration tests (all tests)

```bash
./mvnw clean test
```

### Unit tests only

```bash
./mvnw test -Dtest="WeatherReportDomainTest,WeatherServiceTest"
```

### Integration tests only

```bash
./mvnw test -Dtest="WeatherControllerIntegrationTest"
```

---

## Test Coverage

| Test class | Type | What it tests |
|---|---|---|
| `WeatherReportDomainTest` | Unit | `WeatherReport` record equality, cached flag, field accessors |
| `WeatherServiceTest` | Unit | `WeatherService` happy path, exception propagation, fallback method logic for both Retry exhaustion and `RequestNotPermitted` |
| `WeatherControllerIntegrationTest` | Integration | Full stack via MockMvc + WireMock: healthy upstream, 503 fallback, missing parameter (400), rate limit exhaustion fallback, status endpoints, Actuator health |

### Integration test infrastructure

- **WireMock** (in-process) acts as the fake upstream weather API. No Docker container is needed for it — it runs inside the same JVM.
- **Testcontainers** is included as a dependency to provide the Docker API version fix required on Docker Desktop 29+ (via `src/test/resources/docker-java.properties` and `testcontainers.properties`).
- The `integration-test` Spring profile configures a very tight rate limit (2 calls / 60 s) and short retry waits (100 ms) so tests complete quickly.

---

## Key Concepts

### Retry Pattern

| Concept | Description |
|---|---|
| `max-attempts` | Total attempts including the first call (3 = 1 original + 2 retries) |
| `wait-duration` | Fixed pause between attempts (use `exponential-backoff-multiplier` in production) |
| `retry-exceptions` | Only these exception types trigger a retry; 4xx errors are NOT retried |
| Fallback | Called when all attempts are exhausted; returns `cached: true` report |

### RateLimiter Pattern

| Concept | Description |
|---|---|
| `limit-for-period` | Maximum permits in one refresh window |
| `limit-refresh-period` | Window duration; permits reset after this time |
| `timeout-duration` | How long a call waits for a permit (0 = reject immediately) |
| `RequestNotPermitted` | Exception thrown when no permit available; triggers fallback |

### Fallback Methods

Both `@Retry` and `@RateLimiter` use the same `getWeatherByCityFallback(String city, Throwable t)` method. Resilience4j selects it by matching the primary method's parameter types plus a `Throwable` at the end. The fallback distinguishes the reason via `instanceof RequestNotPermitted` in the log.
