package com.example.graphqlmutations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the GraphQL Mutations Spring Boot application.
 *
 * <p>This mini-project demonstrates how to handle state changes through GraphQL
 * mutations. The domain is a Task Manager with two main entities:
 * <ul>
 *   <li>{@code Project} – a container that groups related tasks.</li>
 *   <li>{@code Task} – a unit of work belonging to a project, with a status
 *       lifecycle: TODO → IN_PROGRESS → DONE.</li>
 * </ul>
 *
 * <p>{@link SpringBootApplication} is a convenience annotation that combines:
 * <ul>
 *   <li>{@code @Configuration} – marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – tells Spring Boot to configure beans based on
 *       the classpath (e.g., auto-configures Spring for GraphQL because
 *       {@code spring-boot-starter-graphql} is on the classpath).</li>
 *   <li>{@code @ComponentScan} – scans {@code com.example.graphqlmutations} and
 *       sub-packages for {@code @Component}, {@code @Service}, {@code @Repository},
 *       and {@code @Controller} beans.</li>
 * </ul>
 */
@SpringBootApplication
public class GraphqlMutationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphqlMutationsApplication.class, args);
    }
}
