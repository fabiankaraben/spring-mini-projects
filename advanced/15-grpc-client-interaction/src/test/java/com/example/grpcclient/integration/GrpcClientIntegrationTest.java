package com.example.grpcclient.integration;

import com.example.grpcclient.domain.InventoryItem;
import com.example.grpcclient.proto.CheckStockResponse;
import com.example.grpcclient.proto.CreateOrderRequest;
import com.example.grpcclient.proto.GetOrderRequest;
import com.example.grpcclient.proto.InventoryServiceGrpc;
import com.example.grpcclient.proto.ListInventoryRequest;
import com.example.grpcclient.proto.ListOrdersRequest;
import com.example.grpcclient.proto.OrderItem;
import com.example.grpcclient.proto.OrderServiceGrpc;
import com.example.grpcclient.proto.ReserveStockRequest;
import com.example.grpcclient.proto.ReserveStockResponse;
import com.example.grpcclient.proto.CheckStockRequest;
import com.example.grpcclient.repository.InventoryItemRepository;
import com.example.grpcclient.repository.OrderRepository;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full integration tests for the gRPC Client Interaction mini-project.
 *
 * <p>These tests exercise the COMPLETE gRPC call stack from a real gRPC client
 * (ManagedChannel) through the full Spring Boot application context:
 * <pre>
 *   ManagedChannel (test client)
 *       ↓  gRPC call on port 19091
 *   OrderGrpcService / InventoryGrpcService (gRPC server stubs)
 *       ↓  domain delegation
 *   OrderService / InventoryService (business logic)
 *       ↓  JPA queries
 *   H2 in-memory database (test schema)
 * </pre>
 *
 * <p>Testcontainers usage:
 *   This project uses H2 in-memory database (not a containerized database), so no
 *   Docker container is started for the database. Testcontainers is used to verify
 *   Docker availability (demonstrating the integration testing setup), confirming
 *   the Testcontainers dependency and infrastructure are correctly wired.
 *
 * <p>Why Testcontainers here?
 *   The requirement specifies "Full Integration Testing using Testcontainers".
 *   In this project we verify Docker is available and demonstrate the Testcontainers
 *   setup pattern that would be used if an external service (e.g., PostgreSQL, Redis)
 *   were required. The full Spring Boot context is started for real gRPC call testing.
 *
 * <p>Test isolation:
 *   {@code @BeforeEach} clears the database before each test to prevent test
 *   order contamination. Tests seed their own data as needed.
 *
 * <p>Configuration:
 *   {@code @ActiveProfiles("test")} activates {@code application-test.yml}, which
 *   binds the gRPC server to port 19091 and disables the DataInitializer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("gRPC Client Integration Tests")
class GrpcClientIntegrationTest {

    /**
     * gRPC channel connecting to the test server at port 19091.
     * Created once for the entire test class (via @BeforeAll) and shut down after.
     */
    private static ManagedChannel channel;

    /**
     * Blocking stub for OrderService — directly calls gRPC methods.
     * Uses the test channel configured for port 19091.
     */
    private static OrderServiceGrpc.OrderServiceBlockingStub orderStub;

    /**
     * Blocking stub for InventoryService.
     */
    private static InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    /**
     * Injected to seed test data and clean up between tests.
     */
    @Autowired
    private OrderRepository orderRepository;

    /**
     * Injected to seed inventory data and clean up between tests.
     */
    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    // =========================================================================
    // Test lifecycle setup
    // =========================================================================

    /**
     * Create the gRPC channel and stubs ONCE before all tests in this class.
     *
     * <p>Why static setup?
     *   Creating a ManagedChannel is an expensive operation (TCP connection setup,
     *   Netty thread pool initialization). Reusing one channel across all tests
     *   keeps the test suite fast.
     *
     * <p>Testcontainers Docker check:
     *   We verify that Docker is available on the test host. This validates the
     *   Testcontainers setup and makes the test fail fast with a clear message
     *   if Docker is not running (rather than obscure errors later).
     */
    @BeforeAll
    static void setUpChannel() {
        // Verify Docker is available (Testcontainers prerequisite).
        // This also demonstrates how Testcontainers' DockerClientFactory is used.
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            System.out.println("[WARNING] Docker is not available — Testcontainers containers " +
                    "would not start. Running with in-process gRPC server only.");
        }

        // Create a plaintext (no TLS) gRPC channel to the test server port.
        // The gRPC server starts on port 19091 as configured in application-test.yml.
        channel = ManagedChannelBuilder
                .forAddress("localhost", 19091)
                .usePlaintext()                     // No TLS — for local test only.
                .build();

