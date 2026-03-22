package com.example.saga.order.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an Order in the saga.
 *
 * <p>Each order tracks:
 * <ul>
 *   <li>The customer who placed it ({@code customerId}).</li>
 *   <li>The product being ordered ({@code productId}) and its quantity.</li>
 *   <li>The total price charged to the customer.</li>
 *   <li>The current {@link OrderStatus} within the saga lifecycle.</li>
 * </ul>
 *
 * <p>Status transitions are driven by Kafka events consumed by
 * {@code OrderEventConsumer} and exposed for query via the REST layer.
 */
@Entity
@Table(name = "orders")
public class Order {

    /**
     * Primary key — a UUID generated before persistence so the value
     * is known immediately (useful for publishing events before flushing).
     */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    /**
     * Identifier of the customer placing the order.
     * Treated as an opaque string (could be a UUID from an IAM system).
     */
    @NotBlank
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    /**
     * Identifier of the product being ordered.
     */
    @NotBlank
    @Column(name = "product_id", nullable = false)
    private String productId;

    /**
     * Number of units requested.
     */
    @Positive
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * Total price = unit_price × quantity, calculated by the service layer.
     */
    @NotNull
    @Column(name = "total_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPrice;

    /**
     * Current saga state of this order. Starts as {@link OrderStatus#PENDING}.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    /**
     * UTC timestamp of when this order was first created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * UTC timestamp of the last status update (set on every status transition).
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Populated when the order is CANCELLED; records a human-readable reason.
     */
    @Column(name = "failure_reason")
    private String failureReason;

    /** JPA no-arg constructor (required by Hibernate). */
    protected Order() {}

    /**
     * Factory constructor — creates a new order in PENDING status.
     *
     * @param customerId identifier of the ordering customer
     * @param productId  identifier of the product
     * @param quantity   number of units
     * @param totalPrice computed total price
     */
    public Order(String customerId, String productId, int quantity, BigDecimal totalPrice) {
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Transitions the order to {@link OrderStatus#PAYMENT_PROCESSING} after
     * the OrderCreatedEvent has been published to Kafka.
     */
    public void markPaymentProcessing() {
        this.status = OrderStatus.PAYMENT_PROCESSING;
        this.updatedAt = Instant.now();
    }

    /**
     * Transitions the order to {@link OrderStatus#INVENTORY_RESERVING} after
     * a PaymentProcessedEvent has been received from the Payment Service.
     */
    public void markInventoryReserving() {
        this.status = OrderStatus.INVENTORY_RESERVING;
        this.updatedAt = Instant.now();
    }

    /**
     * Transitions the order to {@link OrderStatus#COMPLETED} after all saga
     * steps have succeeded (inventory reserved).
     */
    public void markCompleted() {
        this.status = OrderStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    /**
     * Transitions the order to {@link OrderStatus#CANCELLED} due to a saga
     * failure in any downstream service.
     *
     * @param reason human-readable failure description
     */
    public void markCancelled(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }

    public String getCustomerId() { return customerId; }

    public String getProductId() { return productId; }

    public int getQuantity() { return quantity; }

    public BigDecimal getTotalPrice() { return totalPrice; }

    public OrderStatus getStatus() { return status; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public String getFailureReason() { return failureReason; }
}
