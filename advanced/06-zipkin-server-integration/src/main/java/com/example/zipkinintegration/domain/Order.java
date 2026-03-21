package com.example.zipkinintegration.domain;

/**
 * Represents an order in the system.
 *
 * <p>This is a simple domain object (not a JPA entity) used to demonstrate
 * how data flows through the traced service layers. Each order has an ID,
 * a product reference, and a quantity.
 *
 * <p>In a real microservice, this might be persisted to a database and
 * represent a purchase order in an e-commerce platform.
 */
public class Order {

    /** Unique identifier for the order. */
    private Long id;

    /** The name or SKU of the product being ordered. */
    private String product;

    /** How many units of the product are being ordered. */
    private int quantity;

    /** Current lifecycle status of the order. */
    private OrderStatus status;

    /**
     * Constructs an Order with all fields set.
     *
     * @param id       unique order identifier
     * @param product  product name or SKU
     * @param quantity number of units requested
     * @param status   current order status
     */
    public Order(Long id, String product, int quantity, OrderStatus status) {
        this.id = id;
        this.product = product;
        this.quantity = quantity;
        this.status = status;
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
}
