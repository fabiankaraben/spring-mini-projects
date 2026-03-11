package com.example.pdfgeneration.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO representing a single line item within an invoice creation request.
 *
 * <p>Bean Validation annotations ensure the client provides valid data before
 * the service layer is invoked:
 * <ul>
 *   <li>{@code @NotBlank} – description must be a non-empty, non-whitespace string.</li>
 *   <li>{@code @Min(1)} – quantity must be at least 1.</li>
 *   <li>{@code @DecimalMin("0.01")} – unit price must be positive.</li>
 * </ul>
 */
public class InvoiceLineItemRequest {

    /** Human-readable description of the product or service. */
    @NotBlank(message = "Line item description must not be blank")
    private String description;

    /** Number of units. Must be at least 1. */
    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;

    /** Price per unit. Must be greater than zero. */
    @NotNull(message = "Unit price must not be null")
    @DecimalMin(value = "0.01", message = "Unit price must be at least 0.01")
    private BigDecimal unitPrice;

    // ── Default constructor (required by Jackson for deserialization) ──────────

    public InvoiceLineItemRequest() {
    }

    public InvoiceLineItemRequest(String description, int quantity, BigDecimal unitPrice) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
