# Actuator Info Endpoint Mini-Project

🔹 **Actuator Info Endpoint** is a Spring Boot application that exposes standard and custom info metrics utilizing Spring Boot Actuator capabilities.

## Requirements

- **Java Version:** 21+
- **Dependency Manager:** Maven
- **Core Dependencies:** Spring Web, Spring Boot Actuator
- **Testing:** Unit tests using JUnit 5 and Mockito, coupled with standard `@SpringBootTest` and `@WebMvcTest` Integration Test slices.

## Features

- Fully configures the standard Actuator `info` endpoint parameters via `application.properties`.
- Automatically exposes host OS details and environment Java properties natively leveraging `management.info.java.enabled` and `management.info.os.enabled`.
- Injects a **Custom InfoContributor** (`CustomInfoContributor.java`) that programmatically attaches unique business metric details (like user count metrics, toggles, or infrastructure conditions) gracefully appended into the server payload.

## How to use

First, build and run the application utilizing the included Maven wrapper within the miniature project root directory:

```bash
./mvnw spring-boot:run
```

Wait until the web application finishes starting and actively monitors on standard TCP Port 8080.
You can query the actuator `info` endpoint natively utilizing `curl`:

**Check Actuator Info Properties**
```bash
curl -s http://localhost:8080/actuator/info
```

**Expected JSON Result Output Layout:**
```json
{
  "app": {
    "name": "actuator-info-endpoint",
    "description": "A Spring Boot application demonstrating standard and custom info metrics via Actuator",
    "version": "1.0.0",
    "environment": "development",
    "team": "backend-team"
  },
  "custom": {
    "maintenance-mode": false,
    "active-users": 42,
    "status": "System is running optimally"
  },
  "java": {
    "version": "21.x.x",
    "vendor": "Vendor Information",
    ...
  },
  "os": { 
     "name": "OS Name",
     "version": "OS Version",
     "arch": "OS Architecture"
  }
}
```

*Note: Accessing the root endpoint (`http://localhost:8080/`) will automatically issue an HTTP 302 Redirection mechanism directly to the `/actuator/info` route for convenience.*

## How to run the tests

Verify functionalities executing the complete pre-packaged validation suite encompassing sliced test configurations and offline Spring Contexts using Maven capabilities:

```bash
./mvnw test
```

This command directly targets both the standard isolated JUnit Controller evaluation (`HomeControllerTest`) via `MockMvc` frameworks, alongside executing broader configurations mapping into Custom Info Properties logic injections natively verified against deep HTTP parsing mechanics without networking overheads.
