# Dynamic Scheduling

A Spring Boot backend that modifies `@Scheduled` task frequencies **at runtime**, without restarting the application.

## What This Project Demonstrates

Standard Spring Boot scheduling uses `@Scheduled(fixedRate = ...)` which bakes the interval into bytecode at compile-time – it cannot be changed without a restart.

This project replaces that pattern with:

1. **`SchedulingConfigurer`** – registers tasks programmatically via `ScheduledTaskRegistrar` with a custom `Trigger` implementation.
2. **`DynamicTaskRegistry`** – an in-memory `ConcurrentHashMap`-backed registry that stores the current interval and enabled flag for every task.
3. **Custom Trigger** – on every `nextExecution()` evaluation (called after each execution), the trigger reads the live interval from the registry. Updating the registry via the REST API is therefore reflected on the very next execution cycle — **no restart required**.
4. **PostgreSQL + Flyway** – task configurations and execution logs are persisted so schedules survive application restarts.

### Pre-seeded Tasks

Flyway migration `V2__seed_tasks.sql` creates four demo tasks on first startup:

| Task Name  | Default Interval | Enabled |
|------------|-----------------|---------|
| `heartbeat` | 3 seconds | Yes |
| `report`    | 30 seconds | Yes |
| `cleanup`   | 60 seconds | Yes |
| `data-sync` | 15 seconds | No (disabled by default) |

---

## Requirements

- **Java 21+**
- **Maven 3.9+** (or use the included `./mvnw` wrapper)
- **Docker Desktop** (for running PostgreSQL and the full application stack)

---

## Running the Application with Docker Compose

The entire stack (PostgreSQL + Spring Boot application) is managed via Docker Compose.

### Start

```bash
docker compose up --build
```

This will:
1. Pull the `postgres:16-alpine` image and start a PostgreSQL container.
2. Build the Spring Boot application image from the `Dockerfile`.
3. Apply Flyway migrations (create tables + seed initial tasks).
4. Start the application on `http://localhost:8080`.

### Stop

```bash
docker compose down
```

### Stop and remove all data (clean slate)

```bash
docker compose down -v
```

The `-v` flag removes the named `pgdata` volume so the next `docker compose up` starts with a fresh database.

### View logs

```bash
# All services
docker compose logs -f

# Application only
docker compose logs -f app

# PostgreSQL only
docker compose logs -f postgres
```

---

## REST API Reference

Base URL: `http://localhost:8080`

### Task Management

#### Create a new task

```bash
curl -s -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "my-task",
    "description": "My custom background task",
    "intervalMs": 5000,
    "enabled": true
  }' | jq
```

#### List all tasks

```bash
curl -s http://localhost:8080/api/tasks | jq
```

#### Get a single task

```bash
curl -s http://localhost:8080/api/tasks/heartbeat | jq
```

#### Update interval at runtime (core feature)

Change the `heartbeat` task from 3 seconds to 10 seconds — **takes effect on the next execution cycle without restart**:

```bash
curl -s -X PATCH http://localhost:8080/api/tasks/heartbeat/interval \
  -H "Content-Type: application/json" \
  -d '{"intervalMs": 10000}' | jq
```

Change it back to 3 seconds:

```bash
curl -s -X PATCH http://localhost:8080/api/tasks/heartbeat/interval \
  -H "Content-Type: application/json" \
  -d '{"intervalMs": 3000}' | jq
```

#### Enable a disabled task

```bash
curl -s -X POST http://localhost:8080/api/tasks/data-sync/enable | jq
```

#### Disable a running task

```bash
curl -s -X POST http://localhost:8080/api/tasks/heartbeat/disable | jq
```

#### Delete a task

```bash
curl -s -X DELETE http://localhost:8080/api/tasks/my-task | jq
```

---

### Execution Logs

#### Get all execution logs (paginated)

```bash
curl -s "http://localhost:8080/api/logs?page=0&size=20" | jq
```

#### Get execution logs for a specific task

```bash
curl -s "http://localhost:8080/api/logs/heartbeat?page=0&size=10" | jq
```

---

### Health Check

```bash
curl -s http://localhost:8080/actuator/health | jq
```

---

## Demonstrating Dynamic Scheduling

The following sequence shows the core feature in action:

```bash
# 1. Start the application
docker compose up --build

# 2. In a second terminal – watch the heartbeat task execute every 3 seconds
docker compose logs -f app | grep "heartbeat"

# 3. Change the heartbeat interval to 15 seconds (no restart!)
curl -s -X PATCH http://localhost:8080/api/tasks/heartbeat/interval \
  -H "Content-Type: application/json" \
  -d '{"intervalMs": 15000}' | jq

# 4. Watch the logs again – the heartbeat now fires every 15 seconds

# 5. Query the execution log to see the interval snapshot change
curl -s "http://localhost:8080/api/logs/heartbeat?page=0&size=5" | jq '.content[] | {firedAt, intervalMsSnapshot}'
```

