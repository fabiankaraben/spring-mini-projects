# OAuth2 Resource Server

A Spring Boot mini-project implementing an **OAuth2 Resource Server** that validates JWT access tokens and secures REST APIs in a microservices pattern.

## What This Project Demonstrates

This project shows how to build a backend service that acts as an **OAuth2 Resource Server** — a service that:

1. **Accepts Bearer tokens** in the `Authorization` header of incoming HTTP requests.
2. **Validates JWT signatures** by fetching the Authorization Server's RSA public keys from the JWK Set URI (`/oauth2/jwks`) — cached after the first fetch.
3. **Validates JWT claims** — checks the `exp` (expiry), `iss` (issuer), and optionally `aud` (audience) claims.
4. **Converts JWT claims into Spring Security authorities** — both standard `scope` claims (→ `SCOPE_*` authorities) and custom `roles` claims (→ `ROLE_*` authorities).
5. **Enforces scope-based access control** at the HTTP layer and method layer (`@PreAuthorize`).

### The OAuth2 Flow in Context

```
┌─────────────┐          ┌─────────────────────┐          ┌──────────────────────┐
│   Client    │          │ Authorization Server │          │  Resource Server     │
│ (curl/app)  │          │  (port 9000)         │          │  (this project :8081)│
└──────┬──────┘          └──────────┬───────────┘          └──────────┬───────────┘
       │                            │                                  │
       │  POST /oauth2/token        │                                  │
       │  (client_credentials)      │                                  │
       │ ─────────────────────────► │                                  │
       │                            │                                  │
       │  { "access_token": "eyJ…"} │                                  │
       │ ◄───────────────────────── │                                  │
       │                            │                                  │
       │  GET /api/products                                            │
       │  Authorization: Bearer eyJ…                                   │
       │ ────────────────────────────────────────────────────────────► │
       │                            │  GET /oauth2/jwks (once, cached) │
       │                            │ ◄────────────────────────────────│
       │                            │  { RSA public key }              │
       │                            │ ─────────────────────────────────►
       │                            │                                  │ Verify JWT sig
       │                            │                                  │ Check exp/iss
       │                            │                                  │ Map scope→authority
       │  [ { "id": 1, "name": … }]                                   │
       │ ◄────────────────────────────────────────────────────────────│
```

## Requirements

- **Java 21** or later
- **Maven 3.9+** (or use the included Maven Wrapper `./mvnw`)
- **Docker** (for running integration tests with Testcontainers, and for the full Docker Compose stack)
- **Docker Compose** (for running the full stack with the Authorization Server)

> **Note:** The resource server requires an OAuth2 Authorization Server to issue tokens. For the full Docker Compose setup, project `07-oauth2-authorization-server` is used as the auth server. For local development without Docker Compose, you can run the auth server from project 07 separately.

## Project Structure

```
src/main/java/com/example/resourceserver/
├── ResourceServerApplication.java          # Spring Boot entry point
├── config/
│   └── ResourceServerSecurityConfig.java   # Security filter chain + JWT config
├── security/
│   └── JwtClaimsAuthoritiesConverter.java  # Converts JWT claims → authorities
├── domain/
│   └── Product.java                        # Product domain model
├── dto/
│   ├── CreateProductRequest.java           # Request DTO for product creation
│   └── UpdateProductRequest.java           # Request DTO for product update
├── repository/
│   └── ProductRepository.java             # In-memory product data store
├── service/
│   └── ProductService.java                # Product business logic
└── controller/
    ├── ProductController.java             # Protected Products API endpoints
    └── PublicController.java              # Public /api/public/info endpoint

src/test/java/com/example/resourceserver/
├── security/
│   └── JwtClaimsAuthoritiesConverterTest.java  # Unit tests for JWT converter
├── service/
│   └── ProductServiceTest.java                 # Unit tests for ProductService
└── integration/
    └── ResourceServerIntegrationTest.java       # Full integration tests with WireMock
```

## API Endpoints

### Public Endpoints (no token required)

| Method | URL | Description |
|--------|-----|-------------|
| `GET` | `/api/public/info` | Server info, available scopes, endpoint map |
| `GET` | `/actuator/health` | Spring Boot Actuator health check |

### Protected Endpoints (Bearer JWT required)

| Method | URL | Required Scope | Description |
|--------|-----|----------------|-------------|
| `GET` | `/api/products` | `products.read` | List all products |
| `GET` | `/api/products?category=ELECTRONICS` | `products.read` | Filter by category |
| `GET` | `/api/products/{id}` | `products.read` | Get product by ID |
| `POST` | `/api/products` | `products.write` | Create a new product |
| `PUT` | `/api/products/{id}` | `products.write` | Update an existing product |
| `DELETE` | `/api/products/{id}` | `products.write` | Delete a product |

## Running with Docker Compose (Full Stack)

This is the recommended way to run the complete demo — it starts the Authorization Server, its PostgreSQL database, and this Resource Server together.

### Start the stack

```bash
docker compose up --build
```

Or in detached mode:

```bash
docker compose up --build -d
```

### Check service health

```bash
# Authorization Server
curl http://localhost:9000/actuator/health

# Resource Server
curl http://localhost:8081/actuator/health
```

