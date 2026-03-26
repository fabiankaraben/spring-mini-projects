# Debezium CDC (Change Data Capture)

A Spring Boot mini-project that captures **PostgreSQL database changes in real-time** using the **Debezium Embedded Engine** and streams them as events to **Apache Kafka**.

## What This Project Demonstrates

**Change Data Capture (CDC)** is a pattern for detecting and reacting to database changes (INSERT, UPDATE, DELETE) without polling. Instead of querying the database repeatedly, CDC reads the database's transaction log directly, giving you:

- **Real-time events** — changes appear in milliseconds, not polling intervals.
- **Zero application coupling** — downstream systems react to DB changes without the source app knowing about them.
- **Full change history** — every INSERT, UPDATE, and DELETE is captured, including the `before` and `after` state.

### Architecture

```
REST Client
    │  POST/PUT/DELETE /api/products
    ▼
ProductController ──► ProductService ──► ProductRepository (JPA)
                                                  │
                                                  ▼
                                            PostgreSQL
                                          (wal_level=logical)
                                                  │
                                    PostgreSQL WAL (Write-Ahead Log)
                                                  │
                                                  ▼
                                   Debezium Embedded Engine
                                   (reads replication slot)
                                                  │
                                                  ▼
                                       CdcEventDispatcher
                                   (SourceRecord → ProductCdcEvent)
                                                  │
                                                  ▼
                                           KafkaTemplate
                                                  │
                                                  ▼
                                  Kafka topic: product-cdc-events
                                                  │
                                                  ▼
                                       CdcEventConsumer (demo)
                                      (logs each event received)
```

### Key Components

| Component | Description |
|-----------|-------------|
| `Product` | JPA entity persisted to PostgreSQL `products` table |
| `ProductController` | REST API — CRUD operations that trigger CDC events |
| `DebeziumConnectorConfig` | Configures the embedded Debezium PostgreSQL connector |
| `DebeziumEngineRunner` | Starts/stops the Debezium engine with the Spring lifecycle |
| `CdcEventDispatcher` | Translates Debezium `SourceRecord` → `ProductCdcEvent` → Kafka |
| `CdcEventConsumer` | Demo Kafka listener that logs each received CDC event |
| `KafkaConfig` | Configures Kafka producer (JsonSerializer) and consumer (JsonDeserializer) |

### CDC Event Envelope

Each event published to Kafka carries:

```json
{
  "operation": "CREATE",
  "before": null,
  "after": {
    "id": 1,
    "name": "Laptop",
    "description": "High-performance laptop",
    "price": 1299.99,
    "stock": 50
  },
  "capturedAt": "2024-01-15T12:00:00Z"
}
```

For UPDATE, both `before` and `after` are populated. For DELETE, only `before` is populated and `after` is `null`.

---

## Requirements

- **Java 21+**
- **Maven 3.9+** (or use the included Maven Wrapper `./mvnw`)
- **Docker** and **Docker Compose** (for running the full stack)

---

## Running with Docker Compose

The entire project runs inside Docker. Docker Compose orchestrates four services:

| Service | Image | Port |
|---------|-------|------|
| `postgres` | `postgres:16-alpine` | `5432` |
| `zookeeper` | `confluentinc/cp-zookeeper:7.6.1` | — |
| `kafka` | `confluentinc/cp-kafka:7.6.1` | `9092` |
| `app` | Built from `Dockerfile` | `8080` |

### Start all services

```bash
docker compose up --build
```

> The first build downloads dependencies and may take a few minutes. Subsequent builds are fast thanks to Docker layer caching.

### Start in detached (background) mode

```bash
docker compose up --build -d
```

### Follow logs

```bash
docker compose logs -f          # all services
docker compose logs -f app      # Spring Boot app only
docker compose logs -f kafka    # Kafka only
```

### Stop all services

```bash
docker compose down
```

### Stop and remove volumes (wipe database)

