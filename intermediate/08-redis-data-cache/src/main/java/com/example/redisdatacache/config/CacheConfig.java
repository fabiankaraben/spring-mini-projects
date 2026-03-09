package com.example.redisdatacache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Cache configuration that customises how Spring stores data in Redis.
 *
 * <p>Spring Boot auto-configures a {@link RedisCacheManager} when
 * {@code spring-boot-starter-data-redis} is on the classpath, but the defaults
 * use Java serialisation and have no TTL. This configuration class overrides
 * those defaults with:
 *
 * <ul>
 *   <li><strong>JSON serialisation</strong> via {@link GenericJackson2JsonRedisSerializer}
 *       – cached values are stored as human-readable JSON in Redis (use
 *       {@code redis-cli monitor} or {@code redis-cli keys '*'} to inspect them).
 *       {@link StringRedisSerializer} is used for keys so they are also readable.</li>
 *   <li><strong>Per-cache TTL</strong> – each named cache has an independent
 *       time-to-live. After the TTL expires Redis automatically deletes the entry
 *       and the next request will be a cache miss, triggering a fresh data store
 *       read.</li>
 *   <li><strong>Null-value caching disabled</strong> – prevents Redis from storing
 *       explicit null results, which could mask data creation.</li>
 * </ul>
 */
@Configuration
public class CacheConfig {

    /**
     * TTL for the {@code "products"} cache (single product lookups).
     * 10 minutes is a reasonable default for product data that changes infrequently.
     */
    private static final Duration PRODUCTS_TTL = Duration.ofMinutes(10);

    /**
     * TTL for the {@code "products-all"} cache (full product list).
     * A shorter TTL is used here because the list changes more often
     * (any create/update/delete invalidates it).
     */
    private static final Duration PRODUCTS_ALL_TTL = Duration.ofMinutes(5);

    /**
     * Build and register a {@link CacheManager} backed by Redis.
     *
     * <p>The method accepts a {@link RedisConnectionFactory} which Spring Boot
     * auto-configures from the {@code spring.data.redis.*} properties defined in
     * {@code application.yml}.
     *
     * @param connectionFactory the auto-configured Redis connection factory
     * @return a fully configured {@link RedisCacheManager}
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // ── Custom ObjectMapper for Redis serialisation ────────────────────────────
        // GenericJackson2JsonRedisSerializer uses its own internal ObjectMapper by
        // default. That mapper does NOT have the JavaTimeModule registered, so any
        // java.time type (e.g. Instant, LocalDateTime) causes a serialisation error.
        // We create a dedicated ObjectMapper with:
        //   1. JavaTimeModule  – support for java.time types
        //   2. WRITE_DATES_AS_TIMESTAMPS disabled – Instant stored as ISO-8601 string
        //   3. activateDefaultTyping  – includes the "@class" field so that Jackson
        //      can deserialise the cached JSON back to the correct concrete type
        //      without extra configuration on every DTO.
        ObjectMapper redisObjectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(
                        LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL
                );

        // Wrap the custom mapper in the Redis serialiser
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        // ── Default cache configuration ───────────────────────────────────────────
        // Applied to any cache name that does not have a specific override below.
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                // Use JSON for values – makes cache entries inspectable in Redis CLI
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                jsonSerializer))
                // Use plain strings for keys – produces readable keys like "products::1"
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                // Do not cache null values (avoids masking "not found" situations)
                .disableCachingNullValues()
                // Default TTL for any cache without a specific override
                .entryTtl(PRODUCTS_TTL);

        // ── Per-cache overrides ───────────────────────────────────────────────────
        // Each entry customises an individual cache (different TTL here).
        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
                // Single product lookups – 10-minute TTL
                "products",     defaultConfig.entryTtl(PRODUCTS_TTL),
                // Full product list – 5-minute TTL (shorter because writes invalidate it)
                "products-all", defaultConfig.entryTtl(PRODUCTS_ALL_TTL)
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
