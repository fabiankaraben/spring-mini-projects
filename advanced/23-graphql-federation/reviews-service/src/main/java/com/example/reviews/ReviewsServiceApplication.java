package com.example.reviews;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reviews Service — GraphQL Subgraph entry point.
 *
 * <p>This Spring Boot application is the second "subgraph" service in the
 * GraphQL Federation demo. It owns the {@code Review} bounded-context and
 * exposes a standalone GraphQL API at {@code POST /graphql}.
 *
 * <p>Each {@link com.example.reviews.model.Review} references a {@code productId}.
 * The gateway combines this service's reviews data with product details from
 * the products-service to serve federated queries.
 *
 * <p>This service runs on port 8082 (see application.yml).
 */
@SpringBootApplication
public class ReviewsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewsServiceApplication.class, args);
    }
}
