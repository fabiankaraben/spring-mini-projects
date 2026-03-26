package com.example.debeziumcdc.cdc;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a Change Data Capture (CDC) event for the {@code products} table.
 *
 * <p>This event is produced by {@link CdcEventDispatcher} whenever Debezium
 * captures an INSERT, UPDATE, or DELETE on the {@code products} table. It is
 * then published to the Kafka topic {@code product-cdc-events}.
 *
 * <p>The event follows Debezium's envelope pattern:
 * <ul>
 *   <li>{@code operation} — what type of change occurred (CREATE/UPDATE/DELETE/READ).</li>
 *   <li>{@code before} — the row state before the change (null for INSERT).</li>
 *   <li>{@code after}  — the row state after the change (null for DELETE).</li>
 *   <li>{@code capturedAt} — when the CDC event was processed by the dispatcher.</li>
 * </ul>
 *
 * <p>Downstream consumers receive this object and can react accordingly:
 * <ul>
 *   <li>CREATE → index the new product in a search engine.</li>
 *   <li>UPDATE → refresh a cached product view.</li>
 *   <li>DELETE → remove the product from derived stores.</li>
 * </ul>
 */
public class ProductCdcEvent {

    /** The type of database operation that triggered this event. */
    private CdcOperation operation;

    /**
     * Product state BEFORE the change.
     * Null for CREATE events (no previous state exists).
     */
    private ProductSnapshot before;

    /**
     * Product state AFTER the change.
     * Null for DELETE events (the row no longer exists).
     */
    private ProductSnapshot after;

    /**
     * Wall-clock timestamp when this CDC event was processed by the dispatcher.
     * This is NOT the database transaction timestamp — use it for observability
     * and lag monitoring, not for business logic ordering.
     */
    private Instant capturedAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Default no-arg constructor required by Jackson for deserialization. */
    public ProductCdcEvent() {
    }

    /**
     * Creates a new {@link ProductCdcEvent}.
     *
     * @param operation  the type of DML operation
     * @param before     product state before the change (may be null)
     * @param after      product state after the change (may be null)
     * @param capturedAt when this event was processed
     */
    public ProductCdcEvent(CdcOperation operation, ProductSnapshot before,
                           ProductSnapshot after, Instant capturedAt) {
        this.operation = operation;
        this.before = before;
        this.after = after;
        this.capturedAt = capturedAt;
    }

    // -------------------------------------------------------------------------
    // Getters and setters (required by Jackson)
    // -------------------------------------------------------------------------

    public CdcOperation getOperation() {
        return operation;
    }

    public void setOperation(CdcOperation operation) {
        this.operation = operation;
    }

    public ProductSnapshot getBefore() {
        return before;
    }

    public void setBefore(ProductSnapshot before) {
        this.before = before;
    }

    public ProductSnapshot getAfter() {
        return after;
    }

    public void setAfter(ProductSnapshot after) {
        this.after = after;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }

    @Override
    public String toString() {
        return "ProductCdcEvent{operation=" + operation
                + ", before=" + before
                + ", after=" + after
                + ", capturedAt=" + capturedAt + "}";
    }

    // =========================================================================
    // Inner class: ProductSnapshot
    // =========================================================================

    /**
     * An immutable snapshot of a product row's state at a given point in time.
     *
     * <p>Used as both the {@code before} and {@code after} payloads in a
     * {@link ProductCdcEvent}. All field names match the PostgreSQL column
     * names so the mapping from Debezium's struct is straightforward.
     */
    public static class ProductSnapshot {

        /** Database primary key. */
        private Long id;

        /** Product display name. */
        private String name;

        /** Optional product description. */
        private String description;

        /** Unit price. */
        private BigDecimal price;

        /** Number of units in stock. */
        private int stock;

        /** Default no-arg constructor required by Jackson. */
        public ProductSnapshot() {
        }

        /**
         * Full constructor for building a snapshot from parsed field values.
         *
         * @param id          product primary key
         * @param name        product name
         * @param description optional description
         * @param price       unit price
         * @param stock       stock level
         */
        public ProductSnapshot(Long id, String name, String description,
                               BigDecimal price, int stock) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.price = price;
            this.stock = stock;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }

        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }

        @Override
        public String toString() {
            return "ProductSnapshot{id=" + id + ", name='" + name
                    + "', price=" + price + ", stock=" + stock + "}";
        }
    }
}
