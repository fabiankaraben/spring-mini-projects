package com.example.error.exception;

/**
 * Exception thrown when a product is not found.
 */
public class ProductNotFoundException extends RuntimeException {

    /**
     * Constructs a new ProductNotFoundException detailing which ID was missing.
     * 
     * @param id the missing product id
     */
    public ProductNotFoundException(Long id) {
        super("Product with ID " + id + " not found");
    }
}
