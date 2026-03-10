# Vault Secrets

A Spring Boot backend that stores and retrieves credentials securely using **Spring Cloud Vault** and **HashiCorp Vault**.

This mini-project demonstrates a practical secret management pattern: sensitive values (passwords, API keys, tokens) are stored exclusively in Vault, while only non-sensitive metadata (name, Vault path, description) is persisted in a relational database.

---

## What This Project Does

- **Stores secrets** in HashiCorp Vault's KV v2 secrets engine via a REST API
- **Retrieves secrets** from Vault on demand (never from the database)
- **Tracks credential metadata** (name, path, description) in an H2 in-memory database
- **Prevents sensitive data leakage**: secret values never appear in metadata responses or database rows

---

## Key Concepts

| Concept | Description |
|---|---|
| **Spring Cloud Vault** | Integrates Spring Boot with HashiCorp Vault; fetches secrets at startup and allows programmatic read/write |
| **KV v2 secrets engine** | Vault's key-value store that supports versioning; stores arbitrary key-value pairs |
| **Token authentication** | Simplest Vault auth method; the app presents a static token on every Vault request |
| **VaultTemplate** | Spring Vault's primary API for programmatic Vault interaction (analogous to `JdbcTemplate`) |
| **Metadata separation** | Only the Vault path is stored in the database; actual secret values live exclusively in Vault |
| **Spring Data JPA + H2** | Stores credential metadata in an in-memory relational database |

---

## Requirements

- **Java 21+**
- **Maven** (or use the included `./mvnw` wrapper — no separate Maven installation needed)
- **Docker** and **Docker Compose** (for running Vault + the application)

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/vaultsecrets/
│   │   ├── VaultSecretsApplication.java          # Spring Boot entry point
│   │   ├── controller/
│   │   │   └── CredentialController.java          # REST API endpoints
│   │   ├── service/
│   │   │   └── CredentialService.java             # Business logic
│   │   ├── vault/
│   │   │   └── VaultOperationsService.java        # Low-level Vault read/write
│   │   ├── domain/
│   │   │   └── CredentialEntry.java               # JPA entity (metadata only)
│   │   ├── repository/
│   │   │   └── CredentialEntryRepository.java     # Spring Data JPA repository
│   │   └── dto/
│   │       ├── StoreSecretRequest.java             # Request DTO
│   │       ├── CredentialEntryResponse.java        # Response DTO (no secret values)
│   └── resources/
│       └── application.yml                        # App + Vault + H2 configuration
└── test/
    ├── java/com/example/vaultsecrets/
    │   ├── domain/CredentialEntryTest.java         # Unit tests: domain model
    │   ├── service/CredentialServiceTest.java      # Unit tests: business logic (Mockito)
    │   └── integration/VaultSecretsIntegrationTest.java  # Integration tests (Testcontainers)
    └── resources/
        ├── docker-java.properties                  # Testcontainers Docker API fix
        └── testcontainers.properties               # Testcontainers Docker API fix
```

---

## Running with Docker Compose

The entire application stack (Vault + Spring Boot app) is managed by Docker Compose.

### 1. Start all services

```bash
docker compose up --build
```

This starts:
- **Vault** on port `8200` (dev mode, pre-unsealed, KV v2 ready)
- **Spring Boot app** on port `8080` (waits for Vault to be healthy)

### 2. Stop all services

```bash
docker compose down
```

### 3. Rebuild after code changes

```bash
docker compose up --build --force-recreate
```

---

## REST API

### Base URL

```
http://localhost:8080/api/credentials
```

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/credentials` | Store a new secret in Vault + register metadata |
| `GET` | `/api/credentials` | List all credential metadata (no secret values) |
| `GET` | `/api/credentials/{name}` | Get metadata for a specific credential |
| `GET` | `/api/credentials/{name}/secret` | Retrieve the actual secret from Vault |
| `DELETE` | `/api/credentials/{name}` | Delete credential from Vault + metadata table |

---

## curl Examples

### Store a new secret

```bash
curl -s -X POST http://localhost:8080/api/credentials \
  -H "Content-Type: application/json" \
  -d '{
    "name": "prod-db",
    "vaultPath": "myapp/db",
    "description": "Production database credentials",
    "secretData": {
      "username": "admin",
      "password": "super-secret-pass"
    }
  }' | jq
```

**Response (201 Created)** — note: no secret values in the response:
```json
{
  "id": 1,
  "name": "prod-db",
  "vaultPath": "myapp/db",
  "description": "Production database credentials",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

---

### Store an API key

```bash
curl -s -X POST http://localhost:8080/api/credentials \
  -H "Content-Type: application/json" \
  -d '{
    "name": "payment-api",
    "vaultPath": "myapp/payment",
    "description": "Payment gateway API credentials",
    "secretData": {
      "apiKey": "pk_live_abc123xyz",
      "webhookSecret": "whsec_def456"
    }
  }' | jq