### Access the public info endpoint (no token needed)

```bash
curl http://localhost:8081/api/public/info
```

### Obtain a JWT access token from the Authorization Server

Get a token with `products.read` scope (read-only access):

```bash
TOKEN=$(curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Authorization: Basic $(echo -n 'service-account-client:service-secret' | base64)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=products.read" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

echo "Token: $TOKEN"
```

Or manually copy it from the JSON response:

```bash
curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Authorization: Basic $(echo -n 'service-account-client:service-secret' | base64)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=products.read"
```

### Use the token to call the Resource Server

**List all products:**

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/products
```

**Get a product by ID:**

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/products/1
```

**Filter by category:**

```bash
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8081/api/products?category=ELECTRONICS"
```

**Try without a token (expect 401 Unauthorized):**

```bash
curl -v http://localhost:8081/api/products
```

### Get a write-scope token and perform write operations

```bash
WRITE_TOKEN=$(curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Authorization: Basic $(echo -n 'messaging-client:messaging-secret' | base64)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=products.read products.write" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
```

**Create a product (requires `products.write`):**

```bash
curl -s -X POST http://localhost:8081/api/products \
  -H "Authorization: Bearer $WRITE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mechanical Keyboard",
    "description": "TKL mechanical keyboard with Cherry MX switches",
    "price": 119.99,
    "category": "ELECTRONICS",
    "stock": 45
  }'
```

**Update a product:**

```bash
curl -s -X PUT http://localhost:8081/api/products/1 \
  -H "Authorization: Bearer $WRITE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"price": 139.99, "stock": 60}'
```

**Delete a product:**

```bash
curl -s -X DELETE http://localhost:8081/api/products/3 \
  -H "Authorization: Bearer $WRITE_TOKEN"
```

**Try write with read-only token (expect 403 Forbidden):**

```bash
curl -v -X POST http://localhost:8081/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","price":9.99,"category":"TEST","stock":1}'
```

### Stop the stack

```bash
docker compose down

# Also remove the PostgreSQL volume (wipes database):
docker compose down -v
```

## Running Tests

Tests do **not** require Docker Compose to be running. Integration tests use Testcontainers to spin up a WireMock container automatically.

### Run all tests

```bash
./mvnw clean test
```

### What the tests cover

**Unit tests** (no Spring context, no Docker):

- `JwtClaimsAuthoritiesConverterTest` — verifies JWT claims → authorities mapping for `scope`, `scp`, and custom `roles` claims across all combinations.
- `ProductServiceTest` — verifies business logic for all CRUD operations with a mocked repository.

**Integration tests** (full Spring context + WireMock via Testcontainers):

- `ResourceServerIntegrationTest` — uses Spring Security Test's `jwt()` post-processor to inject fake JWT tokens and verifies:
  - Public endpoints are accessible without authentication
  - Protected endpoints return 401 without a token
  - Protected endpoints return 403 with insufficient scope
  - `GET /api/products` returns the pre-loaded product list with `products.read` scope
  - Category filtering works correctly
  - `POST`, `PUT`, `DELETE` operations work with `products.write` scope
  - Invalid request bodies return 400
  - Non-existent resource IDs return 404
  - Role-based access behaves correctly

> **Note:** Integration tests require Docker to be running (for the WireMock Testcontainer). The WireMock container serves a stub JWK Set endpoint that the application fetches on startup.

## Key Concepts Explained

### JWT Validation Flow

When a request with `Authorization: Bearer <token>` arrives:

1. `BearerTokenAuthenticationFilter` extracts the token string.
2. `NimbusJwtDecoder` fetches the JWK Set from the configured `jwk-set-uri` (cached after first call).
3. The JWT signature is verified using the RSA public key from the JWK Set.
4. JWT claims are validated: `exp` (not expired), `iss` (matches `issuer-uri`).
5. `JwtAuthenticationConverter` + `JwtClaimsAuthoritiesConverter` convert the JWT claims into `GrantedAuthority` objects.
6. Spring Security evaluates the authorization rules configured in `ResourceServerSecurityConfig`.

### Scope vs. Role Authorization

This project demonstrates **two complementary authorization mechanisms**:

| Mechanism | JWT Claim | Authority Format | Used In |
|-----------|-----------|-----------------|---------|
| **Scope-based** | `"scope": "products.read"` | `SCOPE_products.read` | Security config (`hasAuthority`) |
| **Role-based** | `"roles": ["ROLE_READER"]` | `ROLE_READER` | `@PreAuthorize` annotations |

### Stateless Security

The resource server is completely stateless — no HTTP sessions are created. Each request must carry its own JWT. This makes the service horizontally scalable: any instance can handle any request independently.

### Defense in Depth

Authorization is enforced at two layers:

1. **HTTP layer** — in `ResourceServerSecurityConfig.authorizeHttpRequests()` (checked before reaching the controller).
2. **Method layer** — `@PreAuthorize("hasAuthority('SCOPE_products.write')")` on controller methods (checked inside the controller).

This defense-in-depth approach ensures that even if the security configuration were accidentally changed, method-level annotations provide a secondary check.
