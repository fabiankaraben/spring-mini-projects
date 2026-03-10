package com.example.dockercomposesupport.exception;

/**
 * Thrown when a create or update request would introduce a duplicate
 * book title or ISBN that already exists in the catalogue.
 *
 * <p>The {@link com.example.dockercomposesupport.exception.GlobalExceptionHandler}
 * translates this into an HTTP 409 Conflict response.</p>
 */
public class DuplicateBookException extends RuntimeException {

    /**
     * Constructs the exception with a descriptive message.
     *
     * @param message explanation of the conflict (e.g. "Book with title 'X' already exists")
     */
    public DuplicateBookException(String message) {
        super(message);
    }
}
