# Elastic Stack Logging

A Spring Boot mini-project demonstrating structured JSON logging shipped to the **Elastic Stack** (Elasticsearch + Logstash + Kibana). The application emits JSON-formatted log events via **logstash-logback-encoder**, which are picked up by **Filebeat**, forwarded through **Logstash** for enrichment, and indexed into **Elasticsearch** for search and visualization in **Kibana**.

---

## What This Project Demonstrates

| Concept | Where to look |
|---|---|
| JSON log formatting via `logstash-logback-encoder` | `src/main/resources/logback-spring.xml` |
| Structured fields with `StructuredArguments.kv()` | `OrderService.java`, `OrderController.java` |
| Request correlation with MDC | `OrderController.java` |
| Logstash pipeline (input → filter → output) | `docker/logstash/pipeline/logstash.conf` |
| Filebeat configuration (log file tailing) | `docker/filebeat/filebeat.yml` |
| Elasticsearch document indexing and search | `ElasticsearchIntegrationTest.java` |

---

## Log Pipeline

```
Spring Boot App
    │  writes JSON lines via logstash-logback-encoder
    ▼
/var/log/app/app.json  (Docker named volume: app-logs)
    │  tailed by Filebeat
    ▼
Filebeat  →  Logstash :5044  (Beats protocol)
    │  parses JSON, promotes structured fields, enriches events
    ▼
Logstash  →  Elasticsearch :9200  (REST)
    │  indexes daily: app-logs-2024.01.15, app-logs-2024.01.16, …
    ▼
Kibana :5601  (Web UI — search, dashboards, alerts)
```

---

## Requirements

- **Java 21+**
- **Docker Desktop** with Docker Compose V2 (`docker compose` command)
- At least **4 GB RAM** available for Docker (Elasticsearch + Kibana + Logstash are memory-intensive)

---

## Project Structure

```
24-elastic-stack-logging/
├── src/
│   ├── main/
│   │   ├── java/com/example/elasticlogging/
│   │   │   ├── ElasticStackLoggingApplication.java  # Entry point
│   │   │   ├── controller/
│   │   │   │   └── OrderController.java             # REST endpoints + MDC
│   │   │   ├── dto/
│   │   │   │   ├── CreateOrderRequest.java           # Input DTO
│   │   │   │   └── UpdateOrderStatusRequest.java     # Input DTO
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java       # Structured error logs
│   │   │   │   └── OrderNotFoundException.java
│   │   │   ├── model/
│   │   │   │   └── Order.java                       # Domain model
│   │   │   └── service/
│   │   │       └── OrderService.java                # Business logic + structured logging
│   │   └── resources/
│   │       ├── application.yml                      # App config
│   │       └── logback-spring.xml                   # JSON logging config
│   └── test/
│       ├── java/com/example/elasticlogging/
│       │   ├── controller/
│       │   │   └── OrderControllerIntegrationTest.java  # MockMvc integration tests
│       │   ├── elasticsearch/
│       │   │   └── ElasticsearchIntegrationTest.java    # Testcontainers + ES REST
│       │   └── service/
│       │       └── OrderServiceTest.java                # JUnit 5 unit tests
│       └── resources/
│           ├── application-test.yml
│           ├── docker-java.properties               # Docker API version fix
│           └── testcontainers.properties
├── docker/
│   ├── filebeat/
│   │   └── filebeat.yml                            # Filebeat log-shipping config
│   └── logstash/
│       └── pipeline/
│           └── logstash.conf                       # Logstash pipeline definition
├── Dockerfile                                      # Multi-stage app image
├── docker-compose.yml                              # Full ELK + app stack
└── pom.xml
```

---

## Running with Docker Compose (Recommended)

This is the primary way to run the full pipeline end-to-end.

### 1. Start all services

```bash
docker compose up --build
```

This starts five containers:
- **elasticsearch** on port `9200`
- **kibana** on port `5601`
- **logstash** on port `5044` (Beats protocol, internal)
- **filebeat** (no exposed ports — reads the log volume, writes to Logstash)
- **app** (Spring Boot) on port `8080`

### 2. Wait for all services to be healthy

Elasticsearch and Kibana take 1–2 minutes to start. Check status:

```bash
docker compose ps
```

All services should show `healthy`. You can also follow logs:

```bash
docker compose logs -f
```

### 3. Open Kibana

Navigate to **http://localhost:5601**

**First-time setup:**
1. Go to **Stack Management → Data Views → Create data view**
2. Name: `App Logs`, Index pattern: `app-logs-*`
3. Time field: `@timestamp`
4. Click **Save data view to Kibana**
5. Go to **Discover** to search and visualise logs

