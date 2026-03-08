# MockMvc Testing Mini-Project

This is a backend in Spring Boot showing how to set up simple `@WebMvcTest` slices without the full context. It demonstrates how to test Spring MVC controllers in isolation by mocking the service layer.

## Requirements

-   **Java**: 21
-   **Maven**: 3.9+ (Wrapper provided)

## Project Structure

-   `src/main/java`: Application source code.
    -   `model`: `User` entity.
    -   `service`: `UserService` (logic layer).
    -   `controller`: `UserController` (REST endpoints).
-   `src/test/java`: Tests.
    -   `controller`: `UserControllerTest` using `@WebMvcTest` and `MockMvc`.

## How to Run the Application

1.  **Build the project:**

    ```bash
    ./mvnw clean install
    ```

2.  **Run the application:**

    ```bash
    ./mvnw spring-boot:run
    ```

    The application will start on port `8080`.

## API Usage Examples (curl)

### Get all users

```bash
curl -v http://localhost:8080/api/users
```

### Get user by ID

```bash
curl -v http://localhost:8080/api/users/1
```

### Create a new user

```bash
curl -v -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Charlie", "email": "charlie@example.com"}'
```

### Delete a user

```bash
curl -v -X DELETE http://localhost:8080/api/users/1
```

## How to Run Tests

This project uses **JUnit 5** and **Mockito**. The tests are focused on the Controller layer using `@WebMvcTest`.

To run the tests, execute:

```bash
./mvnw test
```

### Explanation of Tests

The `UserControllerTest` class demonstrates the following:

-   `@WebMvcTest(UserController.class)`: Tells Spring Boot to only instantiate the web layer (Controllers, ControllerAdvice, etc.) and not the whole context (Services, Repositories).
-   `@Autowired MockMvc`: Injects the `MockMvc` instance to perform HTTP requests against the controller.
-   `@MockitoBean UserService`: Creates a Mockito mock of the `UserService` and adds it to the Spring application context, replacing any real bean. This allows us to define the behavior of the service in our tests using `given(...)`.
-   `mockMvc.perform(...)`: Simulates an HTTP request.
-   `.andExpect(...)`: Asserts the response status, headers, and body content (using JSONPath).
