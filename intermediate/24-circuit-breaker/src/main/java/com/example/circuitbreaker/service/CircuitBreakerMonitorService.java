package com.example.circuitbreaker.service;

import com.example.circuitbreaker.domain.CircuitBreakerStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Service that reads runtime state from the Resilience4j {@link CircuitBreakerRegistry}
 * and converts it into {@link CircuitBreakerStatus} snapshots for the REST API.
 *
 * <p>Resilience4j exposes its state through two mechanisms:
 * <ol>
 *   <li><strong>Spring Boot Actuator</strong> – {@code /actuator/health} and
 *       {@code /actuator/circuitbreakers} (configured in application.yml).</li>
 *   <li><strong>Programmatic API</strong> – the {@link CircuitBreakerRegistry} bean
 *       provides access to every registered instance and its live {@code Metrics}.</li>
 * </ol>
 *
 * <p>This service uses the programmatic API so the application can expose circuit
 * breaker status through its own REST endpoint, which is useful for demonstrations
 * and educational purposes.
 */
@Service
public class CircuitBreakerMonitorService {

    /**
     * The Resilience4j registry that holds all circuit breaker instances.
     * Spring Boot auto-configures and populates this bean from application.yml.
     */
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * @param circuitBreakerRegistry auto-configured Resilience4j registry bean
     */
    public CircuitBreakerMonitorService(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    /**
     * Return a status snapshot for every circuit breaker registered in the application.
     *
     * <p>Each snapshot captures: name, current state, failure rate, slow-call rate,
     * buffered call count, failed call count, successful call count, and the number
     * of calls rejected because the circuit is OPEN.
     *
     * @return list of {@link CircuitBreakerStatus} records (one per registered breaker)
     */
    public List<CircuitBreakerStatus> getAllStatuses() {
        // getAllCircuitBreakers() returns an immutable set; stream it into a list
        return StreamSupport
                .stream(circuitBreakerRegistry.getAllCircuitBreakers().spliterator(), false)
                .map(this::toStatus)
                .toList();
    }

    /**
     * Return a status snapshot for a single named circuit breaker.
     *
     * <p>Resilience4j creates an instance lazily on first use (or eagerly from
     * application.yml config). If the requested name does not exist in the registry,
     * an {@link io.github.resilience4j.core.registry.NoSuchEntryException} is thrown.
     *
     * @param name the circuit breaker instance name (e.g., "inventoryService")
     * @return a {@link CircuitBreakerStatus} snapshot for the named instance
     */
    public CircuitBreakerStatus getStatusByName(String name) {
        // find() returns an Optional; getOrCreate() would create a default instance
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb =
                circuitBreakerRegistry.circuitBreaker(name);
        return toStatus(cb);
    }

    /**
     * Convert a live {@link io.github.resilience4j.circuitbreaker.CircuitBreaker}
     * instance into an immutable {@link CircuitBreakerStatus} snapshot.
     *
     * @param cb the live Resilience4j circuit breaker instance
     * @return a record capturing all relevant metrics at this point in time
     */
    private CircuitBreakerStatus toStatus(
            io.github.resilience4j.circuitbreaker.CircuitBreaker cb) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker.Metrics metrics = cb.getMetrics();
        return new CircuitBreakerStatus(
                cb.getName(),
                // State is an enum; toString() gives "CLOSED", "OPEN", "HALF_OPEN", etc.
                cb.getState().toString(),
                // failureRate() returns -1.0 when the minimum number of calls has not been reached
                metrics.getFailureRate(),
                // slowCallRate() returns -1.0 when the window is not yet full
                metrics.getSlowCallRate(),
                metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfNotPermittedCalls()
        );
    }
}
