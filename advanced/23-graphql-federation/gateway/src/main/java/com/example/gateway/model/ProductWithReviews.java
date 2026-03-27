package com.example.gateway.model;

import java.util.List;

/**
 * Composite DTO that merges a Product with its Reviews.
 *
 * <p>This type is the core "stitched" object that demonstrates GraphQL
 * Federation value: it combines data from two independent subgraph services
 * (products-service + reviews-service) into a single unified object.
 *
 * <p>The gateway assembles this object by:
 * <ol>
 *   <li>Fetching the {@link Product} from the products-service via
 *       {@code POST /graphql} with a product query.</li>
 *   <li>Fetching the {@link Review} list from the reviews-service via
 *       {@code POST /graphql} with a reviewsByProduct query.</li>
 *   <li>Combining both results into this record and returning it to the
 *       client in a single GraphQL response.</li>
 * </ol>
 *
 * <p>From the client's perspective, they send one query to the gateway and
 * get back a fully assembled object — they never need to know about the
 * two downstream services.
 *
 * @param product the product details from the products-service
 * @param reviews the list of reviews for that product from the reviews-service
 */
public record ProductWithReviews(
        Product product,
        List<Review> reviews
) {}
