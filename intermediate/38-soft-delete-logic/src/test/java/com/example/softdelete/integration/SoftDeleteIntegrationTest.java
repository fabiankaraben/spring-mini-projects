package com.example.softdelete.integration;

import com.example.softdelete.dto.ProductRequest;
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
 * Full integration tests for the Soft Delete Logic API.
 *
 * <h2>What these tests verify</h2>
 * <ul>
 *   <li><strong>@SQLDelete behaviour</strong> – calling DELETE on a product does not
 *       physically remove the row; it sets {@code deleted = true}.</li>
 *   <li><strong>@SQLRestriction transparency</strong> – soft-deleted products disappear
 *       from all normal GET endpoints (list, getById, search, category filter).</li>
 *   <li><strong>Admin/audit view</strong> – the {@code /deleted} endpoint correctly
 *       exposes soft-deleted products by using a native SQL query that bypasses
 *       the entity-level filter.</li>
 *   <li><strong>Restore flow</strong> – a soft-deleted product can be un-deleted and
 *       becomes visible again through normal queries.</li>
 *   <li><strong>Validation</strong> – bad request payloads are rejected with 400.</li>
 *   <li><strong>Error handling</strong> – 404 responses for non-existent / deleted IDs.</li>
 * </ul>
 *
 * <h2>Technology used</h2>
 * <ul>
 *   <li><strong>Testcontainers</strong> – a real PostgreSQL Docker container is started
 *       for the test class, ensuring Hibernate's custom SQL (from {@code @SQLDelete}
 *       and {@code @SQLRestriction}) is exercised against a real database.</li>
 *   <li><strong>MockMvc</strong> – HTTP requests go through the full Spring MVC
 *       stack without binding to a real network port.</li>
 *   <li><strong>{@code @DynamicPropertySource}</strong> – injects Testcontainers'
 *       runtime JDBC URL into the Spring context before startup.</li>
 * </ul>
 *
 * <h2>Database isolation</h2>
 * <p>The Hibernate DDL strategy is {@code create-drop} (see {@code application-test.yml}),
 * so the schema is recreated for every test class run.  Individual test methods share
 * the same schema but each creates its own data with unique names to avoid collisions.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Soft Delete Logic API – Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SoftDeleteIntegrationTest {

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
            .withDatabaseName("softdelete_test")
            .withUsername("testuser")
            .withPassword("testpass");

    /**
     * Injects Testcontainers' runtime-assigned JDBC URL, username, and password
     * into the Spring DataSource configuration before the ApplicationContext starts.
     *
     * <p>Without this, Spring would try to connect to the placeholder URL defined
     * in {@code application-test.yml} and fail.</p>
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

    /** Jackson mapper used to serialise request DTOs and parse response bodies. */
    @Autowired
    private ObjectMapper objectMapper;

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Creates a product via the REST API and returns its generated ID.
     *
     * @param name        product name
     * @param description optional description
     * @param price       unit price
     * @param category    optional category
     * @return the auto-generated ID assigned by the database
     */
    private Long createProduct(String name, String description,
                                BigDecimal price, String category) throws Exception {
        ProductRequest request = new ProductRequest(name, description, price, category);

        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("id").asLong();
    }

    // ── Create (POST /api/products) ───────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/products should return 201 with the created product")
    void create_shouldReturn201WithProduct() throws Exception {
        ProductRequest request = new ProductRequest(
                "Wireless Headphones", "Noise-cancelling", new BigDecimal("149.99"), "Audio");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Wireless Headphones")))
                .andExpect(jsonPath("$.price", is(149.99)))
                .andExpect(jsonPath("$.category", is("Audio")))
                // A newly created product must have deleted=false
                .andExpect(jsonPath("$.deleted", is(false)))
                .andExpect(jsonPath("$.deletedAt", nullValue()))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/products should return 400 when name is blank")
    void create_shouldReturn400_whenNameIsBlank() throws Exception {
        ProductRequest request = new ProductRequest("", "desc", new BigDecimal("10.00"), "Cat");

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
        ProductRequest request = new ProductRequest("Valid Name", "desc", new BigDecimal("0.00"), "Cat");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price", notNullValue()));
    }

    // ── Read (GET /api/products) ──────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("GET /api/products should list only active (non-deleted) products")
    void listAll_shouldReturnOnlyActiveProducts() throws Exception {
        // Create two products
        Long activeId = createProduct("Active TV", "Still available", new BigDecimal("799.99"), "Electronics");
        Long deletedId = createProduct("Deleted TV", "Will be soft-deleted", new BigDecimal("399.99"), "Electronics");

        // Soft-delete the second one
        mockMvc.perform(delete("/api/products/" + deletedId))
                .andExpect(status().isNoContent());

        // List all – only the active one should appear
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Active TV")))
                // Soft-deleted product must NOT appear in the normal list
                .andExpect(jsonPath("$[*].name", not(hasItem("Deleted TV"))));
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/products/{id} should return 200 for an active product")
    void getById_shouldReturn200_forActiveProduct() throws Exception {
        Long id = createProduct("Smart Watch", "Wearable device", new BigDecimal("299.99"), "Wearables");

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Smart Watch")))
                .andExpect(jsonPath("$.deleted", is(false)));
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/products/{id} should return 404 for a non-existent ID")
    void getById_shouldReturn404_forNonExistentId() throws Exception {
        mockMvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("999999")));
    }

    // ── Update (PUT /api/products/{id}) ───────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("PUT /api/products/{id} should update and return the modified product")
    void update_shouldReturnUpdatedProduct() throws Exception {
        Long id = createProduct("Old Name", "Old description", new BigDecimal("50.00"), "Books");

        ProductRequest updateRequest = new ProductRequest(
                "New Name", "New description", new BigDecimal("75.00"), "Books");

        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("New Name")))
                .andExpect(jsonPath("$.price", is(75.00)))
                .andExpect(jsonPath("$.description", is("New description")));
    }

    @Test
    @Order(8)
    @DisplayName("PUT /api/products/{id} should return 404 for a non-existent product")
    void update_shouldReturn404_forNonExistentProduct() throws Exception {
        ProductRequest request = new ProductRequest("Name", "Desc", new BigDecimal("10.00"), "Cat");

        mockMvc.perform(put("/api/products/888888")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ── Soft Delete (DELETE /api/products/{id}) ───────────────────────────────

    @Test
    @Order(9)
    @DisplayName("DELETE /api/products/{id} should soft-delete: return 204 and hide the product")
    void delete_shouldSoftDelete_andHideProduct() throws Exception {
        Long id = createProduct("Camera", "DSLR camera", new BigDecimal("899.99"), "Photography");

        // Verify it exists before deletion
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk());

        // Soft-delete it
        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNoContent());

        // KEY TEST: The product must now be INVISIBLE to the normal GET endpoint.
        // This verifies that @SQLRestriction("deleted = false") is working correctly.
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(10)
    @DisplayName("DELETE /api/products/{id} should return 404 for a non-existent product")
    void delete_shouldReturn404_forNonExistentProduct() throws Exception {
        mockMvc.perform(delete("/api/products/777777"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(11)
    @DisplayName("DELETE /api/products/{id} should return 404 when trying to delete an already-deleted product")
    void delete_shouldReturn404_whenProductAlreadyDeleted() throws Exception {
        Long id = createProduct("Temp Product", "Will be deleted twice", new BigDecimal("1.00"), "Test");

        // First deletion should succeed
        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNoContent());

        // Second deletion attempt: product is now invisible (@SQLRestriction), so 404
        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNotFound());
    }

    // ── Admin: list deleted (GET /api/products/deleted) ──────────────────────

    @Test
    @Order(12)
    @DisplayName("GET /api/products/deleted should show soft-deleted products (bypasses @SQLRestriction)")
    void listDeleted_shouldShowSoftDeletedProducts() throws Exception {
        Long id = createProduct("Retired Printer", "No longer sold", new BigDecimal("199.99"), "Office");

        // Soft-delete it
        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNoContent());

        // The deleted endpoint must show it – it uses a native SQL query that
        // bypasses the @SQLRestriction("deleted = false") entity-level filter.
        mockMvc.perform(get("/api/products/deleted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Retired Printer")));
    }

    @Test
    @Order(13)
    @DisplayName("GET /api/products/deleted should show deleted=true and non-null deletedAt for soft-deleted items")
    void listDeleted_shouldShowDeletedFlagAndTimestamp() throws Exception {
        Long id = createProduct("Obsolete Keyboard", "Old model", new BigDecimal("29.99"), "Peripherals");

        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNoContent());

        // Verify the audit fields set by @SQLDelete are present in the response
        mockMvc.perform(get("/api/products/deleted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Obsolete Keyboard')].deleted", contains(true)))
                .andExpect(jsonPath("$[?(@.name=='Obsolete Keyboard')].deletedAt", not(contains(nullValue()))));
    }

    // ── Restore (POST /api/products/{id}/restore) ─────────────────────────────

    @Test
    @Order(14)
    @DisplayName("POST /api/products/{id}/restore should un-delete a product and make it visible again")
    void restore_shouldMakeProductVisibleAgain() throws Exception {
        Long id = createProduct("Restored Tablet", "Brought back", new BigDecimal("499.99"), "Electronics");

        // Soft-delete it
        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNoContent());

        // Confirm it's hidden from normal queries
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound());

        // Restore it
        mockMvc.perform(post("/api/products/" + id + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Restored Tablet")))
                // After restore, deleted must be false and deletedAt must be cleared
                .andExpect(jsonPath("$.deleted", is(false)))
                .andExpect(jsonPath("$.deletedAt", nullValue()));

        // KEY TEST: The product must now be VISIBLE again through the normal endpoint.
        // This verifies that @SQLRestriction re-includes the restored product.
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Restored Tablet")));
    }

    @Test
    @Order(15)
    @DisplayName("POST /api/products/{id}/restore should return 404 when no deleted product with that ID exists")
    void restore_shouldReturn404_whenNoDeletedProductExists() throws Exception {
        // Try to restore a product that was never created (or never deleted)
        mockMvc.perform(post("/api/products/666666/restore"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(16)
    @DisplayName("POST /api/products/{id}/restore should return 404 for an active (non-deleted) product")
    void restore_shouldReturn404_forActiveProduct() throws Exception {
        // An active product has deleted=false; findDeletedById won't find it
        Long id = createProduct("Active Product Restore Test", "Active", new BigDecimal("10.00"), "Test");

        mockMvc.perform(post("/api/products/" + id + "/restore"))
                .andExpect(status().isNotFound());
    }

    // ── Search (GET /api/products/search) ─────────────────────────────────────

    @Test
    @Order(17)
    @DisplayName("GET /api/products/search?name=... should only return active products")
    void search_shouldExcludeDeletedProducts() throws Exception {
        createProduct("Unique Search Item Active", "Active", new BigDecimal("10.00"), "Test");
        Long deletedId = createProduct("Unique Search Item Deleted", "Will be deleted", new BigDecimal("10.00"), "Test");

        mockMvc.perform(delete("/api/products/" + deletedId))
                .andExpect(status().isNoContent());

        // The search must not return the soft-deleted product even though
        // its name contains "Unique Search Item"
        mockMvc.perform(get("/api/products/search").param("name", "Unique Search Item"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Unique Search Item Active")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Unique Search Item Deleted"))));
    }

    // ── Category filter (GET /api/products/category/{category}) ──────────────

    @Test
    @Order(18)
    @DisplayName("GET /api/products/category/{category} should only return active products")
    void categoryFilter_shouldExcludeDeletedProducts() throws Exception {
        createProduct("Category Active Item", "Active", new BigDecimal("50.00"), "UniqueTestCategory");
        Long deletedId = createProduct("Category Deleted Item", "Deleted", new BigDecimal("50.00"), "UniqueTestCategory");

        mockMvc.perform(delete("/api/products/" + deletedId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/category/UniqueTestCategory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Category Active Item")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Category Deleted Item"))));
    }

    // ── Count deleted (GET /api/products/deleted/count) ───────────────────────

    @Test
    @Order(19)
    @DisplayName("GET /api/products/deleted/count should return the correct count of soft-deleted products")
    void countDeleted_shouldReturnCorrectCount() throws Exception {
        // Get current count first
        MvcResult countResult = mockMvc.perform(get("/api/products/deleted/count"))
                .andExpect(status().isOk())
                .andReturn();
        long countBefore = Long.parseLong(countResult.getResponse().getContentAsString());

        // Create and soft-delete a product
        Long id = createProduct("Count Test Product", "For counting", new BigDecimal("1.00"), "Test");
        mockMvc.perform(delete("/api/products/" + id)).andExpect(status().isNoContent());

        // Count should have increased by 1
        MvcResult newCountResult = mockMvc.perform(get("/api/products/deleted/count"))
                .andExpect(status().isOk())
                .andReturn();
        long countAfter = Long.parseLong(newCountResult.getResponse().getContentAsString());

        assertThat(countAfter).isEqualTo(countBefore + 1);
    }

    // ── Full lifecycle: create → delete → restore ──────────────────────────────

    @Test
    @Order(20)
    @DisplayName("Full lifecycle: create → soft-delete → restore should work end-to-end")
    void fullLifecycle_createDeleteRestore() throws Exception {
        // Step 1: Create
        Long id = createProduct("Lifecycle Product", "Full cycle test", new BigDecimal("100.00"), "Test");

        // Step 2: Verify active
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted", is(false)));

        // Step 3: Soft-delete
        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNoContent());

        // Step 4: Verify hidden from normal queries
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound());

        // Step 5: Verify visible in deleted list
        mockMvc.perform(get("/api/products/deleted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(id.intValue())));

        // Step 6: Restore
        mockMvc.perform(post("/api/products/" + id + "/restore"))
                .andExpect(status().isOk());

        // Step 7: Verify active again
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted", is(false)))
                .andExpect(jsonPath("$.deletedAt", nullValue()));

        // Step 8: Verify no longer in deleted list
        mockMvc.perform(get("/api/products/deleted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(id.intValue()))));
    }
}
