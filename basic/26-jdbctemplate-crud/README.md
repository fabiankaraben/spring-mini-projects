# 26 JdbcTemplate CRUD

## Description
This mini-project demonstrates how to perform basic Create, Read, Update, and Delete (CRUD) operations using Spring Boot's `JdbcTemplate`. It communicates with a PostgreSQL database and shows how to execute raw SQL queries, map `ResultSet` to Java objects using `RowMapper`, handle auto-generated keys via `KeyHolder`, and prevent SQL injection using parameterized queries.

## Requirements
- Java 21 or higher
- Maven (Maven Wrapper `mvnw` is included in the project)
- Docker (required to run the PostgreSQL database and application)
- Docker Compose

## External Components & Docker
This project relies on **PostgreSQL** as its database. Since this is an external component, it requires Docker. A `docker-compose.yml` file is provided to orchestrate both the database container and the Spring Boot application container. 

The Spring Boot application is containerized using the provided `Dockerfile` and runs flawlessly alongside the database.

### Running with Docker Compose
To run the entire project (database + application), make sure Docker is running on your system, and from the project root directory execute:
```bash
docker compose up -d --build
```
This will start PostgreSQL and the Spring Boot application, making the API available on port `8080`.

To stop the containers and tear down the environment:
```bash
docker compose down
```
If you also want to remove the database volume, use `docker compose down -v`.

## How to use it
Once the application is running, you can interact with the REST API using the following `curl` commands.

### 1. Create a Book
```bash
curl -X POST http://localhost:8080/api/books \
     -H "Content-Type: application/json" \
     -d '{"title": "Spring in Action", "author": "Craig Walls", "publishedYear": 2020}'
```

### 2. Get All Books
```bash
curl -X GET http://localhost:8080/api/books
```

### 3. Get a Book by ID
```bash
curl -X GET http://localhost:8080/api/books/1
```

### 4. Update a Book
```bash
curl -X PUT http://localhost:8080/api/books/1 \
     -H "Content-Type: application/json" \
     -d '{"title": "Spring Boot in Action", "author": "Craig Walls", "publishedYear": 2021}'
```

### 5. Delete a Book
```bash
curl -X DELETE http://localhost:8080/api/books/1
```

## How to run the tests
Tests evaluate the web, service, and data layers using unit testing, Mockito (`@MockitoBean` replacing deprecated `@MockBean`), and Spring's sliced application contexts (`@WebMvcTest` and `@JdbcTest`). Note that `@JdbcTest` automatically leverages an embedded H2 database to execute and test the `JdbcTemplate` queries without needing an external database.

You can execute the tests locally without needing Docker using the Maven wrapper:
```bash
./mvnw clean test
```
