package com.example.eurekadiscoveryserver.model;

/**
 * Immutable value object that summarises the current state of the Eureka registry.
 *
 * <p>This is a lightweight projection computed by the domain service and returned
 * to callers (e.g. the REST controller). It deliberately avoids Eureka-internal
 * types so that the domain logic layer remains testable without a running server.
 *
 * <p><b>Fields explained:</b>
 * <ul>
 *   <li>{@code totalApplications} – number of distinct logical services currently
 *       registered (each service may have multiple instances).</li>
 *   <li>{@code totalInstances}    – total count of individual JVM instances across
 *       all registered services.</li>
 *   <li>{@code healthyInstances}  – instances whose status is "UP" and are eligible
 *       to receive traffic.</li>
 *   <li>{@code unhealthyInstances} – instances in any non-"UP" state ("DOWN",
 *       "STARTING", "OUT_OF_SERVICE", "UNKNOWN").</li>
 * </ul>
 *
 * @param totalApplications number of distinct registered service names
 * @param totalInstances    total JVM instances across all services
 * @param healthyInstances  instances with status "UP"
 * @param unhealthyInstances instances with any status other than "UP"
 */
public record RegistrationSummary(
        int totalApplications,
        int totalInstances,
        int healthyInstances,
        int unhealthyInstances
) {

    /**
     * Returns {@code true} when every registered instance is healthy ("UP").
     *
     * <p>A registry is considered fully healthy when {@code unhealthyInstances == 0}
     * and at least one application is registered.
     *
     * @return {@code true} if all instances are UP and at least one is registered
     */
    public boolean isFullyHealthy() {
        return totalInstances > 0 && unhealthyInstances == 0;
    }

    /**
     * Computes the fraction of healthy instances as a value in [0.0, 1.0].
     *
     * <p>Returns {@code 0.0} when there are no instances (avoids division by zero).
     *
     * @return ratio of healthy instances to total instances
     */
    public double healthRatio() {
        if (totalInstances == 0) {
            return 0.0;
        }
        return (double) healthyInstances / totalInstances;
    }
}
