# Request Scoped Beans

This mini-project demonstrates the use of `@RequestScope` in Spring Boot. It shows how to create beans that are instantiated once per HTTP request, allowing for stateful services within the context of a single request while maintaining statelessness across the application.

## Requirements

- Java 21+
- Maven (or use the provided Maven Wrapper)

## Project Structure

- **RequestData**: A `@RequestScope` bean that holds data specific to a single request (ID, logs, client info).
- **RequestProcessingService**: A service that injects `RequestData` and modifies it. Note that even though the service is a singleton, it accesses the request-scoped proxy.
- **RequestController**: The entry point that orchestrates the flow.
- **RequestLogFilter**: A servlet filter that also interacts with the request-scoped bean, demonstrating that the scope is available during the filter chain execution.

## How to Run

1. **Build the project**:
   ```bash
   ./mvnw clean package
   ```

2. **Run the application**:
   ```bash
   ./mvnw spring-boot:run
   ```

## Usage

You can test the application using `curl`.

### Process Request

This endpoint triggers the entire flow: Filter -> Controller -> Service. All components interact with the same instance of `RequestData` for that specific request.

```bash
curl "http://localhost:8080/api/process?input=hello-world"
```

**Response Example:**

```json
{
  "requestId": "c4d2e8a1-5b6f-4d3e-9c1a-2b8f7e6d5a4c",
  "clientInfo": "Client-User-Agent-Placeholder",
  "processingLogs": [
    "Filter: Request intercepted",
    "Controller received request with input: hello-world",
    "Service processing input: hello-world",
    "Service finished processing: HELLO-WORLD",
    "Controller preparing response"
  ]
}
```

Notice that the `requestId` changes with every request, and the logs accumulate only for the current request.

## Running Tests

The project includes Unit Tests (using Mockito) and Integration Tests (using `@WebMvcTest`).

```bash
./mvnw test
```
