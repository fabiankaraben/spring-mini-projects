package com.example.serverless.domain;

import java.time.Instant;

/**
 * Input DTO for the {@code auditLogger} consumer function.
 *
 * <p>Represents an audit event that records what happened to an order.
 * The {@code auditLogger} bean is a {@link java.util.function.Consumer} —
 * it accepts the event, logs it, and returns nothing (void / no response body).
 *
 * <p>Spring Cloud Function's web adapter returns HTTP 202 Accepted for Consumer
 * invocations because they are fire-and-forget with no return value.
 *
 * <p>Example JSON payload:
 * <pre>{@code
 * {
 *   "eventType": "INVOICE_GENERATED",
 *   "orderId":   "ORD-001",
 *   "actor":     "invoice-service",
 *   "details":   "Invoice INV-ORD-001-1712345678 generated for CUST-42",
 *   "occurredAt": "2024-04-05T12:34:56Z"
 * }
 * }</pre>
 */
public class AuditEvent {

    /**
     * Type of audit event.
     * Examples: "TAX_CALCULATED", "DISCOUNT_APPLIED", "INVOICE_GENERATED".
     */
    private String eventType;

    /** The order ID this event relates to. */
    private String orderId;

    /** The service or user that triggered the event. */
    private String actor;

    /** Human-readable description of what happened. */
    private String details;

    /**
     * Timestamp when the event occurred.
     * If not provided by the caller, the consumer sets it to {@code Instant.now()}.
     */
    private Instant occurredAt;

    // Default constructor required for Jackson deserialization.
    public AuditEvent() {
    }

    /**
     * Convenience constructor for tests and application code.
     *
     * @param eventType  classification of the event
     * @param orderId    related order identifier
     * @param actor      entity that triggered the event
     * @param details    descriptive message
     * @param occurredAt when the event happened
     */
    public AuditEvent(String eventType, String orderId, String actor,
                      String details, Instant occurredAt) {
        this.eventType = eventType;
        this.orderId = orderId;
        this.actor = actor;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    @Override
    public String toString() {
        return "AuditEvent{eventType='" + eventType + "', orderId='" + orderId
                + "', actor='" + actor + "', occurredAt=" + occurredAt + '}';
    }
}