```

---

### List all credentials (metadata only)

```bash
curl -s http://localhost:8080/api/credentials | jq
```

**Response (200 OK)**:
```json
[
  {
    "id": 1,
    "name": "prod-db",
    "vaultPath": "myapp/db",
    "description": "Production database credentials",
    "createdAt": "2024-01-15T10:30:00Z"
  },
  {
    "id": 2,
    "name": "payment-api",
    "vaultPath": "myapp/payment",
    "description": "Payment gateway API credentials",
    "createdAt": "2024-01-15T10:31:00Z"
  }
]
```

---

### Get metadata for a specific credential

```bash
curl -s http://localhost:8080/api/credentials/prod-db | jq
```

**Response (200 OK)**:
```json
{
  "id": 1,
  "name": "prod-db",
  "vaultPath": "myapp/db",
  "description": "Production database credentials",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

---

### Retrieve the actual secret from Vault

```bash
curl -s http://localhost:8080/api/credentials/prod-db/secret | jq
```

**Response (200 OK)** — this is the only endpoint that returns actual secret values:
```json
{
  "username": "admin",
  "password": "super-secret-pass"
}
```

---

### Retrieve an API key secret

```bash
curl -s http://localhost:8080/api/credentials/payment-api/secret | jq
```

---

### Delete a credential

```bash
curl -s -X DELETE http://localhost:8080/api/credentials/prod-db
```

**Response: 204 No Content**

---

### Error cases

**404 Not Found** — credential not registered:
```bash
curl -s http://localhost:8080/api/credentials/nonexistent | jq
# Returns: 404 Not Found

curl -s http://localhost:8080/api/credentials/nonexistent/secret | jq
# Returns: {"error": "No credential registered with name: nonexistent"}
```

**409 Conflict** — duplicate name:
```bash
# Try to store "prod-db" again after it was already stored:
curl -s -X POST http://localhost:8080/api/credentials \
  -H "Content-Type: application/json" \
  -d '{"name":"prod-db","vaultPath":"other/path","secretData":{"k":"v"}}' | jq
# Returns: {"error": "A credential with name 'prod-db' already exists."}
```

**400 Bad Request** — missing required fields:
```bash
curl -s -X POST http://localhost:8080/api/credentials \
  -H "Content-Type: application/json" \
  -d '{"name":"","vaultPath":"myapp/db","secretData":{"k":"v"}}' | jq
# Returns: 400 Bad Request (validation error)
```

---

## Accessing the Vault UI

HashiCorp Vault ships with a built-in web UI. When running via Docker Compose:

1. Open: [http://localhost:8200](http://localhost:8200)
2. Select **Token** as the auth method
3. Enter token: `root-token`
4. Navigate to **Secrets** → **secret** to browse stored KV v2 secrets

---

## Accessing the H2 Console

The H2 in-memory database console is available for inspecting credential metadata:

1. Open: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
2. JDBC URL: `jdbc:h2:mem:vaultsecrets`
3. Username: `sa`, Password: *(empty)*

> **Note**: The H2 console is only available when running the app locally (not via Docker Compose, as the H2 console requires direct access to the JVM process).

---

## Running Tests

### Run all tests

```bash
./mvnw clean test
```

### Test suite overview

| Test class | Type | What it tests |
|---|---|---|
| `CredentialEntryTest` | Unit (pure JUnit 5) | Domain model: constructor, setters, `@PrePersist` lifecycle |
| `CredentialServiceTest` | Unit (Mockito) | Business logic: storeSecret, listCredentials, getSecretByName, deleteCredential |
| `VaultSecretsIntegrationTest` | Integration (Testcontainers) | Full HTTP round-trip against a real Vault container |

### Unit tests

Unit tests use **Mockito** to replace Vault and the database with mocks. They run in milliseconds — no Docker required:

```bash
./mvnw test -Dtest="CredentialEntryTest,CredentialServiceTest"
```

### Integration tests

Integration tests use **Testcontainers** to spin up a real HashiCorp Vault Docker container (`hashicorp/vault:1.17`) automatically. The container starts in dev mode (pre-unsealed, KV v2 ready):

```bash
./mvnw test -Dtest="VaultSecretsIntegrationTest"
```

> **Requirements**: Docker must be running for integration tests.

The integration tests cover:
- Storing secrets and verifying they appear in metadata listings
- Retrieving actual secret values from Vault
- Validation errors (blank name, empty secretData)
- Duplicate name conflict (409)
- Not-found cases (404)
- Full round-trip: store → list → get metadata → retrieve secret → delete → verify gone

---

## Architecture Overview

```
HTTP Client
     │
     ▼
CredentialController  (REST layer)
     │
     ▼
CredentialService  (business logic)
     │         │
     ▼         ▼
VaultOperationsService    CredentialEntryRepository
(Spring VaultTemplate)    (Spring Data JPA)
     │                          │
     ▼                          ▼
HashiCorp Vault             H2 Database
(secret values)          (metadata only)
```

### Security model

- **Vault** is the single source of truth for sensitive values
- The **database** never stores secret values — only the Vault path and description
- The `/secret` endpoint retrieves values from Vault at query time, not from the database
- All Vault communication uses token authentication (suitable for dev; use AppRole/K8s in production)
