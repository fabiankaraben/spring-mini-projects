# I18n Messages

This mini-project demonstrates how to implement Internationalization (i18n) in a Spring Boot application using `MessageSource` and resolving locales based on the `Accept-Language` header.

## Requirements

-   Java 21
-   Maven

## Project Structure

-   `src/main/java/com/example/i18n/config/LocaleConfig.java`: Configuration for `LocaleResolver` and `MessageSource`.
-   `src/main/java/com/example/i18n/controller/GreetingController.java`: REST controller demonstrating localized messages.
-   `src/main/resources/i18n/messages*.properties`: Resource bundles for different languages (English, Spanish, French).

## How to Run

1.  Build the project:
    ```bash
    ./mvnw clean install
    ```

2.  Run the application:
    ```bash
    ./mvnw spring-boot:run
    ```

## Usage Examples (curl)

The default locale is English (en).

### 1. Hello World

**Default (English):**
```bash
curl -v http://localhost:8080/hello
# Output: Hello World!
```

**Spanish (es):**
```bash
curl -v -H "Accept-Language: es" http://localhost:8080/hello
# Output: ¡Hola Mundo!
```

**French (fr):**
```bash
curl -v -H "Accept-Language: fr" http://localhost:8080/hello
# Output: Bonjour le monde!
```

### 2. Welcome with Parameter

**Default (English):**
```bash
curl -v "http://localhost:8080/welcome?name=Fabian"
# Output: Welcome to our application, Fabian!
```

**Spanish (es):**
```bash
curl -v -H "Accept-Language: es" "http://localhost:8080/welcome?name=Fabian"
# Output: ¡Bienvenido a nuestra aplicación, Fabian!
```

## Testing

The project includes Unit Tests (using Mockito) and Integration Tests (`@WebMvcTest`).

To run the tests:
```bash
./mvnw test
```
