package com.example.basiccaching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Entry point for the Basic Caching mini-project.
 *
 * <p>
 * The @EnableCaching annotation activates Spring's annotation-driven cache
 * management
 * capability. Without it, @Cacheable, @CacheEvict, and @CachePut annotations on
 * methods
 * would be completely ignored at runtime.
 * </p>
 *
 * <p>
 * Since we have no external cache provider configured (e.g., Redis, EhCache),
 * Spring Boot auto-configures a {@code ConcurrentMapCacheManager}, which stores
 * cache entries in a plain {@code ConcurrentHashMap} in the JVM heap. This is
 * the
 * simplest possible cache implementation—perfect for learning the concepts.
 * </p>
 */
@SpringBootApplication
@EnableCaching // Activates Spring's cache abstraction across the application
public class BasicCachingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BasicCachingApplication.class, args);
    }
}
