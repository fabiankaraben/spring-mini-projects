# Session Management Mini-Project

## Overview

This mini-project demonstrates session management in a Spring Boot application. It provides a simple REST API that allows storing and retrieving user-specific data across HTTP requests using `HttpSession`. This is useful for maintaining state for users in web applications without relying on external databases for session storage.

The application stores a user's name in the session and allows retrieval in subsequent requests. Sessions are managed in-memory by default in Spring Boot, but can be configured to use external stores like Redis for production use.

## Requirements

- **Java**: Version 21 or higher
- **Maven**: Version 3.6 or higher (Maven Wrapper is included)
- **Operating System**: Any OS that supports Java and Maven

## Project Structure

```
src/
├── main/
│   └── java/com/example/sessionmanagement/
│       ├── SessionManagementApplication.java  # Main Spring Boot application class
│       └── SessionController.java              # REST controller for session operations
└── test/
    └── java/com/example/sessionmanagement/
        ├── SessionControllerUnitTest.java      # Unit tests with Mockito
        └── SessionControllerIntegrationTest.java # Integration tests with @WebMvcTest
```

## Dependencies

- **Spring Boot Starter Web**: For building REST APIs
- **Spring Boot Starter Test**: For testing (includes JUnit 5, Mockito, and Spring Test)

## How to Run the Application

1. Ensure Java 21 is installed and configured.

2. Clone or navigate to the project directory.

3. Run the application using the Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```

4. The application will start on `http://localhost:8080`.

## API Usage

The application exposes two REST endpoints under the `/api/session` path:

### 1. Set User Name

**Endpoint**: `POST /api/session/user`

**Description**: Stores a user's name in the session.

**Parameters**:
- `name` (String): The user's name to store

**Example using curl**:
```bash
curl -X POST "http://localhost:8080/api/session/user?name=John%20Doe"
```

**Response**:
- Status: 200 OK
- Body: `"User name 'John Doe' stored in session."`

### 2. Get User Name

**Endpoint**: `GET /api/session/user`

**Description**: Retrieves the user's name from the session.

**Example using curl**:
```bash
curl http://localhost:8080/api/session/user
```

**Response** (if user is set):
- Status: 200 OK
- Body: `"Hello, John Doe!"`

**Response** (if no user is set):
- Status: 404 Not Found
- Body: Empty

### Session Behavior

- Sessions are maintained using cookies. The server sends a `JSESSIONID` cookie to the client.
- Subsequent requests from the same client will use the same session.
- Session data persists as long as the session is active (default timeout is 30 minutes).

## How to Run Tests

The project includes both unit tests and integration tests.

### Unit Tests

Unit tests use JUnit 5 and Mockito to test the controller logic in isolation.

### Integration Tests

Integration tests use `@WebMvcTest` to test the web layer, including session handling.

### Running All Tests

Use the Maven Wrapper to run all tests:

```bash
./mvnw test
```

### Running Specific Test Classes

Run unit tests only:
```bash
./mvnw test -Dtest=SessionControllerUnitTest
```

Run integration tests only:
```bash
./mvnw test -Dtest=SessionControllerIntegrationTest
```

## Configuration

The application uses default Spring Boot configurations. For production use, you might want to configure external session storage (e.g., Redis) by adding dependencies and configuration properties.

Example for Redis session storage (not included in this mini-project):
- Add `spring-session-data-redis` dependency
- Configure Redis connection properties

## Notes

- This mini-project uses in-memory session storage, which is not suitable for production deployments with multiple instances.
- All code is documented with comments for educational purposes.
- The project is independent and does not depend on other mini-projects in the repository.
