package com.example.validationgroups.exception;

/**
 * Exception thrown when the {@code newPassword} and {@code confirmPassword} fields
 * in a change-password request do not match each other.
 *
 * <p>This is a cross-field validation check that cannot be expressed with a single
 * field-level Bean Validation constraint, so it is enforced in the service layer.
 * The {@link GlobalExceptionHandler} converts it to an HTTP 400 Bad Request response.</p>
 */
public class PasswordMismatchException extends RuntimeException {

    /**
     * Creates the exception with a fixed human-readable message.
     */
    public PasswordMismatchException() {
        super("New password and confirmation password do not match");
    }
}
