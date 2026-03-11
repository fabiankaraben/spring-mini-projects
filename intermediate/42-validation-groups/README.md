# 42 – Validation Groups

A Spring Boot backend that demonstrates **Bean Validation Groups** – applying different sets of validation rules to the same DTO depending on the operation being performed (create, update, or password change).

---

## What This Project Demonstrates

Standard Bean Validation applies all constraints on a DTO every time it is validated. Validation Groups let you **activate only a subset of constraints** per operation by tagging each constraint with a group marker interface and telling the controller which group to use via `@Validated(SomeGroup.class)`.

### The Three Groups

| Group | Marker Interface | Used by | Required fields |
|---|---|---|---|
| **OnCreate** | `OnCreate.class` | `POST /api/users` (via `UserValidationSequence`) | `name`, `email`, `password`, `role` |
| **OnUpdate** | `OnUpdate.class` | `PATCH /api/users/{id}` | `name`, `email` |
| **OnPasswordChange** | `OnPasswordChange.class` | `PUT /api/users/{id}/password` | `newPassword`, `confirmPassword` |

### Field Activation Matrix

```
Field           | OnCreate | OnUpdate | OnPasswordChange
----------------|----------|----------|-----------------
name            |    ✓     |    ✓     |        ✗
email           |    ✓     |    ✓     |        ✗
password        |    ✓     |    ✗     |        ✗
role            |    ✓     |    ✗     |        ✗
newPassword     |    ✗     |    ✗     |        ✓
confirmPassword |    ✗     |    ✗     |        ✓
```

A field marked `✗` for the active group is completely ignored by the validator – it may be `null` or even invalid without producing an error.

### Ordered Validation with `@GroupSequence`

The create endpoint uses `UserValidationSequence` (which is `@GroupSequence({Default.class, OnCreate.class})`). This ensures that basic format constraints (e.g. `@Email`, `@Size`) are checked first, and only if they pass does the validator check the `OnCreate`-specific constraints. This produces cleaner, ordered error messages.

---

## Requirements

- **Java 21+**
- **Maven** (or use the included Maven Wrapper `./mvnw`)
- **Docker & Docker Compose** (required to run the full stack)

---

## Project Structure

```
src/
├── main/java/com/example/validationgroups/
│   ├── ValidationGroupsApplication.java   # Spring Boot entry point
│   ├── validation/
│   │   ├── OnCreate.java                  # Marker interface for CREATE group
│   │   ├── OnUpdate.java                  # Marker interface for UPDATE group
│   │   ├── OnPasswordChange.java          # Marker interface for PASSWORD CHANGE group
│   │   └── UserValidationSequence.java    # @GroupSequence(Default → OnCreate)
│   ├── domain/
│   │   └── User.java                      # JPA entity
│   ├── dto/
│   │   ├── UserRequest.java               # Unified request DTO with group-annotated constraints
│   │   └── UserResponse.java              # Read-only response DTO (no password)
│   ├── repository/
│   │   └── UserRepository.java            # Spring Data JPA repository
│   ├── service/
│   │   └── UserService.java               # Business logic layer
│   ├── controller/
│   │   └── UserController.java            # REST endpoints with @Validated group selection
│   └── exception/
│       ├── GlobalExceptionHandler.java    # @RestControllerAdvice – maps exceptions to HTTP codes
│       ├── UserNotFoundException.java     # 404
│       ├── EmailAlreadyExistsException.java # 409
│       └── PasswordMismatchException.java # 400
└── test/java/com/example/validationgroups/
    ├── unit/
    │   └── UserServiceTest.java           # JUnit 5 + Mockito unit tests
    └── integration/
        └── ValidationGroupsIntegrationTest.java  # Testcontainers + MockMvc integration tests
```

---

## Running with Docker Compose

The entire stack (PostgreSQL + Spring Boot app) runs via Docker Compose.

### Start

```bash
docker compose up --build
```

