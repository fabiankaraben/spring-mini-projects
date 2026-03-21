package com.example.eurekadiscoveryclient.model;

/**
 * Immutable value object representing a snapshot of a discovered service instance.
 *
 * <p>This model is used by the domain layer to represent information about services
 * retrieved from the Eureka registry via Spring Cloud's {@code DiscoveryClient}.
 * It is intentionally decoupled from Netflix Eureka API types so that the domain
 * logic is independently testable without any Spring or Eureka context.
 *
 * <p><b>Fields explained:</b>
 * <ul>
 *   <li>{@code serviceId}  – the logical service name as registered with Eureka
 *       (e.g. "ORDER-SERVICE"). Eureka normalises names to upper-case.</li>
 *   <li>{@code instanceId} – a unique string identifying this specific JVM
 *       instance, typically {@code hostname:serviceId:port}.</li>
 *   <li>{@code host}       – the hostname or IP address the instance is
 *       reachable at from other services.</li>
 *   <li>{@code port}       – the port on which the service listens for HTTP traffic.</li>
 *   <li>{@code uri}        – the full base URI ({@code http://host:port}) that
 *       callers use to send requests to this instance.</li>
 * </ul>
 *
 * <p>Using a Java record gives us immutability, auto-generated
 * {@code equals}, {@code hashCode} and {@code toString} for free.
 *
 * @param serviceId  logical application name (upper-cased by Eureka)
 * @param instanceId unique identifier for this specific instance
 * @param host       hostname or IP reachable by other services
 * @param port       HTTP port the service listens on
 * @param uri        full base URI, e.g. {@code http://host:8080}
 */
public record ServiceInfo(
        String serviceId,
        String instanceId,
        String host,
        int port,
        String uri
) {

    /**
     * Convenience factory that constructs a {@code ServiceInfo} by deriving
     * the URI from the host and port.
     *
     * @param serviceId  logical service name
     * @param instanceId unique instance identifier
     * @param host       reachable hostname
     * @param port       listening port
     * @return a new {@link ServiceInfo} with {@code uri} = {@code http://host:port}
     */
    public static ServiceInfo of(String serviceId, String instanceId,
                                  String host, int port) {
        return new ServiceInfo(serviceId, instanceId, host, port,
                "http://" + host + ":" + port);
    }

    /**
     * Returns a human-readable label combining the service ID and instance ID.
     *
     * <p>Useful for logging and dashboard display, e.g.:
     * {@code "ORDER-SERVICE (order-host:ORDER-SERVICE:8081)"}.
     *
     * @return display label string
     */
    public String displayLabel() {
        return serviceId + " (" + instanceId + ")";
    }
}
