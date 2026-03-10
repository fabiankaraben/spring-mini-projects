package com.example.dockercomposesupport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Docker Compose Support demo application.
 *
 * <h2>What this demo shows</h2>
 * <p>Spring Boot 3.1 introduced first-class Docker Compose support via the
 * {@code spring-boot-docker-compose} dependency. When this dependency is on the
 * classpath and a {@code compose.yml} file exists in the project root, Spring Boot
 * automatically:</p>
 * <ol>
 *   <li>Detects the compose file at startup.</li>
 *   <li>Runs {@code docker compose up} to start the declared services (e.g. PostgreSQL).</li>
 *   <li>Overrides the application's DataSource (and other service) connection properties
 *       to point to the containers — no manual configuration required.</li>
 *   <li>Runs {@code docker compose stop} when the application shuts down.</li>
 * </ol>
 *
 * <h2>Domain</h2>
 * <p>A simple book catalogue REST API backed by PostgreSQL. The focus is on the
 * Docker Compose integration, not the business logic.</p>
 *
 * <h2>Key files</h2>
 * <ul>
 *   <li>{@code compose.yml} — defines the PostgreSQL dev service used by Spring Boot.</li>
 *   <li>{@code application.yml} — configures Docker Compose behaviour and JPA.</li>
 *   <li>{@code docker-compose.yml} — full production deployment (app + DB together).</li>
 * </ul>
 */
@SpringBootApplication
public class DockerComposeSupportApplication {

    public static void main(String[] args) {
        SpringApplication.run(DockerComposeSupportApplication.class, args);
    }
}
