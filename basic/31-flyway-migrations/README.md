# Flyway Migrations Mini-Project

This is a backend Spring Boot mini-project that manages database schema changes automatically using **Flyway**. It is part of the `spring-mini-projects` repository but is entirely independent.

## Description
Managing database schemas manually can lead to inconsistencies between environments. **Flyway** solves this by using versioned migration scripts (e.g., `V1__Create_table.sql`, `V2__Insert_data.sql`). When the application starts, Flyway reads these scripts and applies any new changes to the database systematically, ensuring the schema is always up-to-date with the code.

In this mini-project:
- We set up Spring Boot with Spring Data JPA and PostgreSQL.
- We disable Hibernate's automatic schema generation (`ddl-auto=validate`) because Flyway must take full control of the database schema.
- We provide three iterative migration scripts in `src/main/resources/db/migration/`:
  1. `V1__Create_employee_table.sql`: Creates an `employees` table.
  2. `V2__Insert_initial_employees.sql`: Inserts default data.
  3. `V3__Add_department_column.sql`: Alters the table to add a new `department` column and populates it.

## Requirements
- Java 21 or higher
- Maven (Wrapper is included)
- Docker and Docker Compose (to run the PostgreSQL database and application)

## How to Run

Because this project requires a PostgreSQL database, the easiest way to run the entire stack is using **Docker Compose**:

1. Make sure Docker is running on your machine.
2. Open a terminal in the root directory of this mini-project.
3. Run the following command to start both the application and the database:
   ```bash
   docker compose up --build
   ```
4. Once started, Flyway will automatically run the migration scripts to build the schema before the Spring context finishes loading.

*(To stop the application, press `Ctrl+C` and then run `docker compose down`)*

## Usage Examples

Once the application is running (on `localhost:8080`), you can test the REST endpoints. You will notice that the default data injected by Flyway's `V2` migration is already available!

### 1. Get All Employees
```bash
curl -X GET http://localhost:8080/api/employees
```
*Expected output: A JSON array of employees including Alice and Bob with their pre-filled departments.*

### 2. Get Employee By ID
```bash
curl -X GET http://localhost:8080/api/employees/1
```
*Expected output: Details for the employee with ID 1 (Alice).*

### 3. Create a New Employee
```bash
curl -X POST http://localhost:8080/api/employees \
     -H "Content-Type: application/json" \
     -d '{
           "name": "Charlie Day",
           "email": "charlie@example.com",
           "department": "Janitorial"
         }'
```
*Expected output: The newly created employee object with an auto-generated ID.*

## Testing

The project includes:
- **Unit Tests** for the Controller utilizing `@WebMvcTest` and Mockito (`@MockitoBean`).
- **Integration Tests** for the Repository using `@DataJpaTest`. For testing purposes, we use an in-memory **H2 database** parameterized with PostgreSQL mode (`MODE=PostgreSQL`). Because Flyway is active, these tests effectively validate our SQL migration scripts by applying them to the H2 database before executing the test assertions!

To run the tests:
```bash
./mvnw test
```
