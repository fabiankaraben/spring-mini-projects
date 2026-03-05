# WebClient Basic

This mini-project demonstrates how to consume a public REST API in a Spring Boot application using the reactive `WebClient` synchronously.

Even though `WebClient` is designed for non-blocking reactive programming as part of the Spring WebFlux framework, it provides methods like `.block()` which allow it to be used synchronously over traditional blocking paradigms in typical Spring WebMVC applications.

## Requirements

- **Java 21** or higher.
- **Maven Wrapper** is included to build and run the project without needing Maven pre-installed.
- No database or Docker is required as it only interacts with the public **JSONPlaceholder** API.

## Design

- `WebClientConfig`: Creates the `WebClient` bean pre-configured with the base URL of JSONPlaceholder, ready to be autowired where needed.
- `User` DTO: Contains the properties mapped directly from the JSON payload.
- `UserWebClientService`: Responsible for calling `WebClient` endpoints securely and retrieving either a `List<User>` or `User` objects, blocking the reactive stream context to return actual standard objects.
- `UserController`: Proxies the methods from `UserWebClientService` over its own API to provide end-users access to the external responses seamlessly.

## Testing

This project leverages:
1. **JUnit 5** and **Mockito**.
2. **MockWebServer** (from squareup OkHttp3) used to intercept `WebClient` calls and mock network returns effectively at the local loopback network-level.
3. **Sliced Integration Tests**: Utilizing `@WebMvcTest` with `@MockitoBean` to ensure controller context initializes safely without booting complex application layers.

### Running Tests

Run the following command at the root of the mini-project to execute tests:

```bash
./mvnw test
```

## Running the Application

You can start the Spring Boot application by executing:

```bash
./mvnw spring-boot:run
```

The server will successfully start on default port `8080`.

## Usage Examples

With the application running, try out these endpoints using `curl` from a new terminal window:

### Fetch All Users

```bash
curl -X GET http://localhost:8080/api/users
```
**Expected response**: A JSON array with all the users fetched and blocked down from the remote JSONPlaceholder API.

### Fetch a User by ID

```bash
curl -X GET http://localhost:8080/api/users/1
```
**Expected response**: A JSON object of the single user queried.
