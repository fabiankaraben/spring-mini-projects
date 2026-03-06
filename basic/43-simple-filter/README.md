# Simple Filter

This mini-project demonstrates the use of a `javax.servlet.Filter` in a Spring Boot application for low-level request manipulation. The filter logs incoming HTTP requests and adds a custom header to the response.

## Requirements

- Java 21 or higher
- Maven 3.6+ (or use the included Maven Wrapper)

## Dependencies

- Spring Boot 3.2.1 (with Jakarta EE)
- JUnit 5 for unit testing
- Mockito for mocking in tests

## Project Structure

```
src/
├── main/java/com/example/simplefilter/
│   ├── SimpleFilterApplication.java    # Main Spring Boot application class
│   ├── LoggingFilter.java              # Custom servlet filter implementation
│   └── TestController.java             # REST controller for testing
└── test/java/com/example/simplefilter/
    ├── LoggingFilterTest.java          # Unit tests for the filter
    └── TestControllerTest.java          # Integration tests for the controller
```

## How to Use

1. **Build the project:**
   ```bash
   ./mvnw clean compile
   ```

2. **Run the application:**
   ```bash
   ./mvnw spring-boot:run
   ```
   The application will start on `http://localhost:8080`.

3. **Test the filter with curl:**
   ```bash
   curl -v http://localhost:8080/hello
   ```
   You should see the custom header `X-Simple-Filter: Processed` in the response, and the request will be logged in the console.

   Example output:
   ```
   * Connected to localhost (127.0.0.1) port 8080 (#0)
   > GET /hello HTTP/1.1
   > Host: localhost:8080
   > User-Agent: curl/7.68.0
   > Accept: */*
   >
   * Mark bundle as not supporting multiuse
   < HTTP/1.1 200 OK
   < X-Simple-Filter: Processed
   < Content-Type: application/json
   < Transfer-Encoding: chunked
   < Date: Thu, 06 Mar 2026 22:54:30 GMT
   <
   Hello, World! This request was processed by the LoggingFilter.
   ```

## How to Run the Tests

Run all tests:
```bash
./mvnw test
```

Run specific test classes:
```bash
./mvnw test -Dtest=LoggingFilterTest
./mvnw test -Dtest=TestControllerTest
```

## Explanation

- **LoggingFilter**: A `jakarta.servlet.Filter` that intercepts all incoming requests. It logs the HTTP method and URI, adds a custom header `X-Simple-Filter` to the response, and then continues the filter chain.

- **TestController**: Provides a simple `/hello` endpoint to demonstrate the filter's functionality.

- **Unit Tests**: `LoggingFilterTest` uses Mockito to mock servlet objects and verify the filter's behavior.

- **Integration Tests**: `TestControllerTest` uses `@WebMvcTest` to test the controller in isolation.

This project showcases how filters can be used for cross-cutting concerns like logging, security, or request/response modification in Spring Boot applications.
