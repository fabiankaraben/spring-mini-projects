# Simple Scheduled Task Mini-Project

This is a standalone Spring Boot backend project that demonstrates how to run background jobs using the `@Scheduled` annotation.

## 📝 Features & Requirements

- **Java 21+** minimum required version.
- **Maven Wrapper** provided to avoid installing Maven globally.
- **Dependency Manager:** Maven.
- Demonstrates periodic task execution in the background using `@Scheduled`.
- Explores `@EnableScheduling` to enable task processing.
- Simple REST Controller (`GET /api/status`) to retrieve the execution count and the last timestamp.
- Educational comments provided inside each file to guide your understanding.

## 🚀 How to Run

1. **Build and test the application** using the Maven Wrapper:
   ```bash
   ./mvnw clean test
   ```

2. **Start the application**:
   ```bash
   ./mvnw spring-boot:run
   ```
   You will notice in the console logs that a task runs every 5 seconds.

3. **Check Job Status Using cURL**:
   You can query the current state of the scheduled jobs by pinging the endpoint exposed by `JobStatusController`:
   ```bash
   curl -X GET http://localhost:8080/api/status
   ```
   *Expected Response Example:*
   ```json
   {
     "totalExecutions": 4,
     "lastExecutionTime": "2025-01-01T12:00:20"
   }
   ```

## 🧪 Testing
The project includes both **Unit Tests** and **Sliced Integration Tests**:
- `ScheduledJobTest` utilizes `JUnit 5` and `Mockito` to test the task logic in isolation.
- `JobStatusControllerTest` utilizes `@WebMvcTest` to safely bring up only the Web components into the test context and effectively verify the HTTP responses using `MockMvc`.

Run tests with:
```bash
./mvnw clean test
```
