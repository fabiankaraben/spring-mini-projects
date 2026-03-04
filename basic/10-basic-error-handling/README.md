# Basic Error Handling

## 🔹 Description
This is a backend mini-project in Spring Boot that demonstrates how to implement a global exception handler using `@ControllerAdvice`. It catches application-specific exceptions and translates them into standard [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) Problem Details JSON representations.

Using Spring Boot 3's built-in `ProblemDetail` enables consistent error responses across all REST APIs.

## 📦 Requirements
- **Java**: 21 or higher
- **Dependency Manager**: Maven
- **Spring Boot**: 3.x
- **Frameworks/Libraries**: Spring Web, Spring Boot Test, JUnit 5, Mockito

## 🛠️ Usage

### 1. Build and Run the Application
You can run the application directly using the Maven Wrapper:
```bash
./mvnw spring-boot:run
```

The server will start on port `8080`.

### 2. cURL Examples

#### Retrieve a Product (Success)
Retrieving product ID 1 simulates a successful operation:
```bash
curl -i http://localhost:8080/api/products/1
```
**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":1,"name":"Sample Product","price":99.99}
```

#### Retrieve a Product (Not Found - 404)
Retrieving any ID other than 1 simulates a missing resource, returning a standard Problem Details structure:
```bash
curl -i http://localhost:8080/api/products/2
```
**Response:**
```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "https://example.com/problems/product-not-found",
  "title": "Product Not Found",
  "status": 404,
  "detail": "Product with ID 2 not found",
  "instance": "/api/products/2",
  "timestamp": "2024-03-01T12:00:00Z"
}
```

#### Create a Product (Invalid - 400 Bad Request)
Sending a negative price simulates a validation failure:
```bash
curl -i -X POST http://localhost:8080/api/products \
     -H "Content-Type: application/json" \
     -d '{"name": "Faulty Product", "price": -15.5}'
```
**Response:**
```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "https://example.com/problems/invalid-product",
  "title": "Invalid Product Details",
  "status": 400,
  "detail": "Price cannot be negative",
  "instance": "/api/products",
  "timestamp": "2024-03-01T12:00:00Z"
}
```

## 🧪 Testing

The mini-project includes unit tests and sliced integration tests using `@WebMvcTest`. JUnit 5 and Mockito are used to verify both success cases and error responses containing standard RFC 7807 `application/problem+json` bodies.

To run the tests:
```bash
./mvnw test
```
