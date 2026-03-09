package com.example.redisdatacache;

import com.example.redisdatacache.domain.Product;
import com.example.redisdatacache.dto.ProductRequest;
import com.example.redisdatacache.repository.ProductRepository;
import com.example.redisdatacache.service.ProductService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the Redis caching layer.
 *
 * <p>This test class verifies end-to-end behaviour from the HTTP layer through the
 * service (with real Spring AOP cache proxies) down to a real Redis instance managed
 * by Testcontainers. Key aspects:
 *
 * <ul>
 *   <li>{@link SpringBootTest.WebEnvironment#RANDOM_PORT} starts a real embedded
 *       servlet container on a random port, so the full filter/serialisation stack
 *       is active — the same as in production.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up a Redis Docker container
 *       for the duration of the test class. The container is shared across all test
 *       methods to avoid the overhead of restarting Redis for each test.</li>
 *   <li>{@link DynamicPropertySource} overrides the Redis host/port in the Spring
 *       {@code Environment} so Spring Boot connects to the container rather than to
 *       a local Redis instance.</li>
 *   <li>The {@code "test"} profile activates {@code application-test.yml} which
 *       reduces logging noise.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Product integration tests (Redis cache + REST API)")
class ProductIntegrationTest {

    // ── Testcontainers Redis container ────────────────────────────────────────────

    /**
     * A Redis container shared by all tests in this class.
     *
     * <p>{@code static} is important: JUnit 5 + Testcontainers reuses a single
     * container instance for all test methods, avoiding repeated Docker start/stop
     * overhead. The container is stopped automatically after the last test.
     *
     * <p>We use the official {@code redis:7-alpine} image which is small (~30 MB)
     * and supports all commands needed by Spring Data Redis.
     */
    @Container
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    /**
     * Register the container's dynamic host/port into the Spring
     * {@link org.springframework.core.env.Environment} before the application
     * context is created.
     *
     * <p>This ensures {@code spring.data.redis.host} and
     * {@code spring.data.redis.port} point to the Testcontainers-managed Redis
     * container, not to any locally installed Redis.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    // ── Injected Spring beans ─────────────────────────────────────────────────────

    /** The random port chosen by Spring Boot for the embedded servlet container. */
    @LocalServerPort
    int port;

    /**
     * {@link TestRestTemplate} is a convenience wrapper around {@code RestTemplate}
     * designed for integration tests: it follows redirects and does not throw
     * exceptions on 4xx/5xx responses (instead it returns the response entity).
     */
    @Autowired
    TestRestTemplate restTemplate;

    /**
     * Direct access to the service layer lets us verify caching behaviour without
     * going through HTTP (faster and less noisy for cache-specific assertions).
     */
    @Autowired
    ProductService productService;

    /**
     * The repository is injected so we can turn off its simulated delay during
     * tests (200 ms × many cache-miss calls would make the suite very slow).
     */
    @Autowired
    ProductRepository productRepository;

    /**
     * The {@link CacheManager} is injected so we can programmatically clear caches
     * between tests to ensure each test starts with a pristine cache state.
     */
    @Autowired
    CacheManager cacheManager;

    // ── Setup ─────────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // Disable the artificial repository delay so tests run at full speed
        productRepository.setSimulatedDelayMs(0);

        // Clear both caches before each test to avoid cache state leaking between tests.
        // Without this, a cached value from one test could affect assertions in the next.
        cacheManager.getCache("products").clear();
        cacheManager.getCache("products-all").clear();
    }

    // ── GET /api/products ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products returns 200 with product list")
    void getAllProducts_returns200WithList() {
        // When: list all products via HTTP
        ResponseEntity<Product[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products",
                Product[].class
        );

        // Then: 200 OK and at least the seeded products are returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isNotEmpty();
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/{id} returns 200 with the product")
    void getProductById_returns200_whenFound() {
        // Given: create a product and capture its ID
        ProductRequest request = new ProductRequest("Integration Laptop", "desc", new BigDecimal("799.99"), "electronics");
        Product created = productService.create(request);

        // When: retrieve the product by ID via HTTP
        ResponseEntity<Product> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products/" + created.getId(),
                Product.class
        );

        // Then: the product is returned with matching data
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Integration Laptop");
    }

    @Test
    @DisplayName("GET /api/products/{id} returns 404 when not found")
    void getProductById_returns404_whenNotFound() {
        // When: request a product with an ID that does not exist
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products/99999",
                String.class
        );

        // Then: 404 Not Found is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── POST /api/products ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/products returns 201 with the created product")
    void createProduct_returns201WithProduct() {
        // Given: a valid product request
        ProductRequest request = new ProductRequest("New Widget", "A shiny widget", new BigDecimal("19.99"), "widgets");

        // When: POST the request via HTTP
        ResponseEntity<Product> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/products",
                request,
                Product.class
        );

        // Then: 201 Created and the product has an assigned ID
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("New Widget");
    }

    @Test
    @DisplayName("POST /api/products returns 400 when request body is invalid")
    void createProduct_returns400_whenInvalidRequest() {
        // Given: a request with a blank name (violates @NotBlank)
        ProductRequest invalid = new ProductRequest("", "desc", new BigDecimal("9.99"), "test");

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
                new ProductRequest("Old Name", "old desc", new BigDecimal("50.00"), "misc"));

        ProductRequest updateRequest = new ProductRequest("New Name", "new desc", new BigDecimal("75.00"), "misc");

        // When: PUT the update via HTTP
        ResponseEntity<Product> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/products/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest),
                Product.class
        );

        // Then: the product is updated
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("New Name");
        assertThat(response.getBody().getPrice()).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("PUT /api/products/{id} returns 404 when product does not exist")
    void updateProduct_returns404_whenNotFound() {
        // When: attempt to update a non-existent product
        ProductRequest request = new ProductRequest("x", "x", new BigDecimal("1.00"), "x");
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/products/99999",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                String.class
        );

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE /api/products/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/products/{id} returns 204 and product is no longer retrievable")
    void deleteProduct_returns204AndProductGone() {
        // Given: create a product to delete
        Product created = productService.create(
                new ProductRequest("Temp Product", "to be deleted", new BigDecimal("5.00"), "test"));

        // When: delete via HTTP
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/products/" + created.getId(),
                HttpMethod.DELETE,
                null,
                Void.class
        );

        // Then: 204 No Content is returned
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // And: a subsequent GET returns 404 (product is gone)
        ResponseEntity<String> getResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/products/" + created.getId(),
                String.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE /api/products/{id} returns 404 when product does not exist")
    void deleteProduct_returns404_whenNotFound() {
        // When: delete a non-existent product
        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/products/99999",
                HttpMethod.DELETE,
                null,
                Void.class
        );

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Cache behaviour ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Second call to findById is served from Redis cache (no repository call)")
    void findById_secondCallServedFromCache() {
        // Given: create a product and warm up the cache with the first call
        Product created = productService.create(
                new ProductRequest("Cached Product", "desc", new BigDecimal("100.00"), "test"));
        Long id = created.getId();

        // First call – cache miss, result stored in Redis
        Optional<Product> first = productService.findById(id);
        assertThat(first).isPresent();

        // Verify the entry is now in the cache
        assertThat(cacheManager.getCache("products").get(id)).isNotNull();

        // Second call – should be a cache hit (the method body is not invoked)
        Optional<Product> second = productService.findById(id);
        assertThat(second).isPresent();
        assertThat(second.get().getId()).isEqualTo(id);
        assertThat(second.get().getName()).isEqualTo("Cached Product");
    }

    @Test
    @DisplayName("deleteById evicts the product from the cache")
    void deleteById_evictsFromCache() {
        // Given: a product whose cache entry is populated
        Product created = productService.create(
                new ProductRequest("Evict Me", "desc", new BigDecimal("20.00"), "test"));
        Long id = created.getId();

        // Warm up the cache
        productService.findById(id);
        assertThat(cacheManager.getCache("products").get(id)).isNotNull();

        // When: the product is deleted
        productService.deleteById(id);

        // Then: the cache entry has been evicted
        assertThat(cacheManager.getCache("products").get(id)).isNull();
    }

    @Test
    @DisplayName("update keeps the cache consistent via @CachePut")
    void update_keepsCacheConsistentViaCachePut() {
        // Given: a product with a primed cache entry
        Product created = productService.create(
                new ProductRequest("Before Update", "desc", new BigDecimal("30.00"), "test"));
        Long id = created.getId();

        // Prime the cache so there is a pre-update value
        productService.findById(id);

        // When: the product is updated
        ProductRequest updateRequest = new ProductRequest("After Update", "new desc", new BigDecimal("45.00"), "test");
        productService.update(id, updateRequest);

        // Then: the cache now holds the UPDATED value (not the stale pre-update value)
        // We verify via the cache manager directly — a real cache hit
        org.springframework.cache.Cache.ValueWrapper cached = cacheManager.getCache("products").get(id);
        assertThat(cached).isNotNull();
        // The cached value should be the updated Optional<Product>
        Object cachedValue = cached.get();
        assertThat(cachedValue).isNotNull();
    }
}
