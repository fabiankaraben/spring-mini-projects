package com.example.reactivewebfluxapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Reactive WebFlux API Spring Boot application.
 *
 * <p>{@link SpringBootApplication} combines three annotations:
 * <ul>
 *   <li>{@code @Configuration} – marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – tells Spring Boot to auto-configure the
 *       application based on classpath contents (e.g., WebFlux, MongoDB reactive driver).</li>
 *   <li>{@code @ComponentScan} – scans the current package and sub-packages for
 *       Spring-managed components such as {@code @Service}, {@code @RestController}, etc.</li>
 * </ul>
 *
 * <p>Because this project uses {@code spring-boot-starter-webflux}, Spring Boot
 * automatically configures Netty (not Tomcat) as the embedded server and sets up
 * Project Reactor's event-loop threading model for non-blocking I/O.
 */
@SpringBootApplication
public class ReactiveWebfluxApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveWebfluxApiApplication.class, args);
    }
}
