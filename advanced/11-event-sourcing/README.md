# Event Sourcing with Axon Framework

A Spring Boot mini-project demonstrating **Event Sourcing** using Axon Framework.
State is stored as an **append-only sequence of immutable events** — never as a mutable snapshot row.

---

## What is Event Sourcing?

Traditional applications store the *current state* of a domain object:

```
┌─────────────────────────────┐
│  account_id │ balance │ ... │
│  abc-123    │ 750.00  │ ... │  ← overwritten on every change
└─────────────────────────────┘
```

Event Sourcing stores the *sequence of events* that led to that state:

```
┌────────────────────────────────────────────────────────────┐
│  #1 AccountOpenedEvent   │ balance=500.00  │ 2024-01-01... │
│  #2 MoneyDepositedEvent  │ amount=250.00   │ 2024-01-02... │
│  #3 MoneyWithdrawnEvent  │ amount=100.00   │ 2024-01-03... │
│  (current balance = 500 + 250 - 100 = 650.00, computed)   │
└────────────────────────────────────────────────────────────┘
```

The current state is reconstructed at any time by **replaying** the event history.

### Key benefits

- **Full audit trail** — every change is recorded as an immutable event
- **Temporal queries** — reconstruct state at any past point in time
- **No data loss** — even closed accounts have their full history
- **Event-driven integration** — other services can subscribe to the event stream
- **Append-only** — no UPDATE or DELETE on the event store

---

## Domain: Bank Account

| Command | Event emitted | Business rule |
|---|---|---|
| `OpenAccountCommand` | `AccountOpenedEvent` | Initial deposit ≥ 0, owner name required |
| `DepositMoneyCommand` | `MoneyDepositedEvent` | Account must be ACTIVE, amount > 0 |
| `WithdrawMoneyCommand` | `MoneyWithdrawnEvent` | Account must be ACTIVE, no overdraft |
| `CloseAccountCommand` | `AccountClosedEvent` | Account must not already be CLOSED |

---

## Requirements

- Java 21+
- Maven (via Maven Wrapper — no installation needed)
- Docker & Docker Compose (for running the full stack)
- Docker (for integration tests via Testcontainers)

---

## Project Structure

```
src/main/java/com/example/eventsourcing/
├── EventSourcingApplication.java       # Spring Boot entry point
├── command/
│   ├── api/                            # Commands (intent) and Events (facts)
│   │   ├── OpenAccountCommand.java
│   │   ├── DepositMoneyCommand.java
│   │   ├── WithdrawMoneyCommand.java
│   │   ├── CloseAccountCommand.java
│   │   ├── AccountOpenedEvent.java
│   │   ├── MoneyDepositedEvent.java
│   │   ├── MoneyWithdrawnEvent.java
│   │   └── AccountClosedEvent.java
│   └── aggregate/
│       ├── BankAccountAggregate.java   # Core domain object — enforces business rules
│       ├── AccountStatus.java          # ACTIVE / CLOSED enum
│       └── InsufficientFundsException.java
├── query/
│   ├── api/                            # Query objects
│   │   ├── FindAccountByIdQuery.java
│   │   └── FindAllAccountsQuery.java
│   ├── handler/
│   │   └── AccountProjection.java      # Builds/queries the read model from events
│   └── model/
│       ├── AccountSummary.java         # JPA entity (read model)
│       └── AccountSummaryRepository.java
├── rest/
│   ├── AccountCommandController.java   # Write endpoints (open, deposit, withdraw, close)
│   ├── AccountQueryController.java     # Read endpoints (get account, list accounts)
│   └── GlobalExceptionHandler.java     # Maps domain exceptions to HTTP status codes
└── config/
    └── AxonConfig.java                 # Embedded JPA event store configuration
```

---

## Running with Docker Compose

This is the recommended way to run the full stack (application + PostgreSQL).

```bash
# Build the application image and start all services
docker compose up --build

# Or in detached (background) mode
docker compose up --build -d

# Tail the application logs
docker compose logs -f app

# Stop and remove containers (keep data volume)
docker compose down

# Stop and remove everything including the data volume
docker compose down -v
```

The application will be available at `http://localhost:8080` once the health check passes (~30s).

### Services started

| Service | Port | Description |
|---|---|---|
| `app` | 8080 | Spring Boot application |
| `postgres` | 5432 | PostgreSQL (event store + read model) |

### PostgreSQL tables (auto-created by Hibernate)

| Table | Description |
|---|---|
| `domain_event_entry` | Axon event store — one row per domain event (append-only) |
| `snapshot_event_entry` | Aggregate snapshots for performance |
| `account_summaries` | Read model projection (denormalised) |

---

## API Reference with curl Examples

### Open a new account

```bash
curl -s -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName": "Alice Smith", "initialDeposit": 500.00}' | jq .
```

Response `201 Created`:
```json
{
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Account opened successfully"
}
```

### Deposit money

```bash
# Replace ACCOUNT_ID with the value from the open-account response
ACCOUNT_ID="550e8400-e29b-41d4-a716-446655440000"

curl -s -X POST http://localhost:8080/api/accounts/$ACCOUNT_ID/deposits \
  -H "Content-Type: application/json" \
  -d '{"amount": 250.00, "description": "Salary"}' | jq .
```

