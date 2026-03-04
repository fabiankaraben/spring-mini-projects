# JSON Request Parser

🔹 **Mini-Project:** A backend in Spring Boot accepting a POST JSON body and mapping it with `@RequestBody`.

## 📌 Project Overview
The **JSON Request Parser** is an educational mini-project showcasing how to handle JSON requests in a Spring Boot application. We specifically demonstrate:
- Deserialization of incoming JSON data using the `@RequestBody` annotation.
- Mapping JSON to a Java Data Transfer Object (DTO) containing validation rules using `spring-boot-starter-validation`.
- Structuring basic controllers and responses natively with `ResponseEntity`.

**Note:** This mini-project is completely independent of other mini-projects found in this repository.

## 📋 Requirements
- **Java**: 21
- **Dependency Manager**: Maven
- **Spring Boot**: 3.2.x or later
- **Testing**: Unit tests and sliced integration tests using JUnit 5, Mockito, and `@WebMvcTest`.

*Docker is not required for this specific mini-project, as there are no external dependencies like databases or storage components.*

## 🚀 How to Run

1. Navigate to the project directory:
   ```bash
   cd basic/12-json-request-parser
   ```
2. Start the application using the Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```

The application will start on port `8080`.

## 🛠️ Usage Examples with `curl`

Once the application is running, you can test it sending JSON payloads via `curl` requests.

### 1. Successful Request
Testing the `POST` endpoint with valid JSON.

```bash
curl -X POST http://localhost:8080/api/users/register \
     -H "Content-Type: application/json" \
     -d '{
           "username": "johndoe",
           "email": "johndoe@example.com",
           "password": "securepassword123"
         }'
```

**Expected Response (HTTP 201 Created):**
```json
{
  "message": "User registered successfully",
  "username": "johndoe",
  "status": "ACTIVE"
}
```

### 2. Validation Error (Bad Request)
Testing the `POST` endpoint with missing or invalid fields. Here we provide a tiny password, invalid email, and an empty username.

```bash
curl -v -X POST http://localhost:8080/api/users/register \
     -H "Content-Type: application/json" \
     -d '{
           "username": "",
           "email": "not-an-email",
           "password": "tiny"
         }'
```

**Expected Response:** An HTTP 400 Bad Request error since the `UserRegistrationRequest` will fail the internal Spring Validation.

## 🧪 Testing

The project uses a sliced testing approach, focusing on testing the Controller layers independently of the real server environment. 

To run the full suite of unit and integration tests, use the following command:

```bash
./mvnw clean test
```
