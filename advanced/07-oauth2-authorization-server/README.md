# OAuth2 Authorization Server

🔹 This is a backend in Spring Boot minting customized JWTs using Spring Authorization Server.  
📦 **Dependency Manager**: Maven  
🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.

---

## What this project demonstrates

This mini-project implements a fully functional **OAuth2 Authorization Server** using [Spring Authorization Server](https://spring.io/projects/spring-authorization-server). It demonstrates:

- **JWT minting** with RSA key pair signing (RS256 algorithm)
- **Custom JWT claims** — roles derived from scopes, tenant from clientId, metadata map, token version
- **JDBC persistence** for registered clients, issued authorizations, and user consent records (PostgreSQL)
- **Multiple OAuth2 grant types** — `client_credentials`, `authorization_code`, `refresh_token`
- **OIDC support** — discovery endpoint, ID tokens, UserInfo endpoint
- **Token introspection** (RFC 7662) and **token revocation** (RFC 7009)
- **JWK Set endpoint** for resource servers to fetch the public key
- Custom management REST endpoints (`/auth/status`, `/auth/clients`)

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (via included Maven Wrapper) |
| Docker | 24+ (for PostgreSQL and integration tests) |
| Docker Compose | v2+ |

> **Note:** Docker Desktop 29+ is supported. Integration tests use Testcontainers to spin up a PostgreSQL container automatically — no manual database setup needed for tests.

---

## Project structure

```
src/
├── main/
│   ├── java/com/example/authserver/
│   │   ├── AuthorizationServerApplication.java      # Spring Boot entry point
│   │   ├── config/
│   │   │   └── AuthorizationServerConfig.java       # OAuth2 server, security, JWT config
│   │   ├── controller/
│   │   │   └── AuthManagementController.java        # /auth/status, /auth/clients endpoints
│   │   ├── dto/
│   │   │   ├── ClientInfoResponse.java              # Response record for client info
│   │   │   └── ServerStatusResponse.java            # Response record for server status
│   │   ├── security/
│   │   │   └── CustomTokenCustomizer.java           # Adds custom claims to JWTs
│   │   └── service/
│   │       └── ClientRegistrationService.java       # Lists registered OAuth2 clients
│   └── resources/
│       ├── application.yml                          # Server config (port 9000, datasource, etc.)
│       └── schema.sql                               # Spring Authorization Server table DDL
└── test/
    ├── java/com/example/authserver/
    │   ├── integration/
    │   │   └── AuthorizationServerIntegrationTest.java  # Full integration tests (Testcontainers)
    │   ├── security/
    │   │   └── CustomTokenCustomizerTest.java           # Unit tests for JWT claim logic
    │   └── service/
    │       └── ClientRegistrationServiceTest.java       # Unit tests for client listing service
    └── resources/
        ├── application.yml                          # Test overrides (random port, verbose logging)
        ├── docker-java.properties                   # Fixes Docker API version for Docker Desktop 29+
        └── testcontainers.properties                # Complements docker-java.properties
```

---

## Pre-registered demo clients

Two OAuth2 clients are registered at startup (in `AuthorizationServerConfig.java`):

| Client ID | Client Secret | Grant Types | Scopes |
|---|---|---|---|
| `messaging-client` | `messaging-secret` | `authorization_code`, `client_credentials`, `refresh_token` | `openid`, `profile`, `read`, `write` |
| `service-account-client` | `service-secret` | `client_credentials` | `api.read`, `api.write` |

---

## Running locally with Docker Compose

This is the recommended way to run the project. Docker Compose starts PostgreSQL and builds/runs the authorization server automatically.

```bash
# Clone the repository and navigate to this project
cd advanced/07-oauth2-authorization-server

# Build the auth-server Docker image and start all services
docker compose up --build

# Or run in the background
docker compose up --build -d

# View logs
docker compose logs -f

# Stop and remove containers (data persists in named volume)
docker compose down

# Stop and remove containers AND delete all data
docker compose down -v
```

After startup (takes ~60 seconds the first time), all endpoints are available at `http://localhost:9000`.

---

## Running locally without Docker (requires PostgreSQL)

If you have a local PostgreSQL instance running, set these environment variables and run with Maven:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/authserver
export SPRING_DATASOURCE_USERNAME=authuser
export SPRING_DATASOURCE_PASSWORD=authpass

./mvnw spring-boot:run
```

The database schema (`schema.sql`) is created automatically on first startup.

---

## OAuth2 Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/oauth2/token` | POST | Issue access tokens (all grant types) |
| `/oauth2/authorize` | GET/POST | Authorization endpoint (authorization_code flow) |
| `/oauth2/jwks` | GET | JWK Set — RSA public keys for JWT verification |
| `/oauth2/introspect` | POST | Token introspection (RFC 7662) |
| `/oauth2/revoke` | POST | Token revocation (RFC 7009) |
| `/.well-known/openid-configuration` | GET | OIDC discovery document |
| `/.well-known/oauth-authorization-server` | GET | OAuth2 AS metadata (RFC 8414) |
| `/auth/status` | GET | Server status (custom endpoint) |
| `/auth/clients` | GET | List registered clients (custom endpoint) |
| `/actuator/health` | GET | Spring Boot health check |

---

## Usage examples (curl)

### 1. Get a token via client_credentials (machine-to-machine)

```bash
curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Authorization: Basic $(echo -n 'service-account-client:service-secret' | base64)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=api.read" | jq .
```

Expected response:
```json
{
  "access_token": "eyJraWQiOiI...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "api.read"
}
```

### 2. Inspect the JWT payload (decode without verification)

```bash
# Store the token
TOKEN=$(curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Authorization: Basic $(echo -n 'service-account-client:service-secret' | base64)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=api.read" | jq -r '.access_token')

# Decode the JWT payload (middle segment, Base64URL-encoded)
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq .
```

Expected payload (showing custom claims):
```json
{
  "iss": "http://localhost:9000",
  "sub": "service-account-client",
  "aud": ["service-account-client"],
  "iat": 1700000000,
  "exp": 1700003600,
  "scope": "api.read",
  "roles": ["ROLE_API_READER"],
  "tenant": "internal",
  "metadata": {
    "client_id": "service-account-client",
    "rate_limit_tier": "premium",
    "allowed_scopes": "api.read"
  },
  "token_version": "1.0"
}
```

### 3. Get a token for messaging-client

```bash
curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Authorization: Basic $(echo -n 'messaging-client:messaging-secret' | base64)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=read" | jq .
```

### 4. Introspect a token (verify it is active)

```bash
TOKEN=$(curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Authorization: Basic $(echo -n 'service-account-client:service-secret' | base64)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=api.read" | jq -r '.access_token')

curl -s -X POST http://localhost:9000/oauth2/introspect \
  -H "Authorization: Basic $(echo -n 'service-account-client:service-secret' | base64)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=$TOKEN" | jq .
```

Expected response:
```json
{
  "active": true,
  "client_id": "service-account-client",
  "token_type": "Bearer",
  "scope": "api.read",
  ...
}
```

### 5. Fetch the JWK Set (public keys for JWT verification)

```bash
curl -s http://localhost:9000/oauth2/jwks | jq .
```

### 6. Fetch OIDC discovery document

```bash
curl -s http://localhost:9000/.well-known/openid-configuration | jq .
```

### 7. Check server status

```bash
curl -s http://localhost:9000/auth/status | jq .
```

### 8. List registered clients

```bash
curl -s http://localhost:9000/auth/clients | jq .
```

### 9. Authorization Code flow (interactive, requires a browser)

Start the server, then open in your browser:

```
http://localhost:9000/oauth2/authorize?response_type=code&client_id=messaging-client&scope=openid%20read&redirect_uri=http://127.0.0.1:8080/login/oauth2/code/messaging-client
```

Log in with the demo user (`user` / `password`), approve the scopes, and you'll be redirected with an authorization code. Exchange it for tokens:

```bash
curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Authorization: Basic $(echo -n 'messaging-client:messaging-secret' | base64)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=YOUR_CODE&redirect_uri=http://127.0.0.1:8080/login/oauth2/code/messaging-client" | jq .
```

---

## Running tests

Tests require Docker to be running (for the Testcontainers PostgreSQL container).

```bash
# Run all tests (unit + integration)
./mvnw clean test

# Run only unit tests (no Docker needed)
./mvnw test -pl . -Dtest="CustomTokenCustomizerTest,ClientRegistrationServiceTest"

# Run only integration tests
./mvnw test -pl . -Dtest="AuthorizationServerIntegrationTest"
```

### What the tests cover

**Unit tests** (`CustomTokenCustomizerTest`, `ClientRegistrationServiceTest`):
- Scope → role mapping logic (`read` → `ROLE_READER`, `api.read` → `ROLE_API_READER`, etc.)
- Tenant derivation from client ID (`service-*` → `internal`, others → `default`)
- Metadata map construction (rate limit tier, allowed scopes)
- Client listing service with mocked `JdbcTemplate`

**Integration tests** (`AuthorizationServerIntegrationTest`):
- Token endpoint issues valid JWTs for `client_credentials` grant
- Issued JWTs contain custom claims (`roles`, `tenant`, `metadata`, `token_version`)
- OIDC discovery endpoint returns correct metadata
- JWK Set endpoint returns the RSA public key
- Token introspection reports issued tokens as active
- Invalid credentials return HTTP 401
- Server status and client listing endpoints respond correctly
- Actuator health reports UP

---

## Custom JWT claims

Every access token issued by this server includes these non-standard claims (added by `CustomTokenCustomizer`):

| Claim | Type | Example | Description |
|---|---|---|---|
| `roles` | `string[]` | `["ROLE_API_READER"]` | Roles derived from granted scopes |
| `tenant` | `string` | `"internal"` | Tenant identifier derived from client ID |
| `metadata` | `object` | `{"client_id": "...", ...}` | Client metadata map |
| `token_version` | `string` | `"1.0"` | JWT schema version for forward compatibility |

**Scope → Role mapping:**

| Scope | Role |
|---|---|
| `read` | `ROLE_READER` |
| `write` | `ROLE_WRITER` |
| `api.read` | `ROLE_API_READER` |
| `api.write` | `ROLE_API_WRITER` |
| `openid`, `profile` | *(filtered out — OIDC scopes produce no roles)* |
| any other scope | `ROLE_<UPPERCASE_SCOPE>` |
