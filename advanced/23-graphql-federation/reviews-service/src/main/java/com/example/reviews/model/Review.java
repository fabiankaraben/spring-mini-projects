package com.example.reviews.model;

/**
 * Domain model representing a customer Review for a product.
 *
 * <p>In the GraphQL Federation architecture this is the canonical type for
 * review data, owned exclusively by the reviews-service subgraph. The
 * {@code productId} field is the "foreign key" that links a review to a
 * product. The gateway uses this ID to fetch the full {@code Product} type
 * from the products-service and merge it with the review data.
 *
 * <p>Defined as a Java record for immutability and compact syntax.
 *
 * @param id        Unique review identifier (e.g., "rev-1").
 * @param productId ID of the product this review is for; references
 *                  {@code Product.id} in the products-service.
 * @param author    Display name of the review author.
 * @param body      Full text content of the review.
 * @param rating    Numeric rating from 1 (worst) to 5 (best).
 */
public record Review(
        String id,
        String productId,
        String author,
        String body,
        int rating
) {}
