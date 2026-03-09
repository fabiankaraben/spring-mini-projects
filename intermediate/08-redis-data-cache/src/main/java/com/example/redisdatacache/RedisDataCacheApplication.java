package com.example.redisdatacache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Entry point for the Redis Data Cache mini-project.
 *
 * <p>{@link SpringBootApplication} enables auto-configuration, component scanning, and
 * configuration-property binding in one annotation.
 *
 * <p>{@link EnableCaching} activates Spring's annotation-driven cache management, which
 * scans beans for {@code @Cacheable}, {@code @CachePut}, and {@code @CacheEvict}
 * annotations and wraps the target methods with proxy-based caching behaviour.
 *
 * <p>The actual cache store is Redis; Spring Boot auto-configures a
 * {@code RedisCacheManager} when {@code spring-boot-starter-data-redis} is on the
 * classpath and a Redis connection is available (see {@code application.yml}).
 */
@SpringBootApplication
@EnableCaching
public class RedisDataCacheApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisDataCacheApplication.class, args);
    }
}
