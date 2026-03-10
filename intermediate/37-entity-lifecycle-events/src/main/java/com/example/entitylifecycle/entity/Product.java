package com.example.entitylifecycle.entity;

import com.example.entitylifecycle.listener.ProductAuditListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * JPA entity representing a product in a catalogue.
 *
 * <p>This entity is the centrepiece of the mini-project: it demonstrates
 * <em>every</em> major JPA lifecycle callback annotation, both inline
 * (methods on this class) and external (via {@link ProductAuditListener}).
 *
 * <h2>Lifecycle callbacks declared on this class</h2>
 * <ul>
 *   <li><b>{@code @PrePersist} – {@link #onPrePersist()}</b>
 *       Runs before the first INSERT. Generates the URL slug from the product
 *       name and stamps the {@code createdAt} / {@code updatedAt} timestamps.
 *       The slug ensures human-readable, SEO-friendly URLs like
 *       {@code /products/wireless-noise-cancelling-headphones}.</li>
 *   <li><b>{@code @PostLoad} – {@link #onPostLoad()}</b>
 *       Runs every time this entity is fetched from the database. Computes the
 *       {@link #discountedPrice} transient field by applying the stored discount
 *       percentage to the base price. Because {@code discountedPrice} is
 *       {@code @Transient}, it is never stored; it is always recalculated on
 *       load.</li>
 * </ul>
 *
 * <h2>Lifecycle callbacks declared in the external listener</h2>
 * <ul>
 *   <li><b>{@code @PostPersist}</b>  – logs the creation event after INSERT.</li>
 *   <li><b>{@code @PreUpdate}</b>    – logs the update event before UPDATE.</li>
 *   <li><b>{@code @PostRemove}</b>   – logs the deletion event after DELETE.</li>
 * </ul>
 *
 * <h2>Why split callbacks between inline and external?</h2>
 * <p>Inline callbacks (methods on the entity) are simpler and keep related code
 * close to the data. External listeners ({@code @EntityListeners}) are useful
 * when the logic is cross-cutting (audit logging) or when you need to reuse the
 * same callbacks across multiple entity types.
 */
@Entity
@Table(name = "products")
@EntityListeners(ProductAuditListener.class) // registers the external listener
public class Product {

    /**
     * Primary key. PostgreSQL generates the ID automatically via IDENTITY/SERIAL
     * on INSERT.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable product name, e.g. "Wireless Noise-Cancelling Headphones".
     * Must be non-null; enforced both by the Bean Validation layer and the DB
     * NOT NULL constraint.
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * URL-friendly version of the product name, e.g.
     * {@code "wireless-noise-cancelling-headphones"}.
     *
     * <p>This field is <em>never set manually</em> by application code. It is
     * always derived from {@link #name} inside the {@link #onPrePersist()}
     * {@code @PrePersist} callback. The {@code updatable = false} constraint
     * ensures the slug is fixed at creation time and is never overwritten by an
     * UPDATE statement, preserving existing URLs.
     */
    @Column(name = "slug", nullable = false, unique = true, updatable = false)
    private String slug;

    /**
     * Full product description. Stored as TEXT in PostgreSQL.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Base price of the product. Stored as NUMERIC(10,2) — two decimal places.
     */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Discount percentage (0–100). Default is 0 (no discount).
     * Used by {@link #onPostLoad()} to calculate the {@link #discountedPrice}.
     */
    @Column(name = "discount_percent", nullable = false)
    private int discountPercent = 0;

    /**
     * Computed transient field: the price after applying {@link #discountPercent}.
     *
     * <p>{@code @Transient} tells JPA to ignore this field completely — it is
     * never read from or written to the database. Instead, it is computed
     * exclusively in the {@link #onPostLoad()} {@code @PostLoad} callback.
     *
     * <p>This pattern is useful for derived fields that:
     * <ul>
     *   <li>Would be expensive or impossible to store (always-current values).</li>
     *   <li>Must be consistent with other fields (computed from them).</li>
     *   <li>Should not be serialised to the DB but should appear in the JSON
     *       response to the client.</li>
     * </ul>
     */
    @Transient
    private BigDecimal discountedPrice;

    /**
     * UTC instant when this product was first persisted.
     *
     * <p>Set once by {@link #onPrePersist()} and never updated ({@code updatable = false}).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * UTC instant of the most recent update to this product.
     *
     * <p>Set by {@link #onPrePersist()} on INSERT and updated by the service
     * layer before each UPDATE. The external {@link ProductAuditListener} also
     * logs it in its {@code @PreUpdate} callback.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // -------------------------------------------------------------------------
    // JPA lifecycle callbacks — declared inline on the entity
    // -------------------------------------------------------------------------

    /**
     * JPA {@code @PrePersist} callback: runs immediately before Hibernate
     * executes the INSERT SQL for this entity.
     *
     * <p>Business logic performed here:
     * <ol>
     *   <li><b>Slug generation</b> — converts {@link #name} to a URL-friendly
     *       slug by lowercasing, replacing all non-alphanumeric characters with
     *       hyphens, and collapsing consecutive hyphens. For example:
     *       {@code "Wireless Headphones (v2)"} → {@code "wireless-headphones-v2"}.</li>
     *   <li><b>Timestamp initialisation</b> — sets both {@code createdAt} and
     *       {@code updatedAt} to the current UTC instant.</li>
     *   <li><b>Discount recalculation</b> — calls {@link #computeDiscountedPrice()}
     *       so {@link #discountedPrice} is available even before the entity is
     *       loaded back from the database.</li>
     * </ol>
     *
     * <p>Constraints:
     * <ul>
     *   <li>Must be {@code void}.</li>
     *   <li>Must not throw checked exceptions.</li>
     *   <li>Must not invoke {@code EntityManager} operations (no cascade, no
     *       flush) — doing so would cause a cascading lifecycle call, which JPA
     *       forbids.</li>
     * </ul>
     */
    @PrePersist
    void onPrePersist() {
        // Generate a URL-friendly slug from the product name.
        // Steps: lowercase → replace non-alphanumeric with hyphens → collapse
        // multiple hyphens → strip leading/trailing hyphens.
        this.slug = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")  // non-alphanumeric → hyphen
                .replaceAll("-{2,}", "-")         // collapse consecutive hyphens
                .replaceAll("^-|-$", "");         // strip leading/trailing hyphens

        // Stamp creation and last-modified timestamps
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;

        // Ensure the transient field is available right after persist, before
        // the entity is re-loaded from the DB by a @PostLoad event.
        computeDiscountedPrice();
    }

    /**
     * JPA {@code @PostLoad} callback: runs after Hibernate loads this entity
     * from the database (whether by a SELECT, a refresh, or an L2C hit).
     *
     * <p>Business logic performed here:
     * <ul>
     *   <li><b>Discount calculation</b> — computes the {@link #discountedPrice}
     *       transient field by applying the {@link #discountPercent} to the base
     *       {@link #price}. Because this field is {@code @Transient} and therefore
     *       never persisted, it must be recalculated every time the entity is
     *       loaded. This guarantees the value is always up-to-date with the
     *       current {@link #discountPercent} stored in the database.</li>
     * </ul>
     *
     * <p>Example:
     * <pre>
     *   price          = 100.00
     *   discountPercent = 20
     *   → discountedPrice = 80.00
     * </pre>
     */
    @PostLoad
    void onPostLoad() {
        // Recompute the transient discountedPrice every time the entity is fetched.
        computeDiscountedPrice();
    }

    /**
     * Helper that calculates the discounted price and assigns it to the
     * {@link #discountedPrice} transient field. Called from both
     * {@link #onPrePersist()} and {@link #onPostLoad()}.
     *
     * <p>Formula: {@code discountedPrice = price × (1 − discountPercent / 100)}
     *
     * <p>The result is rounded to 2 decimal places using {@code HALF_UP} rounding,
     * which is the standard rounding for monetary amounts.
     */
    private void computeDiscountedPrice() {
        if (price == null) {
            this.discountedPrice = null;
            return;
        }
        // Divide discountPercent by 100 to get the discount factor (e.g. 20 → 0.20)
        BigDecimal factor = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(discountPercent).divide(BigDecimal.valueOf(100)));
        this.discountedPrice = price.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Default no-args constructor required by JPA. */
    protected Product() {
    }

    /**
     * Convenience constructor for creating a new product.
     *
     * <p>The {@link #slug}, {@link #createdAt}, {@link #updatedAt}, and
     * {@link #discountedPrice} fields are intentionally omitted because they
     * are all set automatically by the {@link #onPrePersist()} lifecycle callback.
     *
     * @param name            product display name
     * @param description     full product description
     * @param price           base price (before discount)
     * @param discountPercent discount percentage (0–100)
     */
    public Product(String name, String description, BigDecimal price, int discountPercent) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.discountPercent = discountPercent;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** The slug is read-only after creation ({@code updatable = false} column). */
    public String getSlug() {
        return slug;
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

    public int getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(int discountPercent) {
        this.discountPercent = discountPercent;
    }

    /**
     * Returns the computed discounted price.
     *
     * <p>This field is populated by {@link #onPostLoad()} on every DB load and
     * by {@link #onPrePersist()} on the initial INSERT. It is {@code @Transient}
     * and therefore never stored in the database.
     *
     * @return price after applying the discount, rounded to 2 decimal places
     */
    public BigDecimal getDiscountedPrice() {
        return discountedPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Allows the service layer to update the {@code updatedAt} timestamp before
     * calling {@code repository.save()} for an UPDATE operation.
     *
     * @param updatedAt the new last-modified timestamp
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
