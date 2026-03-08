# 04 – JWT Validation

A Spring Boot backend that **parses and verifies JWTs inside a custom Spring Security filter**.

While project `03-jwt-generation` showed how to *issue* a token on successful login, this project
focuses on the next step: validating every incoming JWT on every protected request before it
reaches the controller.

---

## What this project demonstrates

| Concept | Where to look |
|---|---|
| Custom JWT filter (`OncePerRequestFilter`) | `JwtAuthenticationFilter.java` |
| HMAC-SHA256 signature verification (JJWT) | `JwtService.extractAllClaims()` |
| Expiry check | `JwtService.isTokenValid()` |
| Subject (username) match | `JwtService.isTokenValid()` |
| Populating the `SecurityContextHolder` | `JwtAuthenticationFilter.doFilterInternal()` |
| Role-based endpoint access | `SecurityConfig` + `/api/protected/admin` |
| Token claims in responses | `ProtectedController.profile()` |

### Filter pipeline (for every request)

```
Client request
    │
    ▼
JwtAuthenticationFilter          ← extracts + validates the Bearer token
    │  (if valid → sets Authentication in SecurityContextHolder)
    ▼
UsernamePasswordAuthenticationFilter  ← skipped (no form-login)
    │
    ▼
ExceptionTranslationFilter       ← returns 401/403 if auth is missing/insufficient
    │
    ▼
Controller method
```

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven (via wrapper) | 3.9+ |
| Docker | 24+ (for Docker Compose) |
| Docker Compose | v2 (`docker compose`) |

> **Note:** Running the application locally (without Docker) requires a PostgreSQL instance
> on `localhost:5432`. Running via Docker Compose (recommended) handles the database automatically.

---

## Running the application with Docker Compose

This is the recommended way to run the project. Docker Compose starts both the PostgreSQL
database and the Spring Boot application with a single command.

```bash
# Build the image and start all services in detached mode
docker compose up --build -d

# Follow the application logs
docker compose logs -f app

# Stop and remove all containers (data volume is preserved)
docker compose down

# Stop and remove containers AND the data volume (clean slate)
docker compose down -v
```

The API will be available at **http://localhost:8080**.

---

## API endpoints

### Public (no token required)

| Method | URL | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register a new user account |
| `POST` | `/api/auth/login` | Authenticate and receive a signed JWT |

### Protected (require `Authorization: Bearer <token>`)

| Method | URL | Description | Role required |
|---|---|---|---|
| `GET` | `/api/protected/hello` | Personalised greeting | Any authenticated user |
| `GET` | `/api/protected/profile` | JWT claims for the caller | Any authenticated user |
| `GET` | `/api/protected/introspect` | Raw `Authentication` object details | Any authenticated user |
| `GET` | `/api/protected/admin` | Admin-only panel | `ROLE_ADMIN` only |

---

## Usage examples with curl

### 1. Register a new user

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}' | jq
```

Expected response (`201 Created`):
```json
{
  "message": "User 'alice' registered successfully"
}
```

### 2. Log in and receive a JWT

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}' | jq
```

Expected response (`200 OK`):
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9VU0VSIiwic3ViIjoiYWxpY2UiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTcwMDAwMzYwMH0.SIGNATURE",
  "tokenType": "Bearer",
  "username": "alice",
  "role": "ROLE_USER",
  "expiresInSeconds": 3600
}
```

Save the token in a shell variable for subsequent requests:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}' | jq -r '.token')
```

### 3. Access a protected endpoint (JWT validation in action)

```bash
curl -s http://localhost:8080/api/protected/hello \
  -H "Authorization: Bearer $TOKEN" | jq
```

Expected response (`200 OK`):
```json
{
  "message": "Hello, alice! Your JWT was successfully validated."
}
```

### 4. Inspect your token claims

```bash
curl -s http://localhost:8080/api/protected/profile \
  -H "Authorization: Bearer $TOKEN" | jq
```

Expected response (`200 OK`):
```json
{
  "username":  "alice",
  "role":      "ROLE_USER",
  "expiresAt": "Mon Jan 01 13:00:00 UTC 2025"
}
```