Response `200 OK`:
```json
{"message": "Deposit successful"}
```

### Withdraw money

```bash
curl -s -X POST http://localhost:8080/api/accounts/$ACCOUNT_ID/withdrawals \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "description": "Groceries"}' | jq .
```

Response `200 OK`:
```json
{"message": "Withdrawal successful"}
```

### Withdraw more than balance (demonstrates no-overdraft rule)

```bash
curl -s -X POST http://localhost:8080/api/accounts/$ACCOUNT_ID/withdrawals \
  -H "Content-Type: application/json" \
  -d '{"amount": 99999.00, "description": "Overdraft attempt"}' | jq .
```

Response `409 Conflict`:
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient funds: balance=650.00, requested=99999.00",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Get account by ID

```bash
curl -s http://localhost:8080/api/accounts/$ACCOUNT_ID | jq .
```

Response `200 OK`:
```json
{
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "ownerName": "Alice Smith",
  "balance": 650.00,
  "status": "ACTIVE",
  "openedAt": "2024-01-01T10:00:00Z",
  "updatedAt": "2024-01-01T12:00:00Z"
}
```

### List all accounts

```bash
curl -s http://localhost:8080/api/accounts | jq .
```

### Filter accounts by status

```bash
# Only active accounts
curl -s "http://localhost:8080/api/accounts?status=ACTIVE" | jq .

# Only closed accounts
curl -s "http://localhost:8080/api/accounts?status=CLOSED" | jq .
```

### Close an account

```bash
curl -s -X DELETE http://localhost:8080/api/accounts/$ACCOUNT_ID \
  -H "Content-Type: application/json" \
  -d '{"reason": "Moving abroad"}' | jq .
```

Response `200 OK`:
```json
{"message": "Account closed successfully"}
```

### Health check

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

---

## Observing Event Sourcing in Action

After running a few commands, you can query PostgreSQL directly to see the event log:

```bash
# Connect to PostgreSQL
docker exec -it eventsourcing-postgres psql -U axon -d axondb

# See the raw event log — the source of truth
SELECT aggregate_identifier, sequence_number, payload_type, payload
FROM domain_event_entry
ORDER BY global_index;

# See the read model (projection)
SELECT * FROM account_summaries;
```

Notice that the `domain_event_entry` table is **append-only** — there are no UPDATE or DELETE operations. The balance in `account_summaries` is derived by replaying all events for each account.

---

## Running the Tests

### Prerequisites

- Docker must be running (Testcontainers starts a real PostgreSQL container for integration tests)

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests (no Docker required)

```bash
./mvnw test -Dtest="BankAccountAggregateTest"
```

### Run only integration tests

```bash
./mvnw test -Dtest="BankAccountIntegrationTest"
```

### Test structure

```
src/test/java/com/example/eventsourcing/
├── command/
│   └── BankAccountAggregateTest.java       # Unit tests (no Spring, no DB)
└── integration/
    └── BankAccountIntegrationTest.java     # Full integration tests (Testcontainers)
```

#### Unit tests (`BankAccountAggregateTest`)

Uses Axon's `AggregateTestFixture` — no Spring context, no database, sub-millisecond startup.

Test groups:
- **OpenAccountCommand** — valid/invalid account opening
- **DepositMoneyCommand** — valid deposits, closed-account rejection
- **WithdrawMoneyCommand** — valid withdrawals, overdraft rejection, closed-account rejection
- **CloseAccountCommand** — valid closure, double-close rejection
- **Event Sourcing reconstruction** — verifies that replaying events correctly rebuilds state

#### Integration tests (`BankAccountIntegrationTest`)

Uses `@SpringBootTest` + `@Testcontainers` (real PostgreSQL via Docker).

Tests:
- REST API endpoints (201, 200, 404, 409 responses)
- Full command → event → projection pipeline
- Aggregate state reconstruction from the event store
- Balance correctness after multiple operations
- Concurrent command rejection (overdraft, double-close)

---

## Architecture

```
HTTP Request
    │
    ▼
AccountCommandController / AccountQueryController
    │                              │
    ▼                              ▼
CommandGateway               QueryGateway
    │                              │
    ▼                              ▼
BankAccountAggregate         AccountProjection
  @CommandHandler              @QueryHandler
    │                        (reads account_summaries)
    │ AggregateLifecycle.apply(event)
    ▼
EmbeddedEventStore
  │  Appends to domain_event_entry (PostgreSQL)
  │  Calls @EventSourcingHandler (updates in-memory state)
  │
  └─► AccountProjection
        @EventHandler
        (updates account_summaries table)
```

### Key design decisions

- **Embedded event store** — no Axon Server required; uses `JpaEventStorageEngine` backed by PostgreSQL
- **Subscribing event processors** — event handlers run synchronously on the command thread, ensuring immediate read-model consistency within a single process
- **Separate read/write models** — `BankAccountAggregate` owns the write side; `AccountSummary` is the read-side projection
- **No balance column** — the account balance is computed from events (stored in `MoneyDepositedEvent.balanceAfter` and `MoneyWithdrawnEvent.balanceAfter` for projection efficiency)
