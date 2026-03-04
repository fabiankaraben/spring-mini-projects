# Properties Configuration Mini-Project

This is a Spring Boot application demonstrating how to bind `application.properties` to a POJO using `@ConfigurationProperties`.

## Requirements
- Java 21+
- Dependency Manager: Maven
- Framework: Spring Boot 3.x
- Testing: JUnit 5, Mockito, `@WebMvcTest`
- Maven Wrapper is included

## Overview

In Spring Boot, the `@ConfigurationProperties` annotation is a highly structured and type-safe way to bind related external configuration properties (e.g., in `application.properties` or `application.yml`) to strongly-typed Java Beans. 

This educational mini-project defines an `AppProperties` class that maps specific custom configuration settings and exposes them via a simple REST endpoint. 

## How To Run

Use the included Maven wrapper to run the application:
```bash
./mvnw spring-boot:run
```

## Usage

Once running, you can access the configuration endpoint with curl:

```bash
curl http://localhost:8080/config
```

The application will return a JSON representation containing the values loaded from `application.properties`.

## How To Run Tests

To execute tests including unit tests and `WebMvcTest` slices:
```bash
./mvnw test
```
