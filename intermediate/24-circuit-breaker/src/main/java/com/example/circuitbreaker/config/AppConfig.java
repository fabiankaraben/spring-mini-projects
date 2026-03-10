package com.example.circuitbreaker.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Application-level bean configuration.
 *
 * <p>Defines infrastructure beans that are shared across the application.
 * Separating bean definitions into a {@link Configuration} class keeps the
 * main application class clean and makes the wiring explicit and testable.
 */
@Configuration
public class AppConfig {

    /**
     * Create a {@link RestTemplate} bean pre-configured with sensible timeouts.
     *
     * <p>{@link RestTemplateBuilder} is auto-configured by Spring Boot and
     * applies message converters, error handlers, and interceptors automatically.
     *
     * <p><strong>Timeout explanation:</strong>
     * <ul>
     *   <li><em>Connect timeout</em> (3 s) – how long to wait for the TCP connection
     *       to the upstream server to be established. A short timeout surfaces
     *       "host unreachable" errors quickly so the circuit breaker can count them.</li>
     *   <li><em>Read timeout</em> (5 s) – how long to wait for data after the
     *       connection is open. Combined with Resilience4j's slow-call threshold
     *       (configured in application.yml), this determines when a slow response
     *       is treated as a failure.</li>
     * </ul>
     *
     * <p>Both timeouts feed into the circuit breaker's failure/slow-call rate
     * calculations: a {@link java.net.SocketTimeoutException} thrown here is
     * counted as a failure in the sliding window.
     *
     * @param builder Spring Boot's auto-configured builder
     * @return a fully configured {@link RestTemplate} bean
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                // Maximum time to wait for TCP connection establishment
                .connectTimeout(Duration.ofSeconds(3))
                // Maximum time to wait for the response body after the connection is open
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }
}
