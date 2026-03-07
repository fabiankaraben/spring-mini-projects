# Session Management Mini-Project

This project demonstrates how to manage user sessions in a Spring Boot application using `HttpSession`. It allows storing, retrieving, listing, and invalidating session attributes.

## Requirements

- Java 21 or higher
- Maven

## Project Structure

- **SessionController**: REST controller that handles session operations.
- **SessionManagementApplication**: Main entry point of the Spring Boot application.

## How to Run

1.  Navigate to the project directory:
    ```bash
    cd basic/37-session-management
    ```
2.  Run the application using the Maven Wrapper:
    ```bash
    ./mvnw spring-boot:run
    ```

The application will start on port 8080 (default).

## API Endpoints & Usage

You can use `curl` to interact with the API. Since sessions rely on cookies (JSESSIONID), you need to handle cookies in your curl requests.

### 1. Set a Session Attribute

Stores a key-value pair in the session.

```bash
# -c cookies.txt : Save cookies to this file
curl -X POST "http://localhost:8080/session/set?key=username&value=john_doe" -c cookies.txt
```

**Response:**
```
Session attribute set: username = john_doe
```

### 2. Get a Session Attribute

Retrieves a value from the session using a key.

```bash
# -b cookies.txt : Read cookies from this file
curl -X GET "http://localhost:8080/session/get?key=username" -b cookies.txt
```

**Response:**
```
Value for username: john_doe
```

### 3. List All Attributes

Lists all attributes stored in the current session.

```bash
curl -X GET "http://localhost:8080/session/all" -b cookies.txt
```

**Response (JSON):**
```json
{
  "username": "john_doe",
  "sessionId": "FE1234..."
}
```

### 4. Invalidate Session

Invalidates the current session, removing all data.

```bash
curl -X POST "http://localhost:8080/session/invalidate" -b cookies.txt
```

**Response:**
```
Session invalidated.
```

## Running Tests

This project includes unit tests (using Mockito) and sliced integration tests (using `@WebMvcTest`).

To run the tests:

```bash
./mvnw test
```
