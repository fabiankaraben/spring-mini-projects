# 06 – Role-Based Access

A Spring Boot backend that demonstrates **role-based access control (RBAC)** using `@PreAuthorize` and method-level security. Authentication is stateless via **JWT Bearer tokens**. Users are persisted in **PostgreSQL**.

---

## What this project demonstrates

| Concept | Where |
|---|---|
| `@EnableMethodSecurity` | `SecurityConfig.java` |
| `@PreAuthorize("hasRole('ADMIN')")` | `AdminController`, `UserService` |
| `@PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")` | `ModeratorController`, `UserService` |
| `@PreAuthorize("isAuthenticated()")` | `UserController.getMyProfile()` |
| URL-level rules (coarse-grained) | `SecurityConfig.securityFilterChain()` |
| JWT generation & validation | `JwtService`, `JwtAuthenticationFilter` |
| Role embedded in JWT claim | `JwtService.generateToken()` |
| Defence-in-depth (URL + method) | `AdminController` + `SecurityConfig` |

### Role hierarchy

```
ROLE_ADMIN ⊇ ROLE_MODERATOR ⊇ ROLE_USER
```

> Admins can access moderator endpoints (`hasAnyRole` lists them explicitly). Spring Security also supports a formal `RoleHierarchy` bean for automatic inheritance.

---

## Requirements

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | via Maven Wrapper (`./mvnw`) |
| Docker & Docker Compose | latest |

---

## Running the project

The application requires PostgreSQL. Everything is orchestrated with Docker Compose.

### 1. Build and start all services

```bash
docker compose up --build
```

This starts:
- **`db`** – PostgreSQL 16 on port `5432`
- **`app`** – Spring Boot API on port `8080`

The app waits for the database healthcheck to pass before starting.

### 2. Stop all services

```bash
docker compose down
```

To also remove the database volume (wipes all data):

```bash
docker compose down -v
```

---

## API reference & curl examples

> Replace `<TOKEN>` with the JWT returned by the login endpoint.

### Authentication (public — no token required)

#### Register a user (defaults to `ROLE_USER`)
```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret123"}'
```

#### Register an admin user (for demo purposes)
```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","role":"ROLE_ADMIN"}'
```

#### Register a moderator
```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"moderator","password":"mod123","role":"ROLE_MODERATOR"}'
```

#### Login and get a JWT
```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret123"}'
```

Example response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "username": "alice",
  "role": "ROLE_USER",
  "expiresInSeconds": 3600
}
```

Store the token in a shell variable for convenience:
```bash
USER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret123"}' | jq -r '.token')

ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

MOD_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"moderator","password":"mod123"}' | jq -r '.token')
```

---

### User endpoints (`/api/users`)

#### Get my own profile — any authenticated user
```bash
curl -s http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $USER_TOKEN"
```

#### List all users — `ROLE_ADMIN` only
```bash
curl -s http://localhost:8080/api/users \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Calling with a non-admin token returns `403 Forbidden`:
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/users \
  -H "Authorization: Bearer $USER_TOKEN"
# → 403
```

#### Get user by ID — `ROLE_ADMIN` or `ROLE_MODERATOR`
```bash
curl -s http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer $MOD_TOKEN"
```

#### Update a user's role — `ROLE_ADMIN` only
```bash
curl -s -X PATCH http://localhost:8080/api/users/2/role \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"role":"ROLE_MODERATOR"}'
```

#### Delete a user — `ROLE_ADMIN` only
```bash
curl -s -X DELETE http://localhost:8080/api/users/2 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

### Moderator endpoints (`/api/moderator`)

Both `ROLE_MODERATOR` and `ROLE_ADMIN` can access these.

#### Moderator panel
```bash
curl -s http://localhost:8080/api/moderator/panel \
  -H "Authorization: Bearer $MOD_TOKEN"
```

#### Content reports
```bash
curl -s http://localhost:8080/api/moderator/reports \
  -H "Authorization: Bearer $MOD_TOKEN"
```

A regular user (`ROLE_USER`) gets `403`:
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/moderator/panel \
  -H "Authorization: Bearer $USER_TOKEN"
