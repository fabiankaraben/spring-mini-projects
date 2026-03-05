# JPA Custom Queries

This is a backend mini-project built with Spring Boot that demonstrates how to use the `@Query` annotation in Spring Data JPA to define custom JPQL and native SQL queries.

## Requirements
* Java 21 or higher
* Docker and Docker Compose (to run the PostgreSQL database and application)
* Maven 3.9+ (Wrapper included)

## Overview
The application manages `Employee` records. We have created custom queries to search and aggregate employee data:
* Finding employees by exactly matching an email.
* Finding employees by a case-insensitive name search using JPQL.
* Finding all employees in a specific department using Native SQL.

The database used is PostgreSQL, running within a Docker container via Docker Compose.

## How to use

First, build the project and spin up the Docker containers using Compose. The `docker-compose.yml` file defines both the Postgres database container and the application container.

1. Package the application (skip tests if needed):
```bash
./mvnw clean package -DskipTests
```

2. Start the services using Docker Compose:
```bash
docker compose up --build
```
This will start both the PostgreSQL database (`db`) and the Spring Boot application (`app`). The application will be running and listening on port 8080.

### Creating an Employee
```bash
curl -X POST http://localhost:8080/api/employees \
     -H "Content-Type: application/json" \
     -d '{"name": "Alice Smith", "email": "alice@example.com", "department": "Engineering", "salary": 85000}'
     
curl -X POST http://localhost:8080/api/employees \
     -H "Content-Type: application/json" \
     -d '{"name": "Bob Jones", "email": "bob@example.com", "department": "Marketing", "salary": 65000}'

curl -X POST http://localhost:8080/api/employees \
     -H "Content-Type: application/json" \
     -d '{"name": "Charlie Brown", "email": "charlie@example.com", "department": "Engineering", "salary": 75000}'
```

### Get all Employees
```bash
curl http://localhost:8080/api/employees
```

### Custom Query: Find exactly by Email (JPQL)
```bash
curl http://localhost:8080/api/employees/search/email?email=alice@example.com
```

### Custom Query: Find by Name Containing (JPQL, case insensitive)
```bash
curl http://localhost:8080/api/employees/search/name?name=ob
```

### Custom Query: Find by Department (Native SQL)
```bash
curl http://localhost:8080/api/employees/department/Engineering
```

## Running the Tests
The application includes:
- **Unit Tests** for the Service layer.
- **Sliced Integration Tests** (`@DataJpaTest`) using an embedded H2 database for the Repository layer.
- **Sliced Integration Tests** (`@WebMvcTest`) for the Controller layer.

To run the test suite, simply use:
```bash
./mvnw test
```

## Stopping the Application
To stop the application and database, run:
```bash
docker compose down
```
_Note: If you want to delete the database volume as well, run `docker compose down -v`_
