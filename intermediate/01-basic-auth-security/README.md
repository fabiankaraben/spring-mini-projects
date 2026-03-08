# Basic Auth Security

This is a backend mini-project built with Spring Boot that demonstrates how to secure endpoints using Spring Security and HTTP Basic credentials. The project is completely independent and includes its own domain logic, unit tests, and integration tests.

## Requirements

*   **Java 21** or higher
*   **Docker** and **Docker Compose** (for running the PostgreSQL database and application together)

## Features

*   Secured REST endpoints using HTTP Basic Authentication
*   Custom `UserDetailsService` integrating with a PostgreSQL database via Spring Data JPA
*   Data initialization on startup (creates default `user` and `admin` accounts)
*   Role-based access control (differentiating between `USER` and `ADMIN` roles)
*   Unit testing for domain logic using **JUnit 5** and **Mockito**
*   Full integration testing using **Testcontainers** to spin up a real PostgreSQL instance during tests

## How to Run

### Using Docker Compose (Recommended)

This project requires a PostgreSQL database to run. The easiest way to start both the database and the application is using Docker Compose.

1.  Make sure Docker is running on your machine.
2.  Navigate to the project directory:
    ```bash
    cd intermediate/01-basic-auth-security
    ```
3.  Start the services:
    ```bash
    docker compose up --build
    ```

The application will be accessible at `http://localhost:8080`.

### Running Locally (Without Docker for the App)

If you prefer to run the application directly via your IDE or Maven, you still need a PostgreSQL database. You can start just the database using Docker Compose:

1.  Start only the database:
    ```bash
    docker compose up db -d
    ```
2.  Run the application using the Maven wrapper:
    ```bash
    ./mvnw spring-boot:run
    ```

## Default Users

On startup, the application creates two default users:

*   **User:**
    *   Username: `user`
    *   Password: `password`
    *   Role: `USER`
*   **Admin:**
    *   Username: `admin`
    *   Password: `admin123`
    *   Role: `ADMIN`

## Usage Examples (cURL)

Here are some examples of how to interact with the API using `curl`.

### 1. Public Endpoint (No Auth Required)

```bash
curl -X GET http://localhost:8080/api/public
```
**Expected Response (200 OK):**
```
This is a public endpoint. Anyone can access it.
```

### 2. User Endpoint (Requires Auth)

**Without credentials (will fail):**
```bash
curl -X GET http://localhost:8080/api/user/me -v
```
**Expected Response (401 Unauthorized)**

**With valid user credentials:**
```bash
curl -X GET -u user:password http://localhost:8080/api/user/me
```
**Expected Response (200 OK):**
```
Hello, user! You have accessed a secured endpoint.
```

**With invalid credentials:**
```bash
curl -X GET -u user:wrongpass http://localhost:8080/api/user/me -v
```
**Expected Response (401 Unauthorized)**

### 3. Admin Endpoint (Requires ADMIN Role)

**With standard user credentials (will fail):**
```bash
curl -X GET -u user:password http://localhost:8080/api/admin/data -v
```
**Expected Response (403 Forbidden)**

**With admin credentials:**
```bash
curl -X GET -u admin:admin123 http://localhost:8080/api/admin/data
```
**Expected Response (200 OK):**
```
Hello Admin admin! You have accessed an admin-only endpoint.
```

## Running Tests

The project includes both unit tests and integration tests. The integration tests use Testcontainers to spin up a PostgreSQL instance, so you must have Docker running to execute them.

To run all tests, use the included Maven wrapper:

```bash
./mvnw clean test
```
