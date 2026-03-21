package com.example.zipkinintegration.dto;

import com.example.zipkinintegration.domain.Order;
import com.example.zipkinintegration.domain.OrderStatus;

/**
 * Response DTO returned by the Order API.
 *
 * <p>Decouples the internal {@link Order} domain object from the HTTP
 * response payload, allowing both to evolve independently.
 *
 * <p>The trace ID is included in the response so callers can look up the
 * corresponding trace in the Zipkin UI without needing to inspect headers.
 */
public class OrderResponse {

    /** Unique identifier for the order. */
    private Long id;

    /** Product name or SKU that was ordered. */
    private String product;

    /** Number of units ordered. */
    private int quantity;

    /** Current lifecycle status of the order. */
    private OrderStatus status;

    /**
     * The Micrometer Tracing trace ID for this request.
     *
     * <p>This value is extracted from the current span context and included
     * in the response so the caller can quickly navigate to the trace in
     * the Zipkin UI at {@code http://localhost:9411/zipkin/?traceId=<traceId>}.
     */
    private String traceId;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Default constructor required by Jackson for serialization. */
    public OrderResponse() {}

    /**
     * Convenience constructor that maps an {@link Order} domain object
     * plus the current trace ID into the response payload.
     *
     * @param order   the domain order to serialize
     * @param traceId the Micrometer tracing trace ID for the current request
     */
    public OrderResponse(Order order, String traceId) {
        this.id = order.getId();
        this.product = order.getProduct();
        this.quantity = order.getQuantity();
        this.status = order.getStatus();
        this.traceId = traceId;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
