package com.example.gatewaylimiting.model;

/**
 * Immutable value object representing the gateway's operational status.
 *
 * <p>This model is returned by the {@code /gateway/status} endpoint, providing
 * a quick health summary of the gateway along with its rate limiting configuration
 * metadata (total routes and key resolver strategy in use).
 *
 * <p><b>Fields:</b>
 * <ul>
 *   <li>{@code applicationName} — the Spring application name from properties.</li>
 *   <li>{@code status} — a simple operational status string (always "UP" when
 *       the endpoint is reachable).</li>
 *   <li>{@code totalRoutes} — number of routes registered in the gateway.</li>
 *   <li>{@code rateLimitingEnabled} — {@code true} if rate limiting is active.</li>
 *   <li>{@code keyResolverStrategy} — the primary rate limit key resolution
 *       strategy in use (e.g. "IP Address").</li>
 * </ul>
 *
 * @param applicationName      the Spring application name
 * @param status               "UP" when the gateway is operational
 * @param totalRoutes          number of configured routes
 * @param rateLimitingEnabled  whether Redis rate limiting is active
 * @param keyResolverStrategy  the primary key resolver strategy in use
 */
public record GatewayStatus(
        String applicationName,
        String status,
        int totalRoutes,
        boolean rateLimitingEnabled,
        String keyResolverStrategy
) {
}
