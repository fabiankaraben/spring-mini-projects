package com.example.twiliosmssender.exception;

/**
 * Exception thrown when an SMS message record cannot be found in the local database.
 *
 * <p>This is an unchecked ({@link RuntimeException}) exception so it does not need
 * to be declared in method signatures. The {@link GlobalExceptionHandler} catches it
 * and maps it to an HTTP 404 Not Found response.
 *
 * <p>Example scenarios where this is thrown:
 * <ul>
 *   <li>Requesting a message by local ID that does not exist: {@code GET /api/sms/999}</li>
 *   <li>Requesting a message by Twilio SID that was never recorded locally</li>
 * </ul>
 */
public class SmsNotFoundException extends RuntimeException {

    /**
     * Creates an exception for a message not found by its local database ID.
     *
     * @param id the local database ID that was not found
     */
    public SmsNotFoundException(Long id) {
        super("SMS message not found with id: " + id);
    }

    /**
     * Creates an exception for a message not found by its Twilio SID.
     *
     * @param twilioSid the Twilio message SID that was not found
     */
    public SmsNotFoundException(String twilioSid) {
        super("SMS message not found with Twilio SID: " + twilioSid);
    }
}
