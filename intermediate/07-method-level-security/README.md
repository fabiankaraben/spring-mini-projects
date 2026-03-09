# Method Level Security

A Spring Boot backend that secures **service-layer methods** using Spring Security's
method-level annotations: `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter`,
and `@Secured`.

The domain is a **Document Management API** where users create, read, update, and delete
documents. All access control is enforced at the service method level — not just at the
HTTP layer — so the security rules apply regardless of how the service is called.

---

## Concepts demonstrated

| Annotation | When evaluated | Key use |
|---|---|---|
| `@PreAuthorize` | Before the method runs | Role checks, authenticated checks, parameter-based SpEL |
| `@PostAuthorize` | After the method returns | Ownership check on the returned object (`returnObject`) |
| `@PreFilter` | Before the method runs | Filter a collection **parameter** (bulk operations) |
| `@PostFilter` | After the method returns | Filter a collection **return value** (hide inaccessible items) |
| `@Secured` | Before the method runs | Simple role-only check (no SpEL); shown as contrast to `@PreAuthorize` |

---

## Requirements

- **Java 21+**
- **Maven 3.9+** (or use the included Maven Wrapper `./mvnw`)
- **Docker Desktop** (for Docker Compose deployment and Testcontainers integration tests)

---

## Project structure

```
src/main/java/com/example/methodlevelsecurity/
├── MethodLevelSecurityApplication.java   # Entry point
├── config/
│   └── SecurityConfig.java               # @EnableMethodSecurity, filter chain
├── controller/
│   ├── AuthController.java               # /api/auth/register, /api/auth/login
│   ├── DocumentController.java           # /api/documents/**
│   └── UserController.java               # /api/users/**
├── domain/
│   ├── Document.java                     # JPA entity (title, content, owner, visibility)
│   ├── Role.java                         # ROLE_USER, ROLE_MODERATOR, ROLE_ADMIN
│   ├── User.java                         # JPA entity (username, password, role)
│   └── Visibility.java                   # PUBLIC, PRIVATE
├── dto/
│   ├── DocumentRequest.java
│   ├── LoginRequest.java
│   ├── LoginResponse.java
│   └── RegisterRequest.java
├── repository/
│   ├── DocumentRepository.java
│   └── UserRepository.java
├── security/
│   ├── JwtAuthenticationFilter.java      # Validates Bearer JWT on every request
│   └── UserDetailsServiceImpl.java       # Loads users from PostgreSQL
└── service/
    ├── DocumentService.java              # All method-level security annotations here
    ├── JwtService.java                   # JWT generation / parsing / validation
    └── UserService.java                  # @Secured and @PreAuthorize examples
```

---

## Running with Docker Compose

Docker Compose starts a PostgreSQL database and the Spring Boot application together.

```bash
# Build the image and start all services
docker compose up --build

# Stop and remove containers (data volume is preserved)
docker compose down

# Stop and remove containers AND the data volume
docker compose down -v
```

The API is available at `http://localhost:8080`.

---

## Running locally (without Docker)

1. Start a local PostgreSQL instance (or use Docker just for the DB):
   ```bash
   docker compose up db
   ```

2. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

---

## Running the tests

```bash
./mvnw clean test
```

- **Unit tests** (`DocumentServiceTest`, `UserServiceTest`) use Mockito; no Docker needed.
- **Integration tests** (`MethodLevelSecurityIntegrationTest`) use Testcontainers to spin
  up a real PostgreSQL container automatically. Docker must be running.

---

## API usage with curl

### 1. Register users

```bash
# Register a regular user
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice123","role":"ROLE_USER"}'

# Register a moderator
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"mod","password":"mod123","role":"ROLE_MODERATOR"}'

# Register an admin
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","role":"ROLE_ADMIN"}'
```

### 2. Login and capture JWT tokens

```bash
# Login as alice (ROLE_USER) and capture the token
USER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# Login as admin (ROLE_ADMIN) and capture the token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# Login as moderator (ROLE_MODERATOR)
MOD_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"mod","password":"mod123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)
```

### 3. Document operations

#### Create a document (`@PreAuthorize("isAuthenticated()")`)

```bash
# Alice creates a private document
curl -s -X POST http://localhost:8080/api/documents \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Alice Note","content":"My private thoughts","visibility":"PRIVATE"}'

# Alice creates a public document
curl -s -X POST http://localhost:8080/api/documents \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Public Post","content":"Hello everyone!","visibility":"PUBLIC"}'

# Unauthenticated → 401 Unauthorized
curl -s -X POST http://localhost:8080/api/documents \
  -H "Content-Type: application/json" \
  -d '{"title":"No Auth","content":"fail","visibility":"PUBLIC"}'
```

#### Get public documents (`@PostFilter` demo)

```bash
# ROLE_USER sees only PUBLIC documents (PostFilter removes PRIVATE ones)
curl -s http://localhost:8080/api/documents/public \
  -H "Authorization: Bearer $USER_TOKEN"

# ROLE_ADMIN sees ALL documents (PostFilter expression passes admin through)
curl -s http://localhost:8080/api/documents/public \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

#### Get document by ID (`@PostAuthorize` demo)

```bash
# Replace 1 with a real document ID.

