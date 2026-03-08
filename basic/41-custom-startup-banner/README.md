# Custom Startup Banner

This is a backend in Spring Boot displaying a custom ASCII graphic on application startup.

## Requirements

- Java 21
- Maven

## How to use

1.  Clone the repository.
2.  Navigate to the project directory: `basic/41-custom-startup-banner`.
3.  Run the application using the Maven Wrapper:

    ```bash
    ./mvnw spring-boot:run
    ```

4.  Observe the console output. You should see a custom ASCII banner instead of the default Spring Boot banner.

    ```text
      _________  _____________   ______  __  ___   ___  ___    _   ___   ____________
     / ___/ __ \/ __/_  __/ _ | /  _/  |/  /  _/  / _ )/ _ |  / |/ / | / / __/ _  /
    / /__/ /_/ / _/  / / / __ |_/ // /|_/ // /   / _  / __ | /    /  |/ / _// , _/ 
    \___/\____/_/   /_/ /_/ |_/___/_/  /_/___/  /____/_/ |_|/_/|_/_/|__/___/_/|_|  
                                                                                   
     :: Spring Boot ::        (v3.4.3)
     :: Java Version ::       (21)
    ```

5.  You can also access the root endpoint to confirm the application is running:

    ```bash
    curl -v http://localhost:8080/
    ```

    Expected response:
    ```text
    Application started successfully! Check the console for the custom banner.
    ```

## Running Tests

To run the unit and integration tests:

```bash
./mvnw test
```

## Implementation Details

- **Custom Banner**: The banner is defined in `src/main/resources/banner.txt`. Spring Boot automatically detects this file and displays it on startup.
- **Service Layer**: `BannerService` encapsulates the business logic (returning the welcome message), promoting separation of concerns.
- **Controller**: `BannerController` handles the HTTP requests and delegates to `BannerService`.
- **Tests**:
    - `CustomBannerApplicationTests`: Verifies the Spring context loads successfully.
    - `BannerControllerTest`: Uses `@WebMvcTest` to slice test the web layer. It mocks the `BannerService` using `@MockitoBean` (the replacement for `@MockBean`) to verify the controller's behavior in isolation.
    - `BannerServiceTest`: A pure unit test for the service layer using JUnit 5, ensuring business logic works without needing a Spring context.
