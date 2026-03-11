package com.example.stripepayment.service;

import com.example.stripepayment.domain.Payment;
import com.example.stripepayment.domain.PaymentStatus;
import com.example.stripepayment.dto.CreatePaymentRequest;
import com.example.stripepayment.dto.PaymentResponse;
import com.example.stripepayment.exception.PaymentNotFoundException;
import com.example.stripepayment.exception.StripePaymentException;
import com.example.stripepayment.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service that orchestrates Stripe PaymentIntent operations and local persistence.
 *
 * <h2>Stripe PaymentIntent lifecycle</h2>
 * <ol>
 *   <li><b>Create</b> – {@link #createPayment(CreatePaymentRequest)} calls
 *       {@code PaymentIntent.create()} on the Stripe API and saves a local record
 *       with status {@link PaymentStatus#PENDING}.</li>
 *   <li><b>Confirm</b> – {@link #confirmPayment(String)} calls
 *       {@code PaymentIntent.confirm()} which simulates the frontend completing
 *       the payment. Status changes to {@link PaymentStatus#SUCCEEDED}.</li>
 *   <li><b>Cancel</b> – {@link #cancelPayment(String)} calls
 *       {@code PaymentIntent.cancel()} and sets the local status to
 *       {@link PaymentStatus#CANCELED}.</li>
 * </ol>
 *
 * <h2>Local database</h2>
 * <p>Every state-change operation updates the local {@link Payment} record in
 * PostgreSQL via the {@link PaymentRepository}. This gives us a local audit
 * trail and avoids repeated Stripe API calls for read operations.
 *
 * <h2>Error handling</h2>
 * <p>All {@link StripeException} (checked) exceptions are wrapped into the
 * unchecked {@link StripePaymentException} so the service method signatures
 * remain clean.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /**
     * The payment method type used in test mode.
     *
     * <p>In production, the payment method ID comes from the frontend (Stripe.js
     * tokenises the card and returns a {@code pm_xxx} ID). For testing purposes,
     * Stripe provides the special test token {@code pm_card_visa} which always
     * succeeds.
     *
     * <p>See: https://stripe.com/docs/testing#cards
     */
    private static final String TEST_PAYMENT_METHOD = "pm_card_visa";

    /** Repository for local payment record persistence (PostgreSQL via JPA). */
    private final PaymentRepository paymentRepository;

    /**
     * Constructor injection – preferred for testability (easy to provide a mock).
     *
     * @param paymentRepository the JPA repository for Payment entities
     */
    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Create
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new Stripe PaymentIntent and persists a local payment record.
     *
     * <p>Steps:
     * <ol>
     *   <li>Build a {@link PaymentIntentCreateParams} with amount, currency, and
     *       the {@code card} payment method type.</li>
     *   <li>Call {@code PaymentIntent.create(params)} on the Stripe API.</li>
     *   <li>Save a new {@link Payment} entity to PostgreSQL.</li>
     *   <li>Return a {@link PaymentResponse} DTO including the {@code clientSecret}
     *       that the frontend needs to confirm the payment.</li>
     * </ol>
     *
     * @param request the validated request DTO with amount, currency, and description
     * @return a {@link PaymentResponse} containing the Stripe PaymentIntent ID and client secret
     * @throws StripePaymentException if the Stripe API call fails
     */
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.info("Creating PaymentIntent: amount={}, currency={}", request.amount(), request.currency());

        // Build Stripe PaymentIntent creation parameters.
        // 'card' is the payment method type; the frontend will supply the actual
        // card details via Stripe.js using the client_secret returned here.
        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(request.amount())
                .setCurrency(request.currency())
                // Enable automatic payment methods to support cards and more
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                // Allow redirect-based payment methods to be skipped in tests
                                .setAllowRedirects(
                                        PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                .build()
                );

        // Set description only when provided (Stripe rejects empty strings)
        if (request.description() != null && !request.description().isBlank()) {
            paramsBuilder.setDescription(request.description());
        }

        try {
            // Call the Stripe API to create the PaymentIntent
            PaymentIntent stripePaymentIntent = PaymentIntent.create(paramsBuilder.build());
            log.info("Stripe PaymentIntent created: id={}, status={}",
                    stripePaymentIntent.getId(), stripePaymentIntent.getStatus());

            // Map Stripe status to our local PaymentStatus enum
            PaymentStatus localStatus = mapStripeStatus(stripePaymentIntent.getStatus());

            // Persist the local payment record in PostgreSQL
            Payment payment = new Payment(
                    stripePaymentIntent.getId(),
                    request.amount(),
                    request.currency(),
                    localStatus,
                    stripePaymentIntent.getClientSecret()
            );
            payment.setDescription(request.description());
            Payment saved = paymentRepository.save(payment);

            log.info("Payment record saved: localId={}, stripeId={}",
                    saved.getId(), saved.getStripePaymentIntentId());

            return PaymentResponse.from(saved);

        } catch (StripeException e) {
            // Wrap the checked Stripe exception into our unchecked domain exception
            throw new StripePaymentException("Failed to create PaymentIntent with Stripe: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Confirm
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Confirms a PaymentIntent on Stripe and updates the local record status.
     *
     * <p>In a real application, the frontend confirms the PaymentIntent using
     * Stripe.js (the client-side SDK). This server-side confirm endpoint is
     * provided for educational purposes and API testing convenience.
     *
     * <p>The test payment method {@value #TEST_PAYMENT_METHOD} is used so that
     * this endpoint works without a real card number.
     *
     * @param stripePaymentIntentId the Stripe PaymentIntent ID (e.g., {@code pi_xxx})
     * @return the updated {@link PaymentResponse} with {@code SUCCEEDED} or {@code FAILED} status
     * @throws PaymentNotFoundException if no local record exists for the given Stripe ID
     * @throws StripePaymentException   if the Stripe API call fails
     */
    @Transactional
    public PaymentResponse confirmPayment(String stripePaymentIntentId) {
        log.info("Confirming PaymentIntent: {}", stripePaymentIntentId);

        // Find the local record first (fail fast if it doesn't exist)
        Payment payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> new PaymentNotFoundException(stripePaymentIntentId));

        try {
            // Retrieve the PaymentIntent from Stripe
            PaymentIntent stripePaymentIntent = PaymentIntent.retrieve(stripePaymentIntentId);

            // Build confirm parameters using the test payment method token
            PaymentIntentConfirmParams confirmParams = PaymentIntentConfirmParams.builder()
                    .setPaymentMethod(TEST_PAYMENT_METHOD)
                    .setReturnUrl("https://example.com/payment/return")
                    .build();

            // Confirm the PaymentIntent on Stripe
            stripePaymentIntent = stripePaymentIntent.confirm(confirmParams);
            log.info("Stripe PaymentIntent confirmed: id={}, status={}",
                    stripePaymentIntent.getId(), stripePaymentIntent.getStatus());

            // Update the local record status
            payment.setStatus(mapStripeStatus(stripePaymentIntent.getStatus()));
            Payment saved = paymentRepository.save(payment);

            return PaymentResponse.from(saved);

        } catch (StripeException e) {
            throw new StripePaymentException(
                    "Failed to confirm PaymentIntent " + stripePaymentIntentId + ": " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cancel
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cancels a PaymentIntent on Stripe and updates the local record to CANCELED.
     *
     * <p>A PaymentIntent can only be canceled when it is in one of these Stripe
     * statuses: {@code requires_payment_method}, {@code requires_capture},
     * {@code requires_confirmation}, or {@code requires_action}.
     *
     * @param stripePaymentIntentId the Stripe PaymentIntent ID to cancel
     * @return the updated {@link PaymentResponse} with {@code CANCELED} status
     * @throws PaymentNotFoundException if no local record exists for the given Stripe ID
     * @throws StripePaymentException   if the Stripe API call fails
     */
    @Transactional
    public PaymentResponse cancelPayment(String stripePaymentIntentId) {
        log.info("Canceling PaymentIntent: {}", stripePaymentIntentId);

        // Find the local record (fail fast if missing)
        Payment payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> new PaymentNotFoundException(stripePaymentIntentId));

        try {
            // Retrieve and cancel on Stripe
            PaymentIntent stripePaymentIntent = PaymentIntent.retrieve(stripePaymentIntentId);
            PaymentIntentCancelParams cancelParams = PaymentIntentCancelParams.builder().build();
            stripePaymentIntent = stripePaymentIntent.cancel(cancelParams);

            log.info("Stripe PaymentIntent canceled: id={}, status={}",
                    stripePaymentIntent.getId(), stripePaymentIntent.getStatus());

            // Update local record
            payment.setStatus(PaymentStatus.CANCELED);
            Payment saved = paymentRepository.save(payment);

            return PaymentResponse.from(saved);

        } catch (StripeException e) {
            throw new StripePaymentException(
                    "Failed to cancel PaymentIntent " + stripePaymentIntentId + ": " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Read operations (local DB only – no Stripe API calls needed)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lists all payment records from the local PostgreSQL database.
     *
     * <p>This does NOT call the Stripe API — it reads from the local audit table.
     *
     * @return a list of all {@link PaymentResponse} DTOs (may be empty)
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments() {
        log.info("Listing all payments from local database");
        return paymentRepository.findAll().stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /**
     * Retrieves a single payment by its local database ID.
     *
     * @param id the local database primary key
     * @return the {@link PaymentResponse} DTO for the matching record
     * @throws PaymentNotFoundException if no record with that ID exists
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        log.info("Fetching payment by local id={}", id);
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentResponse.from(payment);
    }

    /**
     * Retrieves a single payment by its Stripe PaymentIntent ID.
     *
     * @param stripePaymentIntentId the Stripe-assigned ID (e.g., {@code pi_xxx})
     * @return the {@link PaymentResponse} DTO for the matching record
     * @throws PaymentNotFoundException if no local record matches that Stripe ID
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByStripeId(String stripePaymentIntentId) {
        log.info("Fetching payment by Stripe ID: {}", stripePaymentIntentId);
        Payment payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> new PaymentNotFoundException(stripePaymentIntentId));
        return PaymentResponse.from(payment);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Maps a Stripe PaymentIntent status string to our local {@link PaymentStatus} enum.
     *
     * <p>Stripe status values (as of 2024):
     * <ul>
     *   <li>{@code requires_payment_method} – waiting for card details</li>
     *   <li>{@code requires_confirmation}   – waiting for server-side confirm call</li>
     *   <li>{@code requires_action}         – 3D Secure or redirect needed</li>
     *   <li>{@code processing}              – payment is being processed</li>
     *   <li>{@code requires_capture}        – authorized but not yet captured</li>
     *   <li>{@code canceled}               – explicitly canceled</li>
     *   <li>{@code succeeded}              – payment completed successfully</li>
     * </ul>
     *
     * @param stripeStatus the raw status string from the Stripe API response
     * @return the corresponding local {@link PaymentStatus}
     */
    PaymentStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "canceled" -> PaymentStatus.CANCELED;
            case "requires_payment_method" -> PaymentStatus.FAILED;
            // All other statuses (requires_confirmation, requires_action,
            // processing, requires_capture) are considered PENDING
            default -> PaymentStatus.PENDING;
        };
    }
}
