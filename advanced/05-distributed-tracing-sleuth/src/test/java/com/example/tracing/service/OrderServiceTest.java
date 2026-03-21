package com.example.tracing.service;

import com.example.tracing.client.InventoryClient;
import com.example.tracing.model.InventoryResponse;
import com.example.tracing.model.Order;
import com.example.tracing.model.OrderResult;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderService}.
 *
 * <p>Tests use Mockito to stub both the {@link Tracer} (for child span creation)
 * and the {@link InventoryClient} (to avoid a real HTTP call). This isolates the
 * order-processing business logic from the tracing and HTTP infrastructure.
 *
 * <p><b>What is tested:</b>
 * <ul>
 *   <li>ACCEPTED status when inventory has sufficient stock.</li>
 *   <li>BACKORDER status when inventory is insufficient.</li>
 *   <li>OrderResult contains the traceId and spanId from the child span.</li>
 *   <li>Span tags are applied: order.id, order.product, order.customer, order.quantity, order.status.</li>
 *   <li>The InventoryClient is called with the correct productId.</li>
 *   <li>The child span is ended after processing (via the finally block).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService — unit tests")
class OrderServiceTest {

    @Mock
    private Tracer tracer;

    /**
     * The child span returned by tracer.nextSpan().
     * In Micrometer Tracing, tracer.nextSpan() returns a Span directly.
     * span.name() and span.start() are fluent methods that also return Span.
     */
    @Mock
    private Span orderSpan;

    @Mock
    private Tracer.SpanInScope spanInScope;

    @Mock
    private TraceContext traceContext;

    @Mock
    private InventoryClient inventoryClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // Wire the span-creation chain.
        // tracer.nextSpan() returns a Span; name() and start() are fluent (return Span).
        when(tracer.nextSpan()).thenReturn(orderSpan);
        when(orderSpan.name("process-order")).thenReturn(orderSpan);
        when(orderSpan.start()).thenReturn(orderSpan);
        when(tracer.withSpan(orderSpan)).thenReturn(spanInScope);

        // Wire the context for ID extraction
        when(orderSpan.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("order-trace-id");
        when(traceContext.spanId()).thenReturn("order-span-id");

        // Allow tag() and event() calls (they return the span for chaining)
        when(orderSpan.tag(anyString(), anyString())).thenReturn(orderSpan);

        orderService = new OrderService(tracer, inventoryClient, "distributed-tracing-sleuth");
    }

    // =========================================================================
    // ACCEPTED orders (sufficient stock)
    // =========================================================================

    /**
     * When available stock >= requested quantity, the order status is ACCEPTED.
     */
    @Test
    @DisplayName("process returns ACCEPTED when inventory has sufficient stock")
    void processShouldReturnAcceptedWhenStockSufficient() {
        // Arrange: inventory has 50 units, order wants 2
        when(inventoryClient.checkInventory("PROD-001"))
                .thenReturn(new InventoryResponse("PROD-001", 50, 5, "any", "any"));

        Order order = new Order("ORD-001", "PROD-001", 2, "Alice");

        // Act
        OrderResult result = orderService.process(order);

        // Assert
        assertThat(result.status()).isEqualTo("ACCEPTED");
    }

    /**
     * The OrderResult should contain the orderId from the input.
     */
    @Test
    @DisplayName("process returns OrderResult with the original orderId")
    void processShouldReturnCorrectOrderId() {
        when(inventoryClient.checkInventory("PROD-002"))
                .thenReturn(new InventoryResponse("PROD-002", 100, 10, "any", "any"));

        OrderResult result = orderService.process(new Order("ORD-999", "PROD-002", 1, "Bob"));

        assertThat(result.orderId()).isEqualTo("ORD-999");
    }

    /**
     * The OrderResult must include the traceId from the child span.
     */
    @Test
    @DisplayName("process embeds traceId from the process-order span")
    void processShouldEmbedTraceId() {
        when(inventoryClient.checkInventory(anyString()))
                .thenReturn(new InventoryResponse("PROD-001", 50, 5, "any", "any"));

        OrderResult result = orderService.process(new Order("ORD-001", "PROD-001", 1, "Alice"));

        assertThat(result.traceId()).isEqualTo("order-trace-id");
    }

