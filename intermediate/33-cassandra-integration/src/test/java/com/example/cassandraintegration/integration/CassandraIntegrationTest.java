package com.example.cassandraintegration.integration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.example.cassandraintegration.domain.Product;
import com.example.cassandraintegration.repository.ProductRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests using Testcontainers to spin up a real Apache Cassandra instance.
 *
 * <h2>Key Concepts Demonstrated</h2>
 * <ul>
 *   <li>{@code @Testcontainers} — activates the Testcontainers JUnit 5 extension</li>
 *   <li>{@code @Container} — declares a Cassandra Docker container scoped to this test class</li>
 *   <li>{@code @DynamicPropertySource} — dynamically overrides Spring's Cassandra contact-points
 *       and datacenter settings with the values from the running container</li>
 *   <li>{@code @SpringBootTest(webEnvironment = RANDOM_PORT)} with {@code @AutoConfigureMockMvc}
 *       — boots the full application context and allows HTTP-level testing via MockMvc</li>
 * </ul>
 *
 * <h2>Container Lifecycle</h2>
 * <p>The static {@code @Container} field ensures a single Cassandra container is shared
 * across all test methods in this class (class-scoped lifecycle). This avoids the overhead
 * of starting/stopping a container for each test method.</p>
 *
 * <h2>Schema Creation</h2>
 * <p>The application is configured with {@code schema-action: CREATE_IF_NOT_EXISTS} in
 * {@code application.yml}. Additionally, the keyspace is created via a CQL script executed
 * against the running container before the Spring context starts (see
 * {@link #configureProperties}).</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Cassandra Integration Tests")
class CassandraIntegrationTest {

    /**
     * Testcontainers Cassandra container using the official cassandra:4.1 image.
     *
     * <p>The static field + {@code @Container} annotation means Testcontainers
     * starts this container once before the first test and stops it after the last,
     * which is much faster than per-method container lifecycle.</p>
     */
    @Container
    static CassandraContainer<?> cassandraContainer =
            new CassandraContainer<>("cassandra:4.1")
                    .withExposedPorts(9042);

    /**
     * Dynamically injects Cassandra connection properties into the Spring context
     * and pre-creates the test keyspace.
     *
     * <p>This method runs after the container starts but before the Spring context
     * is created. It overrides the values from {@code application.yml} with the
     * actual host and port assigned by Docker (ephemeral).</p>
     *
     * <h2>Why we create the keyspace here</h2>
     * <p>Spring Boot's Cassandra auto-configuration opens the CQL session with the
     * keyspace name already set (from {@code spring.cassandra.keyspace-name}).
     * If the keyspace does not exist at that point, the driver throws
     * {@code InvalidKeyspaceException}. The Spring Boot
     * {@code keyspace-creation.*} properties run <em>after</em> the initial
     * session is opened, so they cannot help here.</p>
     *
     * <p>The solution is to open a temporary, keyspace-free {@link CqlSession},
     * execute the CREATE KEYSPACE CQL statement, close the session, and then
     * let Spring Boot open its own session pointing to the now-existing keyspace.</p>
     *
     * @param registry Spring's dynamic property registry
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String host = cassandraContainer.getHost();
        int port = cassandraContainer.getMappedPort(9042);

        // Create the test keyspace before the Spring context starts.
        // We use a raw CqlSession with NO keyspace so the driver can connect freely.
        try (CqlSession session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(host, port))
                .withLocalDatacenter("datacenter1")
                .build()) {
            // SimpleStrategy with replication factor 1 is sufficient for a single-node test cluster.
            session.execute(
                    "CREATE KEYSPACE IF NOT EXISTS catalog_test " +
                    "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}"
            );
        }

        // Override contact-points with the container's mapped host and port
        registry.add("spring.cassandra.contact-points", () -> host + ":" + port);

        // Cassandra in the container always uses datacenter1 for single-node setups
        registry.add("spring.cassandra.local-datacenter", () -> "datacenter1");

        // Point Spring to the test keyspace we just created above
        registry.add("spring.cassandra.keyspace-name", () -> "catalog_test");

        // Schema action: create the products table if it does not exist
        registry.add("spring.cassandra.schema-action", () -> "CREATE_IF_NOT_EXISTS");
    }

    /** MockMvc allows HTTP-level testing against the full Spring context. */
    @Autowired
    private MockMvc mockMvc;

    /** Direct repository access for seeding test data and direct assertions. */
    @Autowired
    private ProductRepository productRepository;

    /** Test fixture constants. */
    private static final String CATEGORY = "Electronics";
    private static final String CATEGORY_BOOKS = "Books";

    /**
     * Clears all data from the products table before each test.
     * This ensures test isolation — data created in one test does not affect others.
     */
    @BeforeEach
    void cleanDatabase() {
        productRepository.deleteAll();
    }

    // =========================================================================
    // Create (POST) Integration Tests
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("POST /api/products — should create a product and return 201")
    void createProduct_persistsToCassandra() throws Exception {
        String requestBody = """
                {
                    "category": "Electronics",
                    "name": "Laptop Pro",
                    "description": "A high-end laptop",
                    "price": 1299.99,
                    "stock": 20
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("Electronics"))
                .andExpect(jsonPath("$.name").value("Laptop Pro"))
                .andExpect(jsonPath("$.description").value("A high-end laptop"))
                .andExpect(jsonPath("$.price").value(1299.99))
                .andExpect(jsonPath("$.stock").value(20))
                // The UUID should be generated server-side
                .andExpect(jsonPath("$.id").isNotEmpty());

        // Verify directly in Cassandra via the repository
        assertThat(productRepository.findByCategory("Electronics")).hasSize(1);
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/products — should return 400 when category is blank")
    void createProduct_returns400_whenCategoryBlank() throws Exception {
        String requestBody = """
                {
                    "category": "",
                    "name": "Laptop Pro",
                    "price": 999.99,
                    "stock": 10
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/products — should return 400 when name is blank")
    void createProduct_returns400_whenNameBlank() throws Exception {
        String requestBody = """
                {
                    "category": "Electronics",
                    "name": "",
                    "price": 999.99,
                    "stock": 10
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/products — should return 400 when price is null")
    void createProduct_returns400_whenPriceNull() throws Exception {
        String requestBody = """
                {
                    "category": "Electronics",
                    "name": "Laptop Pro",
                    "stock": 10
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Read (GET) Integration Tests
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("GET /api/products?category=... — should return all products in category")
    void getByCategory_returnsProductsInCategory() throws Exception {
        // Seed two products in Electronics and one in Books
        productRepository.save(new Product(CATEGORY, UUID.randomUUID(), "Laptop",
                null, new BigDecimal("999.99"), 10));
        productRepository.save(new Product(CATEGORY, UUID.randomUUID(), "Phone",
                null, new BigDecimal("499.99"), 50));
        productRepository.save(new Product(CATEGORY_BOOKS, UUID.randomUUID(), "Clean Code",
                null, new BigDecimal("39.99"), 200));

        mockMvc.perform(get("/api/products").param("category", CATEGORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Laptop", "Phone")));
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/products?category=... — should return empty list for unknown category")
    void getByCategory_returnsEmptyList_forUnknownCategory() throws Exception {
        mockMvc.perform(get("/api/products").param("category", "Unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Order(12)
    @DisplayName("GET /api/products/{id}?category=... — should return product by composite key")
    void getById_returnsCorrectProduct() throws Exception {
        UUID id = UUID.randomUUID();
        productRepository.save(new Product(CATEGORY, id, "Tablet",
                "10-inch tablet", new BigDecimal("349.99"), 30));

        mockMvc.perform(get("/api/products/" + id).param("category", CATEGORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tablet"))
                .andExpect(jsonPath("$.category").value(CATEGORY));
    }

    @Test
    @Order(13)
    @DisplayName("GET /api/products/{id}?category=... — should return 404 when not found")
    void getById_returns404_whenNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get("/api/products/" + unknownId).param("category", CATEGORY))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Filter Integration Tests
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("GET /api/products/filter/price — should return products at or below maxPrice")
    void filterByMaxPrice_returnsAffordableProducts() throws Exception {
        productRepository.save(new Product(CATEGORY, UUID.randomUUID(), "Budget Phone",
                null, new BigDecimal("199.99"), 100));
        productRepository.save(new Product(CATEGORY, UUID.randomUUID(), "Premium Laptop",
                null, new BigDecimal("1999.99"), 5));

        mockMvc.perform(get("/api/products/filter/price")
                        .param("category", CATEGORY)
                        .param("maxPrice", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Budget Phone"));
    }

    @Test
    @Order(21)
    @DisplayName("GET /api/products/filter/stock — should return products with stock > minStock")
    void filterByMinStock_returnsInStockProducts() throws Exception {
        productRepository.save(new Product(CATEGORY, UUID.randomUUID(), "Popular Item",
                null, new BigDecimal("99.99"), 500));
        productRepository.save(new Product(CATEGORY, UUID.randomUUID(), "Rare Item",
                null, new BigDecimal("299.99"), 3));

        mockMvc.perform(get("/api/products/filter/stock")
                        .param("category", CATEGORY)
                        .param("minStock", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Popular Item"));
    }

    // =========================================================================
    // Update (PUT) Integration Tests
    // =========================================================================

    @Test
    @Order(30)
    @DisplayName("PUT /api/products/{id}?category=... — should update product fields")
    void updateProduct_updatesFieldsInCassandra() throws Exception {
        UUID id = UUID.randomUUID();
        productRepository.save(new Product(CATEGORY, id, "Old Name",
                "Old desc", new BigDecimal("100.00"), 10));

        String requestBody = """
                {
                    "name": "New Name",
                    "price": 79.99,
                    "stock": 25
                }
                """;

        mockMvc.perform(put("/api/products/" + id)
                        .param("category", CATEGORY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.price").value(79.99))
                .andExpect(jsonPath("$.stock").value(25));

        // Verify the change is persisted in Cassandra
        productRepository.findByCategoryAndId(CATEGORY, id)
                .ifPresent(p -> assertThat(p.getName()).isEqualTo("New Name"));
    }

    @Test
    @Order(31)
    @DisplayName("PUT /api/products/{id}?category=... — should return 404 when product not found")
    void updateProduct_returns404_whenNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        String requestBody = """
                { "name": "Ghost" }
                """;

        mockMvc.perform(put("/api/products/" + unknownId)
                        .param("category", CATEGORY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Delete (DELETE) Integration Tests
    // =========================================================================

    @Test
    @Order(40)
    @DisplayName("DELETE /api/products/{id}?category=... — should remove product from Cassandra")
    void deleteProduct_removesFromCassandra() throws Exception {
        UUID id = UUID.randomUUID();
        productRepository.save(new Product(CATEGORY, id, "To Delete",
                null, new BigDecimal("5.00"), 1));

        mockMvc.perform(delete("/api/products/" + id).param("category", CATEGORY))
                .andExpect(status().isNoContent());

        // Verify the product is gone
        assertThat(productRepository.findByCategoryAndId(CATEGORY, id)).isEmpty();
    }

    @Test
    @Order(41)
    @DisplayName("DELETE /api/products/{id}?category=... — should return 404 when not found")
    void deleteProduct_returns404_whenNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(delete("/api/products/" + unknownId).param("category", CATEGORY))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Wide-Column / Multi-Category Isolation Test
    // =========================================================================

    @Test
    @Order(50)
    @DisplayName("GET /api/products — queries are partition-isolated (wide-column verification)")
    void categoryQuery_isPartitionIsolated() throws Exception {
        // Seed products across two different categories (different Cassandra partitions)
        productRepository.save(new Product("Electronics", UUID.randomUUID(), "Laptop",
                null, new BigDecimal("999.99"), 10));
        productRepository.save(new Product("Electronics", UUID.randomUUID(), "Phone",
                null, new BigDecimal("499.99"), 20));
        productRepository.save(new Product("Books", UUID.randomUUID(), "Clean Code",
                null, new BigDecimal("39.99"), 100));
        productRepository.save(new Product("Books", UUID.randomUUID(), "Refactoring",
                null, new BigDecimal("44.99"), 80));

        // Query only Books — should not return Electronics products
        mockMvc.perform(get("/api/products").param("category", "Books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Clean Code", "Refactoring")));

        // Query only Electronics — should not return Books products
        mockMvc.perform(get("/api/products").param("category", "Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Laptop", "Phone")));
    }
}
