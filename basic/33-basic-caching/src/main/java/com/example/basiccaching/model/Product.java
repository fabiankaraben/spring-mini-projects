package com.example.basiccaching.model;

/**
 * Represents a product in our system.
 *
 * <p>
 * This is a simple Plain Old Java Object (POJO). We use a Java record here
 * because records are immutable by design and provide an excellent, concise way
 * to define data carrier classes. Spring's cache serialization handles records
 * transparently.
 * </p>
 *
 * <p>
 * Fields:
 * <ul>
 * <li>{@code id} - Unique identifier for the product.</li>
 * <li>{@code name} - Display name of the product.</li>
 * <li>{@code category} - Category the product belongs to.</li>
 * <li>{@code price} - Price in USD.</li>
 * </ul>
 * </p>
 */
public record Product(Long id, String name, String category, double price) {
}
