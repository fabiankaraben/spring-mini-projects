package com.example.multitenancy.integration;

import com.example.multitenancy.domain.Product;
import com.example.multitenancy.tenant.TenantContext;
import com.example.multitenancy.web.interceptor.TenantInterceptor;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration test for the multi-tenancy DB pattern.
 *
 * <h2>What is tested?</h2>
 * <ol>
 *   <li><b>Schema isolation</b>: A product created via {@code tenant_alpha} must NOT
 *       appear when the same endpoint is called with {@code tenant_beta}, and vice versa.</li>
 *   <li><b>CRUD operations</b>: Create, read, update, and delete products work correctly
 *       within a tenant's schema.</li>
 *   <li><b>Missing tenant header</b>: Requests without an {@code X-Tenant-ID} header
 *       receive HTTP 400.</li>
 *   <li><b>Search</b>: Keyword search is scoped to the current tenant.</li>
 * </ol>
 *
 * <h2>Infrastructure</h2>
 * <p>A real PostgreSQL database is started using Testcontainers. The container URL,
 * username, and password are injected into the Spring context via
 * {@link DynamicPropertySource} before the application context starts. This ensures
 * the application uses the Testcontainers-managed database, not any external database.</p>
 *
 * <h2>Test lifecycle</h2>
 * <p>The Spring context (and the PostgreSQL container) is started once for all tests
 * in this class ({@code @Testcontainers} with {@code @Container} on a static field).
 * This is more efficient than restarting the context for each test method.</p>
 *
 * <p>Each test method cleans up after itself (or before itself) to ensure test
 * independence.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Multi-Tenancy Integration Tests — schema isolation and CRUD")
class MultitenancyIntegrationTest {

    /**
     * The PostgreSQL Testcontainer.
     *
     * <p>Declared as {@code static} so that the same container is shared across
     * all test methods in this class. Testcontainers starts it once before the
     * first test and stops it after the last test.</p>
     *
     * <p>We use the official {@code postgres:16-alpine} image which is lighter
     * than the full image. The credentials here match what the container actually
     * sets up — they are overridden into Spring's datasource via
     * {@link #overrideProperties(DynamicPropertyRegistry)}.</p>
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("multitenancy_db")
            .withUsername("test_user")
            .withPassword("test_password");

    /**
     * Injects the Testcontainers PostgreSQL URL, username, and password into the
     * Spring application context <em>before</em> the context is started.
     *
     * <p>This is the Testcontainers + Spring Boot way to dynamically wire the
     * container's random port into the application's datasource configuration
     * without hardcoding any ports.</p>
     *
     * @param registry Spring's dynamic property registry
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Header constant reused across all test requests */
    private static final String TENANT_HEADER = TenantInterceptor.TENANT_HEADER;
    private static final String TENANT_ALPHA = "tenant_alpha";
    private static final String TENANT_BETA = "tenant_beta";

    /**
     * Clean up the thread-local context after each test to prevent leakage.
     * MockMvc runs on the same thread as the test, so if any test accidentally
     * leaves context set, subsequent tests could inherit it.
     */
    @AfterEach
    void cleanupContext() {
        TenantContext.clear();
    }

    // =========================================================================
    // Schema isolation tests — the core value proposition of this project
    // =========================================================================

