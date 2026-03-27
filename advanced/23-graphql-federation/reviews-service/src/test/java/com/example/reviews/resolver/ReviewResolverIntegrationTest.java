package com.example.reviews.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReviewResolver} — the GraphQL endpoint layer.
 *
 * <p>Starts the full Spring Boot application context on a random port and sends
 * real GraphQL HTTP requests via {@link HttpGraphQlTester}. No Docker containers
 * are needed because the reviews-service uses an in-memory repository.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@DisplayName("ReviewResolver GraphQL integration tests")
class ReviewResolverIntegrationTest {

    @Autowired
    private GraphQlTester graphQlTester;

    // =========================================================================
    // reviews query
    // =========================================================================

    @Test
    @DisplayName("reviews query returns all 10 seeded reviews")
    void reviewsQuery_returnsAllReviews() {
        String query = """
                query {
                  reviews {
                    id
                    productId
                    author
                    rating
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("reviews")
                .entityList(Object.class)
                .hasSize(10);
    }

    @Test
    @DisplayName("reviews query returns reviews with rating between 1 and 5")
    void reviewsQuery_ratingsAreInValidRange() {
        String query = "query { reviews { id rating } }";

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("reviews[*].rating")
                .entityList(Integer.class)
                .satisfies(ratings -> ratings.forEach(rating ->
                        assertThat(rating).isBetween(1, 5)));
    }

    // =========================================================================
    // review(id) query
    // =========================================================================

    @Test
    @DisplayName("review(id) returns the correct review for a known ID")
    void reviewByIdQuery_knownId_returnsReview() {
        String query = """
                query {
                  review(id: "rev-1") {
                    id
                    productId
                    author
                    rating
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("review.id").entity(String.class).isEqualTo("rev-1")
                .path("review.productId").entity(String.class).isEqualTo("prod-1")
                .path("review.author").entity(String.class).isEqualTo("Alice");
    }

    @Test
    @DisplayName("review(id) returns null for an unknown ID")
    void reviewByIdQuery_unknownId_returnsNull() {
        String query = """
                query {
                  review(id: "rev-999") {
                    id
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("review").valueIsNull();
    }

    // =========================================================================
    // reviewsByProduct query
    // =========================================================================

    @Test
    @DisplayName("reviewsByProduct returns only reviews for the given product")
    void reviewsByProductQuery_prod1_returnsTwoReviews() {
        String query = """
                query {
                  reviewsByProduct(productId: "prod-1") {
                    id
                    productId
                    author
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("reviewsByProduct")
                .entityList(Object.class)
                .hasSize(2);    // rev-1 (Alice) and rev-2 (Bob)
    }

    @Test
    @DisplayName("reviewsByProduct returns empty list for product with no reviews")
    void reviewsByProductQuery_noReviews_returnsEmptyList() {
        String query = """
                query {
                  reviewsByProduct(productId: "prod-4") {
                    id
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("reviewsByProduct")
                .entityList(Object.class)
                .hasSize(0);
    }

    // =========================================================================
    // reviewsByMinRating query
    // =========================================================================

    @Test
    @DisplayName("reviewsByMinRating(5) returns only 5-star reviews")
    void reviewsByMinRatingQuery_5stars_returnsOnlyFiveStarReviews() {
        String query = """
                query {
                  reviewsByMinRating(minRating: 5) {
                    id
                    rating
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("reviewsByMinRating[*].rating")
                .entityList(Integer.class)
                .satisfies(ratings -> ratings.forEach(rating ->
                        assertThat(rating).isEqualTo(5)));
    }

    @Test
    @DisplayName("reviewsByMinRating(4) returns reviews with rating 4 or 5")
    void reviewsByMinRatingQuery_4stars_returnsHighRatedReviews() {
        String query = """
                query {
                  reviewsByMinRating(minRating: 4) {
                    id
                    rating
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("reviewsByMinRating[*].rating")
                .entityList(Integer.class)
                .satisfies(ratings -> ratings.forEach(rating ->
                        assertThat(rating).isGreaterThanOrEqualTo(4)));
    }
}
