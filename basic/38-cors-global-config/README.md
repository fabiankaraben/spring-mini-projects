# CORS Global Config

This mini-project demonstrates how to configure **Cross-Origin Resource Sharing (CORS)** globally in a Spring Boot application.

## 📝 Description

CORS is a security feature implemented by web browsers that restricts web pages from making requests to a different domain than the one that served the web page. This project shows how to enable CORS for your API endpoints so that a frontend application running on a different origin (domain, protocol, or port) can access them.

## 🚀 Requirements

- Java 21
- Maven

## 🛠️ How to Run

1.  **Navigate to the project directory:**
    ```bash
    cd basic/38-cors-global-config
    ```

2.  **Run the application:**
    ```bash
    ./mvnw spring-boot:run
    ```

The application will start on port `8080`.

## 🧪 Testing with curl

You can test the CORS configuration using `curl` by simulating requests from different origins.

### 1. Test with an Allowed Origin

The application is configured to allow requests from `http://localhost:9090`.

```bash
curl -v -H "Origin: http://localhost:9090" http://localhost:8080/greeting
```

**Expected Output:**
Look for the `Access-Control-Allow-Origin` header in the response.

```
< HTTP/1.1 200 
< Vary: Origin
< Access-Control-Allow-Origin: http://localhost:9090
< Access-Control-Allow-Credentials: true
...
Hello, CORS World!
```

### 2. Test with a Disallowed Origin

Try making a request from an origin that is NOT allowed (e.g., `http://evil.com`).

```bash
curl -v -H "Origin: http://evil.com" http://localhost:8080/greeting
```

**Expected Output:**
The request should be forbidden (403 Forbidden) or the CORS headers should be missing, depending on the browser/client behavior. In Spring Security default behavior, it might return 403. Without Spring Security (just WebMvc), it might just omit the headers.

### 3. Test Preflight Request (OPTIONS)

Browsers send a preflight `OPTIONS` request for complex requests (e.g., those with custom headers or methods other than GET/POST/HEAD).

```bash
curl -v -X OPTIONS \
  -H "Origin: http://localhost:9090" \
  -H "Access-Control-Request-Method: DELETE" \
  http://localhost:8080/greeting
```

**Expected Output:**

```
< HTTP/1.1 200 
< Vary: Origin
< Access-Control-Allow-Origin: http://localhost:9090
< Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
< Access-Control-Allow-Credentials: true
...
```

## ✅ Running Tests

To run the unit and integration tests:

```bash
./mvnw test
```

## 📂 Project Structure

- `src/main/java/com/example/cors/CorsGlobalConfigApplication.java`: Main entry point.
- `src/main/java/com/example/cors/GreetingController.java`: REST Controller with a sample endpoint.
- `src/main/java/com/example/cors/WebConfig.java`: Global CORS configuration using `WebMvcConfigurer`.
- `src/test/java/com/example/cors/CorsIntegrationTest.java`: Integration tests verifying CORS headers.
