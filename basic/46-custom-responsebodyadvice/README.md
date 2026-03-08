# Custom ResponseBodyAdvice

This is a backend mini-project in Spring Boot that demonstrates how to intercept and modify API responses before they are serialized using `ResponseBodyAdvice`.

## 📝 Description

The project implements a `GlobalResponseBodyAdvice` that wraps all API responses into a standard JSON structure. This ensures a consistent response format across the entire application without needing to manually wrap responses in every controller method.

The standard response format includes:
- `timestamp`: The time when the response was generated.
- `status`: The HTTP status code (e.g., 200).
- `message`: A status message (e.g., "Success").
- `data`: The actual payload returned by the controller.

## 🛠 Requirements

- **Java**: 21 or higher
- **Maven**: 3.8+ (Wrapper included)

## 🚀 How to Run

1. **Clone the repository** (if not already done).
2. **Navigate to the project directory**:
   ```sh
   cd basic/46-custom-responsebodyadvice
   ```
3. **Run the application**:
   ```sh
   ./mvnw spring-boot:run
   ```

The application will start on port `8080`.

## 📡 Usage Examples

You can test the endpoints using `curl`.

### 1. Get a User by ID
**Request:**
```sh
curl -v http://localhost:8080/users/1
```

**Response:**
```json
{
  "timestamp": "2023-10-27T10:00:00.123456",
  "status": 200,
  "message": "Success",
  "data": {
    "id": 1,
    "name": "John Doe",
    "email": "john.doe@example.com"
  }
}
```

### 2. Get a Hello Message (String response)
**Request:**
```sh
curl -v http://localhost:8080/users/hello
```

**Response:**
```json
{
  "timestamp": "2023-10-27T10:05:00.654321",
  "status": 200,
  "message": "Success",
  "data": "Hello World"
}
```

## 🧪 Running Tests

This project includes Unit tests using **JUnit 5** and **Mockito**, and Sliced Integration Testing using `@WebMvcTest`.

To run the tests, execute:

```sh
./mvnw test
```

## 📂 Project Structure

- `src/main/java/com/example/demo/advice/GlobalResponseBodyAdvice.java`: The core logic that intercepts and wraps the responses.
- `src/main/java/com/example/demo/wrapper/ResponseWrapper.java`: The model class for the standard response structure.
- `src/main/java/com/example/demo/controller/UserController.java`: A simple controller to demonstrate the functionality.

---
*This mini-project is completely independent of other mini-projects found in this repository.*
