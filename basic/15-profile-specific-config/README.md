# Profile Specific Config

🔹 This is a backend in Spring Boot demonstrating the use of `@Profile` to load different Spring beans and configuration properties based on the active environment.

## 📝 Description

Spring Boot's `@Profile` annotation is a core feature that helps segregate parts of your application configuration and bean definitions making them available only in specific environments.
This mini-project demonstrates how to:
1. Load environment-specific properties (`application-dev.properties`, `application-prod.properties`).
2. Conditionally inject Spring beans matching the active profile by leveraging interfaces and multiple service implementations.

## 📋 Requirements
- **Java 21** or later
- **Maven Wrapper** (included)
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-test`
- No Docker required (this application stands independently).

## 🚀 How to Run

When running the application, you can override the active profiles. The application relies on `dev` as its default profile configuration (defined in `application.properties`).

1. **Run with the default `dev` profile:**
   ```bash
   ./mvnw spring-boot:run
   ```

2. **Run with the `prod` profile:**
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
   ```

3. **Run with no profile (Default):**
    If we erase or overwrite the property explicitly to empty:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=default
   ```

## 🛠 Usage & curl Examples

The application exposes a single endpoint under `/api/status` that outputs the current profile and the message from the injected bean.

**If started with `dev` profile (default based on properties):**
```bash
curl -X GET http://localhost:8080/api/status
```
*Expected Output:*
```json
{
  "environment": "Development",
  "message": "You are connected to the DEVELOPMENT module. Experimental features enabled!"
}
```

**If started with `prod` profile:**
```bash
curl -X GET http://localhost:8080/api/status
```
*Expected Output:*
```json
{
  "environment": "Production",
  "message": "You are connected to the PRODUCTION module. System is stable and highly available."
}
```

## 🧪 How to Run Tests

The application includes basic unit and sliced integration tests (`@WebMvcTest`). We use unit tests with Mockito to test controllers in isolation, and `@SpringBootTest` alongside `@ActiveProfiles` to verify beans initialize correctly against their corresponding profiles.

Run the tests using the Maven wrapper:

```bash
./mvnw clean test
```
