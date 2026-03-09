package com.example.redissessionstore.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Represents a single item stored inside a shopping cart session attribute.
 *
 * <p>This class is deliberately simple so the focus stays on the Spring Session
 * mechanics rather than on complex domain modelling.
 *
 * <p>{@link Serializable} is implemented as a safety net for session serialisation.
 * In practice the {@link com.example.redissessionstore.config.SessionConfig} configures
 * JSON serialisation, so Java serialisation is never used at runtime. Implementing
 * {@code Serializable} is still a good habit for any class stored in a session.
 */
public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Unique product identifier. */
    private String productId;

    /** Human-readable product name. */
    private String productName;

    /** Unit price of the product. */
    private BigDecimal price;

    /** Number of units added to the cart. */
    private int quantity;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /** No-arg constructor required by Jackson for JSON deserialisation. */
    public CartItem() {}

    /** Convenience all-args constructor used in tests and controllers. */
    public CartItem(String productId, String productName, BigDecimal price, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    // ── Business helper ───────────────────────────────────────────────────────────

    /**
     * Calculate the line total for this item (price × quantity).
     *
     * @return price multiplied by quantity; never null
     */
    public BigDecimal lineTotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    @Override
    public String toString() {
        return "CartItem{productId='" + productId + "', productName='" + productName
                + "', price=" + price + ", quantity=" + quantity + '}';
    }
}
