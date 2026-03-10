package com.example.circuitbreaker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Circuit Breaker mini-project.
 *
 * <p>This application demonstrates the Circuit Breaker pattern using
 * Resilience4j in a Spring Boot 3 context. It protects an outbound call to an
 * external product-inventory API: when that service becomes slow or unavailable,
 * the circuit breaker opens and a fallback method returns a safe default response
 * instead of propagating failures to callers.
 *
 * <p><strong>Key concepts demonstrated:</strong>
 * <ul>
 *   <li>{@code @CircuitBreaker} – Resilience4j annotation that wraps a method
 *       with a circuit breaker state machine (CLOSED → OPEN → HALF_OPEN).</li>
 *   <li>Fallback method – called instead of the real method when the circuit is OPEN
 *       or when the protected call throws an exception.</li>
 *   <li>{@code @Retry} – automatically re-invokes the method on transient failures
 *       before the circuit breaker counts the call as a failure.</li>
 *   <li>{@code @TimeLimiter} – cancels calls that take longer than a configured
 *       threshold, converting slow responses into failures the circuit breaker tracks.</li>
 *   <li>Spring Boot Actuator – exposes circuit breaker health and metrics at
 *       {@code /actuator/health} and {@code /actuator/circuitbreakers}.</li>
 * </ul>
 */
@SpringBootApplication
public class CircuitBreakerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CircuitBreakerApplication.class, args);
    }
}
