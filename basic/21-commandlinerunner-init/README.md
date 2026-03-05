# CommandLineRunner Init Mini-Project

## Description
This is a Spring Boot educational mini-project demonstrating how to use the `CommandLineRunner` interface. A `CommandLineRunner` acts as a hook to execute logic right after the Spring Application Context is loaded, but before the application starts accepting standard web requests. This is commonly used for tasks such as:
- Seeding a database with initial data
- Verifying external service configurations
- Running startup jobs or caching initial data

In this mini-project, we simulate seeding basic database records recursively if the database is found empty upon startup.

## Requirements
- Java 21 or higher
- Maven (Maven Wrapper is included)

## How It Works
1. When the application starts, Spring Boot looks for all beans implementing `CommandLineRunner`.
2. `DataSeederRunner` is picked up by Spring.
3. The `run` method is executed, checking if there are existing users in the `UserRepository`.
4. If empty, it seeds the repository with some initial dummy data.

## Project Structure
- `model.User` - Simple domain model class.
- `repository.UserRepository` - An in-memory repository list mimicking a database for educational simplicity without the need for docker containers.
- `seeder.DataSeederRunner` - The component implementing `CommandLineRunner`.
- `controller.UserController` - Exposes REST endpoints to let you query the seeded data.

## How to Compile and Run the Project

You can run the application directly using the included Maven Wrapper:

```bash
./mvnw spring-boot:run
```

If you prefer building a standalone JAR first:
```bash
./mvnw clean package
java -jar target/commandlinerunner-init-0.0.1-SNAPSHOT.jar
```

## How to Use (curl examples)

Once the application is running, the `CommandLineRunner` stringently populates the repository. You can verify this by checking the endpoints:

**Get all users:**
```bash
curl -s http://localhost:8080/users
```
**Expected Response:** A JSON array of the seeded users.

**Get a specific user by ID:**
```bash
curl -s http://localhost:8080/users/1
```
**Expected Response:** JSON showing Alice Smith's data.

## How to Run Tests

This project includes tests built with **JUnit 5**, **Mockito**, and sliced testing context. We use the updated `@MockitoBean` annotation to mock dependencies.

To execute the tests:
```bash
./mvnw test
```
