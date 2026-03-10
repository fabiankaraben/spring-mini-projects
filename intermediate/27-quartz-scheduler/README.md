# Quartz Scheduler

A Spring Boot application demonstrating complex cron job scheduling using **Quartz** integrated with Spring. Jobs are persisted to a **PostgreSQL** JDBC job store, so they survive application restarts. Database migrations are managed by **Flyway**.

---

## What this project demonstrates

| Concept | Where |
|---|---|
| Quartz JDBC job store (PostgreSQL) | `application.yml` + `V1__quartz_tables.sql` |
| Three distinct job types (LOGGING, CLEANUP, REPORTING) | `job/` package |
| `@DisallowConcurrentExecution` – prevent overlapping runs | All job classes |
| `@PersistJobDataAfterExecution` – stateful job data map | `ReportGenerationJob` |
| Spring ↔ Quartz DI bridge (`SpringBeanJobFactory`) | `QuartzConfig` |
| REST API – schedule / list / pause / resume / trigger / delete | `JobSchedulerController` |
| Audit log – every execution recorded in PostgreSQL | `JobAuditLog` + `V2__create_job_audit_log.sql` |
| Flyway database migrations | `db/migration/` |
| Unit tests (JUnit 5 + Mockito, no Docker) | `JobSchedulerServiceTest`, `JobAuditLogTest` |
| Integration tests (Testcontainers PostgreSQL) | `JobSchedulerIntegrationTest` |

---

## Requirements

- Java 21+
- Maven (or use the included Maven Wrapper `./mvnw`)
- Docker Desktop (for running the application and integration tests)

---

## Project structure

```
src/
├── main/
│   ├── java/com/example/quartzscheduler/
│   │   ├── QuartzSchedulerApplication.java   ← Spring Boot entry point
│   │   ├── config/
│   │   │   └── QuartzConfig.java             ← Spring ↔ Quartz DI bridge
│   │   ├── job/
│   │   │   ├── SampleLoggingJob.java         ← LOGGING job type
│   │   │   ├── DataCleanupJob.java           ← CLEANUP job type
│   │   │   └── ReportGenerationJob.java      ← REPORTING job type (stateful)
│   │   ├── model/
│   │   │   └── JobAuditLog.java              ← JPA entity for audit records
│   │   ├── repository/
│   │   │   └── JobAuditLogRepository.java    ← Spring Data JPA repository
│   │   ├── service/
│   │   │   └── JobSchedulerService.java      ← All Quartz scheduling logic
│   │   ├── controller/
│   │   │   ├── JobSchedulerController.java   ← REST API for job management
│   │   │   ├── AuditLogController.java       ← REST API for audit history
│   │   │   └── GlobalExceptionHandler.java   ← Centralised error handling
│   │   └── dto/
│   │       ├── ScheduleJobRequest.java       ← POST /api/jobs request body
│   │       ├── JobInfoResponse.java          ← Job metadata response DTO
│   │       └── ApiResponse.java             ← Generic success/error wrapper
│   └── resources/
│       ├── application.yml                   ← Spring + Quartz configuration
│       └── db/migration/
│           ├── V1__quartz_tables.sql         ← Quartz JDBC job-store DDL
│           └── V2__create_job_audit_log.sql  ← Custom audit table DDL
└── test/
    ├── java/com/example/quartzscheduler/
    │   ├── JobSchedulerIntegrationTest.java  ← Full integration tests
    │   ├── model/JobAuditLogTest.java        ← Domain model unit tests
    │   └── service/JobSchedulerServiceTest.java ← Service unit tests
    └── resources/
        ├── application-integration-test.yml
        ├── docker-java.properties
        └── testcontainers.properties
```

---

## Running with Docker Compose

Docker Compose starts both the PostgreSQL database and the Spring Boot application:

```bash
# Build the image and start all services
docker compose up --build

# Stop all services
docker compose down

# Stop and remove the database volume (full reset)
docker compose down -v
```

The application will be available at **http://localhost:8080**.

---

## API reference

### Job management — `POST /api/jobs`

Schedule a new cron job.

**Supported job types:**

| `jobType` | Description |
|---|---|
| `LOGGING` | Writes a periodic log message |
| `CLEANUP` | Deletes audit-log records older than `retentionDays` |
| `REPORTING` | Queries audit-log stats and logs a summary report |

**Quartz cron format** (6 fields): `seconds minutes hours day-of-month month day-of-week`

| Expression | Meaning |
|---|---|
| `0 0/5 * * * ?` | Every 5 minutes |
| `0 0 * * * ?` | Every hour |
| `0 0 3 * * ?` | Daily at 03:00 UTC |
| `0 0 9 ? * MON-FRI` | Weekdays at 09:00 UTC |

---

## curl examples

### Schedule a LOGGING job (every 5 minutes)

```bash
curl -s -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "heartbeatLogger",
    "jobGroup": "MAINTENANCE",
    "jobType": "LOGGING",
    "cronExpression": "0 0/5 * * * ?",
    "description": "Logs a heartbeat message every 5 minutes"
  }' | jq
```

