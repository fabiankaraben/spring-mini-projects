package com.example.grpcclient.web;

import com.example.grpcclient.client.GrpcServiceException;
import com.example.grpcclient.client.OrderGatewayService;
import com.example.grpcclient.proto.CheckStockResponse;
import com.example.grpcclient.proto.InventoryItem;
import com.example.grpcclient.proto.Order;
import com.example.grpcclient.proto.OrderItem;
import com.example.grpcclient.proto.ReserveStockResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller that exposes HTTP endpoints for the Order and Inventory domain.
 *
 * <p>This controller is the entry point for external HTTP clients (curl, browsers,
 * frontend apps). Internally, it delegates ALL work to {@link OrderGatewayService},
 * which in turn calls the internal gRPC microservices. The controller itself has
 * no knowledge of gRPC — it only speaks HTTP/JSON.
 *
 * <p>Architecture flow:
 * <pre>
 *   HTTP Client ──JSON──► OrderController (this class)
 *                                │
 *                         OrderGatewayService (@GrpcClient stubs)
 *                         ├──gRPC──► OrderGrpcService      (port 9091)
 *                         └──gRPC──► InventoryGrpcService  (port 9092)
 * </pre>
 *
 * <p>Endpoints exposed:
 * <ul>
 *   <li>POST /api/orders              — create order (with inventory check).</li>
 *   <li>GET  /api/orders/{orderId}    — get order by ID.</li>
 *   <li>GET  /api/orders?customerId=X — list orders for a customer.</li>
 *   <li>GET  /api/inventory           — list all inventory items.</li>
 *   <li>GET  /api/inventory/{sku}     — check stock for a SKU.</li>
 *   <li>POST /api/inventory/{sku}/reserve — reserve stock.</li>
 *   <li>POST /api/inventory/{sku}/release — release reserved stock.</li>
 * </ul>
 *
 * <p>Error handling:
 *   {@link GrpcServiceException} is caught and translated to appropriate HTTP status codes.
 *   The underlying gRPC {@link StatusRuntimeException} status code is used to determine
 *   the HTTP response code (NOT_FOUND → 404, INVALID_ARGUMENT → 400, etc.).
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    /**
     * The gRPC client service — delegates all business operations to gRPC microservices.
     */
    private final OrderGatewayService gatewayService;

    /**
     * Constructor injection.
     *
     * @param gatewayService the gRPC gateway service that calls the internal microservices
     */
    public OrderController(OrderGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    // =========================================================================
    // Order endpoints
    // =========================================================================

    /**
     * POST /api/orders — create a new order with automatic inventory check.
     *
     * <p>Request body (JSON):
     * <pre>
     * {
     *   "customerId": "customer-001",
     *   "items": [
     *     { "sku": "SKU-LAPTOP-001", "productName": "Laptop Pro", "quantity": 1, "unitPrice": 999.99 }
     *   ]
     * }
     * </pre>
     *
     * <p>This endpoint triggers a multi-service gRPC flow:
     * <ol>
     *   <li>Calls InventoryService.ReserveStock for each item (gRPC).</li>
     *   <li>If all reservations succeed, calls OrderService.CreateOrder (gRPC).</li>
     *   <li>If any reservation fails, releases all reservations (compensation).</li>
     * </ol>
     *
     * @param request the create order request DTO
     * @return 201 Created with the order, 422 Unprocessable if inventory insufficient,
     *         400 Bad Request for validation errors
     */
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        log.info("REST POST /api/orders for customerId='{}'", request.customerId());

        // Convert REST DTO items to protobuf OrderItem messages.
        List<OrderItem> protoItems = request.items().stream()
                .map(item -> OrderItem.newBuilder()
                        .setSku(item.sku())
                        .setProductName(item.productName() != null ? item.productName() : "")
                        .setQuantity(item.quantity())
                        .setUnitPrice(item.unitPrice())
                        .build())
                .toList();

        // Delegate to gateway service — triggers gRPC calls to both microservices.
        OrderGatewayService.OrderCreationResult result =
                gatewayService.createOrderWithInventoryCheck(request.customerId(), protoItems);

        if (result.isSuccess()) {
            // Order created — convert protobuf Order to a JSON-serializable response map.
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(orderToMap(result.getOrder()));
        } else {
            // Business failure (e.g., insufficient stock).
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", result.getErrorMessage()));
        }
    }

    /**
     * GET /api/orders/{orderId} — retrieve a single order by ID.
     *
     * <p>Calls OrderService.GetOrder via gRPC. Returns 404 if the order doesn't exist.
     *
     * @param orderId the UUID string order identifier
     * @return 200 OK with the order, 404 Not Found if not found
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        log.info("REST GET /api/orders/{}", orderId);

        try {
            Order order = gatewayService.getOrder(orderId);
            return ResponseEntity.ok(orderToMap(order));
        } catch (GrpcServiceException e) {
            // Check the underlying gRPC status to determine HTTP response code.
            return handleGrpcException(e);
        }
    }

    /**
     * GET /api/orders?customerId={customerId} — list all orders for a customer.
     *
     * <p>Calls OrderService.ListOrders via gRPC (server-streaming). The controller
     * waits for all streamed orders to be collected before responding.
     *
     * @param customerId the customer ID to list orders for
     * @return 200 OK with the list of orders
     */
    @GetMapping("/orders")
    public ResponseEntity<?> listOrders(@RequestParam String customerId) {
        log.info("REST GET /api/orders?customerId={}", customerId);

        try {
            List<Order> orders = gatewayService.listOrders(customerId);
            // Convert each protobuf Order to a JSON-serializable map.
            List<Map<String, Object>> response = orders.stream()
                    .map(this::orderToMap)
                    .toList();
            return ResponseEntity.ok(response);
        } catch (GrpcServiceException e) {
            return handleGrpcException(e);
        }
    }

    // =========================================================================
    // Inventory endpoints
    // =========================================================================

    /**
     * GET /api/inventory — list all inventory items.
     *
     * <p>Calls InventoryService.ListInventory via gRPC (server-streaming).
     *
     * @param onlyAvailable if "true", only return items with available stock > 0
     * @return 200 OK with the list of inventory items
     */
    @GetMapping("/inventory")
    public ResponseEntity<?> listInventory(
            @RequestParam(defaultValue = "false") boolean onlyAvailable) {
        log.info("REST GET /api/inventory?onlyAvailable={}", onlyAvailable);

        try {
            List<InventoryItem> items = gatewayService.listInventory(onlyAvailable);
            List<Map<String, Object>> response = items.stream()
                    .map(this::inventoryItemToMap)
                    .toList();
            return ResponseEntity.ok(response);
        } catch (GrpcServiceException e) {
            return handleGrpcException(e);
        }
    }

    /**
     * GET /api/inventory/{sku} — check available stock for a specific SKU.
     *
     * <p>Calls InventoryService.CheckStock via gRPC.
     *
     * @param sku the product SKU to check
     * @return 200 OK with stock info, 404 if SKU not found
     */
    @GetMapping("/inventory/{sku}")
    public ResponseEntity<?> checkStock(@PathVariable String sku) {
        log.info("REST GET /api/inventory/{}", sku);

        try {
            CheckStockResponse response = gatewayService.checkStock(sku);
            return ResponseEntity.ok(Map.of(
                    "sku", response.getSku(),
                    "availableQuantity", response.getAvailableQuantity(),
                    "isAvailable", response.getIsAvailable()
            ));
        } catch (GrpcServiceException e) {
            return handleGrpcException(e);
        }
    }

    /**
     * POST /api/inventory/{sku}/reserve — reserve stock units for an order.
     *
     * <p>Request body (JSON):
     * <pre>{ "quantity": 5, "orderId": "order-123" }</pre>
     *
     * @param sku     the product SKU to reserve
     * @param request the reservation request with quantity and order ID
     * @return 200 OK with reservation result
     */
    @PostMapping("/inventory/{sku}/reserve")
    public ResponseEntity<?> reserveStock(
            @PathVariable String sku,
            @RequestBody StockOperationRequest request) {
        log.info("REST POST /api/inventory/{}/reserve qty={}", sku, request.quantity());

        try {
            var response = gatewayService.reserveStock(sku, request.quantity(), request.orderId());
            return ResponseEntity.ok(Map.of(
                    "success", response.getSuccess(),
                    "message", response.getMessage()
            ));
        } catch (GrpcServiceException e) {
            return handleGrpcException(e);
        }
    }

    /**
     * POST /api/inventory/{sku}/release — release previously reserved stock units.
     *
     * <p>Request body (JSON):
     * <pre>{ "quantity": 5, "orderId": "order-123" }</pre>
     *
     * @param sku     the product SKU whose reservation to release
     * @param request the release request with quantity and order ID
     * @return 200 OK with release result
     */
    @PostMapping("/inventory/{sku}/release")
    public ResponseEntity<?> releaseStock(
            @PathVariable String sku,
            @RequestBody StockOperationRequest request) {
        log.info("REST POST /api/inventory/{}/release qty={}", sku, request.quantity());

        try {
            var response = gatewayService.releaseStock(sku, request.quantity(), request.orderId());
            return ResponseEntity.ok(Map.of(
                    "success", response.getSuccess(),
                    "message", response.getMessage()
            ));
        } catch (GrpcServiceException e) {
            return handleGrpcException(e);
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Convert a protobuf {@link Order} message to a plain Java {@link Map} for JSON serialization.
     *
     * <p>Spring MVC's Jackson serializer cannot directly serialize protobuf messages
     * (they are binary-encoded by default). We convert to a plain Map so Jackson
     * can serialize it as standard JSON.
     *
     * <p>Alternative approaches: use protobuf-java-util's {@code JsonFormat.printer()},
     * or register a custom Jackson module for protobuf. We use Map here for simplicity.
     *
     * @param order the protobuf Order message to convert
     * @return a plain Java Map suitable for JSON serialization by Jackson
     */
    private Map<String, Object> orderToMap(Order order) {
        // Convert each nested OrderItem to a map as well.
        List<Map<String, Object>> items = order.getItemsList().stream()
                .map(item -> Map.<String, Object>of(
                        "sku", item.getSku(),
                        "productName", item.getProductName(),
                        "quantity", item.getQuantity(),
                        "unitPrice", item.getUnitPrice()
                ))
                .toList();

        return Map.of(
                "orderId", order.getOrderId(),
                "customerId", order.getCustomerId(),
                "status", order.getStatus().name(),
                "totalAmount", order.getTotalAmount(),
                "createdAt", order.getCreatedAt(),
                "items", items
        );
    }

    /**
     * Convert a protobuf {@link InventoryItem} message to a plain Java Map.
     *
     * @param item the protobuf InventoryItem message to convert
     * @return a plain Java Map for JSON serialization
     */
    private Map<String, Object> inventoryItemToMap(InventoryItem item) {
        return Map.of(
                "sku", item.getSku(),
                "productName", item.getProductName(),
                "totalQuantity", item.getTotalQuantity(),
                "reservedQuantity", item.getReservedQuantity(),
                "availableQuantity", item.getAvailableQuantity()
        );
    }

    /**
     * Translate a {@link GrpcServiceException} into an appropriate HTTP response.
     *
     * <p>Inspects the underlying {@link StatusRuntimeException} cause to determine
     * the correct HTTP status code:
     * <ul>
     *   <li>{@code NOT_FOUND}        → 404 Not Found</li>
     *   <li>{@code INVALID_ARGUMENT} → 400 Bad Request</li>
     *   <li>All others               → 500 Internal Server Error</li>
     * </ul>
     *
     * @param e the gRPC service exception to translate
     * @return the appropriate HTTP error response
     */
    private ResponseEntity<Map<String, String>> handleGrpcException(GrpcServiceException e) {
        // Inspect the cause (StatusRuntimeException) for the gRPC status code.
        if (e.getCause() instanceof StatusRuntimeException srex) {
            Status.Code code = srex.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", e.getMessage()));
            }
            if (code == Status.Code.INVALID_ARGUMENT) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", e.getMessage()));
            }
        }
        // Default: 500 Internal Server Error for unexpected gRPC failures.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal error: " + e.getMessage()));
    }

    // =========================================================================
    // Request DTO records
    // =========================================================================

    /**
     * DTO for POST /api/orders.
     *
     * <p>Java records are immutable value objects — ideal for request DTOs since
     * they only carry data in and are never mutated.
     *
     * @param customerId the customer placing the order
     * @param items      the list of order items
     */
    public record CreateOrderRequest(String customerId, List<OrderItemDto> items) {}

    /**
     * DTO for a single order item within {@link CreateOrderRequest}.
     *
     * @param sku         the product SKU
     * @param productName the product name
     * @param quantity    the number of units to order
     * @param unitPrice   the unit price in USD
     */
    public record OrderItemDto(String sku, String productName, int quantity, double unitPrice) {}

    /**
     * DTO for POST /api/inventory/{sku}/reserve and /release endpoints.
     *
     * @param quantity the number of units to reserve or release
     * @param orderId  the order ID associated with the reservation
     */
    public record StockOperationRequest(int quantity, String orderId) {}
}
