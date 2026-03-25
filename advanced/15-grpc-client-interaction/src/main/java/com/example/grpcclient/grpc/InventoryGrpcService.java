package com.example.grpcclient.grpc;

import com.example.grpcclient.mapper.InventoryMapper;
import com.example.grpcclient.proto.CheckStockRequest;
import com.example.grpcclient.proto.CheckStockResponse;
import com.example.grpcclient.proto.InventoryServiceGrpc;
import com.example.grpcclient.proto.ListInventoryRequest;
import com.example.grpcclient.proto.ReleaseStockRequest;
import com.example.grpcclient.proto.ReleaseStockResponse;
import com.example.grpcclient.proto.ReserveStockRequest;
import com.example.grpcclient.proto.ReserveStockResponse;
import com.example.grpcclient.service.InventoryService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * gRPC server implementation for the InventoryService.
 *
 * <p>This class is the gRPC SERVER side for inventory — it receives protobuf requests
 * from gRPC clients and delegates to the Spring {@link InventoryService} for
 * business logic.
 *
 * <p>Architectural role:
 * <pre>
 *   gRPC Client (OrderGatewayService) ──gRPC──► InventoryGrpcService (this class)
 *                                                       │
 *                                               InventoryService (domain logic)
 *                                                       │
 *                                               InventoryItemRepository (JPA / H2)
 * </pre>
 *
 * <p>Key concept — how this service is configured on its own port:
 *   The {@code net.devh} starter supports multiple gRPC server instances via
 *   named server configurations in {@code application.yml}:
 *   <pre>
 *     grpc.server.port = 9091  (OrderService)
 *   </pre>
 *   Both {@link OrderGrpcService} and this class run in-process, sharing the same
 *   Netty gRPC server instance (a single server can host multiple services).
 *   The InventoryService is registered separately via an additional configured port (9092).
 *
 * <p>RPC methods implemented:
 * <ul>
 *   <li>{@link #checkStock}    — unary: check available stock for a SKU.</li>
 *   <li>{@link #reserveStock}  — unary: reserve units for an order.</li>
 *   <li>{@link #releaseStock}  — unary: release reserved units.</li>
 *   <li>{@link #listInventory} — server-streaming: stream all inventory items.</li>
 * </ul>
 */
@GrpcService
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(InventoryGrpcService.class);

    /** Domain service layer — handles business logic and JPA persistence. */
    private final InventoryService inventoryService;

    /** Maps between JPA domain objects and protobuf messages. */
    private final InventoryMapper inventoryMapper;

    /**
     * Constructor injection — both dependencies are required.
     *
     * @param inventoryService the domain service for inventory management
     * @param inventoryMapper  the mapper for JPA ↔ protobuf conversion
     */
    public InventoryGrpcService(InventoryService inventoryService, InventoryMapper inventoryMapper) {
        this.inventoryService = inventoryService;
        this.inventoryMapper = inventoryMapper;
    }

    // =========================================================================
    // Unary RPC: CheckStock
    // =========================================================================

    /**
     * CheckStock — query available stock for a SKU (unary RPC).
     *
     * <p>Returns:
     * <ul>
     *   <li>{@link CheckStockResponse} with {@code is_available = true} if
     *       {@code available_quantity >= requested_quantity} (we pass 1 as minimum).</li>
     *   <li>{@code NOT_FOUND} error if the SKU does not exist.</li>
     * </ul>
     *
     * @param request          the request containing the SKU to check
     * @param responseObserver the observer used to send the response back to the client
     */
    @Override
    public void checkStock(CheckStockRequest request,
                           StreamObserver<CheckStockResponse> responseObserver) {
        log.info("gRPC checkStock called for sku='{}'", request.getSku());

        if (request.getSku().isBlank()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("SKU must not be blank")
                            .asRuntimeException()
            );
            return;
        }

        inventoryService.checkStock(request.getSku(), 1).ifPresentOrElse(
                item -> {
                    // Build the check response with computed available quantity.
                    CheckStockResponse response = CheckStockResponse.newBuilder()
                            .setSku(item.getSku())
                            .setAvailableQuantity(item.getAvailableQuantity())
                            // is_available = true if at least 1 unit is available.
                            .setIsAvailable(item.getAvailableQuantity() > 0)
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(
                        Status.NOT_FOUND
                                .withDescription("SKU not found: " + request.getSku())
                                .asRuntimeException()
                )
        );
    }

    // =========================================================================
    // Unary RPC: ReserveStock
    // =========================================================================

    /**
     * ReserveStock — reserve units of a SKU for an order (unary RPC).
     *
     * <p>Returns a {@link ReserveStockResponse} with:
     * <ul>
     *   <li>{@code success = true} if the reservation succeeded.</li>
     *   <li>{@code success = false} if insufficient stock was available.</li>
     *   <li>{@code NOT_FOUND} if the SKU does not exist.</li>
     *   <li>{@code INVALID_ARGUMENT} if quantity <= 0.</li>
     * </ul>
     *
     * <p>Note: insufficient stock is NOT a gRPC error — it returns {@code success=false}
     * in the response. This is intentional: the caller (e.g., an order creation flow)
     * should handle this as a business failure, not an infrastructure error.
     *
     * @param request          the reservation request
     * @param responseObserver the observer used to send the response back to the client
     */
    @Override
    public void reserveStock(ReserveStockRequest request,
                             StreamObserver<ReserveStockResponse> responseObserver) {
        log.info("gRPC reserveStock called for sku='{}', qty={}, orderId='{}'",
                request.getSku(), request.getQuantity(), request.getOrderId());

        if (request.getSku().isBlank()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("SKU must not be blank")
                            .asRuntimeException()
            );
            return;
        }

        if (request.getQuantity() <= 0) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Reservation quantity must be positive")
                            .asRuntimeException()
            );
            return;
        }

        // Attempt reservation in the service layer.
        // reserveStock() now returns Optional<StockReservationResult> which explicitly
        // signals success or failure — no ambiguous state inference needed.
        var resultOpt = inventoryService.reserveStock(request.getSku(), request.getQuantity());

        if (resultOpt.isEmpty()) {
            // SKU not found in the inventory.
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("SKU not found: " + request.getSku())
                            .asRuntimeException()
            );
            return;
        }

        var result = resultOpt.get();
        var item = result.item();

        if (result.success()) {
            // Reservation succeeded — reserved quantity was incremented.
            ReserveStockResponse response = ReserveStockResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Reserved " + request.getQuantity() + " units of "
                            + request.getSku() + " for order " + request.getOrderId())
                    .setItem(inventoryMapper.toProto(item))
                    .build();
            responseObserver.onNext(response);
        } else {
            // Insufficient stock — business failure, not a gRPC error.
            ReserveStockResponse response = ReserveStockResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Insufficient stock for SKU " + request.getSku()
                            + ": requested " + request.getQuantity()
                            + ", available " + item.getAvailableQuantity())
                    .setItem(inventoryMapper.toProto(item))
                    .build();
            responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }

    // =========================================================================
    // Unary RPC: ReleaseStock
    // =========================================================================

    /**
     * ReleaseStock — release previously reserved units back to available (unary RPC).
     *
     * <p>Used when an order is cancelled or payment fails. The released units
     * are subtracted from the reserved counter, making them available again.
     *
     * @param request          the release request with SKU, quantity, and order ID
     * @param responseObserver the observer used to send the result back to the client
     */
    @Override
    public void releaseStock(ReleaseStockRequest request,
                             StreamObserver<ReleaseStockResponse> responseObserver) {
        log.info("gRPC releaseStock called for sku='{}', qty={}, orderId='{}'",
                request.getSku(), request.getQuantity(), request.getOrderId());

        if (request.getSku().isBlank()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("SKU must not be blank")
                            .asRuntimeException()
            );
            return;
        }

        if (request.getQuantity() <= 0) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Release quantity must be positive")
                            .asRuntimeException()
            );
            return;
        }

        var itemOpt = inventoryService.releaseStock(request.getSku(), request.getQuantity());

        if (itemOpt.isEmpty()) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("SKU not found: " + request.getSku())
                            .asRuntimeException()
            );
            return;
        }

        ReleaseStockResponse response = ReleaseStockResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Released " + request.getQuantity() + " units of "
                        + request.getSku() + " from order " + request.getOrderId())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // =========================================================================
    // Server-Streaming RPC: ListInventory
    // =========================================================================

    /**
     * ListInventory — stream all inventory items (server-streaming RPC).
     *
     * <p>The {@code onlyAvailable} flag in the request filters the result:
     * <ul>
     *   <li>{@code true}  — stream only items with available quantity > 0.</li>
     *   <li>{@code false} — stream all items (including fully reserved ones).</li>
     * </ul>
     *
     * <p>Why streaming for inventory listing?
     *   An inventory system could have thousands of SKUs. Streaming allows the client
     *   to start consuming items immediately without waiting for all to be serialized.
     *
     * @param request          the request with the optional availability filter
     * @param responseObserver the observer used to stream InventoryItem messages to the client
     */
    @Override
    public void listInventory(ListInventoryRequest request,
                              StreamObserver<com.example.grpcclient.proto.InventoryItem> responseObserver) {
        log.info("gRPC listInventory called with onlyAvailable={}", request.getOnlyAvailable());

        // Fetch all matching inventory items from the service layer.
        List<com.example.grpcclient.domain.InventoryItem> items =
                inventoryService.findAll(request.getOnlyAvailable());

        // Stream each item to the client one at a time.
        for (com.example.grpcclient.domain.InventoryItem item : items) {
            responseObserver.onNext(inventoryMapper.toProto(item));
        }

        // Close the stream — all items have been sent.
        responseObserver.onCompleted();
    }
}
