package com.example.serverless.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Input DTO for the {@code generateInvoice} function.
 *
 * <p>Combines the inputs needed for both tax calculation and discount application
 * so a single function call produces a complete invoice.
 *
 * <p>Example JSON payload:
 * <pre>{@code
 * {
 *   "orderId":      "ORD-001",
 *   "customerId":   "CUST-42",
 *   "subtotal":     "199.99",
 *   "country":      "US",
 *   "state":        "CA",
 *   "discountCode": "SAVE10"
 * }
 * }</pre>
 */
public class InvoiceRequest {

    /** Unique identifier for the order. */
    @NotBlank(message = "orderId must not be blank")
    private String orderId;

    /** Customer identifier. */
    @NotBlank(message = "customerId must not be blank")
    private String customerId;

    /**
     * Pre-tax, pre-discount subtotal.
     * Must be zero or positive.
     */
    @NotNull(message = "subtotal is required")
    @DecimalMin(value = "0.00", message = "subtotal must be >= 0")
    private BigDecimal subtotal;

    /**
     * ISO 3166-1 alpha-2 country code for tax rate lookup.
     */
    @NotBlank(message = "country must not be blank")
    private String country;

    /**
     * State/province code for sub-national tax (optional).
     */
    private String state;

    /**
     * Optional promotional discount code.
     * If null or empty, no discount is applied.
     */
    private String discountCode;

    // Default constructor required for Jackson deserialization.
    public InvoiceRequest() {
    }

    /**
     * Convenience constructor for tests and application code.
     *
     * @param orderId      order identifier
     * @param customerId   customer identifier
     * @param subtotal     pre-tax subtotal
     * @param country      ISO country code
     * @param state        state/province code (may be null)
     * @param discountCode promotional code (may be null)
     */
    public InvoiceRequest(String orderId, String customerId, BigDecimal subtotal,
                          String country, String state, String discountCode) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.subtotal = subtotal;
        this.country = country;
        this.state = state;
        this.discountCode = discountCode;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDiscountCode() {
        return discountCode;
    }

    public void setDiscountCode(String discountCode) {
        this.discountCode = discountCode;
    }

    @Override
    public String toString() {
        return "InvoiceRequest{orderId='" + orderId + "', customerId='" + customerId
                + "', subtotal=" + subtotal + ", country='" + country + "', state='" + state
                + "', discountCode='" + discountCode + "'}";
    }
}