---

## API Response Structure

**Task status response:**
```json
{
  "taskName": "heartbeat",
  "description": "Lightweight health-check ping. Runs every 3 seconds by default.",
  "configuredIntervalMs": 3000,
  "liveIntervalMs": 3000,
  "enabled": true
}
```

**Execution log entry:**
```json
{
  "id": 42,
  "taskName": "heartbeat",
  "firedAt": "2026-03-11T05:00:00Z",
  "durationMs": 52,
  "intervalMsSnapshot": 3000,
  "status": "SUCCESS",
  "errorMessage": null
}
```

**Error response:**
```json
{
  "success": false,
  "message": "Task not found: 'ghost-task'"
}
```

---

## Running the Tests

Tests require Docker to be running (Testcontainers starts a PostgreSQL container automatically).

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests

```bash
./mvnw test -Dtest="DynamicTaskRegistryTest,TaskManagementServiceTest"
```

### Run only integration tests

```bash
./mvnw test -Dtest="DynamicSchedulingIntegrationTest"
```

### Test structure

| Test Class | Type | Description |
|---|---|---|
| `DynamicTaskRegistryTest` | Unit | Tests the in-memory registry in isolation (no Spring context, no database) |
| `TaskManagementServiceTest` | Unit | Tests the service layer with Mockito mocks for all collaborators |
| `DynamicSchedulingIntegrationTest` | Integration | Full Spring context + real PostgreSQL via Testcontainers |

---

## Project Structure

```
src/main/java/com/example/dynamicscheduling/
├── DynamicSchedulingApplication.java       # Entry point (@EnableScheduling)
├── config/
│   └── SchedulerConfig.java                # ThreadPoolTaskScheduler bean (pool size 5)
├── controller/
│   ├── TaskController.java                 # REST API: /api/tasks
│   ├── ExecutionLogController.java         # REST API: /api/logs
│   └── GlobalExceptionHandler.java         # Converts exceptions to JSON responses
├── dto/
│   ├── CreateTaskRequest.java              # Request DTO: create task
│   ├── UpdateIntervalRequest.java          # Request DTO: update interval
│   ├── TaskStatusResponse.java             # Response DTO: task state
│   └── ApiResponse.java                    # Response DTO: success/error wrapper
├── model/
│   ├── TaskConfig.java                     # JPA entity: task configuration
│   └── TaskExecutionLog.java               # JPA entity: execution audit record
├── repository/
│   ├── TaskConfigRepository.java           # Spring Data JPA repository
│   └── TaskExecutionLogRepository.java     # Spring Data JPA repository (paginated)
├── scheduling/
│   ├── DynamicTaskRegistry.java            # In-memory ConcurrentHashMap registry
│   └── DynamicSchedulingConfigurer.java    # SchedulingConfigurer: registers tasks at startup
└── service/
    ├── TaskManagementService.java          # CRUD for task configs + live registry updates
    └── TaskExecutionService.java           # Dispatches and logs each task execution

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__create_tables.sql               # DDL: task_config + task_execution_log
    └── V2__seed_tasks.sql                  # Seed: 4 demo tasks

src/test/
├── java/com/example/dynamicscheduling/
│   ├── DynamicSchedulingIntegrationTest.java
│   ├── scheduling/DynamicTaskRegistryTest.java
│   └── service/TaskManagementServiceTest.java
└── resources/
    ├── application-integration-test.yml
    ├── docker-java.properties              # Docker API version for Testcontainers
    └── testcontainers.properties
```

---

## How Dynamic Rescheduling Works (Technical Detail)

The key is how `DynamicSchedulingConfigurer.scheduleTask()` submits tasks to the `TaskScheduler`:

```java
taskScheduler.schedule(
    () -> taskExecutionService.executeTask(taskName),
    triggerContext -> {
        // This lambda is called AFTER every execution.
        // It reads the CURRENT interval from the registry (not a captured value).
        long currentIntervalMs = registry.getInterval(taskName, 5000L);
        Instant base = (triggerContext.lastCompletion() != null)
            ? triggerContext.lastCompletion() : Instant.now();
        return base.plus(Duration.ofMillis(currentIntervalMs));
    }
);
```

When the REST API receives `PATCH /api/tasks/heartbeat/interval`:
1. The new value is written to PostgreSQL.
2. The new value is put into `DynamicTaskRegistry` (a `ConcurrentHashMap`).
3. On the **very next trigger evaluation** (immediately after the current execution completes), the lambda above reads the new value from the map.
4. The next fire time is calculated using the new interval — **no cancellation or rescheduling needed**.