### 4. Generate log events (see API examples below)

```bash
# Create an order — generates an INFO log with orderId, customerId, amount fields
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-001","description":"Laptop model X","amount":1299.99}' | jq .
```

Wait ~5 seconds for the log to travel through Filebeat → Logstash → Elasticsearch, then search in Kibana.

### 5. Stop all services

```bash
docker compose down
```

To also remove data volumes (clears all indexed logs):

```bash
docker compose down -v
```

---

## API Reference with curl Examples

The Spring Boot app exposes an Order API. All operations generate structured JSON log events.

### Create an Order

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-001","description":"Laptop model X","amount":1299.99}' | jq .
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "customer-001",
  "description": "Laptop model X",
  "amount": 1299.99,
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": null
}
```

**Log event produced:**
```json
{ "level":"INFO", "message":"Order created", "orderId":"550e...", "customerId":"customer-001", "amount":"1299.99", "status":"PENDING", "requestId":"req-uuid" }
```

---

### List All Orders

```bash
curl -s http://localhost:8080/api/orders | jq .
```

---

### Get a Specific Order

```bash
# Replace ORDER_ID with the id from the create response
curl -s http://localhost:8080/api/orders/ORDER_ID | jq .
```

**404 response (also logged as WARN with orderId field):**
```bash
curl -s http://localhost:8080/api/orders/non-existent-id | jq .
```

---

### Update Order Status

Valid statuses: `PENDING`, `PROCESSING`, `SHIPPED`, `CANCELLED`

```bash
curl -s -X PATCH http://localhost:8080/api/orders/ORDER_ID/status \
  -H "Content-Type: application/json" \
  -d '{"status":"PROCESSING"}' | jq .

curl -s -X PATCH http://localhost:8080/api/orders/ORDER_ID/status \
  -H "Content-Type: application/json" \
  -d '{"status":"SHIPPED"}' | jq .
```

---

### Simulate a Processing Failure (ERROR log demo)

```bash
curl -s -X POST http://localhost:8080/api/orders/ORDER_ID/fail | jq .
```

This endpoint emits an **ERROR-level** structured log event — visible in Kibana when filtering by `level: ERROR`.

---

### Validation Error (400 — WARN log demo)

```bash
# Missing required fields — returns 400 and logs a WARN
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"","description":"","amount":0}' | jq .
```

---

### Health Check

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

---

## Query Elasticsearch Directly

You can query Elasticsearch without Kibana using the REST API:

```bash
# List all indices
curl -s http://localhost:9200/_cat/indices?v

# Search all log events
curl -s "http://localhost:9200/app-logs-*/_search?pretty"

# Search for ERROR-level logs
curl -s -X GET "http://localhost:9200/app-logs-*/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{"query":{"term":{"log_level.keyword":"ERROR"}}}'

# Search logs by orderId
curl -s -X GET "http://localhost:9200/app-logs-*/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{"query":{"term":{"orderId.keyword":"YOUR_ORDER_ID"}}}'

# Count documents in the index
curl -s "http://localhost:9200/app-logs-*/_count?pretty"
```

---

## Running the Tests

Tests do **not** require the full Docker Compose stack. They use **Testcontainers** to spin up an Elasticsearch container automatically.

### Requirements for Tests

- Docker Desktop must be running

### Run all tests

```bash
./mvnw clean test
```

### Test suite overview

| Test class | Type | What it tests |
|---|---|---|
| `OrderServiceTest` | Unit | Domain logic: create, get, update, fail — no Spring context |
| `OrderControllerIntegrationTest` | Integration | Full Spring context + MockMvc: all endpoints, validation, error handling |
| `ElasticsearchIntegrationTest` | Integration | Real Elasticsearch via Testcontainers: document indexing, structured field search |

---

## Key Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API with Jackson JSON |
| `spring-boot-starter-validation` | Bean Validation (`@NotBlank`, `@NotNull`, etc.) |
| `spring-boot-starter-actuator` | Health endpoint for Docker healthchecks |
| `logstash-logback-encoder` | JSON log formatting + StructuredArguments |
| `testcontainers:elasticsearch` | Real Elasticsearch in integration tests |
| `elasticsearch-rest-client` | Low-level ES REST client used in integration tests |

---

## Kibana Tips

After generating some log events, try these queries in Kibana Discover:

- Filter by level: `log_level: "ERROR"`
- Filter by order: `orderId: "your-order-id"`
- Filter by customer: `customerId: "customer-001"`
- Filter by request: `requestId: "your-request-id"` (all logs from one HTTP call)
- Find 404s: `httpStatus: 404`
- Find payment failures: `errorType: "PAYMENT_GATEWAY_TIMEOUT"`
