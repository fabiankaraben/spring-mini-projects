package com.example.stripepayment.exception;

/**
 * Exception thrown when a Stripe API call fails in an unexpected way.
 *
 * <p>The Stripe Java SDK throws checked exceptions (subtypes of
 * {@link com.stripe.exception.StripeException}) for every API error.
 * This wrapper converts them into an unchecked runtime exception so that
 * the service layer does not need to declare {@code throws StripeException}
 * on every method signature.
 *
 * <p>The {@link GlobalExceptionHandler} catches this exception and returns
 * an HTTP 502 Bad Gateway response to signal that the upstream Stripe API
 * encountered a problem.
 *
 * <p>Usage:
 * <pre>{@code
 * try {
 *     PaymentIntent pi = PaymentIntent.create(params);
 * } catch (StripeException e) {
 *     throw new StripePaymentException("Failed to create PaymentIntent", e);
 * }
 * }</pre>
 */
public class StripePaymentException extends RuntimeException {

    /**
     * Creates a StripePaymentException with a descriptive message and the original cause.
     *
     * @param message a human-readable description of what failed
     * @param cause   the original {@link com.stripe.exception.StripeException}
     */
    public StripePaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