```bash
docker compose down -v
```

---

## REST API Usage

Once the stack is running, the API is available at `http://localhost:8080/api/products`.

### Create a product (triggers CDC INSERT event)

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop",
    "description": "High-performance laptop",
    "price": 1299.99,
    "stock": 50
  }' | jq .
```

Expected response (HTTP 201):

```json
{
  "id": 1,
  "name": "Laptop",
  "description": "High-performance laptop",
  "price": 1299.99,
  "stock": 50,
  "createdAt": "2024-01-15T12:00:00Z",
  "updatedAt": "2024-01-15T12:00:00Z"
}
```

### Get all products

```bash
curl -s http://localhost:8080/api/products | jq .
```

### Get a product by ID

```bash
curl -s http://localhost:8080/api/products/1 | jq .
```

### Update a product (triggers CDC UPDATE event)

```bash
curl -s -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Gaming Laptop",
    "price": 1499.99,
    "stock": 30
  }' | jq .
```

> Only the provided fields are updated. Null fields in the request body leave the corresponding database column unchanged.

### Delete a product (triggers CDC DELETE event)

```bash
curl -s -X DELETE http://localhost:8080/api/products/1
```

Expected response: HTTP 204 No Content.

### Health check

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

---

## Observing CDC Events in Application Logs

Every CDC event is logged by `CdcEventConsumer`. After creating a product, look for lines like:

```
[CDC Consumer] PRODUCT CREATED — id=1, name='Laptop', price=1299.99, stock=50
```

After updating:
```
[CDC Consumer] PRODUCT UPDATED — id=1, name='Laptop' → 'Gaming Laptop', price=1299.99 → 1499.99, stock=50 → 30
```

After deleting:
```
[CDC Consumer] PRODUCT DELETED — id=1, name='Gaming Laptop'
```

---

## Observing Events Directly in Kafka

With the stack running, you can consume the raw Kafka topic from the host:

```bash
# List topics
docker exec debezium-kafka kafka-topics \
  --bootstrap-server localhost:9092 --list

# Consume CDC events (from beginning)
docker exec debezium-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic product-cdc-events \
  --from-beginning
