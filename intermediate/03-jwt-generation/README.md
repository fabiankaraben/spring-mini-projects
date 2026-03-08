# 03 – JWT Generation

A Spring Boot backend that issues **JSON Web Tokens (JWTs)** upon successful username/password authentication. This project demonstrates the complete JWT generation flow: user registration, credential verification with BCrypt, and token minting with HMAC-SHA256.

---

## What is a JWT?

A JSON Web Token is a compact, URL-safe string with three Base64URL-encoded parts separated by dots:

```
header.payload.signature
```

| Part        | Content                                              |
|-------------|------------------------------------------------------|
| **Header**  | Algorithm (`HS256`) and token type (`JWT`)           |
| **Payload** | Claims: `sub` (username), `iat`, `exp`, `role`       |
| **Signature** | HMAC-SHA256 of header + payload using the secret key |

You can paste any generated token at **<https://jwt.io>** to inspect its claims.

---

## Architecture

```
POST /api/auth/register   →  UserService (BCrypt encode) → PostgreSQL
POST /api/auth/login      →  AuthenticationManager → DaoAuthenticationProvider
                              → UserDetailsServiceImpl → PostgreSQL
                              → JwtService (sign token) → LoginResponse (JWT)
GET  /api/protected/hello →  Requires authentication (echoes back a greeting)
```

---

## Requirements

| Tool          | Version  | Notes                                  |
|---------------|----------|----------------------------------------|
| Java          | 21+      | Eclipse Temurin recommended            |
| Maven         | 3.9+     | Or use the included Maven Wrapper      |
| Docker        | 24+      | Required for Docker Compose deployment |
| Docker Compose | v2+     | Included with Docker Desktop           |

> **Note:** Docker is required to run the application via Docker Compose and to run the integration tests (Testcontainers pulls a PostgreSQL image automatically).

---

## Running with Docker Compose

This is the recommended way to run the full application.

### Start the stack

```bash
docker compose up --build
```

This command:
1. Builds the Spring Boot application image using the multi-stage `Dockerfile`.
2. Starts a PostgreSQL 16 container (`jwtgeneration-postgres`).
3. Waits for PostgreSQL to be healthy, then starts the application (`jwtgeneration-app`).

The API is available at `http://localhost:8080`.

### Stop the stack

```bash
docker compose down
```

To also remove the persistent database volume:

```bash
docker compose down -v
```

---

## API Reference & curl Examples

### Register a new user

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}' | jq
```

**Response (201 Created):**
```json
{
  "message": "User 'alice' registered successfully"
}
```

**Response (409 Conflict – username taken):**
```json
{
  "error": "Username 'alice' is already taken"
}
```

---

### Login and receive a JWT

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}' | jq
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9VU0VSIiwic3ViIjoiYWxpY2UiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTcwMDAwMzYwMH0.signature",
  "tokenType": "Bearer",
  "username": "alice",
  "role": "ROLE_USER",
  "expiresInSeconds": 3600
}
```

**Response (401 Unauthorized – wrong credentials):**
```
HTTP/1.1 401 Unauthorized
```

---

### Call the protected endpoint (manual inspection)

Use the token from the login response in the `Authorization` header:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}' | jq -r '.token')

curl -s http://localhost:8080/api/protected/hello \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Response (200 OK):**
```json
{
  "message": "You have successfully authenticated!",
  "hint": "Paste your token at https://jwt.io to inspect its claims."
}
```

> **Note:** In this project the protected endpoint demonstrates the token is valid structurally but does not verify the JWT signature on the server side on every request. Full server-side JWT validation (reading the `Authorization` header on every request, verifying the signature, and setting the `SecurityContext`) is the subject of the next mini-project **`04-jwt-validation`**.

---

### Inspecting a JWT manually

Copy the `token` value from the login response and paste it at <https://jwt.io>. You will see the decoded payload similar to:

```json
{
  "role": "ROLE_USER",
  "sub": "alice",
  "iat": 1700000000,
  "exp": 1700003600
}
```

---

## Project Structure

