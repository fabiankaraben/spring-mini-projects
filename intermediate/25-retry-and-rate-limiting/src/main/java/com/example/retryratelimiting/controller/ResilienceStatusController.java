package com.example.retryratelimiting.controller;

import com.example.retryratelimiting.domain.ResilienceStatus;
import com.example.retryratelimiting.service.ResilienceMonitorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing Resilience4j Retry and RateLimiter instance metrics.
 *
 * <p>These endpoints provide real-time observability into the fault-tolerance
 * patterns applied to the weather service. They are useful for:
 * <ul>
 *   <li>Verifying that Resilience4j instances are correctly configured.</li>
 *   <li>Monitoring available rate limiter permits to detect saturation.</li>
 *   <li>Tracking retry attempt counts over time.</li>
 * </ul>
 *
 * <p>API routes:
 * <pre>
 *   GET /api/resilience/status                      – all instances (Retry + RateLimiter)
 *   GET /api/resilience/status/retry/{name}         – a single Retry instance by name
 *   GET /api/resilience/status/rate-limiter/{name}  – a single RateLimiter instance by name
 * </pre>
 *
 * <p>For deeper Micrometer-based metrics, also see the Actuator endpoints:
 * <ul>
 *   <li>{@code GET /actuator/retryevents}   – per-event log</li>
 *   <li>{@code GET /actuator/ratelimiters}  – rate limiter names and config</li>
 *   <li>{@code GET /actuator/prometheus}    – Prometheus scrape target</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/resilience/status")
public class ResilienceStatusController {

    private final ResilienceMonitorService monitorService;

    /**
     * Constructor injection ensures the dependency is explicit and testable.
     *
     * @param monitorService service that queries Resilience4j registries
     */
    public ResilienceStatusController(ResilienceMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /**
     * Return a combined status snapshot for all registered Retry and RateLimiter instances.
     *
     * @return 200 OK with a JSON array of {@link ResilienceStatus} records
     */
    @GetMapping
    public ResponseEntity<List<ResilienceStatus>> getAllStatuses() {
        return ResponseEntity.ok(monitorService.getAllStatuses());
    }

    /**
     * Return a status snapshot for a single named Retry instance.
     *
     * @param name the Retry instance name (must match a key in application.yml)
     * @return 200 OK with the {@link ResilienceStatus} record for the Retry instance
     */
    @GetMapping("/retry/{name}")
    public ResponseEntity<ResilienceStatus> getRetryStatus(
            @PathVariable("name") String name) {
        return ResponseEntity.ok(monitorService.getRetryStatus(name));
    }

    /**
     * Return a status snapshot for a single named RateLimiter instance.
     *
     * @param name the RateLimiter instance name (must match a key in application.yml)
     * @return 200 OK with the {@link ResilienceStatus} record for the RateLimiter instance
     */
    @GetMapping("/rate-limiter/{name}")
    public ResponseEntity<ResilienceStatus> getRateLimiterStatus(
            @PathVariable("name") String name) {
        return ResponseEntity.ok(monitorService.getRateLimiterStatus(name));
    }
}
