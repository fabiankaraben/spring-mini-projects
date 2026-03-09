package com.example.mongodbcustomqueries.dto;

import java.math.BigDecimal;

/**
 * Aggregation result DTO for the "customer order summary" query.
 *
 * <p>This class captures the output of a pipeline that groups orders by
 * customer and computes spending statistics. It is useful for building
 * customer ranking dashboards and identifying high-value customers.
 *
 * <p>Example aggregation pipeline this DTO captures:
 * <pre>
 * [
 *   { $match: { status: { $ne: "CANCELLED" } } },
 *   { $group: {
 *       _id: "$customer_name",
 *       totalSpent:   { $sum: "$total_amount" },
 *       orderCount:   { $sum: 1 },
 *       maxOrder:     { $max: "$total_amount" },
 *       minOrder:     { $min: "$total_amount" }
 *   }},
 *   { $sort: { totalSpent: -1 } }
 * ]
 * </pre>
 */
public class CustomerSummaryResult {

    /**
     * The customer name (value of {@code $group._id}, which is {@code $customer_name}).
     */
    private String customerName;

    /**
     * Total amount spent by this customer across all non-cancelled orders.
     * Computed as {@code $sum: "$total_amount"}.
     */
    private BigDecimal totalSpent;

    /**
     * Total number of non-cancelled orders placed by this customer.
     * Computed as {@code $sum: 1}.
     */
    private Long orderCount;

    /**
     * Value of the single most expensive order placed by this customer.
     * Computed as {@code $max: "$total_amount"}.
     */
    private BigDecimal maxOrder;

    /**
     * Value of the single least expensive order placed by this customer.
     * Computed as {@code $min: "$total_amount"}.
     */
    private BigDecimal minOrder;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /** No-arg constructor required by Spring Data MongoDB for aggregation result mapping. */
    public CustomerSummaryResult() {}

    /**
     * Convenience constructor used in unit tests.
     *
     * @param customerName  customer name
     * @param totalSpent    total amount spent
     * @param orderCount    number of orders
     * @param maxOrder      maximum single order value
     * @param minOrder      minimum single order value
     */
    public CustomerSummaryResult(String customerName, BigDecimal totalSpent, Long orderCount,
                                  BigDecimal maxOrder, BigDecimal minOrder) {
        this.customerName = customerName;
        this.totalSpent = totalSpent;
        this.orderCount = orderCount;
        this.maxOrder = maxOrder;
        this.minOrder = minOrder;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public BigDecimal getTotalSpent() { return totalSpent; }
    public void setTotalSpent(BigDecimal totalSpent) { this.totalSpent = totalSpent; }

    public Long getOrderCount() { return orderCount; }
    public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }

    public BigDecimal getMaxOrder() { return maxOrder; }
    public void setMaxOrder(BigDecimal maxOrder) { this.maxOrder = maxOrder; }

    public BigDecimal getMinOrder() { return minOrder; }
    public void setMinOrder(BigDecimal minOrder) { this.minOrder = minOrder; }

    @Override
    public String toString() {
        return "CustomerSummaryResult{customerName='" + customerName + "', totalSpent=" + totalSpent
                + ", orderCount=" + orderCount + ", maxOrder=" + maxOrder + ", minOrder=" + minOrder + "}";
    }
}
