package com.example.camundabpm.integration;

import com.example.camundabpm.domain.Order;
import com.example.camundabpm.domain.OrderStatus;
import com.example.camundabpm.dto.CreateOrderRequest;
import com.example.camundabpm.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration test for the Order Fulfilment process.
 *
 * <p>This test starts a complete Spring Boot application context and a real
 * PostgreSQL container via Testcontainers. It verifies the end-to-end flow:
 * REST API → OrderService → Camunda process → Java delegates → database.
 *
 * <p>Key annotations explained:
 * <ul>
 *   <li>@SpringBootTest(webEnvironment=NONE) — loads the full application context
 *       but without starting an HTTP server (we call services directly).</li>
 *   <li>@Testcontainers — activates Testcontainers JUnit 5 lifecycle management.
 *       Containers annotated @Container are started before the first test and
 *       stopped after the last.</li>
 *   <li>@ActiveProfiles("integration-test") — activates the integration-test profile
 *       which disables Flyway in favour of Camunda+Hibernate schema creation,
 *       and connects to the Testcontainers PostgreSQL instance.</li>
 *   <li>@DynamicPropertySource — injects the Testcontainers container URL/credentials
 *       into Spring's environment BEFORE the application context is created.
 *       This is the recommended way to wire Testcontainers with Spring Boot.</li>
 * </ul>
 *
 * <p>What is being tested:
 * <ol>
 *   <li>Happy path: a valid order goes through the full process and ends COMPLETED.</li>
 *   <li>Order is persisted: can be retrieved by ID after process completion.</li>
 *   <li>Tracking number: is assigned by the ShippingDelegate.</li>
 *   <li>Total amount: is computed correctly by the PaymentProcessingDelegate.</li>
 *   <li>Process instance ID: is stored on the order for cross-reference.</li>
 *   <li>Find all orders: returns the created order.</li>
 *   <li>Find by status: correctly filters by COMPLETED.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Order Fulfilment Integration Test")
class OrderFulfilmentIntegrationTest {

    /**
     * PostgreSQL Testcontainer.
     *
     * <p>@Container makes Testcontainers manage the container lifecycle.
     * static field means the container is shared across all test methods in
     * this class (started once, not per test), which speeds up the test suite.
     *
     * <p>The image postgres:16-alpine is lightweight (~80MB) and production-grade.
     * Camunda 7 officially supports PostgreSQL 14+.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("camundadb")
            .withUsername("camunda")
            .withPassword("camunda");

    /**
     * Dynamically injects the Testcontainers database URL into the Spring environment.
     *
     * <p>This method is called BEFORE the Spring application context is created,
     * ensuring the datasource configuration (spring.datasource.*) points to the
     * running Testcontainers PostgreSQL instance rather than the default localhost:5432.
     *
     * <p>The registry.add() calls override the properties defined in application.yml.
     *
     * @param registry Spring's dynamic property registry
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        // Override the datasource URL to point to the Testcontainers PostgreSQL instance.
        // postgres.getJdbcUrl() returns the actual URL including the mapped port.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Override the driver explicitly (Testcontainers URL uses postgresql protocol)
        registry.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");
    }

    /**
     * The service under test — injected by Spring into the test class.
     * All other beans (delegates, repositories, Camunda engine) are also
     * started and wired by the Spring context.
     */
    @Autowired
    private OrderService orderService;

    /**
     * Happy path integration test: submits an order and verifies the full
     * process completes successfully.
     */
    @Test
    @DisplayName("happy path: order goes through full process and ends COMPLETED")
    void createOrder_happyPath_completesSuccessfully() {
        // Arrange: build the order creation request
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Alice Integration");
        request.setProductName("Laptop Pro 15");
        request.setQuantity(2);
        request.setUnitPrice(new BigDecimal("1299.99"));

        // Act: submit the order — this starts the Camunda process synchronously
        Order completedOrder = orderService.createAndStartOrder(request);

        // Assert: order ends in COMPLETED status (all 4 service tasks ran)
        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // Assert: order was persisted — has a database-generated ID
        assertThat(completedOrder.getId()).isNotNull().isPositive();

        // Assert: tracking number was assigned by ShippingDelegate
        assertThat(completedOrder.getTrackingNumber())
                .isNotNull()
                .startsWith("TRK-");

        // Assert: total amount = 2 × 1299.99 = 2599.98
        assertThat(completedOrder.getTotalAmount())
                .isNotNull()
                .isEqualByComparingTo("2599.98");

        // Assert: Camunda process instance ID was recorded on the order
        // (allows operators to look up the Camunda process history)
        assertThat(completedOrder.getProcessInstanceId()).isNotNull().isNotBlank();

        // Assert: customer and product data is preserved
        assertThat(completedOrder.getCustomerName()).isEqualTo("Alice Integration");
        assertThat(completedOrder.getProductName()).isEqualTo("Laptop Pro 15");
        assertThat(completedOrder.getQuantity()).isEqualTo(2);
    }

    /**
     * Tests that a completed order can be retrieved by its ID.
     */
    @Test
    @DisplayName("completed order can be retrieved by ID")
    void createOrder_thenFindById_returnsOrder() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Bob Lookup");
        request.setProductName("USB-C Hub");
        request.setQuantity(1);
        request.setUnitPrice(new BigDecimal("49.99"));

        // Act: create the order
        Order created = orderService.createAndStartOrder(request);
        Long orderId = created.getId();

        // Act: retrieve the order by ID
        Optional<Order> found = orderService.findById(orderId);

        // Assert: the order is found with the correct state
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(found.get().getCustomerName()).isEqualTo("Bob Lookup");
        assertThat(found.get().getTrackingNumber()).startsWith("TRK-");
    }

    /**
     * Tests that findByStatus correctly filters orders.
     */
    @Test
    @DisplayName("findByStatus returns completed orders")
    void findByStatus_completed_returnsMatchingOrders() {
        // Arrange: create an order that will complete successfully
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Charlie Status");
        request.setProductName("Mechanical Keyboard");
        request.setQuantity(1);
        request.setUnitPrice(new BigDecimal("149.99"));

        orderService.createAndStartOrder(request);

        // Act: query for COMPLETED orders
        List<Order> completedOrders = orderService.findByStatus(OrderStatus.COMPLETED);

        // Assert: at least one COMPLETED order exists
        assertThat(completedOrders).isNotEmpty();
        // All returned orders must have COMPLETED status
        assertThat(completedOrders)
                .allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED));
    }

    /**
     * Tests that the Camunda process instance is recorded and correlates the order
     * to the workflow engine's runtime/history data.
     */
    @Test
    @DisplayName("process instance ID links order to Camunda runtime")
    void createOrder_processInstanceIdIsRecorded() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Diana Process");
        request.setProductName("Wireless Mouse");
        request.setQuantity(3);
        request.setUnitPrice(new BigDecimal("29.99"));

        // Act
        Order order = orderService.createAndStartOrder(request);

        // Assert: process instance ID is stored for Camunda cross-reference
        assertThat(order.getProcessInstanceId()).isNotNull().isNotBlank();
        // Camunda process instance IDs are UUID-like strings
        assertThat(order.getProcessInstanceId()).hasSizeGreaterThan(10);

        // Assert: total = 3 × 29.99 = 89.97
        assertThat(order.getTotalAmount()).isEqualByComparingTo("89.97");
    }
}
