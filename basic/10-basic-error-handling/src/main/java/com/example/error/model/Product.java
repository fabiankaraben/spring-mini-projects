package com.example.error.model;

/**
 * A simple domain object representing a Product.
 * We use a record to represent immutable data concisely.
 */
public record Product(Long id, String name, double price) {
}
