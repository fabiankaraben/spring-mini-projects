package com.example.saga.payment.service;

import com.example.saga.payment.domain.Payment;
import com.example.saga.payment.domain.PaymentStatus;
import com.example.saga.payment.events.*;
import com.example.saga.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Core business logic for the Payment Service.
 *
 * <p>Payment simulation rule (deterministic, no external gateway needed):
 * <ul>
 *   <li>If the integer part of {@code totalPrice} is <strong>even</strong> → payment succeeds.</li>
 *   <li>If the integer part of {@code totalPrice} is <strong>odd</strong>  → payment fails
 *       ("Simulated card decline: odd total price").</li>
 * </ul>
 *
 * <p>Example:
 * <ul>
 *   <li>$30.00 → succeeds (30 is even)</li>
 *   <li>$29.99 → fails (29 is odd)</li>
 *   <li>$10.50 → succeeds (10 is even)</li>
 * </ul>
 *
 * <p>This deterministic rule lets callers predict the outcome from the request,
 * making integration tests straightforward without any mocking.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Kafka topic names as constants to prevent typos. */
    public static final String TOPIC_PAYMENT_PROCESSED = "payment.processed";
    public static final String TOPIC_PAYMENT_FAILED    = "payment.failed";

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentService(PaymentRepository paymentRepository,
                          KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Processes a payment for the given order.
     *
     * <ol>
     *   <li>Creates a {@link Payment} record in PENDING status.</li>
     *   <li>Applies the simulation rule to determine success or failure.</li>
     *   <li>Updates the Payment record accordingly.</li>
     *   <li>Publishes {@link PaymentProcessedEvent} or {@link PaymentFailedEvent} to Kafka.</li>
     * </ol>
     *
     * @param event the OrderCreatedEvent that triggered this payment
     */
    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        log.info("Processing payment for orderId={}, amount={}", event.orderId(), event.totalPrice());

        // Idempotency check: if we already processed this order, skip
        Optional<Payment> existing = paymentRepository.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            log.warn("Duplicate OrderCreatedEvent for orderId={} — skipping", event.orderId());
            return;
        }

        // Create payment record in PENDING state
        Payment payment = new Payment(event.orderId(), event.customerId(), event.totalPrice());
        payment = paymentRepository.save(payment);

        // Apply simulation rule: even integer part → success, odd → failure
        int integerPart = event.totalPrice().intValue();
        boolean shouldSucceed = (integerPart % 2 == 0);

        if (shouldSucceed) {
            // Happy path: mark payment as completed and publish success event
            payment.markCompleted();
            paymentRepository.save(payment);

            PaymentProcessedEvent processed = new PaymentProcessedEvent(
                    event.orderId(),
                    event.customerId(),
                    payment.getId(),
                    event.totalPrice(),
                    event.productId(),
                    event.quantity()
            );
            kafkaTemplate.send(TOPIC_PAYMENT_PROCESSED, event.orderId().toString(), processed);
            log.info("Payment COMPLETED for orderId={}, paymentId={}", event.orderId(), payment.getId());

        } else {
            // Failure path: mark payment as failed and publish failure event
            String reason = "Simulated card decline: odd total price (" + integerPart + ")";
            payment.markFailed(reason);
            paymentRepository.save(payment);

            PaymentFailedEvent failed = new PaymentFailedEvent(
                    event.orderId(), event.customerId(), reason);
            kafkaTemplate.send(TOPIC_PAYMENT_FAILED, event.orderId().toString(), failed);
            log.info("Payment FAILED for orderId={}: {}", event.orderId(), reason);
        }
    }

    /**
     * Processes a compensation refund for a previously charged payment.
     *
     * <p>Called when the Inventory Service fails and the Order Service
     * publishes a {@link PaymentRefundEvent} to reverse the charge.
     *
     * @param event the refund instruction from the Order Service
     */
    @Transactional
    public void processRefund(PaymentRefundEvent event) {
        log.info("Processing refund for orderId={}, paymentId={}", event.orderId(), event.paymentId());

        paymentRepository.findById(event.paymentId()).ifPresentOrElse(payment -> {
            if (payment.getStatus() != PaymentStatus.COMPLETED) {
                log.warn("Cannot refund payment {} — current status is {}", event.paymentId(), payment.getStatus());
                return;
            }
            payment.markRefunded(event.reason());
            paymentRepository.save(payment);
            log.info("Payment {} REFUNDED for orderId={}", event.paymentId(), event.orderId());
        }, () -> log.error("Payment not found for refund: paymentId={}", event.paymentId()));
    }

    /**
     * Finds a payment by its associated order ID.
     *
     * @param orderId the saga order identifier
     * @return an Optional containing the payment if found
     */
    @Transactional(readOnly = true)
    public Optional<Payment> findByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId);
    }
}
