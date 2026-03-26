package com.example.debeziumcdc.web;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Request body DTO for updating an existing product.
 *
 * <p>All fields are optional — only non-null fields are applied to the product.
 * This implements a partial update (PATCH semantics) via a PUT endpoint.
 *
 * <p>Validation annotations only apply when the field is non-null (i.e., provided).
 * A null price or stock means "do not change this field".
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code name} — optional; if provided, must not be blank (checked in service).</li>
 *   <li>{@code description} — optional; may be any string including empty.</li>
 *   <li>{@code price} — optional; if provided, must be positive (> 0).</li>
 *   <li>{@code stock} — optional; if provided, must be >= 0.</li>
 * </ul>
 */
public class UpdateProductRequest {

    /** New product name. Null means "no change". */
    private String name;

    /** New description. Null means "no change". */
    private String description;

    /** New price. Null means "no change". Must be positive if provided. */
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    /** New stock level. Null means "no change". Must be >= 0 if provided. */
    @PositiveOrZero(message = "Stock must be zero or positive")
    private Integer stock;

    /** Default no-arg constructor required by Jackson. */
    public UpdateProductRequest() {
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
