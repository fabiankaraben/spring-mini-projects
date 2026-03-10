package com.example.optimisticlocking.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a product stored in the {@code products} table.
 *
 * <h2>Optimistic Locking with {@code @Version}</h2>
 * <p>The {@link #version} field is annotated with {@code @Version}, which enables
 * JPA optimistic locking.  Hibernate maintains the version number automatically:</p>
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li><strong>On INSERT</strong> – Hibernate initialises {@code version} to {@code 0}.</li>
 *   <li><strong>On UPDATE</strong> – Hibernate appends {@code WHERE version = ?} to the
 *       generated SQL, using the version that was loaded into the current persistence
 *       context.  If the row's version in the database has already been incremented by
 *       another transaction, the WHERE clause matches zero rows.  Hibernate detects the
 *       zero-row update and throws
 *       {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.</li>
 *   <li><strong>On success</strong> – Hibernate increments the version number in the
 *       database (e.g. 0 → 1 → 2 …) and synchronises it in the in-memory entity.</li>
 * </ol>
 *
 * <h3>Lost Update scenario (prevented by {@code @Version})</h3>
 * <pre>
 *   Thread A: loads Product(id=1, version=0, stock=10)
 *   Thread B: loads Product(id=1, version=0, stock=10)
 *   Thread A: sets stock=8, saves → UPDATE … SET stock=8, version=1 WHERE version=0  ✅ (1 row updated)
 *   Thread B: sets stock=5, saves → UPDATE … SET stock=5, version=1 WHERE version=0  ❌ (0 rows – version mismatch!)
 *              → ObjectOptimisticLockingFailureException → HTTP 409 Conflict
 * </pre>
 *
 * <h3>Client responsibility</h3>
 * <p>REST clients must include the current {@code version} value in every update request.
 * The service uses this version to set the entity's version field before saving,
 * allowing Hibernate to detect if the client is working with stale data.</p>
 */
@Entity
@Table(name = "products")
public class Product {

    /**
     * Auto-generated surrogate primary key. Uses the IDENTITY strategy so the database
     * sequence (or serial column) manages the value.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic locking version counter.
     *
     * <p>{@code @Version} tells Hibernate to:</p>
     * <ul>
     *   <li>Initialise this field to {@code 0} on first INSERT.</li>
     *   <li>Automatically increment it on every successful UPDATE.</li>
     *   <li>Append {@code AND version = :currentVersion} to every UPDATE SQL,
     *       so that a concurrent modification causes an exception instead of a
     *       silent overwrite.</li>
     * </ul>
     *
     * <p>This field is mapped to a {@code version} column in the database and is
     * included in every response DTO so that clients always have the latest value
     * available for their next update request.</p>
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Human-readable product name. Must be 2–100 characters and non-blank.
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
     * Price of the product. Must be a positive value greater than 0.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    /**
     * Quantity of items available in stock. Must be zero or greater.
     * This field is a common target for concurrent modifications
     * (e.g. two users buying the last item simultaneously), which makes
     * it ideal for demonstrating optimistic locking protection.
     */
    @Column(nullable = false)
    @NotNull(message = "Stock must not be null")
    private Integer stock;

    /**
     * Category the product belongs to (e.g. "Electronics", "Clothing").
     */
    @Column(length = 50)
    private String category;

    /**
     * Timestamp of when the product was first persisted (creation audit).
     * Set once on insert via {@link #prePersist()} and never changed.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last update to this product.
     * Updated on every merge/update operation via {@link #preUpdate()}.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── JPA lifecycle callbacks ────────────────────────────────────────────────

    /**
     * Sets {@code createdAt} and {@code updatedAt} just before the first INSERT.
     * The {@code version} field is initialised to {@code 0} by Hibernate automatically.
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
     * Convenience constructor for creating a new product.
     *
     * @param name        product name (2–100 chars)
     * @param description optional description
     * @param price       price (must be &gt; 0)
     * @param stock       initial stock quantity (must be &ge; 0)
     * @param category    optional category label
     */
    public Product(String name, String description, BigDecimal price, Integer stock, String category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.category = category;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    /**
     * Returns the current optimistic locking version.
     * Clients must include this value in update requests.
     */
    public Long getVersion() {
        return version;
    }

    /**
     * Sets the version field.
     *
     * <p><strong>Warning:</strong> this setter should only be called by the service layer
     * when applying a client-supplied version before a JPA save, so that Hibernate can
     * verify the client is working with up-to-date data.  Never call this from arbitrary
     * application code.</p>
     */
    public void setVersion(Long version) {
        this.version = version;
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

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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
                + ", version=" + version
                + ", name='" + name + "'"
                + ", price=" + price
                + ", stock=" + stock
                + ", category='" + category + "'}";
    }
}