### 5. Introspect the SecurityContext

```bash
curl -s http://localhost:8080/api/protected/introspect \
  -H "Authorization: Bearer $TOKEN" | jq
```

Expected response (`200 OK`):
```json
{
  "principal":     "alice",
  "authorities":   "[ROLE_USER]",
  "authenticated": true
}
```

### 6. Access a protected endpoint without a token (401)

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/protected/hello
# Output: 401
```

### 7. Access the admin endpoint with a ROLE_USER token (403)

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/protected/admin \
  -H "Authorization: Bearer $TOKEN"
# Output: 403
```

### 8. Send a tampered / invalid token (401)

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/protected/hello \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.tampered.invalidsignature"
# Output: 401
```

---

## Running the tests

Tests are completely independent of Docker Compose. The integration tests use
**Testcontainers**, which automatically pulls and starts a PostgreSQL Docker container
during the test run. Docker must be running on your machine.

```bash
# Run all tests (unit + integration)
./mvnw clean test
```

### Test coverage

| Test class | Type | What it covers |
|---|---|---|
| `JwtServiceTest` | Unit | Token generation, claim extraction, signature verification, expiry, subject mismatch |
| `UserServiceTest` | Unit | User registration, password encoding, duplicate username rejection |
| `JwtValidationIntegrationTest` | Integration (Testcontainers) | Full HTTP round-trip: register → login → use JWT → verify 200/401/403 responses |

---

## Project structure

```
src/
├── main/java/com/example/jwtvalidation/
│   ├── JwtValidationApplication.java       # Spring Boot entry point
│   ├── config/
│   │   └── SecurityConfig.java             # Security filter chain + JWT filter registration
│   ├── controller/
│   │   ├── AuthController.java             # POST /api/auth/register and /login
│   │   └── ProtectedController.java        # GET /api/protected/** (requires JWT)
│   ├── domain/
│   │   ├── Role.java                       # ROLE_USER / ROLE_ADMIN enum
│   │   └── User.java                       # JPA entity (users table)
│   ├── dto/
│   │   ├── LoginRequest.java               # Input DTO for login
│   │   ├── LoginResponse.java              # Output DTO with JWT + metadata
│   │   └── RegisterRequest.java            # Input DTO for registration
│   ├── repository/
│   │   └── UserRepository.java             # Spring Data JPA repository
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java    # *** THE CORE: custom JWT validation filter ***
│   │   └── UserDetailsServiceImpl.java     # Loads users from DB for Spring Security
│   └── service/
│       ├── JwtService.java                 # JWT generation + parsing + validation logic
│       └── UserService.java                # User registration business logic
├── main/resources/
│   └── application.yml                     # App + datasource + JWT configuration
└── test/
    ├── java/com/example/jwtvalidation/
    │   ├── integration/
    │   │   └── JwtValidationIntegrationTest.java  # Full stack tests with Testcontainers
    │   └── unit/
    │       ├── JwtServiceTest.java                # Unit tests for JWT logic
    │       └── UserServiceTest.java               # Unit tests for user service
    └── resources/
        ├── application-test.yml            # Test profile overrides
        ├── docker-java.properties          # Docker API version fix for Testcontainers
        └── testcontainers.properties       # Testcontainers Docker API configuration
```

---

## Key design notes

### Why `OncePerRequestFilter`?
Guarantees the filter executes **exactly once per request**, even when error forwarding
causes multiple servlet dispatches. Without it, signature verification could run twice.

### Why add the filter *before* `UsernamePasswordAuthenticationFilter`?
The JWT filter must populate the `SecurityContextHolder` before the standard Spring
Security filters run. `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`
ensures this ordering.

### Why STATELESS sessions?
JWTs are self-contained. The server does not need to store any session state between
requests. `SessionCreationPolicy.STATELESS` prevents Spring Security from creating
`HttpSession` objects, which keeps the application truly stateless and horizontally scalable.

### Security considerations
- The JWT secret is read from environment variables in production (never hard-coded).
- Tokens carry a `role` claim to enable authorisation without a DB round-trip per request.
- Both invalid signature and expired token cases return `401 Unauthorized` without leaking
  which check failed.
