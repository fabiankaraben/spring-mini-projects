package com.example.liquibasemigrations.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * JPA entity representing the 'products' table.
 *
 * <p>
 * Unlike a purely Hibernate-managed schema (where ddl-auto=create would
 * generate
 * the table), this entity maps to a table that Liquibase creates and manages
 * through
 * its XML changelogs. Hibernate is set to {@code validate} mode so it only
 * checks
 * that the entity fields match the database columns without touching the
 * schema.
 * </p>
 *
 * <p>
 * Liquibase changelog history:
 * </p>
 * <ul>
 * <li>Changeset 1 – creates the {@code products} table with id, name,
 * price.</li>
 * <li>Changeset 2 – inserts sample seed data.</li>
 * <li>Changeset 3 – adds the {@code category} column with a NOT NULL
 * constraint.</li>
 * </ul>
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Product name; must not be null and is limited to 150 characters. */
    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Price of the product; stored as a decimal with 10 total digits and 2 after
     * the decimal point.
     * BigDecimal maps to DECIMAL in Hibernate, matching the DECIMAL(10,2) column
     * defined in
     * the Liquibase changelog. Using Double causes a schema-validation mismatch
     * because
     * Hibernate maps Double to FLOAT, not DECIMAL.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Category of the product.
     * This column did NOT exist in the original table definition.
     * It was added later via a Liquibase changeset (changeset 3), demonstrating
     * how Liquibase handles incremental schema evolution.
     */
    @Column(nullable = false, length = 80)
    private String category;

    // ---------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------

    /** Default no-arg constructor required by JPA. */
    public Product() {
    }

    /** Convenience constructor used in tests and controllers. */
    public Product(String name, BigDecimal price, String category) {
        this.name = name;
        this.price = price;
        this.category = category;
    }

    // ---------------------------------------------------------
    // Getters and Setters — standard JavaBean style
    // ---------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
