package com.example.eurekadiscoveryserver.model;

/**
 * Immutable value object representing a snapshot of a registered service instance.
 *
 * <p>This model is used by the domain layer to pass service information around
 * without coupling to the Netflix Eureka API types. It is intentionally simple
 * so that unit tests can construct it without any Spring or Eureka context.
 *
 * <p><b>Fields explained:</b>
 * <ul>
 *   <li>{@code appName}  – the logical service name (e.g. "ORDER-SERVICE").
 *       Eureka normalises names to upper-case.</li>
 *   <li>{@code instanceId} – a unique string identifying this specific JVM
 *       instance, typically {@code hostname:appName:port}.</li>
 *   <li>{@code hostName}  – the hostname or IP address the instance is
 *       reachable at from other services.</li>
 *   <li>{@code port}      – the port on which the service listens.</li>
 *   <li>{@code status}    – the instance's health status as reported to Eureka.
 *       Common values: "UP", "DOWN", "STARTING", "OUT_OF_SERVICE", "UNKNOWN".</li>
 * </ul>
 *
 * <p>Using a Java record here gives us immutability, auto-generated
 * {@code equals}, {@code hashCode} and {@code toString} for free.
 *
 * @param appName    logical application name (upper-cased by Eureka)
 * @param instanceId unique identifier for this instance
 * @param hostName   hostname or IP reachable by other services
 * @param port       HTTP port the service listens on
 * @param status     health status reported to Eureka ("UP", "DOWN", etc.)
 */
public record ServiceInstance(
        String appName,
        String instanceId,
        String hostName,
        int port,
        String status
) {

    /**
     * Convenience factory method for the common "UP" state.
     *
     * @param appName    logical application name
     * @param instanceId unique instance identifier
     * @param hostName   reachable hostname
     * @param port       listening port
     * @return a new {@link ServiceInstance} with status "UP"
     */
    public static ServiceInstance up(String appName, String instanceId,
                                     String hostName, int port) {
        return new ServiceInstance(appName, instanceId, hostName, port, "UP");
    }

    /**
     * Returns {@code true} if this instance is considered healthy and
     * eligible to receive traffic.
     *
     * <p>Only instances with status {@code "UP"} are healthy; all other states
     * ("DOWN", "STARTING", "OUT_OF_SERVICE", "UNKNOWN") mean the instance should
     * not receive new requests.
     *
     * @return {@code true} when {@link #status()} equals "UP" (case-insensitive)
     */
    public boolean isHealthy() {
        return "UP".equalsIgnoreCase(status);
    }

    /**
     * Builds the base URL that client services use to call this instance.
     *
     * <p>Example: {@code http://host1:8080}
     *
     * @return HTTP base URL string
     */
    public String baseUrl() {
        return "http://" + hostName + ":" + port;
    }
}
