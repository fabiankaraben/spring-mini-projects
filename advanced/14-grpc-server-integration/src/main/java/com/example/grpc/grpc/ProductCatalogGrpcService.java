package com.example.grpc.grpc;

import com.example.grpc.domain.Product;
import com.example.grpc.mapper.ProductMapper;
import com.example.grpc.proto.CreateProductRequest;
import com.example.grpc.proto.DeleteProductRequest;
import com.example.grpc.proto.DeleteProductResponse;
import com.example.grpc.proto.GetProductRequest;
import com.example.grpc.proto.ListProductsRequest;
import com.example.grpc.proto.ProductCatalogServiceGrpc;
import com.example.grpc.proto.UpdateStockRequest;
import com.example.grpc.service.ProductService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * gRPC service implementation for the ProductCatalogService.
 *
 * <p>This class bridges the gRPC transport layer and the Spring service layer.
 * It is responsible for:
 * <ol>
 *   <li>Receiving protobuf request messages from gRPC clients.</li>
 *   <li>Delegating business logic to {@link ProductService} (which operates on JPA entities).</li>
 *   <li>Converting JPA entity results back to protobuf response messages via {@link ProductMapper}.</li>
 *   <li>Sending responses (or errors) back to the client via {@link StreamObserver}.</li>
 * </ol>
 *
 * <p>The {@code @GrpcService} annotation:
 * <ul>
 *   <li>Marks this class as a gRPC service implementation.</li>
 *   <li>The {@code net.devh} starter scans for it and registers it with the Netty gRPC server.</li>
 *   <li>It extends the generated abstract base class
 *       {@link ProductCatalogServiceGrpc.ProductCatalogServiceImplBase} which provides
 *       default no-op implementations for all RPC methods — we override only the ones we need.</li>
 * </ul>
 *
 * <p>Error handling with gRPC {@link Status}:
 *   gRPC uses status codes (NOT HTTP status codes) to signal errors. Common codes used here:
 *   <ul>
 *     <li>{@code NOT_FOUND}       — the requested resource does not exist.</li>
 *     <li>{@code INVALID_ARGUMENT} — the request contains invalid input data.</li>
 *     <li>{@code INTERNAL}         — an unexpected server-side error occurred.</li>
 *   </ul>
 *   Errors are returned by calling {@code responseObserver.onError(StatusRuntimeException)}.
 *
 * <p>StreamObserver contract:
 *   For each RPC call, exactly one of these sequences must be followed:
 *   <ul>
 *     <li>Success: {@code onNext(response)} then {@code onCompleted()}.</li>
 *     <li>Streaming success: multiple {@code onNext()} calls then {@code onCompleted()}.</li>
 *     <li>Error: {@code onError(exception)} — do NOT call onNext or onCompleted after this.</li>
 *   </ul>
 */
