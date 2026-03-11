package com.example.validationgroups.exception;

/**
 * Exception thrown when a requested {@code User} entity cannot be found in the database.
 *
 * <p>This is a runtime exception (unchecked) so it can propagate up through the service
 * and controller layers without requiring explicit {@code throws} declarations.  The
 * {@link GlobalExceptionHandler} catches it and converts it to an HTTP 404 response.</p>
 */
public class UserNotFoundException extends RuntimeException {

    /**
     * Creates the exception with a message that includes the missing user's ID.
     *
     * @param id the ID that was looked up but not found
     */
    public UserNotFoundException(Long id) {
        super("User not found with id: " + id);
    }
}
