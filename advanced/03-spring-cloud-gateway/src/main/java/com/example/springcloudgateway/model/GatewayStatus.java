package com.example.springcloudgateway.model;

import java.time.Instant;
import java.util.List;

/**
 * A domain record representing the overall status of the API Gateway.
 *
 * <p>This record is serialised to JSON and returned by the
 * {@code GET /gateway/status} endpoint defined in
 * {@code GatewayInfoController}. It gives operators a quick snapshot of
 * the gateway's runtime state without having to read through the full
 * Actuator output.
 *
 * <p><b>Fields:</b>
 * <ul>
 *   <li>{@code applicationName} — the value of {@code spring.application.name}
 *       (always {@code "spring-cloud-gateway"}).</li>
 *   <li>{@code status} — a human-readable health label ({@code "UP"}).</li>
 *   <li>{@code timestamp} — the exact moment this status response was
 *       generated, expressed as an ISO-8601 instant (e.g.
 *       {@code "2025-01-01T12:00:00Z"}).</li>
 *   <li>{@code totalRoutes} — the number of routes currently loaded.</li>
 *   <li>{@code routeIds} — list of route identifiers, useful for a quick
 *       sanity check that all expected routes are present.</li>
 * </ul>
 *
 * @param applicationName the Spring application name
 * @param status          human-readable status label
 * @param timestamp       ISO-8601 timestamp of when this response was created
 * @param totalRoutes     number of active routes configured in the gateway
 * @param routeIds        list of route IDs currently active
 */
public record GatewayStatus(
        String applicationName,
        String status,
        Instant timestamp,
        int totalRoutes,
        List<String> routeIds
) {
}
