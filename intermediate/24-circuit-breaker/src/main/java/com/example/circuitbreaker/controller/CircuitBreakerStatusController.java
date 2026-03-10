package com.example.circuitbreaker.controller;

import com.example.circuitbreaker.domain.CircuitBreakerStatus;
import com.example.circuitbreaker.service.CircuitBreakerMonitorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes the runtime state of all registered Resilience4j
 * circuit breakers through the application's own API.
 *
 * <p>This is complementary to Spring Boot Actuator:
 * <ul>
 *   <li>{@code /actuator/health} – overall health with circuit breaker states</li>
 *   <li>{@code /actuator/circuitbreakers} – detailed Resilience4j metrics</li>
 *   <li>{@code /api/circuit-breaker/status} – this endpoint, custom format</li>
 * </ul>
 *
 * <p>Having a custom endpoint makes it easy to demonstrate and observe the
 * circuit breaker state transition during manual testing or curl exercises.
 *
 * <p>API routes:
 * <pre>
 *   GET /api/circuit-breaker/status          – all circuit breakers
 *   GET /api/circuit-breaker/status/{name}   – single circuit breaker by name
 * </pre>
 */
@RestController
@RequestMapping("/api/circuit-breaker")
public class CircuitBreakerStatusController {

    private final CircuitBreakerMonitorService monitorService;

    /**
     * @param monitorService service that reads state from Resilience4j registry
     */
    public CircuitBreakerStatusController(CircuitBreakerMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /**
     * Return a status snapshot for every registered circuit breaker.
     *
     * <p>Useful for observing state transitions (CLOSED → OPEN → HALF_OPEN)
     * while triggering failures on the product endpoints.
     *
     * @return 200 OK with a JSON array of circuit breaker status objects
     */
    @GetMapping("/status")
    public ResponseEntity<List<CircuitBreakerStatus>> getAllStatuses() {
        return ResponseEntity.ok(monitorService.getAllStatuses());
    }

    /**
     * Return a status snapshot for a single named circuit breaker.
     *
     * <p>Use the name configured under
     * {@code resilience4j.circuitbreaker.instances.<name>} in application.yml.
     * For this project the configured name is {@code inventoryService}.
     *
     * @param name circuit breaker instance name
     * @return 200 OK with the circuit breaker status JSON
     */
    @GetMapping("/status/{name}")
    public ResponseEntity<CircuitBreakerStatus> getStatusByName(
            @PathVariable("name") String name) {
        return ResponseEntity.ok(monitorService.getStatusByName(name));
    }
}
