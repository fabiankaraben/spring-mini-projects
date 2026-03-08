# Exception Translators Mini-Project

This is a backend application built with **Spring Boot** that demonstrates how to translate custom persistence exceptions into standard HTTP error responses. It serves as an educational example of handling exceptions globally and providing meaningful error messages to API consumers.

## 📝 Project Description

The application manages a simple registry of **Books**. It enforces business rules (e.g., unique ISBNs) and validates input data. When rules are violated or resources are not found, the application catches the specific exceptions and maps them to appropriate HTTP status codes (e.g., 404 Not Found, 409 Conflict, 400 Bad Request) using a global exception handler.

## 📋 Requirements

- **Java**: 21 or higher
- **Maven**: 3.8+ (Wrapper included)

## 🚀 How to Run

1.  **Clone the repository** (if you haven't already).
2.  Navigate to the project directory:
    ```bash
    cd basic/50-exception-translators
    ```
3.  **Run the application**:
    ```bash
    ./mvnw spring-boot:run
    ```

The application will start on port `8080`.

## 🛠 Usage Examples (curl)

Here are some example `curl` commands to interact with the API.

### 1. Create a Book (Success - 201 Created)

```bash
curl -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "isbn": "978-0134685991",
    "title": "Effective Java",
    "author": "Joshua Bloch"
  }'
```

### 2. Create a Duplicate Book (Error - 409 Conflict)

Try creating the same book again. You should receive a 409 Conflict error.

```bash
curl -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "isbn": "978-0134685991",
    "title": "Effective Java",
    "author": "Joshua Bloch"
  }'
```

**Response:**
```json
{
  "status": 409,
  "message": "Book with ISBN 978-0134685991 already exists",
  "timestamp": "..."
}
```

### 3. Create a Book with Invalid Data (Error - 400 Bad Request)

Missing required fields will trigger validation errors.

```bash
curl -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "isbn": "",
    "title": "Incomplete Book",
    "author": ""
  }'
```

**Response:**
```json
{
  "status": 400,
  "message": "isbn: ISBN is required, author: Author is required",
  "timestamp": "..."
}
```

### 4. Get a Book by ISBN (Success - 200 OK)

```bash
curl -X GET http://localhost:8080/api/books/978-0134685991
```

### 5. Get a Non-Existent Book (Error - 404 Not Found)

```bash
curl -X GET http://localhost:8080/api/books/999-9999999999
```

**Response:**
```json
{
  "status": 404,
  "message": "Book with ISBN 999-9999999999 not found",
  "timestamp": "..."
}
```

## 🧪 How to Run Tests

This project includes Unit Tests and Sliced Integration Tests using **JUnit 5** and **Mockito**.

To run all tests:

```bash
./mvnw test
```

The tests cover:
- **Service Layer**: Unit tests mocking the repository.
- **Controller Layer**: Sliced tests using `@WebMvcTest` to verify HTTP responses and exception handling.
- **Repository Layer**: Sliced tests using `@DataJpaTest` with an in-memory H2 database.
