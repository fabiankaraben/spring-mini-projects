# 19 — Keycloak Identity

A Spring Boot backend that delegates user authentication **entirely** to a [Keycloak](https://www.keycloak.org/) SSO (Single Sign-On) server. The backend acts as an **OAuth2 Resource Server**, accepting and validating JWT access tokens issued by Keycloak. It never manages passwords or user sessions itself.

---

## What This Project Demonstrates

- **Keycloak as the sole authentication authority** — the Spring Boot app has no login form, no password storage, no session management
- **Spring Security OAuth2 Resource Server** configured to validate Keycloak-issued JWTs
- **Custom `KeycloakJwtAuthoritiesConverter`** that reads Keycloak's specific `realm_access.roles` JWT claim structure
- **Role-based access control** using Keycloak realm roles (`USER`, `ADMIN`) mapped to Spring Security authorities
- **JWT claims extraction** — reading `sub`, `preferred_username`, `email`, and `name` directly from Keycloak tokens
- **`/api/users/me` pattern** — looking up the caller's application profile by matching the JWT's `sub` claim to a stored Keycloak UUID
- **In-memory user repository** — keeps focus on Keycloak integration, not database concerns
- **Full test suite** — JUnit 5 unit tests + Testcontainers integration tests with a real Keycloak container

---

## Architecture

```
[Client App / curl]
        │
        │  1. POST /realms/demo-realm/protocol/openid-connect/token
        │     (username + password + client_id + client_secret)
        ▼
[Keycloak SSO Server :9080]
        │
        │  2. Returns signed JWT access token
        │     { "sub": "uuid", "realm_access": {"roles": ["USER"]}, ... }
        ▼
[Client App / curl]
        │
        │  3. GET /api/users   Authorization: Bearer <jwt>
        ▼
[Spring Boot App :8082]
        │
        │  4. BearerTokenAuthenticationFilter extracts the JWT
        │  5. NimbusJwtDecoder fetches Keycloak's RSA public keys
        │     from /realms/demo-realm/protocol/openid-connect/certs
        │  6. Verifies JWT signature + validates exp/iss claims
        │  7. KeycloakJwtAuthoritiesConverter maps realm_access.roles
        │     → Spring Security authorities (ROLE_USER, ROLE_ADMIN)
        │  8. SecurityFilterChain checks the required role
        ▼
[Protected Resource: Users API]
```

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (or use the included `./mvnw` wrapper) |
| Docker | 24+ with Docker Compose v2 |
| Docker Desktop | 4.x+ |

---

## Project Structure

```
19-keycloak-identity/
├── keycloak/
│   └── demo-realm.json          # Keycloak realm config for Docker Compose
├── src/
│   ├── main/
│   │   ├── java/com/example/keycloakidentity/
│   │   │   ├── KeycloakIdentityApplication.java     # Spring Boot entry point
│   │   │   ├── config/
│   │   │   │   └── SecurityConfig.java              # OAuth2 Resource Server + authorization rules
│   │   │   ├── security/
│   │   │   │   └── KeycloakJwtAuthoritiesConverter.java  # Reads realm_access.roles from Keycloak JWTs
│   │   │   ├── domain/
│   │   │   │   └── User.java                        # Application user domain model
│   │   │   ├── dto/
│   │   │   │   ├── CreateUserRequest.java           # Validated create request DTO
│   │   │   │   └── UpdateUserRequest.java           # Partial update request DTO
│   │   │   ├── repository/
│   │   │   │   └── UserRepository.java              # In-memory ConcurrentHashMap store
│   │   │   ├── service/
│   │   │   │   └── UserService.java                 # Business logic layer
│   │   │   └── controller/
│   │   │       ├── UserController.java              # Protected /api/users endpoints
│   │   │       └── PublicController.java            # Public /api/public/info endpoint
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/com/example/keycloakidentity/
│       │   ├── security/
│       │   │   └── KeycloakJwtAuthoritiesConverterTest.java  # Unit tests
│       │   ├── service/
│       │   │   └── UserServiceTest.java                      # Unit tests
│       │   └── integration/
│       │       └── KeycloakIdentityIntegrationTest.java      # Integration tests (Keycloak Testcontainer)
│       └── resources/
│           ├── application.yml
│           ├── docker-java.properties
│           ├── testcontainers.properties
│           └── keycloak/
│               └── demo-realm.json                  # Realm config for Testcontainers
├── Dockerfile
├── docker-compose.yml
├── mvnw / mvnw.cmd
└── pom.xml
```

---

## Running with Docker Compose

This is the recommended way to run the full stack (Keycloak + Spring Boot app together).

### 1. Start the stack

```bash
docker compose up --build
```

This will:
1. Start the **Keycloak** container on port `9080` and import `keycloak/demo-realm.json`
2. Wait for Keycloak to be healthy (may take ~30-60 seconds on first run)
3. Build and start the **Spring Boot app** on port `8082`

### 2. Verify the stack is running

```bash
# Check the app health
curl http://localhost:8082/actuator/health

# Check public API info (shows Keycloak connection details)
curl http://localhost:8082/api/public/info
```

### 3. Obtain a JWT from Keycloak

**As Alice (ADMIN + USER roles):**

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:9080/realms/demo-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=demo-client" \
  -d "client_secret=demo-client-secret" \
  -d "username=alice-admin" \
  -d "password=alice-password" \
  | jq -r '.access_token')

echo $TOKEN
```

**As Bob (USER role only):**

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:9080/realms/demo-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=demo-client" \
  -d "client_secret=demo-client-secret" \
  -d "username=bob-user" \
  -d "password=bob-password" \
  | jq -r '.access_token')
```

### 4. Stop the stack

```bash
docker compose down
```

---

## API Endpoints and curl Examples

All protected endpoints require `Authorization: Bearer <token>`. Obtain a token first (see above).

### Public endpoints (no token required)

```bash
# Service info and Keycloak configuration
curl http://localhost:8082/api/public/info

# Health check
curl http://localhost:8082/actuator/health
```

### Current user profile — any authenticated user

```bash
# Returns your Keycloak identity + application profile (if registered)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/users/me
```

### List users — USER or ADMIN role

```bash
# List all users
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/users

# Filter by role
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8082/api/users?role=ADMIN"
```

### Get user by ID — USER or ADMIN role

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/users/1
```

### Create user — ADMIN role required

```bash
# Must use Alice's token (ADMIN role)
curl -s -X POST http://localhost:8082/api/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Frank New",
    "email": "frank@example.com",
    "role": "USER",
    "keycloakId": "some-keycloak-uuid"
  }'
