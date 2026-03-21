package com.example.eurekadiscoveryclient.model;

/**
 * Immutable value object representing the current Eureka registration status
 * of this service instance.
 *
 * <p>This is returned by the {@code /client/status} endpoint to give operators
 * and integration tests a quick, structured view of whether this service has
 * successfully registered itself with the Eureka server.
 *
 * <p><b>Fields explained:</b>
 * <ul>
 *   <li>{@code applicationName} – the value of {@code spring.application.name},
 *       which becomes the logical service name in the Eureka registry.</li>
 *   <li>{@code registeredServices} – the number of distinct service IDs currently
 *       known to the local Eureka registry cache (including this service itself
 *       if registration is enabled).</li>
 *   <li>{@code registrationEnabled} – whether this instance is configured to
 *       register itself with Eureka ({@code eureka.client.register-with-eureka}).</li>
 *   <li>{@code fetchEnabled} – whether this instance fetches the registry from
 *       Eureka ({@code eureka.client.fetch-registry}).</li>
 * </ul>
 *
 * @param applicationName    the Spring application name of this service
 * @param registeredServices number of services found in the local Eureka registry cache
 * @param registrationEnabled whether Eureka registration is enabled for this instance
 * @param fetchEnabled        whether Eureka registry fetching is enabled
 */
public record RegistrationStatus(
        String applicationName,
        int registeredServices,
        boolean registrationEnabled,
        boolean fetchEnabled
) {

    /**
     * Returns {@code true} when this instance is actively participating in the
     * Eureka ecosystem (both registration and registry fetching are on).
     *
     * <p>A fully active client registers itself (so other services can discover it)
     * AND fetches the registry (so it can discover other services).
     *
     * @return {@code true} if both {@code registrationEnabled} and {@code fetchEnabled}
     *         are {@code true}
     */
    public boolean isFullyActive() {
        return registrationEnabled && fetchEnabled;
    }

    /**
     * Returns {@code true} if the local registry cache contains at least one service.
     *
     * <p>When the Eureka server is reachable and this client has successfully fetched
     * the registry, {@code registeredServices} will be at least 1 (this service itself,
     * if self-registration is enabled).
     *
     * @return {@code true} when at least one service is known to the local cache
     */
    public boolean hasDiscoveredServices() {
        return registeredServices > 0;
    }
}
