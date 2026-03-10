# Feign Client Integration

A Spring Boot mini-project that demonstrates how to create **declarative HTTP clients** using **Spring Cloud OpenFeign**. Instead of writing boilerplate HTTP code with `RestTemplate` or `WebClient`, OpenFeign lets you define a plain Java interface annotated with Spring MVC annotations, and Spring Cloud generates the implementation automatically at startup.

---

## What This Project Demonstrates

- **`@FeignClient`** — declare an interface as an HTTP client bound to a base URL
- **`@EnableFeignClients`** — scan the classpath for `@FeignClient` interfaces and register them as Spring beans
- **`@GetMapping` / `@PostMapping`** on interface methods — map method calls to HTTP requests
- **`@PathVariable`** — substitute values into URL path segments
- **`@RequestParam`** — append values as query parameters
- **`@RequestBody`** — serialise a Java object to a JSON request body
- **Feign timeout configuration** — `connect-timeout` and `read-timeout` via `application.yml`
- **Feign log levels** — `NONE`, `BASIC`, `HEADERS`, `FULL`
- **Service-layer fan-out** — combine two Feign calls into one aggregated response (`EnrichedPost`)
- **Error handling** — map `FeignException` subclasses to structured RFC 9457 `ProblemDetail` responses via `@RestControllerAdvice`
- **Bean Validation** — validate request bodies with `@Valid`, `@NotBlank`, `@NotNull`, `@Min`

### Upstream API

