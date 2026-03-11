package com.example.stripepayment.controller;

import com.example.stripepayment.dto.CreatePaymentRequest;
import com.example.stripepayment.dto.PaymentResponse;
import com.example.stripepayment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that exposes the Stripe Payment API endpoints.
 *
 * <p>Base path: {@code /api/payments}
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST   /api/payments}                  – Create a new PaymentIntent</li>
 *   <li>{@code GET    /api/payments}                  – List all local payment records</li>
 *   <li>{@code GET    /api/payments/{id}}             – Get a payment by local DB ID</li>
 *   <li>{@code GET    /api/payments/stripe/{stripeId}} – Get a payment by Stripe ID</li>
 *   <li>{@code POST   /api/payments/{stripeId}/confirm} – Confirm a PaymentIntent</li>
 *   <li>{@code POST   /api/payments/{stripeId}/cancel}  – Cancel a PaymentIntent</li>
 * </ul>
 *
 * <p>The controller is intentionally thin: all business logic and Stripe API calls
 * live in {@link PaymentService}. This class only maps HTTP semantics
 * (status codes, request/response bodies) to service method calls.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    /** The service containing all Stripe and database logic. */
    private final PaymentService paymentService;

    /**
     * Constructor injection – preferred over field injection for testability.
     *
     * @param paymentService the service handling Stripe and persistence operations
     */
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Create
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new Stripe PaymentIntent and persists a local payment record.
     *
     * <p>The response includes a {@code clientSecret} that the frontend must use
     * with Stripe.js (or the mobile SDK) to collect and submit card details.
     *
     * <p>Example with curl:
     * <pre>
     * curl -X POST http://localhost:8080/api/payments \
     *      -H "Content-Type: application/json" \
     *      -d '{"amount": 2000, "currency": "usd", "description": "Order #1234"}'
     * </pre>
     *
     * @param request the validated request body with amount, currency, and description
     * @return HTTP 201 Created with a {@link PaymentResponse} JSON body
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse response = paymentService.createPayment(request);
        // 201 Created is the conventional status for a successfully created resource
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // List
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lists all payment records stored in the local PostgreSQL database.
     *
     * <p>This endpoint reads from the local audit table only — it does NOT call
     * the Stripe API.
     *
     * <p>Example with curl:
     * <pre>
     * curl http://localhost:8080/api/payments
     * </pre>
     *
     * @return HTTP 200 with a JSON array of all {@link PaymentResponse} records
     */
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> listPayments() {
        return ResponseEntity.ok(paymentService.listPayments());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get by local ID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves a single payment record by its local database ID.
     *
     * <p>Example with curl:
     * <pre>
     * curl http://localhost:8080/api/payments/1
     * </pre>
     *
     * @param id the local PostgreSQL primary key of the payment record
     * @return HTTP 200 with a {@link PaymentResponse} body, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get by Stripe PaymentIntent ID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves a single payment record by its Stripe PaymentIntent ID.
     *
     * <p>Example with curl:
     * <pre>
     * curl http://localhost:8080/api/payments/stripe/pi_3NtYvJ2eZvKYlo2C1kfmXjBR
     * </pre>
     *
     * @param stripeId the Stripe PaymentIntent ID (e.g., {@code pi_xxx})
     * @return HTTP 200 with a {@link PaymentResponse} body, or 404 if not found
     */
    @GetMapping("/stripe/{stripeId}")
    public ResponseEntity<PaymentResponse> getPaymentByStripeId(@PathVariable String stripeId) {
        return ResponseEntity.ok(paymentService.getPaymentByStripeId(stripeId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Confirm
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Confirms a PaymentIntent on Stripe using a test card payment method.
     *
     * <p>In a real application, confirmation is done client-side using Stripe.js.
     * This endpoint exists for educational/testing purposes to simulate the
     * full payment lifecycle from the server side.
     *
     * <p>Example with curl:
     * <pre>
     * curl -X POST http://localhost:8080/api/payments/pi_3NtYvJ2eZvKYlo2C1kfmXjBR/confirm
     * </pre>
     *
     * @param stripeId the Stripe PaymentIntent ID to confirm
     * @return HTTP 200 with an updated {@link PaymentResponse} showing the new status
     */
    @PostMapping("/{stripeId}/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(@PathVariable String stripeId) {
        return ResponseEntity.ok(paymentService.confirmPayment(stripeId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cancel
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cancels a PaymentIntent on Stripe and updates the local record status.
     *
     * <p>A PaymentIntent can only be canceled if it has not already been confirmed
     * (i.e., it must still be in a {@code requires_*} state on Stripe).
     *
     * <p>Example with curl:
     * <pre>
     * curl -X POST http://localhost:8080/api/payments/pi_3NtYvJ2eZvKYlo2C1kfmXjBR/cancel
     * </pre>
     *
     * @param stripeId the Stripe PaymentIntent ID to cancel
     * @return HTTP 200 with an updated {@link PaymentResponse} showing CANCELED status
     */
    @PostMapping("/{stripeId}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(@PathVariable String stripeId) {
        return ResponseEntity.ok(paymentService.cancelPayment(stripeId));
    }
}
