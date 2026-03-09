package com.example.mongodbcustomqueries;

import com.example.mongodbcustomqueries.domain.Order;
import com.example.mongodbcustomqueries.domain.OrderItem;
import com.example.mongodbcustomqueries.dto.CustomerSummaryResult;
import com.example.mongodbcustomqueries.dto.OrderRequest;
import com.example.mongodbcustomqueries.dto.RevenueByRegionResult;
import com.example.mongodbcustomqueries.dto.TopProductResult;
import com.example.mongodbcustomqueries.repository.OrderRepository;
import com.example.mongodbcustomqueries.service.OrderAggregationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
 * Full integration tests for the MongoDB Custom Queries mini-project.
 *
 * <p>This test class verifies end-to-end behaviour — from the HTTP layer through
 * the service and its MongoTemplate aggregation pipelines down to a real MongoDB
 * instance managed by Testcontainers. Key aspects:
 *
 * <ul>
 *   <li>{@link SpringBootTest.WebEnvironment#RANDOM_PORT} starts a real embedded
 *       servlet container on a random port, so the full Spring MVC filter chain,
 *       serialisation stack, and exception handlers are active — identical to the
 *       production runtime.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up a real MongoDB Docker
 *       container for the duration of the test class. The container is shared
 *       across all test methods to avoid the overhead of restarting MongoDB for
 *       each individual test.</li>
 *   <li>{@link DynamicPropertySource} overrides the MongoDB connection URI in the
 *       Spring {@code Environment} before the application context is created, so
 *       Spring Boot connects to the Testcontainers-managed MongoDB container
 *       instead of any locally installed MongoDB instance.</li>
 *   <li>The {@code "test"} profile activates {@code application-test.yml} which
 *       reduces logging noise during test runs.</li>
 *   <li>Aggregation pipelines are validated end-to-end against real BSON data,
 *       which is the only reliable way to verify complex pipeline logic.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Order integration tests (MongoDB aggregation + REST API)")
class OrderIntegrationTest {

    // ── Testcontainers MongoDB container ──────────────────────────────────────────

    /**
     * A MongoDB container shared by all tests in this class.
     *
     * <p>{@code static} is crucial: JUnit 5 + Testcontainers reuses a single
     * container instance for the entire test class lifecycle, avoiding the
     * significant overhead of starting/stopping MongoDB for each test method.
     * The container is stopped automatically after the last test finishes.
     */
    @Container
    static final MongoDBContainer MONGODB =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    /**
     * Register the container's dynamic connection URI into the Spring
     * {@link org.springframework.core.env.Environment} before the application
     * context is created.
     *
     * <p>Testcontainers assigns a random host port to the container. Using
     * {@code getReplicaSetUrl()} returns the complete connection URI, which
     * Spring Data MongoDB uses when {@code spring.data.mongodb.uri} is set.
     *
     * @param registry the property registry Spring Boot reads before context startup
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
     * {@link TestRestTemplate} is designed for integration tests — it does NOT throw
     * exceptions on 4xx/5xx responses (returns the ResponseEntity instead) and follows
     * HTTP redirects automatically.
     */
    @Autowired
    TestRestTemplate restTemplate;

    /** Direct access to the service layer for data setup and assertion shortcuts. */
    @Autowired
    OrderAggregationService orderService;

    /** Direct repository access for test setup (clearing the collection between tests). */
    @Autowired
    OrderRepository orderRepository;

    // ── Setup ─────────────────────────────────────────────────────────────────────

    /**
     * Clear the MongoDB collection before each test method.
     *
     * <p>Without this, documents created in one test would be visible to subsequent
     * tests, causing non-deterministic failures (e.g. aggregation counts being higher
     * than expected because a previous test inserted extra documents).
     */
    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    // ── Helper: build a base URL ──────────────────────────────────────────────────

    /** Convenience method to build URLs against the randomly-assigned port. */
    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // ── POST /api/orders ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders returns 201 with the created order including generated ID")
    void createOrder_returns201WithOrder() {
        // Given: a valid order request
        OrderRequest request = buildRequest("Alice", "North", "PENDING",
                new BigDecimal("999.99"),
                List.of(new OrderItem("Laptop", "electronics", 1, new BigDecimal("999.99"))));

        // When: POST via HTTP
        ResponseEntity<Order> response = restTemplate.postForEntity(
                url("/api/orders"), request, Order.class);

        // Then: 201 Created and the order has a MongoDB-generated ID
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull().isNotEmpty();
        assertThat(response.getBody().getCustomerName()).isEqualTo("Alice");
        assertThat(response.getBody().getRegion()).isEqualTo("North");
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING");
        assertThat(response.getBody().getTotalAmount()).isEqualByComparingTo("999.99");
    }

    @Test
    @DisplayName("POST /api/orders returns 400 when customer name is blank")
    void createOrder_returns400_whenCustomerNameIsBlank() {
        // Given: an invalid request with a blank customer name
        OrderRequest invalid = buildRequest("", "North", "PENDING",
                new BigDecimal("99.99"),
                List.of(new OrderItem("Widget", "misc", 1, new BigDecimal("99.99"))));

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/orders"), invalid, String.class);

        // Then: 400 Bad Request (Bean Validation rejects the blank name)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/orders returns 400 when items list is empty")
    void createOrder_returns400_whenItemsIsEmpty() {
        // Given: an order with no items (violates @NotEmpty)
        OrderRequest invalid = buildRequest("Bob", "South", "PENDING",
                new BigDecimal("0.00"), List.of());

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/orders"), invalid, String.class);

        // Then: 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── GET /api/orders ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders returns 200 with empty array when collection is empty")
    void getAllOrders_returns200WithEmptyArray_whenCollectionIsEmpty() {
        // When: list all orders (collection was cleared in setUp)
        ResponseEntity<Order[]> response = restTemplate.getForEntity(
                url("/api/orders"), Order[].class);

        // Then: 200 OK with an empty array
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("GET /api/orders returns 200 with all orders when documents exist")
    void getAllOrders_returns200WithAllOrders() {
        // Given: two orders in the database
        orderService.create(buildRequest("Alice", "North", "PENDING",
                new BigDecimal("100.00"),
                List.of(new OrderItem("Book", "books", 1, new BigDecimal("100.00")))));
        orderService.create(buildRequest("Bob", "South", "DELIVERED",
                new BigDecimal("200.00"),
                List.of(new OrderItem("Pen", "stationery", 2, new BigDecimal("100.00")))));

        // When
        ResponseEntity<Order[]> response = restTemplate.getForEntity(
                url("/api/orders"), Order[].class);

        // Then: 200 OK with both orders
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(2);
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders/{id} returns 200 with the order when found")
    void getOrderById_returns200_whenFound() {
        // Given: an order in the database
        Order created = orderService.create(buildRequest("Charlie", "East", "SHIPPED",
                new BigDecimal("500.00"),
                List.of(new OrderItem("Monitor", "electronics", 1, new BigDecimal("500.00")))));

        // When: retrieve by ID
        ResponseEntity<Order> response = restTemplate.getForEntity(
                url("/api/orders/" + created.getId()), Order.class);

        // Then: 200 OK with the correct data
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCustomerName()).isEqualTo("Charlie");
        assertThat(response.getBody().getRegion()).isEqualTo("East");
    }

    @Test
    @DisplayName("GET /api/orders/{id} returns 404 when the order does not exist")
    void getOrderById_returns404_whenNotFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url("/api/orders/507f1f77bcf86cd799439999"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE /api/orders/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/orders/{id} returns 204 and the order is no longer retrievable")
    void deleteOrder_returns204AndOrderGone() {
        // Given: an order to delete
        Order created = orderService.create(buildRequest("Dave", "West", "PENDING",
                new BigDecimal("50.00"),
                List.of(new OrderItem("Pen", "stationery", 5, new BigDecimal("10.00")))));

        // When: delete via HTTP
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/api/orders/" + created.getId()),
                HttpMethod.DELETE, null, Void.class);

        // Then: 204 No Content
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // And: a subsequent GET returns 404
        ResponseEntity<String> getResponse = restTemplate.getForEntity(
                url("/api/orders/" + created.getId()), String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE /api/orders/{id} returns 404 when the order does not exist")
    void deleteOrder_returns404_whenNotFound() {
        ResponseEntity<Void> response = restTemplate.exchange(
                url("/api/orders/507f1f77bcf86cd799439999"),
                HttpMethod.DELETE, null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/orders/above-amount ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders/above-amount?min=... returns orders at or above the minimum")
    void getOrdersAboveAmount_returnsMatchingOrders() {
        // Given: a cheap order and an expensive order
        orderService.create(buildRequest("Alice", "North", "DELIVERED",
                new BigDecimal("50.00"),
                List.of(new OrderItem("Book", "books", 1, new BigDecimal("50.00")))));
        orderService.create(buildRequest("Bob", "South", "DELIVERED",
                new BigDecimal("1500.00"),
                List.of(new OrderItem("Laptop", "electronics", 1, new BigDecimal("1500.00")))));

        // When: filter by min amount of 500.00
        ResponseEntity<Order[]> response = restTemplate.getForEntity(
                url("/api/orders/above-amount?min=500.00"), Order[].class);

        // Then: only the expensive order is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody()[0].getCustomerName()).isEqualTo("Bob");
    }

    // ── GET /api/orders/analytics/revenue-by-region ───────────────────────────────

    @Test
    @DisplayName("GET /analytics/revenue-by-region groups orders by region and sums revenue")
    void getRevenueByRegion_groupsOrdersByRegion() {
        // Given: orders in two different regions
        orderService.create(buildRequest("Alice", "North", "DELIVERED",
                new BigDecimal("1000.00"),
                List.of(new OrderItem("Laptop", "electronics", 1, new BigDecimal("1000.00")))));
        orderService.create(buildRequest("Bob", "North", "DELIVERED",
                new BigDecimal("500.00"),
                List.of(new OrderItem("Mouse", "electronics", 5, new BigDecimal("100.00")))));
        orderService.create(buildRequest("Charlie", "South", "DELIVERED",
                new BigDecimal("200.00"),
                List.of(new OrderItem("Book", "books", 2, new BigDecimal("100.00")))));

        // When: call the aggregation endpoint
        ResponseEntity<RevenueByRegionResult[]> response = restTemplate.getForEntity(
                url("/api/orders/analytics/revenue-by-region"),
                RevenueByRegionResult[].class);

        // Then: two region results are returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(2);

        // North should have higher revenue and come first (sorted desc)
        RevenueByRegionResult[] results = response.getBody();
        assertThat(results[0].getRegion()).isEqualTo("North");
        assertThat(results[0].getTotalRevenue()).isEqualByComparingTo("1500.00");
        assertThat(results[0].getOrderCount()).isEqualTo(2L);
        assertThat(results[1].getRegion()).isEqualTo("South");
        assertThat(results[1].getTotalRevenue()).isEqualByComparingTo("200.00");
    }

    // ── GET /api/orders/analytics/revenue-by-region/{status} ─────────────────────

    @Test
    @DisplayName("GET /analytics/revenue-by-region/{status} filters by status before grouping")
    void getRevenueByRegionForStatus_filtersBeforeGrouping() {
        // Given: DELIVERED and CANCELLED orders in the same region
        orderService.create(buildRequest("Alice", "North", "DELIVERED",
                new BigDecimal("1000.00"),
                List.of(new OrderItem("Laptop", "electronics", 1, new BigDecimal("1000.00")))));
        orderService.create(buildRequest("Bob", "North", "CANCELLED",
                new BigDecimal("500.00"),
                List.of(new OrderItem("Mouse", "electronics", 1, new BigDecimal("500.00")))));

        // When: get revenue by region for DELIVERED only
        ResponseEntity<RevenueByRegionResult[]> response = restTemplate.getForEntity(
                url("/api/orders/analytics/revenue-by-region/DELIVERED"),
                RevenueByRegionResult[].class);

        // Then: only the DELIVERED order's revenue is included
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody()[0].getRegion()).isEqualTo("North");
        // Only the 1000.00 DELIVERED order should count, not the 500.00 CANCELLED one
        assertThat(response.getBody()[0].getTotalRevenue()).isEqualByComparingTo("1000.00");
        assertThat(response.getBody()[0].getOrderCount()).isEqualTo(1L);
    }

    // ── GET /api/orders/analytics/top-products ────────────────────────────────────

    @Test
    @DisplayName("GET /analytics/top-products unwinds items and returns top products by revenue")
    void getTopProducts_unwindsItemsAndRanksProducts() {
        // Given: two orders each with different products
        orderService.create(buildRequest("Alice", "North", "DELIVERED",
                new BigDecimal("2099.98"),
                List.of(
                        new OrderItem("Laptop", "electronics", 2, new BigDecimal("999.99")),
                        new OrderItem("Mouse", "electronics", 1, new BigDecimal("100.00"))
                )));
        orderService.create(buildRequest("Bob", "South", "DELIVERED",
                new BigDecimal("300.00"),
                List.of(
                        new OrderItem("Laptop", "electronics", 1, new BigDecimal("999.99")),
                        new OrderItem("Keyboard", "electronics", 1, new BigDecimal("79.99"))
                )));

        // When: get top 3 products
        ResponseEntity<TopProductResult[]> response = restTemplate.getForEntity(
                url("/api/orders/analytics/top-products?topN=3"),
                TopProductResult[].class);

        // Then: results are returned, sorted by revenue descending
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSizeGreaterThanOrEqualTo(1);

        // Laptop should be #1: (2 * 999.99) + (1 * 999.99) = 2999.97
        TopProductResult[] results = response.getBody();
        assertThat(results[0].getProductName()).isEqualTo("Laptop");
        assertThat(results[0].getTotalQuantity()).isEqualTo(3L);
    }

    // ── GET /api/orders/analytics/customer-summary ────────────────────────────────

    @Test
    @DisplayName("GET /analytics/customer-summary excludes CANCELLED orders and groups by customer")
    void getCustomerSummary_excludesCancelledOrders() {
        // Given: Alice has one delivered and one cancelled order; Bob has one delivered order
        orderService.create(buildRequest("Alice", "North", "DELIVERED",
                new BigDecimal("1000.00"),
                List.of(new OrderItem("Laptop", "electronics", 1, new BigDecimal("1000.00")))));
        orderService.create(buildRequest("Alice", "North", "CANCELLED",
                new BigDecimal("500.00"),
                List.of(new OrderItem("Phone", "electronics", 1, new BigDecimal("500.00")))));
        orderService.create(buildRequest("Bob", "South", "DELIVERED",
                new BigDecimal("200.00"),
                List.of(new OrderItem("Book", "books", 2, new BigDecimal("100.00")))));

        // When: call the customer summary endpoint
        ResponseEntity<CustomerSummaryResult[]> response = restTemplate.getForEntity(
                url("/api/orders/analytics/customer-summary"),
                CustomerSummaryResult[].class);

        // Then: two customers are returned (cancelled order excluded from Alice's total)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(2);

        // Alice should be first (higher totalSpent), with ONLY the delivered order counted
        CustomerSummaryResult[] results = response.getBody();
        assertThat(results[0].getCustomerName()).isEqualTo("Alice");
        // Only the 1000.00 DELIVERED order counts, not the 500.00 CANCELLED one
        assertThat(results[0].getTotalSpent()).isEqualByComparingTo("1000.00");
        assertThat(results[0].getOrderCount()).isEqualTo(1L);
    }

    // ── GET /api/orders/analytics/status-distribution ────────────────────────────

    @Test
    @DisplayName("GET /analytics/status-distribution counts orders per status")
    void getStatusDistribution_countsOrdersPerStatus() {
        // Given: orders with different statuses
        orderService.create(buildRequest("A", "North", "DELIVERED", new BigDecimal("100.00"),
                List.of(new OrderItem("X", "cat", 1, new BigDecimal("100.00")))));
        orderService.create(buildRequest("B", "North", "DELIVERED", new BigDecimal("100.00"),
                List.of(new OrderItem("X", "cat", 1, new BigDecimal("100.00")))));
        orderService.create(buildRequest("C", "South", "PENDING", new BigDecimal("50.00"),
                List.of(new OrderItem("Y", "cat", 1, new BigDecimal("50.00")))));

        // When
        ResponseEntity<org.bson.Document[]> response = restTemplate.getForEntity(
                url("/api/orders/analytics/status-distribution"),
                org.bson.Document[].class);

        // Then: status distribution is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(2);
    }

    // ── GET /api/orders/analytics/category-breakdown ──────────────────────────────

    @Test
    @DisplayName("GET /analytics/category-breakdown unwinds items and groups by category")
    void getCategoryBreakdown_groupsItemsByCategory() {
        // Given: an order with items from two different categories
        orderService.create(buildRequest("Alice", "North", "DELIVERED",
                new BigDecimal("1200.00"),
                List.of(
                        new OrderItem("Laptop", "electronics", 1, new BigDecimal("1000.00")),
                        new OrderItem("Novel", "books", 2, new BigDecimal("100.00"))
                )));

        // When: call the category breakdown endpoint
        ResponseEntity<org.bson.Document[]> response = restTemplate.getForEntity(
                url("/api/orders/analytics/category-breakdown"),
                org.bson.Document[].class);

        // Then: two category results are returned (electronics and books)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(2);
    }

    // ── Service-layer direct MongoDB verification ─────────────────────────────────

    @Test
    @DisplayName("Created order is persisted and retrievable from MongoDB")
    void createdOrder_isPersistedInMongoDB() {
        // Given: an order created via the service layer
        Order created = orderService.create(buildRequest("Alice", "North", "PENDING",
                new BigDecimal("750.00"),
                List.of(new OrderItem("Tablet", "electronics", 1, new BigDecimal("750.00")))));

        // When: retrieve directly from MongoDB via the service
        Optional<Order> found = orderService.findById(created.getId());

        // Then: the order exists in MongoDB with the correct data
        assertThat(found).isPresent();
        assertThat(found.get().getCustomerName()).isEqualTo("Alice");
        assertThat(found.get().getRegion()).isEqualTo("North");
        assertThat(found.get().getTotalAmount()).isEqualByComparingTo("750.00");
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getId()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Deleted order is no longer present in MongoDB")
    void deletedOrder_isRemovedFromMongoDB() {
        // Given: an order in MongoDB
        Order created = orderService.create(buildRequest("Bob", "South", "SHIPPED",
                new BigDecimal("300.00"),
                List.of(new OrderItem("Camera", "electronics", 1, new BigDecimal("300.00")))));
        String id = created.getId();

        // When: delete the order
        boolean deleted = orderService.deleteById(id);

        // Then: the order is gone from MongoDB
        assertThat(deleted).isTrue();
        assertThat(orderService.findById(id)).isEmpty();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("findOrdersAboveAmount returns only orders at or above the minimum")
    void findOrdersAboveAmount_returnsCorrectOrders() {
        // Given: orders with varying amounts
        orderService.create(buildRequest("Alice", "North", "DELIVERED",
                new BigDecimal("100.00"),
                List.of(new OrderItem("Book", "books", 1, new BigDecimal("100.00")))));
        orderService.create(buildRequest("Bob", "South", "DELIVERED",
                new BigDecimal("2000.00"),
                List.of(new OrderItem("Laptop", "electronics", 2, new BigDecimal("1000.00")))));
        orderService.create(buildRequest("Charlie", "East", "PENDING",
                new BigDecimal("500.00"),
                List.of(new OrderItem("Phone", "electronics", 1, new BigDecimal("500.00")))));

        // When: filter orders at or above 500.00
        List<Order> results = orderService.findOrdersAboveAmount(new BigDecimal("500.00"));

        // Then: only the 2000.00 and 500.00 orders are returned
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Order::getCustomerName)
                .containsExactlyInAnyOrder("Bob", "Charlie");
    }

    @Test
    @DisplayName("getRevenueByRegion aggregation produces correct totals")
    void getRevenueByRegion_producesCorrectTotals() {
        // Given: three orders — two in North, one in South
        orderService.create(buildRequest("Alice", "North", "DELIVERED",
                new BigDecimal("300.00"),
                List.of(new OrderItem("Item A", "cat", 3, new BigDecimal("100.00")))));
        orderService.create(buildRequest("Bob", "North", "DELIVERED",
                new BigDecimal("200.00"),
                List.of(new OrderItem("Item B", "cat", 2, new BigDecimal("100.00")))));
        orderService.create(buildRequest("Charlie", "South", "DELIVERED",
                new BigDecimal("400.00"),
                List.of(new OrderItem("Item C", "cat", 4, new BigDecimal("100.00")))));

        // When
        List<RevenueByRegionResult> results = orderService.getRevenueByRegion();

        // Then: North has 500.00 total, South has 400.00 total
        assertThat(results).hasSize(2);
        // Results are sorted desc by totalRevenue, so North (500) comes first
        RevenueByRegionResult northResult = results.stream()
                .filter(r -> "North".equals(r.getRegion()))
                .findFirst().orElseThrow();
        assertThat(northResult.getTotalRevenue()).isEqualByComparingTo("500.00");
        assertThat(northResult.getOrderCount()).isEqualTo(2L);

        RevenueByRegionResult southResult = results.stream()
                .filter(r -> "South".equals(r.getRegion()))
                .findFirst().orElseThrow();
        assertThat(southResult.getTotalRevenue()).isEqualByComparingTo("400.00");
        assertThat(southResult.getOrderCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getCustomerSpendingSummary correctly excludes CANCELLED orders")
    void getCustomerSpendingSummary_correctlyExcludesCancelled() {
        // Given: Alice with a delivered + cancelled order; Bob with a delivered order
        orderService.create(buildRequest("Alice", "North", "DELIVERED",
                new BigDecimal("600.00"),
                List.of(new OrderItem("TV", "electronics", 1, new BigDecimal("600.00")))));
        orderService.create(buildRequest("Alice", "North", "CANCELLED",
                new BigDecimal("999.00"),
                List.of(new OrderItem("Laptop", "electronics", 1, new BigDecimal("999.00")))));
        orderService.create(buildRequest("Bob", "South", "DELIVERED",
                new BigDecimal("100.00"),
                List.of(new OrderItem("Book", "books", 1, new BigDecimal("100.00")))));

        // When
        List<CustomerSummaryResult> results = orderService.getCustomerSpendingSummary();

        // Then: Alice only counts her 600.00 DELIVERED order, not the 999.00 CANCELLED one
        assertThat(results).hasSize(2);
        CustomerSummaryResult aliceResult = results.stream()
                .filter(r -> "Alice".equals(r.getCustomerName()))
                .findFirst().orElseThrow();
        assertThat(aliceResult.getTotalSpent()).isEqualByComparingTo("600.00");
        assertThat(aliceResult.getOrderCount()).isEqualTo(1L);
    }

    // ── Monthly revenue ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /analytics/monthly-revenue returns non-empty result when orders exist")
    void getMonthlyRevenue_returnsNonEmptyResult() {
        // Given: at least one order
        orderService.create(buildRequest("Alice", "North", "DELIVERED",
                new BigDecimal("100.00"),
                List.of(new OrderItem("Book", "books", 1, new BigDecimal("100.00")))));

        // When
        ResponseEntity<org.bson.Document[]> response = restTemplate.getForEntity(
                url("/api/orders/analytics/monthly-revenue"),
                org.bson.Document[].class);

        // Then: at least one month-group is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSizeGreaterThanOrEqualTo(1);
    }

    // ── Helper ────────────────────────────────────────────────────────────────────

    /**
     * Convenience factory to build an {@link OrderRequest} without repeating boilerplate.
     */
    private OrderRequest buildRequest(String customer, String region, String status,
                                       BigDecimal total, List<OrderItem> items) {
        return new OrderRequest(customer, region, status, total, items);
    }
}
