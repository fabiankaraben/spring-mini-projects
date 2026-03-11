package com.example.stripepayment.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity that represents a local record of a Stripe PaymentIntent.
 *
 * <p>Every time a PaymentIntent is created via the Stripe API, we also persist a
 * local record in PostgreSQL. This allows the application to:
 * <ul>
 *   <li>List all payments without hitting the Stripe API each time.</li>
 *   <li>Store additional business context (e.g., description, currency).</li>
 *   <li>Track status changes as the payment lifecycle progresses.</li>
 * </ul>
 *
 * <p>Database table: {@code payments}
 *
 * <p>Important fields:
 * <ul>
 *   <li>{@link #stripePaymentIntentId} – the Stripe-assigned ID (e.g., {@code pi_xxx}),
 *       used to correlate local records with Stripe objects.</li>
 *   <li>{@link #amount} – the payment amount in the smallest currency unit
 *       (e.g., cents for USD). This is how Stripe expects amounts.</li>
 *   <li>{@link #currency} – 3-letter ISO 4217 currency code (e.g., {@code usd}).</li>
 *   <li>{@link #status} – the current payment lifecycle status (see {@link PaymentStatus}).</li>
 * </ul>
 */
@Entity
@Table(name = "payments")
public class Payment {

    /**
     * Auto-generated surrogate primary key (local database ID).
     * This is distinct from the Stripe payment intent ID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The Stripe PaymentIntent ID (e.g., {@code pi_3NtYvJ2eZvKYlo2C1kfmXjBR}).
     * Unique and indexed for fast lookups by Stripe ID.
     */
    @Column(name = "stripe_payment_intent_id", nullable = false, unique = true, length = 100)
    private String stripePaymentIntentId;

    /**
     * Payment amount in the smallest currency unit (e.g., 1000 = $10.00 for USD).
     * Stripe requires amounts to be positive integers in the smallest unit.
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /**
     * 3-letter ISO 4217 currency code in lowercase (e.g., {@code usd}, {@code eur}).
     * Must match the currency configured in your Stripe account.
     */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /**
     * Human-readable description for this payment (shown in Stripe dashboard).
     * Optional field; may be null.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * The current status of the payment in our local tracking system.
     * Stored as a VARCHAR string (not an integer ordinal) for readability.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    /**
     * Timestamp when this local record was first created (i.e., when the
     * PaymentIntent was created on Stripe).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp of the last status update for this payment record.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Stripe's client secret for this PaymentIntent.
     *
     * <p>The client secret is returned to the frontend so it can confirm
     * the PaymentIntent using Stripe.js or the mobile SDKs. It is sensitive
     * and should never be logged or exposed publicly.
     */
    @Column(name = "client_secret", length = 200)
    private String clientSecret;

    /**
     * Sets {@link #createdAt} and {@link #updatedAt} automatically before
     * the entity is first persisted to the database.
     */
    @PrePersist
    protected void onPersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Updates {@link #updatedAt} automatically every time the entity is modified
     * and saved to the database.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required by JPA – do not use directly; use the parameterized constructor. */
    protected Payment() {
    }

    /**
     * Creates a new Payment record with the minimum required fields.
     *
     * @param stripePaymentIntentId the Stripe PaymentIntent ID
     * @param amount                the amount in smallest currency unit (e.g., cents)
     * @param currency              the 3-letter currency code (e.g., "usd")
     * @param status                the initial payment status
     * @param clientSecret          the Stripe client secret for frontend confirmation
     */
    public Payment(String stripePaymentIntentId, Long amount, String currency,
                   PaymentStatus status, String clientSecret) {
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.clientSecret = clientSecret;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public void setStripePaymentIntentId(String stripePaymentIntentId) {
        this.stripePaymentIntentId = stripePaymentIntentId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @Override
    public String toString() {
        return "Payment{" +
                "id=" + id +
                ", stripePaymentIntentId='" + stripePaymentIntentId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", status=" + status +
                '}';
    }
}
