# Custom Validator Mini-Project

🔹 This is a backend in Spring Boot building a custom Bean Validation annotation for specific business rules.

## Requirements

- **Java 21** or higher.
- **Maven** (Maven Wrapper is included).
- Standard Spring Boot web and validation dependencies.

## Description

This mini-project demonstrates how to create a custom Bean Validation annotation in Spring Boot. It uses a custom `@ValidEmployeeCode` annotation to enforce that an employee code must start with `EMP-` and be followed by precisely 4 digits (e.g., `EMP-1234`).

The project includes:
- `@ValidEmployeeCode`: The custom constraint annotation.
- `EmployeeCodeValidator`: The class containing the validation logic.
- `GlobalExceptionHandler`: A controller advice that formats validation failures according to the RFC 7807 Standard `ProblemDetail` responses.
- `EmployeeController`: A REST endpoint to test the functionality.

## Usage

### 1. Build and Run the Application

You can start the project using the included Maven Wrapper:

```bash
./mvnw spring-boot:run
```

The application will start on port `8080`.

### 2. cURL Examples

**Successful Creation:**
Valid request with correct name and valid employee code (`EMP-1234`).

```bash
curl -X POST http://localhost:8080/employees \
     -H "Content-Type: application/json" \
     -d '{
           "name": "Jane Doe",
           "code": "EMP-1234"
         }'
```

**Response:**
```json
{
  "message": "Employee created successfully!",
  "employeeCode": "EMP-1234",
  "employeeName": "Jane Doe"
}
```

**Validation Error - Custom Validator Failure:**

```bash
curl -X POST http://localhost:8080/employees \
     -H "Content-Type: application/json" \
     -d '{
           "name": "Jane Doe",
           "code": "EMPLOYEE-12"
         }'
```

**Response:**
```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed for request.",
  "type": "https://example.com/probs/validation-error",
  "invalid-params": {
    "code": "Invalid employee code format. It must start with 'EMP-' followed by 4 digits (e.g., EMP-1234)."
  }
}
```

**Validation Error - Blank Name & Invalid Code:**

```bash
curl -X POST http://localhost:8080/employees \
     -H "Content-Type: application/json" \
     -d '{
           "name": "",
           "code": "EMP1234"
         }'
```

**Response:**
```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed for request.",
  "type": "https://example.com/probs/validation-error",
  "invalid-params": {
    "name": "Name cannot be blank.",
    "code": "Invalid employee code format. It must start with 'EMP-' followed by 4 digits (e.g., EMP-1234)."
  }
}
```

## Running Tests

The application contains both unit tests and sliced integration tests (`@WebMvcTest`).
You can run them using the command below:

```bash
./mvnw test
```
