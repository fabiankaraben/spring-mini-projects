package com.example.eurekadiscoveryserver.controller;

import com.example.eurekadiscoveryserver.model.RegistrationSummary;
import com.example.eurekadiscoveryserver.model.ServiceInstance;
import com.example.eurekadiscoveryserver.service.RegistryQueryService;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes read-only information about the Eureka registry.
 *
 * <p>While the Eureka server already provides its own REST API under {@code /eureka/*}
 * (used by Eureka clients), this controller adds a human-friendly JSON API over
 * the same underlying data — useful for dashboards, monitoring tools, and
 * integration tests.
 *
 * <p><b>Endpoint summary:</b>
 * <ul>
 *   <li>{@code GET /registry/summary}           – aggregated counts for all services.</li>
 *   <li>{@code GET /registry/instances}          – flat list of all registered instances.</li>
 *   <li>{@code GET /registry/instances/{appName}} – instances for a specific service name.</li>
 * </ul>
 *
 * <p><b>How registry data is retrieved:</b>
 * The Eureka server stores registrations in a {@link PeerAwareInstanceRegistry}.
 * We access it through {@link EurekaServerContextHolder}, which is a static holder
 * populated by the Eureka server at startup. From the registry we read all
 * {@code InstanceInfo} objects and map them to our {@link ServiceInstance} model.
 */
@RestController
@RequestMapping("/registry")
public class RegistryController {

    /**
     * The domain service that contains the business logic for querying
     * and summarising registry data. Injected by Spring's constructor injection.
     */
    private final RegistryQueryService registryQueryService;

    /**
     * Constructs the controller with its required dependency.
     *
     * @param registryQueryService service containing domain logic for registry queries
     */
    public RegistryController(RegistryQueryService registryQueryService) {
        this.registryQueryService = registryQueryService;
    }

    /**
     * Returns a summary of the current registry state.
     *
     * <p>The summary includes the total number of applications (logical service names),
     * total instances, healthy instances and unhealthy instances.
     *
     * <p>Example response:
     * <pre>
     * {
     *   "totalApplications": 2,
     *   "totalInstances": 3,
     *   "healthyInstances": 3,
     *   "unhealthyInstances": 0
     * }
     * </pre>
     *
     * @return HTTP 200 with a {@link RegistrationSummary} JSON body
     */
    @GetMapping("/summary")
    public ResponseEntity<RegistrationSummary> getSummary() {
        List<ServiceInstance> all = getAllInstances();
        RegistrationSummary summary = registryQueryService.buildSummary(all);
        return ResponseEntity.ok(summary);
    }

    /**
     * Returns the flat list of all currently registered service instances.
     *
     * <p>Each entry corresponds to one running JVM (or Docker container) that has
     * successfully registered itself with this Eureka server.
     *
     * @return HTTP 200 with a JSON array of {@link ServiceInstance} objects
     */
    @GetMapping("/instances")
    public ResponseEntity<List<ServiceInstance>> getAllRegisteredInstances() {
        return ResponseEntity.ok(getAllInstances());
    }

    /**
     * Returns the instances registered under a specific application name.
     *
     * <p>Application names are case-insensitive: Eureka normalises them to
     * upper-case on registration, but the lookup here is case-insensitive for
     * convenience.
     *
     * @param appName the logical service name (e.g. "order-service")
     * @return HTTP 200 with matching instances; empty array if none registered
     */
    @GetMapping("/instances/{appName}")
    public ResponseEntity<List<ServiceInstance>> getInstancesByAppName(
            @PathVariable String appName) {
        List<ServiceInstance> all = getAllInstances();
        List<ServiceInstance> filtered = registryQueryService.findByAppName(all, appName);
        return ResponseEntity.ok(filtered);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Reads all registered {@code InstanceInfo} objects from the embedded Eureka
     * registry and converts each one to a {@link ServiceInstance} value object.
     *
     * <p>The Eureka server's in-memory registry is accessed through the static
     * {@link EurekaServerContextHolder} — the canonical way to read registry data
     * from within the same JVM as the Eureka server.
     *
     * @return list of all currently registered instances (empty if none)
     */
    private List<ServiceInstance> getAllInstances() {
        // Obtain the server context (populated by @EnableEurekaServer at startup)
        EurekaServerContext serverContext = EurekaServerContextHolder.getInstance()
                .getServerContext();

        // The peer-aware registry stores all registered InstanceInfo objects
        PeerAwareInstanceRegistry registry = serverContext.getRegistry();

        // getSortedApplications() returns List<Application> — one entry per distinct
        // logical service name. Each Application holds a list of InstanceInfo objects.
        List<Application> applications = registry.getSortedApplications();
        return applications.stream()
                // flatMap: each Application (logical service) has N InstanceInfo objects
                .flatMap(app -> app.getInstances().stream())
                // Map Netflix InstanceInfo → our clean domain ServiceInstance
                .map(info -> new ServiceInstance(
                        info.getAppName(),
                        info.getInstanceId(),
                        info.getHostName(),
                        info.getPort(),
                        info.getStatus().name()
                ))
                .toList();
    }
}
