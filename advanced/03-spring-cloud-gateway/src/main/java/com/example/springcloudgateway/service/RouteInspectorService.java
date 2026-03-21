package com.example.springcloudgateway.service;

import com.example.springcloudgateway.model.GatewayStatus;
import com.example.springcloudgateway.model.RouteInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for inspecting the routes currently loaded in the
 * API Gateway and building status information about the gateway itself.
 *
 * <p><b>Design rationale:</b>
 * Controller classes should be thin — they translate HTTP requests into service
 * calls and service results into HTTP responses. Business/domain logic (here:
 * how to extract route metadata from the {@link RouteLocator}) lives in this
 * service class. This separation makes the logic independently unit-testable
 * without starting a web server.
 *
 * <p><b>Reactive return types:</b>
 * {@link RouteLocator#getRoutes()} returns a {@link Flux} (a reactive stream
 * of zero or more elements). This service exposes both a reactive
 * ({@link #getRoutes()}) and a blocking-collected ({@link #getRouteList()})
 * method so callers can choose the appropriate style.
 *
 * <p>In production Spring WebFlux controllers you should prefer the reactive
 * {@link Flux}/{@link Mono} return types to avoid blocking the event loop.
 * The blocking {@code collectList().block()} version is provided here for
 * simplicity in the management controller and is acceptable for low-traffic
 * management endpoints.
 */
@Service
public class RouteInspectorService {

    /** The application name read from {@code spring.application.name}. */
    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * The {@link RouteLocator} is the central registry of all routes in
     * Spring Cloud Gateway. Injecting it lets us query the live set of
     * routes at runtime — including any dynamically added routes.
     */
    private final RouteLocator routeLocator;

    /**
     * Constructor-based injection (preferred over field injection).
     *
     * @param routeLocator the Spring Cloud Gateway route registry
     */
    public RouteInspectorService(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
    }

    /**
     * Returns a reactive stream of all configured routes, each mapped to a
     * {@link RouteInfo} summary record.
     *
     * <p>The mapping extracts:
     * <ul>
     *   <li>The route ID (unique string identifier).</li>
     *   <li>The downstream URI the route proxies to.</li>
     *   <li>Predicate descriptions — each predicate is converted to a string
     *       via {@code toString()} which produces a human-readable form like
     *       {@code "Paths: [/api/products/**], match trailing slash: true"}.</li>
     *   <li>Filter descriptions — same approach for filter chain elements.</li>
     * </ul>
     *
     * @return a {@link Flux} emitting one {@link RouteInfo} per registered route
     */
    public Flux<RouteInfo> getRoutes() {
        return routeLocator.getRoutes()
                .map(route -> new RouteInfo(
                        route.getId(),
                        route.getUri().toString(),
                        // Convert each predicate to a human-readable string.
                        // Route predicates implement toString() to show their
                        // type and configuration (path pattern, method, etc.).
                        List.of(route.getPredicate().toString()),
                        // Convert each gateway filter to a human-readable string.
                        route.getFilters().stream()
                                .map(Object::toString)
                                .collect(Collectors.toList())
                ));
    }

    /**
     * Collects all routes into a {@link List} (blocking).
     *
     * <p>This method blocks the calling thread until all routes are collected.
     * It is acceptable for management/info endpoints that are called
     * infrequently by operators, not by client applications at high frequency.
     *
     * @return a list of {@link RouteInfo} records for all configured routes
     */
    public List<RouteInfo> getRouteList() {
        return getRoutes().collectList().block();
    }

    /**
     * Builds a {@link GatewayStatus} snapshot of the gateway's current state.
     *
     * <p>Collects route IDs from the live {@link RouteLocator} to show which
     * routes are actually loaded (not just what is in the YAML/config), then
     * wraps them with application metadata.
     *
     * @return a {@link Mono} emitting the current gateway status
     */
    public Mono<GatewayStatus> getStatus() {
        return routeLocator.getRoutes()
                .map(route -> route.getId())
                // Collect all IDs into a List, then build the status record.
                .collectList()
                .map(routeIds -> new GatewayStatus(
                        applicationName,
                        "UP",
                        Instant.now(),
                        routeIds.size(),
                        routeIds
                ));
    }
}
