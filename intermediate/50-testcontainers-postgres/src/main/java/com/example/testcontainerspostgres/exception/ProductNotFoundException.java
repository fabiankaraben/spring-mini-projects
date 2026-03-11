package com.example.testcontainerspostgres.exception;

/**
 * Exception thrown when a requested {@code Product} cannot be found in the database.
 *
 * <p>Extending {@link RuntimeException} (an unchecked exception) means callers
 * do not need to declare it in a {@code throws} clause, keeping the service API
 * clean. The {@link GlobalExceptionHandler} catches this exception and maps it
 * to an HTTP 404 Not Found response automatically.
 *
 * <p>The constructor takes the product {@code id} so that the error message
 * includes the ID that was looked up — making it easier to debug.
 */
public class ProductNotFoundException extends RuntimeException {

    /**
     * Creates a new exception with a descriptive message containing the product ID.
     *
     * @param id the ID of the product that was not found
     */
    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }
}
