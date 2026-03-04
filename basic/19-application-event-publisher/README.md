# 19 - Application Event Publisher

This is a backend mini-project built in Spring Boot that demonstrates how to publish and listen to custom application events using the Spring `ApplicationContext`. It showcases event-driven architecture within a monolithic Spring application, allowing decoupling of business logic.

`UserRegistrationPublisher` simulates an entry-point event trigger. `UserRegistrationListener` demonstrates a decoupled component catching that event seamlessly.

## Requirements
* Java 21 or later
* Maven 3.9+ (or use the provided Wrapper)
* Completely docker-free. No external databases or message brokers required. 

## Key Features & Goals
1. **Decoupled Business Flow:** Emphasizes internal decoupling through events.
2. **Standard Interfaces:** Showcases `ApplicationEventPublisher` and `@EventListener`.
3. **In-Memory Verification:** A lightweight REST controller checks what events got processed contextually.

## Setup & Running 
Use the Maven wrapper to build and run the application locally:

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run
```
Alternatively, build it and run via `java -jar`:
```bash
./mvnw clean package
java -jar target/application-event-publisher-0.0.1-SNAPSHOT.jar
```

## Usage (curl Examples)

The embedded server binds to `http://localhost:8080`.

**1. Create Event (Register a User)**
Trigger the component structure by posting to the users endpoint. This will invoke the `UserRegistrationPublisher` and automatically notify the `UserRegistrationListener` handling in the background.

```bash
curl -X POST http://localhost:8080/api/users/fabian
```
*Expected Output:*
```text
Successfully handled initial step and published UserRegistrationEvent for 'fabian'.
```

**2. Verify Event Side-effects**
Check which users were recorded in memory strictly via the Event Listener hook:

```bash
curl http://localhost:8080/api/users
```
*Expected Output:*
```json
["fabian"]
```

## Testing

The project has comprehensive unit tests using **JUnit 5** and **Mockito**. There is also a sliced integration test context verified by `@WebMvcTest`. 
You can run all the tests via:
```bash
./mvnw test
```
