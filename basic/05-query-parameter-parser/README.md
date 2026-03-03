# 05 - Query Parameter Parser

---

### Description
This mini-project demonstrates how to parse and validate query parameters in a Spring Boot application using Spring MVC and `spring-boot-starter-validation`. It covers two main approaches:
1. Extracting individual query parameters using `@RequestParam` and validating them with constraint annotations directly on the method arguments (requires `@Validated` at the class level).
2. Grouping query parameters into a POJO (or Record) and validating the object using the `@Valid` annotation.

A global exception handler (`@RestControllerAdvice`) is also included to intercept validation exceptions and convert them into user-friendly JSON responses mapping field names to validation error messages.

### Requirements
- **Java**: 21 or higher
- **Dependency Manager**: Maven
- **Dependencies**: `spring-boot-starter-web`, `spring-boot-starter-validation`
- **Testing**: `junit-jupiter`, `mockito-core`, `spring-boot-starter-test`
- No external services (like Docker/databases) are required.

### How to Use

Start the application using the Maven wrapper:
```bash
./mvnw spring-boot:run
```
The server will start on port 8080.

#### 1. Validating Individual `@RequestParam` Variables

**Endpoint:** `GET /api/products/search-params`

* **Valid Request:**
  ```bash
  curl "http://localhost:8080/api/products/search-params?category=electronics&limit=15&tags=sale,new"
  ```
  **Response:**
  ```json
  {
    "category": "electronics",
    "limit": 15,
    "tags": ["sale", "new"],
    "message": "Search executed successfully via individual @RequestParam"
  }
  ```

* **Invalid Request** (Missing required category and negative limit):
  ```bash
  curl -v "http://localhost:8080/api/products/search-params?limit=-5"
  ```
  **Response (400 Bad Request):**
  ```json
  {
    "limit": "Limit must be at least 1"
  }
  ```

#### 2. Validating via POJO Mapping (`@Valid`)

**Endpoint:** `GET /api/products/search-pojo`

* **Valid Request:**
  ```bash
  curl "http://localhost:8080/api/products/search-pojo?q=laptop&minPrice=500&maxPrice=1500"
  ```
  **Response:**
  ```json
  {
    "query": "laptop",
    "minPrice": 500,
    "maxPrice": 1500,
    "message": "Search executed successfully via POJO mapping"
  }
  ```

* **Invalid Request** (Query too short and negative price):
  ```bash
  curl -v "http://localhost:8080/api/products/search-pojo?q=ab&minPrice=-100"
  ```
  **Response (400 Bad Request):**
  ```json
  {
    "q": "Query must be between 3 and 50 characters",
    "minPrice": "Minimum price cannot be negative"
  }
  ```

### Running Tests

This project includes both Sliced Integration Tests (`@WebMvcTest`) executing requests and asserting the JSON responses, and Unit Tests covering isolated controller logic.

To execute the tests:
```bash
./mvnw test
```
