package com.example.serverless.domain;

import java.math.BigDecimal;

/**
 * Output DTO returned by the {@code applyDiscount} function.
 *
 * <p>Contains the discount percentage applied, the discount amount deducted,
 * and the final total after the discount.
 *
 * <p>Example JSON response:
 * <pre>{@code
 * {
 *   "orderId":         "ORD-001",
 *   "originalTotal":   "199.99",
 *   "discountCode":    "SAVE10",
 *   "discountPercent": "10.00",
 *   "discountAmount":  "20.00",
 *   "finalTotal":      "179.99"
 * }
 * }</pre>
 */
public class DiscountResult {

    /** The order identifier echoed back from the request. */
    private String orderId;

    /** The total amount before the discount was applied. */
    private BigDecimal originalTotal;

    /** The discount code that was evaluated. */
    private String discountCode;

    /**
     * The discount percentage applied (e.g., 10.00 for 10%).
     * Zero means the discount code was not recognized or resulted in no discount.
     */
    private BigDecimal discountPercent;

    /**
     * The monetary amount deducted from the original total.
     * Computed as: originalTotal × (discountPercent / 100), rounded to 2 decimal places.
     */
    private BigDecimal discountAmount;

    /** Final total after the discount: originalTotal - discountAmount. */
    private BigDecimal finalTotal;

    // Default constructor required for Jackson deserialization.
    public DiscountResult() {
    }

    /**
     * Full constructor used by the discount service.
     *
     * @param orderId         original order ID
     * @param originalTotal   pre-discount total
     * @param discountCode    code that was applied
     * @param discountPercent percentage discount (0-100)
     * @param discountAmount  monetary discount deducted
     * @param finalTotal      post-discount total
     */
    public DiscountResult(String orderId, BigDecimal originalTotal, String discountCode,
                          BigDecimal discountPercent, BigDecimal discountAmount, BigDecimal finalTotal) {
        this.orderId = orderId;
        this.originalTotal = originalTotal;
        this.discountCode = discountCode;
        this.discountPercent = discountPercent;
        this.discountAmount = discountAmount;
        this.finalTotal = finalTotal;
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

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(BigDecimal discountPercent) {
        this.discountPercent = discountPercent;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getFinalTotal() {
        return finalTotal;
    }

    public void setFinalTotal(BigDecimal finalTotal) {
        this.finalTotal = finalTotal;
    }

    @Override
    public String toString() {
        return "DiscountResult{orderId='" + orderId + "', originalTotal=" + originalTotal
                + ", discountCode='" + discountCode + "', discountPercent=" + discountPercent
                + ", discountAmount=" + discountAmount + ", finalTotal=" + finalTotal + '}';
    }
}
