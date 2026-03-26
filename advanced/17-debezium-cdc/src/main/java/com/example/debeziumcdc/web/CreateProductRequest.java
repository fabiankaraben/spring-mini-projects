package com.example.debeziumcdc.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Request body DTO for creating a new product.
 *
 * <p>Validated by Spring MVC when the controller method parameter is annotated
 * with {@code @Valid}. Validation failures result in a 400 Bad Request response.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code name} — required, non-blank.</li>
 *   <li>{@code description} — optional; may be null or empty.</li>
 *   <li>{@code price} — required, must be positive (> 0).</li>
 *   <li>{@code stock} — required, must be >= 0.</li>
 * </ul>
 */
public class CreateProductRequest {

    /** Product display name. Required and must not be blank. */
    @NotBlank(message = "Product name must not be blank")
    private String name;

    /** Optional product description. No validation constraints. */
    private String description;

    /** Unit price. Required and must be strictly positive. */
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    /** Initial stock level. Required and must be >= 0. */
    @NotNull(message = "Stock is required")
    @PositiveOrZero(message = "Stock must be zero or positive")
    private Integer stock;

    /** Default no-arg constructor required by Jackson. */
    public CreateProductRequest() {
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
}
