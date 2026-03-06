# I18n Messages

This mini-project demonstrates how to use Spring Boot's `MessageSource` to provide localized messages based on the `Accept-Language` header in HTTP requests. It showcases internationalization (i18n) in a Spring Boot web application, allowing the application to return messages in different languages depending on the client's language preference.

## Requirements

- Java 21 or higher
- Maven (or use the included Maven Wrapper)

## How to Run the Application

1. Navigate to the project directory: `basic/39-i18n-messages`
2. Run the application using Maven Wrapper: `./mvnw spring-boot:run`
3. The Spring Boot application will start on port 8080 by default.

## Usage

Once the application is running, you can test the localization feature using `curl` or any HTTP client.

### Available Endpoints

- `GET /message/{key}`: Returns the localized message for the given key based on the `Accept-Language` header.

### Curl Examples

1. **English (default)**:
   ```
   curl -H "Accept-Language: en" http://localhost:8080/message/greeting
   ```
   Expected response: `Hello World`

2. **Spanish**:
   ```
   curl -H "Accept-Language: es" http://localhost:8080/message/greeting
   ```
   Expected response: `Hola Mundo`

3. **French**:
   ```
   curl -H "Accept-Language: fr" http://localhost:8080/message/greeting
   ```
   Expected response: `Bonjour le Monde`

4. **Error message in English**:
   ```
   curl -H "Accept-Language: en" http://localhost:8080/message/error.notfound
   ```
   Expected response: `Message not found`

5. **Error message in Spanish**:
   ```
   curl -H "Accept-Language: es" http://localhost:8080/message/error.notfound
   ```
   Expected response: `Mensaje no encontrado`

### Supported Languages

- English (`en`)
- Spanish (`es`)
- French (`fr`)

If no `Accept-Language` header is provided or an unsupported language is requested, the default (English) messages are returned.

## How to Run the Tests

To run the tests, execute the following command:

```
./mvnw test
```

This will run:
- **Unit tests** (`MessageControllerTest`): Tests the controller logic using JUnit 5 and Mockito to mock dependencies.
- **Integration tests** (`MessageControllerIntegrationTest`): Uses `@WebMvcTest` to test the web layer, verifying HTTP requests and responses with different `Accept-Language` headers.

All tests should pass, confirming that the localization works correctly.

## Project Structure

- `src/main/java/com/example/i18nmessages/`: Source code
  - `I18nMessagesApplication.java`: Main Spring Boot application class
  - `MessageController.java`: REST controller handling message requests
- `src/main/resources/`: Resources
  - `messages.properties`: Default (English) messages
  - `messages_es.properties`: Spanish messages
  - `messages_fr.properties`: French messages
- `src/test/java/com/example/i18nmessages/`: Test code
  - `MessageControllerTest.java`: Unit tests
  - `MessageControllerIntegrationTest.java`: Integration tests

## Technologies Used

- **Spring Boot**: Framework for building the web application
- **MessageSource**: Spring's built-in internationalization support
- **JUnit 5**: For unit and integration testing
- **Mockito**: For mocking dependencies in unit tests
- **`@WebMvcTest`**: For sliced integration testing of the web layer
- **Maven**: Build tool and dependency management

This mini-project is independent and does not require any external databases or Docker components.
