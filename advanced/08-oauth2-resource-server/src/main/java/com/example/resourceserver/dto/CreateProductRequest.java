package com.example.resourceserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request DTO (Data Transfer Object) for creating a new product.
 *
 * <p>This class represents the JSON body of a {@code POST /api/products} request.
 * It is separate from the {@link com.example.resourceserver.domain.Product} domain
 * class intentionally:
 * <ul>
 *   <li>DTOs define the API contract (what clients send).</li>
 *   <li>Domain objects represent business entities (what the service works with).</li>
 *   <li>Keeping them separate prevents leaking internal domain details to the API.</li>
 * </ul>
 *
 * <p>Bean Validation annotations ({@code @NotBlank}, {@code @Positive}, etc.) are
 * applied here. Spring Boot calls the Hibernate Validator automatically when the
 * controller method parameter is annotated with {@code @Valid} or {@code @Validated}.
 *
 * <p><b>Example JSON request body:</b>
 * <pre>{@code
 * {
 *   "name": "Wireless Headphones",
 *   "description": "Noise-cancelling over-ear headphones",
 *   "price": 149.99,
 *   "category": "ELECTRONICS",
 *   "stock": 50
 * }
 * }</pre>
 */
public class CreateProductRequest {

    /**
     * Product name — required, non-blank, maximum 200 characters.
     * The product name is what users see in listings and search results.
     */
    @NotBlank(message = "Product name must not be blank")
    @Size(max = 200, message = "Product name must be at most 200 characters")
    private String name;

    /**
     * Product description — optional but must not exceed 2000 characters if provided.
     * Detailed marketing copy or technical specifications.
     */
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    /**
     * Unit price — required and must be a positive value.
     * Uses BigDecimal to avoid floating-point precision issues in monetary values.
     */
    @NotNull(message = "Price must not be null")
    @Positive(message = "Price must be a positive value")
    private BigDecimal price;

    /**
     * Product category — required, non-blank, maximum 100 characters.
     * Used for grouping and filtering (e.g., "ELECTRONICS", "BOOKS").
     */
    @NotBlank(message = "Category must not be blank")
    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;

    /**
     * Initial stock count — required and must be zero or positive.
     * A new product can start with zero stock (pre-announced).
     */
    @NotNull(message = "Stock must not be null")
    @PositiveOrZero(message = "Stock must be zero or a positive value")
    private Integer stock;

    // -------------------------------------------------------------------------
    // Getters and setters (required for Jackson deserialization)
    // -------------------------------------------------------------------------

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}
