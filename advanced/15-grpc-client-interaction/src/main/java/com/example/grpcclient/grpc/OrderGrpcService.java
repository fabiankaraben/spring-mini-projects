package com.example.grpcclient.grpc;

import com.example.grpcclient.domain.OrderLineItem;
import com.example.grpcclient.domain.OrderStatus;
import com.example.grpcclient.mapper.OrderMapper;
import com.example.grpcclient.proto.CreateOrderRequest;
import com.example.grpcclient.proto.GetOrderRequest;
import com.example.grpcclient.proto.ListOrdersRequest;
import com.example.grpcclient.proto.OrderServiceGrpc;
import com.example.grpcclient.service.OrderService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * gRPC server implementation for the OrderService.
 *
 * <p>This class is the gRPC SERVER side — it receives protobuf requests from
 * gRPC clients (including the REST gateway within the same application) and
 * delegates to the Spring {@link OrderService} for business logic.
 *
 * <p>Architectural role:
 * <pre>
 *   gRPC Client (OrderGatewayService) ──gRPC──► OrderGrpcService (this class)
 *                                                     │
 *                                               OrderService (domain logic)
 *                                                     │
 *                                               OrderRepository (JPA / H2)
 * </pre>
 *
 * <p>{@code @GrpcService} annotation:
 * <ul>
 *   <li>Marks this class as a gRPC service implementation.</li>
 *   <li>The {@code net.devh} starter scans for it and registers it with the
 *       Netty gRPC server on the configured port (9091).</li>
 *   <li>It extends the generated abstract base class
 *       {@link OrderServiceGrpc.OrderServiceImplBase}.</li>
 * </ul>
 *
 * <p>RPC methods implemented:
 * <ul>
 *   <li>{@link #createOrder}    — unary RPC: create a new order.</li>
 *   <li>{@link #getOrder}       — unary RPC: retrieve an order by ID.</li>
 *   <li>{@link #listOrders}     — server-streaming RPC: stream orders for a customer.</li>
 * </ul>
 *
 * <p>Error handling uses gRPC {@link io.grpc.Status} codes:
 * <ul>
 *   <li>{@code NOT_FOUND}        — the requested order does not exist.</li>
 *   <li>{@code INVALID_ARGUMENT} — the request has missing/invalid data.</li>
 *   <li>{@code INTERNAL}         — an unexpected server-side error.</li>
 * </ul>
 */
@GrpcService
public class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(OrderGrpcService.class);

    /** Domain service layer — handles business logic and JPA persistence. */
    private final OrderService orderService;

    /** Maps between JPA domain objects and protobuf messages. */
    private final OrderMapper orderMapper;

    /**
     * Constructor injection — both dependencies are required.
     *
     * @param orderService the domain service for order management
     * @param orderMapper  the mapper for JPA ↔ protobuf conversion
     */
    public OrderGrpcService(OrderService orderService, OrderMapper orderMapper) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
    }

    // =========================================================================
    // Unary RPC: CreateOrder
    // =========================================================================

    /**
     * CreateOrder — place a new order and return the created entity.
     *
     * <p>This is a unary RPC:
     * <ul>
     *   <li>Client sends one {@link CreateOrderRequest}.</li>
     *   <li>Server responds with the created {@link com.example.grpcclient.proto.Order}
     *       (including the server-assigned UUID).</li>
     * </ul>
     *
     * <p>Validation:
     * <ul>
     *   <li>Customer ID must not be blank → {@code INVALID_ARGUMENT}.</li>
     *   <li>At least one order item must be present → {@code INVALID_ARGUMENT}.</li>
     * </ul>
     *
     * @param request          the create order request containing customer ID and items
     * @param responseObserver the observer used to send the response back to the client
     */
    @Override
    public void createOrder(CreateOrderRequest request,
                            StreamObserver<com.example.grpcclient.proto.Order> responseObserver) {
        log.info("gRPC createOrder called for customerId='{}'", request.getCustomerId());

        // Validate customer ID — proto3 string defaults to "" if not set.
        if (request.getCustomerId().isBlank()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Customer ID must not be blank")
                            .asRuntimeException()
            );
            return;
        }

        // Validate that at least one item is present.
        if (request.getItemsCount() == 0) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Order must contain at least one item")
                            .asRuntimeException()
            );
            return;
        }

        // Convert protobuf OrderItem messages to JPA OrderLineItem entities.
        // The OrderService is unaware of protobuf types — the conversion happens here.
        List<OrderLineItem> lineItems = request.getItemsList().stream()
                .map(protoItem -> new OrderLineItem(
                        protoItem.getSku(),
                        protoItem.getProductName(),
                        protoItem.getQuantity(),
                        protoItem.getUnitPrice()
                ))
                .toList();

        // Delegate to the domain service — handles UUID generation, total, status, persistence.
        com.example.grpcclient.domain.Order created =
                orderService.createOrder(request.getCustomerId(), lineItems);

        // Convert the JPA entity back to a protobuf message and send as the response.
        responseObserver.onNext(orderMapper.toProto(created));
        responseObserver.onCompleted();
    }

    // =========================================================================
    // Unary RPC: GetOrder
    // =========================================================================

    /**
     * GetOrder — retrieve a single order by its unique order ID.
     *
     * <p>This is a unary RPC:
     * <ul>
     *   <li>Client sends one {@link GetOrderRequest}.</li>
     *   <li>Server responds with the {@link com.example.grpcclient.proto.Order} or
     *       a {@code NOT_FOUND} error.</li>
     * </ul>
     *
     * @param request          the request containing the order ID to look up
     * @param responseObserver the observer used to send the response back to the client
     */
    @Override
    public void getOrder(GetOrderRequest request,
                         StreamObserver<com.example.grpcclient.proto.Order> responseObserver) {
        log.info("gRPC getOrder called for orderId='{}'", request.getOrderId());

        if (request.getOrderId().isBlank()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Order ID must not be blank")
                            .asRuntimeException()
            );
            return;
        }

        orderService.findById(request.getOrderId()).ifPresentOrElse(
                order -> {
                    // Order found — convert to protobuf and send as response.
                    responseObserver.onNext(orderMapper.toProto(order));
                    responseObserver.onCompleted();
                },
                () -> {
                    // Order not found — signal NOT_FOUND to the client.
                    responseObserver.onError(
                            Status.NOT_FOUND
                                    .withDescription("Order not found with ID: " + request.getOrderId())
                                    .asRuntimeException()
                    );
                }
        );
    }

    // =========================================================================
    // Server-Streaming RPC: ListOrders
    // =========================================================================

    /**
     * ListOrders — stream all orders for a customer.
     *
     * <p>This is a server-streaming RPC:
     * <ul>
     *   <li>Client sends one {@link ListOrdersRequest} containing a customer ID.</li>
     *   <li>Server writes each {@link com.example.grpcclient.proto.Order} into the stream
     *       one at a time, then closes the stream with {@code onCompleted()}.</li>
     * </ul>
     *
     * <p>Why server-streaming for listing?
     *   A customer could have many orders. Streaming allows the client to start
     *   processing orders immediately as they are serialized, without buffering
     *   the entire list in memory on either side.
     *
     * <p>The orders are returned most-recent-first (ordered by createdAt DESC in the repository).
     *
     * @param request          the request containing the customer ID to filter by
     * @param responseObserver the observer used to stream Order messages to the client
     */
    @Override
    public void listOrders(ListOrdersRequest request,
                           StreamObserver<com.example.grpcclient.proto.Order> responseObserver) {
        log.info("gRPC listOrders called for customerId='{}'", request.getCustomerId());

        if (request.getCustomerId().isBlank()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Customer ID must not be blank")
                            .asRuntimeException()
            );
            return;
        }

        // Fetch all orders for this customer from the service layer.
        java.util.List<com.example.grpcclient.domain.Order> orders =
                orderService.findByCustomerId(request.getCustomerId());

        // Stream each order to the client individually.
        // The client receives them one-by-one as they are converted and written.
        for (com.example.grpcclient.domain.Order order : orders) {
            responseObserver.onNext(orderMapper.toProto(order));
        }

        // Close the stream — no more orders will be sent.
        responseObserver.onCompleted();
    }
}