The app will be available at `http://localhost:8080`.

### Stop

```bash
docker compose down
```

### Stop and remove volumes (wipes database data)

```bash
docker compose down -v
```

---

## API Reference & curl Examples

Base URL: `http://localhost:8080`

### Create a User – `POST /api/users`

**Validation group: `UserValidationSequence` (Default → OnCreate)**

All of `name`, `email`, `password`, and `role` are required.

```bash
curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Smith",
    "email": "alice@example.com",
    "password": "securePass123",
    "role": "USER"
  }' | jq
```

**Missing password → 400 (OnCreate requires it):**

```bash
curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Bob Jones",
    "email": "bob@example.com",
    "role": "USER"
  }' | jq
```

**Invalid role → 400 (must be USER, ADMIN, or MODERATOR):**

```bash
curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Carol White",
    "email": "carol@example.com",
    "password": "securePass123",
    "role": "SUPERUSER"
  }' | jq
```

---

### List All Users – `GET /api/users`

```bash
curl -s http://localhost:8080/api/users | jq
```

### Get User by ID – `GET /api/users/{id}`

```bash
curl -s http://localhost:8080/api/users/1 | jq
```

### Search Users by Name – `GET /api/users/search?name=...`

```bash
curl -s "http://localhost:8080/api/users/search?name=alice" | jq
```

### Filter Users by Role – `GET /api/users/role/{role}`

```bash
curl -s http://localhost:8080/api/users/role/ADMIN | jq
```

---

### Update User – `PATCH /api/users/{id}`

**Validation group: `OnUpdate`**

Only `name` and `email` are validated and applied. `password` and `role` are **completely ignored** – no validation error is produced if they are absent or even invalid.

```bash
curl -s -X PATCH http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Johnson",
    "email": "alice.johnson@example.com"
  }' | jq
```

**KEY: Omitting password is perfectly valid with OnUpdate:**

```bash
# No "password" field – no error, even though it would be required on create
curl -s -X PATCH http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Updated",
    "email": "alice.updated@example.com"
  }' | jq
```

**Attempting to change role via update (silently ignored):**

```bash
# Sending "role": "ADMIN" has no effect – the service ignores it
curl -s -X PATCH http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Updated",
    "email": "alice.updated@example.com",
    "role": "ADMIN"
  }' | jq
```

---

### Change Password – `PUT /api/users/{id}/password`

**Validation group: `OnPasswordChange`**

Only `newPassword` (min 8 chars) and `confirmPassword` are required. All other fields are completely ignored by the validator.

```bash
curl -s -X PUT http://localhost:8080/api/users/1/password \
  -H "Content-Type: application/json" \
  -d '{
    "newPassword": "newSecurePass123",
    "confirmPassword": "newSecurePass123"
  }' | jq
```

**KEY: Blank name and invalid email are ignored (OnPasswordChange group is active):**

```bash
curl -s -X PUT http://localhost:8080/api/users/1/password \
  -H "Content-Type: application/json" \
  -d '{
    "name": "",
    "email": "not-an-email",
    "newPassword": "newSecurePass123",
    "confirmPassword": "newSecurePass123"
  }' | jq
```

**Mismatched passwords → 400:**

```bash
curl -s -X PUT http://localhost:8080/api/users/1/password \
  -H "Content-Type: application/json" \
  -d '{
    "newPassword": "newSecurePass123",
    "confirmPassword": "differentPassword"
  }' | jq
```

---

### Delete User – `DELETE /api/users/{id}`

```bash
curl -s -X DELETE http://localhost:8080/api/users/1
# Returns HTTP 204 No Content on success
```

---

## Running Tests

Tests require Docker to be running (Testcontainers spins up a PostgreSQL container).

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests

```bash
./mvnw test -Dtest="**/unit/**"
```

### Run only integration tests

```bash
./mvnw test -Dtest="**/integration/**"
```

---

## Test Coverage

