package com.example.reviews.repository;

import com.example.reviews.model.Review;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReviewRepository}.
 *
 * <p>Pure unit tests — no Spring context, no network. The repository is
 * instantiated directly and its in-memory logic is verified in isolation.
 */
@DisplayName("ReviewRepository unit tests")
class ReviewRepositoryTest {

    /** Fresh repository re-created before each test for isolation. */
    private ReviewRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ReviewRepository();
    }

    // =========================================================================
    // findAll()
    // =========================================================================

    @Test
    @DisplayName("findAll() returns all 10 seeded reviews")
    void findAll_returnsAllSeededReviews() {
        List<Review> reviews = repository.findAll();

        // The repository seeds exactly 10 reviews in its constructor
        assertThat(reviews)
                .as("Repository should contain all 10 seeded reviews")
                .hasSize(10);
    }

    @Test
    @DisplayName("findAll() reviews have required fields populated")
    void findAll_reviewsHaveRequiredFields() {
        List<Review> reviews = repository.findAll();

        assertThat(reviews).allSatisfy(r -> {
            assertThat(r.id()).isNotBlank();
            assertThat(r.productId()).isNotBlank();
            assertThat(r.author()).isNotBlank();
            assertThat(r.body()).isNotBlank();
            assertThat(r.rating()).isBetween(1, 5);
        });
    }

    // =========================================================================
    // findById()
    // =========================================================================

    @Test
    @DisplayName("findById() returns the correct review for a known ID")
    void findById_knownId_returnsReview() {
        Optional<Review> result = repository.findById("rev-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("rev-1");
        assertThat(result.get().productId()).isEqualTo("prod-1");
        assertThat(result.get().author()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("findById() returns empty Optional for unknown ID")
    void findById_unknownId_returnsEmpty() {
        Optional<Review> result = repository.findById("rev-999");

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // findByProductId()
    // =========================================================================

    @Test
    @DisplayName("findByProductId() returns reviews only for the given product")
    void findByProductId_prod1_returnsTwoReviews() {
        List<Review> reviews = repository.findByProductId("prod-1");

        // prod-1 has 2 reviews: rev-1 (Alice) and rev-2 (Bob)
        assertThat(reviews).hasSize(2);
        assertThat(reviews).allMatch(r -> r.productId().equals("prod-1"));
    }

    @Test
    @DisplayName("findByProductId() returns empty list for product with no reviews")
    void findByProductId_noReviews_returnsEmptyList() {
        // prod-4 and prod-8 have no reviews in the seed data
        List<Review> reviews = repository.findByProductId("prod-4");

        assertThat(reviews).isEmpty();
    }

    @Test
    @DisplayName("findByProductId() returns empty list for unknown product")
    void findByProductId_unknownProduct_returnsEmptyList() {
        List<Review> reviews = repository.findByProductId("prod-999");

        assertThat(reviews).isEmpty();
    }

    @Test
    @DisplayName("findByProductId() for prod-2 returns exactly 2 reviews")
    void findByProductId_prod2_returnsTwoReviews() {
        List<Review> reviews = repository.findByProductId("prod-2");

        assertThat(reviews).hasSize(2);
        assertThat(reviews).allMatch(r -> r.productId().equals("prod-2"));
    }

    // =========================================================================
    // findByMinRating()
    // =========================================================================

    @Test
    @DisplayName("findByMinRating(5) returns only 5-star reviews")
    void findByMinRating_5_returnsOnly5StarReviews() {
        List<Review> reviews = repository.findByMinRating(5);

        assertThat(reviews).isNotEmpty();
        assertThat(reviews).allMatch(r -> r.rating() >= 5);
    }

    @Test
    @DisplayName("findByMinRating(1) returns all reviews")
    void findByMinRating_1_returnsAllReviews() {
        List<Review> all = repository.findAll();
        List<Review> minRating1 = repository.findByMinRating(1);

        // Rating >= 1 includes everything since all seeded ratings are >= 1
        assertThat(minRating1).hasSameSizeAs(all);
    }

    @Test
    @DisplayName("findByMinRating(4) returns reviews with rating 4 or 5")
    void findByMinRating_4_returnsOnlyHighRatedReviews() {
        List<Review> reviews = repository.findByMinRating(4);

        assertThat(reviews).isNotEmpty();
        assertThat(reviews).allMatch(r -> r.rating() >= 4);

        // There should be fewer or equal results than the 3-star threshold
        List<Review> lowerThreshold = repository.findByMinRating(3);
        assertThat(reviews.size()).isLessThanOrEqualTo(lowerThreshold.size());
    }

    @Test
    @DisplayName("findByMinRating(6) returns empty list (rating max is 5)")
    void findByMinRating_aboveMax_returnsEmptyList() {
        // No seeded review has rating > 5
        List<Review> reviews = repository.findByMinRating(6);

        assertThat(reviews).isEmpty();
    }
}
