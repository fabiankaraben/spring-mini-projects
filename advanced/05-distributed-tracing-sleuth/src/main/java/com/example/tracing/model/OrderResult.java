package com.example.tracing.model;

/**
 * Represents the result of processing an order.
 *
 * <p>Returned by {@code POST /orders} after the order has been validated,
 * the inventory has been reserved, and the order has been persisted.
 *
 * <p><b>Tracing fields:</b>
 * The {@code traceId} and {@code spanId} fields are populated from the
 * <em>active span</em> at the moment the response is assembled. Including them
 * in the HTTP response body serves two educational purposes:
 * <ol>
 *   <li>It makes the trace immediately visible to the caller — you can paste
 *       the {@code traceId} straight into the Zipkin search box.</li>
 *   <li>It demonstrates that the trace context (the same IDs that appear in
 *       application logs) is accessible from application code via the
 *       {@code io.micrometer.tracing.Tracer} API.</li>
 * </ol>
 *
 * @param orderId   the order identifier (echo of the request)
 * @param status    human-readable status message (e.g. "ACCEPTED")
 * @param traceId   the Micrometer Tracing trace ID for this request's trace
 * @param spanId    the Micrometer Tracing span ID for the order-processing span
 * @param message   optional detail message (e.g. inventory reservation result)
 */
public record OrderResult(
        String orderId,
        String status,
        String traceId,
        String spanId,
        String message
) {}
