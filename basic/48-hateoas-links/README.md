# Hateoas Links

This mini-project is a Spring Boot application that demonstrates how to add hypermedia links to resources using **Spring HATEOAS**. It follows the HATEOAS (Hypermedia as the Engine of Application State) principle of REST application architecture.

## Requirements

-   **Java 21** or later
-   **Maven** (bundled wrapper included)

## Project Structure

-   `src/main/java/com/example/hateoas/domain/Greeting.java`: The resource representation extending `RepresentationModel`.
-   `src/main/java/com/example/hateoas/service/GreetingService.java`: Service layer containing business logic.
-   `src/main/java/com/example/hateoas/controller/GreetingController.java`: The REST controller that builds links using `WebMvcLinkBuilder`.

## How to Run

1.  **Build and Run the application:**

    ```bash
    ./mvnw spring-boot:run
    ```

2.  The application will start on port `8080`.

## Usage

You can interact with the API using `curl`.

### 1. Default Greeting

Request:
```bash
curl -v http://localhost:8080/greeting
```

Response:
```json
{
  "content": "Hello, World!",
  "_links": {
    "self": {
      "href": "http://localhost:8080/greeting?name=World"
    },
    "greet-spring": {
      "href": "http://localhost:8080/greeting?name=Spring"
    }
  }
}
```

### 2. Custom Greeting

Request:
```bash
curl -v "http://localhost:8080/greeting?name=User"
```

Response:
```json
{
  "content": "Hello, User!",
  "_links": {
    "self": {
      "href": "http://localhost:8080/greeting?name=User"
    },
    "greet-spring": {
      "href": "http://localhost:8080/greeting?name=Spring"
    }
  }
}
```

### 3. "Spring" Greeting (Conditional Link)

Request:
```bash
curl -v "http://localhost:8080/greeting?name=Spring"
```

Response (notice the absence of `greet-spring` link as we are already greeting Spring):
```json
{
  "content": "Hello, Spring!",
  "_links": {
    "self": {
      "href": "http://localhost:8080/greeting?name=Spring"
    }
  }
}
```

## Running Tests

This project includes:
-   **Unit Tests**: For the domain model using JUnit 5.
-   **Integration Tests**: Sliced tests using `@WebMvcTest` and **Mockito** (`@MockitoBean`) to mock the service layer.

To run the tests:

```bash
./mvnw test
```