```
03-jwt-generation/
├── src/
│   ├── main/
│   │   ├── java/com/example/jwtgeneration/
│   │   │   ├── JwtGenerationApplication.java   # Entry point
│   │   │   ├── config/
│   │   │   │   └── SecurityConfig.java         # Spring Security filter chain
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java          # /api/auth/register & /login
│   │   │   │   └── ProtectedController.java     # /api/protected/hello
│   │   │   ├── domain/
│   │   │   │   ├── Role.java                   # ROLE_USER / ROLE_ADMIN enum
│   │   │   │   └── User.java                   # JPA entity
│   │   │   ├── dto/
│   │   │   │   ├── LoginRequest.java            # POST /login body
│   │   │   │   ├── LoginResponse.java           # JWT response
│   │   │   │   └── RegisterRequest.java         # POST /register body
│   │   │   ├── repository/
│   │   │   │   └── UserRepository.java          # Spring Data JPA
│   │   │   ├── security/
│   │   │   │   └── UserDetailsServiceImpl.java  # Loads users from DB
│   │   │   └── service/
│   │   │       ├── JwtService.java              # JWT creation & validation
│   │   │       └── UserService.java             # User registration
│   │   └── resources/
│   │       └── application.yml                  # App configuration
│   └── test/
│       ├── java/com/example/jwtgeneration/
│       │   ├── integration/
│       │   │   └── AuthIntegrationTest.java     # Full integration tests
│       │   └── unit/
│       │       ├── JwtServiceTest.java          # Unit tests for JwtService
│       │       └── UserServiceTest.java         # Unit tests for UserService
│       └── resources/
│           └── application-test.yml             # Test configuration
├── .gitignore
├── .mvn/                                        # Maven Wrapper metadata
├── docker-compose.yml                           # Full stack: app + PostgreSQL
├── Dockerfile                                   # Multi-stage image build
├── mvnw / mvnw.cmd                             # Maven Wrapper scripts
├── pom.xml
└── README.md
```

---

## Running the Tests

> **Prerequisites:** Docker must be running. Testcontainers automatically pulls and starts a PostgreSQL container for the integration tests.

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests

```bash
./mvnw test -Dtest="com.example.jwtgeneration.unit.*"
```

### Run only integration tests

```bash
./mvnw test -Dtest="com.example.jwtgeneration.integration.*"
```

### Test summary

| Test class             | Type        | What it tests                                              |
|------------------------|-------------|------------------------------------------------------------|
| `JwtServiceTest`       | Unit        | Token generation, claim extraction, validation, expiry     |
| `UserServiceTest`      | Unit        | Registration logic, password encoding, duplicate rejection |
| `AuthIntegrationTest`  | Integration | Full HTTP flow: register → login → JWT structure           |

---

## Configuration Reference

| Property                        | Default (dev)                                  | Description                              |
|---------------------------------|------------------------------------------------|------------------------------------------|
| `spring.datasource.url`         | `jdbc:postgresql://localhost:5432/jwtdb`       | JDBC URL (overridden by Docker Compose)  |
| `spring.datasource.username`    | `postgres`                                     | DB username                              |
| `spring.datasource.password`    | `postgres`                                     | DB password                              |
| `spring.jpa.hibernate.ddl-auto` | `create-drop`                                  | Schema strategy (use `validate` in prod) |
| `app.jwt.secret`                | (see `application.yml`)                        | HS256 signing secret (min 32 bytes)      |
| `app.jwt.expiration-seconds`    | `3600`                                         | Token validity in seconds                |

### Overriding settings via environment variables

Spring Boot maps environment variables to properties by converting to lowercase and replacing underscores with dots:

```bash
export APP_JWT_SECRET="$(openssl rand -base64 48)"
export APP_JWT_EXPIRATION_SECONDS=7200
./mvnw spring-boot:run
```

---

## Security Notes

- Passwords are hashed with **BCrypt** (cost factor 10) and are never stored in plain text.
- The JWT is signed with **HMAC-SHA256**. The signature ensures the token cannot be tampered with without knowing the secret.
- The default secret in `application.yml` is for **development only**. Always use a strong, randomly generated secret in production.
- Sessions are **stateless** (`SessionCreationPolicy.STATELESS`) – no server-side session is created or stored.
- CSRF protection is **disabled** because stateless REST APIs using Bearer tokens are not vulnerable to CSRF attacks.
