package com.example.testcontainerspostgres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Testcontainers Postgres mini-project.
 *
 * <p>This application exposes a simple Product REST API backed by PostgreSQL.
 * Its primary purpose is to demonstrate how to write integration tests using
 * <b>Testcontainers</b> — a library that starts a real PostgreSQL Docker
 * container automatically during the test phase, providing a production-
 * equivalent database without any manual setup.
 *
 * <p>The project also includes unit tests for the service layer using
 * <b>JUnit 5</b> and <b>Mockito</b>, showing the difference between
 * pure unit tests (no database, no Spring context) and full integration tests
 * (real database, full Spring context slice).
 */
@SpringBootApplication
public class TestcontainersPostgresApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestcontainersPostgresApplication.class, args);
    }
}
