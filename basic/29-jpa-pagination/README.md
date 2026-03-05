# JPA Pagination

## Description
This is a Spring Boot backend mini-project that demonstrates how to retrieve database records in pages using `Pageable` and Spring Data JPA. By returning data in chunks (or pages), you reduce memory overload and improve application performance. 

This educational project includes a `DataSeeder` that automatically populates the PostgreSQL database with 50 dummy `Employee` records on startup if none exist. This way, pagination can be tested immediately.

## Requirements
- Java 21 or higher
- Maven (Maven Wrapper is included)
- Docker & Docker Compose (for running the PostgreSQL database and application simultaneously)

## Features & Technologies
- Spring Boot 3 + Spring Data JPA
- PostgreSQL Database
- Pagination & Sorting built-in via Spring's `Pageable`
- REST Controller passing `Pageable` natively from HTTP parameters
- Unit Tests using JUnit 5 and Mockito
- Integration Tests using `@DataJpaTest` and `@WebMvcTest`
- Docker & Docker Compose setup

## Getting Started

Because this application relies on a PostgreSQL database, the easiest way to run it is through Docker Compose.

1. **Option 1: Using Docker Compose (Recommended)**
   Make sure you have Docker installed and running. Start the entire stack right from the root of this mini-project:
   ```bash
   docker compose up --build
   ```
   *Note*: The first build will take some time since it will download the Maven dependencies and compile the `.jar`. After initialization, Spring Boot will automatically launch and generate dummy employees.

2. **Option 2: Running Locally (Providing your own DB)**
   You can run it locally without Docker, but you must have a PostgreSQL instance running. Ensure you override or set the environment properties `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` and `DB_PASSWORD`.
   ```bash
   export DB_HOST=localhost
   export DB_PORT=5432
   export DB_NAME=pagination_db
   export DB_USER=myuser
   export DB_PASSWORD=mypassword
   ./mvnw spring-boot:run
   ```

## Usage (cURL Examples)
Once the app is running on port 8080 and properties have been seeded, open a new terminal and try out paginated endpoints:

**1. Retrieve a Paginated List of All Employees**
Fetching page 0, specifying 5 items per page, and sorting them by `salary` descending:
```bash
curl "http://localhost:8080/api/employees?page=0&size=5&sort=salary,desc"
```

**2. Retrieve a Paginated List Filtered By Department**
Filter primarily by department (`IT`) and fetch page 1 with 3 items per page:
```bash
curl "http://localhost:8080/api/employees/department?department=IT&page=1&size=3"
```

*Note: Pagination in Spring is `0`-indexed. If `page` and `size` parameters aren't provided, `@PageableDefault` annotations on endpoints kick in to provide sensible defaults.*

## Running the Tests
This project utilizes both unit tests with Mockito and sliced integration tests using an embedded `H2` test database.

You can run the full test suite without needing Docker or a PostgreSQL instance running, since integration tests automatically map to the embedded Test DB.

Run the tests using the Maven wrapper:
```bash
./mvnw test
```
