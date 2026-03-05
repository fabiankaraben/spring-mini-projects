package com.example.basiccaching.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration class.
 *
 * <p>
 * Spring Boot auto-configures a {@code ConcurrentMapCacheManager} when:
 * </p>
 * <ul>
 * <li>The {@code spring-boot-starter-cache} dependency is on the
 * classpath.</li>
 * <li>No other cache provider (e.g., Redis, EhCache) is configured.</li>
 * <li>{@code @EnableCaching} is present on a configuration class.</li>
 * </ul>
 *
 * <p>
 * We define the {@code CacheManager} bean explicitly here to:
 * </p>
 * <ol>
 * <li>Make the chosen implementation visible and educational.</li>
 * <li>Pre-declare the cache names so they are guaranteed to exist at
 * startup.</li>
 * </ol>
 *
 * <p>
 * Internals: {@code ConcurrentMapCacheManager} wraps one or more
 * {@code ConcurrentHashMap} instances, one per named cache region. Entries live
 * in
 * the JVM heap and are lost when the application restarts. This is ideal for
 * development, testing, and learning, but not for production clustered
 * environments.
 * </p>
 */
@Configuration
public class CacheConfig {

    /**
     * Defines the application's {@link CacheManager}.
     *
     * <p>
     * {@code ConcurrentMapCacheManager} accepts the names of the caches it manages.
     * Any {@code @Cacheable} annotation that references a name not listed here
     * would
     * cause a runtime error unless {@code spring.cache.cache-names} is also
     * configured.
     * </p>
     *
     * @return a cache manager backed by {@code ConcurrentHashMap}
     */
    @Bean
    public CacheManager cacheManager() {
        // "products" matches the value used in @Cacheable/@CacheEvict/@CachePut
        // annotations
        return new ConcurrentMapCacheManager("products");
    }
}