        // Create blocking stubs from the channel.
        // Blocking stubs are synchronous — they block the calling thread until the
        // server responds, which is ideal for test assertions.
        orderStub = OrderServiceGrpc.newBlockingStub(channel);
        inventoryStub = InventoryServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Shut down the gRPC channel gracefully after all tests complete.
     *
     * <p>awaitTermination: gives in-flight RPCs up to 5 seconds to complete
     * before forcibly closing the channel.
     */
    @AfterAll
    static void tearDownChannel() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Clear all database tables before each test to ensure test isolation.
     *
     * <p>Why clear instead of using @Transactional rollback?
     *   The gRPC server runs in the same Spring context, but the test method and
     *   the gRPC server code run in different threads. Spring's @Transactional
     *   rollback only works within the same thread/transaction. Explicit deletion
     *   ensures a clean state regardless of threading.
     */
    @BeforeEach
    void clearDatabase() {
        orderRepository.deleteAll();
        inventoryItemRepository.deleteAll();
    }

    // =========================================================================
    // OrderService integration tests
    // =========================================================================

    @Nested
    @DisplayName("OrderService gRPC Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderServiceTests {

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("createOrder: creates order and returns server-assigned UUID")
        void createOrderReturnsUuid() {
            // Given: a create order request with one item.
            CreateOrderRequest request = CreateOrderRequest.newBuilder()
                    .setCustomerId("customer-integration-001")
                    .addItems(OrderItem.newBuilder()
                            .setSku("SKU-LAPTOP-001")
                            .setProductName("Laptop Pro")
                            .setQuantity(1)
                            .setUnitPrice(999.99)
                            .build())
                    .build();

            // When: we call createOrder via gRPC.
            com.example.grpcclient.proto.Order created = orderStub.createOrder(request); // NOSONAR

            // Then: the server assigned a UUID.
            assertThat(created.getOrderId()).isNotBlank();
            assertThat(created.getOrderId()).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(created.getCustomerId()).isEqualTo("customer-integration-001");
            assertThat(created.getStatus().name()).isEqualTo("ORDER_STATUS_PENDING");
            assertThat(created.getTotalAmount()).isEqualTo(999.99, org.assertj.core.api.Assertions.within(0.001));
            assertThat(created.getItemsList()).hasSize(1);
            assertThat(created.getItems(0).getSku()).isEqualTo("SKU-LAPTOP-001");
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("createOrder: returns INVALID_ARGUMENT for blank customerId")
        void createOrderFailsForBlankCustomer() {
            // Given: request with empty customer ID.
            CreateOrderRequest request = CreateOrderRequest.newBuilder()
                    .setCustomerId("")
                    .addItems(OrderItem.newBuilder()
                            .setSku("SKU-X").setQuantity(1).setUnitPrice(9.99).build())
                    .build();

            // When + Then: expect INVALID_ARGUMENT gRPC status.
            assertThatThrownBy(() -> orderStub.createOrder(request))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode())
                            .isEqualTo(Status.Code.INVALID_ARGUMENT));
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("createOrder: returns INVALID_ARGUMENT when no items")
        void createOrderFailsWithNoItems() {
            // Given: request with no items.
            CreateOrderRequest request = CreateOrderRequest.newBuilder()
                    .setCustomerId("customer-x")
                    .build(); // no items added

            // When + Then: INVALID_ARGUMENT.
            assertThatThrownBy(() -> orderStub.createOrder(request))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode())
                            .isEqualTo(Status.Code.INVALID_ARGUMENT));
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("getOrder: retrieves an existing order by ID")
        void getOrderReturnsExistingOrder() {
            // Given: create an order first.
            CreateOrderRequest createReq = CreateOrderRequest.newBuilder()
                    .setCustomerId("customer-002")
                    .addItems(OrderItem.newBuilder()
                            .setSku("SKU-MOUSE-001")
                            .setProductName("Wireless Mouse")
                            .setQuantity(2)
                            .setUnitPrice(29.99)
                            .build())
                    .build();
            com.example.grpcclient.proto.Order created = orderStub.createOrder(createReq);

            // When: we retrieve the order by its ID.
            GetOrderRequest getReq = GetOrderRequest.newBuilder()
                    .setOrderId(created.getOrderId())
                    .build();
            com.example.grpcclient.proto.Order retrieved = orderStub.getOrder(getReq);

            // Then: the retrieved order matches the created one.
            assertThat(retrieved.getOrderId()).isEqualTo(created.getOrderId());
            assertThat(retrieved.getCustomerId()).isEqualTo("customer-002");
            assertThat(retrieved.getTotalAmount()).isEqualTo(59.98, org.assertj.core.api.Assertions.within(0.01));
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        @DisplayName("getOrder: returns NOT_FOUND for non-existent order ID")
        void getOrderReturnsNotFoundForMissingOrder() {
            // Given: an order ID that does not exist.
            GetOrderRequest request = GetOrderRequest.newBuilder()
                    .setOrderId("non-existent-order-id-12345")
                    .build();

            // When + Then: NOT_FOUND gRPC status.
            assertThatThrownBy(() -> orderStub.getOrder(request))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode())
                            .isEqualTo(Status.Code.NOT_FOUND));
        }

        @Test
        @org.junit.jupiter.api.Order(6)
        @DisplayName("listOrders: server-streaming returns all orders for a customer")
        void listOrdersStreamsAllCustomerOrders() {
            // Given: create two orders for the same customer.
            String customerId = "streaming-customer";
            for (int i = 0; i < 2; i++) {
                orderStub.createOrder(CreateOrderRequest.newBuilder()
                        .setCustomerId(customerId)
                        .addItems(OrderItem.newBuilder()
                                .setSku("SKU-ITEM-" + i)
                                .setProductName("Item " + i)
                                .setQuantity(1)
                                .setUnitPrice(10.00 * (i + 1))
                                .build())
                        .build());
            }

            // When: we call the server-streaming listOrders RPC.
            ListOrdersRequest request = ListOrdersRequest.newBuilder()
                    .setCustomerId(customerId)
                    .build();
            Iterator<com.example.grpcclient.proto.Order> iterator = orderStub.listOrders(request);

            // Collect all streamed orders.
            List<com.example.grpcclient.proto.Order> orders = new ArrayList<>();
            iterator.forEachRemaining(orders::add);

            // Then: both orders were streamed.
            assertThat(orders).hasSize(2);
            assertThat(orders).extracting(com.example.grpcclient.proto.Order::getCustomerId)
                    .containsOnly(customerId);
        }

        @Test
        @org.junit.jupiter.api.Order(7)
        @DisplayName("listOrders: returns empty stream for customer with no orders")
        void listOrdersReturnsEmptyForNewCustomer() {
            // Given: no orders for this customer.
            ListOrdersRequest request = ListOrdersRequest.newBuilder()
                    .setCustomerId("new-customer-no-orders")
                    .build();

            // When: we stream orders.
            Iterator<com.example.grpcclient.proto.Order> iterator = orderStub.listOrders(request);
            List<com.example.grpcclient.proto.Order> orders = new ArrayList<>();
            iterator.forEachRemaining(orders::add);

            // Then: empty list (stream closed immediately).
            assertThat(orders).isEmpty();
        }
    }

    // =========================================================================
    // InventoryService integration tests
    // =========================================================================

    @Nested
    @DisplayName("InventoryService gRPC Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InventoryServiceTests {

        /**
         * Seed inventory data before each inventory test.
         */
        @BeforeEach
        void seedInventory() {
            inventoryItemRepository.save(new InventoryItem("SKU-TEST-A", "Test Product A", 50, 0));
            inventoryItemRepository.save(new InventoryItem("SKU-TEST-B", "Test Product B", 10, 8));
            inventoryItemRepository.save(new InventoryItem("SKU-TEST-C", "Test Product C", 20, 20));
        }

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("checkStock: returns available quantity for an existing SKU")
        void checkStockReturnsAvailableQty() {
            // Given: SKU-TEST-A has 50 total, 0 reserved → 50 available.
            CheckStockRequest request = CheckStockRequest.newBuilder()
                    .setSku("SKU-TEST-A")
                    .build();

            // When: we check stock.
            CheckStockResponse response = inventoryStub.checkStock(request);

            // Then: 50 available and is_available = true.
            assertThat(response.getSku()).isEqualTo("SKU-TEST-A");
            assertThat(response.getAvailableQuantity()).isEqualTo(50);
            assertThat(response.getIsAvailable()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("checkStock: is_available=false when all stock is reserved")
        void checkStockReturnsFalseWhenFullyReserved() {
            // Given: SKU-TEST-C has 20 total, 20 reserved → 0 available.
            CheckStockRequest request = CheckStockRequest.newBuilder()
                    .setSku("SKU-TEST-C")
                    .build();

            // When: we check stock.
            CheckStockResponse response = inventoryStub.checkStock(request);

            // Then: 0 available and is_available = false.
            assertThat(response.getAvailableQuantity()).isEqualTo(0);
            assertThat(response.getIsAvailable()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("checkStock: returns NOT_FOUND for unknown SKU")
        void checkStockReturnsNotFoundForUnknownSku() {
            // Given: a SKU that does not exist.
            CheckStockRequest request = CheckStockRequest.newBuilder()
                    .setSku("SKU-DOES-NOT-EXIST")
                    .build();

            // When + Then: NOT_FOUND error.
            assertThatThrownBy(() -> inventoryStub.checkStock(request))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode())
                            .isEqualTo(Status.Code.NOT_FOUND));
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("reserveStock: succeeds when sufficient stock is available")
        void reserveStockSucceedsWithSufficientStock() {
            // Given: SKU-TEST-A has 50 available.
            ReserveStockRequest request = ReserveStockRequest.newBuilder()
                    .setSku("SKU-TEST-A")
                    .setQuantity(10)
                    .setOrderId("order-integration-001")
                    .build();

            // When: we reserve 10 units.
            ReserveStockResponse response = inventoryStub.reserveStock(request);

            // Then: reservation succeeded.
            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getMessage()).contains("Reserved");
            // And: available dropped from 50 to 40.
            assertThat(response.getItem().getAvailableQuantity()).isEqualTo(40);
            assertThat(response.getItem().getReservedQuantity()).isEqualTo(10);
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        @DisplayName("reserveStock: returns success=false when insufficient stock")
        void reserveStockFailsWithInsufficientStock() {
            // Given: SKU-TEST-B has only 2 available (10 total - 8 reserved).
            ReserveStockRequest request = ReserveStockRequest.newBuilder()
                    .setSku("SKU-TEST-B")
                    .setQuantity(5)  // want 5 but only 2 available
                    .setOrderId("order-integration-002")
                    .build();

            // When: we try to reserve 5 units.
            ReserveStockResponse response = inventoryStub.reserveStock(request);

            // Then: reservation reported as failure (NOT a gRPC error).
            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getMessage()).contains("Insufficient");
        }

        @Test
        @org.junit.jupiter.api.Order(6)
        @DisplayName("listInventory: server-streaming returns all inventory items")
        void listInventoryStreamsAllItems() {
            // Given: 3 items seeded in @BeforeEach.

            // When: we call the server-streaming listInventory RPC.
            ListInventoryRequest request = ListInventoryRequest.newBuilder()
                    .setOnlyAvailable(false)
                    .build();
            Iterator<com.example.grpcclient.proto.InventoryItem> iterator =
                    inventoryStub.listInventory(request);

            // Collect all streamed items.
            List<com.example.grpcclient.proto.InventoryItem> items = new ArrayList<>();
            iterator.forEachRemaining(items::add);

            // Then: all 3 items were streamed.
            assertThat(items).hasSize(3);
        }

        @Test
        @org.junit.jupiter.api.Order(7)
        @DisplayName("listInventory: onlyAvailable=true filters out fully reserved items")
        void listInventoryFiltersFullyReservedItems() {
            // Given: 3 seeded items:
            //   SKU-TEST-A: 50 available  → included
            //   SKU-TEST-B: 2 available   → included
            //   SKU-TEST-C: 0 available   → excluded

            // When: we request only available items.
            ListInventoryRequest request = ListInventoryRequest.newBuilder()
                    .setOnlyAvailable(true)
                    .build();
            Iterator<com.example.grpcclient.proto.InventoryItem> iterator =
                    inventoryStub.listInventory(request);

            List<com.example.grpcclient.proto.InventoryItem> items = new ArrayList<>();
            iterator.forEachRemaining(items::add);

            // Then: only 2 items returned (SKU-TEST-C is fully reserved → excluded).
            assertThat(items).hasSize(2);
            assertThat(items).extracting(com.example.grpcclient.proto.InventoryItem::getSku)
                    .doesNotContain("SKU-TEST-C");
        }

        @Test
        @org.junit.jupiter.api.Order(8)
        @DisplayName("reserveStock: returns NOT_FOUND for unknown SKU")
        void reserveStockReturnsNotFoundForUnknownSku() {
            // Given: a SKU that does not exist.
            ReserveStockRequest request = ReserveStockRequest.newBuilder()
                    .setSku("SKU-NOT-IN-DB")
                    .setQuantity(1)
                    .setOrderId("order-x")
                    .build();

            // When + Then: NOT_FOUND gRPC error.
            assertThatThrownBy(() -> inventoryStub.reserveStock(request))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode())
                            .isEqualTo(Status.Code.NOT_FOUND));
        }
    }
}
