package com.example.thymeleafbasicui.model;

/**
 * Represents a single item in the product catalog.
 *
 * <p>
 * This is a plain Java record used exclusively as a data-transfer object (DTO)
 * between the service layer and the Thymeleaf templates. Records are immutable
 * by design, which is ideal for view models because the template should never
 * mutate the data it receives.
 * </p>
 *
 * @param id       unique identifier
 * @param name     display name of the product
 * @param category category it belongs to (e.g. "Electronics", "Books")
 * @param price    price in USD
 * @param inStock  whether the product is currently available
 */
public record Product(
        Long id,
        String name,
        String category,
        double price,
        boolean inStock) {
}
