package com.example.reviews.repository;

import com.example.reviews.model.Review;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository for {@link Review} entities.
 *
 * <p>Stores all reviews in a {@link ConcurrentHashMap} (thread-safe) pre-seeded
 * with sample data on construction. No external database is required — this keeps
 * the project self-contained and easy to run locally.
 *
 * <p>The seeded reviews reference product IDs from the products-service
 * (prod-1 through prod-8). The gateway uses these IDs to fetch full product
 * details when a client queries reviews together with product information.
 */
@Repository
public class ReviewRepository {

    /**
     * In-memory store: reviewId → Review.
     */
    private final Map<String, Review> store = new ConcurrentHashMap<>();

    /**
     * Seed the in-memory store with sample reviews.
     * Reviews are distributed across several products (prod-1 to prod-6)
     * and use a variety of ratings and authors.
     */
    public ReviewRepository() {
        List<Review> seed = List.of(
                new Review("rev-1", "prod-1", "Alice",
                        "Great keyboard, very responsive keys and comfortable to type on.",
                        5),
                new Review("rev-2", "prod-1", "Bob",
                        "Battery life is shorter than advertised but otherwise solid.",
                        4),
                new Review("rev-3", "prod-2", "Carol",
                        "Eliminated my wrist pain after just one week of use!",
                        5),
                new Review("rev-4", "prod-2", "David",
                        "Took some getting used to, but now I wouldn't switch back.",
                        4),
                new Review("rev-5", "prod-3", "Eve",
                        "Desk quality is excellent, motor is quiet and reliable.",
                        5),
                new Review("rev-6", "prod-3", "Frank",
                        "Assembly instructions were unclear but the desk itself is great.",
                        4),
                new Review("rev-7", "prod-5", "Grace",
                        "All ports work perfectly. Power delivery charges my laptop fast.",
                        5),
                new Review("rev-8", "prod-5", "Henry",
                        "HDMI only supports 1080p60 — not ideal for 4K monitors.",
                        3),
                new Review("rev-9", "prod-6", "Iris",
                        "Best headphones I've ever owned. ANC is class-leading.",
                        5),
                new Review("rev-10", "prod-6", "James",
                        "Slightly tight on large heads but audio quality is fantastic.",
                        4)
        );
        seed.forEach(r -> store.put(r.id(), r));
    }

    /**
     * Returns all reviews in the store.
     *
     * @return an unordered list of all reviews
     */
    public List<Review> findAll() {
        return List.copyOf(store.values());
    }

    /**
     * Looks up a single review by its unique identifier.
     *
     * @param id the review ID to search for
     * @return an {@link Optional} containing the review, or empty if not found
     */
    public Optional<Review> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Returns all reviews that belong to the specified product.
     *
     * @param productId the product ID to filter by
     * @return a list of reviews for that product (may be empty)
     */
    public List<Review> findByProductId(String productId) {
        return store.values().stream()
                .filter(r -> r.productId().equals(productId))
                .toList();
    }

    /**
     * Returns all reviews with a rating greater than or equal to the given value.
     *
     * @param minRating minimum rating threshold (1-5)
     * @return list of reviews meeting the threshold
     */
    public List<Review> findByMinRating(int minRating) {
        return store.values().stream()
                .filter(r -> r.rating() >= minRating)
                .toList();
    }
}
