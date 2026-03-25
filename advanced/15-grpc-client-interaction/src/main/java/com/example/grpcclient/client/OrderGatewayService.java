package com.example.grpcclient.client;

import com.example.grpcclient.proto.CheckStockRequest;
import com.example.grpcclient.proto.CheckStockResponse;
import com.example.grpcclient.proto.CreateOrderRequest;
import com.example.grpcclient.proto.GetOrderRequest;
import com.example.grpcclient.proto.InventoryItem;
import com.example.grpcclient.proto.InventoryServiceGrpc;
import com.example.grpcclient.proto.ListInventoryRequest;
import com.example.grpcclient.proto.ListOrdersRequest;
import com.example.grpcclient.proto.Order;
import com.example.grpcclient.proto.OrderItem;
import com.example.grpcclient.proto.OrderServiceGrpc;
import com.example.grpcclient.proto.ReleaseStockRequest;
import com.example.grpcclient.proto.ReleaseStockResponse;
import com.example.grpcclient.proto.ReserveStockRequest;
import com.example.grpcclient.proto.ReserveStockResponse;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * gRPC CLIENT service — the heart of this mini-project.
 *
 * <p>This class is the REST gateway's bridge to the two internal gRPC microservices.
 * It demonstrates the CLIENT side of gRPC communication:
 *
 * <pre>
 *   REST Controller ──► OrderGatewayService ──gRPC──► OrderGrpcService   (port 9091)
 *                                              │
 *                                              └──gRPC──► InventoryGrpcService (port 9092)
 * </pre>
 *
 * <p>Key gRPC client concepts demonstrated here:
 *
 * <ol>
 *   <li><b>{@code @GrpcClient} annotation</b>:
 *       Injects a fully managed gRPC stub (blocking or async) into this Spring bean.
 *       The annotation value (e.g., "order-service") corresponds to a channel name
 *       defined in {@code application.yml} under {@code grpc.client.<name>.address}.
 *       The starter creates the channel, manages its lifecycle (connection pooling,
 *       reconnection), and provides the stub for RPC calls.
 *   </li>
 *
 *   <li><b>Blocking vs. async stubs</b>:
 *       We use blocking stubs here for simplicity (synchronous calls that block the
 *       calling thread until the server responds). For production, async or reactive
 *       stubs are preferred for better throughput under high concurrency.
 *   </li>
 *
 *   <li><b>Server-streaming client handling</b>:
 *       For ListOrders and ListInventory, the blocking stub returns an
 *       {@link java.util.Iterator} that can be traversed to collect all streamed
 *       responses.
 *   </li>
 *
 *   <li><b>Multi-service fan-out</b>:
 *       {@link #createOrderWithInventoryCheck} demonstrates orchestrating TWO
 *       gRPC calls — first checking/reserving inventory, then creating the order.
 *       This is a fundamental pattern in microservice architectures.
 *   </li>
 *
 *   <li><b>gRPC error handling</b>:
 *       {@link StatusRuntimeException} is caught and translated into meaningful
 *       application exceptions, keeping the gRPC error model encapsulated here
 *       and not leaking into the REST controllers.
 *   </li>
 * </ol>
 */
@Service
public class OrderGatewayService {

    private static final Logger log = LoggerFactory.getLogger(OrderGatewayService.class);

    /**
     * gRPC blocking stub for the OrderService.
     *
     * <p>{@code @GrpcClient("order-service")} tells the net.devh starter to:
     * <ol>
     *   <li>Look up the channel configuration under {@code grpc.client.order-service.address}
     *       in application.yml.</li>
     *   <li>Create a managed gRPC channel to that address (connection pooling, TLS, etc.).</li>
     *   <li>Create a blocking stub from the channel and inject it here.</li>
     * </ol>
     *
     * <p>The blocking stub is thread-safe and can be called from multiple threads concurrently.
     * It blocks the calling thread until the server sends a response or an error occurs.
     */
    @GrpcClient("order-service")
    private OrderServiceGrpc.OrderServiceBlockingStub orderStub;

    /**
     * gRPC blocking stub for the InventoryService.
     *
     * <p>Configured via {@code grpc.client.inventory-service.address} in application.yml.
     * This demonstrates using @GrpcClient with a second, separate gRPC service.
     */
    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    // =========================================================================
    // Order operations (delegates to OrderService via gRPC)
    // =========================================================================

    /**
     * Create a new order by calling the OrderService via gRPC.
     *
     * <p>This demonstrates a simple unary gRPC call from a client perspective:
     * <ol>
     *   <li>Build a {@link CreateOrderRequest} protobuf message.</li>
     *   <li>Call the blocking stub — the thread blocks until the server responds.</li>
     *   <li>Return the server's {@link Order} response to the REST controller.</li>
     * </ol>
     *
     * @param customerId the customer placing the order
     * @param items      the list of protobuf {@link OrderItem} messages
     * @return the created {@link Order} protobuf message (with server-assigned ID)
     * @throws GrpcServiceException if the gRPC call fails (e.g., INVALID_ARGUMENT)
     */
    public Order createOrder(String customerId, List<OrderItem> items) {
        log.info("gRPC client: calling OrderService.createOrder for customerId='{}'", customerId);

        // Build the protobuf request message using the builder pattern.
        CreateOrderRequest request = CreateOrderRequest.newBuilder()
                .setCustomerId(customerId)
                .addAllItems(items)
                .build();

        try {
            // Make the unary gRPC call — blocks until server responds.
            Order order = orderStub.createOrder(request);
            log.info("gRPC client: order created with id='{}'", order.getOrderId());
            return order;
        } catch (StatusRuntimeException e) {
            // Translate the gRPC status code into an application exception.
            // This keeps gRPC error handling encapsulated in the client layer.
            log.error("gRPC client: createOrder failed — status={}, message={}",
                    e.getStatus().getCode(), e.getMessage());
            throw new GrpcServiceException("Failed to create order: " + e.getStatus().getDescription(), e);
        }
    }

    /**
     * Retrieve an order by its ID by calling the OrderService via gRPC.
     *
     * <p>Demonstrates unary RPC error handling:
     * <ul>
     *   <li>If the order is found, the stub returns it directly.</li>
     *   <li>If not found, the server returns {@code NOT_FOUND} which the stub
     *       throws as a {@link StatusRuntimeException}.</li>
     * </ul>
     *
     * @param orderId the UUID string order ID to look up
     * @return the {@link Order} protobuf message
     * @throws GrpcServiceException if the order is not found or the call fails
     */
    public Order getOrder(String orderId) {
        log.info("gRPC client: calling OrderService.getOrder for orderId='{}'", orderId);

        GetOrderRequest request = GetOrderRequest.newBuilder()
                .setOrderId(orderId)
                .build();

        try {
            return orderStub.getOrder(request);
        } catch (StatusRuntimeException e) {
            log.error("gRPC client: getOrder failed — status={}, message={}",
                    e.getStatus().getCode(), e.getMessage());
            throw new GrpcServiceException("Failed to get order: " + e.getStatus().getDescription(), e);
        }
    }

    /**
     * List all orders for a customer by calling the OrderService via gRPC.
     *
     * <p>Demonstrates consuming a SERVER-STREAMING RPC from the client:
     * <ol>
     *   <li>The blocking stub's {@code listOrders()} call returns an
     *       {@link java.util.Iterator}{@code <Order>}.</li>
     *   <li>We iterate over it to collect all streamed {@link Order} messages
     *       into a {@link List}.</li>
     *   <li>The iterator blocks on each {@code next()} call until the next message
     *       arrives from the server, then returns when the stream is closed.</li>
     * </ol>
     *
     * @param customerId the customer whose orders should be listed
     * @return list of all {@link Order} protobuf messages for that customer
     * @throws GrpcServiceException if the gRPC call fails
     */
    public List<Order> listOrders(String customerId) {
        log.info("gRPC client: calling OrderService.listOrders for customerId='{}'", customerId);

        ListOrdersRequest request = ListOrdersRequest.newBuilder()
                .setCustomerId(customerId)
                .build();

        try {
            // The blocking stub returns an Iterator for streaming RPCs.
            // We collect all streamed messages into a list.
            List<Order> orders = new ArrayList<>();
            orderStub.listOrders(request).forEachRemaining(orders::add);
            log.info("gRPC client: received {} orders for customerId='{}'", orders.size(), customerId);
            return orders;
        } catch (StatusRuntimeException e) {
            log.error("gRPC client: listOrders failed — status={}, message={}",
                    e.getStatus().getCode(), e.getMessage());
            throw new GrpcServiceException("Failed to list orders: " + e.getStatus().getDescription(), e);
        }
    }

    // =========================================================================
    // Inventory operations (delegates to InventoryService via gRPC)
    // =========================================================================

    /**
     * Check available stock for a SKU by calling the InventoryService via gRPC.
     *
     * @param sku the product SKU to check
     * @return {@link CheckStockResponse} with available quantity and availability flag
     * @throws GrpcServiceException if the SKU is not found or the call fails
     */
    public CheckStockResponse checkStock(String sku) {
        log.info("gRPC client: calling InventoryService.checkStock for sku='{}'", sku);

        CheckStockRequest request = CheckStockRequest.newBuilder()
                .setSku(sku)
                .build();

        try {
            return inventoryStub.checkStock(request);
        } catch (StatusRuntimeException e) {
            log.error("gRPC client: checkStock failed — status={}, message={}",
                    e.getStatus().getCode(), e.getMessage());
            throw new GrpcServiceException("Failed to check stock: " + e.getStatus().getDescription(), e);
        }
    }

    /**
     * Reserve stock for an order by calling the InventoryService via gRPC.
     *
     * @param sku      the product SKU to reserve
     * @param quantity the number of units to reserve
     * @param orderId  the order ID this reservation is for
     * @return {@link ReserveStockResponse} with success flag and updated inventory
     * @throws GrpcServiceException if the SKU is not found or the call fails
     */
    public ReserveStockResponse reserveStock(String sku, int quantity, String orderId) {
        log.info("gRPC client: calling InventoryService.reserveStock for sku='{}', qty={}", sku, quantity);

        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setSku(sku)
                .setQuantity(quantity)
                .setOrderId(orderId)
                .build();

        try {
            return inventoryStub.reserveStock(request);
        } catch (StatusRuntimeException e) {
            log.error("gRPC client: reserveStock failed — status={}, message={}",
                    e.getStatus().getCode(), e.getMessage());
            throw new GrpcServiceException("Failed to reserve stock: " + e.getStatus().getDescription(), e);
        }
    }

    /**
     * Release reserved stock by calling the InventoryService via gRPC.
     *
     * @param sku      the product SKU whose reservation to release
     * @param quantity the number of units to release
     * @param orderId  the order ID the reservation was for
     * @return {@link ReleaseStockResponse} with success flag
     * @throws GrpcServiceException if the SKU is not found or the call fails
     */
    public ReleaseStockResponse releaseStock(String sku, int quantity, String orderId) {
        log.info("gRPC client: calling InventoryService.releaseStock for sku='{}', qty={}", sku, quantity);

        ReleaseStockRequest request = ReleaseStockRequest.newBuilder()
                .setSku(sku)
                .setQuantity(quantity)
                .setOrderId(orderId)
                .build();

        try {
            return inventoryStub.releaseStock(request);
        } catch (StatusRuntimeException e) {
            log.error("gRPC client: releaseStock failed — status={}, message={}",
                    e.getStatus().getCode(), e.getMessage());
            throw new GrpcServiceException("Failed to release stock: " + e.getStatus().getDescription(), e);
        }
    }

    /**
     * List all inventory items by calling the InventoryService via gRPC.
     *
     * <p>Demonstrates consuming a SERVER-STREAMING RPC for inventory listing.
     * Each {@link InventoryItem} is streamed from the server and collected here.
     *
     * @param onlyAvailable if true, only return items with available quantity > 0
     * @return list of all {@link InventoryItem} protobuf messages
     * @throws GrpcServiceException if the gRPC call fails
     */
    public List<InventoryItem> listInventory(boolean onlyAvailable) {
        log.info("gRPC client: calling InventoryService.listInventory with onlyAvailable={}", onlyAvailable);

        ListInventoryRequest request = ListInventoryRequest.newBuilder()
                .setOnlyAvailable(onlyAvailable)
                .build();

        try {
            // Consume the server-streaming response using forEachRemaining.
            List<InventoryItem> items = new ArrayList<>();
            inventoryStub.listInventory(request).forEachRemaining(items::add);
            log.info("gRPC client: received {} inventory items", items.size());
            return items;
        } catch (StatusRuntimeException e) {
            log.error("gRPC client: listInventory failed — status={}, message={}",
                    e.getStatus().getCode(), e.getMessage());
            throw new GrpcServiceException("Failed to list inventory: " + e.getStatus().getDescription(), e);
        }
    }

    // =========================================================================
    // Orchestration: multi-service fan-out
    // =========================================================================

    /**
     * Create an order with automatic inventory reservation — a multi-service orchestration.
     *
     * <p>This method demonstrates a fundamental microservice pattern:
     * one incoming request triggers multiple gRPC calls to separate services.
     *
     * <p>Flow:
     * <ol>
     *   <li>For each item in the order, call {@code InventoryService.ReserveStock} via gRPC.</li>
     *   <li>If ALL reservations succeed, call {@code OrderService.CreateOrder} via gRPC.</li>
     *   <li>If ANY reservation fails, release all previously made reservations (compensation)
     *       and return an error response to the caller.</li>
     * </ol>
     *
     * <p>This is a simplified version of the Saga pattern (choreography-based compensation).
     * Production systems would use durable sagas with event sourcing for failure recovery.
     *
     * @param customerId   the customer placing the order
     * @param items        the protobuf OrderItems to include
     * @return result containing the created {@link Order} or an error message
     */
    public OrderCreationResult createOrderWithInventoryCheck(String customerId, List<OrderItem> items) {
        log.info("gRPC client: starting order creation with inventory check for customerId='{}'", customerId);

        // Track which reservations were made so we can compensate on partial failure.
        List<ReservationRecord> madeReservations = new ArrayList<>();

        // Step 1: Reserve inventory for every item in the order.
        for (OrderItem item : items) {
            // Use a temporary placeholder order ID for the reservation.
            // The real order ID is only available after the order is created.
            String tempOrderId = "pending-" + customerId;

            try {
                ReserveStockResponse reserveResponse = reserveStock(
                        item.getSku(), item.getQuantity(), tempOrderId);

                if (!reserveResponse.getSuccess()) {
                    // Insufficient stock — release all previous reservations (compensation).
                    log.warn("gRPC client: insufficient stock for sku='{}', releasing {} prior reservations",
                            item.getSku(), madeReservations.size());
                    compensateReservations(madeReservations);
                    return OrderCreationResult.failure(
                            "Insufficient stock for SKU: " + item.getSku()
                                    + " (available: " + reserveResponse.getItem().getAvailableQuantity()
                                    + ", requested: " + item.getQuantity() + ")");
                }

                // Track the successful reservation for potential compensation.
                madeReservations.add(new ReservationRecord(item.getSku(), item.getQuantity(), tempOrderId));
                log.info("gRPC client: reserved {} units of sku='{}'", item.getQuantity(), item.getSku());

            } catch (GrpcServiceException e) {
                // gRPC call failure — release already-made reservations.
                log.error("gRPC client: reservation failed for sku='{}' — compensating", item.getSku());
                compensateReservations(madeReservations);
                return OrderCreationResult.failure("Inventory service error: " + e.getMessage());
            }
        }

        // Step 2: All inventory reserved — now create the order via gRPC.
        try {
            Order createdOrder = createOrder(customerId, items);
            log.info("gRPC client: order created successfully with id='{}'", createdOrder.getOrderId());
            return OrderCreationResult.success(createdOrder);

        } catch (GrpcServiceException e) {
            // Order creation failed — release all inventory reservations.
            log.error("gRPC client: order creation failed — releasing {} reservations", madeReservations.size());
            compensateReservations(madeReservations);
            return OrderCreationResult.failure("Order service error: " + e.getMessage());
        }
    }

    /**
     * Release all inventory reservations that were made during a failed order creation.
     *
     * <p>This is the compensation step — it is called when a later step in the
     * {@link #createOrderWithInventoryCheck} flow fails, ensuring inventory
     * reservations from earlier steps are rolled back.
     *
     * @param reservations the list of reservations to release
     */
    private void compensateReservations(List<ReservationRecord> reservations) {
        for (ReservationRecord reservation : reservations) {
            try {
                releaseStock(reservation.sku(), reservation.quantity(), reservation.orderId());
                log.info("gRPC client: compensation — released {} units of sku='{}'",
                        reservation.quantity(), reservation.sku());
            } catch (GrpcServiceException e) {
                // Log compensation failure but don't re-throw — we're already in error recovery.
                log.error("gRPC client: compensation failed for sku='{}': {}",
                        reservation.sku(), e.getMessage());
            }
        }
    }

    // =========================================================================
    // Result and record types
    // =========================================================================

    /**
     * Result wrapper for {@link #createOrderWithInventoryCheck}.
     *
     * <p>Encapsulates either a successfully created {@link Order} or an error message,
     * without throwing exceptions for business-level failures (insufficient stock, etc.).
     */
    public static class OrderCreationResult {

        /** true if the order was created successfully; false if a business error occurred. */
        private final boolean success;

        /** The created order (non-null when success = true). */
        private final Order order;

        /** Error message (non-null when success = false). */
        private final String errorMessage;

        private OrderCreationResult(boolean success, Order order, String errorMessage) {
            this.success = success;
            this.order = order;
            this.errorMessage = errorMessage;
        }

        /** Factory method for a successful result. */
        public static OrderCreationResult success(Order order) {
            return new OrderCreationResult(true, order, null);
        }

        /** Factory method for a failure result. */
        public static OrderCreationResult failure(String errorMessage) {
            return new OrderCreationResult(false, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public Order getOrder() { return order; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Value record tracking a single inventory reservation for compensation purposes.
     *
     * @param sku      the reserved product SKU
     * @param quantity the number of units reserved
     * @param orderId  the temporary order ID used for the reservation
     */
    private record ReservationRecord(String sku, int quantity, String orderId) {}
}
