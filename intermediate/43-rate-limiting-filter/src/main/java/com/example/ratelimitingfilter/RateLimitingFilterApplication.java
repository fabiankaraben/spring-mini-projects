package com.example.ratelimitingfilter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Rate Limiting Filter mini-project.
 *
 * <p>This application demonstrates a token-bucket rate limiter implemented as a
 * Spring {@link org.springframework.web.servlet.HandlerInterceptor}.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>The token-bucket state is stored in Redis, making the limiter suitable
 *       for horizontally-scaled deployments (multiple app instances share the
 *       same counters).</li>
 *   <li>The client is identified by its IP address (extracted from the request).
 *       The identifier strategy can be swapped to use an API key or JWT subject
 *       without changing any other code.</li>
 *   <li>Rate-limit parameters (capacity and refill period) are externalised in
 *       {@code application.yml} and bound to {@link com.example.ratelimitingfilter.config.RateLimitProperties}.</li>
 * </ul>
 */
@SpringBootApplication
public class RateLimitingFilterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimitingFilterApplication.class, args);
    }
}
