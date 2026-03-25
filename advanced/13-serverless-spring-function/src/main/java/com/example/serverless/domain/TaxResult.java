package com.example.serverless.domain;

import java.math.BigDecimal;

/**
 * Output DTO returned by the {@code calculateTax} function.
 *
 * <p>Contains the computed tax amount and the resulting total (subtotal + tax)
 * for the given order. The tax rate used is also included for transparency.
 *
 * <p>Example JSON response:
 * <pre>{@code
 * {
 *   "orderId":  "ORD-001",
 *   "subtotal": "199.99",
 *   "taxRate":  "0.0875",
 *   "taxAmount": "17.50",
 *   "total":    "217.49"
 * }
 * }</pre>
 */
public class TaxResult {

    /** The order identifier echoed back from the request. */
    private String orderId;

    /** The original subtotal before tax. */
    private BigDecimal subtotal;

    /**
     * The tax rate applied (as a decimal fraction).
     * E.g., 0.08 represents 8%, 0.0875 represents 8.75%.
     */
    private BigDecimal taxRate;

    /** Computed tax amount (subtotal × taxRate), rounded to 2 decimal places. */
    private BigDecimal taxAmount;

    /** Final total: subtotal + taxAmount. */
    private BigDecimal total;

    // Default constructor required for Jackson deserialization.
    public TaxResult() {
    }

    /**
     * Full constructor used by the tax calculation service.
     *
     * @param orderId    original order ID
     * @param subtotal   pre-tax amount
     * @param taxRate    rate applied (e.g., 0.08 for 8%)
     * @param taxAmount  computed tax (subtotal × taxRate)
     * @param total      subtotal + taxAmount
     */
    public TaxResult(String orderId, BigDecimal subtotal, BigDecimal taxRate,
                     BigDecimal taxAmount, BigDecimal total) {
        this.orderId = orderId;
        this.subtotal = subtotal;
        this.taxRate = taxRate;
        this.taxAmount = taxAmount;
        this.total = total;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    @Override
    public String toString() {
        return "TaxResult{orderId='" + orderId + "', subtotal=" + subtotal
                + ", taxRate=" + taxRate + ", taxAmount=" + taxAmount + ", total=" + total + '}';
    }
}
