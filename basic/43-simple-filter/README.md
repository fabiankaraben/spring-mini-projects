# Simple Filter Mini-Project

This project is a simple Spring Boot application demonstrating the creation and usage of a standard `javax.servlet.Filter` (Jakarta EE namespace in Spring Boot 3+) for low-level request manipulation/logging.

## 📝 Project Description

The application defines a custom filter `RequestLoggingFilter` that intercepts HTTP requests to log:
- The HTTP Method (GET, POST, etc.)
- The Request URI
- The time taken to process the request

This demonstrates how to tap into the Servlet lifecycle in a Spring Boot application using the `@Component` annotation for automatic registration, or standard Filter interfaces.

## 🚀 Requirements

- Java 21+
- Maven (Wrapper included)

## 🛠️ How to Run

### Using Maven Wrapper

1. **Clone the repository** (if not already done).
2. **Navigate** to the project directory:
   ```bash
   cd basic/43-simple-filter
   ```
3. **Run the application**:
   ```bash
   ./mvnw spring-boot:run
   ```

The application will start on port `8080`.

## 🧪 How to Use

You can test the filter by making requests to the sample endpoint `/hello`.

### Example with curl

```bash
curl -v http://localhost:8080/hello
```

**Response:**
```text
Hello World!
```

**Application Logs (Console Output):**
You should see logs similar to:
```text
INFO ... com.example.simplefilter.filter.RequestLoggingFilter : Incoming request: GET /hello
INFO ... com.example.simplefilter.filter.RequestLoggingFilter : Request processing completed in 5 ms
```

## 🧪 Running Tests

This project includes:
- **Unit Tests**: Using Mockito to verify the filter logic in isolation.
- **Integration Tests**: Using `@WebMvcTest` to verify the controller layer.

To run the tests:

```bash
./mvnw test
```
