# Basic AOP Logging

This mini-project demonstrates how to implement **Aspect-Oriented Programming (AOP)** in a Spring Boot application using AspectJ annotations to log method entry, execution timing, success, and exceptions in the service layer.

## 📝 Description

In this application, we use a simple set of REST endpoints triggered by an HTTP Client (such as `curl` or Postman) that execute logic in the `UserService`.
Instead of manually adding `Logger` statements inside the service layer, we offload this cross-cutting concern to the `LoggingAspect`. The Aspect uses **Pointcuts** to define *where* the logging should happen (all methods in the `service` package) and **Advices** (`@Before`, `@AfterReturning`, `@AfterThrowing`, `@Around`) to define *what* happens when it hits these points. This achieves separation of concerns and keeps core service code clean.

## 🛠 Features / Requirements

- **Java 21+** minimum version requirement (utilizes Java Records for Models).
- **Spring Boot 3** Web & AOP starters.
- **Maven** with internal Wrapper included.
- Defines Pointcuts dynamically via AspectJ syntax expression `execution(...)`.
- Tested with **JUnit 5** and **Mockito**.
- Integrates Sliced Integration Tests with `@WebMvcTest` replacing the deprecated `@MockBean` with `@MockitoBean`.

## 🚀 How to Run

1. Open a terminal and navigate to the root directory `basic/22-basic-aop-logging`.
2. Compile and run the application via Maven Wrapper:
```bash
./mvnw spring-boot:run
```
3. The server starts up dynamically on port `8080` by default. Observe console outputs when executing `curl` commands below.

## 🎯 Usage Examples (cURL)

**1. Create a User (Observe `@Before` and `@AfterReturning`)**
```bash
curl -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"name": "Alice"}'
```

**2. Create Another User**
```bash
curl -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"name": "Bob"}'
```

**3. Retrieve All Users (Observe timing metric via `@Around`)**
```bash
curl -X GET http://localhost:8080/api/users
```

**4. Retrieve a Specific User by ID**
```bash
curl -X GET http://localhost:8080/api/users/1
```

**5. Delete a user**
```bash
curl -X DELETE http://localhost:8080/api/users/1
```

**6. Trigger an Internal Exception (Observe `@AfterThrowing`)**
```bash
curl -X GET http://localhost:8080/api/users/error
```
*Note: This will return a `500 Internal Server Error` and log the captured exception via `LoggingAspect`.*

## 🧪 Testing

To execute all unit and sliced integration tests:

```bash
./mvnw test
```

We specifically use:
- **`@WebMvcTest`**: Focuses purely on testing the Spring MVC components (Controllers).
- **`@MockitoBean`**: Creates and injects a Mockito mock instance for our `UserService` inside the MVC test, preventing dependencies from actually starting and loading.
