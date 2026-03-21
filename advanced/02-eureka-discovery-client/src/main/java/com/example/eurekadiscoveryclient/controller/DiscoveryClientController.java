package com.example.eurekadiscoveryclient.controller;

import com.example.eurekadiscoveryclient.model.RegistrationStatus;
import com.example.eurekadiscoveryclient.model.ServiceInfo;
import com.example.eurekadiscoveryclient.service.DiscoveryQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes discovery-client information and a simple
 * product catalogue API to demonstrate that this service is alive and
 * registered in the Eureka registry.
 *
 * <p><b>Endpoint summary:</b>
 * <ul>
 *   <li>{@code GET /client/status}              – current registration status of this client.</li>
 *   <li>{@code GET /client/services}            – all services known to the local registry cache.</li>
 *   <li>{@code GET /client/services/{serviceId}} – instances of a specific service.</li>
 *   <li>{@code GET /products}                   – sample product list (business endpoint).</li>
 *   <li>{@code GET /products/{id}}              – single product by ID.</li>
 * </ul>
 *
 * <p><b>How {@link DiscoveryClient} works here:</b>
 * Spring Cloud auto-configures a {@code DiscoveryClient} bean backed by the
 * Eureka client. It provides:
 * <ul>
 *   <li>{@code getServices()} — returns all distinct service IDs in the registry.</li>
 *   <li>{@code getInstances(serviceId)} — returns all healthy instances of a service.</li>
 * </ul>
 * The registry data is fetched from the Eureka server periodically (every 30 s
 * by default) and cached locally, so these calls are in-memory lookups — they
 * do NOT reach out to the Eureka server on every request.
 */
@RestController
public class DiscoveryClientController {

    /**
     * Spring Cloud's abstraction for querying the service registry.
     * With eureka-client on the classpath, this is backed by a Eureka-specific
     * implementation. The abstraction makes it easy to switch to Consul or
     * Zookeeper without changing this code.
     */
    private final DiscoveryClient discoveryClient;

    /**
     * Domain service containing business logic for filtering and summarising
     * discovered service information. Injected by Spring's constructor injection.
     */
    private final DiscoveryQueryService discoveryQueryService;

    /**
     * The logical name of this application in the Eureka registry.
     * Read from {@code spring.application.name} in application.yml.
     */
    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * Whether this instance registers itself with Eureka.
     * Read from {@code eureka.client.register-with-eureka} in application.yml.
     */
    @Value("${eureka.client.register-with-eureka:true}")
    private boolean registrationEnabled;

    /**
     * Whether this instance fetches the registry from Eureka.
     * Read from {@code eureka.client.fetch-registry} in application.yml.
     */
    @Value("${eureka.client.fetch-registry:true}")
    private boolean fetchEnabled;

    /**
     * Constructs the controller with its required collaborators.
     *
     * @param discoveryClient      Spring Cloud's registry query abstraction
     * @param discoveryQueryService domain service for filtering/summarising services
     */
    public DiscoveryClientController(DiscoveryClient discoveryClient,
                                     DiscoveryQueryService discoveryQueryService) {
        this.discoveryClient = discoveryClient;
        this.discoveryQueryService = discoveryQueryService;
    }

    // =========================================================================
    // Discovery / registry endpoints
    // =========================================================================

    /**
     * Returns the current Eureka registration status of this client instance.
     *
     * <p>Shows:
     * <ul>
     *   <li>The application name as registered in Eureka.</li>
     *   <li>How many distinct services are in the local registry cache.</li>
     *   <li>Whether registration and registry-fetching are enabled.</li>
     * </ul>
     *
     * <p>Example response:
     * <pre>
     * {
     *   "applicationName": "product-service",
     *   "registeredServices": 2,
     *   "registrationEnabled": true,
     *   "fetchEnabled": true
     * }
     * </pre>
     *
     * @return HTTP 200 with a {@link RegistrationStatus} JSON body
     */
    @GetMapping("/client/status")
    public ResponseEntity<RegistrationStatus> getRegistrationStatus() {
        // Count distinct service IDs known to the local registry cache
        int serviceCount = discoveryQueryService.countDistinctServices(getAllServiceInfos());

        RegistrationStatus status = discoveryQueryService.buildStatus(
                applicationName,
                serviceCount,
                registrationEnabled,
                fetchEnabled
        );
        return ResponseEntity.ok(status);
    }

