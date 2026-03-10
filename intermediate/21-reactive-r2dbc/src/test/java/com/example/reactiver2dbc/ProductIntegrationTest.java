package com.example.reactiver2dbc;

import com.example.reactiver2dbc.domain.Product;
import com.example.reactiver2dbc.dto.ProductRequest;
import com.example.reactiver2dbc.repository.ProductRepository;
import com.example.reactiver2dbc.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the Reactive R2DBC Product API.
 *
 * <p>This test class verifies end-to-end behaviour from the HTTP layer through the
 * service and repository layers down to a real PostgreSQL instance managed by
 * Testcontainers.
 *
 * <p>Key aspects:
 * <ul>
 *   <li>{@link SpringBootTest.WebEnvironment#RANDOM_PORT} starts a real embedded
 *       Netty server on a random port. The full WebFlux filter chain, exception
 *       handlers, and serialisation stack are active — identical to production.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up a PostgreSQL Docker
 *       container shared across all test methods to avoid the overhead of restarting
 *       PostgreSQL for each individual test.</li>
 *   <li>{@link DynamicPropertySource} injects the container's JDBC and R2DBC URLs
 *       into the Spring {@link org.springframework.core.env.Environment} before the
 *       application context is created. This overrides the default localhost settings
 *       so both Flyway (JDBC) and the app (R2DBC) connect to the container.</li>
 *   <li>{@link WebTestClient} is the reactive-aware HTTP test client from Spring
 *       WebFlux. Unlike {@code TestRestTemplate} (which blocks), WebTestClient
 *       integrates naturally with the reactive pipeline and provides a fluent
 *       assertion API.</li>
 *   <li>The {@code "integration-test"} profile activates separate logging config
 *       to reduce test output noise.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Product integration tests (PostgreSQL R2DBC + Reactive WebFlux API)")
class ProductIntegrationTest {

    // ── Testcontainers PostgreSQL container ───────────────────────────────────────

    /**
     * A PostgreSQL 16 container shared by all tests in this class.
     *
     * <p>{@code static} is crucial: JUnit 5 + Testcontainers reuses a single container
     * for the entire test class lifecycle, avoiding the significant overhead of
     * starting/stopping PostgreSQL for each test method.
     *
     * <p>We use the official {@code postgres:16-alpine} image. The Alpine variant is
     * smaller and starts faster than the full Debian-based image, which is ideal for CI.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("testproductsdb")
                    .withUsername("testuser")
                    .withPassword("testpass");

    /**
     * Register the container's dynamic connection details into the Spring
     * {@link org.springframework.core.env.Environment} before the application
     * context is created.
     *
     * <p>Two connection URLs are required:
     * <ul>
     *   <li><strong>R2DBC URL</strong> ({@code spring.r2dbc.url}) — the reactive
     *       driver URL used by Spring Data R2DBC for all runtime queries.</li>
     *   <li><strong>JDBC URL</strong> ({@code spring.flyway.url}) — used by Flyway
     *       to apply schema migrations synchronously at startup. Flyway requires JDBC.</li>
     * </ul>
     *
     * @param registry the property registry Spring Boot reads before context startup
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        // R2DBC URL: replace the "jdbc:" scheme with "r2dbc:" to get the reactive URL
        registry.add("spring.r2dbc.url", () ->
                POSTGRES.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        // Flyway JDBC URL (Flyway needs JDBC, not R2DBC)
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    // ── Injected Spring beans ─────────────────────────────────────────────────────

    /**
     * {@link WebTestClient} wired to the embedded Netty server started by
     * {@link SpringBootTest}. Spring Boot auto-configures this bean when
     * {@code WebEnvironment.RANDOM_PORT} is used.
     */
    @Autowired
    WebTestClient webTestClient;

    /**
     * Direct access to the service layer for test data setup, bypassing HTTP
     * serialisation overhead for the "Given" steps.
     */
    @Autowired
    ProductService productService;

    /**
     * Direct access to the repository for teardown (clearing all rows before
     * each test to ensure test isolation).
     */
    @Autowired
    ProductRepository productRepository;

    // ── Setup ─────────────────────────────────────────────────────────────────────

    /**
     * Clear the products table before each test to ensure test isolation.
     *
     * <p>Without this, rows created in one test would affect subsequent tests,
     * causing non-deterministic failures (e.g., count assertions failing because
     * a previous test inserted extra rows).
     *
     * <p>{@code .block()} waits for the reactive delete to complete before the test
     * method starts — necessary because {@code @BeforeEach} is not reactive-aware.
     */
    @BeforeEach
    void setUp() {
        productRepository.deleteAll().block();
    }

    // ── GET /api/products ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products returns 200 with empty array when table is empty")
    void getAllProducts_returns200WithEmptyArray_whenTableIsEmpty() {
        webTestClient.get()
                .uri("/api/products")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class).hasSize(0);
    }

    @Test
    @DisplayName("GET /api/products returns 200 with all products when rows exist")
    void getAllProducts_returns200WithAllProducts() {
        // Given: two products in the database
        productService.create(new ProductRequest("Keyboard", "Desc", new BigDecimal("129.99"), "electronics", 10, true)).block();
        productService.create(new ProductRequest("Mouse", "Desc", new BigDecimal("59.99"), "electronics", 20, true)).block();

        // When / Then
        webTestClient.get()
                .uri("/api/products")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class).hasSize(2);
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/{id} returns 200 with the product when found")
    void getProductById_returns200_whenFound() {
        // Given: a persisted product
        Product created = productService.create(
                new ProductRequest("Find Me", "Body", new BigDecimal("49.99"), "electronics", 5, true)).block();

        // When / Then
        webTestClient.get()
                .uri("/api/products/{id}", created.getId())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Product.class)
                .value(p -> {
                    assertThat(p.getName()).isEqualTo("Find Me");
                    assertThat(p.getId()).isEqualTo(created.getId());
                });
    }

    @Test
    @DisplayName("GET /api/products/{id} returns 404 when the product does not exist")
    void getProductById_returns404_whenNotFound() {
        webTestClient.get()
                .uri("/api/products/999999")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── GET /api/products/category/{category} ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/category/{category} returns products in the category")
    void getByCategory_returnsProductsInCategory() {
        // Given: one electronics and one furniture product
        productService.create(new ProductRequest("Keyboard", "Desc", new BigDecimal("99.99"), "electronics", 10, true)).block();
        productService.create(new ProductRequest("Chair", "Desc", new BigDecimal("299.99"), "furniture", 5, true)).block();

        // When / Then: only the electronics product is returned
        webTestClient.get()
                .uri("/api/products/category/electronics")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getCategory()).isEqualTo("electronics"));
    }

    // ── GET /api/products/active ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/active returns only active products")
    void getActive_returnsOnlyActiveProducts() {
        // Given: one active and one inactive product
        productService.create(new ProductRequest("Active Product", "Desc", new BigDecimal("10.00"), "misc", 5, true)).block();
        productService.create(new ProductRequest("Inactive Product", "Desc", new BigDecimal("10.00"), "misc", 0, false)).block();

        // When / Then: only the active product is returned
        webTestClient.get()
                .uri("/api/products/active")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).isActive()).isTrue());
    }

    // ── GET /api/products/search?keyword=... ──────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/search?keyword=... returns matching products case-insensitively")
    void searchByName_returnsMatchingProducts_caseInsensitive() {
        // Given: two products, one whose name contains "keyboard"
        productService.create(new ProductRequest("Wireless Keyboard", "Desc", new BigDecimal("99.99"), "electronics", 10, true)).block();
        productService.create(new ProductRequest("Ergonomic Mouse", "Desc", new BigDecimal("59.99"), "electronics", 20, true)).block();

        // When: search for "KEYBOARD" (uppercase)
        webTestClient.get()
                .uri(uri -> uri.path("/api/products/search").queryParam("keyword", "KEYBOARD").build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getName()).containsIgnoringCase("keyboard"));
    }

    // ── GET /api/products/price-range?min=...&max=... ─────────────────────────────

    @Test
    @DisplayName("GET /api/products/price-range returns products within the price range")
    void getByPriceRange_returnsProductsInRange() {
        // Given: products at different price points
        productService.create(new ProductRequest("Cheap Item", "Desc", new BigDecimal("9.99"), "misc", 100, true)).block();
        productService.create(new ProductRequest("Mid Item", "Desc", new BigDecimal("50.00"), "misc", 50, true)).block();
        productService.create(new ProductRequest("Expensive Item", "Desc", new BigDecimal("999.99"), "misc", 5, true)).block();

        // When: filter between 10 and 100
        webTestClient.get()
                .uri(uri -> uri.path("/api/products/price-range")
                        .queryParam("min", "10.00")
                        .queryParam("max", "100.00")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getName()).isEqualTo("Mid Item"));
    }

    // ── GET /api/products/low-stock ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/low-stock returns products at or below the threshold")
    void getLowStock_returnsLowStockProducts() {
        // Given: products with varying stock levels
        productService.create(new ProductRequest("Almost Gone", "Desc", new BigDecimal("10.00"), "misc", 2, true)).block();
        productService.create(new ProductRequest("Well Stocked", "Desc", new BigDecimal("10.00"), "misc", 100, true)).block();

        // When: query low-stock with threshold=5 (default)
        webTestClient.get()
                .uri("/api/products/low-stock")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getStockQuantity()).isLessThanOrEqualTo(5));
    }

    // ── GET /api/products/category/{category}/count ───────────────────────────────

    @Test
    @DisplayName("GET /api/products/category/{category}/count returns the product count")
    void countByCategory_returnsCorrectCount() {
        // Given: three electronics products
        productService.create(new ProductRequest("P1", "D", new BigDecimal("10"), "electronics", 1, true)).block();
        productService.create(new ProductRequest("P2", "D", new BigDecimal("10"), "electronics", 1, true)).block();
        productService.create(new ProductRequest("P3", "D", new BigDecimal("10"), "electronics", 1, true)).block();

        // When / Then: the count is 3
        webTestClient.get()
                .uri("/api/products/category/electronics/count")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(3L);
    }

    // ── POST /api/products ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/products returns 201 with the created product including generated ID")
    void createProduct_returns201WithCreatedProduct() {
        // Given: a valid product request
        ProductRequest request = new ProductRequest(
                "New Product", "Rich description", new BigDecimal("79.99"), "electronics", 25, true);

        // When / Then
        webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Product.class)
                .value(saved -> {
                    assertThat(saved.getId()).isNotNull().isPositive();
                    assertThat(saved.getName()).isEqualTo("New Product");
                    assertThat(saved.getCategory()).isEqualTo("electronics");
                    assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("79.99"));
                    assertThat(saved.isActive()).isTrue();
                    assertThat(saved.getCreatedAt()).isNotNull();
                    assertThat(saved.getUpdatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("POST /api/products returns 400 when the name is blank")
    void createProduct_returns400_whenNameIsBlank() {
        ProductRequest invalid = new ProductRequest("", "Desc", new BigDecimal("10.00"), "misc", 5, true);

        webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/products returns 400 when the price is null")
    void createProduct_returns400_whenPriceIsNull() {
        // Build JSON with null price to trigger @NotNull validation
        String invalidJson = "{\"name\":\"Item\",\"description\":\"Desc\",\"price\":null,\"category\":\"misc\",\"stockQuantity\":5,\"active\":true}";

        webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidJson)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/products returns 400 when the category is blank")
    void createProduct_returns400_whenCategoryIsBlank() {
        ProductRequest invalid = new ProductRequest("Name", "Desc", new BigDecimal("10.00"), "", 5, true);

        webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── PUT /api/products/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/products/{id} returns 200 with the updated product")
    void updateProduct_returns200WithUpdatedProduct() {
        // Given: an existing product
        Product created = productService.create(
                new ProductRequest("Old Name", "Old desc", new BigDecimal("49.99"), "misc", 10, false)).block();

        ProductRequest updateRequest = new ProductRequest(
                "New Name", "New desc", new BigDecimal("99.99"), "electronics", 5, true);

        // When / Then
        webTestClient.put()
                .uri("/api/products/{id}", created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Product.class)
                .value(updated -> {
                    assertThat(updated.getName()).isEqualTo("New Name");
                    assertThat(updated.getDescription()).isEqualTo("New desc");
                    assertThat(updated.getPrice()).isEqualByComparingTo(new BigDecimal("99.99"));
                    assertThat(updated.getCategory()).isEqualTo("electronics");
                    assertThat(updated.getStockQuantity()).isEqualTo(5);
                    assertThat(updated.isActive()).isTrue();
                    // The ID must be preserved
                    assertThat(updated.getId()).isEqualTo(created.getId());
                });
    }

    @Test
    @DisplayName("PUT /api/products/{id} returns 404 when the product does not exist")
    void updateProduct_returns404_whenNotFound() {
        ProductRequest request = new ProductRequest("Name", "Desc", new BigDecimal("10.00"), "misc", 1, true);

        webTestClient.put()
                .uri("/api/products/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── DELETE /api/products/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/products/{id} returns 204 and the product is no longer retrievable")
    void deleteProduct_returns204AndProductGone() {
        // Given: a persisted product
        Product created = productService.create(
                new ProductRequest("Delete Me", "Desc", new BigDecimal("5.00"), "misc", 1, true)).block();

        // When: delete via HTTP
        webTestClient.delete()
                .uri("/api/products/{id}", created.getId())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NO_CONTENT);

        // Then: a subsequent GET returns 404 (product is gone from PostgreSQL)
        webTestClient.get()
                .uri("/api/products/{id}", created.getId())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("DELETE /api/products/{id} returns 404 when the product does not exist")
    void deleteProduct_returns404_whenNotFound() {
        webTestClient.delete()
                .uri("/api/products/999999")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── Service-layer PostgreSQL verification ─────────────────────────────────────

    @Test
    @DisplayName("Created product is persisted and retrievable from PostgreSQL")
    void createdProduct_isPersistedInPostgres() {
        // Given: a product created via the service layer
        Product created = productService.create(
                new ProductRequest("Persisted Product", "Desc", new BigDecimal("19.99"), "test", 10, true)).block();

        // When: retrieve directly from PostgreSQL via the service
        Product found = productService.findById(created.getId()).block();

        // Then: the product exists with the correct data
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Persisted Product");
        assertThat(found.isActive()).isTrue();
        assertThat(found.getId()).isNotNull().isPositive();
        // Audit timestamps must be set by @CreatedDate and @LastModifiedDate
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Updated product reflects new values in PostgreSQL")
    void updatedProduct_reflectsNewValuesInPostgres() {
        // Given: a product with initial values
        Product created = productService.create(
                new ProductRequest("Before Update", "Old body", new BigDecimal("10.00"), "test", 5, false)).block();

        // When: update the product
        productService.update(created.getId(),
                new ProductRequest("After Update", "New body", new BigDecimal("20.00"), "test", 10, true)).block();

        // Then: re-fetching from PostgreSQL shows the new values
        Product updated = productService.findById(created.getId()).block();
        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("After Update");
        assertThat(updated.getDescription()).isEqualTo("New body");
        assertThat(updated.isActive()).isTrue();
    }

    @Test
    @DisplayName("Deleted product is no longer present in PostgreSQL")
    void deletedProduct_isRemovedFromPostgres() {
        // Given: a product in PostgreSQL
        Product created = productService.create(
                new ProductRequest("Temp", "Desc", new BigDecimal("1.00"), "test", 1, true)).block();
        Long id = created.getId();

        // When: delete the product
        Boolean deleted = productService.deleteById(id).block();

        // Then: the product is gone
        assertThat(deleted).isTrue();
        assertThat(productService.findById(id).block()).isNull();
        assertThat(productRepository.count().block()).isZero();
    }
}
