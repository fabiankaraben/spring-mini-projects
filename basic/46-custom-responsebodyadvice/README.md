# Custom ResponseBodyAdvice Mini-Project

## Overview

This mini-project demonstrates how to use **Custom ResponseBodyAdvice** in Spring Boot to intercept and modify API responses before they are serialized and sent to the client. ResponseBodyAdvice is a powerful feature that allows you to globally modify response bodies from controllers, such as adding metadata, logging, or transforming data.

In this example, the custom advice adds a `processedAt` timestamp to all responses that are `Map<String, Object>` types, indicating when the response was processed by the server.

## Requirements

- **Java**: Version 21 or higher
- **Maven**: 3.6+ (or use the included Maven Wrapper)
- **Spring Boot**: 3.2.1 (automatically managed by Maven)

## Project Structure

```
src/
├── main/java/com/example/customresponsebodyadvice/
│   ├── CustomResponsebodyadviceApplication.java  # Main Spring Boot application
│   ├── CustomResponseBodyAdvice.java             # Custom ResponseBodyAdvice implementation
│   └── DemoController.java                       # REST controller with demo endpoints
└── test/java/com/example/customresponsebodyadvice/
    ├── CustomResponseBodyAdviceTest.java        # Unit tests for the advice
    └── DemoControllerIntegrationTest.java       # Integration tests using @WebMvcTest
```

## How to Run the Application

1. **Clone or navigate to the project directory**

2. **Build the project**:
   ```bash
   ./mvnw clean compile
   ```

3. **Run the application**:
   ```bash
   ./mvnw spring-boot:run
   ```

   The application will start on `http://localhost:8080`

## Usage

Once the application is running, you can test the endpoints using `curl` or any HTTP client.

### Get Demo Data
Returns sample data with the custom `processedAt` field added by the ResponseBodyAdvice.

```bash
curl -X GET http://localhost:8080/api/demo/data
```

**Response Example:**
```json
{
  "message": "This is a demo response from the Custom ResponseBodyAdvice mini-project",
  "status": "success",
  "data": {
    "key1": "value1",
    "key2": 42
  },
  "processedAt": "2023-10-15T10:30:45.123Z"
}
```

### Get User Info
Returns user information with the `processedAt` timestamp.

```bash
curl -X GET http://localhost:8080/api/demo/user
```

**Response Example:**
```json
{
  "userId": 12345,
  "username": "demoUser",
  "email": "demo@example.com",
  "processedAt": "2023-10-15T10:31:12.456Z"
}
```

## How to Run Tests

The project includes both unit tests and integration tests.

### Run All Tests
```bash
./mvnw test
```

### Run Unit Tests Only
```bash
./mvnw test -Dtest=CustomResponseBodyAdviceTest
```

### Run Integration Tests Only
```bash
./mvnw test -Dtest=DemoControllerIntegrationTest
```

### Test Results
- **Unit Tests**: Test the `CustomResponseBodyAdvice` logic in isolation using JUnit 5 and Mockito
- **Integration Tests**: Test the full web layer including the controller and advice using `@WebMvcTest`

## Key Concepts

### ResponseBodyAdvice
- Interface that allows intercepting response bodies before serialization
- Applied globally via `@ControllerAdvice`
- Methods: `supports()` to determine applicability, `beforeBodyWrite()` to modify the body

### Educational Notes
- This project demonstrates aspect-oriented programming concepts in Spring
- The advice is applied transparently to controllers without modifying their code
- Useful for cross-cutting concerns like logging, auditing, or response enrichment
- The `processedAt` field simulates a common use case of adding server-side metadata

## Dependencies

- `spring-boot-starter-web`: For REST API functionality
- `spring-boot-starter-test`: For testing (includes JUnit 5, Mockito, MockMvc)

This mini-project is completely independent and can be run standalone without any external dependencies or Docker containers.
