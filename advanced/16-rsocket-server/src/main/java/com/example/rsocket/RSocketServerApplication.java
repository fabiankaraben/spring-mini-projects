package com.example.rsocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the RSocket Server Spring Boot application.
 *
 * <p>When this application starts, Spring Boot auto-configuration:
 * <ul>
 *   <li>Starts an embedded Netty-based RSocket TCP server on port 7000
 *       (configured via spring.rsocket.server.port in application.yml).</li>
 *   <li>Starts a Spring MVC HTTP server on port 8080 for Actuator endpoints.</li>
 *   <li>Scans for {@code @Controller} + {@code @MessageMapping} beans and
 *       registers them as RSocket route handlers.</li>
 *   <li>Initializes Spring Data JPA with the H2 in-memory database.</li>
 * </ul>
 *
 * <p>{@code @SpringBootApplication} is a convenience annotation that combines:
 * <ul>
 *   <li>{@code @Configuration}     — marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} — enables Spring Boot's auto-configuration.</li>
 *   <li>{@code @ComponentScan}     — scans the current package and sub-packages for beans.</li>
 * </ul>
 */
@SpringBootApplication
public class RSocketServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RSocketServerApplication.class, args);
    }
}
