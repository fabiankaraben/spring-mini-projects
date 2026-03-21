# Eureka Discovery Server

A Spring Boot application that sets up a **Spring Cloud Netflix Eureka** service registry. In a microservices architecture, the Eureka server acts as a central directory where services register themselves on startup and discover other services by logical name instead of hard-coded hostnames and ports.

---

## What is a Service Registry?

| Problem | Solution |
|---|---|
| Microservices need to call each other, but their IP addresses and ports change (containers, auto-scaling, restarts) | A service registry holds the current address of every running instance |
| Clients cannot hard-code `http://order-service:8081` because the port may vary | Clients ask the registry: *"where is order-service right now?"* |
| Detecting unhealthy instances | The registry tracks heartbeats and removes instances that stop responding |

**Netflix Eureka** solves all of the above:

1. **Registration** – each microservice sends a `POST /eureka/apps/{appId}` to this server when it starts.  
2. **Heartbeat** – clients send a heartbeat every 30 seconds to prove they are alive.  
3. **Discovery** – other clients call `GET /eureka/apps` to get a list of all healthy instances.  
4. **De-registration** – on graceful shutdown a client sends `DELETE /eureka/apps/{appId}/{instanceId}`.

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21 or newer |
| Maven | 3.9+ (or use the included Maven Wrapper) |
| Docker | 24+ with Docker Compose plugin (for running via Docker Compose) |
| Docker Desktop | 29+ supported (Testcontainers configured for API v1.44) |

---

## Project Structure

```
01-eureka-discovery-server/
├── src/
│   ├── main/
│   │   ├── java/com/example/eurekadiscoveryserver/
│   │   │   ├── EurekaDiscoveryServerApplication.java   # @EnableEurekaServer entry point
│   │   │   ├── controller/
│   │   │   │   └── RegistryController.java             # Custom JSON API over the registry
│   │   │   ├── model/
│   │   │   │   ├── ServiceInstance.java                # Domain value object (record)
│   │   │   │   └── RegistrationSummary.java            # Aggregated registry snapshot (record)
│   │   │   └── service/
│   │   │       └── RegistryQueryService.java           # Domain logic: filter, find, summarise
│   │   └── resources/
│   │       └── application.yml                         # Server configuration
│   └── test/
│       ├── java/com/example/eurekadiscoveryserver/
│       │   ├── model/
│       │   │   └── ServiceInstanceTest.java            # Unit tests for ServiceInstance
│       │   ├── service/
│       │   │   └── RegistryQueryServiceTest.java       # Unit tests for RegistryQueryService
│       │   └── integration/
│       │       └── EurekaServerIntegrationTest.java    # Full integration tests (Testcontainers)
│       └── resources/
│           ├── application.yml                         # Test-specific config (random port)
│           ├── docker-java.properties                  # Docker API v1.44 (Docker Desktop 29+)
│           └── testcontainers.properties               # Testcontainers Docker API config
├── Dockerfile                                          # Multi-stage Docker build
├── docker-compose.yml                                  # Production stack
├── pom.xml
└── mvnw / mvnw.cmd                                     # Maven Wrapper
```

---

## Running the Application

### Option 1: Maven Wrapper (local JDK required)

```bash
./mvnw spring-boot:run
```

The Eureka server starts on port **8761**.

### Option 2: Docker Compose (recommended — no local JDK needed)

Build the Docker image and start the server:

```bash
docker compose up --build
```

Run in the background (detached mode):

```bash
docker compose up --build -d
```

Stop and remove containers:

```bash
docker compose down
```

The Eureka server will be available at `http://localhost:8761` after the health check passes (approximately 30–45 seconds for the first start while Docker builds the image).

---

## Available Endpoints

Once the server is running, the following endpoints are available:

### Eureka Built-in Endpoints

| Endpoint | Description |
|---|---|
| `GET /` | Eureka web dashboard (HTML UI showing all registered services) |
| `GET /eureka/apps` | Full registry — all registered applications (XML or JSON) |
| `GET /eureka/apps/{appId}` | Instances for a specific application |

### Spring Boot Actuator

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Health status of the Eureka server |
| `GET /actuator/info` | Application information |

### Custom Registry API (JSON)

| Endpoint | Description |
|---|---|
| `GET /registry/summary` | Aggregated counts: applications, instances, healthy, unhealthy |
| `GET /registry/instances` | Flat list of all registered service instances |
| `GET /registry/instances/{appName}` | Instances for a specific service name |

---

## curl Examples

### Check server health

```bash
curl http://localhost:8761/actuator/health
```

Expected output when no services are registered:
```json
{
  "status": "UP",
  "components": {
    "diskSpace": { "status": "UP", ... },
    "ping": { "status": "UP" }
  }
}
```

### Get registry summary

```bash
curl http://localhost:8761/registry/summary
```

