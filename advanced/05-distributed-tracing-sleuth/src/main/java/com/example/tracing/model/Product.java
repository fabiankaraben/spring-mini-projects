package com.example.tracing.model;

/**
 * Represents a product in the catalogue.
 *
 * <p>Returned by {@code GET /products/{id}}. The response also includes
 * the active {@code traceId} and {@code spanId} so that callers can see
 * the tracing context in action directly from the HTTP response.
 *
 * <p><b>Manual span tagging:</b>
 * In {@code ProductService}, when a product is looked up, the following
 * custom tags are added to the active span:
 * <ul>
 *   <li>{@code product.id} — the product identifier</li>
 *   <li>{@code product.name} — the product name</li>
 *   <li>{@code product.category} — the product category</li>
 * </ul>
 * These tags appear in Zipkin's span detail panel, making it easy to search
 * for all traces that touched a specific product.
 *
 * @param id        unique product identifier
 * @param name      human-readable product name
 * @param price     product price in USD
 * @param category  product category for tag-based filtering in Zipkin
 * @param traceId   the Micrometer Tracing trace ID for this request
 * @param spanId    the Micrometer Tracing span ID for this operation
 */
public record Product(
        String id,
        String name,
        double price,
        String category,
        String traceId,
        String spanId
) {}
