package com.example.tracing.service;

import com.example.tracing.model.InventoryResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service layer for inventory stock operations.
 *
 * <p>This service is called by the InventoryController. From a tracing perspective,
 * the span that wraps calls to this service is a <em>child span</em> of whatever
 * span the caller had active when it made the HTTP request. If the call came from
 * the Feign client in {@code OrderService}, the chain looks like:
 * <pre>
 *   [POST /orders]          — root span (created by Spring MVC auto-instrumentation)
 *     └─ [process-order]    — child span (created manually in OrderService)
 *          └─ [GET /inventory/{id}] — grandchild span (created by Feign + Brave)
 *               └─ [inventory-check] — manually created span inside InventoryService
 * </pre>
 *
 * <p>All four spans share the same traceId, which is why in Zipkin you see all of
 * them as a waterfall under a single trace.
 *
 * <p><b>Programmatic span creation:</b>
 * This service demonstrates starting a child span <em>manually</em> using
 * {@code tracer.nextSpan().name("inventory-check").start()}. The span must be
 * activated in a try-with-resources block (via {@code tracer.withSpan(span)})
 * so that the tracing context is set on the current thread and subsequent log
 * statements include the correct span IDs.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /** Micrometer Tracing API — auto-configured by Spring Boot. */
    private final Tracer tracer;

    /**
     * Simulated inventory store — maps productId to (available, reserved) units.
     * In a production service this would be backed by a database.
     */
    private static final Map<String, int[]> INVENTORY = Map.of(
            "PROD-001", new int[]{50, 5},
            "PROD-002", new int[]{120, 12},
            "PROD-003", new int[]{30, 3},
            "PROD-004", new int[]{200, 20},
            "PROD-005", new int[]{15, 2}
    );

    public InventoryService(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Checks the inventory level for the given product and returns the result
     * enriched with the current trace context.
     *
     * <p><b>Programmatic child span:</b>
     * A named child span {@code "inventory-check"} is started manually here to
     * demonstrate that you can create spans for any piece of work — not just HTTP
     * requests. The span is tagged with the productId and the availability result.
     *
     * <p>The span MUST be ended in a {@code finally} block to avoid span leaks.
     * A leaked span (one that is started but never ended) prevents its data from
     * being flushed to Zipkin and corrupts the tracing context for subsequent
     * operations on the same thread.
     *
     * @param productId the product to check
     * @return current stock levels, including tracing identifiers
     */
    public InventoryResponse checkStock(String productId) {
        // Create a new child span named "inventory-check".
        // tracer.nextSpan() creates a span that is a child of the currently active span
        // (the Feign client span, which itself is a child of the order-processing span).
        Span inventorySpan = tracer.nextSpan().name("inventory-check");

        // withSpan() activates the span on the current thread, making it the "current span"
        // for the duration of the try block. This ensures log MDC is updated with the new
        // span's IDs, and any further tracer.currentSpan() calls see this span.
        try (Tracer.SpanInScope ws = tracer.withSpan(inventorySpan.start())) {

            log.info("Checking inventory [productId={}] traceId={} spanId={}",
                    productId,
                    inventorySpan.context().traceId(),
                    inventorySpan.context().spanId());

            // Tag the span with the product being queried
            inventorySpan.tag("inventory.product_id", productId);

            int[] stock = INVENTORY.get(productId);

            if (stock == null) {
                // Record the miss and tag accordingly
                inventorySpan.tag("inventory.found", "false");
                inventorySpan.event("inventory.product_unknown");
                log.warn("Inventory record not found [productId={}]", productId);
                return new InventoryResponse(
                        productId, 0, 0,
                        inventorySpan.context().traceId(),
                        inventorySpan.context().spanId());
            }

            int available = stock[0];
            int reserved  = stock[1];

            // Tag the span with the inventory levels
            inventorySpan.tag("inventory.available", String.valueOf(available));
            inventorySpan.tag("inventory.reserved",  String.valueOf(reserved));
            inventorySpan.tag("inventory.found",     "true");

            // Record an event to mark the successful lookup moment
            inventorySpan.event("inventory.stock_read");

            log.info("Inventory found [productId={} available={} reserved={}]",
                    productId, available, reserved);

            return new InventoryResponse(
                    productId,
                    available,
                    reserved,
                    inventorySpan.context().traceId(),
                    inventorySpan.context().spanId()
            );

        } finally {
            // IMPORTANT: always end the span in a finally block.
            // If the span is never ended it is never reported to Zipkin.
            inventorySpan.end();
        }
    }
}
