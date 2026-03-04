# Content Negotiation Mini-Project

This is a Spring Boot mini-project demonstrating **Content Negotiation**. It returns data formatted as either JSON or XML depending on the client's preference, specified in the `Accept` HTTP header, using Spring's built-in `HttpMessageConverters`.

## Requirements
*   **Java 21** or higher.
*   **Maven** (the project includes a Maven wrapper, `./mvnw`).
*   **Spring Boot 3.x**
*   **Jackson Dataformat XML** for processing and returning XML.
*   **JUnit 5** and **Mockito** for testing.

## How it Works
The application exposes a single endpoint `/api/messages`. Because we've included `jackson-dataformat-xml` in our dependencies, Spring Boot's automatic configuration instantiates both JSON and XML message converters. 

When a client makes a request, Spring MVC parses the `Accept` header to determine the desired response content type:
*   `Accept: application/json` produces JSON.
*   `Accept: application/xml` produces XML.

## Usage

Start the application using the Maven wrapper:
```bash
./mvnw spring-boot:run
```

Once the application is running (on the default port `8080`), you can test the content negotiation mechanism using `curl`.

### Requesting JSON
Provide the `Accept: application/json` header:
```bash
curl -X GET http://localhost:8080/api/messages \
     -H "Accept: application/json"
```

**Expected JSON Response:**
```json
{
  "id": "e24d26f7-c353-4dc9-8b64-28b3a0cc1d1b",
  "content": "Hello, this is a response demonstrating Content Negotiation!"
}
```

### Requesting XML
Provide the `Accept: application/xml` header:
```bash
curl -X GET http://localhost:8080/api/messages \
     -H "Accept: application/xml"
```

**Expected XML Response:**
```xml
<message>
  <id>e24d26f7-c353-4dc9-8b64-28b3a0cc1d1b</id>
  <content>Hello, this is a response demonstrating Content Negotiation!</content>
</message>
```

## Running Tests
The project includes a sliced integration test `@WebMvcTest` to verify that both JSON and XML responses are fully functional and properly formatted by the framework.

To execute the tests, run:
```bash
./mvnw clean test
```
