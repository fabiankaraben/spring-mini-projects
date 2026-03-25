package com.example.grpcclient.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity representing a single line item within an order.
 *
 * <p>Each line item corresponds to one SKU (product) and the quantity ordered.
 * An {@link Order} contains one or more line items.
 *
 * <p>Relationship:
 *   Many {@code OrderLineItem}s belong to one {@link Order}.
 *   The foreign key {@code order_id} links them in the database.
 */
@Entity
@Table(name = "order_line_items")
public class OrderLineItem {

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stock Keeping Unit — unique product identifier (e.g., "SKU-LAPTOP-001").
     */
    @Column(nullable = false, length = 100)
    private String sku;

    /**
     * Human-readable product name at the time of ordering.
     * Stored here so the order history remains accurate even if the product name changes.
     */
    @Column(nullable = false)
    private String productName;

    /**
     * Number of units ordered for this line item.
     */
    @Column(nullable = false)
    private Integer quantity;

    /**
     * Unit price in USD at the time of ordering.
     * Snapshotted here so price changes don't retroactively alter order totals.
     */
    @Column(nullable = false)
    private Double unitPrice;

    /**
     * The parent order this line item belongs to.
     * Mapped by the foreign key column "order_id" in the order_line_items table.
     */
    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** JPA requires a no-arg constructor. */
    protected OrderLineItem() {}

    /**
     * Full constructor used by the service layer when building new line items.
     *
     * @param sku         the product SKU
     * @param productName the product name (snapshot)
     * @param quantity    number of units ordered
     * @param unitPrice   unit price in USD (snapshot)
     */
    public OrderLineItem(String sku, String productName, Integer quantity, Double unitPrice) {
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
}
