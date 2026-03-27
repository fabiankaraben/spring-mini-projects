package com.example.reviews.resolver;

import com.example.reviews.model.Review;
import com.example.reviews.repository.ReviewRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL resolver for the {@code Query} type defined in the reviews schema.
 *
 * <p>Each {@link QueryMapping} method maps to a field on the GraphQL
 * {@code Query} type declared in {@code schema.graphqls}. Spring for GraphQL
 * matches methods by name — the method name must equal the GraphQL field name.
 *
 * <p>{@link Argument} binds a GraphQL operation argument to a Java method
 * parameter by name.
 */
@Controller
public class ReviewResolver {

    private final ReviewRepository reviewRepository;

    /**
     * Constructor injection makes dependencies explicit and testable without
     * needing a full Spring application context.
     */
    public ReviewResolver(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    /**
     * Resolves the {@code reviews} query field.
     *
     * <p>Returns all reviews across all products.
     *
     * <p>GraphQL query:
     * <pre>
     * query {
     *   reviews {
     *     id
     *     productId
     *     author
     *     rating
     *   }
     * }
     * </pre>
     *
     * @return list of all reviews (never null)
     */
    @QueryMapping
    public List<Review> reviews() {
        return reviewRepository.findAll();
    }

    /**
     * Resolves the {@code review(id: ID!)} query field.
     *
     * <p>Returns a single review by its unique ID, or {@code null} if none found.
     *
     * <p>GraphQL query:
     * <pre>
     * query {
     *   review(id: "rev-1") {
     *     id
     *     author
     *     body
     *     rating
     *   }
     * }
     * </pre>
     *
     * @param id the review ID to look up
     * @return the found review, or {@code null}
     */
    @QueryMapping
    public Review review(@Argument String id) {
        return reviewRepository.findById(id).orElse(null);
    }

    /**
     * Resolves the {@code reviewsByProduct(productId: ID!)} query field.
     *
     * <p>Returns all reviews for a specific product. Clients (or the gateway)
     * can use this to retrieve reviews when they already know the product ID.
     *
     * <p>GraphQL query:
     * <pre>
     * query {
     *   reviewsByProduct(productId: "prod-1") {
     *     id
     *     author
     *     body
     *     rating
     *   }
     * }
     * </pre>
     *
     * @param productId the product ID to filter reviews by
     * @return list of reviews for that product
     */
    @QueryMapping
    public List<Review> reviewsByProduct(@Argument String productId) {
        return reviewRepository.findByProductId(productId);
    }

    /**
     * Resolves the {@code reviewsByMinRating(minRating: Int!)} query field.
     *
     * <p>Returns all reviews with a rating >= the given threshold. Useful for
     * showing only highly-rated reviews.
     *
     * <p>GraphQL query:
     * <pre>
     * query {
     *   reviewsByMinRating(minRating: 4) {
     *     id
     *     author
     *     rating
     *     body
     *   }
     * }
     * </pre>
     *
     * @param minRating minimum rating (1-5)
     * @return list of qualifying reviews
     */
    @QueryMapping
    public List<Review> reviewsByMinRating(@Argument int minRating) {
        return reviewRepository.findByMinRating(minRating);
    }
}
