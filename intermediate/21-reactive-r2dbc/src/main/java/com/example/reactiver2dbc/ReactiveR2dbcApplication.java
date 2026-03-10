package com.example.reactiver2dbc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Reactive R2DBC Spring Boot application.
 *
 * <p>{@link SpringBootApplication} combines three annotations:
 * <ul>
 *   <li>{@code @Configuration} – marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – tells Spring Boot to auto-configure the
 *       application based on classpath contents (e.g., WebFlux, R2DBC PostgreSQL driver,
 *       Flyway migrations).</li>
 *   <li>{@code @ComponentScan} – scans the current package and sub-packages for
 *       Spring-managed components such as {@code @Service}, {@code @RestController}, etc.</li>
 * </ul>
 *
 * <p>Key differences from a traditional Spring MVC + JPA application:
 * <ul>
 *   <li><strong>Netty instead of Tomcat</strong> – Spring Boot selects Netty as the
 *       embedded server because {@code spring-boot-starter-webflux} is on the classpath.
 *       Netty uses an event-loop threading model (non-blocking I/O) rather than a
 *       thread-per-request model.</li>
 *   <li><strong>R2DBC instead of JDBC</strong> – all SQL queries are issued through the
 *       reactive R2DBC driver. No thread ever blocks waiting for a SQL response; the
 *       driver notifies the event loop via callbacks when results are ready.</li>
 *   <li><strong>Flyway for schema migrations</strong> – Flyway applies versioned SQL
 *       scripts at startup, keeping the database schema in sync with the code. Flyway
 *       uses JDBC internally (a separate datasource), while the app uses R2DBC at
 *       runtime.</li>
 * </ul>
 */
@SpringBootApplication
public class ReactiveR2dbcApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveR2dbcApplication.class, args);
    }
}
