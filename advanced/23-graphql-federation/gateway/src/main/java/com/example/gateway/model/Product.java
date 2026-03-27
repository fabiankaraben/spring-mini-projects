package com.example.gateway.model;

import java.math.BigDecimal;

/**
 * Gateway-side representation of a Product.
 *
 * <p>This is a Data Transfer Object (DTO) that mirrors the {@code Product} type
 * returned by the products-service GraphQL API. The gateway deserializes the
 * JSON response from the products-service HTTP call into this record.
 *
 * <p>Using a separate DTO in the gateway (rather than sharing the same class
 * as the products-service) demonstrates service isolation — the gateway only
 * needs to know the fields it will actually serve to clients.
 *
 * @param id          Unique product identifier.
 * @param name        Human-readable product name.
 * @param description Short product description.
 * @param price       Product price in USD.
 * @param category    Product category.
 * @param inStock     Whether the product is currently in stock.
 */
public record Product(
        String id,
        String name,
        String description,
        BigDecimal price,
        String category,
        boolean inStock
) {}
