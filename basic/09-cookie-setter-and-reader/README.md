# 09. Cookie Setter and Reader

## Description
This is an educational Spring Boot mini-project demonstrating how to interact with HTTP cookies. It sets a custom cookie (`user_preference`) directly on the HTTP response using `HttpServletResponse` and later retrieves that cookie from an incoming request using the `@CookieValue` annotation. The project contains clear and thoroughly commented code to help developers understand cookie manipulation in a modern Java backend application.

## Requirements
- **Java**: 21 or higher
- **Framework**: Spring Boot 3.4.x
- **Dependency Manager**: Maven
- **Dependencies**:
  - `spring-boot-starter-web` (Spring MVC)
  - `spring-boot-starter-test` (JUnit 5, Mockito, MockMvc)
- **External Dependencies**: None (this project manages everything securely within Spring and doesn't require Docker or external databases).

## Endpoints

### 1. Set a Cookie
**Endpoint:** `POST /api/cookies/set`  
Sets a cookie named `user_preference` with the value provided via the request parameter. Security properties like `HttpOnly` and `MaxAge` are configured.

### 2. Read a Cookie
**Endpoint:** `GET /api/cookies/read`  
Reads the cookie named `user_preference` using `@CookieValue`.

---

## Usage Examples

### Running the Application

To run the application locally, use the generated Maven Wrapper:
```bash
# Start the application on default port 8080
./mvnw spring-boot:run
```

### cURL Commands for Testing the API

**1. Set a Custom Cookie**
First, execute a `POST` request to set a specific cookie value. Use the `-c` or `--cookie-jar` flag to tell `curl` to store the received cookie in a file.
```bash
curl -i -X POST -d "value=dark_mode" -c cookies.txt http://localhost:8080/api/cookies/set
```
*Expected Output:*
```text
HTTP/1.1 200 
Set-Cookie: user_preference=dark_mode; Max-Age=604800; Expires=Wed, 11-Mar-2026 18:00:00 GMT; Path=/; HttpOnly
Content-Type: text/plain;charset=UTF-8
Content-Length: 61
Date: Wed, 04 Mar 2026 18:00:00 GMT

Cookie 'user_preference' has been set with value: dark_mode
```

**2. Read the Custom Cookie**
Next, access the `GET` endpoint. Provide the `-b` or `--cookie` flag to inform `curl` to send the cookie that we just saved in the previous step.
```bash
curl -i -X GET -b cookies.txt http://localhost:8080/api/cookies/read
```
*Expected Output:*
```text
HTTP/1.1 200 
Content-Type: text/plain;charset=UTF-8
Content-Length: 46
Date: Wed, 04 Mar 2026 18:01:00 GMT

The read value of 'user_preference' is: dark_mode
```

**3. Read Without a Cookie**
If you invoke the `read` endpoint without providing any cookie, the controller defaults to "No cookie found".
```bash
curl -i -X GET http://localhost:8080/api/cookies/read
```
*Expected Output:*
```text
HTTP/1.1 200 
Content-Type: text/plain;charset=UTF-8
Content-Length: 46
Date: Wed, 04 Mar 2026 18:02:00 GMT

The read value of 'user_preference' is: No cookie found
```

---

## Testing

This project incorporates comprehensive tests designed across two scopes:
1. **Pure Unit Tests**: Employs JUnit 5 alongside **Mockito** to examine the controller behaviors completely in isolating, stubbing `HttpServletResponse`.
2. **Sliced Integration Tests**: Employs `@WebMvcTest` with `MockMvc` to test cookie creation and parsing exactly as the web context would evaluate them. 

**Running the Tests**
Ensure you are at the project root folder. Use the Maven Wrapper command to execute the test suite:
```bash
./mvnw test
```
