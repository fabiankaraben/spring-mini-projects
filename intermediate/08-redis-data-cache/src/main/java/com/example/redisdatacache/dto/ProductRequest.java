package com.example.redisdatacache.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO (Data Transfer Object) used to receive product data from HTTP request bodies.
 *
 * <p>Separating the API contract (this DTO) from the domain model ({@link com.example.redisdatacache.domain.Product})
 * is a best practice: it prevents API consumers from accidentally setting internal
 * fields such as {@code lastModified} and keeps validation annotations off the domain.
 *
 * <p>Bean Validation annotations ({@link NotBlank}, {@link NotNull}, {@link DecimalMin})
 * are processed by {@code @Valid} in the controller layer before the request reaches
 * the service.
 */
public class ProductRequest {

    /** Product name; must not be blank. */
    @NotBlank(message = "Name must not be blank")
    private String name;

    /** Short description; must not be blank. */
    @NotBlank(message = "Description must not be blank")
    private String description;

    /**
     * Retail price; must be a positive number.
     * Using {@code inclusive = false} means zero is also rejected.
     */
    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    /** Category label (e.g. "electronics", "books"). Must not be blank. */
    @NotBlank(message = "Category must not be blank")
    private String category;

    // ── Constructors ─────────────────────────────────────────────────────────────

    public ProductRequest() {}

    public ProductRequest(String name, String description, BigDecimal price, String category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
