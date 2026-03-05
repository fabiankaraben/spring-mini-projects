# RestTemplate Client Mini-Project

## Description
This is a Spring Boot mini-project demonstrating how to use `RestTemplate` to make basic HTTP GET and POST requests to an external API (JSONPlaceholder). It acts as a client proxy, exposing its own REST endpoints that delegate calls using `RestTemplate`.

## Requirements
- Java 21 or higher
- Maven Build Tool
- Spring Boot 3.4.x
- **No external infrastructure (like Docker/Databases) is required** as it uses the public JSONPlaceholder API.

## How to use it
To run the application, navigate to the root directory of this project where `pom.xml` is located, and execute the following Maven Wrapper command:

```bash
./mvnw spring-boot:run
```

Once the application is running (by default on port 8080), you can test the endpoints using `curl`:

1. **Get all posts:**
   ```bash
   curl -X GET http://localhost:8080/api/client/posts
   ```

2. **Get a specific post by ID:**
   ```bash
   curl -X GET http://localhost:8080/api/client/posts/1
   ```

3. **Create a new post:**
   ```bash
   curl -X POST http://localhost:8080/api/client/posts \
   -H "Content-Type: application/json" \
   -d '{
       "title": "My New Post",
       "body": "This is the body of the post",
       "userId": 1
   }'
   ```

## How to run the tests
This mini-project includes unit tests using JUnit 5 and Mockito, as well as sliced integration tests using `@WebMvcTest`. 

To run the test suite, execute the following command:

```bash
./mvnw test
```
