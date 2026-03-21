package com.example.springcloudgateway.model;

import java.util.List;

/**
 * A domain record representing a summary of a single gateway route.
 *
 * <p><b>What is a record?</b>
 * Java records (introduced in Java 16, stable in Java 17+) are immutable data
 * carriers. The compiler automatically generates:
 * <ul>
 *   <li>A canonical constructor with all fields.</li>
 *   <li>Accessor methods (e.g. {@code id()}, {@code uri()}).</li>
 *   <li>{@code equals()}, {@code hashCode()}, and {@code toString()}.</li>
 * </ul>
 * They are ideal for DTOs and value objects where mutability is not needed.
 *
 * <p><b>Purpose of this record:</b>
 * The gateway exposes a management endpoint ({@code /gateway/routes}) that
 * returns a list of all configured routes so operators can inspect the running
 * configuration without restarting or reading YAML files. This record is the
 * JSON response body for each entry in that list.
 *
 * <p><b>Fields:</b>
 * <ul>
 *   <li>{@code id} — the unique route identifier (e.g. "product-service-route").</li>
 *   <li>{@code uri} — the downstream target URI (e.g. "http://localhost:8081").</li>
 *   <li>{@code predicates} — human-readable list of matching conditions
 *       (e.g. ["Path=/api/products/**"]).</li>
 *   <li>{@code filters} — human-readable list of filters applied to the route
 *       (e.g. ["StripPrefix=1", "AddRequestHeader=X-Gateway-Source, spring-cloud-gateway"]).</li>
 * </ul>
 *
 * @param id         the unique route identifier
 * @param uri        the downstream target URI
 * @param predicates list of predicates that determine when this route matches
 * @param filters    list of filters applied to matching requests/responses
 */
public record RouteInfo(
        String id,
        String uri,
        List<String> predicates,
        List<String> filters
) {
    // Records are intentionally empty of methods unless you need custom logic.
    // All accessors, equals/hashCode/toString are compiler-generated.
}
