package com.example.gateway.resolver;

import com.example.gateway.client.ProductsClient;
import com.example.gateway.client.ReviewsClient;
import com.example.gateway.model.Product;
import com.example.gateway.model.ProductWithReviews;
import com.example.gateway.model.Review;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GatewayQueryResolver}.
 *
 * <p>These are pure unit tests — no Spring context, no HTTP calls, no Docker.
 * The two client dependencies ({@link ProductsClient} and {@link ReviewsClient})
 * are replaced with Mockito mocks so we can verify the resolver's orchestration
 * logic in complete isolation.
 *
 * <p>What is tested here:
 * <ul>
 *   <li>That the resolver delegates each query to the correct client method.</li>
 *   <li>That {@link GatewayQueryResolver#productWithReviews(String)} correctly
 *       merges data from both clients into a {@link ProductWithReviews}.</li>
 *   <li>That the resolver returns {@code null} (not throw) when a product is
 *       not found — which is correct GraphQL null semantics.</li>
 * </ul>
 *
 * <p>{@link ExtendWith(MockitoExtension.class)} wires Mockito's JUnit 5
 * extension which processes @Mock and @InjectMocks annotations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayQueryResolver unit tests")
class GatewayQueryResolverTest {

    // -------------------------------------------------------------------------
    // Shared test fixtures
    // -------------------------------------------------------------------------

    /** A sample product used across multiple tests. */
    private static final Product PRODUCT_1 = new Product(
            "prod-1", "Wireless Keyboard", "Compact Bluetooth keyboard",
            new BigDecimal("79.99"), "Electronics", true);

    /** A second sample product used for list tests. */
    private static final Product PRODUCT_2 = new Product(
            "prod-2", "Ergonomic Mouse", "Vertical ergonomic mouse",
            new BigDecimal("49.99"), "Electronics", true);

    /** Sample reviews belonging to prod-1. */
    private static final List<Review> REVIEWS_PROD_1 = List.of(
            new Review("rev-1", "prod-1", "Alice", "Great keyboard!", 5),
            new Review("rev-2", "prod-1", "Bob", "Battery life is short.", 4)
    );

    // -------------------------------------------------------------------------
    // Mocks and system under test
    // -------------------------------------------------------------------------

    /** Mocked HTTP client for the products-service subgraph. */
    @Mock
    private ProductsClient productsClient;

    /** Mocked HTTP client for the reviews-service subgraph. */
    @Mock
    private ReviewsClient reviewsClient;

    /**
     * System under test — Mockito injects the two @Mock fields via constructor.
     * GatewayQueryResolver uses constructor injection, so @InjectMocks works.
     */
    @InjectMocks
    private GatewayQueryResolver resolver;

    // =========================================================================
    // products()
    // =========================================================================

    @Test
    @DisplayName("products() delegates to ProductsClient.fetchAllProducts()")
    void products_delegatesToProductsClient() {
        // Arrange — stub the client to return our two sample products
        when(productsClient.fetchAllProducts()).thenReturn(List.of(PRODUCT_1, PRODUCT_2));

        // Act
        List<Product> result = resolver.products();

        // Assert — the resolver returns what the client returned
        assertThat(result).containsExactlyInAnyOrder(PRODUCT_1, PRODUCT_2);
        // Verify the correct client method was called exactly once
        verify(productsClient, times(1)).fetchAllProducts();
        // Ensure the reviews client was NOT touched (no cross-contamination)
        verifyNoInteractions(reviewsClient);
    }

    // =========================================================================
    // product(id)
    // =========================================================================

    @Test
    @DisplayName("product(id) returns the product when found")
    void product_knownId_returnsProduct() {
        when(productsClient.fetchProductById("prod-1")).thenReturn(Optional.of(PRODUCT_1));

        Product result = resolver.product("prod-1");

        assertThat(result).isEqualTo(PRODUCT_1);
        verify(productsClient, times(1)).fetchProductById("prod-1");
        verifyNoInteractions(reviewsClient);
    }

    @Test
    @DisplayName("product(id) returns null when product is not found")
    void product_unknownId_returnsNull() {
        when(productsClient.fetchProductById("prod-999")).thenReturn(Optional.empty());

        Product result = resolver.product("prod-999");

        // GraphQL nullable field — resolver returns null, not an exception
        assertThat(result).isNull();
        verify(productsClient, times(1)).fetchProductById("prod-999");
    }

    // =========================================================================
    // productsByCategory(category)
    // =========================================================================

    @Test
    @DisplayName("productsByCategory() delegates to ProductsClient with the category argument")
    void productsByCategory_delegatesWithCorrectArgument() {
        when(productsClient.fetchProductsByCategory("Electronics"))
                .thenReturn(List.of(PRODUCT_1, PRODUCT_2));

        List<Product> result = resolver.productsByCategory("Electronics");

        assertThat(result).hasSize(2);
        verify(productsClient, times(1)).fetchProductsByCategory("Electronics");
    }

    // =========================================================================
    // productsInStock()
    // =========================================================================

    @Test
    @DisplayName("productsInStock() delegates to ProductsClient.fetchProductsInStock()")
    void productsInStock_delegatesToClient() {
        when(productsClient.fetchProductsInStock()).thenReturn(List.of(PRODUCT_1));

        List<Product> result = resolver.productsInStock();

        assertThat(result).containsExactly(PRODUCT_1);
        verify(productsClient, times(1)).fetchProductsInStock();
    }

    // =========================================================================
    // reviews()
    // =========================================================================

    @Test
    @DisplayName("reviews() delegates to ReviewsClient.fetchAllReviews()")
    void reviews_delegatesToReviewsClient() {
        when(reviewsClient.fetchAllReviews()).thenReturn(REVIEWS_PROD_1);

        List<Review> result = resolver.reviews();

        assertThat(result).isEqualTo(REVIEWS_PROD_1);
        verify(reviewsClient, times(1)).fetchAllReviews();
        verifyNoInteractions(productsClient);
    }

    // =========================================================================
    // review(id)
    // =========================================================================

    @Test
    @DisplayName("review(id) returns review when found")
    void review_knownId_returnsReview() {
        Review expected = REVIEWS_PROD_1.get(0);
        when(reviewsClient.fetchReviewById("rev-1")).thenReturn(Optional.of(expected));

        Review result = resolver.review("rev-1");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("review(id) returns null when not found")
    void review_unknownId_returnsNull() {
        when(reviewsClient.fetchReviewById("rev-999")).thenReturn(Optional.empty());

        Review result = resolver.review("rev-999");

        assertThat(result).isNull();
    }

    // =========================================================================
    // productWithReviews(productId) — THE core federation operation
    // =========================================================================

    @Test
    @DisplayName("productWithReviews() merges product and reviews from both clients")
    void productWithReviews_found_mergesProductAndReviews() {
        // Arrange — stub both clients
        when(productsClient.fetchProductById("prod-1")).thenReturn(Optional.of(PRODUCT_1));
        when(reviewsClient.fetchReviewsByProduct("prod-1")).thenReturn(REVIEWS_PROD_1);

        // Act — invoke the core federation method
        ProductWithReviews result = resolver.productWithReviews("prod-1");

        // Assert — the composite object must contain data from BOTH services
        assertThat(result).isNotNull();
        assertThat(result.product()).isEqualTo(PRODUCT_1);
        assertThat(result.reviews()).isEqualTo(REVIEWS_PROD_1);

        // Both clients must have been called exactly once
        verify(productsClient, times(1)).fetchProductById("prod-1");
        verify(reviewsClient, times(1)).fetchReviewsByProduct("prod-1");
    }

    @Test
    @DisplayName("productWithReviews() returns null when product does not exist")
    void productWithReviews_productNotFound_returnsNull() {
        // Arrange — product client returns empty
        when(productsClient.fetchProductById("prod-999")).thenReturn(Optional.empty());

        // Act
        ProductWithReviews result = resolver.productWithReviews("prod-999");

        // Assert — must return null, NOT call the reviews client
        assertThat(result).isNull();
        verify(productsClient, times(1)).fetchProductById("prod-999");
        // Reviews client must NOT be called when the product doesn't exist
        verifyNoInteractions(reviewsClient);
    }

    @Test
    @DisplayName("productWithReviews() handles product with no reviews")
    void productWithReviews_productWithNoReviews_returnsEmptyReviewsList() {
        when(productsClient.fetchProductById("prod-1")).thenReturn(Optional.of(PRODUCT_1));
        when(reviewsClient.fetchReviewsByProduct("prod-1")).thenReturn(List.of());

        ProductWithReviews result = resolver.productWithReviews("prod-1");

        assertThat(result).isNotNull();
        assertThat(result.product()).isEqualTo(PRODUCT_1);
        assertThat(result.reviews()).isEmpty();
    }

    // =========================================================================
    // allProductsWithReviews()
    // =========================================================================

    @Test
    @DisplayName("allProductsWithReviews() fetches reviews for each product")
    void allProductsWithReviews_callsReviewsClientForEachProduct() {
        when(productsClient.fetchAllProducts()).thenReturn(List.of(PRODUCT_1, PRODUCT_2));
        when(reviewsClient.fetchReviewsByProduct("prod-1")).thenReturn(REVIEWS_PROD_1);
        when(reviewsClient.fetchReviewsByProduct("prod-2")).thenReturn(List.of());

        List<ProductWithReviews> result = resolver.allProductsWithReviews();

        assertThat(result).hasSize(2);

        // First composite: prod-1 with its reviews
        ProductWithReviews first = result.stream()
                .filter(p -> p.product().id().equals("prod-1"))
                .findFirst().orElseThrow();
        assertThat(first.reviews()).hasSize(2);

        // Second composite: prod-2 with no reviews
        ProductWithReviews second = result.stream()
                .filter(p -> p.product().id().equals("prod-2"))
                .findFirst().orElseThrow();
        assertThat(second.reviews()).isEmpty();

        // Reviews client called once per product
        verify(reviewsClient, times(1)).fetchReviewsByProduct("prod-1");
        verify(reviewsClient, times(1)).fetchReviewsByProduct("prod-2");
    }
}
