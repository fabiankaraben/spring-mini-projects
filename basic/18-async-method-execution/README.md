# Async Method Execution Mini-Project

## Overview
This mini-project demonstrates how to execute long-running methods in background threads using Spring Boot's `@Async` annotation. It features configuring a custom thread pool (`ThreadPoolTaskExecutor`) to gracefully manage async background behavior, thus preventing HTTP worker threads from being blocked by intensive logic workloads.

## Requirements
* **Java:** 21+
* **Dependency Manager:** Maven (Maven Wrapper included)
* **Testing:** JUnit 5, Mockito, and Spring Web MVC Integration Testing (`@WebMvcTest`)
* **Core Mechanisms:** `@EnableAsync` and `@Async` executing logic isolated from controllers.

## Architecture
- **AsyncConfig**: Initializes a `ThreadPoolTaskExecutor` bean to strictly specify thread limitations (core, max sizes, thread names).
- **TaskService**: Hosts the core simulation (`Thread.sleep()`) tagged with `@Async("taskExecutor")`. Returns a non-blocking `CompletableFuture`.
- **TaskController**: Provides mapping edges to interact with the service logic transparently.

## Usage

Start the Spring Boot application, returning the application to an active running state bound to `localhost:8080`.

### 1. Fire-and-Forget Executions
To immediately acknowledge a long-running request without waiting for the task to finish, execute:

```bash
curl -i -X GET http://localhost:8080/api/tasks/fire/fire-and-forget
```
**Expected Response**:
```http
HTTP/1.1 202 Accepted
Content-Type: text/plain;charset=UTF-8

Task fire has been accepted for background processing.
```
*Note: Check your application logs to see the task starting properly on an "AsyncThread-[x]" rather than a default "http-nio-8080-exec-[x]" thread.*

### 2. Non-blocking Async Request Mapping
With Servlet APIs combined with Spring async (`CompletableFuture`), the Spring Web thread will unbind immediately and later return the asynchronous computation out-of-band:

```bash
curl -i -X GET http://localhost:8080/api/tasks/wait/non-blocking
```
**Expected Response (Wait for ~2 seconds)**:
```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

Task wait completed successfully
```
*Despite waiting via cURL, the HTTP container thread under Tomcat was inherently unblocked and returned to process other incoming HTTP calls during the simulation execution frame!*

## Running tests
To run the included unit tests and sliced integration tests:
```bash
./mvnw clean test
```
