package com.example.graphqlapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the GraphQL API Spring Boot application.
 *
 * <p>{@link SpringBootApplication} is a convenience annotation that combines:
 * <ul>
 *   <li>{@code @Configuration} – marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – tells Spring Boot to configure beans based on
 *       the classpath (e.g., auto-configures Spring for GraphQL because
 *       {@code spring-boot-starter-graphql} is on the classpath).</li>
 *   <li>{@code @ComponentScan} – scans {@code com.example.graphqlapi} and sub-packages
 *       for {@code @Component}, {@code @Service}, {@code @Repository}, and
 *       {@code @Controller} beans.</li>
 * </ul>
 */
@SpringBootApplication
public class GraphqlApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphqlApiApplication.class, args);
    }
}
