package com.example.products;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Products Service — GraphQL Subgraph entry point.
 *
 * <p>This Spring Boot application is one of two "subgraph" services in the
 * GraphQL Federation demo. It owns the {@code Product} bounded-context and
 * exposes a standalone GraphQL API at {@code POST /graphql}.
 *
 * <p>In a federated architecture each subgraph is responsible for a distinct
 * slice of the data model. Subgraphs publish their schema so that a gateway
 * can introspect them and stitch the schemas together into a unified graph.
 *
 * <p>This service runs on port 8081 (see application.yml).
 */
@SpringBootApplication
public class ProductsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductsServiceApplication.class, args);
    }
}
