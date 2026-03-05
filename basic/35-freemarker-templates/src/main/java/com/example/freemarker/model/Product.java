package com.example.freemarker.model;

/**
 * Represents a single item in the product catalog.
 *
 * <p>
 * This is a plain Java record used as a view model (DTO) passed from the
 * service layer to the FreeMarker templates. Records are immutable by design,
 * which is ideal here because the template should only <em>read</em> the data,
 * never mutate it.
 * </p>
 *
 * <p>
 * FreeMarker accesses record components via their accessor methods.
 * For example, {@code product.name()} in Java is accessed as
 * {@code ${product.name}} inside a {@code .ftlh} template — FreeMarker
 * automatically calls the no-arg accessor that matches the property name.
 * </p>
 *
 * @param id       unique identifier
 * @param name     display name of the product
 * @param category category it belongs to (e.g. "Electronics", "Books")
 * @param price    price in USD
 * @param inStock  whether the product is currently available for purchase
 */
public record Product(
        Long id,
        String name,
        String category,
        double price,
        boolean inStock) {
}
