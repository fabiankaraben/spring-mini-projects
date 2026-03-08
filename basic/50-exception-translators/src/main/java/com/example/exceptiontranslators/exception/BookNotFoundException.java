package com.example.exceptiontranslators.exception;

/**
 * Custom exception thrown when a requested book cannot be found.
 */
public class BookNotFoundException extends RuntimeException {
    public BookNotFoundException(String message) {
        super(message);
    }
}