# Owner can read their own private document → 200 OK
curl -s http://localhost:8080/api/documents/1 \
  -H "Authorization: Bearer $USER_TOKEN"

# Non-owner ROLE_USER trying to read someone else's PRIVATE doc → 403 Forbidden
# (PostAuthorize evaluates AFTER the load: returnObject.ownerUsername != authentication.name)
curl -s http://localhost:8080/api/documents/1 \
  -H "Authorization: Bearer $MOD_TOKEN"

# ROLE_ADMIN can read any document → 200 OK
curl -s http://localhost:8080/api/documents/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

#### Get all documents (`@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")`)

```bash
# ROLE_ADMIN → 200 OK
curl -s http://localhost:8080/api/documents \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# ROLE_USER → 403 Forbidden (PreAuthorize blocks before method runs)
curl -s http://localhost:8080/api/documents \
  -H "Authorization: Bearer $USER_TOKEN"
```

#### Update a document (ownership check)

```bash
# Replace 1 with a real document ID owned by alice.

# Owner updates their document → 200 OK
curl -s -X PUT http://localhost:8080/api/documents/1 \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Updated Title","content":"New content","visibility":"PUBLIC"}'

# Non-owner trying to update → 403 Forbidden
curl -s -X PUT http://localhost:8080/api/documents/1 \
  -H "Authorization: Bearer $MOD_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Hacked","content":"Hacked","visibility":"PUBLIC"}'
```

#### Delete a document (ownership check)

```bash
# Owner deletes their document → 200 OK
curl -s -X DELETE http://localhost:8080/api/documents/1 \
  -H "Authorization: Bearer $USER_TOKEN"

# Non-owner → 403 Forbidden
curl -s -X DELETE http://localhost:8080/api/documents/1 \
  -H "Authorization: Bearer $MOD_TOKEN"
```

#### Delete all documents of a user (`@PreAuthorize` with parameter binding)

```bash
# User deletes their own documents (SpEL: authentication.name == #username) → 200
curl -s -X DELETE http://localhost:8080/api/documents/user/alice \
  -H "Authorization: Bearer $USER_TOKEN"

# ROLE_USER trying to delete another user's documents → 403
curl -s -X DELETE http://localhost:8080/api/documents/user/admin \
  -H "Authorization: Bearer $USER_TOKEN"

# ROLE_ADMIN can delete any user's documents → 200
curl -s -X DELETE http://localhost:8080/api/documents/user/alice \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### 4. User management (`@Secured` and `@PreAuthorize`)

```bash
# View own profile (any authenticated user)
curl -s http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $USER_TOKEN"

# List all users: ROLE_ADMIN only (@Secured("ROLE_ADMIN"))
curl -s http://localhost:8080/api/users \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# ROLE_USER trying to list users → 403
curl -s http://localhost:8080/api/users \
  -H "Authorization: Bearer $USER_TOKEN"

# Find user by ID: ROLE_ADMIN or ROLE_MODERATOR
curl -s http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer $MOD_TOKEN"

# Update role: ROLE_ADMIN only (@PreAuthorize("hasRole('ADMIN')"))
curl -s -X PATCH http://localhost:8080/api/users/1/role \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"role":"ROLE_MODERATOR"}'

# Delete user: ROLE_ADMIN only (@Secured("ROLE_ADMIN"))
curl -s -X DELETE http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## Security annotation quick reference

| Location | Annotation | SpEL expression | Meaning |
|---|---|---|---|
| `DocumentService#createDocument` | `@PreAuthorize` | `isAuthenticated()` | Any logged-in user |
| `DocumentService#getDocumentById` | `@PostAuthorize` | `returnObject.ownerUsername == authentication.name or returnObject.visibility.name() == 'PUBLIC' or hasAnyRole(...)` | Owner, public, or privileged role |
| `DocumentService#getPublicDocuments` | `@PostFilter` | `filterObject.visibility.name() == 'PUBLIC' or hasAnyRole(...)` | Silently removes private docs from the list |
| `DocumentService#getAllDocuments` | `@PreAuthorize` | `hasAnyRole('ADMIN', 'MODERATOR')` | Admin or moderator only |
| `DocumentService#bulkCreateDocuments` | `@PreFilter` | `filterObject.ownerUsername == authentication.name or hasRole('ADMIN')` | Silently drops items the caller doesn't own |
| `DocumentService#deleteAllDocumentsOf` | `@PreAuthorize` | `hasRole('ADMIN') or authentication.name == #username` | Admin or the user themselves |
| `UserService#getAllUsers` | `@Secured` | `"ROLE_ADMIN"` | Admin only (no SpEL) |
| `UserService#getUserById` | `@PreAuthorize` | `hasAnyRole('ADMIN', 'MODERATOR')` | Admin or moderator |
| `UserService#updateUserRole` | `@PreAuthorize` | `hasRole('ADMIN')` | Admin only |
| `UserService#deleteUser` | `@Secured` | `"ROLE_ADMIN"` | Admin only (no SpEL) |
