package com.example.gatewaylimiting.controller;

import com.example.gatewaylimiting.model.GatewayStatus;
import com.example.gatewaylimiting.model.RateLimitInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * REST controller that exposes informational endpoints about the gateway's
 * configuration, including its rate limiting rules.
 *
 * <p><b>Why a separate info controller?</b>
 * The Spring Boot Actuator exposes technical gateway metadata under
 * {@code /actuator/gateway/routes}, but that endpoint is primarily for ops tooling.
 * This controller provides a simpler, domain-oriented API for inspecting the
 * gateway's rate limiting policy — useful for documentation, dashboards, or
 * clients that want to know their allowed request rates before hitting the limits.
 *
 * <p><b>Reactive return types:</b>
 * All handler methods return {@code Mono<T>} or {@code Flux<T>} because Spring
 * Cloud Gateway runs on Spring WebFlux (Netty). Returning reactive types ensures
 * these handlers are non-blocking and compatible with the reactive server model.
 * Never call {@code .block()} inside a WebFlux handler — it would deadlock the
 * Netty event loop.
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>{@code GET /gateway/status} — gateway operational status and summary.</li>
 *   <li>{@code GET /gateway/rate-limits} — list of all route rate limit configs.</li>
 * </ul>
 */
@RestController
@RequestMapping("/gateway")
public class GatewayInfoController {

    /**
     * The application name injected from {@code spring.application.name}.
     * Used in the status response.
     */
    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * Returns the gateway's operational status and a summary of its configuration.
     *
     * <p>This endpoint always returns HTTP 200 with status "UP" when the gateway
     * is running. It provides a quick human-readable summary that includes the
     * number of configured routes and whether rate limiting is active.
     *
     * <p><b>Example response:</b>
     * <pre>
     * {
     *   "applicationName": "gateway-rate-limiting",
     *   "status": "UP",
     *   "totalRoutes": 3,
     *   "rateLimitingEnabled": true,
     *   "keyResolverStrategy": "IP Address"
     * }
     * </pre>
     *
     * @return a {@link Mono} emitting the gateway status
     */
    @GetMapping("/status")
    public Mono<GatewayStatus> getStatus() {
        return Mono.just(new GatewayStatus(
                applicationName,
                "UP",
                3,           // product-route, order-route, user-route
                true,        // Redis rate limiting is always enabled
                "IP Address" // primary key resolver is ipKeyResolver
        ));
    }

    /**
     * Returns the rate limit configuration for all registered routes.
     *
     * <p>This endpoint lists all routes along with their Token Bucket parameters
     * (replenishRate, burstCapacity) and the key resolver strategy. Clients can
     * use this information to understand their allowed request rates.
     *
     * <p><b>Example response:</b>
     * <pre>
     * [
     *   {
     *     "routeId": "product-route",
     *     "path": "/api/products/**",
     *     "replenishRate": 5,
     *     "burstCapacity": 10,
     *     "keyResolverType": "IP Address"
     *   },
     *   ...
     * ]
     * </pre>
     *
     * @return a {@link Flux} emitting one {@link RateLimitInfo} per route
     */
    @GetMapping("/rate-limits")
    public Flux<RateLimitInfo> getRateLimits() {
        // Build the static list of rate limit configurations.
        // These values mirror exactly what is configured in GatewayRoutesConfig.
        List<RateLimitInfo> limits = List.of(
                new RateLimitInfo("product-route", "/api/products/**", 5, 10, "IP Address"),
                new RateLimitInfo("order-route",   "/api/orders/**",   3,  5, "IP Address"),
                new RateLimitInfo("user-route",    "/api/users/**",   10, 20, "IP Address")
        );
        // Flux.fromIterable converts the list into a reactive stream.
        // Spring WebFlux serialises the resulting stream into a JSON array.
        return Flux.fromIterable(limits);
    }
}
