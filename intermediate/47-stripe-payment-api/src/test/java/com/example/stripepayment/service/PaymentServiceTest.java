package com.example.stripepayment.service;

import com.example.stripepayment.domain.Payment;
import com.example.stripepayment.domain.PaymentStatus;
import com.example.stripepayment.dto.CreatePaymentRequest;
import com.example.stripepayment.dto.PaymentResponse;
import com.example.stripepayment.exception.PaymentNotFoundException;
import com.example.stripepayment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentService}.
 *
 * <p>These tests use Mockito to replace the real {@link PaymentRepository} with a mock,
 * so no database or Docker is required. The Stripe API calls (which live inside
 * static SDK methods) are tested via the {@link PaymentService#mapStripeStatus(String)}
 * helper directly.
 *
 * <p>Integration with the actual Stripe API is tested separately in
 * {@code PaymentServiceIntegrationTest}, where a real PostgreSQL container is used.
 *
 * <p>Key patterns demonstrated:
 * <ul>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} for lightweight Mockito injection.</li>
 *   <li>Constructing the service manually so we control which repository mock is injected.</li>
 *   <li>Using {@code @Nested} to group related tests by method under test.</li>
 *   <li>Verifying that exceptions are thrown correctly when records are not found.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService unit tests")
class PaymentServiceTest {

    /** Mocked repository – no real database connection. */
    @Mock
    private PaymentRepository paymentRepository;

    /** The service under test, constructed manually to inject the mock. */
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // Construct service with the mock repository (no Spring context needed)
        paymentService = new PaymentService(paymentRepository);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // mapStripeStatus (pure logic – no mocks needed)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("mapStripeStatus")
    class MapStripeStatusTests {

        @Test
        @DisplayName("should return SUCCEEDED for 'succeeded'")
        void mapStripeStatus_succeeded() {
            // Act & Assert – direct call to the package-private helper method
            assertThat(paymentService.mapStripeStatus("succeeded"))
                    .isEqualTo(PaymentStatus.SUCCEEDED);
        }

        @Test
        @DisplayName("should return CANCELED for 'canceled'")
        void mapStripeStatus_canceled() {
            assertThat(paymentService.mapStripeStatus("canceled"))
                    .isEqualTo(PaymentStatus.CANCELED);
        }

