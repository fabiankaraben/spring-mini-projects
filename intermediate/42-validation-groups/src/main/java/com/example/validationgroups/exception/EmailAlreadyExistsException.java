package com.example.validationgroups.exception;

/**
 * Exception thrown when an attempt is made to register or update a user with
 * an email address that is already in use by another account.
 *
 * <p>The {@link GlobalExceptionHandler} catches this and converts it to an
 * HTTP 409 Conflict response, informing the client that the email is taken.</p>
 */
public class EmailAlreadyExistsException extends RuntimeException {

    /**
     * Creates the exception with a message identifying the duplicate email.
     *
     * @param email the email address that caused the conflict
     */
    public EmailAlreadyExistsException(String email) {
        super("Email address is already in use: " + email);
    }
}
