package com.example.stripepayment.exception;

/**
 * Exception thrown when a requested payment record cannot be found.
 *
 * <p>This is a domain exception that wraps the "not found" case into a
 * meaningful type. The {@link GlobalExceptionHandler} catches this and
 * returns an HTTP 404 Not Found response to the client.
 *
 * <p>Usage examples:
 * <ul>
 *   <li>Looking up a payment by local database ID that does not exist.</li>
 *   <li>Looking up a payment by Stripe PaymentIntent ID that has no local record.</li>
 * </ul>
 */
public class PaymentNotFoundException extends RuntimeException {

    /**
     * Creates a PaymentNotFoundException for a local database ID lookup.
     *
     * @param id the local database ID that was not found
     */
    public PaymentNotFoundException(Long id) {
        super("Payment not found with id: " + id);
    }

    /**
     * Creates a PaymentNotFoundException for a Stripe PaymentIntent ID lookup.
     *
     * @param stripePaymentIntentId the Stripe ID that has no matching local record
     */
    public PaymentNotFoundException(String stripePaymentIntentId) {
        super("Payment not found with Stripe PaymentIntent ID: " + stripePaymentIntentId);
    }
}
