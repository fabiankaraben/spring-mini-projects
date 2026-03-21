package com.example.eurekadiscoveryserver.service;

import com.example.eurekadiscoveryserver.model.RegistrationSummary;
import com.example.eurekadiscoveryserver.model.ServiceInstance;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Domain service that answers questions about the current state of the registry.
 *
 * <p>This service contains the pure domain logic for:
 * <ul>
 *   <li>Filtering service instances by health status.</li>
 *   <li>Building a {@link RegistrationSummary} from a flat list of instances.</li>
 *   <li>Looking up instances for a specific application name.</li>
 * </ul>
 *
 * <p><b>Design choice — no Eureka types here:</b>
 * The methods accept plain {@link ServiceInstance} value objects, not Netflix
 * Eureka API types. This keeps the domain logic decoupled from the infrastructure
 * and makes it trivially unit-testable without any Spring context.
 *
 * <p>The actual Eureka registry data is read by the controller layer through the
 * Eureka server's own APIs and mapped to {@link ServiceInstance} before being
 * passed into this service.
 */
@Service
public class RegistryQueryService {

    /**
     * Filters a list of instances, retaining only those whose status is "UP".
     *
     * <p>This mirrors the client-side behaviour in a real microservices system:
     * a load balancer would only route traffic to instances that are healthy.
     *
     * @param instances all instances to filter (may be empty; must not be null)
     * @return a new list containing only instances where {@link ServiceInstance#isHealthy()} is true
     */
    public List<ServiceInstance> filterHealthy(List<ServiceInstance> instances) {
        return instances.stream()
                .filter(ServiceInstance::isHealthy)
                .toList();
    }

    /**
     * Filters a list of instances, retaining only those whose status is NOT "UP".
     *
     * <p>Useful for alerting or dashboard displays that highlight problem instances.
     *
     * @param instances all instances to filter (may be empty; must not be null)
     * @return a new list containing only instances where {@link ServiceInstance#isHealthy()} is false
     */
    public List<ServiceInstance> filterUnhealthy(List<ServiceInstance> instances) {
        return instances.stream()
                .filter(i -> !i.isHealthy())
                .toList();
    }

    /**
     * Filters instances to those belonging to a specific application name.
     *
     * <p>The comparison is case-insensitive because Eureka normalises app names to
     * upper-case on registration but clients may query with mixed case.
     *
     * @param instances list of all instances (must not be null)
     * @param appName   the application name to look up (e.g. "order-service")
     * @return instances whose {@link ServiceInstance#appName()} matches {@code appName}
     *         (case-insensitive); empty list if none found
     */
    public List<ServiceInstance> findByAppName(List<ServiceInstance> instances, String appName) {
        if (appName == null || appName.isBlank()) {
            return List.of();
        }
        return instances.stream()
                .filter(i -> i.appName().equalsIgnoreCase(appName))
                .toList();
    }

    /**
     * Computes a {@link RegistrationSummary} from a flat list of service instances.
     *
     * <p>The summary aggregates:
     * <ul>
     *   <li>The number of distinct application names (logical services).</li>
     *   <li>The total instance count.</li>
     *   <li>Healthy vs unhealthy instance counts.</li>
     * </ul>
     *
     * @param instances all currently registered instances (must not be null)
     * @return a non-null summary with aggregated counts
     */
    public RegistrationSummary buildSummary(List<ServiceInstance> instances) {
        // Count distinct logical service names (Eureka groups instances by app name)
        int totalApplications = (int) instances.stream()
                .map(ServiceInstance::appName)
                .distinct()
                .count();

        int totalInstances = instances.size();

        // Healthy = status is "UP"; unhealthy = everything else
        int healthyInstances = (int) instances.stream()
                .filter(ServiceInstance::isHealthy)
                .count();

        int unhealthyInstances = totalInstances - healthyInstances;

        return new RegistrationSummary(
                totalApplications,
                totalInstances,
                healthyInstances,
                unhealthyInstances
        );
    }
}
