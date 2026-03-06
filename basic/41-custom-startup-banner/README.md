# Custom Startup Banner

## Description

This mini-project is a Spring Boot backend application that displays a custom ASCII graphic on application startup. It demonstrates how to customize the startup banner in Spring Boot using a `banner.txt` file in the resources directory.

## Requirements

- Java 21 or higher
- Maven (or use the provided Maven Wrapper)

## How to Run

1. Navigate to the project directory: `basic/41-custom-startup-banner`

2. Run the application using the Maven Wrapper:

   ```bash
   ./mvnw spring-boot:run
   ```

   Or using Maven directly:

   ```bash
   mvn spring-boot:run
   ```

3. The application will start on port 8080, displaying the custom ASCII banner in the console.

## Usage

Once the application is running, you can access the endpoint via HTTP.

### Example curl command:

```bash
curl http://localhost:8080/
```

Expected response: `Welcome to the Custom Startup Banner Application!`

## How to Run the Tests

Run the tests using the Maven Wrapper:

```bash
./mvnw test
```

Or using Maven:

```bash
mvn test
```

The tests include:

- **Unit tests** using JUnit 5 and Mockito (e.g., `BannerControllerTest`)

- **Sliced Integration Tests** using `@WebMvcTest` (e.g., `BannerControllerWebTest`)

## Project Structure

- `src/main/java/com/example/custombanner/`: Application source code
  - `CustomBannerApplication.java`: Main Spring Boot application class
  - `BannerController.java`: REST controller providing the home endpoint
  - `BannerService.java`: Interface for banner service
  - `BannerServiceImpl.java`: Implementation of banner service
- `src/main/resources/banner.txt`: Custom ASCII art displayed on startup
- `src/test/java/com/example/custombanner/`: Test classes
  - `BannerControllerTest.java`: Unit test for controller using Mockito
  - `BannerControllerWebTest.java`: Integration test for web layer using @WebMvcTest
- `pom.xml`: Maven configuration with Spring Boot dependencies
- `README.md`: This file
- `.gitignore`: Git ignore file for Maven and IDE files
- `mvnw` and `.mvn/`: Maven Wrapper for easy execution

## Dependencies

- Spring Boot Starter Web: For building web applications
- Spring Boot Starter Test: Includes JUnit 5 and Mockito for testing

## Notes

- This mini-project is completely independent of other mini-projects in the repository.
- No Docker is required as there are no external dependencies like databases.
- All code is documented with comments for educational purposes.
