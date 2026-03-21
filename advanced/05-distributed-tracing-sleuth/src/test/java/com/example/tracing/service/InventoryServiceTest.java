package com.example.tracing.service;

import com.example.tracing.model.InventoryResponse;
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
 * Unit tests for {@link InventoryService}.
 *
 * <p>These tests verify the service's business logic and tracing behaviour
 * without starting a Spring context or Docker containers.
 *
 * <p><b>Mocking the Tracer for programmatic span creation:</b>
 * In Micrometer Tracing, {@code tracer.nextSpan()} returns a {@code Span} directly
 * (not a builder). The span is then named via {@code span.name("inventory-check")}
 * (returns {@code Span}) and started via {@code span.start()} (returns {@code Span}).
 * The mock chain is:
 * <pre>
 *   tracer.nextSpan()              → inventorySpan (Span)
 *   inventorySpan.name("...")      → inventorySpan (Span, fluent)
 *   inventorySpan.start()          → inventorySpan (Span, fluent)
 *   tracer.withSpan(inventorySpan) → spanInScope   (SpanInScope / Closeable)
 *   inventorySpan.context()        → traceContext
 *   traceContext.traceId()         → "inv-trace"
 *   traceContext.spanId()          → "inv-span"
 * </pre>
 * The {@code SpanInScope} is a {@code Closeable}, so the mock must also implement
 * the {@code close()} method (Mockito auto-mocks this for interfaces).
 *
 * <p><b>What is tested:</b>
 * <ul>
 *   <li>Known product returns correct available/reserved values.</li>
 *   <li>Response includes traceId and spanId from the child span.</li>
 *   <li>Span tags are applied (product_id, available, reserved, found).</li>
 *   <li>Unknown product returns zero stock with found=false tag.</li>
 *   <li>Span is ended (via end()) regardless of outcome (verified by Mockito).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService — unit tests")
class InventoryServiceTest {

    @Mock
    private Tracer tracer;

    /**
     * The child span returned by tracer.nextSpan().
     * In Micrometer Tracing, tracer.nextSpan() returns a Span directly.
     * span.name() and span.start() are fluent methods that also return Span.
     */
    @Mock
    private Span inventorySpan;

    /** The SpanInScope returned by tracer.withSpan() */
    @Mock
    private Tracer.SpanInScope spanInScope;

    /** The TraceContext returned by inventorySpan.context() */
    @Mock
    private TraceContext traceContext;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        // Wire the span-creation chain.
        // tracer.nextSpan() returns a Span; name() and start() are fluent (return Span).
        when(tracer.nextSpan()).thenReturn(inventorySpan);
        when(inventorySpan.name("inventory-check")).thenReturn(inventorySpan);
        when(inventorySpan.start()).thenReturn(inventorySpan);
        when(tracer.withSpan(inventorySpan)).thenReturn(spanInScope);

        // Wire the context chain for ID extraction
        when(inventorySpan.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("inv-trace");
        when(traceContext.spanId()).thenReturn("inv-span");

        // Allow tag() and event() calls on the mock span
        when(inventorySpan.tag(anyString(), anyString())).thenReturn(inventorySpan);

        inventoryService = new InventoryService(tracer);
    }

    // =========================================================================
    // Known product
    // =========================================================================

    /**
     * Checking stock for a known product returns the correct product ID.
     */
    @Test
    @DisplayName("checkStock returns correct productId for known product")
    void checkStockShouldReturnCorrectProductId() {
        InventoryResponse response = inventoryService.checkStock("PROD-001");
        assertThat(response.productId()).isEqualTo("PROD-001");
    }

    /**
     * Checking stock for PROD-001 returns the seeded available value of 50.
     */
    @Test
    @DisplayName("checkStock returns available=50 for PROD-001")
    void checkStockShouldReturnCorrectAvailable() {
        InventoryResponse response = inventoryService.checkStock("PROD-001");
        assertThat(response.available()).isEqualTo(50);
    }

    /**
     * Checking stock for PROD-001 returns the seeded reserved value of 5.
     */
    @Test
    @DisplayName("checkStock returns reserved=5 for PROD-001")
    void checkStockShouldReturnCorrectReserved() {
        InventoryResponse response = inventoryService.checkStock("PROD-001");
        assertThat(response.reserved()).isEqualTo(5);
    }

    /**
     * The response should include the traceId from the child span.
     */
    @Test
    @DisplayName("checkStock embeds traceId from the child inventory span")
    void checkStockShouldEmbedTraceId() {
        InventoryResponse response = inventoryService.checkStock("PROD-002");
        assertThat(response.traceId()).isEqualTo("inv-trace");
    }

    /**
     * The response should include the spanId from the child span.
     */
    @Test
    @DisplayName("checkStock embeds spanId from the child inventory span")
    void checkStockShouldEmbedSpanId() {
        InventoryResponse response = inventoryService.checkStock("PROD-002");
        assertThat(response.spanId()).isEqualTo("inv-span");
    }

    /**
     * The inventory span must be tagged with the product ID.
     */
    @Test
    @DisplayName("checkStock tags span with inventory.product_id")
    void checkStockShouldTagSpanWithProductId() {
        inventoryService.checkStock("PROD-003");
        verify(inventorySpan).tag("inventory.product_id", "PROD-003");
    }

    /**
     * The inventory span must be tagged with found=true for a known product.
     */
    @Test
    @DisplayName("checkStock tags span with inventory.found=true for known product")
    void checkStockShouldTagFoundTrueForKnownProduct() {
        inventoryService.checkStock("PROD-001");
        verify(inventorySpan).tag("inventory.found", "true");
    }

    /**
     * The span must be ended after stock check completes (even on success).
     */
    @Test
    @DisplayName("checkStock ends the child span after completion")
    void checkStockShouldEndTheSpan() {
        inventoryService.checkStock("PROD-001");
        verify(inventorySpan).end();
    }

    // =========================================================================
    // Unknown product
    // =========================================================================

    /**
     * An unknown product returns available=0 and reserved=0.
     */
    @Test
    @DisplayName("checkStock returns zero stock for unknown product")
    void checkStockShouldReturnZeroForUnknownProduct() {
        InventoryResponse response = inventoryService.checkStock("PROD-UNKNOWN");
        assertThat(response.available()).isZero();
        assertThat(response.reserved()).isZero();
    }

    /**
     * An unknown product should tag the span with inventory.found=false.
     */
    @Test
    @DisplayName("checkStock tags span with inventory.found=false for unknown product")
    void checkStockShouldTagFoundFalseForUnknownProduct() {
        inventoryService.checkStock("PROD-UNKNOWN");
        verify(inventorySpan).tag("inventory.found", "false");
    }

    /**
     * An unknown product records the inventory.product_unknown event.
     */
    @Test
    @DisplayName("checkStock records inventory.product_unknown event for unknown product")
    void checkStockShouldRecordUnknownEventForUnknownProduct() {
        inventoryService.checkStock("PROD-UNKNOWN");
        verify(inventorySpan).event("inventory.product_unknown");
    }

    /**
     * The span must be ended even when the product is not found (finally block).
     */
    @Test
    @DisplayName("checkStock ends the span even when product is not found")
    void checkStockShouldEndSpanEvenForUnknownProduct() {
        inventoryService.checkStock("PROD-UNKNOWN");
        verify(inventorySpan).end();
    }
}
