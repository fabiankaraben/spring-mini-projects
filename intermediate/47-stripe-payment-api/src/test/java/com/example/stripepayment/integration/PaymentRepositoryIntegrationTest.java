package com.example.stripepayment.integration;

import com.example.stripepayment.domain.Payment;
import com.example.stripepayment.domain.PaymentStatus;
import com.example.stripepayment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PaymentRepository} using a real PostgreSQL database
 * managed by Testcontainers.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Testcontainers starts a real PostgreSQL Docker container before any test runs.</li>
 *   <li>{@link DynamicPropertySource} injects the container's JDBC URL, username, and
 *       password into Spring's datasource properties at runtime.</li>
 *   <li>{@link DataJpaTest} loads only the JPA slice of the Spring context (no web layer,
 *       no services) – making these tests faster than a full {@code @SpringBootTest}.</li>
 *   <li>Each test method runs inside a transaction that is rolled back after the test,
 *       ensuring test isolation without manual cleanup.</li>
 * </ol>
 *
 * <h2>Why @DataJpaTest + Testcontainers?</h2>
 * <p>Using {@code @DataJpaTest} with Testcontainers (instead of an in-memory H2 database)
 * tests against the real PostgreSQL engine, catching issues that H2 might silently ignore
 * (e.g., PostgreSQL-specific SQL features, enum type handling, unique constraint violations).
 *
 * <h2>Container lifecycle</h2>
 * <p>The static {@code @Container} field causes Testcontainers to start the PostgreSQL
 * container once for the entire test class, not once per test method. This is more efficient.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PaymentRepository integration tests (PostgreSQL via Testcontainers)")
class PaymentRepositoryIntegrationTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Container setup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PostgreSQL Testcontainer – starts once for the entire test class.
     *
     * <p>The {@code PostgreSQLContainer} class from the Testcontainers PostgreSQL module
     * provides convenience methods for retrieving the JDBC URL, username, and password
     * needed to configure the Spring datasource.
     */
    @Container
    static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("stripetest")
                    .withUsername("testuser")
                    .withPassword("testpass");

    /**
     * Overrides Spring Boot's datasource configuration at runtime to point at the
     * Testcontainers PostgreSQL instance instead of the default localhost:5432.
     *
     * @param registry the Spring property registry to populate
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test setup
    // ─────────────────────────────────────────────────────────────────────────

    /** The real repository wired with the real JPA context targeting the container. */
    @Autowired
    private PaymentRepository paymentRepository;

    /**
     * Clears all payment records before each test to ensure full isolation.
     * Although @DataJpaTest rolls back transactions, explicit deletion makes
     * test dependencies clearer and prevents ordering issues.
     */
    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save and findById
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should save a payment and retrieve it by local ID")
    void saveAndFindById() {
        // Arrange – create a payment entity
        Payment payment = new Payment("pi_integ_001", 2000L, "usd", PaymentStatus.PENDING, "secret_001");
        payment.setDescription("Integration test payment");

        // Act – persist via the repository
        Payment saved = paymentRepository.save(payment);

        // Assert – the saved entity has a generated ID and all fields persisted correctly
        assertThat(saved.getId()).isNotNull();
        Optional<Payment> found = paymentRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStripePaymentIntentId()).isEqualTo("pi_integ_001");
        assertThat(found.get().getAmount()).isEqualTo(2000L);
        assertThat(found.get().getCurrency()).isEqualTo("usd");
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(found.get().getDescription()).isEqualTo("Integration test payment");
        assertThat(found.get().getClientSecret()).isEqualTo("secret_001");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByStripePaymentIntentId
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should find a payment by Stripe PaymentIntent ID")
    void findByStripePaymentIntentId_found() {
        // Arrange
        Payment payment = new Payment("pi_stripe_abc", 5000L, "eur", PaymentStatus.PENDING, "secret_abc");
        paymentRepository.save(payment);

        // Act
        Optional<Payment> found = paymentRepository.findByStripePaymentIntentId("pi_stripe_abc");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getStripePaymentIntentId()).isEqualTo("pi_stripe_abc");
        assertThat(found.get().getAmount()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("should return empty Optional when Stripe ID does not exist")
    void findByStripePaymentIntentId_notFound() {
        // Act
        Optional<Payment> found = paymentRepository.findByStripePaymentIntentId("pi_nonexistent");

        // Assert
        assertThat(found).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should find all payments with a specific status")
    void findByStatus_returnsMatchingPayments() {
        // Arrange – save payments with different statuses
        paymentRepository.save(new Payment("pi_s1", 1000L, "usd", PaymentStatus.PENDING, "sec1"));
        paymentRepository.save(new Payment("pi_s2", 2000L, "usd", PaymentStatus.SUCCEEDED, "sec2"));
        paymentRepository.save(new Payment("pi_s3", 3000L, "usd", PaymentStatus.PENDING, "sec3"));
        paymentRepository.save(new Payment("pi_s4", 4000L, "usd", PaymentStatus.CANCELED, "sec4"));

        // Act – find only PENDING payments
        List<Payment> pendingPayments = paymentRepository.findByStatus(PaymentStatus.PENDING);

        // Assert – only the 2 PENDING records are returned
        assertThat(pendingPayments).hasSize(2);
        assertThat(pendingPayments)
                .extracting(Payment::getStripePaymentIntentId)
                .containsExactlyInAnyOrder("pi_s1", "pi_s3");
    }

    @Test
    @DisplayName("should return empty list when no payments match the status")
    void findByStatus_noMatches() {
        // Arrange – save only PENDING payments
        paymentRepository.save(new Payment("pi_only", 1000L, "usd", PaymentStatus.PENDING, "sec_only"));

        // Act
        List<Payment> failedPayments = paymentRepository.findByStatus(PaymentStatus.FAILED);

        // Assert – no FAILED payments exist
        assertThat(failedPayments).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // existsByStripePaymentIntentId
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return true when a payment with the Stripe ID exists")
    void existsByStripePaymentIntentId_exists() {
        // Arrange
        paymentRepository.save(new Payment("pi_exists", 1000L, "usd", PaymentStatus.PENDING, "sec"));

        // Act & Assert
        assertThat(paymentRepository.existsByStripePaymentIntentId("pi_exists")).isTrue();
    }

    @Test
    @DisplayName("should return false when no payment with the Stripe ID exists")
    void existsByStripePaymentIntentId_doesNotExist() {
        // Act & Assert – no records in the database
        assertThat(paymentRepository.existsByStripePaymentIntentId("pi_ghost")).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status update
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should update payment status correctly")
    void updatePaymentStatus() {
        // Arrange – save a PENDING payment
        Payment payment = new Payment("pi_update", 3000L, "gbp", PaymentStatus.PENDING, "sec_update");
        Payment saved = paymentRepository.save(payment);

        // Act – update status to SUCCEEDED
        saved.setStatus(PaymentStatus.SUCCEEDED);
        Payment updated = paymentRepository.save(saved);

        // Assert – the status is persisted in the database
        Optional<Payment> reloaded = paymentRepository.findById(updated.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findAll
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return all saved payments")
    void findAll_returnsAllPayments() {
        // Arrange – save three payments
        paymentRepository.save(new Payment("pi_all1", 100L, "usd", PaymentStatus.PENDING, "s1"));
        paymentRepository.save(new Payment("pi_all2", 200L, "eur", PaymentStatus.SUCCEEDED, "s2"));
        paymentRepository.save(new Payment("pi_all3", 300L, "gbp", PaymentStatus.CANCELED, "s3"));

        // Act
        List<Payment> all = paymentRepository.findAll();

        // Assert
        assertThat(all).hasSize(3);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // @PrePersist lifecycle callback
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should automatically set createdAt and updatedAt on persist")
    void prePersist_setsTimestamps() {
        // Arrange & Act – save a payment (timestamps set by @PrePersist)
        Payment payment = new Payment("pi_ts", 1000L, "usd", PaymentStatus.PENDING, "sec_ts");
        Payment saved = paymentRepository.save(payment);

        // Assert – both timestamps are populated after persist
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        // createdAt and updatedAt should be equal right after creation
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
    }
}
