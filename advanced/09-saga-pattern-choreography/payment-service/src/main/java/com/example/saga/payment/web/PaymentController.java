package com.example.saga.payment.web;

import com.example.saga.payment.domain.Payment;
import com.example.saga.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST controller for the Payment Service.
 *
 * <p>Exposes one read endpoint:
 * <ul>
 *   <li>{@code GET /api/payments/order/{orderId}} — look up payment status by order ID.</li>
 * </ul>
 *
 * <p>Payments are created automatically by consuming Kafka events, not via REST.
 * This endpoint exists purely for observability (e.g., curl queries in demos).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Returns the payment associated with an order.
     *
     * @param orderId the saga order UUID
     * @return 200 with the payment details, or 404 if not yet processed
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrder(@PathVariable UUID orderId) {
        return paymentService.findByOrderId(orderId)
                .map(PaymentResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // Response DTO
    // -------------------------------------------------------------------------

    /**
     * Response body for payment queries.
     */
    public record PaymentResponse(
            String id,
            String orderId,
            String customerId,
            BigDecimal amount,
            String status,
            String reason,
            String createdAt,
            String updatedAt
    ) {
        /**
         * Converts a {@link Payment} domain entity to the REST response record.
         *
         * @param payment the entity to convert
         * @return the response DTO
         */
        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.getId().toString(),
                    payment.getOrderId().toString(),
                    payment.getCustomerId(),
                    payment.getAmount(),
                    payment.getStatus().name(),
                    payment.getReason(),
                    payment.getCreatedAt().toString(),
                    payment.getUpdatedAt().toString()
            );
        }
    }
}
