# H2 Database Setup

This is a backend Spring Boot mini-project demonstrating how to configure an embedded H2 database and interact with it. It also shows how to query the database automatically on startup using a `CommandLineRunner`.

## Features
- Provides REST endpoints to Create and Retrieve `Product` entities.
- Configures an in-memory embedded H2 Database.
- Pre-populates and queries the database on application startup.
- Enables the H2 Web Console to inspect the schema/data from the browser.
- Full testing suite (Unit using JUnit 5 & Mockito, Integration tests via `@DataJpaTest` and `@WebMvcTest`).

## Requirements
- **Java 21+**
- **Maven** (A Maven Wrapper is included in the project)

## Usage

### 1. Build & Run
You can run the application seamlessly using the provided Maven wrapper:

```bash
./mvnw spring-boot:run
```

Upon startup, check your console. You should see `DatabaseInitializer` logging the inserted and queried entities right before the application finish booting.

### 2. Accessing H2 Console
Open your browser and navigate to:
`http://localhost:8080/h2-console`

- **JDBC URL:** `jdbc:h2:mem:testdb`
- **Username:** `sa`
- **Password:** *(leave blank)*

### 3. API Examples (curl)

**Retrieve all products (GET)**:
```bash
curl -X GET http://localhost:8080/api/products
```

**Create a new product (POST)**:
```bash
curl -X POST http://localhost:8080/api/products \
     -H "Content-Type: application/json" \
     -d '{
           "name": "Wireless Mouse",
           "price": 25.50
         }'
```

## Running the Tests

To run the unit tests and the sliced integration tests, execute the following command:

```bash
./mvnw test
```

This commands runs:
- `ProductServiceTest` (Unit Tests using Mockito)
- `ProductRepositoryTest` (Data JPA sliced tests using `@DataJpaTest`)
- `ProductControllerTest` (Web MVC sliced tests using `@WebMvcTest`)
