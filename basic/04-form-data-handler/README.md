# 04 - Form Data Handler

## Description
This is a small Spring Boot application containing a backend capable of handling POST requests containing `application/x-www-form-urlencoded` data. The application parses the form inputs and mirrors them back to the client in JSON format. This validates that the request data is successfully read and data binding with the domain objects was successful.

## Requirements
- Java 21+
- Spring Boot 3.4+
- Maven Wrapper is included.
- Docker is not required since there are no external dependencies.

## Structure
- `UserForm.java`: Record class used as a Data Transfer Object to bind form attributes.
- `FormHandlerService.java`: Service mimicking business logic, formatting an echo response.
- `FormController.java`: A controller declaring endpoints to parse the `x-www-form-urlencoded` payloads using the `@ModelAttribute` annotation.
- `FormControllerUnitTest.java`: True Unit Test applying JUnit 5 and Mockito logic by isolating the Controller object completely from the Spring Context.
- `FormControllerWebMvcTest.java`: A Spring MVC slice test checking the Controller inside a web context configuration using `MockMvc` while simulating the service with `@MockitoBean`.

## Running the Application
To run the server locally, you can use the Maven wrapper:

```bash
./mvnw spring-boot:run
```

The application will start directly at standard port `8080`.

## Testing the Application (Usage examples)
You can mock a form submission with `curl` using the `--data` (or `-d`) flag, which automatically sets the Content-Type to `application/x-www-form-urlencoded`:

```bash
# Example 1: Passing multiple fields as URL encoded text
curl -X POST http://localhost:8080/submit \
     -d "username=johndoe" \
     -d "email=john@example.com" \
     -d "message=Hello+World"

# Output should resemble:
# {"data":{"username":"johndoe","email":"john@example.com","message":"Hello World"},"message":"Form data successfully captured.","status":"success"}
```

## Running the Tests
This project exhibits Unit Tests isolating components with Mockito, alongside Sliced Integration Tests with `@WebMvcTest`.
You can run all of them alongside Maven's validation lifecycle:

```bash
./mvnw test
```
