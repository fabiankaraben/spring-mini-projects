package com.example.stripepayment.domain;

/**
 * Represents the lifecycle status of a payment record stored locally.
 *
 * <p>These values mirror the subset of Stripe PaymentIntent statuses that this
 * application tracks. Stripe has more statuses (e.g., {@code requires_action},
 * {@code processing}), but for this educational project we simplify to the most
 * common outcomes:
 *
 * <ul>
 *   <li>{@link #PENDING}   – PaymentIntent has been created but not yet confirmed.</li>
 *   <li>{@link #SUCCEEDED} – Payment was confirmed and funds were captured.</li>
 *   <li>{@link #CANCELED}  – PaymentIntent was explicitly canceled before capture.</li>
 *   <li>{@link #FAILED}    – The payment attempt was declined or errored.</li>
 * </ul>
 *
 * <p>The enum value is stored as a {@code VARCHAR} column in PostgreSQL via
 * {@code @Enumerated(EnumType.STRING)} so the column is human-readable.
 */
public enum PaymentStatus {

    /**
     * PaymentIntent created on Stripe; waiting for client-side confirmation.
     * Corresponds to Stripe statuses {@code requires_payment_method} or
     * {@code requires_confirmation}.
     */
    PENDING,

    /**
     * The payment succeeded. Stripe status: {@code succeeded}.
     */
    SUCCEEDED,

    /**
     * The PaymentIntent was canceled before it could be completed.
     * Stripe status: {@code canceled}.
     */
    CANCELED,

    /**
     * The payment failed (e.g., card declined, insufficient funds).
     * Stripe status: {@code requires_payment_method} after a failed attempt.
     */
    FAILED
}
