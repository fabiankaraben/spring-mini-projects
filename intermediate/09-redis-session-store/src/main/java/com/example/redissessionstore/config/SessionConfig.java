package com.example.redissessionstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

/**
 * Spring Session configuration for Redis-backed HTTP sessions.
 *
 * <p>{@link EnableRedisIndexedHttpSession} activates Spring Session with indexed support,
 * which registers a {@code SessionRepositoryFilter} that wraps each incoming
 * {@link jakarta.servlet.http.HttpServletRequest}. From that point on, any call to
 * {@code request.getSession()} returns a Spring-managed session whose data is stored
 * in Redis, not in the servlet container's in-memory store.
 *
 * <p>Key concepts demonstrated here:
 * <ul>
 *   <li><strong>maxInactiveIntervalInSeconds</strong> – controls how long Redis keeps
 *       a session alive after the last request. After the TTL expires Redis deletes the
 *       key automatically and the user is effectively logged out.</li>
 *   <li><strong>JSON serialisation</strong> – by default Spring Session serialises
 *       session attributes with Java serialisation. Registering a
 *       {@link GenericJackson2JsonRedisSerializer} makes session data human-readable
 *       when you inspect Redis with {@code redis-cli}.</li>
 *   <li><strong>Indexed sessions</strong> – the "indexed" variant stores extra Redis
 *       keys per session so you can look up sessions by principal name or session
 *       attribute, which is useful for implementing "log out all devices" features.</li>
 * </ul>
 */
@Configuration
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 1800) // 30-minute session TTL
public class SessionConfig {

    /**
     * Override Spring Session's default Java serialiser with a JSON serialiser.
     *
     * <p>Spring Session looks for a bean named exactly
     * {@code "springSessionDefaultRedisSerializer"} and uses it for all session
     * attribute values. This makes session data inspectable via:
     * <pre>{@code
     *   redis-cli keys 'spring:session:*'
     *   redis-cli hgetall spring:session:sessions:<session-id>
     * }</pre>
     *
     * @return a {@link GenericJackson2JsonRedisSerializer} used for all session attributes
     */
    @Bean("springSessionDefaultRedisSerializer")
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // GenericJackson2JsonRedisSerializer includes type information in the JSON
        // so Spring Session can reconstruct the original Java objects on deserialization.
        return new GenericJackson2JsonRedisSerializer();
    }
}
