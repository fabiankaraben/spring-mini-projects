package com.example.circuitbreaker.domain;

/**
 * Snapshot of a single Resilience4j circuit breaker's runtime state,
 * returned by the {@code GET /api/circuit-breaker/status} endpoint.
 *
 * <p>This record is used to expose circuit breaker internals in a
 * human-readable, API-friendly format for educational purposes.
 * In production you would typically rely on Actuator + Prometheus instead.
 *
 * @param name              the circuit breaker instance name (e.g., "inventoryService")
 * @param state             current state: CLOSED, OPEN, HALF_OPEN, DISABLED, or FORCED_OPEN
 * @param failureRate       percentage of calls recorded as failures (0.0–100.0), or -1 if
 *                          not enough calls have been made yet to calculate a rate
 * @param slowCallRate      percentage of calls that exceeded the slow-call duration threshold,
 *                          or -1 if the calculation window is not yet full
 * @param bufferedCalls     total calls recorded in the current sliding window
 * @param failedCalls       number of calls recorded as failures in the current window
 * @param successfulCalls   number of calls recorded as successful in the current window
 * @param notPermittedCalls number of calls rejected because the circuit is OPEN
 */
public record CircuitBreakerStatus(
        String name,
        String state,
        float failureRate,
        float slowCallRate,
        int bufferedCalls,
        int failedCalls,
        int successfulCalls,
        long notPermittedCalls
) {
}