### Schedule a CLEANUP job (daily at 03:00 UTC)

```bash
curl -s -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "auditCleanup",
    "jobGroup": "MAINTENANCE",
    "jobType": "CLEANUP",
    "cronExpression": "0 0 3 * * ?",
    "description": "Deletes audit records older than 30 days"
  }' | jq
```

### Schedule a REPORTING job (weekdays at 09:00 UTC)

```bash
curl -s -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "dailyReport",
    "jobGroup": "REPORTING",
    "jobType": "REPORTING",
    "cronExpression": "0 0 9 ? * MON-FRI",
    "description": "Generates a daily audit summary report"
  }' | jq
```

### List all scheduled jobs

```bash
curl -s http://localhost:8080/api/jobs | jq
```

### Get a single job

```bash
curl -s http://localhost:8080/api/jobs/MAINTENANCE/heartbeatLogger | jq
```

### Pause a job

```bash
curl -s -X POST http://localhost:8080/api/jobs/MAINTENANCE/heartbeatLogger/pause | jq
```

### Resume a paused job

```bash
curl -s -X POST http://localhost:8080/api/jobs/MAINTENANCE/heartbeatLogger/resume | jq
```

### Trigger a job immediately (outside its schedule)

```bash
curl -s -X POST http://localhost:8080/api/jobs/MAINTENANCE/heartbeatLogger/trigger | jq
```

### Delete a job

```bash
curl -s -X DELETE http://localhost:8080/api/jobs/MAINTENANCE/heartbeatLogger | jq
```

### View audit log (paginated, newest first)

```bash
curl -s "http://localhost:8080/api/audit?page=0&size=20" | jq
```

### View audit log for a specific job

```bash
curl -s http://localhost:8080/api/audit/heartbeatLogger | jq
```

### View audit log for a specific job + group

```bash
curl -s http://localhost:8080/api/audit/MAINTENANCE/heartbeatLogger | jq
```

### Health check

```bash
curl -s http://localhost:8080/actuator/health | jq
```

---

## Running the tests

Integration tests use **Testcontainers** to spin up a real PostgreSQL container automatically — no manual setup required.

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests (no Docker required)

```bash
./mvnw test -Dtest="JobAuditLogTest,JobSchedulerServiceTest"
```

### Run only integration tests

```bash
./mvnw test -Dtest="JobSchedulerIntegrationTest"
```

### Test coverage

| Test class | Type | What is verified |
|---|---|---|
| `JobAuditLogTest` | Unit | Domain model constructor, setters, edge cases |
| `JobSchedulerServiceTest` | Unit | Job class resolution, cron validation, Quartz delegation |
| `JobSchedulerIntegrationTest` | Integration | Full HTTP → scheduler → PostgreSQL → audit log flow |

---

## How the Quartz JDBC job store works

```
Application startup
       │
       ▼
  Flyway applies:
  ┌─────────────────────────────────┐
  │ V1__quartz_tables.sql           │  ← QRTZ_* tables (job store)
  │ V2__create_job_audit_log.sql    │  ← job_audit_log table
  └─────────────────────────────────┘
       │
       ▼
  Quartz Scheduler starts
  (reads existing jobs from QRTZ_JOB_DETAILS + QRTZ_TRIGGERS)
       │
       ▼
  REST API: POST /api/jobs
  ┌─────────────────────────────────────────────────────┐
  │ ScheduleJobRequest (jobType, cronExpression, ...)   │
  │           │                                         │
  │           ▼                                         │
  │   JobSchedulerService.scheduleJob()                 │
  │           │                                         │
  │           ▼                                         │
  │   Quartz Scheduler.scheduleJob(JobDetail, Trigger)  │
  │           │                                         │
  │           ▼                                         │
  │   Written to QRTZ_JOB_DETAILS + QRTZ_CRON_TRIGGERS │
  └─────────────────────────────────────────────────────┘
       │
       ▼  (at each scheduled time)
  Quartz fires trigger
  ┌──────────────────────────────────────┐
  │ SampleLoggingJob / DataCleanupJob /  │
  │ ReportGenerationJob.executeInternal()│
  │           │                          │
  │           ▼                          │
  │   Writes JobAuditLog row to DB       │
  └──────────────────────────────────────┘
```

---

## Docker Compose architecture

```
┌─────────────────────────────────────────────┐
│  Docker Compose network                     │
│                                             │
│  ┌──────────────────────┐                   │
│  │  postgres:16-alpine   │ ← port 5432       │
│  │  database: quartzdb   │                   │
│  │  user: quartz         │                   │
│  └──────────┬───────────┘                   │
│             │  depends_on (healthcheck)      │
│  ┌──────────▼───────────┐                   │
│  │  Spring Boot app     │ ← port 8080       │
│  │  quartz-scheduler    │                   │
│  └──────────────────────┘                   │
└─────────────────────────────────────────────┘

Host access:
  API:      http://localhost:8080/api/jobs
  Postgres: localhost:5432 (quartzdb / quartz / quartz)
```
