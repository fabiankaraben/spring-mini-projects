package com.example.saga.payment.service;

import com.example.saga.payment.domain.Payment;
import com.example.saga.payment.domain.PaymentStatus;
import com.example.saga.payment.events.*;
import com.example.saga.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentService}.
 *
 * <p>Verifies the payment simulation rule, success/failure paths, and
 * the refund (compensation) path, all in isolation with Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService unit tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, kafkaTemplate);
    }

    // =========================================================================
    // processPayment — happy path (even total price)
    // =========================================================================

    @Test
    @DisplayName("processPayment succeeds and publishes PaymentProcessedEvent for even total price")
    void processPaymentSucceedsForEvenTotal() {
        // $30.00 — integer part 30 is EVEN → should succeed
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), "cust-1", "prod-A", 2, new BigDecimal("30.00"));

        when(paymentRepository.findByOrderId(event.orderId())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            // Inject a UUID id via reflection to simulate JPA
            try {
                var field = Payment.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(p, UUID.randomUUID());
            } catch (Exception e) { throw new RuntimeException(e); }
            return p;
        });

        paymentService.processPayment(event);

        // Verify PaymentProcessedEvent was published
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(PaymentService.TOPIC_PAYMENT_PROCESSED), anyString(), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PaymentProcessedEvent.class);

        PaymentProcessedEvent published = (PaymentProcessedEvent) captor.getValue();
        assertThat(published.orderId()).isEqualTo(event.orderId());
        assertThat(published.amountCharged()).isEqualByComparingTo("30.00");
        assertThat(published.productId()).isEqualTo(event.productId());
        assertThat(published.quantity()).isEqualTo(event.quantity());

        // No failure event published
        verify(kafkaTemplate, never()).send(eq(PaymentService.TOPIC_PAYMENT_FAILED), anyString(), any());
    }

    // =========================================================================
    // processPayment — failure path (odd total price)
    // =========================================================================

    @Test
    @DisplayName("processPayment fails and publishes PaymentFailedEvent for odd total price")
    void processPaymentFailsForOddTotal() {
        // $29.99 — integer part 29 is ODD → should fail
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), "cust-2", "prod-B", 1, new BigDecimal("29.99"));

        when(paymentRepository.findByOrderId(event.orderId())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.processPayment(event);

        // Verify PaymentFailedEvent was published
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(PaymentService.TOPIC_PAYMENT_FAILED), anyString(), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PaymentFailedEvent.class);

        PaymentFailedEvent published = (PaymentFailedEvent) captor.getValue();
        assertThat(published.orderId()).isEqualTo(event.orderId());
        assertThat(published.reason()).contains("odd total price");

        // No success event published
        verify(kafkaTemplate, never()).send(eq(PaymentService.TOPIC_PAYMENT_PROCESSED), anyString(), any());
    }

    // =========================================================================
    // processPayment — idempotency
    // =========================================================================

    @Test
    @DisplayName("processPayment is idempotent — ignores duplicate OrderCreatedEvent")
    void processPaymentIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, "cust-3", "prod-C", 1, new BigDecimal("10.00"));

        // Simulate: payment already exists for this order
        Payment existing = new Payment(orderId, "cust-3", new BigDecimal("10.00"));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        paymentService.processPayment(event);

        // No new payment saved, no Kafka event published
        verify(paymentRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    // =========================================================================
    // processRefund
    // =========================================================================

    @Test
    @DisplayName("processRefund marks payment as REFUNDED when payment is COMPLETED")
    void processRefundUpdatesPaymentToRefunded() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Payment payment = new Payment(orderId, "cust-4", new BigDecimal("30.00"));
        payment.markCompleted();
        // Set id via reflection
        try {
            var field = Payment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(payment, paymentId);
        } catch (Exception e) { throw new RuntimeException(e); }

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentRefundEvent refundEvent = new PaymentRefundEvent(
                orderId, "cust-4", paymentId, new BigDecimal("30.00"), "Inventory failed");

        paymentService.processRefund(refundEvent);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getReason()).isEqualTo("Inventory failed");
        verify(paymentRepository).save(payment);
    }

    @Test
    @DisplayName("processRefund ignores non-COMPLETED payments")
    void processRefundIgnoresNonCompletedPayment() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment(UUID.randomUUID(), "cust-5", new BigDecimal("30.00"));
        // Payment is still PENDING — cannot be refunded
        try {
            var field = Payment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(payment, paymentId);
        } catch (Exception e) { throw new RuntimeException(e); }

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentRefundEvent refundEvent = new PaymentRefundEvent(
                UUID.randomUUID(), "cust-5", paymentId, new BigDecimal("30.00"), "reason");

        paymentService.processRefund(refundEvent);

        // Status unchanged
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(any());
    }

    // =========================================================================
    // Simulation rule edge cases
    // =========================================================================

    @Test
    @DisplayName("processPayment succeeds for $0.00 (zero is even)")
    void processPaymentSucceedsForZeroTotal() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), "cust-6", "prod-D", 1, new BigDecimal("0.00"));

        when(paymentRepository.findByOrderId(event.orderId())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            try {
                var f = Payment.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(p, UUID.randomUUID());
            } catch (Exception e) { throw new RuntimeException(e); }
            return p;
        });

        paymentService.processPayment(event);

        verify(kafkaTemplate).send(eq(PaymentService.TOPIC_PAYMENT_PROCESSED), anyString(), any());
    }

    @Test
    @DisplayName("processPayment fails for $101.00 (101 is odd)")
    void processPaymentFailsFor101Total() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), "cust-7", "prod-E", 1, new BigDecimal("101.00"));

        when(paymentRepository.findByOrderId(event.orderId())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.processPayment(event);

        verify(kafkaTemplate).send(eq(PaymentService.TOPIC_PAYMENT_FAILED), anyString(), any());
    }
}
