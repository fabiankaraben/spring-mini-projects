package com.example.springcloudgateway.config;

import com.example.springcloudgateway.filter.AddCorrelationIdGatewayFilterFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines all gateway routes using the Spring Cloud Gateway Java DSL.
 *
 * <p><b>Two ways to configure routes in Spring Cloud Gateway:</b>
 * <ol>
 *   <li><b>YAML/Properties</b> — declare routes under {@code spring.cloud.gateway.routes}
 *       in {@code application.yml}. Easy to read but limited in expressiveness.</li>
 *   <li><b>Java DSL (this class)</b> — build routes programmatically using the
 *       {@code RouteLocatorBuilder} fluent API. More powerful: you can compose
 *       conditions, reference Spring beans, and use the full Java language.</li>
 * </ol>
 *
 * <p><b>Route anatomy:</b>
 * Each route has three parts:
 * <ul>
 *   <li><b>ID</b> — a unique name used in logs and the Actuator routes endpoint.</li>
 *   <li><b>Predicates</b> — conditions that must ALL be true for the route to match
 *       (e.g. path starts with {@code /api/products/**}).</li>
 *   <li><b>Filters</b> — transformations applied to the request before forwarding
 *       and/or to the response before returning to the client.</li>
 * </ul>
 *
 * <p><b>Routes defined here:</b>
 * <pre>
 *   /api/products/** → product-service (URI configurable via properties)
 *   /api/orders/**   → order-service   (URI configurable via properties)
 *   /api/users/**    → user-service    (URI configurable via properties)
 * </pre>
 *
 * <p>The downstream URIs are injected from application properties so they can
 * be overridden per environment (dev / Docker Compose / prod) without changing
 * source code. In this demo project the defaults point to WireMock stubs during
 * tests and to configurable URIs in production.
 */
@Configuration
public class GatewayRoutesConfig {

    /**
     * Base URI of the downstream product microservice.
     * Defaults to {@code http://localhost:8081} for local development.
     * Override via the {@code services.product.url} property or the
     * {@code SERVICES_PRODUCT_URL} environment variable.
     */
    @Value("${services.product.url:http://localhost:8081}")
    private String productServiceUrl;

    /**
     * Base URI of the downstream order microservice.
     * Defaults to {@code http://localhost:8082}.
     */
    @Value("${services.order.url:http://localhost:8082}")
    private String orderServiceUrl;

    /**
     * Base URI of the downstream user microservice.
     * Defaults to {@code http://localhost:8083}.
     */
    @Value("${services.user.url:http://localhost:8083}")
    private String userServiceUrl;

    /**
     * Builds and registers all gateway routes.
     *
     * <p>The {@code RouteLocatorBuilder} is injected by Spring Cloud Gateway
     * automatically. It provides a fluent DSL:
     * <pre>
     *   builder.routes()
     *     .route("route-id", r -> r
     *         .predicate(...)    // when to match
     *         .filters(f -> ...) // what to do
     *         .uri("...")        // where to forward
     *     )
     *     .build();
     * </pre>
     *
     * @param builder              the route builder injected by Spring
     * @param correlationIdFactory the custom filter factory for correlation IDs
     * @return the fully configured {@link RouteLocator}
     */
    @Bean
    public RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            AddCorrelationIdGatewayFilterFactory correlationIdFactory) {

        return builder.routes()

                // ------------------------------------------------------------------
                // Route 1: Product Service
                //
                // Matches: GET|POST|PUT|DELETE /api/products/**
                //
                // Filters applied:
                //   1. StripPrefix(1) — removes the leading "/api" segment before
                //      forwarding. The downstream service sees "/products/..." not
                //      "/api/products/...".
                //   2. AddRequestHeader — injects the source-service name so
                //      downstream services know requests arrived via the gateway.
                //   3. AddCorrelationId (custom) — adds X-Correlation-ID header.
                //   4. AddResponseHeader — stamps the response with the service name.
                //
                // Example:
                //   Client:      GET  /api/products/42
                //   Downstream:  GET  /products/42  (at productServiceUrl)
                // ------------------------------------------------------------------
                .route("product-service-route", r -> r
                        .path("/api/products/**")
                        .filters(f -> f
                                // Remove the first path segment ("/api") before forwarding.
                                // StripPrefix(1) strips exactly one leading prefix segment.
                                .stripPrefix(1)
                                // Tell the downstream service where the request came from.
                                .addRequestHeader("X-Gateway-Source", "spring-cloud-gateway")
                                // Apply the custom correlation-ID filter (defined as a bean).
                                .filter(correlationIdFactory.apply(
                                        new AddCorrelationIdGatewayFilterFactory.Config()))
                                // Stamp the response so clients know which service handled it.
                                .addResponseHeader("X-Served-By", "product-service")
                        )
                        .uri(productServiceUrl)
                )

                // ------------------------------------------------------------------
                // Route 2: Order Service
                //
                // Matches: /api/orders/**
                // Same filter chain as the product route.
                //
                // Example:
                //   Client:      POST /api/orders
                //   Downstream:  POST /orders  (at orderServiceUrl)
                // ------------------------------------------------------------------
                .route("order-service-route", r -> r
                        .path("/api/orders/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Gateway-Source", "spring-cloud-gateway")
                                .filter(correlationIdFactory.apply(
                                        new AddCorrelationIdGatewayFilterFactory.Config()))
                                .addResponseHeader("X-Served-By", "order-service")
                        )
                        .uri(orderServiceUrl)
                )

                // ------------------------------------------------------------------
                // Route 3: User Service
                //
                // Matches: /api/users/**
                // Additionally uses RewritePath to demonstrate path rewriting:
                //   /api/users/(.*)  →  /v1/users/$1
                //
                // This is useful when a legacy downstream service uses a versioned
                // path that differs from the public gateway path.
                //
                // Example:
                //   Client:      GET  /api/users/profile
                //   Downstream:  GET  /v1/users/profile  (at userServiceUrl)
                // ------------------------------------------------------------------
                .route("user-service-route", r -> r
                        .path("/api/users/**")
                        .filters(f -> f
                                // RewritePath rewrites the request path using a regex.
                                // Capture group (?<segment>.*) captures everything after
                                // /api/users/ and replaces the whole path with /v1/users/${segment}.
                                // RewritePath rewrites /api/users or /api/users/anything
                                // to /v1/users or /v1/users/anything respectively.
                                // The optional group handles both the bare /api/users path
                                // and paths with sub-segments like /api/users/profile.
                                .rewritePath("/api/users(?<segment>/?.*)", "/v1/users${segment}")
                                .addRequestHeader("X-Gateway-Source", "spring-cloud-gateway")
                                .filter(correlationIdFactory.apply(
                                        new AddCorrelationIdGatewayFilterFactory.Config()))
                                .addResponseHeader("X-Served-By", "user-service")
                        )
                        .uri(userServiceUrl)
                )

                .build();
    }
}
