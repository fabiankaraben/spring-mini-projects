package com.example.optimisticlocking.exception;

/**
 * Thrown by the service layer when a product with the given ID cannot be found.
 *
 * <p>The {@link GlobalExceptionHandler} catches this exception and translates it
 * into an HTTP 404 Not Found response with a descriptive JSON error body.</p>
 *
 * <p>Extending {@link RuntimeException} (an unchecked exception) means callers
 * are not forced to handle or declare it – Spring's transaction management will
 * roll back the current transaction automatically when this is thrown.</p>
 */
public class ProductNotFoundException extends RuntimeException {

    /**
     * Creates the exception with a message that includes the missing product's ID.
     *
     * @param id the ID of the product that was not found
     */
    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }
}
