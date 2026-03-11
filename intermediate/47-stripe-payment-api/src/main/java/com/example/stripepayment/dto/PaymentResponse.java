package com.example.stripepayment.dto;

import com.example.stripepayment.domain.Payment;
import com.example.stripepayment.domain.PaymentStatus;

import java.time.Instant;

/**
 * DTO (Data Transfer Object) for the payment HTTP response body.
 *
 * <p>Returned by all endpoints that produce payment data. It flattens the
 * {@link Payment} JPA entity into a plain record that is safe to serialize
 * directly to JSON via Jackson.
 *
 * <p>The {@link #clientSecret} field is included because the frontend needs it
 * to confirm the PaymentIntent using Stripe.js or the Stripe mobile SDKs.
 * In a real production application you would only return the client secret in
 * the create response and omit it from list/retrieve responses to minimize
 * sensitive data exposure.
 *
 * <p>Example JSON response:
 * <pre>{@code
 * {
 *   "id": 1,
 *   "stripePaymentIntentId": "pi_3NtYvJ2eZvKYlo2C1kfmXjBR",
 *   "amount": 2000,
 *   "currency": "usd",
 *   "description": "Order #1234",
 *   "status": "PENDING",
 *   "clientSecret": "pi_3NtYvJ2eZvKYlo2C1kfmXjBR_secret_...",
 *   "createdAt": "2024-01-15T10:30:00Z",
 *   "updatedAt": "2024-01-15T10:30:00Z"
 * }
 * }</pre>
 *
 * @param id                    the local database ID
 * @param stripePaymentIntentId the Stripe-assigned PaymentIntent ID
 * @param amount                the amount in the smallest currency unit (e.g., cents)
 * @param currency              the 3-letter ISO 4217 currency code
 * @param description           optional human-readable description
 * @param status                the current payment lifecycle status
 * @param clientSecret          the Stripe client secret for frontend confirmation
 * @param createdAt             timestamp when the record was created
 * @param updatedAt             timestamp of the last status update
 */
public record PaymentResponse(
        Long id,
        String stripePaymentIntentId,
        Long amount,
        String currency,
        String description,
        PaymentStatus status,
        String clientSecret,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Factory method that maps a {@link Payment} entity to a {@link PaymentResponse} DTO.
     *
     * <p>This pattern keeps the DTO construction in one place and avoids spreading
     * entity-to-DTO mapping logic across multiple layers.
     *
     * @param payment the JPA entity to convert
     * @return a new PaymentResponse DTO populated from the entity fields
     */
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStripePaymentIntentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getDescription(),
                payment.getStatus(),
                payment.getClientSecret(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
