package com.example.springcloudgateway.controller;

import com.example.springcloudgateway.model.GatewayStatus;
import com.example.springcloudgateway.model.RouteInfo;
import com.example.springcloudgateway.service.RouteInspectorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller that exposes management endpoints for inspecting the
 * gateway's runtime configuration.
 *
 * <p><b>Why a custom management controller?</b>
 * Spring Boot Actuator already provides {@code /actuator/gateway/routes}. This
 * controller exists for educational purposes — it demonstrates:
 * <ul>
 *   <li>How to inject and use the {@link org.springframework.cloud.gateway.route.RouteLocator}
 *       indirectly (via a service) to inspect live route data.</li>
 *   <li>How to write reactive WebFlux controllers that return {@link Mono}
 *       instead of plain objects.</li>
 *   <li>How domain records ({@link RouteInfo}, {@link GatewayStatus}) are
 *       serialised to JSON by Jackson automatically.</li>
 * </ul>
 *
 * <p><b>Endpoints:</b>
 * <pre>
 *   GET /gateway/status          — overall gateway status snapshot
 *   GET /gateway/routes          — list of all configured routes
 *   GET /gateway/routes/{routeId} — details for a single route by ID
 * </pre>
 *
 * <p><b>WebFlux controller note:</b>
 * This controller runs on Netty (not Tomcat) because Spring Cloud Gateway uses
 * WebFlux. Returning {@link Mono} or {@link reactor.core.publisher.Flux} from
 * controller methods is the reactive way to avoid blocking the event-loop
 * thread. For simple management endpoints that are called infrequently it is
 * also acceptable to return plain objects (Spring wraps them in {@link Mono}
 * automatically), which is the approach used for the routes list.
 */
@RestController
@RequestMapping("/gateway")
public class GatewayInfoController {

    /** Service that queries the live RouteLocator for route metadata. */
    private final RouteInspectorService routeInspectorService;

    /**
     * Constructor injection — preferred over {@code @Autowired} field injection
     * because it makes the dependency explicit and the class easier to test.
     *
     * @param routeInspectorService the route inspection service
     */
    public GatewayInfoController(RouteInspectorService routeInspectorService) {
        this.routeInspectorService = routeInspectorService;
    }

    // =========================================================================
    // Gateway status
    // =========================================================================

    /**
     * Returns a status snapshot of the running gateway: application name,
     * health status, timestamp, and a list of active route IDs.
     *
     * <p>Example response:
     * <pre>{@code
     * {
     *   "applicationName": "spring-cloud-gateway",
     *   "status": "UP",
     *   "timestamp": "2025-01-01T12:00:00Z",
     *   "totalRoutes": 3,
     *   "routeIds": ["product-service-route", "order-service-route", "user-service-route"]
     * }
     * }</pre>
     *
     * @return a {@link Mono} wrapping the {@link GatewayStatus}
     */
    @GetMapping("/status")
    public Mono<GatewayStatus> getStatus() {
        // Delegate to the service, which queries the RouteLocator reactively.
        return routeInspectorService.getStatus();
    }

    // =========================================================================
    // Route listing
    // =========================================================================

    /**
     * Returns a JSON array of all routes currently configured in the gateway.
     *
     * <p>Each entry includes the route ID, downstream URI, predicates, and
     * applied filters. This endpoint is useful for operators who need to verify
     * that all expected routes are present without restarting the service.
     *
     * <p>Returns a reactive {@link Flux} — Spring WebFlux serialises it to a
     * JSON array automatically. Using Flux here avoids blocking the Netty
     * event-loop thread (calling {@code block()} inside a WebFlux handler
     * throws {@link IllegalStateException}).
     *
     * @return a {@link Flux} emitting one {@link RouteInfo} per route
     */
    @GetMapping("/routes")
    public Flux<RouteInfo> getRoutes() {
        // Return the reactive stream directly — no blocking collect needed.
        return routeInspectorService.getRoutes();
    }

    // =========================================================================
    // Single route lookup
    // =========================================================================

    /**
     * Returns the details of a single route identified by its route ID.
     *
     * <p>Returns HTTP 200 with the route details if found, or HTTP 404 if no
     * route with the given ID exists.
     *
     * <p>Example:
     * <pre>
     *   GET /gateway/routes/product-service-route
     * </pre>
     *
     * @param routeId the unique route identifier (path variable)
     * @return HTTP 200 with the {@link RouteInfo}, or HTTP 404 if not found
     */
    @GetMapping("/routes/{routeId}")
    public Mono<ResponseEntity<RouteInfo>> getRouteById(@PathVariable String routeId) {
        // Filter the reactive stream by route ID, take the first match.
        // switchIfEmpty emits a 404 response when no route matches the ID.
        return routeInspectorService.getRoutes()
                .filter(route -> routeId.equals(route.id()))
                .next()
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.<RouteInfo>notFound().build()));
    }
}
