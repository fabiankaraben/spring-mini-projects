package com.example.zipkinintegration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request body DTO for creating a new order.
 *
 * <p>Bean Validation annotations ensure that invalid payloads are rejected
 * at the controller layer before reaching the service.
 */
public class CreateOrderRequest {

    /**
     * Product name or SKU. Must not be blank.
     * Example: {@code "laptop-pro-15"}
     */
    @NotBlank(message = "Product name must not be blank")
    private String product;

    /**
     * Number of units to order. Must be a positive integer (≥ 1).
     */
    @Positive(message = "Quantity must be a positive number")
    private int quantity;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Default constructor required by Jackson for deserialization. */
    public CreateOrderRequest() {}

    /**
     * Convenience constructor for tests.
     *
     * @param product  product name or SKU
     * @param quantity number of units
     */
    public CreateOrderRequest(String product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

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
}