        @Test
        @DisplayName("should return FAILED for 'requires_payment_method'")
        void mapStripeStatus_requiresPaymentMethod() {
            // 'requires_payment_method' after a failed attempt means the card was declined
            assertThat(paymentService.mapStripeStatus("requires_payment_method"))
                    .isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("should return PENDING for 'requires_confirmation'")
        void mapStripeStatus_requiresConfirmation() {
            assertThat(paymentService.mapStripeStatus("requires_confirmation"))
                    .isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("should return PENDING for 'requires_action'")
        void mapStripeStatus_requiresAction() {
            assertThat(paymentService.mapStripeStatus("requires_action"))
                    .isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("should return PENDING for 'processing'")
        void mapStripeStatus_processing() {
            assertThat(paymentService.mapStripeStatus("processing"))
                    .isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("should return PENDING for any unknown status string")
        void mapStripeStatus_unknownStatus() {
            // Unknown statuses should safely default to PENDING
            assertThat(paymentService.mapStripeStatus("some_future_stripe_status"))
                    .isEqualTo(PaymentStatus.PENDING);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listPayments
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listPayments")
    class ListPaymentsTests {

        @Test
        @DisplayName("should return an empty list when there are no payments")
        void listPayments_empty() {
            // Arrange – repository returns empty list
            when(paymentRepository.findAll()).thenReturn(List.of());

            // Act
            List<PaymentResponse> result = paymentService.listPayments();

            // Assert
            assertThat(result).isEmpty();
            verify(paymentRepository).findAll();
        }

        @Test
        @DisplayName("should return mapped DTOs for all payments in the database")
        void listPayments_withResults() {
            // Arrange – create two Payment entities and stub the repository
            Payment p1 = buildPayment(1L, "pi_001", 1000L, "usd", PaymentStatus.SUCCEEDED);
            Payment p2 = buildPayment(2L, "pi_002", 5000L, "eur", PaymentStatus.PENDING);
            when(paymentRepository.findAll()).thenReturn(List.of(p1, p2));

            // Act
            List<PaymentResponse> result = paymentService.listPayments();

            // Assert – both records are returned and fields mapped correctly
            assertThat(result).hasSize(2);
            assertThat(result.get(0).stripePaymentIntentId()).isEqualTo("pi_001");
            assertThat(result.get(0).status()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(result.get(1).stripePaymentIntentId()).isEqualTo("pi_002");
            assertThat(result.get(1).status()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPaymentById
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPaymentById")
    class GetPaymentByIdTests {

        @Test
        @DisplayName("should return the payment DTO when the ID exists")
        void getPaymentById_found() {
            // Arrange
            Payment payment = buildPayment(42L, "pi_abc", 2000L, "usd", PaymentStatus.PENDING);
            when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));

            // Act
            PaymentResponse response = paymentService.getPaymentById(42L);

            // Assert – all fields are correctly mapped from entity to DTO
            assertThat(response.id()).isEqualTo(42L);
            assertThat(response.stripePaymentIntentId()).isEqualTo("pi_abc");
            assertThat(response.amount()).isEqualTo(2000L);
            assertThat(response.currency()).isEqualTo("usd");
            assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("should throw PaymentNotFoundException when the ID does not exist")
        void getPaymentById_notFound() {
            // Arrange – repository returns empty Optional
            when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> paymentService.getPaymentById(99L))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPaymentByStripeId
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPaymentByStripeId")
    class GetPaymentByStripeIdTests {

        @Test
        @DisplayName("should return the payment DTO when the Stripe ID exists")
        void getPaymentByStripeId_found() {
            // Arrange
            Payment payment = buildPayment(1L, "pi_stripe123", 3000L, "gbp", PaymentStatus.SUCCEEDED);
            when(paymentRepository.findByStripePaymentIntentId("pi_stripe123"))
                    .thenReturn(Optional.of(payment));

            // Act
            PaymentResponse response = paymentService.getPaymentByStripeId("pi_stripe123");

            // Assert
            assertThat(response.stripePaymentIntentId()).isEqualTo("pi_stripe123");
            assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        }

        @Test
        @DisplayName("should throw PaymentNotFoundException when the Stripe ID has no local record")
        void getPaymentByStripeId_notFound() {
            // Arrange
            when(paymentRepository.findByStripePaymentIntentId("pi_ghost"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> paymentService.getPaymentByStripeId("pi_ghost"))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining("pi_ghost");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // confirmPayment – repository-level behavior
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("confirmPayment – repository behavior")
    class ConfirmPaymentRepositoryTests {

        @Test
        @DisplayName("should throw PaymentNotFoundException when Stripe ID has no local record")
        void confirmPayment_notFound() {
            // Arrange – no local record exists for this Stripe ID
            when(paymentRepository.findByStripePaymentIntentId("pi_missing"))
                    .thenReturn(Optional.empty());

            // Act & Assert – service should throw before calling Stripe API
            assertThatThrownBy(() -> paymentService.confirmPayment("pi_missing"))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining("pi_missing");

            // Verify the repository was queried and nothing else was called
            verify(paymentRepository).findByStripePaymentIntentId("pi_missing");
            verify(paymentRepository, never()).save(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // cancelPayment – repository-level behavior
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelPayment – repository behavior")
    class CancelPaymentRepositoryTests {

        @Test
        @DisplayName("should throw PaymentNotFoundException when Stripe ID has no local record")
        void cancelPayment_notFound() {
            // Arrange
            when(paymentRepository.findByStripePaymentIntentId("pi_unknown"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> paymentService.cancelPayment("pi_unknown"))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining("pi_unknown");

            verify(paymentRepository).findByStripePaymentIntentId("pi_unknown");
            verify(paymentRepository, never()).save(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a {@link Payment} entity with a reflectively-set {@code id} field.
     *
     * <p>JPA entities usually have their ID set by the database during a persist
     * operation. In unit tests we never hit the database, so we build entities
     * manually and simulate an existing persisted record.
     */
    private Payment buildPayment(Long id, String stripeId, Long amount,
                                  String currency, PaymentStatus status) {
        Payment p = new Payment(stripeId, amount, currency, status, "secret_" + stripeId);
        // Use reflection to set the private id field (JPA-managed, no public setter)
        try {
            var field = Payment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id on Payment", e);
        }
        return p;
    }
}
