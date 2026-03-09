package com.example.redissessionstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Redis Session Store mini-project.
 *
 * <p>{@link SpringBootApplication} enables auto-configuration, component scanning, and
 * configuration-property binding in one annotation.
 *
 * <p>Spring Session is activated automatically via the {@code spring-session-data-redis}
 * dependency on the classpath combined with the {@code spring.session.store-type=redis}
 * property in {@code application.yml}. No explicit {@code @EnableRedisHttpSession}
 * annotation is required when using Spring Boot auto-configuration.
 *
 * <p>Once active, Spring Session replaces Tomcat's default in-memory session store with
 * Redis. Every call to {@code HttpSession.setAttribute()} transparently writes the value
 * to Redis, and every call to {@code HttpSession.getAttribute()} reads it back — even
 * after a server restart, because the data lives in Redis, not in the JVM heap.
 */
@SpringBootApplication
public class RedisSessionStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisSessionStoreApplication.class, args);
    }
}
