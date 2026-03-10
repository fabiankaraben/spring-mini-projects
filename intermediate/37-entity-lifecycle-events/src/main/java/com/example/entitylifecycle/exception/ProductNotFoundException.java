package com.example.entitylifecycle.exception;

/**
 * Exception thrown when a {@code Product} with the requested ID does not exist
 * in the database.
 *
 * <p>Extending {@link RuntimeException} means callers are not forced to declare
 * or catch it (unchecked exception). The
 * {@link com.example.entitylifecycle.exception.GlobalExceptionHandler} translates
 * this exception into an HTTP 404 Not Found response automatically.
 */
public class ProductNotFoundException extends RuntimeException {

    /**
     * Creates the exception with a descriptive message that includes the missing ID.
     *
     * @param id the primary key that was not found
     */
    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }
}
