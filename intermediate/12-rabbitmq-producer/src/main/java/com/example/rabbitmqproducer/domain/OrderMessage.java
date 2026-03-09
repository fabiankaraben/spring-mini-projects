package com.example.rabbitmqproducer.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing an order message published to RabbitMQ.
 *
 * <p>This class is the payload that travels through the message broker.
 * Each instance encapsulates all the data a downstream consumer (e.g. an
 * inventory service, a shipping service) needs to process the order.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li><strong>messageId</strong>: a UUID generated at creation time that
 *       uniquely identifies this message. Consumers can use it for idempotency
 *       checks (i.e. ignore duplicate deliveries).</li>
 *   <li><strong>createdAt</strong>: an ISO-8601 timestamp set at creation time.
 *       Useful for auditing, dead-letter analysis, and SLA tracking.</li>
 *   <li>The class is serialised to JSON by {@link com.fasterxml.jackson.databind.ObjectMapper}
 *       via the {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter}
 *       configured in {@link com.example.rabbitmqproducer.config.RabbitMQConfig}.</li>
 * </ul>
 */
public class OrderMessage {

    /**
     * Unique identifier for this message, auto-generated as a random UUID.
     * Consumers should store this ID and discard any message with a duplicate ID
     * to achieve at-least-once delivery with idempotent processing.
     */
    private String messageId;

    /**
     * Business identifier for the order (provided by the API caller).
     * Example: "ORD-20240315-001"
     */
    private String orderId;

    /**
     * Name of the product included in this order.
     * Example: "Wireless Keyboard"
     */
    private String product;

    /**
     * Number of units ordered.
     * Must be a positive integer.
     */
    private int quantity;

    /**
     * ISO-8601 UTC timestamp recording when this message object was created.
     * Set automatically in the constructor — callers do not need to provide this.
     */
    private Instant createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────────

    /**
     * No-arg constructor required by Jackson for JSON deserialisation.
     *
     * <p>Jackson uses reflection to create an empty instance and then populates
     * fields via setter methods (or public fields). Without this constructor
     * Jackson cannot deserialise a JSON payload back into an {@code OrderMessage}.
     */
    public OrderMessage() {
    }

    /**
     * Full constructor used by the service layer to create a new message.
     *
     * <p>{@code messageId} and {@code createdAt} are auto-generated here so
     * callers do not need to worry about them.
     *
     * @param orderId  the business order identifier
     * @param product  the name of the ordered product
     * @param quantity the number of units ordered
     */
    public OrderMessage(String orderId, String product, int quantity) {
        // Auto-generate a unique message ID to support consumer idempotency checks
        this.messageId = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.product = product;
        this.quantity = quantity;
        // Capture creation time for auditing and SLA tracking
        this.createdAt = Instant.now();
    }

    // ── Getters and Setters ───────────────────────────────────────────────────────

    /**
     * @return the unique message identifier (UUID string)
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * @param messageId the unique message identifier (used by Jackson for deserialisation)
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /**
     * @return the business order identifier
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * @param orderId the business order identifier
     */
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    /**
     * @return the name of the ordered product
     */
    public String getProduct() {
        return product;
    }

    /**
     * @param product the name of the ordered product
     */
    public void setProduct(String product) {
        this.product = product;
    }

    /**
     * @return the number of units ordered
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * @param quantity the number of units ordered
     */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    /**
     * @return the ISO-8601 UTC timestamp when this message was created
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * @param createdAt the creation timestamp (used by Jackson for deserialisation)
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Human-readable representation useful for logging and debugging.
     *
     * @return string form of the order message
     */
    @Override
    public String toString() {
        return "OrderMessage{" +
                "messageId='" + messageId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", product='" + product + '\'' +
                ", quantity=" + quantity +
                ", createdAt=" + createdAt +
                '}';
    }
}
