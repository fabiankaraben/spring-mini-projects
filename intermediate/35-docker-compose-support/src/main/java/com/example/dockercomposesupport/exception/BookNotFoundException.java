package com.example.dockercomposesupport.exception;

/**
 * Thrown when a requested book does not exist in the catalogue.
 *
 * <p>Extending {@link RuntimeException} allows this to be thrown without
 * checked-exception handling at call sites. The
 * {@link com.example.dockercomposesupport.exception.GlobalExceptionHandler}
 * translates this into an HTTP 404 response.</p>
 */
public class BookNotFoundException extends RuntimeException {

    /**
     * Constructs the exception with a descriptive message.
     *
     * @param message description of the missing resource (e.g. "Book with id 42 not found")
     */
    public BookNotFoundException(String message) {
        super(message);
    }
}
