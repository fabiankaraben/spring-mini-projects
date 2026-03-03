# 01 - Hello World HTTP Server

## Description
This is a basic Spring Boot backend serving a single `GET` endpoint that returns a "Hello World" message. It demonstrates how to initialize a Spring REST API, configure routing, and inject dependencies. The documentation and coding conventions are exclusively in English.

## Requirements
- **Java**: 21
- **Dependency Manager**: Maven
- **Framework**: Spring Boot 3.4.3
- **Testing**: JUnit 5, Mockito, `@WebMvcTest`, `@SpringBootTest`

## How to use it

1. Make sure you are inside the project's root folder:
   ```bash
   cd basic/01-hello-world-http-server
   ```

2. Compile and run the application using the generated Maven Wrapper (`./mvnw`). You can also use your local Maven installation (`mvn`):
   ```bash
   ./mvnw spring-boot:run
   ```

3. Open a new terminal window and use `curl` to test the API endpoint. The default server port is configured to `8080`.
   ```bash
   curl -v http://localhost:8080/api/hello
   ```

   **Expected Output:**
   ```
   > GET /api/hello HTTP/1.1
   > Host: localhost:8080
   ...
   < HTTP/1.1 200 OK
   < Content-Type: text/plain;charset=UTF-8
   < Content-Length: 11
   ...
   Hello World
   ```

## How to run the tests

This basic application showcases the most critical levels of testing in a Spring Boot application:
- **Unit Testing**: We test our components individually, without loading any Spring Context. Example: `HelloWorldServiceTest.java`.
- **Sliced Integration Testing**: We test only the web layers (`@Controller`) by slicing the Application Context with `@WebMvcTest` and injecting HTTP mocks by means of Mockito's `@MockBean`. Example: `HelloWorldControllerTest.java`.
- **Full Integration Testing (Context Loads)**: Ensures that the application starts completely and correctly without breaking. Example: `HelloWorldApplicationTests.java`.

To execute all the test cases, run:
```bash
./mvnw test
```
