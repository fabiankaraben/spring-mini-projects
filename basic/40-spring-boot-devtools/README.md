# Spring Boot DevTools Mini-Project

This mini-project demonstrates the capabilities of **Spring Boot DevTools**, focusing on developer productivity features like automatic restart and LiveReload.

## 📝 Description

Spring Boot DevTools provides a set of tools that can make the application development experience a little more pleasant. The main features showcased here are:

*   **Automatic Restart**: The application automatically restarts whenever files on the classpath change. This is much faster than a cold start because it uses two classloaders (base and restart).
*   **LiveReload**: Includes an embedded LiveReload server that can be used to trigger a browser refresh when a resource is changed.
*   **Property Defaults**: Sensible defaults for development (e.g., disabling template caching).

## 🚀 Requirements

*   **Java 21** or higher
*   **Maven** (Wrapper included)

## 📦 Project Structure

```
basic/40-spring-boot-devtools/
├── src/
│   ├── main/
│   │   └── java/com/example/devtools/
│   │       ├── DevToolsApplication.java     # Main entry point
│   │       ├── GreetingController.java      # REST Controller
│   │       └── GreetingService.java         # Service logic
│   └── test/
│       └── java/com/example/devtools/
│           ├── GreetingControllerTest.java  # Unit tests (JUnit 5 + Mockito)
│           └── GreetingControllerIntegrationTest.java # Integration tests (@WebMvcTest)
├── mvnw                                     # Maven Wrapper (Unix)
├── mvnw.cmd                                 # Maven Wrapper (Windows)
├── pom.xml                                  # Project dependencies
└── README.md                                # This file
```

## 🛠️ How to Run

1.  **Navigate to the project directory:**
    ```bash
    cd basic/40-spring-boot-devtools
    ```

2.  **Run the application:**
    ```bash
    ./mvnw spring-boot:run
    ```

3.  **Test the endpoint:**
    Open a terminal and use `curl`:
    ```bash
    curl http://localhost:8080/greet
    ```
    Output:
    ```text
    Hello, DevTools!
    ```

## 🔄 Demonstrating Auto-Restart

1.  Keep the application running.
2.  Open `src/main/java/com/example/devtools/GreetingService.java`.
3.  Change the return string:
    ```java
    return "Hello, DevTools (Updated)!";
    ```
4.  Save the file.
5.  Watch the console; you will see the application restarting automatically.
6.  Run the curl command again:
    ```bash
    curl http://localhost:8080/greet
    ```
    Output:
    ```text
    Hello, DevTools (Updated)!
    ```

## 🧪 How to Run Tests

This project includes both unit tests and integration tests.

To run all tests:
```bash
./mvnw test
```

### Test Types
*   **Unit Tests (`GreetingControllerTest`)**: Uses `Mockito` to test the controller in isolation, mocking the service layer.
*   **Integration Tests (`GreetingControllerIntegrationTest`)**: Uses `@WebMvcTest` to test the web layer with a mocked service bean using `@MockitoBean`.

## 🐳 Docker Support

This project does not require any external dependencies like databases, so Docker is not required to run it.

## 📚 Dependencies

*   `spring-boot-starter-web`: For building the REST API.
*   `spring-boot-devtools`: Provides the development-time features.
*   `spring-boot-starter-test`: Includes JUnit 5, Mockito, and other testing utilities.
