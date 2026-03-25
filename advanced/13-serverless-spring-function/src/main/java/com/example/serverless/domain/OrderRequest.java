package com.example.serverless.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Input DTO for the {@code calculateTax} function.
 *
 * <p>Represents a customer order for which a tax calculation is requested.
 * All fields are validated via Bean Validation annotations so that invalid
 * payloads are rejected with a 400 Bad Request before reaching the function.
 *
 * <p>Example JSON payload:
 * <pre>{@code
 * {
 *   "orderId":    "ORD-001",
 *   "customerId": "CUST-42",
 *   "subtotal":   "199.99",
 *   "country":    "US",
 *   "state":      "CA"
 * }
 * }</pre>
 */
public class OrderRequest {

    /** Unique identifier for the order (e.g., "ORD-001"). */
    @NotBlank(message = "orderId must not be blank")
    private String orderId;

    /** Customer identifier. */
    @NotBlank(message = "customerId must not be blank")
    private String customerId;

    /**
     * Subtotal amount before tax and discounts.
     * Must be a non-negative value (zero is allowed for free orders).
     */
    @NotNull(message = "subtotal is required")
    @DecimalMin(value = "0.00", message = "subtotal must be >= 0")
    private BigDecimal subtotal;

    /**
     * ISO 3166-1 alpha-2 country code (e.g., "US", "DE", "GB").
     * Used to determine the applicable tax rate.
     */
    @NotBlank(message = "country must not be blank")
    private String country;

    /**
     * State or province code (e.g., "CA", "NY").
     * Optional — used for sub-national tax rules (e.g., US state taxes).
     * May be null or empty for countries without state-level taxes.
     */
    private String state;

    // Default constructor required for Jackson deserialization.
    public OrderRequest() {
    }

    /**
     * Convenience constructor for use in tests and application code.
     *
     * @param orderId    order identifier
     * @param customerId customer identifier
     * @param subtotal   pre-tax subtotal
     * @param country    ISO country code
     * @param state      state/province code (may be null)
     */
    public OrderRequest(String orderId, String customerId, BigDecimal subtotal,
                        String country, String state) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.subtotal = subtotal;
        this.country = country;
        this.state = state;
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

    @Override
    public String toString() {
        return "OrderRequest{orderId='" + orderId + "', customerId='" + customerId
                + "', subtotal=" + subtotal + ", country='" + country + "', state='" + state + "'}";
    }
}
