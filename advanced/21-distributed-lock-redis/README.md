# Distributed Lock Redis

A Spring Boot mini-project demonstrating how to prevent concurrent task execution
across multiple application instances using a **Redis-backed distributed lock**
provided by [Redisson](https://redisson.org/).

---

## What is a Distributed Lock?

In a horizontally-scaled deployment, several JVM processes run the same code simultaneously.
A standard Java `synchronized` block only serialises threads within **one** JVM.

If the same logical task (e.g. "generate the monthly report") is triggered on two nodes at
the same instant, both will run concurrently — potentially causing duplicate work, data
corruption, or resource exhaustion.

A **distributed lock** uses a single shared Redis instance as the coordination point:

```
Node A                          Redis                           Node B
  |                               |                               |
  |-- SET task-lock:X <uuidA> --> |                               |
  |   NX PX 30000                 |                               |
  |<-- OK (lock acquired) --------|                               |
  |                               |<-- SET task-lock:X <uuidB> --|
  |  [processing...]              |    NX PX 30000                |
  |                               |--> nil (lock busy) ---------->|
  |                               |   [waits or returns SKIPPED]  |
  |-- DEL task-lock:X (Lua) ----> |                               |
  |<-- OK (lock released) --------|                               |
  |                               |<-- SET task-lock:X <uuidB> --|
  |                               |--> OK (lock acquired) ------->|
  |                               |  [processing...]              |
```

If the holder **crashes** before releasing, Redis auto-expires the key after
`leaseSeconds` → **no dead-locks**.

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Java        | 21+     |
| Maven       | 3.9+ (or use the included Maven Wrapper) |
| Docker      | 24+     |
| Docker Compose | v2 (`docker compose`) |

> **Running tests locally** requires Docker to be running (Testcontainers spins up a real Redis container).

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/distributedlock/
│   │   ├── DistributedLockRedisApplication.java  # Spring Boot entry point
│   │   ├── config/
│   │   │   └── AppProperties.java                # Typed config (lock timeout, lease, processing-ms)
│   │   ├── domain/
│   │   │   ├── TaskResult.java                   # Immutable value object (record) — outcome of task execution
│   │   │   ├── TaskService.java                  # Core service: acquires Redis lock, runs task
│   │   │   └── TaskStatus.java                   # Enum: COMPLETED | SKIPPED | INTERRUPTED
│   │   └── web/
│   │       ├── controller/
│   │       │   └── TaskController.java           # REST endpoints
│   │       └── dto/
│   │           ├── TaskRequest.java              # Validated request body (record)
│   │           └── TaskResponse.java             # JSON response body (record)
│   └── resources/
│       └── application.yml                       # App + Redis + lock configuration
└── test/
    ├── java/com/example/distributedlock/
    │   ├── domain/
    │   │   ├── TaskResultTest.java               # Unit tests — domain value object
    │   │   └── TaskServiceTest.java              # Unit tests — locking logic (Mockito)
    │   └── integration/
    │       └── TaskControllerIntegrationTest.java # Integration tests (Testcontainers Redis)
    └── resources/
        ├── application-test.yml                  # Test profile overrides
        ├── docker-java.properties                # Docker API version fix for Docker Desktop 29+
        └── testcontainers.properties             # Testcontainers API version fix
```

---

## Running with Docker Compose

The entire project (Redis + Spring Boot app) runs via Docker Compose.

### Start

```bash
docker compose up --build
```

This will:
1. Build the Spring Boot fat-jar inside a Docker build stage (Maven is not required on the host).
2. Start a Redis 7 container.
3. Start the application container once Redis is healthy.

### Start in detached mode

```bash
docker compose up --build -d
```

### Stop

```bash
docker compose down
```

### Stop and remove data volume

```bash
docker compose down -v
```

### Follow logs

```bash
docker compose logs -f         # all services
docker compose logs -f app     # application only
docker compose logs -f redis   # Redis only
```

---

## API Reference

### Submit a Task

```
POST /api/tasks
Content-Type: application/json
```

**Request body:**

| Field      | Type   | Required | Description |
|------------|--------|----------|-------------|
| `taskKey`  | string | Yes      | Logical task identifier. Two requests with the **same** key contend for the **same** Redis lock. |
| `payload`  | string | Yes      | Arbitrary data to process (logged for demonstration). |

**Response body:**

| Field       | Type   | Description |
|-------------|--------|-------------|
| `taskKey`   | string | Echo of the submitted key. |
| `payload`   | string | Echo of the submitted payload. |
| `status`    | string | `COMPLETED`, `SKIPPED`, or `INTERRUPTED`. |
| `message`   | string | Human-readable description of the outcome. |
| `timestamp` | string | ISO-8601 UTC timestamp. |
| `elapsedMs` | number | Total elapsed time in milliseconds. |

**HTTP status codes:**

| Status | Meaning |
|--------|---------|
| `200 OK`                  | Lock acquired, task COMPLETED. |
| `409 Conflict`            | Lock not available (SKIPPED) — another node is executing the same task. |
| `400 Bad Request`         | Validation error (blank fields, size exceeded). |
| `500 Internal Server Error` | Thread interrupted during lock acquisition or processing. |

---

## curl Examples

### 1. Submit a task (happy path)

```bash
curl -s -X POST http://localhost:8080/api/tasks \
     -H 'Content-Type: application/json' \
     -d '{"taskKey":"report-generation","payload":"Q4 financial data"}' | jq .
```

Expected response (`200 OK`):
```json
{
  "taskKey": "report-generation",
  "payload": "Q4 financial data",
  "status": "COMPLETED",
  "message": "Task completed successfully while holding the distributed lock.",
  "timestamp": "2025-01-01T12:00:02.000Z",
  "elapsedMs": 3012
}
```

---

### 2. Demonstrate lock contention (concurrent submissions — same key)

Send two requests simultaneously with the **same** `taskKey`.
The first acquires the lock; the second is skipped (409).

```bash
curl -s -X POST http://localhost:8080/api/tasks \
     -H 'Content-Type: application/json' \
     -d '{"taskKey":"report-generation","payload":"first request"}' &

curl -s -X POST http://localhost:8080/api/tasks \
     -H 'Content-Type: application/json' \
     -d '{"taskKey":"report-generation","payload":"second request"}'
```

First response (`200 OK`):
```json
{
  "taskKey": "report-generation",
  "status": "COMPLETED",
  "elapsedMs": 3011
}
```

Second response (`409 Conflict`):
```json
{
  "taskKey": "report-generation",
  "status": "SKIPPED",
  "message": "Task skipped: could not acquire the distributed lock within the timeout...",
  "elapsedMs": 5001
}
```

> **Tip:** The `APP_TASK_PROCESSING-MS` environment variable in `docker-compose.yml` controls
> how long the app "works" while holding the lock. Default is `3000` ms — enough for
> the second request to time out.

---

### 3. Demonstrate independent locks (concurrent submissions — different keys)

Tasks with **different** `taskKey` values use **independent** Redis locks and run fully concurrently.

```bash
curl -s -X POST http://localhost:8080/api/tasks \
     -H 'Content-Type: application/json' \
     -d '{"taskKey":"job-A","payload":"data for A"}' &

curl -s -X POST http://localhost:8080/api/tasks \
     -H 'Content-Type: application/json' \
     -d '{"taskKey":"job-B","payload":"data for B"}'
```

Both respond with `200 OK` and `"status": "COMPLETED"` simultaneously.

---

### 4. Inspect a live Redis lock

While a task is processing, inspect the lock key in Redis:

```bash
# Connect to the Redis container
docker compose exec redis redis-cli

# List active lock keys
KEYS task-lock:*

# Inspect a specific lock (shows remaining TTL in milliseconds)
TTL task-lock:report-generation
```

---

### 5. Health check

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

```bash
curl -s http://localhost:8080/api/tasks/health
```

---

### 6. Validation error example

```bash
curl -s -X POST http://localhost:8080/api/tasks \
     -H 'Content-Type: application/json' \
     -d '{"taskKey":"","payload":"data"}' | jq .
```

Response (`400 Bad Request`):
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "taskKey must not be blank"
}
```

---

## Running Tests

> Docker must be running before executing tests (Testcontainers uses it for the Redis container).

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests

```bash
./mvnw test -Dtest="TaskResultTest,TaskServiceTest"
```

### Run only integration tests

```bash
./mvnw test -Dtest="TaskControllerIntegrationTest"
```

---

## Test Coverage

| Test class | Type | What it verifies |
|------------|------|------------------|
| `TaskResultTest` | Unit | `TaskResult` factory methods, record equality, field correctness |
| `TaskServiceTest` | Unit (Mockito) | Lock acquisition → COMPLETED; lock busy → SKIPPED; interrupt → INTERRUPTED; `unlock()` always called; correct key prefix |
| `TaskControllerIntegrationTest` | Integration (Testcontainers) | Full HTTP cycle: 200/400/409; sequential lock release; independent keys; Redis container health |

---

## Configuration Reference

All tunable values live in `application.yml` and can be overridden via environment variables.

| YAML key | Env variable | Default | Description |
|----------|-------------|---------|-------------|
| `app.lock.timeout-seconds` | `APP_LOCK_TIMEOUT-SECONDS` | `5` | Seconds to wait for the lock before returning SKIPPED |
| `app.lock.lease-seconds` | `APP_LOCK_LEASE-SECONDS` | `30` | Lock TTL in Redis (dead-lock prevention) |
| `app.task.processing-ms` | `APP_TASK_PROCESSING-MS` | `2000` | Simulated task duration in milliseconds |
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` | Redis server hostname |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` | Redis server port |

---

## Key Technologies

| Technology | Role |
|------------|------|
| **Spring Boot 3.4** | Application framework |
| **Redisson 3.37** | Redis client providing distributed `RLock` |
| **Redis 7** | Lock coordination backend |
| **JUnit 5** | Unit and integration test runner |
| **Mockito** | Mock `RedissonClient` / `RLock` in unit tests |
| **Testcontainers** | Spin up a real Redis Docker container for integration tests |
| **AssertJ** | Fluent assertions in tests |
| **Bean Validation** | Request body validation (`@NotBlank`, `@Size`) |
| **Spring Actuator** | `/actuator/health` for Docker healthcheck probes |
