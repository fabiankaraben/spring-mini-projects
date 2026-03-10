package com.example.reactiver2dbc.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Data Transfer Object (DTO) for creating and updating products.
 *
 * <p>A DTO separates the API contract (what the client sends) from the domain
 * model (how data is stored in the database). Benefits:
 * <ul>
 *   <li>Validation annotations live here, not on the entity, keeping the entity clean.</li>
 *   <li>API fields can evolve independently from the storage schema.</li>
 *   <li>Sensitive or auto-generated fields ({@code id}, {@code createdAt},
 *       {@code updatedAt}) are never accidentally overwritten by client input.</li>
 *   <li>The client cannot set internal state (e.g., they cannot forge timestamps).</li>
 * </ul>
 *
 * <p>Bean Validation annotations (Jakarta Validation API) are evaluated by Spring WebFlux
 * when the controller method parameter is annotated with {@code @Valid}. If any constraint
 * fails, Spring returns HTTP 400 Bad Request automatically before the method is invoked.
 */
public class ProductRequest {

    /**
     * Product display name — must be non-blank and at most 150 characters.
     * {@code @NotBlank} rejects {@code null}, empty string, and whitespace-only values.
     */
    @NotBlank(message = "Name must not be blank")
    @Size(max = 150, message = "Name must be 150 characters or fewer")
    private String name;

    /**
     * Product description — must be non-blank.
     */
    @NotBlank(message = "Description must not be blank")
    private String description;

    /**
     * Product price — must be provided and must be at least 0.01 (no free or negative prices).
     * {@code @DecimalMin(inclusive = false)} with value "0.00" is equivalent to "> 0.00".
     */
    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
    private BigDecimal price;

    /**
     * Category tag — must be non-blank (e.g., "electronics", "books", "clothing").
     */
    @NotBlank(message = "Category must not be blank")
    private String category;

    /**
     * Available stock quantity — must be zero or greater (can't have negative stock).
     * {@code @Min(0)} rejects any value below zero.
     */
    @Min(value = 0, message = "Stock quantity must be zero or greater")
    private int stockQuantity;

    /**
     * Whether the product is active/visible on the storefront.
     * Defaults to {@code false} (inactive) if omitted in JSON.
     */
    private boolean active;

    /**
     * No-arg constructor required by Jackson for JSON deserialization.
     */
    public ProductRequest() {}

    /**
     * All-args constructor for convenient test setup.
     *
     * @param name          product display name
     * @param description   product description
     * @param price         product price
     * @param category      category tag
     * @param stockQuantity available units in stock
     * @param active        whether the product is publicly visible
     */
    public ProductRequest(String name, String description, BigDecimal price,
                          String category, int stockQuantity, boolean active) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.stockQuantity = stockQuantity;
        this.active = active;
    }

    // ── Getters and setters ────────────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
