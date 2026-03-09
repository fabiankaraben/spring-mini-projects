package com.example.redissessionstore.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO (Data Transfer Object) used to receive cart item data from HTTP request bodies.
 *
 * <p>Keeping the API contract (this DTO) separate from the domain model
 * ({@link com.example.redissessionstore.domain.CartItem}) is a best practice: it prevents
 * API consumers from accidentally setting computed fields and keeps validation
 * annotations off the domain class.
 *
 * <p>Bean Validation annotations are processed by {@code @Valid} in the controller
 * before the request reaches the service layer.
 */
public class CartItemRequest {

    /** Product identifier; must not be blank. */
    @NotBlank(message = "Product ID must not be blank")
    private String productId;

    /** Human-readable product name; must not be blank. */
    @NotBlank(message = "Product name must not be blank")
    private String productName;

    /** Unit price; must be a positive number greater than zero. */
    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    /** Quantity to add; must be at least 1. */
    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /** No-arg constructor required by Jackson for JSON deserialisation. */
    public CartItemRequest() {}

    /** Convenience all-args constructor used in tests. */
    public CartItemRequest(String productId, String productName, BigDecimal price, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
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
}
