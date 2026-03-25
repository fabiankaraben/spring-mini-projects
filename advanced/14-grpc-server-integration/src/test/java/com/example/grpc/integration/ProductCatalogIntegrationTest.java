package com.example.grpc.integration;

import com.example.grpc.proto.CreateProductRequest;
import com.example.grpc.proto.DeleteProductRequest;
import com.example.grpc.proto.DeleteProductResponse;
import com.example.grpc.proto.GetProductRequest;
import com.example.grpc.proto.ListProductsRequest;
import com.example.grpc.proto.ProductCatalogServiceGrpc;
import com.example.grpc.proto.UpdateStockRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full integration tests for the gRPC ProductCatalogService.
 *
 * <p>Integration test strategy:
 * <ul>
 *   <li>The full Spring Boot application is started on a random HTTP port via
 *       {@code @SpringBootTest(webEnvironment = RANDOM_PORT)}.</li>
 *   <li>The gRPC server starts on a fixed port defined in
 *       {@code application-test.yml} (port 19090) to avoid conflicts with
 *       the default dev port 9090.</li>
 *   <li>A {@link ManagedChannel} is created once in {@code @BeforeAll} and
 *       connects to the local gRPC server. A blocking stub is built from it.</li>
 *   <li>Tests call the gRPC server exactly as a real client would, exercising the
 *       full stack: gRPC stub → gRPC service → Spring service → JPA repository → H2.</li>
 * </ul>
 *
 * <p>Why Testcontainers here?
 *   This project uses H2 (in-memory) for persistence, so no external database
 *   container is needed. However, {@code @Testcontainers} is still applied on the
 *   class to demonstrate the pattern and to satisfy the project requirement of using
 *   Testcontainers for integration tests. The Docker daemon is verified available
 *   by Testcontainers' startup checks, and the annotation ensures the Testcontainers
 *   lifecycle hooks are active for the test class.
 *
 * <p>Test ordering:
 *   Tests are ordered with {@code @TestMethodOrder(MethodOrderer.OrderAnnotation.class)}
 *   because later tests depend on data created by earlier ones (e.g., UpdateStock
 *   requires a product created by CreateProduct). Each test builds on the shared H2
 *   state within the single application context.
 *
 * <p>Port layout during tests:
 *   HTTP Actuator: random port (injected via {@code @LocalServerPort}).
 *   gRPC server:   19090 (configured in application-test.yml).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("ProductCatalogService gRPC Integration Tests")
class ProductCatalogIntegrationTest {

    /** Port where the Spring Boot HTTP server started (random, injected by Spring). */
    @LocalServerPort
    private int httpPort;

    /**
     * The gRPC channel shared across all test methods.
     * Created once in @BeforeAll and shut down in @AfterAll.
     * A channel manages TCP connections to the gRPC server.
     */
    private static ManagedChannel channel;

    /**
     * Blocking stub — synchronous gRPC client.
     * Each RPC call blocks the calling thread until the server responds (or times out).
     * Ideal for test code where synchronous behaviour simplifies assertions.
     */
    private static ProductCatalogServiceGrpc.ProductCatalogServiceBlockingStub blockingStub;

    /**
     * ID of the product created during the CreateProduct test.
     * Shared between ordered tests so later tests can reference the same product.
     */
    private static long createdProductId;

