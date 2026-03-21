package com.example.eurekadiscoveryclient.service;

import com.example.eurekadiscoveryclient.model.RegistrationStatus;
import com.example.eurekadiscoveryclient.model.ServiceInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Domain service that encapsulates pure business logic for querying
 * and summarising discovered service information.
 *
 * <p>This service contains no Spring Cloud or Eureka-specific types.
 * It works exclusively with the clean domain model ({@link ServiceInfo},
 * {@link RegistrationStatus}) making every method trivially unit-testable
 * without any Spring application context.
 *
 * <p><b>Design rationale — separation of concerns:</b>
 * <ul>
 *   <li>The <em>infrastructure layer</em> (the controller) reads raw service
 *       data from Spring Cloud's {@code DiscoveryClient} and maps it to
 *       {@link ServiceInfo} objects.</li>
 *   <li>The <em>domain layer</em> (this service) applies business rules to
 *       those plain objects: filtering, grouping, counting.</li>
 *   <li>This separation keeps the domain logic portable and independently
 *       testable.</li>
 * </ul>
 */
@Service
public class DiscoveryQueryService {

    /**
     * Filters a list of service instances, retaining only those belonging
     * to the given service ID.
     *
     * <p>The comparison is case-insensitive because Eureka normalises service
     * IDs to upper-case on registration, but callers may query with any case.
     *
     * @param services  all known service instances (must not be null)
     * @param serviceId the service ID to look up (e.g. "order-service")
     * @return instances whose {@link ServiceInfo#serviceId()} matches
     *         {@code serviceId} (case-insensitive); empty list if none found
     */
    public List<ServiceInfo> findByServiceId(List<ServiceInfo> services, String serviceId) {
        // Guard against null or blank service IDs — return empty instead of throwing
        if (serviceId == null || serviceId.isBlank()) {
            return List.of();
        }
        return services.stream()
                .filter(s -> s.serviceId().equalsIgnoreCase(serviceId))
                .toList();
    }

    /**
     * Counts the number of distinct logical service IDs in the given list.
     *
     * <p>The count is case-insensitive: "ORDER-SERVICE" and "order-service"
     * are treated as the same logical service (Eureka normalises to upper-case).
     *
     * @param services list of discovered service instances (must not be null)
     * @return the number of distinct service IDs
     */
    public int countDistinctServices(List<ServiceInfo> services) {
        return (int) services.stream()
                .map(s -> s.serviceId().toUpperCase())
                .distinct()
                .count();
    }

    /**
     * Builds a {@link RegistrationStatus} summarising the client's configuration
     * and the number of services it has discovered.
     *
     * <p>This method is called by the controller to produce the response for
     * {@code GET /client/status}, giving operators a quick health check of the
     * registration and discovery setup.
     *
     * @param applicationName     the {@code spring.application.name} value
     * @param discoveredServices  number of distinct service IDs found in the registry
     * @param registrationEnabled whether {@code eureka.client.register-with-eureka} is true
     * @param fetchEnabled        whether {@code eureka.client.fetch-registry} is true
     * @return a non-null {@link RegistrationStatus} summary
     */
    public RegistrationStatus buildStatus(String applicationName,
                                          int discoveredServices,
                                          boolean registrationEnabled,
                                          boolean fetchEnabled) {
        return new RegistrationStatus(
                applicationName,
                discoveredServices,
                registrationEnabled,
                fetchEnabled
        );
    }

    /**
     * Groups the given service instances by their service ID (upper-cased).
     *
     * <p>Returns a flat list of {@link ServiceInfo} objects sorted by service ID
     * so that instances of the same logical service appear together. Sorting is
     * done alphabetically by service ID for deterministic, readable output.
     *
     * @param services all discovered service instances (must not be null)
     * @return the same instances, sorted by {@link ServiceInfo#serviceId()} ascending
     */
    public List<ServiceInfo> sortByServiceId(List<ServiceInfo> services) {
        return services.stream()
                .sorted((a, b) -> a.serviceId().compareToIgnoreCase(b.serviceId()))
                .toList();
    }
}
