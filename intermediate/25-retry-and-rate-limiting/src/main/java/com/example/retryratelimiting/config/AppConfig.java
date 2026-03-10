package com.example.retryratelimiting.config;

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
     *       "host unreachable" errors quickly so the Retry can react fast.</li>
     *   <li><em>Read timeout</em> (5 s) – how long to wait for data after the
     *       connection is open. A {@link java.net.SocketTimeoutException} thrown
     *       here is treated as a transient error and will trigger the Retry logic.</li>
     * </ul>
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
