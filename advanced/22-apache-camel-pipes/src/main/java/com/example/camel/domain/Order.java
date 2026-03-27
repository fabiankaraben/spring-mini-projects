package com.example.camel.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Core domain object representing a customer order travelling through the pipeline.
 *
 * <p>An {@code Order} is created from the REST request payload and is then progressively
 * enriched and classified as it moves through each Camel filter stage.  All fields are
 * mutable so that processors can attach computed values (e.g. VAT amount, region, priority).
 *
 * <p>Validation constraints are enforced in {@link com.example.camel.processor.ValidationProcessor}
 * rather than via Bean Validation annotations so that validation failure can be handled
 * inside the Camel error-handling DSL and routed to the dead-letter channel.
 */
public class Order {

    // ── Mandatory fields set by the caller ─────────────────────────────────

    /** Unique identifier for the order (UUID string expected). */
    private String orderId;

    /** Identifier of the customer placing the order. */
    private String customerId;

    /** Human-readable product name being ordered. */
    private String productName;

    /** Unit price of the product (must be positive). */
    private BigDecimal unitPrice;

    /** Number of units ordered (must be ≥ 1). */
    private int quantity;

    // ── Enriched / computed fields set by pipeline processors ───────────────

    /**
     * Total cost = unitPrice × quantity, computed by the enrichment processor.
     * Null until the enrichment stage runs.
     */
    private BigDecimal totalAmount;

    /**
     * VAT amount = totalAmount × vatRate, computed by the enrichment processor.
     * Null until the enrichment stage runs.
     */
    private BigDecimal vatAmount;

    /**
     * Geographic region derived from customerId prefix (e.g. "EU", "US", "APAC").
     * Set by the enrichment processor.
     */
    private String region;

    /**
     * Order classification: "PRIORITY" if totalAmount exceeds the configured threshold,
     * "STANDARD" otherwise.  Set by the classification processor.
     */
    private OrderPriority priority;

    /** ISO-8601 timestamp of when the order was received. Set by the validation processor. */
    private Instant receivedAt;

    /** ISO-8601 timestamp of when the order completed the full pipeline. */
    private Instant processedAt;

    /** Current pipeline stage label for observability (set by each processor). */
    private String stage;

    // ── Constructors ────────────────────────────────────────────────────────

    /** Default no-arg constructor required by Jackson deserialization. */
    public Order() {}

    /** Convenience constructor for tests and factories. */
    public Order(String orderId, String customerId, String productName,
                 BigDecimal unitPrice, int quantity) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    // ── Getters and setters ─────────────────────────────────────────────────

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getVatAmount() { return vatAmount; }
    public void setVatAmount(BigDecimal vatAmount) { this.vatAmount = vatAmount; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public OrderPriority getPriority() { return priority; }
    public void setPriority(OrderPriority priority) { this.priority = priority; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    @Override
    public String toString() {
        return "Order{orderId='" + orderId + "', customerId='" + customerId +
                "', product='" + productName + "', qty=" + quantity +
                ", total=" + totalAmount + ", priority=" + priority +
                ", region='" + region + "', stage='" + stage + "'}";
    }
}
