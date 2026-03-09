package com.example.mongodbcustomqueries.dto;

import java.math.BigDecimal;

/**
 * Aggregation result DTO for the "revenue by region" query.
 *
 * <p>This class is used to receive the output of a MongoDB aggregation pipeline
 * that groups orders by region and computes statistics. MongoTemplate maps the
 * aggregation output document into this class using reflection.
 *
 * <p>The field names in this class correspond to the alias names assigned in
 * the {@code $group} aggregation stage via Spring Data's
 * {@link org.springframework.data.mongodb.core.aggregation.Aggregation} API.
 *
 * <p>Example aggregation pipeline this DTO captures:
 * <pre>
 * [
 *   { $group: {
 *       _id: "$region",
 *       totalRevenue: { $sum: "$total_amount" },
 *       orderCount:   { $sum: 1 },
 *       avgOrderValue: { $avg: "$total_amount" }
 *   }}
 * ]
 * </pre>
 */
public class RevenueByRegionResult {

    /**
     * The region name (the value of the {@code $group._id} expression).
     * Spring Data MongoDB maps the {@code _id} field of the aggregation output
     * to the field annotated as the group identifier.
     */
    private String region;

    /**
     * Sum of {@code total_amount} across all orders in this region.
     * Corresponds to {@code $sum: "$total_amount"} in the {@code $group} stage.
     */
    private BigDecimal totalRevenue;

    /**
     * Number of orders in this region.
     * Corresponds to {@code $sum: 1} (count all documents) in the {@code $group} stage.
     */
    private Long orderCount;

    /**
     * Average order value in this region.
     * Corresponds to {@code $avg: "$total_amount"} in the {@code $group} stage.
     */
    private BigDecimal avgOrderValue;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /** No-arg constructor required by Spring Data MongoDB for aggregation result mapping. */
    public RevenueByRegionResult() {}

    /**
     * Convenience constructor used in unit tests.
     *
     * @param region        the region name
     * @param totalRevenue  total revenue for this region
     * @param orderCount    number of orders in this region
     * @param avgOrderValue average order value in this region
     */
    public RevenueByRegionResult(String region, BigDecimal totalRevenue,
                                  Long orderCount, BigDecimal avgOrderValue) {
        this.region = region;
        this.totalRevenue = totalRevenue;
        this.orderCount = orderCount;
        this.avgOrderValue = avgOrderValue;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public Long getOrderCount() { return orderCount; }
    public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }

    public BigDecimal getAvgOrderValue() { return avgOrderValue; }
    public void setAvgOrderValue(BigDecimal avgOrderValue) { this.avgOrderValue = avgOrderValue; }

    @Override
    public String toString() {
        return "RevenueByRegionResult{region='" + region + "', totalRevenue=" + totalRevenue
                + ", orderCount=" + orderCount + ", avgOrderValue=" + avgOrderValue + "}";
    }
}
