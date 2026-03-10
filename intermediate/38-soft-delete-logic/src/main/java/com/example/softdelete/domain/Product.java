package com.example.softdelete.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a product stored in the {@code products} table.
 *
 * <h2>Soft Delete Mechanism</h2>
 * <p>This entity uses two Hibernate annotations to implement logical (soft) deletion:</p>
 *
 * <h3>{@code @SQLDelete}</h3>
 * <p>Overrides the default {@code DELETE FROM products WHERE id = ?} SQL that Hibernate would
 * normally generate. Instead, it executes an {@code UPDATE} that sets the {@code deleted} column
 * to {@code true} and records a {@code deleted_at} timestamp.  The row is <em>never</em>
 * physically removed from the database.</p>
 *
 * <h3>{@code @SQLRestriction}</h3>
 * <p>Automatically appends a {@code WHERE deleted = false} clause to <em>every</em> Hibernate
 * query that targets this entity (including JOIN fetches).  This means soft-deleted rows are
 * completely transparent to the rest of the application – they behave as if they do not exist
 * for all standard JPA/Spring Data operations.</p>
 *
 * <h2>How to "see" soft-deleted rows</h2>
 * <p>Because {@code @SQLRestriction} hides deleted rows from normal queries, you need a native
 * SQL query (or a JPQL query that bypasses the filter) if you ever need to inspect or restore
 * soft-deleted records.  See {@link com.example.softdelete.repository.ProductRepository} for
 * examples.</p>
 */
@Entity
@Table(name = "products")
/*
 * @SQLDelete – custom SQL executed by Hibernate when EntityManager.remove() is called.
 * Instead of issuing "DELETE FROM products WHERE id=?", Hibernate runs this UPDATE,
 * which sets deleted=true and records the timestamp of the logical deletion.
 */
@SQLDelete(sql = "UPDATE products SET deleted = true, deleted_at = NOW() WHERE id = ?")
/*
 * @SQLRestriction – a global WHERE clause fragment appended to every Hibernate-generated
 * query for this entity.  "deleted = false" ensures that soft-deleted rows are invisible
 * to all standard JPA find/query operations without any extra code in the service layer.
 *
 * Note: @SQLRestriction was introduced in Hibernate 6.3 as the successor to the deprecated
 * @Where annotation.  Spring Boot 3.x bundles Hibernate 6.x, so @SQLRestriction is the
 * correct choice here.
 */
@SQLRestriction("deleted = false")
public class Product {

    /**
     * Auto-generated surrogate primary key. Uses the IDENTITY strategy so the database
     * sequence (or serial column) manages the value.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable product name.  Must be 2–100 characters and non-blank.
     */
    @Column(nullable = false, length = 100)
    @NotBlank(message = "Product name must not be blank")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
    private String name;

    /**
     * Optional description providing additional details about the product.
     */
    @Column(length = 500)
    private String description;

    /**
     * Price of the product.  Must be a positive value.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    /**
     * Category the product belongs to (e.g. "Electronics", "Clothing").
     * Used to demonstrate filtering alongside soft-delete transparency.
     */
    @Column(length = 50)
    private String category;

    /**
     * Soft-delete flag.
     *
     * <p>When {@code false} (the default), the product is active and visible.
     * When {@code true}, the product has been logically deleted and is hidden
     * from all normal queries (see {@code @SQLRestriction} above).</p>
     *
     * <p>This flag is set to {@code true} by the custom SQL in {@code @SQLDelete}.
     * Application code must never set it manually – always call the JPA
     * {@code EntityManager.remove()} path (i.e. the repository's {@code delete*}
     * methods) so the custom SQL is triggered.</p>
     */
    @Column(nullable = false)
    private boolean deleted = false;

    /**
     * Timestamp of when the product was logically deleted, or {@code null} if active.
     * Populated automatically by the {@code @SQLDelete} SQL via {@code NOW()}.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Timestamp of when the product was first persisted (creation audit).
     * Set once on insert and never changed.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last update to any non-deletion field.
     * Updated on every merge/update operation.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── JPA lifecycle callbacks ────────────────────────────────────────────────

    /**
     * Sets {@code createdAt} and {@code updatedAt} just before the first INSERT.
     * Using a lifecycle callback is simpler than {@code @CreationTimestamp} /
     * {@code @UpdateTimestamp} for educational clarity.
     */
    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Updates {@code updatedAt} just before every subsequent UPDATE.
     */
    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA; do not call directly from application code. */
    protected Product() {}

    /**
     * Convenience constructor for creating a new active product.
     *
     * @param name        product name (2–100 chars)
     * @param description optional description
     * @param price       price (must be &gt; 0)
     * @param category    optional category label
     */
    public Product(String name, String description, BigDecimal price, String category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public Long getId() {
        return id;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Product{id=" + id
                + ", name='" + name + "'"
                + ", price=" + price
                + ", category='" + category + "'"
                + ", deleted=" + deleted + "}";
    }
}
