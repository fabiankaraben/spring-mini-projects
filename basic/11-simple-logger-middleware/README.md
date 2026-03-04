# Simple Logger Middleware

This is a basic Spring Boot mini-project that demonstrates the use of a `HandlerInterceptor`. The application has a custom interceptor that logs the request HTTP method, Path, and the time taken (Duration) to process each incoming HTTP request.

## Requirements
* Java 21 or later
* Maven as the dependency manager (embedded Maven wrapper is available)

## Features Included
* Implementation of a simple `HandlerInterceptor` to store request start times and calculate duration.
* Registration of the interceptor using `WebMvcConfigurer` to be applied onto routes.
* A basic REST controller endpoint to demonstrate logging.
* Sliced component testing utilizing `@WebMvcTest`.
* Unit testing of interceptor with `JUnit 5` and `Mockito`.

## How to use it

1. Ensure your Java version is at least 21.
2. Build and start the server using the embedded Maven wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```
3. Open another terminal window and use `curl` to access the endpoint:
   ```bash
   curl http://localhost:8080/hello
   ```
4. You will receive the response `Hello World!`.
5. Check the terminal where your Spring Boot application is running. You will see a log statement similar to:
   ```
   INFO  com.example.simple_logger_middleware.interceptor.RequestLoggingInterceptor : Method: GET | Path: /hello | Duration: 104 ms
   ```

## Running the tests

To run the unit and integration tests (using JUnit 5 and Mockito), execute the following command:

```bash
./mvnw clean test
```