```

---

## Running the Tests

> **Note:** Tests use Testcontainers, which requires Docker to be running. Do NOT use `docker compose up` before running tests — Testcontainers manages its own containers automatically.

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests

```bash
./mvnw test -Dtest="ProductTest,CdcOperationTest,ProductServiceTest"
```

### Run only integration tests

```bash
./mvnw test -Dtest="DebeziumCdcIntegrationTest"
```

---

## Test Coverage

### Unit Tests (no Docker required)

| Test Class | What It Tests |
|-----------|---------------|
| `ProductTest` | Domain entity construction, setters, defaults |
| `CdcOperationTest` | Debezium op-code mapping (`c`→CREATE, `u`→UPDATE, etc.) |
| `ProductServiceTest` | Service CRUD logic with mocked repository (Mockito) |

### Integration Tests (requires Docker)

| Test Class | What It Tests |
|-----------|---------------|
| `DebeziumCdcIntegrationTest` | Full CDC pipeline with real PostgreSQL + Kafka (Testcontainers) |

The integration test verifies:
- REST API returns correct HTTP status codes (201, 200, 204, 404, 400)
- INSERT → CDC CREATE event arrives in Kafka with correct `after` payload
- UPDATE → CDC UPDATE event arrives in Kafka with correct `before` and `after` payloads
- DELETE → CDC DELETE event arrives in Kafka with correct `before` payload and null `after`

---

## Project Structure

```
17-debezium-cdc/
├── src/
│   ├── main/
│   │   ├── java/com/example/debeziumcdc/
│   │   │   ├── DebeziumCdcApplication.java         # Spring Boot entry point
│   │   │   ├── cdc/
│   │   │   │   ├── CdcOperation.java               # Debezium op-code enum
│   │   │   │   ├── CdcEventConsumer.java           # Demo Kafka @KafkaListener
│   │   │   │   ├── CdcEventDispatcher.java         # Debezium → Kafka bridge
│   │   │   │   └── ProductCdcEvent.java            # CDC event DTO (envelope)
│   │   │   ├── config/
│   │   │   │   ├── DebeziumConnectorConfig.java    # Embedded engine configuration
│   │   │   │   ├── DebeziumEngineRunner.java       # Engine lifecycle management
│   │   │   │   └── KafkaConfig.java                # Kafka producer/consumer beans
│   │   │   ├── domain/
│   │   │   │   └── Product.java                    # JPA entity
│   │   │   ├── repository/
│   │   │   │   └── ProductRepository.java          # Spring Data JPA repository
│   │   │   ├── service/
│   │   │   │   └── ProductService.java             # Business logic + transactions
│   │   │   └── web/
│   │   │       ├── CreateProductRequest.java       # POST request DTO
│   │   │       ├── ProductController.java          # REST controller
│   │   │       └── UpdateProductRequest.java       # PUT request DTO
│   │   └── resources/
│   │       └── application.yml                     # Application configuration
│   └── test/
│       ├── java/com/example/debeziumcdc/
│       │   ├── cdc/
│       │   │   └── CdcOperationTest.java           # Unit tests for op mapping
│       │   ├── domain/
│       │   │   └── ProductTest.java                # Unit tests for entity
│       │   ├── integration/
│       │   │   └── DebeziumCdcIntegrationTest.java # Full CDC pipeline test
│       │   └── service/
│       │       └── ProductServiceTest.java         # Service unit tests (Mockito)
│       └── resources/
│           ├── docker-java.properties              # Docker API v1.44 fix
│           └── testcontainers.properties           # Testcontainers Docker fix
├── .gitignore
├── .mvn/wrapper/maven-wrapper.properties
├── docker-compose.yml                              # Full Docker stack
├── Dockerfile                                      # Multi-stage build
├── mvnw                                            # Maven Wrapper script
├── pom.xml                                         # Maven dependencies
└── README.md                                       # This file
```

---

## How Debezium Logical Replication Works

1. **WAL (Write-Ahead Log)** — PostgreSQL writes every committed transaction to the WAL before applying it to data files. This ensures durability and supports replication.

2. **Logical Replication** — When `wal_level=logical`, PostgreSQL decodes WAL records into a logical change stream using a _logical decoding plugin_. This project uses the built-in `pgoutput` plugin (available since PostgreSQL 10).

3. **Replication Slot** — A named cursor (`debezium_product_slot`) that tracks how far Debezium has read the WAL. PostgreSQL retains WAL segments until the slot consumer has processed them, ensuring no changes are lost even if Debezium restarts.

4. **Publication** — A PostgreSQL `PUBLICATION` (`debezium_product_pub`) defines which tables are included in the logical replication stream. Debezium auto-creates this with `all_tables` mode.

5. **Debezium Embedded Engine** — Runs inside the Spring Boot process on a daemon thread. It connects to the replication slot, receives decoded change records (`SourceRecord`), and invokes the `CdcEventDispatcher` for each batch.

---

## Production Considerations

This mini-project uses in-memory offset and schema history storage (fine for demos). For production:

| Concern | Demo Setting | Production Recommendation |
|---------|-------------|---------------------------|
| Offset storage | `MemoryOffsetBackingStore` (lost on restart) | `KafkaOffsetBackingStore` or `FileOffsetBackingStore` |
| Schema history | `MemorySchemaHistory` (lost on restart) | `KafkaSchemaHistory` |
| Scaling | Single embedded engine | Kafka Connect cluster with distributed mode |
| Replication slot leak | Not applicable (demo) | Monitor active slots; clean up unused ones |
| Snapshot mode | `initial` (re-snapshots on restart if no offset) | `never` after first run, with durable offset storage |