@GrpcService
public class ProductCatalogGrpcService
        extends ProductCatalogServiceGrpc.ProductCatalogServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogGrpcService.class);

    /** Business logic layer — operates on JPA entities, unaware of gRPC. */
    private final ProductService productService;

    /** Maps between JPA domain objects and protobuf messages. */
    private final ProductMapper productMapper;

    /**
     * Constructor injection — both dependencies are required.
     *
     * @param productService the domain service for product management
     * @param productMapper  the mapper for JPA ↔ protobuf conversion
     */
    public ProductCatalogGrpcService(ProductService productService, ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
    }

    // =========================================================================
    // Unary RPC: GetProduct
    // =========================================================================

    /**
     * GetProduct — retrieve a single product by its numeric ID.
     *
     * <p>This is a unary RPC:
     * <ul>
     *   <li>Client sends one {@link GetProductRequest}.</li>
     *   <li>Server responds with one {@link com.example.grpc.proto.Product} or an error.</li>
     * </ul>
     *
     * <p>gRPC status codes used:
     * <ul>
     *   <li>{@code NOT_FOUND} — if no product with the given ID exists in the database.</li>
     * </ul>
     *
     * @param request          the request containing the product ID to look up
     * @param responseObserver the observer used to send the response back to the client
     */
    @Override
    public void getProduct(GetProductRequest request,
                           StreamObserver<com.example.grpc.proto.Product> responseObserver) {
        log.info("gRPC getProduct called for id={}", request.getId());

        productService.findById(request.getId()).ifPresentOrElse(
                product -> {
                    // Product found — convert to protobuf and send as the single response.
                    responseObserver.onNext(productMapper.toProto(product));
                    responseObserver.onCompleted();
                },
                () -> {
                    // Product not found — signal NOT_FOUND to the client.
                    responseObserver.onError(
                            Status.NOT_FOUND
                                    .withDescription("Product not found with id: " + request.getId())
                                    .asRuntimeException()
                    );
                }
        );
    }

    // =========================================================================
    // Server-Streaming RPC: ListProducts
    // =========================================================================

    /**
     * ListProducts — stream all products to the client, optionally filtered by category.
     *
     * <p>This is a server-streaming RPC:
     * <ul>
     *   <li>Client sends one {@link ListProductsRequest}.</li>
     *   <li>Server writes each {@link com.example.grpc.proto.Product} into the stream
     *       as it processes the result set, then closes the stream.</li>
     * </ul>
     *
     * <p>Why server-streaming?
     *   With a large product catalog, returning all products in a single response
     *   requires buffering everything in memory before sending. Streaming allows the
     *   client to start processing results immediately as they arrive, with lower
     *   peak memory usage on both sides.
     *
     * <p>DISCONTINUED products are excluded from the stream — they are soft-deleted.
     *
     * @param request          the request, optionally containing a category filter
     * @param responseObserver the observer used to stream Product messages to the client
     */
    @Override
    public void listProducts(ListProductsRequest request,
                             StreamObserver<com.example.grpc.proto.Product> responseObserver) {
        log.info("gRPC listProducts called with category='{}'", request.getCategory());

        // Fetch all matching products from the service layer.
        List<Product> products = productService.findAll(request.getCategory());

        // Write each product into the stream individually.
        // The client receives them one-by-one as they are sent.
        for (Product product : products) {
            responseObserver.onNext(productMapper.toProto(product));
        }

        // Signal that no more messages will be sent. The client stream is now closed.
        responseObserver.onCompleted();
    }

    // =========================================================================
    // Unary RPC: CreateProduct
    // =========================================================================

    /**
     * CreateProduct — create a new product and return the persisted entity.
     *
     * <p>This is a unary RPC:
     * <ul>
     *   <li>Client sends one {@link CreateProductRequest}.</li>
     *   <li>Server responds with the created {@link com.example.grpc.proto.Product}
     *       (including the server-assigned ID).</li>
     * </ul>
     *
     * <p>gRPC status codes used:
     * <ul>
     *   <li>{@code INVALID_ARGUMENT} — if the request has validation issues
     *       (e.g., blank name or non-positive price).</li>
     * </ul>
     *
     * @param request          the creation request with product attributes
     * @param responseObserver the observer used to send the created product back to the client
     */
    @Override
    public void createProduct(CreateProductRequest request,
                              StreamObserver<com.example.grpc.proto.Product> responseObserver) {
        log.info("gRPC createProduct called for name='{}'", request.getName());

        // Basic validation — protobuf proto3 string fields default to "" if not set.
        if (request.getName().isBlank()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Product name must not be blank")
                            .asRuntimeException()
            );
            return;
        }
        if (request.getPrice() <= 0) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Price must be greater than 0")
                            .asRuntimeException()
            );
            return;
        }
        if (request.getStockQuantity() < 0) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Stock quantity cannot be negative")
                            .asRuntimeException()
            );
            return;
        }

        // Delegate to the service layer which handles business logic and persistence.
        Product created = productService.createProduct(
                request.getName(),
                request.getDescription(),
                request.getCategory(),
                request.getPrice(),
                request.getStockQuantity()
        );

        // Return the persisted entity (with its generated ID) as the response.
        responseObserver.onNext(productMapper.toProto(created));
        responseObserver.onCompleted();
    }

    // =========================================================================
    // Unary RPC: UpdateStock
    // =========================================================================

    /**
     * UpdateStock — update the stock quantity of an existing product.
     *
     * <p>This is a unary RPC:
     * <ul>
     *   <li>Client sends one {@link UpdateStockRequest}.</li>
     *   <li>Server responds with the updated {@link com.example.grpc.proto.Product}.</li>
     * </ul>
     *
     * <p>The service layer automatically transitions the product's status:
     * <ul>
     *   <li>{@code ACTIVE} → {@code OUT_OF_STOCK} when the new quantity is 0.</li>
     *   <li>{@code OUT_OF_STOCK} → {@code ACTIVE} when the new quantity is > 0.</li>
     * </ul>
     *
     * <p>gRPC status codes used:
     * <ul>
     *   <li>{@code NOT_FOUND}        — product with the given ID does not exist.</li>
     *   <li>{@code INVALID_ARGUMENT} — new stock quantity is negative.</li>
     * </ul>
     *
     * @param request          the request containing the product ID and new stock quantity
     * @param responseObserver the observer used to send the updated product back to the client
     */
    @Override
    public void updateStock(UpdateStockRequest request,
                            StreamObserver<com.example.grpc.proto.Product> responseObserver) {
        log.info("gRPC updateStock called for id={}, newQty={}", request.getId(), request.getNewStockQuantity());

        if (request.getNewStockQuantity() < 0) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Stock quantity cannot be negative")
                            .asRuntimeException()
            );
            return;
        }

        try {
            productService.updateStock(request.getId(), request.getNewStockQuantity())
                    .ifPresentOrElse(
                            updated -> {
                                responseObserver.onNext(productMapper.toProto(updated));
                                responseObserver.onCompleted();
                            },
                            () -> responseObserver.onError(
                                    Status.NOT_FOUND
                                            .withDescription("Product not found with id: " + request.getId())
                                            .asRuntimeException()
                            )
                    );
        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        }
    }

    // =========================================================================
    // Unary RPC: DeleteProduct
    // =========================================================================

    /**
     * DeleteProduct — soft-delete a product by marking it as DISCONTINUED.
     *
     * <p>This is a unary RPC:
     * <ul>
     *   <li>Client sends one {@link DeleteProductRequest}.</li>
     *   <li>Server responds with a {@link DeleteProductResponse} indicating success or failure.</li>
     * </ul>
     *
     * <p>Soft-delete semantics:
     *   The product record is NOT removed from the database. Its status is set to
     *   {@code DISCONTINUED}. This preserves referential integrity for order history
     *   and other systems that reference the product by ID.
     *
     * @param request          the request containing the product ID to delete
     * @param responseObserver the observer used to send the result back to the client
     */
    @Override
    public void deleteProduct(DeleteProductRequest request,
                              StreamObserver<DeleteProductResponse> responseObserver) {
        log.info("gRPC deleteProduct called for id={}", request.getId());

        boolean deleted = productService.deleteProduct(request.getId());

        // Build the response — success flag and human-readable message.
        DeleteProductResponse response = DeleteProductResponse.newBuilder()
                .setSuccess(deleted)
                .setMessage(deleted
                        ? "Product with id " + request.getId() + " has been discontinued."
                        : "Product with id " + request.getId() + " was not found.")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
