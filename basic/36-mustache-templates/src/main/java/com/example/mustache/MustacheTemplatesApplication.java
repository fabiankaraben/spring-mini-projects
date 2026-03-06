package com.example.mustache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Mustache Templates mini-project.
 *
 * <p>
 * {@code @SpringBootApplication} is a meta-annotation that combines:
 * <ul>
 * <li>{@code @Configuration} — marks this class as a source of Spring bean
 * definitions.</li>
 * <li>{@code @EnableAutoConfiguration} — tells Spring Boot to auto-configure
 * beans
 * based on the jars found on the classpath (e.g. Mustache, Spring MVC,
 * Tomcat).</li>
 * <li>{@code @ComponentScan} — scans this package and all sub-packages for
 * Spring-managed components ({@code @Controller}, {@code @Service}, etc.).</li>
 * </ul>
 * </p>
 *
 * <p>
 * Mustache auto-configuration is triggered by the presence of
 * {@code spring-boot-starter-mustache} on the classpath. Spring Boot will:
 * <ol>
 * <li>Register a {@code MustacheViewResolver} that resolves logical view names
 * (returned by {@code @Controller} methods) to {@code .mustache} files under
 * {@code classpath:/templates/}.</li>
 * <li>No additional configuration is required in this class.</li>
 * </ol>
 * </p>
 */
@SpringBootApplication
public class MustacheTemplatesApplication {

    public static void main(String[] args) {
        // Launches the embedded Tomcat server and initialises the Spring context
        SpringApplication.run(MustacheTemplatesApplication.class, args);
    }
}
