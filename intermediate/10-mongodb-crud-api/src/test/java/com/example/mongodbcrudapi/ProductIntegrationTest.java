package com.example.mongodbcrudapi;

import com.example.mongodbcrudapi.domain.Product;
import com.example.mongodbcrudapi.dto.ProductRequest;
import com.example.mongodbcrudapi.repository.ProductRepository;
import com.example.mongodbcrudapi.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the MongoDB CRUD API.
 *
 * <p>This test class verifies end-to-end behaviour from the HTTP layer through
 * the service and repository layers down to a real MongoDB instance managed by
 * Testcontainers. Key aspects:
 *
 * <ul>
 *   <li>{@link SpringBootTest.WebEnvironment#RANDOM_PORT} starts a real embedded
 *       servlet container on a random port, so the full Spring MVC filter chain,
 *       serialisation stack, and exception handlers are active — identical to
 *       production runtime.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up a MongoDB Docker
 *       container for the duration of the test class. The container is shared
 *       across all test methods to avoid the overhead of restarting MongoDB
 *       for each individual test.</li>
 *   <li>{@link DynamicPropertySource} overrides the MongoDB connection URI in
 *       the Spring {@code Environment} before the application context is created,
 *       so Spring Boot connects to the Testcontainers-managed MongoDB container
 *       instead of any locally installed MongoDB instance.</li>
 *   <li>The {@code "test"} profile activates {@code application-test.yml} which
 *       reduces logging noise during test runs.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Product integration tests (MongoDB + REST API)")
class ProductIntegrationTest {

    // ── Testcontainers MongoDB container ──────────────────────────────────────────

    /**
     * A MongoDB container shared by all tests in this class.
     *
     * <p>{@code static} is crucial: JUnit 5 + Testcontainers reuses a single
     * container instance for the entire test class lifecycle, avoiding the
     * significant overhead of starting/stopping MongoDB for each test method.
     * The container is stopped automatically after the last test finishes.
     *
     * <p>We use the official {@code mongo:7.0} image which matches the version
     * commonly used in production environments.
     */
    @Container
    static final MongoDBContainer MONGODB =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    /**
     * Register the container's dynamic connection URI into the Spring
     * {@link org.springframework.core.env.Environment} before the application
     * context is created.
     *
     * <p>Testcontainers assigns a random host port to the container, so we use
     * {@code getReplicaSetUrl()} which returns the complete connection URI
     * (e.g. {@code mongodb://localhost:32768/test}). Spring Data MongoDB uses
     * the {@code spring.data.mongodb.uri} property when set, which takes
     * precedence over host/port/database individual properties.
     *
     * @param registry the property registry that Spring Boot reads before context startup
     */
    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    }

    // ── Injected Spring beans ─────────────────────────────────────────────────────

    /** The random port chosen by Spring Boot for the embedded servlet container. */
    @LocalServerPort
    int port;

    /**
     * {@link TestRestTemplate} is a convenience wrapper around {@code RestTemplate}
     * designed for integration tests. Unlike a plain RestTemplate, it:
     * <ul>
     *   <li>Does NOT throw exceptions on 4xx/5xx responses (returns the ResponseEntity).</li>
     *   <li>Follows HTTP redirects automatically.</li>
     *   <li>Is pre-configured to connect to the embedded server started by Spring Boot.</li>
     * </ul>
     */
    @Autowired
    TestRestTemplate restTemplate;

    /**
     * Direct access to the service layer allows testing business logic without
     * going through HTTP serialisation, which is faster and less noisy for
     * data-setup steps.
     */
    @Autowired
    ProductService productService;

    /**
     * Direct access to the repository allows test setup and teardown operations
     * (e.g. clearing all documents before each test).
     */
    @Autowired
    ProductRepository productRepository;

    /**
     * Low-level MongoDB access used to inspect raw BSON documents during debugging
     * and to verify data stored in MongoDB at the document level.
     */
    @Autowired
    MongoTemplate mongoTemplate;

    // ── Setup ─────────────────────────────────────────────────────────────────────

    /**
     * Clear the MongoDB collection before each test method.
     *
     * <p>Without this, documents created in one test would be visible to subsequent
     * tests, causing non-deterministic failures (e.g. "findAll" returning more
     * products than expected because a previous test created some).
     */
    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    // ── GET /api/products ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products returns 200 with empty array when collection is empty")
    void getAllProducts_returns200WithEmptyArray_whenCollectionIsEmpty() {
        // When: list all products via HTTP (collection was cleared in setUp)
        ResponseEntity<Product[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products",
                Product[].class
        );

        // Then: 200 OK with an empty array
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("GET /api/products returns 200 with all products when documents exist")
    void getAllProducts_returns200WithAllProducts() {
        // Given: two products in the database
        productService.create(new ProductRequest("Laptop", "Pro laptop", new BigDecimal("999.99"), "electronics", 10));
        productService.create(new ProductRequest("Mouse", "Wireless mouse", new BigDecimal("29.99"), "electronics", 100));

        // When
        ResponseEntity<Product[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products",
                Product[].class
        );

        // Then: 200 OK with both products
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(2);
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/{id} returns 200 with the product when found")
    void getProductById_returns200WithProduct_whenFound() {
        // Given: a product in the database
        Product created = productService.create(
                new ProductRequest("Integration Laptop", "desc", new BigDecimal("799.99"), "electronics", 5));

        // When: retrieve the product by its MongoDB ObjectId
        ResponseEntity<Product> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products/" + created.getId(),
                Product.class
        );

        // Then: 200 OK and the product data matches
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Integration Laptop");
        assertThat(response.getBody().getPrice()).isEqualByComparingTo("799.99");
    }

    @Test
    @DisplayName("GET /api/products/{id} returns 404 when the product does not exist")
    void getProductById_returns404_whenNotFound() {
        // When: request a product with a valid-format but non-existent ObjectId
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products/507f1f77bcf86cd799439999",
                String.class
        );

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/products/search ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/search?name=... returns matching products case-insensitively")
    void searchByName_returnsMatchingProducts_caseInsensitive() {
        // Given: two products, one whose name contains "laptop" (various cases)
        productService.create(new ProductRequest("Gaming Laptop", "desc", new BigDecimal("1499.99"), "electronics", 3));
        productService.create(new ProductRequest("Wireless Mouse", "desc", new BigDecimal("39.99"), "electronics", 50));

        // When: search for "LAPTOP" (uppercase)
        ResponseEntity<Product[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products/search?name=LAPTOP",
                Product[].class
        );

        // Then: only the laptop is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody()[0].getName()).isEqualTo("Gaming Laptop");
    }

    // ── GET /api/products/category/{category} ────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/category/{category} returns products in the category")
    void getByCategory_returnsProductsInCategory() {
        // Given: one electronics and one books product
        productService.create(new ProductRequest("Laptop", "desc", new BigDecimal("999.99"), "electronics", 5));
        productService.create(new ProductRequest("Java Book", "desc", new BigDecimal("49.99"), "books", 20));

        // When: request all electronics
        ResponseEntity<Product[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products/category/electronics",
                Product[].class
        );

        // Then: only the electronics product is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody()[0].getCategory()).isEqualTo("electronics");
    }

    // ── GET /api/products/max-price ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/max-price?price=... returns products within budget")
    void getByMaxPrice_returnsProductsWithinBudget() {
        // Given: a cheap product and an expensive product
        productService.create(new ProductRequest("USB Cable", "desc", new BigDecimal("9.99"), "accessories", 200));
        productService.create(new ProductRequest("Laptop", "desc", new BigDecimal("999.99"), "electronics", 10));

        // When: filter by max price of 50.00
        ResponseEntity<Product[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products/max-price?price=50.00",
                Product[].class
        );

        // Then: only the cheap product is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody()[0].getName()).isEqualTo("USB Cable");
    }

    // ── GET /api/products/low-stock ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/low-stock?threshold=... returns low-stock products")
    void getLowStockProducts_returnsProductsBelowThreshold() {
        // Given: a low-stock and a well-stocked product
        productService.create(new ProductRequest("Rare Collectible", "desc", new BigDecimal("299.99"), "collectibles", 2));
        productService.create(new ProductRequest("Common Widget", "desc", new BigDecimal("4.99"), "misc", 500));

        // When: request products with fewer than 10 units
        ResponseEntity<Product[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products/low-stock?threshold=10",
                Product[].class
        );

        // Then: only the low-stock product is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody()[0].getName()).isEqualTo("Rare Collectible");
    }

    // ── POST /api/products ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/products returns 201 with the created product including generated ID")
    void createProduct_returns201WithProduct() {
        // Given: a valid product request
        ProductRequest request = new ProductRequest(
                "New Widget", "A shiny new widget", new BigDecimal("19.99"), "widgets", 100);

        // When: POST the request via HTTP
        ResponseEntity<Product> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/products",
                request,
                Product.class
        );

        // Then: 201 Created and the product has a MongoDB-generated ID
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull().isNotEmpty();
        assertThat(response.getBody().getName()).isEqualTo("New Widget");
        assertThat(response.getBody().getPrice()).isEqualByComparingTo("19.99");
        assertThat(response.getBody().getStockQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("POST /api/products returns 400 when the name is blank")
    void createProduct_returns400_whenNameIsBlank() {
        // Given: an invalid request with a blank name
        ProductRequest invalid = new ProductRequest("", "desc", new BigDecimal("9.99"), "test", 10);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/products",
                invalid,
                String.class
        );

        // Then: 400 Bad Request (Bean Validation rejects the blank name)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/products returns 400 when the price is zero")
    void createProduct_returns400_whenPriceIsZero() {
        // Given: a request with price = 0 (violates @DecimalMin("0.01"))
        ProductRequest invalid = new ProductRequest("Item", "desc", BigDecimal.ZERO, "test", 10);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/products",
                invalid,
                String.class
        );

        // Then: 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/products returns 400 when stock quantity is negative")
    void createProduct_returns400_whenStockIsNegative() {
        // Given: a request with negative stock (violates @Min(0))
        ProductRequest invalid = new ProductRequest("Item", "desc", new BigDecimal("9.99"), "test", -1);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/products",
                invalid,
                String.class
        );

        // Then: 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── PUT /api/products/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/products/{id} returns 200 with the updated product")
    void updateProduct_returns200WithUpdatedProduct() {
        // Given: an existing product
        Product created = productService.create(
                new ProductRequest("Old Name", "old desc", new BigDecimal("50.00"), "misc", 20));

        ProductRequest updateRequest = new ProductRequest(
                "New Name", "new desc", new BigDecimal("75.00"), "misc", 15);

        // When: PUT the update via HTTP
        ResponseEntity<Product> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/products/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest),
                Product.class
        );

        // Then: 200 OK with updated values
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("New Name");
        assertThat(response.getBody().getPrice()).isEqualByComparingTo("75.00");
        assertThat(response.getBody().getStockQuantity()).isEqualTo(15);
        // The ID must be preserved (not replaced)
        assertThat(response.getBody().getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("PUT /api/products/{id} returns 404 when the product does not exist")
    void updateProduct_returns404_whenNotFound() {
        // When: attempt to update a non-existent product
        ProductRequest request = new ProductRequest("x", "x", new BigDecimal("1.00"), "x", 1);
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/products/507f1f77bcf86cd799439999",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                String.class
        );

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE /api/products/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/products/{id} returns 204 and the product is no longer retrievable")
    void deleteProduct_returns204AndProductGone() {
        // Given: a product to delete
        Product created = productService.create(
                new ProductRequest("Temp Product", "to be deleted", new BigDecimal("5.00"), "test", 1));

        // When: delete via HTTP
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/products/" + created.getId(),
                HttpMethod.DELETE,
                null,
                Void.class
        );

        // Then: 204 No Content is returned
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // And: a subsequent GET returns 404 (product is gone from MongoDB)
        ResponseEntity<String> getResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products/" + created.getId(),
                String.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE /api/products/{id} returns 404 when the product does not exist")
    void deleteProduct_returns404_whenNotFound() {
        // When: delete a non-existent product
        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/products/507f1f77bcf86cd799439999",
                HttpMethod.DELETE,
                null,
                Void.class
        );

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Service-layer MongoDB verification ────────────────────────────────────────

    @Test
    @DisplayName("Created product is persisted and retrievable from MongoDB")
    void createdProduct_isPersistedInMongoDB() {
        // Given: a product created via the service layer
        Product created = productService.create(
                new ProductRequest("Persisted Item", "verify in mongo", new BigDecimal("42.00"), "test", 7));

        // When: retrieve directly from MongoDB via the service
        Optional<Product> found = productService.findById(created.getId());

        // Then: the product exists in MongoDB with the correct data
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Persisted Item");
        assertThat(found.get().getPrice()).isEqualByComparingTo("42.00");
        assertThat(found.get().getStockQuantity()).isEqualTo(7);
        // MongoDB should have assigned a non-null ObjectId string
        assertThat(found.get().getId()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Updated product reflects new values in MongoDB")
    void updatedProduct_reflectsNewValuesInMongoDB() {
        // Given: a product with initial values
        Product created = productService.create(
                new ProductRequest("Before Update", "old desc", new BigDecimal("30.00"), "test", 10));

        // When: update the product
        productService.update(created.getId(),
                new ProductRequest("After Update", "new desc", new BigDecimal("45.00"), "test", 5));

        // Then: re-fetching from MongoDB shows the new values
        Optional<Product> updated = productService.findById(created.getId());
        assertThat(updated).isPresent();
        assertThat(updated.get().getName()).isEqualTo("After Update");
        assertThat(updated.get().getPrice()).isEqualByComparingTo("45.00");
        assertThat(updated.get().getStockQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Deleted product is no longer present in MongoDB")
    void deletedProduct_isRemovedFromMongoDB() {
        // Given: a product in MongoDB
        Product created = productService.create(
                new ProductRequest("Delete Me", "desc", new BigDecimal("1.00"), "test", 1));
        String id = created.getId();

        // When: delete the product
        boolean deleted = productService.deleteById(id);

        // Then: the product is gone from MongoDB
        assertThat(deleted).isTrue();
        assertThat(productService.findById(id)).isEmpty();
        assertThat(productRepository.count()).isZero();
    }

    @Test
    @DisplayName("findByCategory returns only products in the specified category from MongoDB")
    void findByCategory_returnsOnlyCategoryProductsFromMongoDB() {
        // Given: products in different categories
        productService.create(new ProductRequest("Phone", "desc", new BigDecimal("599.99"), "electronics", 20));
        productService.create(new ProductRequest("Headphones", "desc", new BigDecimal("149.99"), "electronics", 30));
        productService.create(new ProductRequest("Novel", "desc", new BigDecimal("14.99"), "books", 50));

        // When: fetch all electronics
        List<Product> electronics = productService.findByCategory("electronics");

        // Then: only the two electronics products are returned
        assertThat(electronics).hasSize(2);
        assertThat(electronics).allMatch(p -> "electronics".equals(p.getCategory()));
    }
}
