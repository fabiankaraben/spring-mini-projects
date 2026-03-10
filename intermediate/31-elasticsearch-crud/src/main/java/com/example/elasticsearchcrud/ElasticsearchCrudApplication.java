package com.example.elasticsearchcrud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Elasticsearch CRUD mini-project.
 *
 * <p>{@link SpringBootApplication} is a convenience annotation that combines:
 * <ul>
 *   <li>{@code @Configuration} – marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – activates Spring Boot's auto-configuration
 *       mechanism, which, among other things, auto-configures the Elasticsearch client
 *       based on {@code spring.elasticsearch.*} properties.</li>
 *   <li>{@code @ComponentScan} – scans this package and sub-packages for Spring
 *       components (controllers, services, repositories, etc.).</li>
 * </ul>
 */
@SpringBootApplication
public class ElasticsearchCrudApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElasticsearchCrudApplication.class, args);
    }
}
