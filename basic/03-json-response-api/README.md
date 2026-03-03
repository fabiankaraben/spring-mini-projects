# 03 - JSON Response API

## 📝 Description
This is a basic Spring Boot application that provides a single RESTful GET endpoint `/api/users/current`. 
Instead of rendering a view or responding with plain text, it returns a plain old Java object (POJO) representing a User. 
Spring Web automatically serializes this object into a formatted JSON response using the Jackson library, which is the default JSON mapper in Spring.

This project is useful to understand the inner workings of `@RestController`, dependency injection with `@Service`, and how automatic POJO serialization transforms your Java objects into standard, front-end friendly JSON formats.

## 🛠️ Requirements
- ☕ **Java:** 21 or higher
- 📦 **Dependency Manager:** Maven
- 🌱 **Framework:** Spring Boot 3.4.x
- 🧪 **Testing:** JUnit 5, Mockito, and Spring Boot Test (`@WebMvcTest`)

## 🚀 How to Run

1. Open your terminal and navigate to the project directory:
   ```bash
   cd basic/03-json-response-api
   ```

2. Run the application using the Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```

3. The application will start on the default port `8080`.

## 📌 Usage

Once the application is running, you can test the JSON API using `curl` or by visiting the URL in a web browser.

**Request:**
```bash
curl -v -X GET http://localhost:8080/api/users/current
```

**Expected JSON Response:**
```json
{
  "id": 1,
  "name": "Alice Smith",
  "email": "alice.smith@example.com",
  "role": "ADMIN"
}
```

## 🧪 Testing

This project includes both automated unit tests and sliced integration tests:
- **Unit Testing:** Validates independent business logic in `UserServiceTest`.
- **Sliced Integration Testing:** Uses `@WebMvcTest` in `UserControllerTest` with Mockito `@MockitoBean` to perform automated requests simulating an HTTP client. It isolates the Web/Controller layer without needing the full Spring web application context, making testing smaller and faster.

To execute the test suite, run:

```bash
./mvnw clean test
```

## 🐳 Docker
For this particular application, Docker is **not** required. There are no relational databases or external components configured. Simply launch using the Maven Wrapper.