Output when no services are registered:
```json
{
  "totalApplications": 0,
  "totalInstances": 0,
  "healthyInstances": 0,
  "unhealthyInstances": 0,
  "fullyHealthy": false,
  "healthRatio": 0.0
}
```

### Get all registered instances

```bash
curl http://localhost:8761/registry/instances
```

Output when no services are registered:
```json
[]
```

### Get instances for a specific service

```bash
curl http://localhost:8761/registry/instances/ORDER-SERVICE
```

Output if `ORDER-SERVICE` has two instances running:
```json
[
  {
    "appName": "ORDER-SERVICE",
    "instanceId": "order-host-1:order-service:8081",
    "hostName": "order-host-1",
    "port": 8081,
    "status": "UP"
  },
  {
    "appName": "ORDER-SERVICE",
    "instanceId": "order-host-2:order-service:8081",
    "hostName": "order-host-2",
    "port": 8081,
    "status": "UP"
  }
]
```

### Query the Eureka REST API directly (all apps, JSON format)

```bash
curl -H "Accept: application/json" http://localhost:8761/eureka/apps
```

### View the Eureka dashboard

Open in a browser: [http://localhost:8761/](http://localhost:8761/)

---

## Registering a Client Service (How other services use this server)

Any Spring Boot service with the `spring-cloud-starter-netflix-eureka-client` dependency can register with this server by adding to its `application.yml`:

```yaml
spring:
  application:
    name: my-service   # The name shown in the Eureka dashboard

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

When running via Docker Compose, other services on the same Docker network use the service name as the hostname:

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
```

---

## Running the Tests

### Run all tests (unit + integration)

```bash
./mvnw clean test
```

### Test categories

| Test class | Type | What it tests |
|---|---|---|
| `ServiceInstanceTest` | Unit | Domain record methods: `isHealthy()`, `baseUrl()`, `up()` factory, equality |
| `RegistryQueryServiceTest` | Unit | Service logic: `filterHealthy()`, `filterUnhealthy()`, `findByAppName()`, `buildSummary()` |
| `EurekaServerIntegrationTest` | Integration (Testcontainers) | Full Spring Boot context starts; HTTP requests verify Eureka endpoints, Actuator, and custom API |

### What the integration tests verify

The `EurekaServerIntegrationTest` starts the complete Spring Boot application on a random port (no hard-coded ports to avoid CI conflicts) and verifies:

- The Eureka web dashboard responds at `/`
- The Eureka REST API responds at `/eureka/apps`
- The Actuator health endpoint reports `UP`
- `GET /registry/summary` returns HTTP 200 with a valid JSON object
- `GET /registry/summary` reports all-zero counts when no services are registered
- `GET /registry/instances` returns an empty JSON array when no services are registered
- `GET /registry/instances/{appName}` returns an empty JSON array for unknown service names

### Testcontainers and Docker

The integration tests use **Testcontainers** (with `@Testcontainers`) for its JUnit 5 lifecycle management. The Eureka server itself has no external Docker dependencies (no database, no message broker), so Docker is not strictly required for the tests. However, Docker must be running because Testcontainers validates the Docker environment at test startup.

**Docker must be running before executing `./mvnw clean test`.**

---

## Docker Compose Details

The `docker-compose.yml` defines a single service:

| Service | Image | Port | Description |
|---|---|---|---|
| `eureka-server` | Built from `Dockerfile` | `8761:8761` | Spring Boot Eureka Discovery Server |

The `Dockerfile` uses a **multi-stage build**:

1. **Build stage** (`eclipse-temurin:21-jdk-alpine`): downloads dependencies and compiles the fat-jar with Maven.  
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`): copies only the compiled jar into a minimal JRE image.

This keeps the final Docker image small (JRE only, no JDK or Maven in the production image).

---

## Key Concepts

### Self-Preservation Mode

Eureka has a built-in protection against mass de-registration caused by network partitions (not actual service failures). When it detects that too many heartbeats are missing, it stops evicting instances — this is "self-preservation".

- **Disabled** in this project's configuration (`eureka.server.enable-self-preservation: false`) so stale entries are removed quickly during development.
- **Enable it** in a real production cluster to avoid wiping healthy registrations during brief network issues.

### Standalone vs. Clustered Mode

This project runs Eureka in **standalone (single-server) mode**:

```yaml
eureka:
  client:
    register-with-eureka: false   # Do not register this server as a client
    fetch-registry: false         # Do not fetch registry from peers
```

In production, you would typically run **3 Eureka server instances** in a peer-aware cluster. Each server registers with the others, providing high availability. Client applications list all peer URLs in `eureka.client.serviceUrl.defaultZone`.

### Heartbeat and Eviction

- Clients send a heartbeat every **30 seconds** (configurable via `eureka.instance.lease-renewal-interval-in-seconds`).
- The server evicts an instance if it misses heartbeats for **90 seconds** by default.
- In this project, the eviction interval is reduced to **5 seconds** for faster feedback during development.