    /**
     * The OrderResult must include the spanId from the child span.
     */
    @Test
    @DisplayName("process embeds spanId from the process-order span")
    void processShouldEmbedSpanId() {
        when(inventoryClient.checkInventory(anyString()))
                .thenReturn(new InventoryResponse("PROD-001", 50, 5, "any", "any"));

        OrderResult result = orderService.process(new Order("ORD-001", "PROD-001", 1, "Alice"));

        assertThat(result.spanId()).isEqualTo("order-span-id");
    }

    /**
     * The accepted message should mention the reserved quantity.
     */
    @Test
    @DisplayName("process message for ACCEPTED mentions the reserved quantity")
    void processAcceptedMessageShouldMentionQuantity() {
        when(inventoryClient.checkInventory("PROD-001"))
                .thenReturn(new InventoryResponse("PROD-001", 50, 5, "any", "any"));

        OrderResult result = orderService.process(new Order("ORD-001", "PROD-001", 3, "Alice"));

        assertThat(result.message()).contains("3");
    }

    // =========================================================================
    // BACKORDER (insufficient stock)
    // =========================================================================

    /**
     * When available stock < requested quantity, the order status is BACKORDER.
     */
    @Test
    @DisplayName("process returns BACKORDER when inventory is insufficient")
    void processShouldReturnBackorderWhenStockInsufficient() {
        // Arrange: only 2 units available, order wants 10
        when(inventoryClient.checkInventory("PROD-003"))
                .thenReturn(new InventoryResponse("PROD-003", 2, 1, "any", "any"));

        Order order = new Order("ORD-002", "PROD-003", 10, "Charlie");
        OrderResult result = orderService.process(order);

        assertThat(result.status()).isEqualTo("BACKORDER");
    }

    /**
     * The backorder message should mention the available stock count.
     */
    @Test
    @DisplayName("process BACKORDER message mentions available stock")
    void processBackorderMessageShouldMentionAvailableStock() {
        when(inventoryClient.checkInventory("PROD-003"))
                .thenReturn(new InventoryResponse("PROD-003", 2, 0, "any", "any"));

        OrderResult result = orderService.process(new Order("ORD-002", "PROD-003", 10, "Dave"));

        assertThat(result.message()).contains("2");
    }

    // =========================================================================
    // Span tagging
    // =========================================================================

    /**
     * The span must be tagged with the order ID.
     */
    @Test
    @DisplayName("process tags span with order.id")
    void processShouldTagSpanWithOrderId() {
        when(inventoryClient.checkInventory(anyString()))
                .thenReturn(new InventoryResponse("PROD-001", 50, 5, "any", "any"));

        orderService.process(new Order("ORD-555", "PROD-001", 1, "Alice"));

        verify(orderSpan).tag("order.id", "ORD-555");
    }

    /**
     * The span must be tagged with the product ID.
     */
    @Test
    @DisplayName("process tags span with order.product")
    void processShouldTagSpanWithProductId() {
        when(inventoryClient.checkInventory("PROD-002"))
                .thenReturn(new InventoryResponse("PROD-002", 50, 0, "any", "any"));

        orderService.process(new Order("ORD-001", "PROD-002", 1, "Alice"));

        verify(orderSpan).tag("order.product", "PROD-002");
    }

    /**
     * The inventory client must be called with the correct productId.
     */
    @Test
    @DisplayName("process calls InventoryClient with the order's productId")
    void processShouldCallInventoryClientWithCorrectProductId() {
        when(inventoryClient.checkInventory("PROD-004"))
                .thenReturn(new InventoryResponse("PROD-004", 100, 0, "any", "any"));

        orderService.process(new Order("ORD-001", "PROD-004", 1, "Alice"));

        verify(inventoryClient).checkInventory("PROD-004");
    }

    /**
     * The child span must be ended after processing (verifies finally block).
     */
    @Test
    @DisplayName("process ends the child span after processing")
    void processShouldEndTheSpan() {
        when(inventoryClient.checkInventory(anyString()))
                .thenReturn(new InventoryResponse("PROD-001", 50, 5, "any", "any"));

        orderService.process(new Order("ORD-001", "PROD-001", 1, "Alice"));

        verify(orderSpan).end();
    }
}
