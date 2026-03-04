# 06. Basic Router

## Overview
**Basic Router** is a minimal backend Spring Boot application that demonstrates the implementation of multiple HTTP mapping annotations (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, etc.). This project serves as an educational base for understanding REST controller structures, dependency injection, and in-memory simulated database management in Spring.

## Requirements
*   **Java**: 21
*   **Dependency Manager**: Maven
*   **Spring Boot**: 3.4.3
*   **Testing**: JUnit 5, Mockito, and Sliced Integration testing (`@WebMvcTest`)
*   **Docker**: Not required for this basic implementation.

## Project Structure
*   **`model.Course`**: A Java Record representing a simple data entity.
*   **`service.CourseService` & `service.CourseServiceImpl`**: The business logic and in-memory state representation using `ConcurrentHashMap`.
*   **`controller.CourseController`**: Exposes the REST API mapping endpoints using mapped annotations.

## Usage

### 1. Build and Run the Application
Navigate to the `basic/06-basic-router` directory and use the embedded Maven Wrapper to run the application:

```bash
./mvnw spring-boot:run
```

The server will start on `http://localhost:8080`.

### 2. Available Endpoints

You can interact with the API using `curl`. Below are some examples.

**Get all courses:**
```bash
curl -X GET http://localhost:8080/api/courses
```

**Get a specific course by ID:**
```bash
curl -X GET http://localhost:8080/api/courses/1
```

**Create a new course:**
```bash
curl -X POST http://localhost:8080/api/courses \
  -H 'Content-Type: application/json' \
  -d '{"title": "Spring Boot Testing", "description": "Master Spring Boot tests"}'
```

**Update an existing course:**
```bash
curl -X PUT http://localhost:8080/api/courses/1 \
  -H 'Content-Type: application/json' \
  -d '{"title": "Updated Basic Java", "description": "Updated Description"}'
```

**Delete a course:**
```bash
curl -X DELETE http://localhost:8080/api/courses/1 -v
```

## Testing

This project includes unit tests for the controller using sliced testing (`@WebMvcTest`) to isolate the web layer. We use `@MockitoBean` to mock the service layer completely so tests are purely checking controller mapping, parameter parsing, and serialization.

To run the unit and integration tests, use the following Maven command:

```bash
./mvnw test
```
