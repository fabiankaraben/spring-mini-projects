package com.example.stripepayment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Payment} domain entity.
 *
 * <p>These tests verify the business logic and invariants of the Payment entity
 * without involving any database or Spring context.
 *
 * <p>Key aspects tested:
 * <ul>
 *   <li>Constructor correctly sets all fields.</li>
 *   <li>Status can be changed via the setter (lifecycle updates).</li>
 *   <li>Description setter works correctly for optional field.</li>
 *   <li>{@link PaymentStatus} enum values represent valid payment states.</li>
 * </ul>
 */
@DisplayName("Payment domain entity unit tests")
class PaymentTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor should correctly set all required fields")
    void constructor_setsAllFields() {
        // Arrange & Act
        Payment payment = new Payment(
                "pi_test_12345",
                2000L,
                "usd",
                PaymentStatus.PENDING,
                "pi_test_12345_secret_abc"
        );

        // Assert – all fields are set correctly
        assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_test_12345");
        assertThat(payment.getAmount()).isEqualTo(2000L);
        assertThat(payment.getCurrency()).isEqualTo("usd");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getClientSecret()).isEqualTo("pi_test_12345_secret_abc");
        // id is null until persisted by JPA
        assertThat(payment.getId()).isNull();
        // description is null until explicitly set
        assertThat(payment.getDescription()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status transitions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setStatus should update the payment status")
    void setStatus_updatesStatus() {
        // Arrange
        Payment payment = new Payment("pi_001", 1000L, "usd", PaymentStatus.PENDING, "secret_001");

        // Act – simulate a status transition from PENDING to SUCCEEDED
        payment.setStatus(PaymentStatus.SUCCEEDED);

        // Assert
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("setStatus should allow transition to CANCELED")
    void setStatus_toCanceled() {
        // Arrange
        Payment payment = new Payment("pi_002", 500L, "eur", PaymentStatus.PENDING, "secret_002");

        // Act
        payment.setStatus(PaymentStatus.CANCELED);

        // Assert
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("setStatus should allow transition to FAILED")
    void setStatus_toFailed() {
        // Arrange
        Payment payment = new Payment("pi_003", 750L, "gbp", PaymentStatus.PENDING, "secret_003");

        // Act
        payment.setStatus(PaymentStatus.FAILED);

        // Assert
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Optional fields
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setDescription should store the description text")
    void setDescription_storesValue() {
        // Arrange
        Payment payment = new Payment("pi_004", 3000L, "usd", PaymentStatus.PENDING, "secret_004");

        // Act
        payment.setDescription("Order #9999 – Spring Boot Course");

        // Assert
        assertThat(payment.getDescription()).isEqualTo("Order #9999 – Spring Boot Course");
    }

    @Test
    @DisplayName("description should remain null when not set")
    void description_isNullByDefault() {
        // Arrange & Act
        Payment payment = new Payment("pi_005", 100L, "usd", PaymentStatus.PENDING, "secret_005");

        // Assert – description is an optional field, null is valid
        assertThat(payment.getDescription()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PaymentStatus enum
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PaymentStatus enum should have exactly 4 values")
    void paymentStatus_hasFourValues() {
        // This test ensures no accidental addition or removal of status values,
        // which could break the Stripe status mapping logic in PaymentService.
        assertThat(PaymentStatus.values()).hasSize(4);
    }

    @Test
    @DisplayName("PaymentStatus enum should contain all expected lifecycle states")
    void paymentStatus_containsExpectedValues() {
        // Assert that all required business states are present
        assertThat(PaymentStatus.values()).containsExactlyInAnyOrder(
                PaymentStatus.PENDING,
                PaymentStatus.SUCCEEDED,
                PaymentStatus.CANCELED,
                PaymentStatus.FAILED
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // toString
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString should include key fields for debugging")
    void toString_includesKeyFields() {
        // Arrange
        Payment payment = new Payment("pi_tostring", 1234L, "usd", PaymentStatus.PENDING, "secret");

        // Act
        String result = payment.toString();

        // Assert – important fields are present in the string representation
        assertThat(result)
                .contains("pi_tostring")
                .contains("1234")
                .contains("usd")
                .contains("PENDING");
    }
}
