# Eureka Discovery Client

A Spring Boot microservice that registers itself with a **Netflix Eureka Discovery Server** for dynamic service discovery. It exposes a sample product catalogue API and a set of discovery-introspection endpoints, demonstrating the full lifecycle of a Eureka client: registration, heartbeat, registry fetching, and graceful de-registration on shutdown.

---

## Table of Contents

- [What This Project Demonstrates](#what-this-project-demonstrates)
- [Requirements](#requirements)
- [Project Structure](#project-structure)
- [Running with Docker Compose](#running-with-docker-compose)
- [Running Locally (without Docker)](#running-locally-without-docker)
- [REST API Reference](#rest-api-reference)
  - [Discovery Client Endpoints](#discovery-client-endpoints)
  - [Product Catalogue Endpoints](#product-catalogue-endpoints)
  - [Actuator Endpoints](#actuator-endpoints)
- [curl Examples](#curl-examples)
- [Running the Tests](#running-the-tests)
- [Architecture Overview](#architecture-overview)

---

## What This Project Demonstrates

| Concept | Where to look |
|---|---|
| `@EnableDiscoveryClient` activation | `EurekaDiscoveryClientApplication.java` |
| Automatic Eureka registration on startup | `application.yml` → `eureka.client` section |
| Querying the local registry cache via `DiscoveryClient` | `DiscoveryClientController.java` |
| Domain-layer separation (no Eureka types in service) | `DiscoveryQueryService.java` |
| Dynamic property injection for Testcontainers | `EurekaClientIntegrationTest.java` |
| Client-side service discovery pattern | `GET /client/services` endpoint |

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21 or higher |
| Maven | 3.9+ (or use the included Maven Wrapper `./mvnw`) |
| Docker | Required for Docker Compose and integration tests |
| Docker Compose | v2 (uses `docker compose`, not `docker-compose`) |

> **Note:** The integration tests use **Testcontainers** to spin up a Eureka server in Docker automatically. Docker must be running when you execute `./mvnw clean test`.

---

## Project Structure

```
02-eureka-discovery-client/
├── src/
│   ├── main/
│   │   ├── java/com/example/eurekadiscoveryclient/
│   │   │   ├── EurekaDiscoveryClientApplication.java   # Main entry point, @EnableDiscoveryClient
│   │   │   ├── controller/
│   │   │   │   └── DiscoveryClientController.java      # REST endpoints (discovery + products)
│   │   │   ├── model/
│   │   │   │   ├── ServiceInfo.java                    # Domain record: discovered service instance
│   │   │   │   └── RegistrationStatus.java             # Domain record: client registration state
│   │   │   └── service/
│   │   │       └── DiscoveryQueryService.java          # Pure domain logic for querying registry data
│   │   └── resources/
│   │       └── application.yml                         # Spring Boot + Eureka client configuration
│   └── test/
│       ├── java/com/example/eurekadiscoveryclient/
│       │   ├── model/
│       │   │   ├── ServiceInfoTest.java                # Unit tests for ServiceInfo record
│       │   │   └── RegistrationStatusTest.java         # Unit tests for RegistrationStatus record
│       │   ├── service/
│       │   │   └── DiscoveryQueryServiceTest.java      # Unit tests for DiscoveryQueryService
│       │   └── integration/
│       │       └── EurekaClientIntegrationTest.java    # Full integration tests (Testcontainers)
│       └── resources/
│           ├── application.yml                         # Test overrides (Eureka disabled by default)
│           ├── docker-java.properties                  # Docker API version fix for Docker Desktop 29+
│           └── testcontainers.properties               # Testcontainers Docker API configuration
├── Dockerfile                                          # Multi-stage build (JDK build → JRE runtime)
├── docker-compose.yml                                  # Full stack: eureka-server + product-service
├── pom.xml
└── README.md
```

---

## Running with Docker Compose

This is the **recommended way** to run the complete stack. Docker Compose starts both the Eureka server and the product-service (this client) together.

### 1. Build and start all services

```bash
docker compose up --build
```

Or in detached (background) mode:

```bash
docker compose up --build -d
```

### 2. Wait for services to become healthy

The `product-service` waits for the `eureka-server` health check to pass before starting, so startup order is handled automatically.

You can watch the logs:

```bash
docker compose logs -f
```

### 3. Verify the product-service is registered in Eureka

Open the Eureka Dashboard in your browser:

```
http://localhost:8761/
```

You should see **PRODUCT-SERVICE** listed under "Instances currently registered with Eureka".

### 4. Stop all services

```bash
docker compose down
```

On graceful shutdown, the product-service sends a DELETE request to the Eureka server to de-register itself immediately.

---

## Running Locally (without Docker)

If you want to run the service locally against a separately running Eureka server:

### 1. Start a Eureka server

You can use the companion `01-eureka-discovery-server` project, or run:

```bash
docker run -p 8761:8761 steeltoeoss/eureka-server:latest
```

### 2. Start this service

```bash
./mvnw spring-boot:run
```

The service registers with `http://localhost:8761/eureka/` by default.

---

## REST API Reference

### Discovery Client Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/client/status` | Registration status of this client instance |
| `GET` | `/client/services` | All services in the local registry cache (sorted by service ID) |
| `GET` | `/client/services/{serviceId}` | Instances of a specific service |

### Product Catalogue Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/products` | Returns the full product list |
| `GET` | `/products/{id}` | Returns a single product by ID (404 if not found) |

### Actuator Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/actuator/health` | Application health status (used by Eureka) |
| `GET` | `/actuator/info` | Application info |

---

## curl Examples

All examples assume the service is running on `http://localhost:8080`.

### Check the client's Eureka registration status

```bash
curl -s http://localhost:8080/client/status | jq .
```

Expected response (when fully registered):
```json
{
  "applicationName": "product-service",
  "registeredServices": 1,
  "registrationEnabled": true,
  "fetchEnabled": true
}
```

### List all services in the local registry cache

```bash
curl -s http://localhost:8080/client/services | jq .
```

Expected response (after registration):
```json
[
  {
    "serviceId": "PRODUCT-SERVICE",
    "instanceId": "product-service:product-service:8080",
    "host": "product-service",
    "port": 8080,
    "uri": "http://product-service:8080"
  }
]
```

### Look up instances of a specific service

```bash
curl -s http://localhost:8080/client/services/product-service | jq .
```

### Get all products

```bash
curl -s http://localhost:8080/products | jq .
```

Expected response:
```json
[
  {"id": 1, "name": "Laptop",    "price": 999.99},
  {"id": 2, "name": "Mouse",     "price": 29.99},
  {"id": 3, "name": "Monitor",   "price": 349.99},
  {"id": 4, "name": "Keyboard",  "price": 79.99},
  {"id": 5, "name": "USB-C Hub", "price": 49.99}
]
```

### Get a single product by ID

```bash
curl -s http://localhost:8080/products/1 | jq .
```

Expected response:
```json
{"id": 1, "name": "Laptop", "price": 999.99}
```

### Get a non-existent product (404)

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/products/999
# Output: 404
```

### Check the actuator health endpoint

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

Expected response:
```json
{
  "status": "UP",
  "components": { ... }
}
```

---

## Running the Tests

> **Prerequisites:** Docker must be running. The integration tests use **Testcontainers** to automatically start a real Eureka server in a Docker container.

### Run all tests (unit + integration)

```bash
./mvnw clean test
```

### What gets tested

| Test class | Type | What it covers |
|---|---|---|
| `ServiceInfoTest` | Unit | `ServiceInfo` record: factory method, `displayLabel()`, equality |
| `RegistrationStatusTest` | Unit | `RegistrationStatus` record: `isFullyActive()`, `hasDiscoveredServices()` |
| `DiscoveryQueryServiceTest` | Unit | `DiscoveryQueryService`: `findByServiceId()`, `countDistinctServices()`, `buildStatus()`, `sortByServiceId()` — all edge cases |
| `EurekaClientIntegrationTest` | Integration | Full Spring Boot context + Testcontainers Eureka server: all REST endpoints, actuator health, 404 handling |

### How the integration test works

```
┌─────────────────────────────────────────────────────────────────┐
│  JUnit 5 + Testcontainers                                        │
│                                                                  │
│  1. @Container starts steeltoeoss/eureka-server in Docker        │
│  2. @DynamicPropertySource points the Spring context at it       │
│  3. @SpringBootTest starts the full application context          │
│  4. TestRestTemplate sends real HTTP requests to the running app │
│  5. Assertions verify status codes and JSON response bodies      │
└─────────────────────────────────────────────────────────────────┘
```

### Testcontainers + Docker Desktop 29+ notes

The test classpath includes two configuration files that fix compatibility with Docker Desktop 29+:

- `src/test/resources/docker-java.properties` — sets `api.version=1.44` (Docker Desktop 29+ rejects the legacy v1.24 API)
- `src/test/resources/testcontainers.properties` — sets `DOCKER_API_VERSION=1.44`

These files are automatically picked up by the Testcontainers and docker-java libraries at test runtime.

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│  Docker Compose Stack                                         │
│                                                               │
│  ┌─────────────────────┐      registers/heartbeat            │
│  │   product-service   │ ──────────────────────────────────► │
│  │  (port 8080)        │                                      │
│  │                     │ ◄──────────────────────────────────  │
│  │  /products          │      registry cache fetch            │
│  │  /client/status     │                                      │
│  │  /client/services   │      ┌─────────────────────────┐    │
│  └─────────────────────┘      │   eureka-server          │    │
│                                │   (port 8761)            │    │
│                                │                          │    │
│                                │   Dashboard: /           │    │
│                                │   REST API:  /eureka/    │    │
│                                └─────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

### Eureka client lifecycle

1. **Startup** — the client sends `POST /eureka/apps/PRODUCT-SERVICE` to register itself, including its hostname, port, health-check URL and instance ID.
2. **Heartbeat** — every 10 seconds (configurable), the client sends `PUT /eureka/apps/PRODUCT-SERVICE/{instanceId}` to renew its lease.
3. **Registry cache** — every 10 seconds, the client fetches the full registry from the server and caches it locally. Calls to `DiscoveryClient.getServices()` use this cache — they do NOT reach out to the server on every request.
4. **Shutdown** — on graceful stop (`SIGTERM`), the client sends `DELETE /eureka/apps/PRODUCT-SERVICE/{instanceId}` to de-register immediately.

### Domain model

```
ServiceInfo            ← snapshot of a discovered service instance
RegistrationStatus     ← summary of this client's registration state
DiscoveryQueryService  ← pure domain logic (no Spring/Eureka types)
DiscoveryClientController ← maps DiscoveryClient → domain objects → JSON
```
