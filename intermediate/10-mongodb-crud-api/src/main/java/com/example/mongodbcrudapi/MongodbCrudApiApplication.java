package com.example.mongodbcrudapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the MongoDB CRUD API mini-project.
 *
 * <p>{@link SpringBootApplication} is a convenience annotation that combines:
 * <ul>
 *   <li>{@code @Configuration} – marks this as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – lets Spring Boot auto-configure beans
 *       based on the dependencies found on the classpath (e.g. Spring Data MongoDB,
 *       Spring Web MVC).</li>
 *   <li>{@code @ComponentScan} – scans this package and sub-packages for
 *       Spring-managed components ({@code @Controller}, {@code @Service}, etc.).</li>
 * </ul>
 *
 * <p>When Spring Boot detects {@code spring-boot-starter-data-mongodb} on the
 * classpath it automatically creates a {@code MongoClient} bean and configures
 * the connection to the MongoDB instance defined in {@code application.yml}.
 */
@SpringBootApplication
public class MongodbCrudApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MongodbCrudApiApplication.class, args);
    }
}
