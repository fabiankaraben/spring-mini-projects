package com.example.products.model;

import java.math.BigDecimal;

/**
 * Domain model representing a Product in the product catalogue.
 *
 * <p>In the GraphQL Federation architecture this is the canonical "source of truth"
 * type for product data. The products-service owns this type; no other subgraph
 * service should modify or duplicate it.
 *
 * <p>Defined as a Java record because products are essentially value objects —
 * they are read from the repository and returned to the GraphQL resolver. Records
 * give us immutability, {@code equals()}, {@code hashCode()}, and a compact
 * constructor for free.
 *
 * @param id          Unique identifier (e.g., "prod-1"). Used by the gateway and
 *                    reviews-service to reference products by ID.
 * @param name        Human-readable product name (e.g., "Wireless Keyboard").
 * @param description Short description of the product.
 * @param price       Product price in the base currency (e.g., USD).
 * @param category    Product category label (e.g., "Electronics").
 * @param inStock     Whether the product is currently available for purchase.
 */
public record Product(
        String id,
        String name,
        String description,
        BigDecimal price,
        String category,
        boolean inStock
) {}
