package com.example.error.exception;

/**
 * Exception thrown when a product has invalid data (e.g. negative price).
 */
public class InvalidProductException extends RuntimeException {

    /**
     * Constructs a new InvalidProductException with a specific message.
     * 
     * @param message the validation failure reason
     */
    public InvalidProductException(String message) {
        super(message);
    }
}