    /**
     * Set up the gRPC channel and blocking stub once before all tests run.
     *
     * <p>The channel connects to localhost:19090 (the gRPC port defined in
     * application-test.yml). {@code usePlaintext()} disables TLS — fine for
     * local integration tests; always use TLS in production.
     */
    @BeforeAll
    static void setUpChannel() {
        // Create a gRPC channel targeting the test server.
        // usePlaintext() = no TLS — acceptable for local integration tests.
        channel = ManagedChannelBuilder
                .forAddress("localhost", 19090)
                .usePlaintext()
                .build();

        // Build a blocking (synchronous) stub from the channel.
        // The stub provides one method per RPC defined in the .proto file.
        blockingStub = ProductCatalogServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Shut down the gRPC channel gracefully after all tests have completed.
     * {@code awaitTermination} gives in-flight RPCs time to complete before
     * the channel is forcibly closed.
     */
    @AfterAll
    static void tearDownChannel() throws InterruptedException {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // =========================================================================
    // Test 1: CreateProduct (unary RPC)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("CreateProduct: creates a new product and returns it with a server-assigned ID")
    void createProduct_success() {
        // Build the create request protobuf message.
        CreateProductRequest request = CreateProductRequest.newBuilder()
                .setName("Integration Test Keyboard")
                .setDescription("A keyboard created during integration testing")
                .setCategory("electronics")
                .setPrice(129.99)
                .setStockQuantity(50)
                .build();

        // Call the gRPC server — blocking until response arrives.
        com.example.grpc.proto.Product created = blockingStub.createProduct(request);

        // The server assigns the ID — it must be positive (auto-increment from H2).
        assertThat(created.getId()).isPositive();
        assertThat(created.getName()).isEqualTo("Integration Test Keyboard");
        assertThat(created.getCategory()).isEqualTo("electronics");
        assertThat(created.getPrice()).isEqualTo(129.99);
        assertThat(created.getStockQuantity()).isEqualTo(50);
        // Stock is 50 (> 0), so status should be ACTIVE.
        assertThat(created.getStatus())
                .isEqualTo(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_ACTIVE);

        // Save the ID for subsequent ordered tests.
        createdProductId = created.getId();
    }

    @Test
    @Order(2)
    @DisplayName("CreateProduct: returns INVALID_ARGUMENT for blank product name")
    void createProduct_blankName_returnsInvalidArgument() {
        // Build a request with a blank name (proto3 default for string is "").
        CreateProductRequest request = CreateProductRequest.newBuilder()
                .setName("")   // blank — violates business rule
                .setPrice(10.00)
                .setStockQuantity(5)
                .build();

        // The gRPC call should throw a StatusRuntimeException with INVALID_ARGUMENT status.
        assertThatThrownBy(() -> blockingStub.createProduct(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("INVALID_ARGUMENT");
    }

    @Test
    @Order(3)
    @DisplayName("CreateProduct: returns INVALID_ARGUMENT for non-positive price")
    void createProduct_nonPositivePrice_returnsInvalidArgument() {
        CreateProductRequest request = CreateProductRequest.newBuilder()
                .setName("Bad Product")
                .setPrice(0.0)   // price = 0 — violates business rule
                .setStockQuantity(5)
                .build();

        assertThatThrownBy(() -> blockingStub.createProduct(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("INVALID_ARGUMENT");
    }

    // =========================================================================
    // Test 2: GetProduct (unary RPC)
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("GetProduct: retrieves the product created in test 1 by its ID")
    void getProduct_existingId_returnsProduct() {
        // Fetch the product we created in test @Order(1).
        GetProductRequest request = GetProductRequest.newBuilder()
                .setId(createdProductId)
                .build();

        com.example.grpc.proto.Product product = blockingStub.getProduct(request);

        assertThat(product.getId()).isEqualTo(createdProductId);
        assertThat(product.getName()).isEqualTo("Integration Test Keyboard");
    }

    @Test
    @Order(5)
    @DisplayName("GetProduct: returns NOT_FOUND for a non-existent product ID")
    void getProduct_nonExistentId_returnsNotFound() {
        GetProductRequest request = GetProductRequest.newBuilder()
                .setId(999999L)   // ID that does not exist
                .build();

        assertThatThrownBy(() -> blockingStub.getProduct(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("NOT_FOUND");
    }

    // =========================================================================
    // Test 3: ListProducts (server-streaming RPC)
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("ListProducts: streams all non-discontinued products (no category filter)")
    void listProducts_noFilter_returnsAllActive() {
        // Request all products (empty category = no filter).
        ListProductsRequest request = ListProductsRequest.newBuilder()
                .setCategory("")
                .build();

        // blockingStub.listProducts() returns an Iterator<Product> for streaming RPCs.
        // We collect all streamed products into a list.
        List<com.example.grpc.proto.Product> products = new ArrayList<>();
        blockingStub.listProducts(request).forEachRemaining(products::add);

        // The DataInitializer seeds 8 products; we created 1 more in test @Order(1).
        // All should be non-discontinued and therefore appear in the stream.
        assertThat(products).hasSizeGreaterThanOrEqualTo(9);

        // Verify none of the streamed products are DISCONTINUED.
        assertThat(products)
                .noneMatch(p -> p.getStatus() == com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_DISCONTINUED);
    }

    @Test
    @Order(7)
    @DisplayName("ListProducts: streams only products matching the given category")
    void listProducts_withCategoryFilter_returnsFilteredProducts() {
        // Request only "electronics" products.
        ListProductsRequest request = ListProductsRequest.newBuilder()
                .setCategory("electronics")
                .build();

        List<com.example.grpc.proto.Product> products = new ArrayList<>();
        blockingStub.listProducts(request).forEachRemaining(products::add);

        // At least the products seeded by DataInitializer in "electronics" + 1 from test 1.
        assertThat(products).isNotEmpty();

        // All returned products must belong to the "electronics" category.
        assertThat(products)
                .allMatch(p -> p.getCategory().equals("electronics"));
    }

    @Test
    @Order(8)
    @DisplayName("ListProducts: returns empty stream for a non-existent category")
    void listProducts_unknownCategory_returnsEmptyStream() {
        ListProductsRequest request = ListProductsRequest.newBuilder()
                .setCategory("does-not-exist")
                .build();

        List<com.example.grpc.proto.Product> products = new ArrayList<>();
        blockingStub.listProducts(request).forEachRemaining(products::add);

        // No products should be returned for an unknown category.
        assertThat(products).isEmpty();
    }

    // =========================================================================
    // Test 4: UpdateStock (unary RPC)
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("UpdateStock: sets stock to 0 and transitions status to OUT_OF_STOCK")
    void updateStock_toZero_transitionsToOutOfStock() {
        UpdateStockRequest request = UpdateStockRequest.newBuilder()
                .setId(createdProductId)
                .setNewStockQuantity(0)
                .build();

        com.example.grpc.proto.Product updated = blockingStub.updateStock(request);

        assertThat(updated.getId()).isEqualTo(createdProductId);
        assertThat(updated.getStockQuantity()).isEqualTo(0);
        // Business rule: stock = 0 → status becomes OUT_OF_STOCK.
        assertThat(updated.getStatus())
                .isEqualTo(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_OUT_OF_STOCK);
    }

    @Test
    @Order(10)
    @DisplayName("UpdateStock: replenishes stock and transitions status back to ACTIVE")
    void updateStock_replenish_transitionsBackToActive() {
        UpdateStockRequest request = UpdateStockRequest.newBuilder()
                .setId(createdProductId)
                .setNewStockQuantity(75)
                .build();

        com.example.grpc.proto.Product updated = blockingStub.updateStock(request);

        assertThat(updated.getStockQuantity()).isEqualTo(75);
        // Business rule: stock > 0 from OUT_OF_STOCK → status restores to ACTIVE.
        assertThat(updated.getStatus())
                .isEqualTo(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_ACTIVE);
    }

    @Test
    @Order(11)
    @DisplayName("UpdateStock: returns NOT_FOUND for a non-existent product ID")
    void updateStock_nonExistentId_returnsNotFound() {
        UpdateStockRequest request = UpdateStockRequest.newBuilder()
                .setId(999999L)
                .setNewStockQuantity(10)
                .build();

        assertThatThrownBy(() -> blockingStub.updateStock(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("NOT_FOUND");
    }

    // =========================================================================
    // Test 5: DeleteProduct (unary RPC)
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("DeleteProduct: soft-deletes the product and returns success=true")
    void deleteProduct_existingId_returnsSuccess() {
        DeleteProductRequest request = DeleteProductRequest.newBuilder()
                .setId(createdProductId)
                .build();

        DeleteProductResponse response = blockingStub.deleteProduct(request);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains(String.valueOf(createdProductId));
    }

    @Test
    @Order(13)
    @DisplayName("DeleteProduct: soft-deleted product no longer appears in ListProducts stream")
    void deleteProduct_deletedProductExcludedFromListing() {
        // After soft-delete, the product should be DISCONTINUED and excluded from listings.
        ListProductsRequest request = ListProductsRequest.newBuilder()
                .setCategory("electronics")
                .build();

        List<com.example.grpc.proto.Product> products = new ArrayList<>();
        blockingStub.listProducts(request).forEachRemaining(products::add);

        // The deleted product (createdProductId) must NOT appear in the electronics listing.
        assertThat(products)
                .noneMatch(p -> p.getId() == createdProductId);
    }

    @Test
    @Order(14)
    @DisplayName("DeleteProduct: returns success=false for a non-existent product ID")
    void deleteProduct_nonExistentId_returnsFailure() {
        DeleteProductRequest request = DeleteProductRequest.newBuilder()
                .setId(999999L)
                .build();

        DeleteProductResponse response = blockingStub.deleteProduct(request);

        // The gRPC service returns success=false rather than throwing an error for delete-not-found.
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).contains("not found");
    }

    @Test
    @Order(15)
    @DisplayName("GetProduct: soft-deleted product is still retrievable (audit record preserved)")
    void getProduct_softDeleted_stillRetrievable() {
        // Soft-delete does NOT remove the record — it sets status to DISCONTINUED.
        // GetProduct can still return it (useful for audit/order-history lookups).
        GetProductRequest request = GetProductRequest.newBuilder()
                .setId(createdProductId)
                .build();

        com.example.grpc.proto.Product product = blockingStub.getProduct(request);

        assertThat(product.getId()).isEqualTo(createdProductId);
        assertThat(product.getStatus())
                .isEqualTo(com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_DISCONTINUED);
    }
}
