package com.example.mongodbcrudapi.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain model representing a product stored in MongoDB.
 *
 * <p>Key MongoDB-specific annotations used here:
 * <ul>
 *   <li>{@link Document} – marks this class as a MongoDB document (equivalent to a
 *       JPA {@code @Entity}). The {@code collection} attribute sets the collection
 *       name in MongoDB. Without it, Spring Data derives the name from the class name
 *       (lowercase), but being explicit avoids surprises.</li>
 *   <li>{@link Id} – maps this field to MongoDB's {@code _id} field. Spring Data
 *       uses a {@link String} ID to store MongoDB's ObjectId as a hex string, which
 *       is human-readable and serialises cleanly to JSON.</li>
 *   <li>{@link Field} – optionally overrides the MongoDB field name stored in the
 *       document. Used here to show the feature; in practice you would use it when
 *       the Java field name differs from the desired MongoDB field name.</li>
 *   <li>{@link Indexed} – creates a MongoDB index on the field, making queries
 *       against {@code name} faster when the collection grows large.</li>
 * </ul>
 *
 * <p>A no-arg constructor is required by Spring Data MongoDB to instantiate documents
 * when reading from the database (it uses reflection).
 */
@Document(collection = "products")
public class Product {

    /**
     * MongoDB document identifier.
     *
     * <p>Declared as {@link String} so Spring Data automatically maps MongoDB's
     * ObjectId BSON type to a hex string (e.g. {@code "507f1f77bcf86cd799439011"}).
     * This keeps the JSON API clean — no complex ObjectId wrapper objects are needed.
     * MongoDB generates this value on insert if left null.
     */
    @Id
    private String id;

    /**
     * Human-readable product name.
     *
     * <p>{@code @Indexed} creates a MongoDB index on this field, useful for
     * name-based lookups. {@code unique = false} (default) allows duplicate names.
     */
    @Indexed
    @Field("name")
    private String name;

    /** Short description of the product's purpose and features. */
    @Field("description")
    private String description;

    /**
     * Retail price of the product.
     *
     * <p>{@link BigDecimal} avoids the floating-point precision issues that arise
     * with {@code double}. MongoDB stores it as a Decimal128 BSON type when using
     * the Spring Data MongoDB converter, ensuring exact decimal arithmetic.
     */
    @Field("price")
    private BigDecimal price;

    /**
     * Category the product belongs to (e.g. "electronics", "books", "clothing").
     *
     * <p>Indexed for efficient category-based filtering queries.
     */
    @Indexed
    @Field("category")
    private String category;

    /**
     * Number of units available in stock.
     *
     * <p>Demonstrates storing a numeric field in MongoDB. In a real inventory
     * system this would be managed by atomic increment/decrement operations to
     * avoid race conditions.
     */
    @Field("stock_quantity")
    private Integer stockQuantity;

    /**
     * Timestamp of when this product document was first created.
     *
     * <p>Stored as an ISO-8601 string in MongoDB (via Jackson serialisation).
     * In production you would use {@code @CreatedDate} from Spring Data's auditing
     * module to populate this automatically.
     */
    @Field("created_at")
    private Instant createdAt;

    /**
     * Timestamp of the last modification to this product document.
     *
     * <p>Updated manually in the service layer on every PUT request. In production
     * you would use {@code @LastModifiedDate} from Spring Data's auditing module.
     */
    @Field("updated_at")
    private Instant updatedAt;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /** No-arg constructor required by Spring Data MongoDB for document deserialisation. */
    public Product() {}

    /**
     * Convenience constructor used when creating new products.
     * The {@code id} is intentionally omitted – MongoDB assigns it on insert.
     *
     * @param name          product name
     * @param description   short description
     * @param price         retail price
     * @param category      product category
     * @param stockQuantity available stock
     */
    public Product(String name, String description, BigDecimal price,
                   String category, Integer stockQuantity) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.stockQuantity = stockQuantity;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "Product{id='" + id + "', name='" + name + "', price=" + price
                + ", category='" + category + "', stockQuantity=" + stockQuantity + "}";
    }
}
