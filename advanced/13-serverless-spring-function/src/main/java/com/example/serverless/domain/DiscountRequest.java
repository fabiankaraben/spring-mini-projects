package com.example.serverless.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Input DTO for the {@code applyDiscount} function.
 *
 * <p>Represents a request to apply a promotional discount code to an order total.
 *
 * <p>Example JSON payload:
 * <pre>{@code
 * {
 *   "orderId":      "ORD-001",
 *   "originalTotal": "199.99",
 *   "discountCode": "SAVE10"
 * }
 * }</pre>
 */
public class DiscountRequest {

    /** Unique identifier for the order. */
    @NotBlank(message = "orderId must not be blank")
    private String orderId;

    /**
     * The total amount before any discount is applied.
     * Must be zero or positive.
     */
    @NotNull(message = "originalTotal is required")
    @DecimalMin(value = "0.00", message = "originalTotal must be >= 0")
    private BigDecimal originalTotal;

    /**
     * Promotional discount code entered by the customer.
     * The {@code DiscountService} maps each code to a discount percentage.
     * Unknown codes result in zero discount (no error).
     */
    @NotBlank(message = "discountCode must not be blank")
    private String discountCode;

    // Default constructor required for Jackson deserialization.
    public DiscountRequest() {
    }

    /**
     * Convenience constructor for tests and application code.
     *
     * @param orderId       order identifier
     * @param originalTotal total before discount
     * @param discountCode  promotional code
     */
    public DiscountRequest(String orderId, BigDecimal originalTotal, String discountCode) {
        this.orderId = orderId;
        this.originalTotal = originalTotal;
        this.discountCode = discountCode;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getOriginalTotal() {
        return originalTotal;
    }

    public void setOriginalTotal(BigDecimal originalTotal) {
        this.originalTotal = originalTotal;
    }

    public String getDiscountCode() {
        return discountCode;
    }

    public void setDiscountCode(String discountCode) {
        this.discountCode = discountCode;
    }

    @Override
    public String toString() {
        return "DiscountRequest{orderId='" + orderId + "', originalTotal=" + originalTotal
                + ", discountCode='" + discountCode + "'}";
    }
}
