package com.example.mongodbcustomqueries.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Domain model representing a customer order stored in MongoDB.
 *
 * <p>This document is the central entity for demonstrating MongoTemplate
 * aggregation queries. Each order belongs to a customer, contains one or
 * more line items, and is associated with a region.
 *
 * <p>Key MongoDB-specific annotations used here:
 * <ul>
 *   <li>{@link Document} – marks this class as a MongoDB document. The
 *       {@code collection} attribute sets the collection name to "orders".</li>
 *   <li>{@link Id} – maps to MongoDB's {@code _id} field. Using a {@link String}
 *       ID lets Spring Data store MongoDB's ObjectId as a human-readable hex string.</li>
 *   <li>{@link Field} – overrides the MongoDB field name in stored documents,
 *       allowing Java naming conventions (camelCase) to differ from MongoDB
 *       field naming (snake_case).</li>
 *   <li>{@link Indexed} – creates a MongoDB index on the annotated field to
 *       speed up filter and grouping queries in aggregation pipelines.</li>
 * </ul>
 *
 * <p>The nested {@link OrderItem} list models a one-to-many relationship
 * embedded directly in the parent document (denormalised), which is the
 * idiomatic MongoDB approach for data that is always accessed together.
 */
@Document(collection = "orders")
public class Order {

    /**
     * MongoDB document identifier.
     *
     * <p>Declared as {@link String} so Spring Data automatically maps MongoDB's
     * ObjectId BSON type to a hex string. MongoDB generates this value on insert
     * when the field is left {@code null}.
     */
    @Id
    private String id;

    /**
     * Name or identifier of the customer who placed this order.
     *
     * <p>Indexed for efficient grouping and filtering queries (e.g. "all orders
     * by customer X", "revenue per customer").
     */
    @Indexed
    @Field("customer_name")
    private String customerName;

    /**
     * Geographic region for the order (e.g. "North", "South", "East", "West").
     *
     * <p>Indexed to support efficient region-based aggregations such as
     * "total revenue by region" or "top-selling products per region".
     */
    @Indexed
    @Field("region")
    private String region;

    /**
     * Current status of the order (e.g. "PENDING", "SHIPPED", "DELIVERED", "CANCELLED").
     *
     * <p>Indexed to allow efficient filtering by order status in pipeline
     * {@code $match} stages.
     */
    @Indexed
    @Field("status")
    private String status;

    /**
     * Total monetary value of the order, computed as the sum of all line item totals.
     *
     * <p>Stored as {@link BigDecimal} to avoid floating-point precision issues.
     * The custom {@code MongoConfig} converters ensure this is persisted as a
     * BSON {@code Decimal128}, enabling correct numeric comparisons and arithmetic
     * in aggregation expressions (e.g. {@code $sum}, {@code $avg}).
     */
    @Field("total_amount")
    private BigDecimal totalAmount;

    /**
     * List of individual items within this order.
     *
     * <p>Stored as an embedded array in the MongoDB document. The {@code $unwind}
     * aggregation stage can deconstruct this array to produce one document per
     * item, enabling per-product analytics.
     */
    @Field("items")
    private List<OrderItem> items;

    /**
     * Timestamp when the order was placed.
     *
     * <p>Used in time-range filter stages ({@code $match}) and for sorting results
     * by recency.
     */
    @Field("created_at")
    private Instant createdAt;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /** No-arg constructor required by Spring Data MongoDB for document deserialisation. */
    public Order() {}

    /**
     * Convenience constructor for creating new order documents.
     * The {@code id} is intentionally omitted — MongoDB assigns it on insert.
     *
     * @param customerName  customer identifier
     * @param region        geographic region
     * @param status        order status
     * @param totalAmount   total order value
     * @param items         list of order line items
     */
    public Order(String customerName, String region, String status,
                 BigDecimal totalAmount, List<OrderItem> items) {
        this.customerName = customerName;
        this.region = region;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = items;
        this.createdAt = Instant.now();
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Order{id='" + id + "', customerName='" + customerName
                + "', region='" + region + "', status='" + status
                + "', totalAmount=" + totalAmount + ", items=" + (items != null ? items.size() : 0) + "}";
    }
}
