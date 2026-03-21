package com.example.tracing.model;

/**
 * Response returned by the InventoryController when checking stock.
 *
 * <p>This record is used both as the HTTP response body of
 * {@code GET /inventory/{productId}} and as the deserialized response
 * received by the Feign client {@code InventoryClient} inside the same JVM.
 *
 * <p><b>Why same-JVM Feign call for tracing demos?</b>
 * In a real microservices deployment, the OrderService and InventoryService
 * would run as separate processes. Here, both controllers live in the same
 * Spring Boot application to keep the mini-project self-contained. The Feign
 * client still makes a real outbound HTTP call (to {@code localhost}), so
 * Brave correctly injects {@code X-B3-TraceId} / {@code X-B3-SpanId} headers
 * into the outbound request and the InventoryController's
 * instrumentation extracts them, creating a genuine parent→child span relationship
 * visible in Zipkin.
 *
 * @param productId   the product whose stock was checked
 * @param available   number of units currently in stock
 * @param reserved    number of units reserved for pending orders
 * @param traceId     the trace ID active in the inventory span (for demonstration)
 * @param spanId      the span ID of the inventory check span
 */
public record InventoryResponse(
        String productId,
        int available,
        int reserved,
        String traceId,
        String spanId
) {}
