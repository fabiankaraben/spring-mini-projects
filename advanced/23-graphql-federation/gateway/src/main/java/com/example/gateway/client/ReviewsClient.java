package com.example.gateway.client;

import com.example.gateway.model.Review;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP client that delegates GraphQL queries to the reviews-service subgraph.
 *
 * <p>This client encapsulates all communication with the reviews-service. The
 * gateway calls these methods when it needs to resolve Review-related fields
 * in the unified supergraph schema.
 *
 * <p>Mirrors the design of {@link ProductsClient} — see that class for a
 * detailed explanation of the communication protocol and design decisions.
 */
@Component
public class ReviewsClient {

    /**
     * WebClient pointed at the reviews-service base URL.
     * The base URL is injected from application.yml (federation.reviews-url).
     */
    private final WebClient webClient;

    /** Jackson ObjectMapper for extracting the GraphQL data node. */
    private final ObjectMapper objectMapper;

    /**
     * @param reviewsUrl   base URL of the reviews-service (e.g. http://localhost:8082)
     * @param objectMapper Jackson ObjectMapper bean
     */
    public ReviewsClient(
            @Value("${federation.reviews-url}") String reviewsUrl,
            ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .baseUrl(reviewsUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches all reviews from the reviews-service.
     *
     * @return list of all reviews (never null)
     */
    public List<Review> fetchAllReviews() {
        String query = """
                query {
                  reviews {
                    id productId author body rating
                  }
                }
                """;
        JsonNode data = executeQuery(query, Map.of());
        return parseList(data.path("reviews"), new TypeReference<List<Review>>() {});
    }

    /**
     * Fetches a single review by ID from the reviews-service.
     *
     * @param id the review ID to look up
     * @return Optional containing the review if found, or empty
     */
    public Optional<Review> fetchReviewById(String id) {
        String query = """
                query($id: ID!) {
                  review(id: $id) {
                    id productId author body rating
                  }
                }
                """;
        JsonNode data = executeQuery(query, Map.of("id", id));
        JsonNode reviewNode = data.path("review");
        if (reviewNode.isMissingNode() || reviewNode.isNull()) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.convertValue(reviewNode, Review.class));
    }

    /**
     * Fetches all reviews for a specific product from the reviews-service.
     *
     * <p>The gateway uses this method when assembling a {@link com.example.gateway.model.ProductWithReviews}
     * — it passes the product ID to get the matching reviews, then merges them
     * with the product details fetched from the products-service.
     *
     * @param productId the product ID to filter reviews by
     * @return list of reviews for that product
     */
    public List<Review> fetchReviewsByProduct(String productId) {
        String query = """
                query($productId: ID!) {
                  reviewsByProduct(productId: $productId) {
                    id productId author body rating
                  }
                }
                """;
        JsonNode data = executeQuery(query, Map.of("productId", productId));
        return parseList(data.path("reviewsByProduct"),
                new TypeReference<List<Review>>() {});
    }

    /**
     * Fetches all reviews with rating >= minRating from the reviews-service.
     *
     * @param minRating minimum rating threshold (1-5)
     * @return list of qualifying reviews
     */
    public List<Review> fetchReviewsByMinRating(int minRating) {
        String query = """
                query($minRating: Int!) {
                  reviewsByMinRating(minRating: $minRating) {
                    id productId author body rating
                  }
                }
                """;
        JsonNode data = executeQuery(query, Map.of("minRating", minRating));
        return parseList(data.path("reviewsByMinRating"),
                new TypeReference<List<Review>>() {});
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Executes a GraphQL operation against the reviews-service and returns
     * the {@code data} node of the response.
     *
     * @param query     GraphQL query string
     * @param variables map of variable name → value
     * @return the data JsonNode (never null; empty object node on error)
     */
    private JsonNode executeQuery(String query, Map<String, Object> variables) {
        Map<String, Object> requestBody = Map.of(
                "query", query,
                "variables", variables
        );

        String responseBody = webClient.post()
                .uri("/graphql")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("data");
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    /**
     * Converts a JsonNode array to a typed Java List.
     */
    private <T> List<T> parseList(JsonNode node, TypeReference<List<T>> typeReference) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(node, typeReference);
        } catch (Exception e) {
            return List.of();
        }
    }
}
