package com.example.gateway.resolver;

import com.example.gateway.client.ProductsClient;
import com.example.gateway.client.ReviewsClient;
import com.example.gateway.model.Product;
import com.example.gateway.model.ProductWithReviews;
import com.example.gateway.model.Review;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL resolver for the gateway's unified supergraph {@code Query} type.
 *
 * <p>This is the central component of the federation gateway. It maps every
 * field declared in the supergraph {@code schema.graphqls} to a Java method
 * and delegates each query to the appropriate downstream subgraph service via
 * the injected clients:
 *
 * <ul>
 *   <li>{@link ProductsClient} — calls the products-service over HTTP GraphQL</li>
 *   <li>{@link ReviewsClient} — calls the reviews-service over HTTP GraphQL</li>
 * </ul>
 *
 * <h2>Federation data flow</h2>
 * <pre>
 *  Client
 *    │  POST /graphql  (supergraph query)
 *    ▼
 *  Gateway (this class)
 *    ├── ProductsClient ──► products-service:8081/graphql
 *    └── ReviewsClient  ──► reviews-service:8082/graphql
 *    │
 *    └── merges results → returns unified response to client
 * </pre>
 *
 * <h2>Key method: productWithReviews</h2>
 * <p>The {@link #productWithReviews(String)} method is the clearest example
 * of federation value: it stitches data from <em>both</em> subgraphs by
 * calling them independently and merging the results before returning a
 * single composite object to the client.
 */
@Controller
public class GatewayQueryResolver {

    private final ProductsClient productsClient;
    private final ReviewsClient reviewsClient;

    /**
     * Constructor injection makes dependencies explicit and eases unit testing.
     */
    public GatewayQueryResolver(ProductsClient productsClient,
                                ReviewsClient reviewsClient) {
        this.productsClient = productsClient;
        this.reviewsClient = reviewsClient;
    }

    // =========================================================================
    // Product queries — delegated to products-service
    // =========================================================================

    /**
     * Resolves {@code Query.products} — returns all products.
     *
     * <p>Delegates to {@link ProductsClient#fetchAllProducts()}.
     */
    @QueryMapping
    public List<Product> products() {
        return productsClient.fetchAllProducts();
    }

    /**
     * Resolves {@code Query.product(id: ID!)} — returns a single product by ID.
     *
     * <p>Returns {@code null} if the product is not found (maps to GraphQL null).
     */
    @QueryMapping
    public Product product(@Argument String id) {
        return productsClient.fetchProductById(id).orElse(null);
    }

    /**
     * Resolves {@code Query.productsByCategory(category: String!)} —
     * returns products filtered by category.
     */
    @QueryMapping
    public List<Product> productsByCategory(@Argument String category) {
        return productsClient.fetchProductsByCategory(category);
    }

    /**
     * Resolves {@code Query.productsInStock} — returns only in-stock products.
     */
    @QueryMapping
    public List<Product> productsInStock() {
        return productsClient.fetchProductsInStock();
    }

    // =========================================================================
    // Review queries — delegated to reviews-service
    // =========================================================================

    /**
     * Resolves {@code Query.reviews} — returns all reviews.
     *
     * <p>Delegates to {@link ReviewsClient#fetchAllReviews()}.
     */
    @QueryMapping
    public List<Review> reviews() {
        return reviewsClient.fetchAllReviews();
    }

    /**
     * Resolves {@code Query.review(id: ID!)} — returns a single review by ID.
     */
    @QueryMapping
    public Review review(@Argument String id) {
        return reviewsClient.fetchReviewById(id).orElse(null);
    }

    /**
     * Resolves {@code Query.reviewsByProduct(productId: ID!)} —
     * returns all reviews for a specific product.
     */
    @QueryMapping
    public List<Review> reviewsByProduct(@Argument String productId) {
        return reviewsClient.fetchReviewsByProduct(productId);
    }

    /**
     * Resolves {@code Query.reviewsByMinRating(minRating: Int!)} —
     * returns reviews with rating >= minRating.
     */
    @QueryMapping
    public List<Review> reviewsByMinRating(@Argument int minRating) {
        return reviewsClient.fetchReviewsByMinRating(minRating);
    }

    // =========================================================================
    // Federated (stitched) queries — combine data from both subgraphs
    // =========================================================================

    /**
     * Resolves {@code Query.productWithReviews(productId: ID!)} — THE core
     * federation operation.
     *
     * <p>This method demonstrates the central value proposition of GraphQL
     * Federation: the client makes a single request to the gateway and receives
     * data that was assembled from two independent downstream services.
     *
     * <p>Implementation steps:
     * <ol>
     *   <li>Call the products-service to fetch the Product by ID.</li>
     *   <li>Call the reviews-service to fetch all Reviews for that product ID.</li>
     *   <li>Combine them into a {@link ProductWithReviews} composite object.</li>
     *   <li>Return the composite object — GraphQL serialises it into a single
     *       unified response for the client.</li>
     * </ol>
     *
     * <p>GraphQL query (from the client's perspective):
     * <pre>
     * query {
     *   productWithReviews(productId: "prod-1") {
     *     product { id name price category }
     *     reviews { id author body rating }
     *   }
     * }
     * </pre>
     *
     * @param productId the product to fetch along with its reviews
     * @return a composite object containing the product and its reviews,
     *         or {@code null} if the product is not found
     */
    @QueryMapping
    public ProductWithReviews productWithReviews(@Argument String productId) {
        // Step 1: Fetch the product from products-service
        Product product = productsClient.fetchProductById(productId).orElse(null);

        // If the product doesn't exist we cannot build a meaningful composite —
        // return null and let GraphQL handle it (the schema field is nullable).
        if (product == null) {
            return null;
        }

        // Step 2: Fetch the reviews for this product from reviews-service.
        // Note: this is a sequential (N+1) call. In a production system you
        // would use DataLoader batching to fan-out these calls in parallel and
        // avoid N+1 problems. For this educational project sequential calls
        // make the data flow easy to understand.
        List<Review> reviews = reviewsClient.fetchReviewsByProduct(productId);

        // Step 3: Assemble and return the composite DTO
        return new ProductWithReviews(product, reviews);
    }

    /**
     * Resolves {@code Query.allProductsWithReviews} — fetches every product
     * together with its reviews.
     *
     * <p>This produces a complete cross-service dataset in one gateway request.
     * Each product makes one call to the reviews-service for its own reviews.
     * In production this should be batched with DataLoader.
     *
     * @return list of all products with their respective reviews
     */
    @QueryMapping
    public List<ProductWithReviews> allProductsWithReviews() {
        // Fetch all products first
        List<Product> products = productsClient.fetchAllProducts();

        // For each product, fetch its reviews and assemble a composite object.
        // Stream map is clear and concise for this educational example.
        return products.stream()
                .map(product -> {
                    List<Review> reviews = reviewsClient.fetchReviewsByProduct(product.id());
                    return new ProductWithReviews(product, reviews);
                })
                .toList();
    }
}
