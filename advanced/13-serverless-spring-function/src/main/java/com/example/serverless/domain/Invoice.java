package com.example.serverless.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Output DTO returned by the {@code generateInvoice} function.
 *
 * <p>A complete invoice that combines the tax calculation and discount application
 * results into a single document. This is the "composed function" output —
 * Spring Cloud Function can chain {@code calculateTax} and {@code applyDiscount}
 * via function composition, but here we produce the combined result in one step
 * for clarity.
 *
 * <p>Example JSON response:
 * <pre>{@code
 * {
 *   "invoiceId":       "INV-ORD-001-1712345678",
 *   "orderId":         "ORD-001",
 *   "customerId":      "CUST-42",
 *   "subtotal":        "199.99",
 *   "taxRate":         "0.0875",
 *   "taxAmount":       "17.50",
 *   "totalBeforeDiscount": "217.49",
 *   "discountCode":    "SAVE10",
 *   "discountPercent": "10.00",
 *   "discountAmount":  "21.75",
 *   "finalTotal":      "195.74",
 *   "issuedAt":        "2024-04-05T12:34:56Z"
 * }
 * }</pre>
 */
public class Invoice {

    /**
     * Unique invoice identifier.
     * Generated as "INV-{orderId}-{epochSecond}" to ensure uniqueness
     * while remaining human-readable.
     */
    private String invoiceId;

    /** The order for which this invoice was generated. */
    private String orderId;

    /** The customer who placed the order. */
    private String customerId;

    /** Original pre-tax, pre-discount subtotal. */
    private BigDecimal subtotal;

    /** Tax rate applied (decimal fraction, e.g., 0.0875 = 8.75%). */
    private BigDecimal taxRate;

    /** Computed tax amount (subtotal × taxRate). */
    private BigDecimal taxAmount;

    /** Subtotal plus tax, before any discount. */
    private BigDecimal totalBeforeDiscount;

    /**
     * The discount code applied.
     * May be null or empty if no discount was requested.
     */
    private String discountCode;

    /**
     * Discount percentage applied (e.g., 10.00 = 10%).
     * Zero if no discount was applied.
     */
    private BigDecimal discountPercent;

    /**
     * Monetary discount amount deducted from the pre-discount total.
     * Zero if no discount was applied.
     */
    private BigDecimal discountAmount;

    /** The final amount the customer owes: totalBeforeDiscount - discountAmount. */
    private BigDecimal finalTotal;

    /** Timestamp when this invoice was generated. */
    private Instant issuedAt;

    // Default constructor required for Jackson deserialization.
    public Invoice() {
    }

    /**
     * Full constructor used by the invoice service.
     *
     * @param invoiceId            unique invoice identifier
     * @param orderId              order reference
     * @param customerId           customer reference
     * @param subtotal             pre-tax subtotal
     * @param taxRate              rate applied
     * @param taxAmount            tax amount
     * @param totalBeforeDiscount  subtotal + tax
     * @param discountCode         promotional code used (may be null)
     * @param discountPercent      discount percentage (0 if none)
     * @param discountAmount       discount amount deducted (0 if none)
     * @param finalTotal           amount due after discount
     * @param issuedAt             invoice generation timestamp
     */
    public Invoice(String invoiceId, String orderId, String customerId,
                   BigDecimal subtotal, BigDecimal taxRate, BigDecimal taxAmount,
                   BigDecimal totalBeforeDiscount, String discountCode,
                   BigDecimal discountPercent, BigDecimal discountAmount,
                   BigDecimal finalTotal, Instant issuedAt) {
        this.invoiceId = invoiceId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.subtotal = subtotal;
        this.taxRate = taxRate;
        this.taxAmount = taxAmount;
        this.totalBeforeDiscount = totalBeforeDiscount;
        this.discountCode = discountCode;
        this.discountPercent = discountPercent;
        this.discountAmount = discountAmount;
        this.finalTotal = finalTotal;
        this.issuedAt = issuedAt;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
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

    public BigDecimal getTotalBeforeDiscount() {
        return totalBeforeDiscount;
    }

    public void setTotalBeforeDiscount(BigDecimal totalBeforeDiscount) {
        this.totalBeforeDiscount = totalBeforeDiscount;
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

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    @Override
    public String toString() {
        return "Invoice{invoiceId='" + invoiceId + "', orderId='" + orderId
                + "', finalTotal=" + finalTotal + ", issuedAt=" + issuedAt + '}';
    }
}
