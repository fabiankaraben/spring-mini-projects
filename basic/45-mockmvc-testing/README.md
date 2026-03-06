# MockMvc Testing Mini-Project

This mini-project demonstrates how to set up and use `@WebMvcTest` in Spring Boot for sliced integration testing. It focuses on testing the web layer (controllers) without loading the entire Spring application context, making tests faster and more focused. The project includes simple REST endpoints and corresponding unit/integration tests using JUnit 5 and MockMvc.

## Requirements

- **Java**: Version 21 or higher
- **Build Tool**: Maven (Maven Wrapper is included)
- **Operating System**: Any OS that supports Java and Maven

## Project Structure

```
basic/45-mockmvc-testing/
├── src/
│   ├── main/java/com/example/mockmvctesting/
│   │   ├── MockMvcTestingApplication.java  # Main Spring Boot application class
│   │   └── HelloController.java            # REST controller with sample endpoints
│   └── test/java/com/example/mockmvctesting/
│       └── HelloControllerTest.java        # Tests using @WebMvcTest
├── .gitignore                              # Git ignore file
├── pom.xml                                 # Maven configuration
├── README.md                               # This file
└── mvnw (and mvnw.cmd)                     # Maven Wrapper scripts
```

## How to Use

1. **Clone or navigate to the project directory**:
   ```
   cd basic/45-mockmvc-testing
   ```

2. **Run the application**:
   ```
   ./mvnw spring-boot:run
   ```
   The application will start on `http://localhost:8080`.

3. **Test the endpoints** using curl:

   - Get a simple greeting:
     ```
     curl http://localhost:8080/hello
     ```
     Expected response: `Hello, World!`

   - Get a personalized greeting:
     ```
     curl http://localhost:8080/hello/Alice
     ```
     Expected response: `Hello, Alice!`

## How to Run the Tests

This project demonstrates both unit testing and sliced integration testing:

- **Run all tests**:
  ```
  ./mvnw test
  ```

- **Run specific test class**:
  ```
  ./mvnw test -Dtest=HelloControllerTest
  ```

The tests use `@WebMvcTest(HelloController.class)` to load only the web layer, allowing fast and focused testing of controller behavior without the full application context.

## Key Concepts Demonstrated

- **@WebMvcTest**: Slices the Spring context to include only MVC-related components.
- **MockMvc**: Provides a way to test web endpoints in a simulated environment.
- **JUnit 5**: Modern testing framework used in Spring Boot.
- **Mockito**: Included in Spring Boot Test starter for mocking dependencies.
- **Sliced Testing**: Faster tests by loading only necessary parts of the application.

## Notes

- This mini-project is independent and does not require any external dependencies like databases.
- No Docker setup is needed as the project runs standalone.
- All code is documented with comments for educational purposes.