    /**
     * Returns all service instances currently in the local Eureka registry cache,
     * sorted alphabetically by service ID.
     *
     * <p>The registry cache is refreshed from the Eureka server every 30 seconds
     * by default. This endpoint shows what this client currently knows about the
     * cluster — it does not make a live request to the Eureka server.
     *
     * @return HTTP 200 with a JSON array of {@link ServiceInfo} objects
     */
    @GetMapping("/client/services")
    public ResponseEntity<List<ServiceInfo>> getAllServices() {
        List<ServiceInfo> sorted = discoveryQueryService.sortByServiceId(getAllServiceInfos());
        return ResponseEntity.ok(sorted);
    }

    /**
     * Returns all instances registered under the given service ID.
     *
     * <p>Service IDs are case-insensitive. In a production cluster with multiple
     * instances of the same service running, this endpoint would return all of them
     * — a client-side load balancer (e.g. Spring Cloud LoadBalancer) would then
     * pick one to call.
     *
     * @param serviceId the logical service name (e.g. "product-service")
     * @return HTTP 200 with matching instances; empty array if none found
     */
    @GetMapping("/client/services/{serviceId}")
    public ResponseEntity<List<ServiceInfo>> getServiceInstances(
            @PathVariable String serviceId) {
        List<ServiceInfo> all = getAllServiceInfos();
        List<ServiceInfo> filtered = discoveryQueryService.findByServiceId(all, serviceId);
        return ResponseEntity.ok(filtered);
    }

    // =========================================================================
    // Business endpoints (product catalogue)
    // =========================================================================

    /**
     * Returns a static list of sample products.
     *
     * <p>This endpoint exists to demonstrate that this service is a real
     * microservice (not just a Eureka scaffolding). In a production system it
     * would query a database; here it returns hard-coded data for simplicity.
     *
     * <p>Example response:
     * <pre>
     * [
     *   {"id": 1, "name": "Laptop",  "price": 999.99},
     *   {"id": 2, "name": "Mouse",   "price": 29.99},
     *   {"id": 3, "name": "Monitor", "price": 349.99}
     * ]
     * </pre>
     *
     * @return HTTP 200 with a JSON array of product objects
     */
    @GetMapping("/products")
    public ResponseEntity<List<Product>> getProducts() {
        return ResponseEntity.ok(SAMPLE_PRODUCTS);
    }

    /**
     * Returns a single product by its numeric ID.
     *
     * <p>Returns HTTP 404 if no product with the given ID exists.
     *
     * @param id the product ID (1-based)
     * @return HTTP 200 with the matching product, or HTTP 404 if not found
     */
    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable int id) {
        return SAMPLE_PRODUCTS.stream()
                .filter(p -> p.id() == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Reads all service instances from the local Eureka registry cache and
     * maps them to {@link ServiceInfo} domain objects.
     *
     * <p>The {@link DiscoveryClient} gives us:
     * <ol>
     *   <li>{@code getServices()} — list of all service IDs (logical names).</li>
     *   <li>{@code getInstances(id)} — list of {@link ServiceInstance} for each ID.</li>
     * </ol>
     * We flat-map the two calls to produce a single list of all instances.
     *
     * @return list of all known service instances as {@link ServiceInfo} domain objects
     */
    private List<ServiceInfo> getAllServiceInfos() {
        return discoveryClient.getServices().stream()
                // For each service ID, get all its instances
                .flatMap(serviceId -> discoveryClient.getInstances(serviceId).stream())
                // Map Spring Cloud ServiceInstance → our domain ServiceInfo
                .map(instance -> new ServiceInfo(
                        instance.getServiceId(),
                        instance.getInstanceId(),
                        instance.getHost(),
                        instance.getPort(),
                        instance.getUri().toString()
                ))
                .toList();
    }

    // =========================================================================
    // Inner record: Product (simple domain object for the business endpoints)
    // =========================================================================

    /**
     * Represents a product in the sample catalogue.
     *
     * <p>Defined as an inner record for simplicity — in a larger application
     * this would be a top-level class in the {@code model} package, backed by
     * a database entity.
     *
     * @param id    unique product identifier
     * @param name  display name of the product
     * @param price price in USD
     */
    public record Product(int id, String name, double price) {}

    /**
     * Hard-coded product catalogue used by the business endpoints.
     * Simulates a real product database for demonstration purposes.
     */
    private static final List<Product> SAMPLE_PRODUCTS = List.of(
            new Product(1, "Laptop",       999.99),
            new Product(2, "Mouse",         29.99),
            new Product(3, "Monitor",      349.99),
            new Product(4, "Keyboard",      79.99),
            new Product(5, "USB-C Hub",     49.99)
    );
}
