package com.example.exceptiontranslators.exception;

/**
 * Custom exception thrown when trying to create a book that already exists.
 */
public class BookAlreadyExistsException extends RuntimeException {
    public BookAlreadyExistsException(String message) {
        super(message);
    }
}