# → 403
```

---

### Admin endpoints (`/api/admin`)

Double-protected: URL-level rule + `@PreAuthorize("hasRole('ADMIN')")`.

#### Admin dashboard
```bash
curl -s http://localhost:8080/api/admin/dashboard \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

#### System status
```bash
curl -s http://localhost:8080/api/admin/system \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## Running the tests

Tests require Docker to be running (Testcontainers spins up a real PostgreSQL container automatically).

```bash
./mvnw clean test
```

### Test types

| Class | Type | Description |
|---|---|---|
| `UserTest` | Unit | Domain entity construction, setters, `toString()` safety |
| `UserServiceTest` | Unit (Mockito) | Service business logic, role assignment, error handling |
| `RoleBasedAccessIntegrationTest` | Integration (Testcontainers) | Full HTTP cycle, JWT flow, `@PreAuthorize` enforcement |

### What the integration tests verify

- Login returns a JWT for valid credentials; `401` for invalid
- `GET /api/users/me` — `200` with token, `401` without
- `GET /api/users` — `200` for admin, `403` for user/moderator
- `GET /api/users/{id}` — `200` for admin/moderator, `403` for user
- `PATCH /api/users/{id}/role` — `200` for admin, `403` for user
- `GET /api/admin/**` — `200` for admin, `403` for others
- `GET /api/moderator/**` — `200` for moderator/admin, `403` for user
- `DELETE /api/users/{id}` — `403` for moderator, `404` for admin on missing user

---

## Project structure

```
src/
├── main/
│   ├── java/com/example/rolebasedaccess/
│   │   ├── RoleBasedAccessApplication.java   # Entry point
│   │   ├── config/
│   │   │   └── SecurityConfig.java            # @EnableMethodSecurity, filter chain
│   │   ├── controller/
│   │   │   ├── AuthController.java            # POST /api/auth/register, /login
│   │   │   ├── UserController.java            # GET/PATCH/DELETE /api/users/**
│   │   │   ├── ModeratorController.java       # GET /api/moderator/**
│   │   │   └── AdminController.java           # GET /api/admin/**
│   │   ├── domain/
│   │   │   ├── User.java                      # JPA entity
│   │   │   └── Role.java                      # ROLE_USER, ROLE_MODERATOR, ROLE_ADMIN
│   │   ├── dto/
│   │   │   ├── LoginRequest.java
│   │   │   ├── LoginResponse.java
│   │   │   └── RegisterRequest.java
│   │   ├── repository/
│   │   │   └── UserRepository.java
│   │   ├── security/
│   │   │   ├── JwtAuthenticationFilter.java   # Validates Bearer token on each request
│   │   │   └── UserDetailsServiceImpl.java    # Loads user from DB for Spring Security
│   │   └── service/
│   │       ├── JwtService.java                # JWT generation, parsing, validation
│   │       └── UserService.java               # Business logic with @PreAuthorize
│   └── resources/
│       └── application.yml
└── test/
    ├── java/com/example/rolebasedaccess/
    │   ├── domain/UserTest.java                # Unit tests for User entity
    │   ├── service/UserServiceTest.java        # Unit tests for UserService (Mockito)
    │   └── integration/
    │       └── RoleBasedAccessIntegrationTest.java  # Full integration tests
    └── resources/
        ├── application-test.yml
        ├── docker-java.properties              # Docker API version fix for Testcontainers
        └── testcontainers.properties
```

---

## Security design notes

- **Stateless sessions** — `SessionCreationPolicy.STATELESS`; no `HttpSession` is ever created.
- **CSRF disabled** — REST APIs with Bearer tokens are not susceptible to CSRF.
- **BCrypt** — passwords are hashed with cost factor 10 before storage.
- **Role in JWT** — the `role` claim is embedded at login time and read from the token on subsequent requests, avoiding a DB round-trip per request.
- **Defence-in-depth** — admin endpoints are protected at both URL level (`SecurityConfig`) and method level (`@PreAuthorize`).
- **Secret management** — the JWT secret is read from `app.jwt.secret`. In production, inject it via an environment variable or secrets manager; never commit it to version control.
