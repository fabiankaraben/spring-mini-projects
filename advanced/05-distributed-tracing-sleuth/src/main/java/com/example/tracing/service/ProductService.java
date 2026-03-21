package com.example.tracing.service;

import com.example.tracing.model.Product;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service layer for product catalogue operations.
 *
 * <p>This service demonstrates how to:
 * <ol>
 *   <li><b>Read the current trace/span IDs</b> from the Micrometer Tracer and include
 *       them in the domain object returned to the controller.</li>
 *   <li><b>Add custom tags to the active span</b> via {@link Span#tag(String, String)}.
 *       Tags appear in the Zipkin span detail panel and can be used to filter/search
 *       traces (e.g., find all traces that touched product "PROD-001").</li>
 *   <li><b>Record span events</b> via {@link Span#event(String)} to annotate
 *       significant moments inside a span (similar to log entries but attached to the span).</li>
 * </ol>
 *
 * <p><b>In-memory catalogue:</b>
 * Products are stored in a static {@link Map} to keep the project self-contained
 * (no database). In a real service, this would be a JPA repository or REST call.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /**
     * Micrometer Tracing's core API for accessing and manipulating the current span.
     * Auto-configured by Spring Boot when {@code micrometer-tracing-bridge-brave} is
     * on the classpath.
     */
    private final Tracer tracer;

    /** The Spring application name, injected from {@code spring.application.name}. */
    private final String serviceName;

    /**
     * In-memory product catalogue — keyed by product ID.
     * Pre-populated with sample data for demonstration.
     */
    private static final Map<String, Product> CATALOGUE = Map.of(
            "PROD-001", new Product("PROD-001", "Laptop Pro 15", 1299.99, "Electronics", null, null),
            "PROD-002", new Product("PROD-002", "Wireless Headphones", 249.99, "Electronics", null, null),
            "PROD-003", new Product("PROD-003", "Ergonomic Chair", 499.99, "Furniture", null, null),
            "PROD-004", new Product("PROD-004", "Mechanical Keyboard", 159.99, "Electronics", null, null),
            "PROD-005", new Product("PROD-005", "Standing Desk", 799.99, "Furniture", null, null)
    );

    public ProductService(Tracer tracer,
                          @Value("${spring.application.name}") String serviceName) {
        this.tracer = tracer;
        this.serviceName = serviceName;
    }

    /**
     * Looks up a product by its identifier and enriches the result with the current
     * trace context (traceId, spanId).
     *
     * <p><b>What happens here from a tracing perspective:</b>
     * <ol>
     *   <li>The HTTP request arrived at {@code ProductController} and Spring MVC
     *       auto-instrumentation already created an enclosing span for this request.</li>
     *   <li>We retrieve that active span via {@code tracer.currentSpan()}.</li>
     *   <li>We add product-specific tags to the span so they show up in Zipkin.</li>
     *   <li>We record a "product.lookup" event on the span to mark the exact moment
     *       the catalogue was queried.</li>
     *   <li>We include the traceId and spanId in the returned Product record so the
     *       HTTP caller can immediately see and use them.</li>
     * </ol>
     *
     * @param productId the product identifier to look up
     * @return a {@link Product} with tracing context included, or {@code null} if not found
     */
    public Product findById(String productId) {
        // Obtain the span that was started by Spring MVC's auto-instrumentation
        // for this HTTP request. We do NOT start a new span here — instead we
        // enrich the existing one with product-specific tags.
        Span currentSpan = tracer.currentSpan();

        log.info("Looking up product [productId={}] traceId={} spanId={}",
                productId,
                currentSpan != null ? currentSpan.context().traceId() : "none",
                currentSpan != null ? currentSpan.context().spanId() : "none");

        Product product = CATALOGUE.get(productId);

        if (product == null) {
            // Tag the span to record the miss — useful for alerting on high 404 rates
            if (currentSpan != null) {
                currentSpan.tag("product.found", "false");
                currentSpan.tag("product.id", productId);
                currentSpan.event("product.not_found");
            }
            log.warn("Product not found [productId={}]", productId);
            return null;
        }

        // Add custom tags to the active span.
        // These key=value pairs appear in Zipkin under the span's "Tags" section.
        // Useful for filtering: in Zipkin you can query "product.category=Electronics"
        // to find all traces for electronics products.
        if (currentSpan != null) {
            currentSpan.tag("product.id", product.id());
            currentSpan.tag("product.name", product.name());
            currentSpan.tag("product.category", product.category());
            currentSpan.tag("product.found", "true");

            // Record a span event — a timestamped annotation inside the span.
            // Events are shown on the Zipkin timeline as vertical markers.
            currentSpan.event("product.catalogue_lookup");
        }

        // Extract the trace and span IDs to include in the response body.
        // This is educational — in production you would NOT typically return trace IDs
        // in the response body (they belong in logs and the tracing backend).
        String traceId = currentSpan != null ? currentSpan.context().traceId() : "no-trace";
        String spanId  = currentSpan != null ? currentSpan.context().spanId()  : "no-span";

        // Return a new Product record with the tracing fields populated.
        // The original catalogue entries have null traceId/spanId because they are
        // created at class load time (before any request arrives).
        return new Product(
                product.id(),
                product.name(),
                product.price(),
                product.category(),
                traceId,
                spanId
        );
    }

    /**
     * Returns all products in the catalogue without any specific tracing enrichment.
     * Used by the listing endpoint as a simpler demonstration.
     *
     * @return all products in the static catalogue
     */
    public java.util.Collection<Product> findAll() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag("product.count", String.valueOf(CATALOGUE.size()));
            currentSpan.event("product.list_all");
        }
        // Return products with trace context embedded in each entry
        String traceId = currentSpan != null ? currentSpan.context().traceId() : "no-trace";
        String spanId  = currentSpan != null ? currentSpan.context().spanId()  : "no-span";
        return CATALOGUE.values().stream()
                .map(p -> new Product(p.id(), p.name(), p.price(), p.category(), traceId, spanId))
                .toList();
    }
}
