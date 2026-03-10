package com.example.optimisticlocking.integration;

import com.example.optimisticlocking.domain.Product;
import com.example.optimisticlocking.dto.ProductRequest;
import com.example.optimisticlocking.dto.ProductUpdateRequest;
import com.example.optimisticlocking.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the Optimistic Locking API.
 *
 * <h2>What these tests verify</h2>
 * <ul>
 *   <li><strong>Version initialisation</strong> – a newly created product has {@code version=0}.</li>
 *   <li><strong>Version increment</strong> – a successful update increments the version.</li>
 *   <li><strong>Optimistic locking conflict (HTTP 409)</strong> – the key test: using a stale
 *       version in an update request causes the API to return 409 Conflict.</li>
 *   <li><strong>CRUD operations</strong> – create, read, update, delete work correctly.</li>
 *   <li><strong>Validation</strong> – bad request payloads are rejected with 400.</li>
 *   <li><strong>Error handling</strong> – 404 responses for non-existent IDs.</li>
 * </ul>
 *
 * <h2>Technology used</h2>
 * <ul>
 *   <li><strong>Testcontainers</strong> – a real PostgreSQL Docker container is started for
 *       the test class, ensuring Hibernate's {@code @Version} column actually exists in the
 *       schema and the WHERE clause is exercised against a real database engine.</li>
 *   <li><strong>MockMvc</strong> – HTTP requests go through the full Spring MVC stack without
 *       binding to a real network port.</li>
 *   <li><strong>{@code @DynamicPropertySource}</strong> – injects Testcontainers' runtime
 *       JDBC URL into the Spring context before startup.</li>
 * </ul>
 *
 * <h2>Database isolation</h2>
 * <p>The Hibernate DDL strategy is {@code create-drop} (see {@code application-test.yml}),
 * so the schema is recreated for every test class run. Tests use unique product names to
 * avoid data collisions across test methods that share the same schema.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Optimistic Locking API – Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OptimisticLockingIntegrationTest {

    /**
     * PostgreSQL Testcontainer shared across all test methods in this class.
     *
     * <p>The {@code static} keyword ensures the container starts once before the
     * Spring ApplicationContext is created, which is required for
     * {@link #overrideDataSourceProperties} to inject the correct JDBC URL.</p>
     */
    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("optimisticlocking_test")
            .withUsername("testuser")
            .withPassword("testpass");

    /**
     * Injects Testcontainers' runtime-assigned JDBC URL, username, and password
     * into the Spring DataSource configuration before the ApplicationContext starts.
     *
     * @param registry the dynamic property registry provided by Spring Test
     */
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Starts the container before any test in this class runs. */
    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    /** Stops and removes the container after all tests in this class have finished. */
    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Creates a product via the REST API and returns its generated ID.
     */
    private Long createProduct(String name, String description,
                                BigDecimal price, Integer stock, String category) throws Exception {
        ProductRequest request = new ProductRequest(name, description, price, stock, category);

        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("id").asLong();
    }

    /**
     * GETs a product by ID and returns the parsed response tree.
     */
    private com.fasterxml.jackson.databind.JsonNode getProduct(Long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ── Create (POST /api/products) ───────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/products should return 201 with version=0 for a new product")
    void create_shouldReturn201WithVersionZero() throws Exception {
        ProductRequest request = new ProductRequest(
                "Wireless Headphones", "Noise-cancelling", new BigDecimal("149.99"), 50, "Audio");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Wireless Headphones")))
                .andExpect(jsonPath("$.price", is(149.99)))
                .andExpect(jsonPath("$.stock", is(50)))
                // KEY: Hibernate initialises @Version to 0 on the first INSERT
                .andExpect(jsonPath("$.version", is(0)))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/products should return 400 when name is blank")
    void create_shouldReturn400_whenNameIsBlank() throws Exception {
        ProductRequest request = new ProductRequest("", "desc", new BigDecimal("10.00"), 5, "Cat");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name", notNullValue()));
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/products should return 400 when price is zero or negative")
    void create_shouldReturn400_whenPriceIsInvalid() throws Exception {
        ProductRequest request = new ProductRequest("Valid Name", "desc", new BigDecimal("0.00"), 5, "Cat");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price", notNullValue()));
    }

    // ── Read (GET /api/products) ──────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("GET /api/products should list all products with version fields")
    void listAll_shouldReturnProductsWithVersions() throws Exception {
        createProduct("Smart TV", "4K display", new BigDecimal("799.99"), 20, "Electronics");

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].version", everyItem(notNullValue())));
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/products/{id} should return 200 with version field")
    void getById_shouldReturn200WithVersion() throws Exception {
        Long id = createProduct("Smart Watch", "Wearable device", new BigDecimal("299.99"), 30, "Wearables");

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Smart Watch")))
                .andExpect(jsonPath("$.version", notNullValue()));
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/products/{id} should return 404 for a non-existent ID")
    void getById_shouldReturn404_forNonExistentId() throws Exception {
        mockMvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("999999")));
    }

    // ── Update with correct version (PUT /api/products/{id}) ─────────────────

    @Test
    @Order(7)
    @DisplayName("PUT /api/products/{id} with correct version should succeed and increment version")
    void update_withCorrectVersion_shouldSucceedAndIncrementVersion() throws Exception {
        // Step 1: Create a product (version=0)
        Long id = createProduct("Old Laptop", "Old description", new BigDecimal("999.99"), 10, "Electronics");

        // Step 2: Fetch it to get the current version
        com.fasterxml.jackson.databind.JsonNode productNode = getProduct(id);
        long currentVersion = productNode.get("version").asLong();
        assertThat(currentVersion).isEqualTo(0L);

        // Step 3: Update with the correct version (version=0)
        ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                currentVersion,
                "Gaming Laptop", "Updated description", new BigDecimal("1299.99"), 5, "Gaming");

        MvcResult result = mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Gaming Laptop")))
                .andExpect(jsonPath("$.price", is(1299.99)))
                .andExpect(jsonPath("$.stock", is(5)))
                // KEY: version must be incremented after a successful update (0 → 1)
                .andExpect(jsonPath("$.version", is(1)))
                .andReturn();

        long newVersion = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("version").asLong();
        assertThat(newVersion).isEqualTo(currentVersion + 1);
    }

    // ── Optimistic Locking Conflict (the core test) ───────────────────────────

    @Test
    @Order(8)
    @DisplayName("PUT with stale version should return 409 Conflict – the core optimistic locking test")
    void update_withStaleVersion_shouldReturn409Conflict() throws Exception {
        // Step 1: Create a product (version=0)
        Long id = createProduct("Conflict Product", "Will cause conflict", new BigDecimal("100.00"), 50, "Test");

        // Step 2: Fetch it (version=0)
        com.fasterxml.jackson.databind.JsonNode productNode = getProduct(id);
        long version0 = productNode.get("version").asLong();
        assertThat(version0).isEqualTo(0L);

        // Step 3: First update succeeds (version 0 → 1)
        ProductUpdateRequest firstUpdate = new ProductUpdateRequest(
                version0, "First Update", "Updated by first client",
                new BigDecimal("110.00"), 45, "Test");

        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(1)));

        // Step 4: Second update uses the OLD version (0) – simulating a second client
        //         that read the product before the first update happened.
        //         This is the classic "lost update" scenario that optimistic locking prevents.
        ProductUpdateRequest secondUpdate = new ProductUpdateRequest(
                version0,  // STALE: version is still 0, but the DB now has version=1
                "Second Update (should fail)", "This should cause 409",
                new BigDecimal("120.00"), 40, "Test");

        // KEY TEST: The second update must be rejected with 409 Conflict because
        // Hibernate detects that the version in the request (0) does not match the
        // version in the database (1).  This prevents the "lost update" problem.
        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondUpdate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("Concurrent modification detected")));
    }

    @Test
    @Order(9)
    @DisplayName("PUT with version missing (null) should return 400 Bad Request")
    void update_withNullVersion_shouldReturn400() throws Exception {
        Long id = createProduct("Version Test Product", "Test", new BigDecimal("50.00"), 10, "Test");

        // Omit the version field – should be rejected by Bean Validation
        String requestBody = """
                {
                    "name": "Updated Name",
                    "price": 60.00,
                    "stock": 8
                }
                """;

        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version", notNullValue()));
    }

    @Test
    @Order(10)
    @DisplayName("Multiple sequential updates should increment version monotonically")
    void multipleUpdates_shouldIncrementVersionEachTime() throws Exception {
        // Step 1: Create (version=0)
        Long id = createProduct("Version Sequence Product", "Test", new BigDecimal("50.00"), 100, "Test");

        // Step 2: Update 1 (version 0 → 1)
        ProductUpdateRequest update1 = new ProductUpdateRequest(
                0L, "Update 1", "v0→v1", new BigDecimal("51.00"), 99, "Test");
        MvcResult result1 = mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(1)))
                .andReturn();

        long version1 = objectMapper.readTree(result1.getResponse().getContentAsString())
                .get("version").asLong();

        // Step 3: Update 2 (version 1 → 2)
        ProductUpdateRequest update2 = new ProductUpdateRequest(
                version1, "Update 2", "v1→v2", new BigDecimal("52.00"), 98, "Test");
        MvcResult result2 = mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(2)))
                .andReturn();

        long version2 = objectMapper.readTree(result2.getResponse().getContentAsString())
                .get("version").asLong();

        // Step 4: Update 3 (version 2 → 3)
        ProductUpdateRequest update3 = new ProductUpdateRequest(
                version2, "Update 3", "v2→v3", new BigDecimal("53.00"), 97, "Test");
        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(3)));
    }

    // ── Delete (DELETE /api/products/{id}) ────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("DELETE /api/products/{id} should return 204 and remove the product")
    void delete_shouldReturn204_andRemoveProduct() throws Exception {
        Long id = createProduct("Camera", "DSLR camera", new BigDecimal("899.99"), 8, "Photography");

        // Verify it exists
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk());

        // Delete it
        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(12)
    @DisplayName("DELETE /api/products/{id} should return 404 for a non-existent product")
    void delete_shouldReturn404_forNonExistentProduct() throws Exception {
        mockMvc.perform(delete("/api/products/777777"))
                .andExpect(status().isNotFound());
    }

    // ── Search and Category ───────────────────────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("GET /api/products/search?name=... should return matching products with version")
    void search_shouldReturnMatchingProductsWithVersion() throws Exception {
        createProduct("Unique Search Laptop", "Search test", new BigDecimal("10.00"), 5, "Test");

        mockMvc.perform(get("/api/products/search").param("name", "Unique Search Laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Unique Search Laptop")))
                .andExpect(jsonPath("$[0].version", notNullValue()));
    }

    @Test
    @Order(14)
    @DisplayName("GET /api/products/category/{category} should return products in category with version")
    void categoryFilter_shouldReturnProductsWithVersion() throws Exception {
        createProduct("Category Version Item", "Test", new BigDecimal("50.00"), 10, "UniqueVersionCategory");

        mockMvc.perform(get("/api/products/category/UniqueVersionCategory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Category Version Item")))
                .andExpect(jsonPath("$[0].version", notNullValue()));
    }

    // ── Full lifecycle with version tracking ──────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("Full lifecycle: create(v0) → update(v0→v1) → stale update(v0 rejects) → update(v1→v2)")
    void fullLifecycle_withOptimisticLocking() throws Exception {
        // Step 1: Create (version=0)
        Long id = createProduct("Lifecycle Product", "Full test", new BigDecimal("100.00"), 100, "Test");
        assertThat(getProduct(id).get("version").asLong()).isEqualTo(0L);

        // Step 2: Client A reads version=0 and updates → version=1
        ProductUpdateRequest updateA = new ProductUpdateRequest(
                0L, "Updated by A", "A's description", new BigDecimal("110.00"), 95, "Test");
        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(1)));

        // Step 3: Client B (who read version=0 before A's update) tries to update → 409
        ProductUpdateRequest updateB = new ProductUpdateRequest(
                0L, "Updated by B", "B's description", new BigDecimal("120.00"), 90, "Test");
        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateB)))
                .andExpect(status().isConflict());

        // Step 4: Client B re-fetches (now gets version=1) and retries → succeeds (version=2)
        com.fasterxml.jackson.databind.JsonNode fresh = getProduct(id);
        long freshVersion = fresh.get("version").asLong();
        assertThat(freshVersion).isEqualTo(1L);
        assertThat(fresh.get("name").asText()).isEqualTo("Updated by A");

        ProductUpdateRequest updateBRetry = new ProductUpdateRequest(
                freshVersion, "Updated by B (retry)", "B's retry", new BigDecimal("120.00"), 90, "Test");
        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBRetry)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(2)))
                .andExpect(jsonPath("$.name", is("Updated by B (retry)")));
    }

    // ── Database state verification ───────────────────────────────────────────

    @Test
    @Order(16)
    @DisplayName("Database should contain the version column and it should reflect updates")
    void database_shouldPersistVersionColumn() throws Exception {
        // Create a product via API
        Long id = createProduct("DB Version Check", "Direct DB check", new BigDecimal("75.00"), 25, "Test");

        // Verify via direct repository access that the version column exists and is 0
        Product product = productRepository.findById(id).orElseThrow();
        assertThat(product.getVersion()).isEqualTo(0L);

        // Update via API (version 0 → 1)
        ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                0L, "DB Version Check Updated", "Direct DB check", new BigDecimal("80.00"), 24, "Test");
        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // Verify the version was incremented in the database
        // Re-load from DB (clear JPA cache by finding fresh)
        Product updatedProduct = productRepository.findById(id).orElseThrow();
        assertThat(updatedProduct.getVersion()).isEqualTo(1L);
        assertThat(updatedProduct.getName()).isEqualTo("DB Version Check Updated");
    }
}
