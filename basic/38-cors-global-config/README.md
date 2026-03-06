# CORS Global Config Mini-Project

## Description

This mini-project demonstrates how to configure Cross-Origin Resource Sharing (CORS) globally in a Spring Boot application for API endpoints. CORS is a security feature implemented by web browsers to prevent web pages from making requests to a different domain than the one that served the web page. By configuring CORS, we allow specific origins to access our API resources.

The project showcases:
- Global CORS configuration using `WebMvcConfigurer`
- REST API endpoints with various HTTP methods (GET, POST, PUT, DELETE)
- Dependency injection with Spring services
- Unit and integration testing with JUnit 5 and Mockito

## Requirements

- Java 21 (minimum version)
- Maven 3.6+ (Maven Wrapper is included for convenience)

## Project Structure

```
src/
├── main/
│   ├── java/com/example/corsconfig/
│   │   ├── ApiController.java          # REST controller with API endpoints
│   │   ├── CorsConfig.java             # Global CORS configuration
│   │   ├── CorsGlobalConfigApplication.java  # Main Spring Boot application
│   │   ├── GreetingService.java        # Service interface
│   │   └── GreetingServiceImpl.java    # Service implementation
│   └── resources/
│       └── application.properties      # Application configuration (empty)
└── test/
    └── java/com/example/corsconfig/
        ├── ApiControllerTest.java      # Integration tests for controller
        └── GreetingServiceImplTest.java # Unit tests for service
```

## How to Run

1. Navigate to the project directory: `basic/38-cors-global-config`
2. Run the application using Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```
3. The application will start on `http://localhost:8080`

## API Endpoints

The application provides the following REST endpoints under the `/api` path:

- `GET /api/hello` - Returns a greeting message
- `POST /api/data` - Accepts JSON data and echoes it back
- `PUT /api/data/{id}` - Simulates updating a resource by ID
- `DELETE /api/data/{id}` - Simulates deleting a resource by ID

## Usage Examples with curl

Since CORS is enforced by browsers, testing with curl requires manually setting the `Origin` header to see CORS headers in the response. The server will include CORS headers like `Access-Control-Allow-Origin` in the response.

### GET Request
```bash
curl -X GET http://localhost:8080/api/hello \
  -H "Origin: http://localhost:3000" \
  -v
```

Expected response:
```
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Credentials: true
...

"Hello from CORS enabled API service!"
```

### POST Request
```bash
curl -X POST http://localhost:8080/api/data \
  -H "Content-Type: application/json" \
  -H "Origin: http://localhost:3000" \
  -d '"sample data"' \
  -v
```

Expected response:
```
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Credentials: true
...

"Received data: sample data"
```

### PUT Request
```bash
curl -X PUT http://localhost:8080/api/data/123 \
  -H "Content-Type: application/json" \
  -H "Origin: http://localhost:3000" \
  -d '"updated data"' \
  -v
```

Expected response:
```
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Credentials: true
...

"Updated resource with ID: 123 with data: updated data"
```

### DELETE Request
```bash
curl -X DELETE http://localhost:8080/api/data/123 \
  -H "Origin: http://localhost:3000" \
  -v
```

Expected response:
```
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Credentials: true
...

"Deleted resource with ID: 123"
```

### Preflight Request (OPTIONS)
For complex requests, browsers send a preflight OPTIONS request:
```bash
curl -X OPTIONS http://localhost:8080/api/data \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: content-type" \
  -v
```

Expected response:
```
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
Access-Control-Allow-Headers: *
Access-Control-Allow-Credentials: true
...
```

## CORS Configuration Details

The `CorsConfig` class implements `WebMvcConfigurer` and overrides `addCorsMappings()` to configure CORS globally:

- **Allowed Origins**: `http://localhost:*` (allows any port on localhost for development)
- **Allowed Methods**: GET, POST, PUT, DELETE, OPTIONS
- **Allowed Headers**: All headers (`*`)
- **Credentials**: Allowed (supports cookies, authorization headers, etc.)

In production, you should specify exact allowed origins instead of using wildcards for security.

## Running Tests

### Unit Tests
Unit tests for individual components:
```bash
./mvnw test -Dtest=GreetingServiceImplTest
```

### Integration Tests
Sliced integration tests for the web layer:
```bash
./mvnw test -Dtest=ApiControllerTest
```

### All Tests
Run all tests:
```bash
./mvnw test
```

The tests use:
- **JUnit 5** for test framework
- **Mockito** for mocking dependencies in integration tests
- **@WebMvcTest** for sliced web layer testing

## Key Concepts Demonstrated

1. **Global CORS Configuration**: Using `WebMvcConfigurer` to apply CORS settings application-wide
2. **REST API Design**: Creating endpoints for different HTTP methods
3. **Dependency Injection**: Using `@Autowired` to inject services into controllers
4. **Service Layer**: Separating business logic into services
5. **Testing Strategies**: Unit tests for services, integration tests for controllers
6. **Maven Project Structure**: Standard Maven directory layout

## Notes

- This project is designed for educational purposes and includes detailed comments in the code.
- The CORS configuration allows requests from `http://localhost:*` for development convenience. In production, restrict origins to specific domains.
- The application does not require any external databases or services, making it self-contained.
