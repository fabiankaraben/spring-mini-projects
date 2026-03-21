package com.example.springcloudgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Cloud Gateway application.
 *
 * <p><b>What is an API Gateway?</b>
 * An API Gateway is a server that acts as the single entry point for all client
 * requests in a microservices architecture. Instead of clients calling each
 * microservice directly, every request passes through the gateway, which:
 * <ol>
 *   <li><b>Routes</b> requests to the appropriate downstream microservice based
 *       on predicates (path, method, headers, query params, etc.).</li>
 *   <li><b>Filters</b> requests and responses — adding/removing headers,
 *       rewriting paths, enforcing authentication, logging, rate limiting, etc.</li>
 *   <li><b>Aggregates</b> cross-cutting concerns in one place, keeping
 *       individual microservices focused on their domain logic.</li>
 * </ol>
 *
 * <p><b>Why Spring Cloud Gateway?</b>
 * Spring Cloud Gateway is the modern replacement for the legacy Netflix Zuul
 * gateway. It is built on Spring WebFlux (Project Reactor / Netty) rather than
 * the blocking Servlet API, which gives it:
 * <ul>
 *   <li>Non-blocking, reactive I/O — handles many concurrent connections with
 *       a small thread pool instead of one thread per request.</li>
 *   <li>Tight integration with the Spring ecosystem (Security, Actuator, Cloud).</li>
 *   <li>A rich, composable predicate and filter DSL available both in YAML
 *       configuration and as a Java {@code RouteLocatorBuilder} fluent API.</li>
 * </ul>
 *
 * <p><b>Architecture overview:</b>
 * <pre>
 *   Client → Gateway (port 8080)
 *               ├── /api/products/** → Product Service (downstream)
 *               ├── /api/orders/**   → Order Service (downstream)
 *               └── /api/users/**    → User Service (downstream)
 * </pre>
 *
 * <p><b>Key components in this project:</b>
 * <ul>
 *   <li>{@code GatewayRoutesConfig} — defines all routes programmatically using
 *       the {@code RouteLocatorBuilder} Java DSL.</li>
 *   <li>{@code LoggingGlobalFilter} — a {@code GlobalFilter} that logs every
 *       incoming request and outgoing response passing through the gateway.</li>
 *   <li>{@code RequestIdGlobalFilter} — a {@code GlobalFilter} that attaches a
 *       unique correlation ID to every request for distributed tracing.</li>
 *   <li>{@code RouteInfo} — a domain record describing a configured route.</li>
 *   <li>{@code GatewayInfoController} — exposes a REST endpoint listing all
 *       active routes, demonstrating how to inspect gateway configuration at
 *       runtime.</li>
 * </ul>
 *
 * <p><b>Important: reactive application</b>
 * Because Spring Cloud Gateway uses WebFlux, there is NO embedded Tomcat.
 * The server is Netty. Standard Spring MVC annotations ({@code @RestController},
 * etc.) still work on WebFlux, but you must use {@code WebTestClient} (not
 * {@code MockMvc}) in tests.
 */
@SpringBootApplication
public class SpringCloudGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringCloudGatewayApplication.class, args);
    }
}
