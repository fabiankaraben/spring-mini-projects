# File Upload Server

This is a backend mini-project built with Spring Boot that handles `multipart/form-data` requests to save uploaded files to the local disk.

## Requirements
- **Java**: 21 or higher
- **Dependency Manager**: Maven
- **Spring Boot**: 3.4.x
- **Testing**: JUnit 5, Mockito, and Spring Boot Test capabilities.

## Code Structure

- **Controller**: Endpoint to receive the file via POST request.
- **Service**: Abstraction over the storage mechanism (saving files to a local directory).
- **Exception**: Custom exception handling for storage issues.
- **Tests**: Contains unit tests for the service layer and sliced integration testing (`@WebMvcTest`) for the controller layer.

## How to use

1. Go to the project directory:
   ```bash
   cd basic/08-file-upload-server
   ```
2. Start the application using Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```
   The server will start on port `8080` by default. And it will create an `uploads` directory in the project root to store the uploaded files.

3. Example usages with `curl`:

   **Upload a valid file:**
   Create a sample file locally, e.g. `test.txt`.
   ```bash
   echo "Hello from file" > test.txt
   curl -F "file=@test.txt" http://localhost:8080/api/files/upload
   ```
   You should see a success message that the file was uploaded, and it will be available in the `uploads` directory.

   **Upload without providing a file (Simulating bad request):**
   ```bash
   curl -X POST http://localhost:8080/api/files/upload
   ```
   Expect an error response because the required `multipart/form-data` part is missing.

   **Upload an empty file:**
   ```bash
   touch empty-file.txt
   curl -F "file=@empty-file.txt" http://localhost:8080/api/files/upload
   ```
   The server should return a BAD_REQUEST indicating that the file is empty.

## How to run the tests

You can run the tests using Maven. The tests include unit tests for the storage logic utilizing JUnit 5 and isolated REST controller tests utilizing `@WebMvcTest` with `@MockitoBean`.

To execute all tests, run:
```bash
./mvnw test
```
