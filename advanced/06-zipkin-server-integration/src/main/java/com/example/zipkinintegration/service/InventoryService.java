package com.example.zipkinintegration.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Simulates an inventory sub-service that checks product stock availability.
 *
 * <h2>Tracing purpose</h2>
 * <p>This service exists primarily to demonstrate <em>child spans</em> in
 * Zipkin. When {@link OrderService} calls {@code checkAvailability()}, a new
 * child span is started manually using the {@link Tracer} API. This produces
 * a two-level trace tree in the Zipkin UI:
 * <pre>
 *   POST /api/orders  (root span, created by Spring MVC instrumentation)
 *   └── order-service.createOrder  (child span, created by OrderService)
 *       └── inventory.checkAvailability  (grandchild span, created here)
 * </pre>
 *
 * <h2>Simulated latency</h2>
 * <p>A small artificial delay is introduced so that spans have a visible
 * duration in the Zipkin UI waterfall. In a real system this would be the
 * time spent querying a database or calling another microservice.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /**
     * The Micrometer {@link Tracer} is auto-configured by Spring Boot when the
     * {@code micrometer-tracing-bridge-brave} dependency is on the classpath.
     * Injecting it here allows us to create and manage child spans manually.
     */
    private final Tracer tracer;

    public InventoryService(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Checks whether the requested quantity of a product is available in stock.
     *
     * <p>A new child span named {@code "inventory.checkAvailability"} is
     * started before the simulated check and closed in a {@code finally} block
     * to ensure it is always ended, even if an exception is thrown.
     *
     * <p>Key span tags added:
     * <ul>
     *   <li>{@code product} – the product being checked</li>
     *   <li>{@code requested.quantity} – the number of units requested</li>
     *   <li>{@code available} – whether stock is sufficient</li>
     * </ul>
     *
     * @param product  the product name or SKU to look up
     * @param quantity the number of units required
     * @return {@code true} if sufficient stock is available; {@code false} otherwise
     */
    public boolean checkAvailability(String product, int quantity) {
        // Start a new child span. nextSpan() automatically sets the current
        // span as the parent, building the trace tree.
        Span span = tracer.nextSpan().name("inventory.checkAvailability").start();

        // Use try-with-resources on SpanInScope so that the span is made
        // "current" for the duration of this block (propagates to MDC, etc.)
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            log.info("Checking inventory for product='{}', quantity={}", product, quantity);

            // Add structured tags to the span – these appear as annotations
            // in the Zipkin UI and help correlate traces with business data.
            span.tag("product", product);
            span.tag("requested.quantity", String.valueOf(quantity));

            // Simulate a small I/O delay (e.g. a database or remote call)
            simulateDelay(50);

            // Simple simulation: products with names starting with "unavailable"
            // are always out of stock; everything else is available.
            boolean available = !product.toLowerCase().startsWith("unavailable");

            span.tag("available", String.valueOf(available));
            log.info("Inventory check result: product='{}', available={}", product, available);

            return available;
        } finally {
            // Always end the span in a finally block to avoid span leaks
            // (spans that are never reported to Zipkin).
            span.end();
        }
    }

    /**
     * Introduces a simulated processing delay.
     *
     * <p>This makes span durations visible in the Zipkin UI waterfall diagram.
     * In production code this method would not exist.
     *
     * @param millis how many milliseconds to sleep
     */
    private void simulateDelay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
