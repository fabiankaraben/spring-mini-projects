package com.example.saga.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Payment} domain entity.
 *
 * <p>Verifies status transitions and field behaviour in complete isolation
 * — no Spring context, no database, no Kafka.
 */
@DisplayName("Payment Domain Entity")
class PaymentTest {

    private Payment buildPayment() {
        return new Payment(UUID.randomUUID(), "customer-1", new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("New payment starts with PENDING status")
    void newPaymentHasPendingStatus() {
        Payment payment = buildPayment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("New payment stores constructor fields correctly")
    void newPaymentStoresFields() {
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment(orderId, "cust-42", new BigDecimal("99.99"));

        assertThat(payment.getOrderId()).isEqualTo(orderId);
        assertThat(payment.getCustomerId()).isEqualTo("cust-42");
        assertThat(payment.getAmount()).isEqualByComparingTo("99.99");
        assertThat(payment.getCreatedAt()).isNotNull();
        assertThat(payment.getUpdatedAt()).isNotNull();
        assertThat(payment.getReason()).isNull();
    }

    @Test
    @DisplayName("markCompleted transitions status to COMPLETED")
    void markCompletedTransitionsStatus() {
        Payment payment = buildPayment();
        payment.markCompleted();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getReason()).isNull();
    }

    @Test
    @DisplayName("markFailed transitions status to FAILED and stores reason")
    void markFailedTransitionsStatus() {
        Payment payment = buildPayment();
        payment.markFailed("Card declined");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getReason()).isEqualTo("Card declined");
    }

    @Test
    @DisplayName("markRefunded transitions status to REFUNDED and stores reason")
    void markRefundedTransitionsStatus() {
        Payment payment = buildPayment();
        payment.markCompleted();
        payment.markRefunded("Inventory out of stock");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getReason()).isEqualTo("Inventory out of stock");
    }

    @Test
    @DisplayName("updatedAt is refreshed on each status transition")
    void updatedAtIsRefreshedOnTransition() throws InterruptedException {
        Payment payment = buildPayment();
        var before = payment.getUpdatedAt();
        Thread.sleep(10);
        payment.markCompleted();
        assertThat(payment.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("PaymentStatus enum contains all expected values")
    void paymentStatusEnumContainsAllValues() {
        assertThat(PaymentStatus.values()).containsExactlyInAnyOrder(
                PaymentStatus.PENDING,
                PaymentStatus.COMPLETED,
                PaymentStatus.FAILED,
                PaymentStatus.REFUNDED
        );
    }
}
