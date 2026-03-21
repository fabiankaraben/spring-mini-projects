package com.example.tracing.service;

import com.example.tracing.client.InventoryClient;
import com.example.tracing.model.InventoryResponse;
import com.example.tracing.model.Order;
import com.example.tracing.model.OrderResult;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Core business logic for order processing.
 *
 * <p>This service demonstrates two key distributed tracing patterns:
 * <ol>
 *   <li><b>Manual child span creation</b> — creating a named span around a specific
 *       business operation ({@code "process-order"}) that appears as a child of the
 *       inbound HTTP request span in Zipkin.</li>
 *   <li><b>Automatic cross-service context propagation</b> — when this service
 *       calls the InventoryService via the {@code InventoryClient} Feign client,
 *       Brave automatically injects the current span context ({@code traceId},
 *       {@code spanId}) into the outbound HTTP headers. The receiving
 *       InventoryController extracts these headers and continues the same trace,
 *       so both the order-processing span and the inventory-check span appear in
 *       the same Zipkin trace waterfall.</li>
 * </ol>
 *
 * <p><b>Span hierarchy produced by placing a single order:</b>
 * <pre>
 *   [POST /orders]          traceId=abc spanId=111  ← root, created by Spring MVC
 *     └─ [process-order]    traceId=abc spanId=222  ← child, created manually here
 *          └─ [GET /inventory/{id}] traceId=abc spanId=333 ← created by Feign+Brave
 *               └─ [inventory-check] traceId=abc spanId=444 ← created in InventoryService
 * </pre>
 * All four spans share {@code traceId=abc}.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Micrometer Tracing API — auto-configured by Spring Boot. */
    private final Tracer tracer;

    /**
     * Feign HTTP client for the Inventory service.
     * Brave intercepts each call and injects B3 propagation headers automatically.
     */
    private final InventoryClient inventoryClient;

    /** The Spring application name, used in log messages and trace tags. */
    private final String serviceName;

    public OrderService(Tracer tracer,
                        InventoryClient inventoryClient,
                        @Value("${spring.application.name}") String serviceName) {
        this.tracer = tracer;
        this.inventoryClient = inventoryClient;
        this.serviceName = serviceName;
    }

    /**
     * Processes an incoming order request:
     * <ol>
     *   <li>Creates a child span named {@code "process-order"} under the active request span.</li>
     *   <li>Tags the span with order metadata (orderId, productId, customer, quantity).</li>
     *   <li>Calls the InventoryClient to reserve stock — this makes a real HTTP call and
     *       automatically propagates the trace context through B3 headers.</li>
     *   <li>Tags the span with the inventory reservation result.</li>
     *   <li>Returns an {@link OrderResult} that includes the trace and span IDs so the
     *       caller can look up the trace in Zipkin.</li>
     * </ol>
     *
     * @param order the validated order submitted by the client
     * @return the result of the order processing, including tracing identifiers
     */
    public OrderResult process(Order order) {
        // Create a child span for the "process-order" unit of work.
        // tracer.nextSpan() makes this span a child of whatever span is currently active.
        // At this point the active span is the one that Spring MVC created for POST /orders.
        Span orderSpan = tracer.nextSpan().name("process-order");

        // Activate the span on this thread so all operations inside the try block
        // have the correct span context in log MDC and tracer.currentSpan().
        try (Tracer.SpanInScope ws = tracer.withSpan(orderSpan.start())) {

            log.info("Processing order [orderId={} productId={} qty={} customer={}] traceId={} spanId={}",
                    order.orderId(), order.productId(), order.quantity(), order.customer(),
                    orderSpan.context().traceId(), orderSpan.context().spanId());

            // Tag the span with all relevant order fields.
            // These tags make the span searchable in Zipkin by order ID, product, etc.
            orderSpan.tag("order.id",       order.orderId());
            orderSpan.tag("order.product",  order.productId());
            orderSpan.tag("order.customer", order.customer());
            orderSpan.tag("order.quantity", String.valueOf(order.quantity()));
            orderSpan.tag("service.name",   serviceName);

            // Record an event to mark the start of inventory reservation
            orderSpan.event("order.inventory_reservation_started");

            // Call the InventoryService via the Feign client.
            // This triggers an HTTP GET /inventory/{productId}.
            // Brave's FeignClient integration automatically injects:
            //   X-B3-TraceId: <current traceId>
            //   X-B3-SpanId:  <new child spanId for the Feign call>
            //   X-B3-ParentSpanId: <this process-order span's spanId>
            //   X-B3-Sampled: 1
            // The InventoryController extracts these headers and creates a child span.
            InventoryResponse inventory = inventoryClient.checkInventory(order.productId());

            // Determine whether we can fulfil the order based on available stock
            boolean canFulfil = inventory.available() >= order.quantity();
            String status = canFulfil ? "ACCEPTED" : "BACKORDER";

            orderSpan.tag("order.status",     status);
            orderSpan.tag("inventory.available", String.valueOf(inventory.available()));
            orderSpan.event("order.inventory_reservation_completed");

            log.info("Order processed [orderId={} status={} available={}]",
                    order.orderId(), status, inventory.available());

            // Build the result, including the trace and span IDs for the caller
            return new OrderResult(
                    order.orderId(),
                    status,
                    orderSpan.context().traceId(),
                    orderSpan.context().spanId(),
                    canFulfil
                            ? "Reserved " + order.quantity() + " unit(s) from stock."
                            : "Insufficient stock. Available: " + inventory.available()
                              + ". Order placed on backorder."
            );

        } finally {
            // Always end the manually created span in a finally block.
            orderSpan.end();
        }
    }
}
