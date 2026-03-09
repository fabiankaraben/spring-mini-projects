package com.example.redisdatacache.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain model representing a product in the catalogue.
 *
 * <p>This class is intentionally kept simple (no JPA / database persistence) so the
 * focus of the project remains on the caching layer rather than on storage details.
 * Products are held in an in-memory {@code Map} inside {@link com.example.redisdatacache.repository.ProductRepository}.
 *
 * <p>Implements {@link Serializable} because Spring's Redis cache serialises cached
 * objects with Java serialisation by default. Any object stored in Redis must be
 * serialisable; failing to implement this interface causes a runtime exception.
 *
 * <p>Note: in production you would typically prefer JSON serialisation (configured via
 * {@link com.example.redisdatacache.config.CacheConfig}) to keep cached data human-readable
 * in Redis, but Java serialisation works without additional configuration and is used
 * here as the default approach shown in the configuration class comments.
 */
public class Product implements Serializable {

    // serialVersionUID is required for stable Java serialisation across JVM restarts.
    private static final long serialVersionUID = 1L;

    /** Unique identifier of the product (acts as the cache key). */
    private Long id;

    /** Human-readable product name. */
    private String name;

    /** Short description of the product. */
    private String description;

    /** Retail price; uses BigDecimal to avoid floating-point rounding issues. */
    private BigDecimal price;

    /** Category the product belongs to (e.g. "electronics", "books"). */
    private String category;

    /** Timestamp when this product record was last modified (used in demo output). */
    private Instant lastModified;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /** No-arg constructor required by Java serialisation and Jackson. */
    public Product() {}

    /** Convenience all-args constructor used in tests and the repository seed data. */
    public Product(Long id, String name, String description, BigDecimal price, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.lastModified = Instant.now();
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Instant getLastModified() { return lastModified; }
    public void setLastModified(Instant lastModified) { this.lastModified = lastModified; }

    @Override
    public String toString() {
        return "Product{id=" + id + ", name='" + name + "', price=" + price + ", category='" + category + "'}";
    }
}