    @Test
    @DisplayName("products created for tenant_alpha must NOT appear for tenant_beta")
    void productCreatedForTenantAlphaMustNotAppearForTenantBeta() throws Exception {
        // Step 1: Create a product for tenant_alpha
        String requestJson = """
                {
                    "name": "Alpha Laptop",
                    "description": "Exclusive to alpha tenant",
                    "price": 999.99,
                    "stockQuantity": 5
                }
                """;

        mockMvc.perform(post("/api/products")
                        .header(TENANT_HEADER, TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alpha Laptop"));

        // Step 2: List products for tenant_beta — should NOT contain "Alpha Laptop"
        mockMvc.perform(get("/api/products")
                        .header(TENANT_HEADER, TENANT_BETA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Alpha Laptop')]").doesNotExist());
    }

    @Test
    @DisplayName("products created for tenant_beta must NOT appear for tenant_alpha")
    void productCreatedForTenantBetaMustNotAppearForTenantAlpha() throws Exception {
        // Step 1: Create a product for tenant_beta
        String requestJson = """
                {
                    "name": "Beta Monitor",
                    "description": "Exclusive to beta tenant",
                    "price": 399.99,
                    "stockQuantity": 8
                }
                """;

        mockMvc.perform(post("/api/products")
                        .header(TENANT_HEADER, TENANT_BETA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Beta Monitor"));

        // Step 2: Confirm it appears for tenant_beta
        mockMvc.perform(get("/api/products")
                        .header(TENANT_HEADER, TENANT_BETA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Beta Monitor')]").exists());

        // Step 3: Confirm it does NOT appear for tenant_alpha
        mockMvc.perform(get("/api/products")
                        .header(TENANT_HEADER, TENANT_ALPHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Beta Monitor')]").doesNotExist());
    }

    @Test
    @DisplayName("IDs are independent per tenant — product ID from one tenant cannot be fetched by another")
    void productIdFromOneTenantCannotBeFetchedByAnother() throws Exception {
        // Create a product in tenant_alpha and get its ID
        String requestJson = """
                {
                    "name": "Alpha-Only Keyboard",
                    "price": 79.99,
                    "stockQuantity": 20
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/products")
                        .header(TENANT_HEADER, TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        Product created = objectMapper.readValue(
                result.getResponse().getContentAsString(), Product.class);
        Long alphaProductId = created.getId();

        // Try to fetch the same ID using tenant_beta — should return 404
        // (the row exists in tenant_alpha's schema, not in tenant_beta's)
        mockMvc.perform(get("/api/products/" + alphaProductId)
                        .header(TENANT_HEADER, TENANT_BETA))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Full CRUD tests within a single tenant
    // =========================================================================

    @Test
    @DisplayName("should create a product and retrieve it by ID within the same tenant")
    void shouldCreateAndRetrieveProductById() throws Exception {
        String requestJson = """
                {
                    "name": "Wireless Mouse",
                    "description": "Ergonomic wireless mouse",
                    "price": 49.99,
                    "stockQuantity": 100
                }
                """;

        // Create
        MvcResult createResult = mockMvc.perform(post("/api/products")
                        .header(TENANT_HEADER, TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Wireless Mouse"))
                .andExpect(jsonPath("$.price").value(49.99))
                .andExpect(jsonPath("$.stockQuantity").value(100))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        Product created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), Product.class);

        // Retrieve by ID
        mockMvc.perform(get("/api/products/" + created.getId())
                        .header(TENANT_HEADER, TENANT_ALPHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.name").value("Wireless Mouse"));
    }

    @Test
    @DisplayName("should update a product within the same tenant")
    void shouldUpdateProduct() throws Exception {
        // Create
        String createJson = """
                {
                    "name": "Old Headphones",
                    "price": 59.99,
                    "stockQuantity": 30
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/products")
                        .header(TENANT_HEADER, TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        Product created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), Product.class);

        // Update
        String updateJson = """
                {
                    "name": "Premium Headphones",
                    "description": "Updated to premium model",
                    "price": 149.99,
                    "stockQuantity": 15
                }
                """;

        mockMvc.perform(put("/api/products/" + created.getId())
                        .header(TENANT_HEADER, TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Premium Headphones"))
                .andExpect(jsonPath("$.price").value(149.99))
                .andExpect(jsonPath("$.stockQuantity").value(15));
    }

    @Test
    @DisplayName("should delete a product and verify it no longer exists")
    void shouldDeleteProduct() throws Exception {
        // Create
        String createJson = """
                {
                    "name": "Temporary Product",
                    "price": 9.99,
                    "stockQuantity": 1
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/products")
                        .header(TENANT_HEADER, TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        Product created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), Product.class);

        // Delete
        mockMvc.perform(delete("/api/products/" + created.getId())
                        .header(TENANT_HEADER, TENANT_ALPHA))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/products/" + created.getId())
                        .header(TENANT_HEADER, TENANT_ALPHA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should search products by keyword within a tenant")
    void shouldSearchProductsByKeyword() throws Exception {
        // Create two products for tenant_beta
        String laptop = """
                {"name": "Gaming Laptop", "price": 1499.99, "stockQuantity": 3}
                """;
        String desk = """
                {"name": "Standing Desk", "price": 299.99, "stockQuantity": 10}
                """;

        mockMvc.perform(post("/api/products").header(TENANT_HEADER, TENANT_BETA)
                .contentType(MediaType.APPLICATION_JSON).content(laptop))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/products").header(TENANT_HEADER, TENANT_BETA)
                .contentType(MediaType.APPLICATION_JSON).content(desk))
                .andExpect(status().isCreated());

        // Search for "laptop" — should match only the Gaming Laptop
        mockMvc.perform(get("/api/products?search=laptop")
                        .header(TENANT_HEADER, TENANT_BETA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.name =~ /.*[Ll]aptop.*/)]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Standing Desk')]").doesNotExist());
    }

    // =========================================================================
    // Error handling tests
    // =========================================================================

    @Test
    @DisplayName("should return 400 Bad Request when X-Tenant-ID header is missing")
    void shouldReturn400WhenTenantHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 400 Bad Request for POST without X-Tenant-ID header")
    void shouldReturn400ForPostWithoutTenantHeader() throws Exception {
        String requestJson = """
                {"name": "Test Product", "price": 10.00, "stockQuantity": 1}
                """;
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 400 Bad Request when product price is missing")
    void shouldReturn400WhenPriceMissing() throws Exception {
        String requestJson = """
                {"name": "Bad Product", "stockQuantity": 1}
                """;
        mockMvc.perform(post("/api/products")
                        .header(TENANT_HEADER, TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 400 Bad Request when product name is blank")
    void shouldReturn400WhenNameBlank() throws Exception {
        String requestJson = """
                {"name": "", "price": 10.00, "stockQuantity": 1}
                """;
        mockMvc.perform(post("/api/products")
                        .header(TENANT_HEADER, TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 404 when deleting a non-existent product")
    void shouldReturn404WhenDeletingNonExistentProduct() throws Exception {
        mockMvc.perform(delete("/api/products/999999")
                        .header(TENANT_HEADER, TENANT_ALPHA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should return 404 when updating a non-existent product")
    void shouldReturn404WhenUpdatingNonExistentProduct() throws Exception {
        String updateJson = """
                {"name": "Ghost Product", "price": 1.00, "stockQuantity": 0}
                """;
        mockMvc.perform(put("/api/products/999999")
                        .header(TENANT_HEADER, TENANT_ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound());
    }
}
