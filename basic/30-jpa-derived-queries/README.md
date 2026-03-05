# JPA Derived Queries

🔹 This is a backend in Spring Boot creating repository methods by simply defining method names.

This mini-project demonstrates the power of Spring Data JPA Derived Queries. Instead of writing custom JPQL or native SQL queries, Spring Data JPA can automatically figure out the intent of a query based on the signature of the repository method name. It parses the method name and translates it into a database query.

## Requirements
- Java 21 or later
- Maven
- Docker (optional, but needed if testing with PostgreSQL easily)
- Spring Boot 3.5.0+

## Features Demonstrated
1. **Basic `findBy`**: Search by a single field.
2. **`findBy` with `And`**: Combine multiple fields.
3. **`findBy` with `GreaterThan`**: Search for values exceeding a number.
4. **`findBy` with `True`**: Query boolean fields.
5. **`findBy` with `Between`**: Search ranges, such as Date ranges.
6. **`findBy` with `OrderBy`**: Sort results.
7. **`findBy` with `StartingWith`**: Provide prefix searches on strings.

## How to Run

### 1. Using Docker Compose (Recommended)
This approach spins up both the application and a PostgreSQL database.
Ensure Docker is running, then execute:

```bash
docker compose up --build
```

### 2. Using Maven Wrapper Locally (Requires local PostgreSQL)
If you prefer running the application outside of Docker, make sure you have a local PostgreSQL running on port `5432` with username `postgres`, password `postgres` and a database named `derivedqueries`. Then you can use the Maven wrapper:

```bash
./mvnw spring-boot:run
```

## How to Use (curl examples)

When the application starts, it immediately seeds the database with a few employees if the table is empty.

1. **Find employees by department:**
```bash
curl -X GET http://localhost:8080/api/employees/department/Engineering
```

2. **Find a specific employee by first and last name:**
```bash
curl -X GET "http://localhost:8080/api/employees/search?firstName=Alice&lastName=Smith"
```

3. **Find employees with salary greater than 85000:**
```bash
curl -X GET http://localhost:8080/api/employees/salary-greater-than/85000
```

4. **Find active employees:**
```bash
curl -X GET http://localhost:8080/api/employees/active
```

5. **Find employees hired between two dates:**
```bash
curl -X GET "http://localhost:8080/api/employees/hired-between?start=2019-01-01&end=2021-12-31"
```

6. **Find active employees ordered by salary (desc):**
```bash
curl -X GET http://localhost:8080/api/employees/active-ordered
```

7. **Find employees whose first name starts with 'C':**
```bash
curl -X GET http://localhost:8080/api/employees/starting-with/C
```

## Running the Tests

Tests are written using **JUnit 5** and **Mockito**. We use `@DataJpaTest` for slicing the persistence layer (tests run against an in-memory H2 database) and `@WebMvcTest` for slicing the web layer with mock services.

To execute the unit and integration tests, run:

```bash
./mvnw test
```
