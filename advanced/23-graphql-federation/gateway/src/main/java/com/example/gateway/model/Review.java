package com.example.gateway.model;

/**
 * Gateway-side representation of a Review.
 *
 * <p>This DTO mirrors the {@code Review} type returned by the reviews-service
 * GraphQL API. The gateway deserializes the JSON response from the
 * reviews-service HTTP call into this record.
 *
 * @param id        Unique review identifier.
 * @param productId ID of the product this review is for.
 * @param author    Display name of the review author.
 * @param body      Full text of the review.
 * @param rating    Numeric rating 1-5.
 */
public record Review(
        String id,
        String productId,
        String author,
        String body,
        int rating
) {}
