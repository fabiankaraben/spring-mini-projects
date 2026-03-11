package com.example.twiliosmssender.exception;

/**
 * Exception thrown when the Twilio API returns an error during an SMS send operation.
 *
 * <p>This is an unchecked ({@link RuntimeException}) exception that wraps the
 * checked {@link com.twilio.exception.ApiException} from the Twilio SDK. The
 * {@link GlobalExceptionHandler} catches it and maps it to an HTTP 502 Bad Gateway
 * response, indicating that the upstream Twilio service returned an error.
 *
 * <p>Wrapping the Twilio SDK exception in our own domain exception keeps the
 * service layer decoupled from the specific Twilio exception hierarchy — if we
 * ever switch to a different SMS provider, the service contract stays the same.
 */
public class TwilioSmsException extends RuntimeException {

    /**
     * Creates a new exception with a descriptive message and the original Twilio cause.
     *
     * @param message a human-readable description of what failed
     * @param cause   the original Twilio SDK exception
     */
    public TwilioSmsException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception with only a message (no cause).
     * Useful when the error does not originate from an exception chain.
     *
     * @param message a human-readable description of what failed
     */
    public TwilioSmsException(String message) {
        super(message);
    }
}
