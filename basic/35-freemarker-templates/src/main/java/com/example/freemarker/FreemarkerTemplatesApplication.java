package com.example.freemarker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Freemarker Templates mini-project.
 *
 * <p>
 * {@code @SpringBootApplication} is a convenience annotation that combines:
 * <ul>
 * <li>{@code @Configuration} – marks this class as a source of bean
 * definitions.</li>
 * <li>{@code @EnableAutoConfiguration} – tells Spring Boot to auto-configure
 * beans
 * based on the dependencies on the classpath (e.g. FreeMarker, Tomcat).</li>
 * <li>{@code @ComponentScan} – scans the current package and its sub-packages
 * for
 * Spring components ({@code @Controller}, {@code @Service}, etc.).</li>
 * </ul>
 * </p>
 */
@SpringBootApplication
public class FreemarkerTemplatesApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreemarkerTemplatesApplication.class, args);
    }
}
