# Spring Data JPA Setup Mini-Project

This mini-project demonstrates how to set up and use Spring Data JPA in a Spring Boot application. It incorporates a simple `Book` Entity and connects it to a `BookRepository` that handles data persistence dynamically. Spring Data JPA dramatically simplifies data access code by removing boilerplate and generating repository implementations directly based on method names.

## Features
- Complete CRUD functionality for a `Book` entity.
- Uses an in-memory **H2 database** (No external Docker container necessary).
- Sliced integration tests leveraging `@DataJpaTest` and `@WebMvcTest`.
- Exposes standard RESTful API endpoints.

## Requirements
- Java 21 or higher
- Maven (Maven wrapper included)

## Educational Points
- **Entity Setup:** Demonstrates using `@Entity`, `@Id`, and `@GeneratedValue` annotations.
- **Repository Interface:** Exposes the power of `JpaRepository` interface, giving native access to database queries without needing to implement an adapter bridging code.
- **Unit and Sliced Integration Testing:** The use of JUnit 5 and Mockito, leveraging the brand-new `@MockitoBean` for modern Spring testing.
- **H2 in-memory Configuration:** Allows developers to inspect the DB visually inside the browser through `/h2-console`.

## Setup and Usage

### Running the App
1. Navigate to the root directory `27-spring-data-jpa-setup`.
2. Start the application:
   ```bash
   ./mvnw spring-boot:run
   ```
   The API will be available at `http://localhost:8080`.
   You can inspect the database visually at `http://localhost:8080/h2-console` using:
   - Driver Class: `org.h2.Driver`
   - JDBC URL: `jdbc:h2:mem:bookdb`
   - User Name: `sa`
   - Password: `password`

### Endpoints and Curl Examples

**1. Create a Book (POST)**
```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"title": "Spring in Action", "author": "Craig Walls", "price": 40.0}' \
  http://localhost:8080/api/books
```

**2. List All Books (GET)**
```bash
curl -X GET http://localhost:8080/api/books
```

**3. Get a Single Book (GET)**
```bash
curl -X GET http://localhost:8080/api/books/1
```

**4. Delete a Book (DELETE)**
```bash
curl -X DELETE http://localhost:8080/api/books/1
```

## Running Tests
Run all unit and sliced integration tests to verify functionality using Maven Wrapper:

```bash
./mvnw clean test
```