### Unit Tests (`UserServiceTest`)

Pure JUnit 5 + Mockito tests – no Spring context, no database.

| Test | What it verifies |
|---|---|
| `findAll_shouldReturnAllUsers` | Service delegates to repository and returns results |
| `findById_shouldReturnUser_whenFound` | Happy path lookup |
| `findById_shouldThrow_whenUserNotFound` | `UserNotFoundException` for missing ID |
| `create_shouldSaveAndReturnUser` | User entity is persisted with correct data |
| `create_shouldThrow_whenEmailAlreadyExists` | Duplicate email guard |
| `update_shouldChangeNameAndEmailOnly` | Password and role remain untouched after update |
| `update_shouldThrow_whenUserNotFound` | 404 guard in update |
| `update_shouldThrow_whenNewEmailIsAlreadyTaken` | Email uniqueness on update |
| `update_shouldAllowKeepingSameEmail` | Same-email update does not trigger conflict |
| `changePassword_shouldUpdatePassword_whenPasswordsMatch` | Password is persisted |
| `changePassword_shouldThrow_whenPasswordsDoNotMatch` | Cross-field mismatch guard |
| `changePassword_shouldThrow_whenUserNotFound` | 404 guard in change-password |
| `delete_shouldCallDeleteById` | Repository `deleteById` is invoked |
| `delete_shouldThrow_whenUserNotFound` | 404 guard in delete |
| `searchByName_shouldReturnMatchingUsers` | Name search delegation |
| `findByRole_shouldReturnUsersWithRole` | Role filter delegation |
| `userResponse_shouldMapCorrectly` | DTO mapping excludes password |

### Integration Tests (`ValidationGroupsIntegrationTest`)

Full Spring Boot context + real PostgreSQL via Testcontainers + MockMvc.

| Test | What it verifies |
|---|---|
| `create_withAllRequiredFields_shouldReturn201` | OnCreate happy path |
| `create_withMissingPassword_shouldReturn400` | OnCreate requires password |
| `create_withMissingRole_shouldReturn400` | OnCreate requires role |
| `create_withInvalidRole_shouldReturn400` | @Pattern on role |
| `create_withInvalidEmail_shouldReturn400` | @Email constraint |
| `create_withShortPassword_shouldReturn400` | @Size(min=8) on password |
| `create_withDuplicateEmail_shouldReturn409` | Email uniqueness guard |
| `update_withValidNameAndEmail_shouldReturn200_andPasswordNotRequired` | **Core: password absent → no error with OnUpdate** |
| `update_withRoleInBody_shouldIgnoreRole` | **Core: role in update body is silently ignored** |
| `update_withBlankName_shouldReturn400` | OnUpdate enforces name constraint |
| `update_withNonExistentId_shouldReturn404` | 404 on update |
| `changePassword_withMatchingPasswords_shouldReturn204` | OnPasswordChange happy path |
| `changePassword_withMissingNewPassword_shouldReturn400` | OnPasswordChange requires newPassword |
| `changePassword_withMismatchedPasswords_shouldReturn400` | Cross-field mismatch guard |
| `changePassword_withShortNewPassword_shouldReturn400` | @Size(min=8) on newPassword |
| `changePassword_withExtraNameField_shouldIgnoreItAndReturn204` | **Core: blank name + invalid email ignored with OnPasswordChange** |
| `listAll_shouldReturnAllUsers` | GET /api/users |
| `getById_shouldReturnUser` | GET /api/users/{id} |
| `getById_withNonExistentId_shouldReturn404` | 404 on GET |
| `search_shouldReturnMatchingUsers` | Name search endpoint |
| `byRole_shouldReturnUsersWithRole` | Role filter endpoint |
| `delete_shouldRemoveUser` | DELETE endpoint |
| `delete_withNonExistentId_shouldReturn404` | 404 on DELETE |
| `fullLifecycle_demonstratingAllThreeGroups` | Full create → update → change-password flow |
