package com.example.autoconfiguration.exception;

/**
 * Exception thrown when a {@link com.example.autoconfiguration.entity.GreetingLog}
 * with the requested ID does not exist in the database.
 *
 * <p>Extending {@link RuntimeException} makes this an unchecked exception — callers
 * are not forced to catch it. The {@link GlobalExceptionHandler} intercepts it and
 * maps it to a {@code 404 Not Found} HTTP response.
 */
public class GreetingLogNotFoundException extends RuntimeException {

    /**
     * Creates the exception with a message that includes the missing ID.
     *
     * @param id the ID that was not found in the database
     */
    public GreetingLogNotFoundException(Long id) {
        super("Greeting log not found with id: " + id);
    }
}
