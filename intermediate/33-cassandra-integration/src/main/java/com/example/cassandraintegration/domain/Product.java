package com.example.cassandraintegration.domain;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cassandra entity representing a product in the catalog.
 *
 * <p>In Cassandra, table design is query-driven. This table is designed to
 * support efficient lookups by category (the partition key) and ordered
 * retrieval by product ID within a category (the clustering key).</p>
 *
 * <h2>Wide-Column Model Explained</h2>
 * <p>Cassandra organises data into partitions. All rows sharing the same
 * partition key ({@code category}) are stored together on the same node,
 * making category-based range queries extremely fast without any index scans.</p>
 *
 * <h2>Primary Key</h2>
 * <ul>
 *   <li><b>Partition key</b>: {@code category} — determines which node(s) hold the data.</li>
 *   <li><b>Clustering key</b>: {@code id} — sorts rows within the partition (ascending UUID order).</li>
 * </ul>
 *
 * <p>The composite primary key {@code (category, id)} also ensures uniqueness.</p>
 */
@Table("products")
public class Product {

    /**
     * Partition key — groups products of the same category on the same Cassandra node.
     * Queries filtering by {@code category} are served with a single partition read.
     */
    @PrimaryKeyColumn(name = "category", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String category;

    /**
     * Clustering key — uniquely identifies a product within its category partition.
     * UUIDs are time-based (Type 4 random) here; Cassandra sorts them ascending.
     */
    @PrimaryKeyColumn(name = "id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private UUID id;

    /** Human-readable product name. */
    @Column("name")
    private String name;

    /** Optional description of the product. */
    @Column("description")
    private String description;

    /** Price of the product. BigDecimal maps to Cassandra's {@code decimal} type. */
    @Column("price")
    private BigDecimal price;

    /** Current stock quantity available. */
    @Column("stock")
    private int stock;

    /** Required no-arg constructor for Spring Data Cassandra mapping. */
    public Product() {}

    /**
     * Convenience constructor for creating a product with all fields.
     *
     * @param category    product category (partition key)
     * @param id          unique product UUID (clustering key)
     * @param name        product name
     * @param description optional description
     * @param price       product price
     * @param stock       stock quantity
     */
    public Product(String category, UUID id, String name, String description,
                   BigDecimal price, int stock) {
        this.category = category;
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    @Override
    public String toString() {
        return "Product{category='" + category + "', id=" + id +
                ", name='" + name + "', price=" + price + ", stock=" + stock + '}';
    }
}