```

### Update user — ADMIN role required

```bash
curl -s -X PUT http://localhost:8082/api/users/2 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Bob Updated",
    "active": false
  }'
```

### Delete user — ADMIN role required

```bash
curl -s -X DELETE http://localhost:8082/api/users/5 \
  -H "Authorization: Bearer $TOKEN"
# Returns HTTP 204 No Content on success
```

### Access control demonstration

```bash
# Get Bob's token (USER role only)
BOB_TOKEN=$(curl -s -X POST \
  http://localhost:9080/realms/demo-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=demo-client&client_secret=demo-client-secret&username=bob-user&password=bob-password" \
  | jq -r '.access_token')

# Bob CAN read users (HTTP 200)
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  http://localhost:8082/api/users
# → 200

# Bob CANNOT create users (HTTP 403)
curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8082/api/users \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"displayName":"X","email":"x@x.com","role":"USER"}'
# → 403

# No token at all (HTTP 401)
curl -s -o /dev/null -w "%{http_code}" \
  http://localhost:8082/api/users
# → 401
```

---

## Keycloak Admin Console

When running via Docker Compose, the Keycloak admin console is available at:

```
http://localhost:9080
Username: admin
Password: admin
```

From the admin console you can:
- Browse the `demo-realm` realm
- Inspect the `alice-admin` and `bob-user` test users
- View the `USER` and `ADMIN` realm roles
- Add new users and assign roles
- Configure the `demo-client` OIDC client

---

## How to Run the Tests

Tests use **Testcontainers** — Docker must be running before executing the test suite.

```bash
./mvnw clean test
```

### Test categories

| Test class | Type | What it tests |
|---|---|---|
| `KeycloakJwtAuthoritiesConverterTest` | Unit | JWT claims → Spring authorities conversion logic |
| `UserServiceTest` | Unit | Service business logic, DTO mapping, partial updates |
| `KeycloakIdentityIntegrationTest` | Integration | Full HTTP stack: auth rules, 401/403/200 responses, CRUD |

### Integration test details

The `KeycloakIdentityIntegrationTest` starts a **real Keycloak container** via `testcontainers-keycloak`. The container:
- Uses image `quay.io/keycloak/keycloak:26.0`
- Imports `src/test/resources/keycloak/demo-realm.json` on startup
- Creates the `demo-realm` with `USER`/`ADMIN` roles and two test users

The Spring context's `jwk-set-uri` and `issuer-uri` are dynamically pointed at the container via `@DynamicPropertySource`.

JWT injection in tests uses Spring Security Test's `jwt()` MockMvc post-processor — this injects a fake `JwtAuthenticationToken` into the security context, bypassing real JWT signature verification and making tests fast and deterministic.

> **Note:** The first test run downloads the Keycloak Docker image (~500 MB). Subsequent runs use the cached image and start in ~15-20 seconds.

---

## Keycloak JWT Structure

A token issued by Keycloak for this realm looks like:

```json
{
  "sub": "user-keycloak-uuid",
  "iss": "http://localhost:9080/realms/demo-realm",
  "preferred_username": "alice-admin",
  "email": "alice@example.com",
  "name": "Alice Admin",
  "realm_access": {
    "roles": ["USER", "ADMIN", "offline_access", "uma_authorization"]
  },
  "scope": "openid email profile",
  "exp": 1700000000
}
```

The `KeycloakJwtAuthoritiesConverter` maps `realm_access.roles` to Spring Security authorities:
- `"USER"` → `ROLE_USER`
- `"ADMIN"` → `ROLE_ADMIN`

This enables `hasRole("ADMIN")` checks in both the security filter chain and `@PreAuthorize` annotations.

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-oauth2-resource-server` | JWT validation via Keycloak JWKS URI |
| `spring-boot-starter-web` | REST controllers |
| `spring-boot-starter-validation` | Bean Validation on DTOs |
| `spring-boot-starter-actuator` | Health/info endpoints |
| `spring-boot-starter-test` | JUnit 5, AssertJ, Mockito, MockMvc |
| `spring-security-test` | `jwt()` MockMvc post-processor |
| `testcontainers-keycloak` (dasniko) | Real Keycloak container for integration tests |
| `testcontainers:junit-jupiter` | `@Testcontainers` / `@Container` JUnit 5 integration |
