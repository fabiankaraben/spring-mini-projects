package com.example.mongodbcustomqueries.domain;

import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * Embedded document representing a single line item within an {@link Order}.
 *
 * <p>This class is <em>not</em> annotated with {@code @Document} because it is
 * never stored as a top-level MongoDB collection. Instead, it is embedded
 * (nested) inside the {@code Order} document as an element of the {@code items}
 * array. This is the idiomatic MongoDB pattern for child data that:
 * <ul>
 *   <li>Is always accessed together with the parent document.</li>
 *   <li>Has no independent lifecycle (an item only exists within an order).</li>
 *   <li>Does not grow unboundedly (orders have a finite number of items).</li>
 * </ul>
 *
 * <p>Using embedded documents avoids joins (MongoDB has no native join) and
 * allows the {@code $unwind} aggregation stage to "explode" the items array
 * so that each item can be processed individually in the pipeline.
 */
public class OrderItem {

    /**
     * Name of the product in this line item (e.g. "Laptop", "Headphones").
     * Used in per-product aggregations such as "total revenue per product".
     */
    @Field("product_name")
    private String productName;

    /**
     * Product category (e.g. "electronics", "books", "clothing").
     * Enables grouping and filtering at the category level even within
     * the {@code $unwind} stage.
     */
    @Field("category")
    private String category;

    /**
     * Number of units ordered for this line item.
     * Combined with {@code unitPrice} to compute this item's contribution to
     * the order total.
     */
    @Field("quantity")
    private Integer quantity;

    /**
     * Price per unit of the product.
     *
     * <p>Stored as {@link BigDecimal} and persisted as BSON {@code Decimal128}
     * via the custom converter in {@link com.example.mongodbcustomqueries.config.MongoConfig}.
     * This ensures correct arithmetic in aggregation expressions like
     * {@code $multiply: ["$items.unit_price", "$items.quantity"]}.
     */
    @Field("unit_price")
    private BigDecimal unitPrice;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /** No-arg constructor required by Spring Data MongoDB for BSON deserialisation. */
    public OrderItem() {}

    /**
     * Convenience all-args constructor used in tests and data setup.
     *
     * @param productName  name of the product
     * @param category     product category
     * @param quantity     number of units
     * @param unitPrice    price per unit
     */
    public OrderItem(String productName, String category, Integer quantity, BigDecimal unitPrice) {
        this.productName = productName;
        this.category = category;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    @Override
    public String toString() {
        return "OrderItem{productName='" + productName + "', category='" + category
                + "', quantity=" + quantity + ", unitPrice=" + unitPrice + "}";
    }
}
