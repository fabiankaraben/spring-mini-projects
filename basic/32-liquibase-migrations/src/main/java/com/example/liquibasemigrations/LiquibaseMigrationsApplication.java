package com.example.liquibasemigrations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Liquibase Migrations mini-project.
 *
 * <p>When Spring Boot starts, it automatically detects the liquibase-core dependency
 * and runs the master changelog file defined in application.properties before
 * the application context is fully initialized. This guarantees the database schema
 * is always up to date before the application begins serving requests.</p>
 */
@SpringBootApplication
public class LiquibaseMigrationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiquibaseMigrationsApplication.class, args);
    }

}
