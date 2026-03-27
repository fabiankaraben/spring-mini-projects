package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GraphQL Federation Gateway — entry point.
 *
 * <p>This Spring Boot application acts as the "supergraph" gateway in the
 * federation architecture. It exposes a single unified GraphQL API at
 * {@code POST /graphql} (port 8080) that aggregates data from two downstream
 * subgraph services:
 *
 * <ul>
 *   <li><strong>products-service</strong> (port 8081) — owns Product types</li>
 *   <li><strong>reviews-service</strong> (port 8082) — owns Review types</li>
 * </ul>
 *
 * <p>How federation works here (schema-stitching approach):
 * <ol>
 *   <li>The gateway holds its own "supergraph" GraphQL schema that declares
 *       all types ({@code Product}, {@code Review}, {@code ProductWithReviews})
 *       visible to external clients.</li>
 *   <li>{@link com.example.gateway.resolver.GatewayQueryResolver} intercepts
 *       top-level Query fields and delegates each sub-query to the appropriate
 *       downstream service using a {@link org.springframework.web.reactive.function.client.WebClient}.</li>
 *   <li>For combined queries (e.g. "give me a product AND its reviews"), the
 *       gateway calls both services, receives their results, and merges them
 *       into a composite response.</li>
 * </ol>
 *
 * <p>This is the "transparent" approach — every HTTP call to a subgraph is
 * visible, making it ideal for educational purposes.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
