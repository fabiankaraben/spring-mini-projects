# 02. Static File Server

This is an educational mini-project demonstrating how to use **Spring Boot** to serve static resources (like HTML, CSS, and JS) from a backend. Spring MVC automatically configures a `ResourceHttpRequestHandler` out of the box to serve any resources placed in the `src/main/resources/static`, `public`, or `resources` directory directly to clients without writing explicit controller endpoints.

To also demonstrate some minimal testing practices (such as using `JUnit 5`, `Mockito`, and sliced testing), a small `HealthController` and `HealthService` the testing section. Since this application has no external requirements like a database or object storage, there is no Docker setup required.

## 🎯 Requirements

*   **Java**: Version 21 or higher.
*   **Dependency Manager**: Maven (via the included Wrapper).
*   **Testing Technologies**: JUnit 5, Mockito, Spring Boot `@WebMvcTest`, and `@SpringBootTest` alongside `MockMvc`.
*   **Docker Requirement**: None (No databases or external setups).

## 🚀 How to Use

The application serves static files over HTTP at `http://localhost:8080`.

1. **Start the Application**
   Run the following command from the root of this mini-project:
   ```bash
   ./mvnw spring-boot:run
   ```

2. **Accessing Static Resources (cURL Examples)**

   *   **index.html**: Since it is named index, you can request it natively or via the root path.
       ```bash
       curl -i http://localhost:8080/
       ```
       Or explicitly:
       ```bash
       curl -i http://localhost:8080/index.html
       ```

   *   **style.css**:
       ```bash
       curl -i http://localhost:8080/style.css
       ```

   *   **script.js**:
       ```bash
       curl -i http://localhost:8080/script.js
       ```

3. **Bonus: The Health API Endpoint**  
   We've included an endpoint just to demonstrate sliced testing with Mockito.
   ```bash
   curl -i http://localhost:8080/api/health
   ```

## 🧪 How to Run Tests

This mini-project extensively utilizes both Unit and Integration Testing:
- **Unit Testing inside Sliced Tests**: `HealthControllerTest` uses `@WebMvcTest` with `@MockBean` (Mockito) to slice a specific controller layer from the application context.
- **Integration Tests**: `StaticResourceTest` uses `@SpringBootTest` to evaluate the actual auto-configuration serving static resources.

Run the test suite using the Maven Wrapper:

```bash
./mvnw test
```

This ensures both our Mocked WebMvc setups and the complete static resource serving contexts perform without errors.