This application calls the free [JSONPlaceholder](https://jsonplaceholder.typicode.com) fake REST API. No account or API key is required. All data is read-only (write operations are accepted by JSONPlaceholder but not actually persisted).

---

## Architecture

```
HTTP Request
     │
     ▼
PostController          REST endpoints exposed to callers
     │
     ▼
PostService             Business logic; fan-out aggregation
     │
     ▼
JsonPlaceholderClient   OpenFeign declarative HTTP client interface
     │  (HTTP)
     ▼
JSONPlaceholder API     https://jsonplaceholder.typicode.com
```

### Project Structure

```
src/
├── main/
│   ├── java/com/example/feignclientintegration/
│   │   ├── FeignClientIntegrationApplication.java   Entry point + @EnableFeignClients
│   │   ├── client/
│   │   │   └── JsonPlaceholderClient.java           Feign client interface
│   │   ├── controller/
│   │   │   └── PostController.java                  REST controller
│   │   ├── domain/
│   │   │   ├── Post.java                            Post domain record
│   │   │   ├── Comment.java                         Comment domain record
│   │   │   └── User.java                            User domain record
│   │   ├── dto/
│   │   │   └── CreatePostRequest.java               Validated request DTO
│   │   ├── exception/
│   │   │   └── GlobalExceptionHandler.java          Feign + validation error mapping
│   │   └── service/
│   │       └── PostService.java                     Business logic + EnrichedPost
│   └── resources/
│       └── application.yml                          App + Feign configuration
└── test/
    ├── java/com/example/feignclientintegration/
    │   ├── PostControllerIntegrationTest.java        Full integration tests (WireMock)
    │   ├── domain/
    │   │   └── PostDomainTest.java                   Domain model unit tests
    │   └── service/
    │       └── PostServiceTest.java                  Service unit tests (Mockito)
    └── resources/
        ├── application-integration-test.yml          Integration test profile
        ├── docker-java.properties                    Docker API version for Testcontainers
        └── testcontainers.properties                 Testcontainers Docker config
```

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Java        | 21+     |
| Maven       | 3.9+ (or use the included Maven Wrapper) |
| Docker      | Required only for `docker compose` deployment |

---

## Running the Application

### Option 1 — Local (Maven Wrapper)

```bash
./mvnw spring-boot:run
```

The application starts on **http://localhost:8080** and connects to `https://jsonplaceholder.typicode.com`.

### Option 2 — Docker Compose

Build the image and start the container:

```bash
docker compose up --build
```

Stop and remove:

```bash
docker compose down
```

The application is accessible at **http://localhost:8080**.

> **Note:** This project has no local database or message broker. Docker Compose runs only the Spring Boot application container. The Feign client makes outbound HTTPS calls to `jsonplaceholder.typicode.com`.

To point the Feign client at a different URL (e.g., a local WireMock for manual testing):

```bash
JSONPLACEHOLDER_BASE_URL=http://localhost:9090 docker compose up
```

---

## API Reference

### Posts

#### Get all posts

```bash
curl -s http://localhost:8080/api/posts | jq '.[0:3]'
```

#### Get posts by user

```bash
curl -s "http://localhost:8080/api/posts?userId=1" | jq '.[0:3]'
```

#### Get a post by ID

```bash
curl -s http://localhost:8080/api/posts/1 | jq
```

Expected response:
```json
{
  "id": 1,
  "userId": 1,
  "title": "sunt aut facere repellat provident occaecati excepturi optio reprehenderit",
  "body": "quia et suscipit..."
}
```

#### Get a post with its comments (aggregated response)

This endpoint makes **two** Feign calls and combines them into one response:

```bash
curl -s http://localhost:8080/api/posts/1/with-comments | jq '{post: .post.title, commentCount: (.comments | length)}'
```

Expected response shape:
```json
{
  "post": {
    "id": 1,
    "userId": 1,
    "title": "...",
    "body": "..."
  },
  "comments": [
    {
      "id": 1,
      "postId": 1,
      "name": "...",
      "email": "...",
      "body": "..."
    }
  ]
}
```

#### Create a post

```bash
curl -s -X POST http://localhost:8080/api/posts \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "title": "My New Post", "body": "Post content here"}' | jq
```

Expected response (JSONPlaceholder always returns `id: 101`):
```json
{
  "id": 101,
  "userId": 1,
  "title": "My New Post",
  "body": "Post content here"
}
```

#### Create a post — validation error (blank title)

```bash
curl -s -X POST http://localhost:8080/api/posts \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "title": "", "body": "Content"}' | jq
```

Expected response (400 Bad Request):
```json
{
  "type": "https://problems.example.com/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "title: title must not be blank"
}
```

### Comments

#### Get comments for a post

```bash
curl -s http://localhost:8080/api/posts/1/comments | jq '.[0:2]'
```

### Users

#### Get all users

```bash
curl -s http://localhost:8080/api/users | jq '.[0:3]'
```

#### Get a user by ID

```bash
curl -s http://localhost:8080/api/users/1 | jq
```

Expected response:
```json
{
  "id": 1,
  "name": "Leanne Graham",
  "username": "Bret",
  "email": "Sincere@april.biz"
}
```

#### Get a non-existent user (404 from upstream)

```bash
curl -s http://localhost:8080/api/users/999 | jq
```

Expected response (404 Not Found):
```json
{
  "type": "https://problems.example.com/feign-not-found",
  "title": "Upstream Resource Not Found",
  "status": 404,
  "detail": "The upstream API returned 404: ..."
}
```

---

## Running the Tests

```bash
./mvnw clean test
```

### Test Overview

| Test Class | Type | Description |
|---|---|---|
| `PostDomainTest` | Unit | Verifies Java record accessors, equality, and null handling |
| `PostServiceTest` | Unit | Tests service logic with a Mockito mock of the Feign client |
| `PostControllerIntegrationTest` | Integration | Full Spring context + WireMock HTTP mock server |

### Unit Tests (`PostDomainTest`, `PostServiceTest`)

- No Spring context loaded — pure JUnit 5 + Mockito
- The Feign client interface is replaced by a Mockito mock
- Verify DTO mapping, fan-out aggregation, and delegation logic
- Run in milliseconds

### Integration Tests (`PostControllerIntegrationTest`)

- Full Spring Boot application context is started
- **WireMock** runs as an in-process HTTP server on a random port, acting as the fake JSONPlaceholder API
- The Feign client's `base-url` is overridden via `@DynamicPropertySource` to point at WireMock
- Tests verify the complete request-response pipeline: HTTP → controller → service → Feign → WireMock → back
- Covers: happy paths, upstream 404 mapping, bean validation errors, aggregated responses

---

## Key Concepts

### OpenFeign vs RestTemplate vs WebClient

| | OpenFeign | RestTemplate | WebClient |
|---|---|---|---|
| Style | Declarative (interface) | Imperative | Reactive / imperative |
| Boilerplate | None | High | Medium |
| Thread model | Blocking | Blocking | Non-blocking |
| Best for | Microservice-to-microservice | Legacy code | High-throughput async |

### How `@FeignClient` Works

1. `@EnableFeignClients` on the main class triggers a classpath scan for `@FeignClient` interfaces.
2. For each interface, Spring Cloud creates a **JDK dynamic proxy** at startup.
3. When a method is called, the proxy reads its Spring MVC annotations, builds an HTTP request, and executes it using Feign's HTTP client (Apache HttpClient or OkHttp, defaulting to `java.net.HttpURLConnection`).
4. The response body is deserialised to the method's return type via Jackson.
5. Non-2xx responses throw `FeignException` subclasses (e.g., `FeignException.NotFound` for 404).

### WireMock in Integration Tests

WireMock is an in-process HTTP mock server. In tests it replaces the real JSONPlaceholder API:

```java
// Register a stub: when GET /posts/1 is called, return this JSON
stubFor(get(urlEqualTo("/posts/1"))
    .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"id\":1,\"userId\":1,\"title\":\"My Post\",\"body\":\"Body\"}")));
```

The Feign client's `base-url` is overridden to `http://localhost:<wiremock-port>` via `@DynamicPropertySource`, so Feign calls WireMock instead of the real internet.
