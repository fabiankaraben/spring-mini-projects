package com.example.twiliosmssender.integration;

import com.example.twiliosmssender.domain.SmsMessage;
import com.example.twiliosmssender.domain.SmsStatus;
import com.example.twiliosmssender.repository.SmsMessageRepository;
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
 * Integration tests for {@link SmsMessageRepository} using a real PostgreSQL database
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
 * tests against the real PostgreSQL engine, catching PostgreSQL-specific issues that H2
 * might silently ignore (e.g., column length constraints, enum handling, unique violations).
 *
 * <h2>Container lifecycle</h2>
 * <p>The static {@code @Container} field causes Testcontainers to start the PostgreSQL
 * container once for the entire test class, not once per test method. This is more efficient.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("SmsMessageRepository integration tests (PostgreSQL via Testcontainers)")
class SmsMessageRepositoryIntegrationTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Container setup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PostgreSQL Testcontainer – starts once for the entire test class.
     *
     * <p>The {@code PostgreSQLContainer} class provides convenience methods for
     * retrieving the JDBC URL, username, and password used to configure Spring's
     * datasource at runtime via {@link #configureDataSource(DynamicPropertyRegistry)}.
     */
    @Container
    static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("twiliosmstest")
                    .withUsername("testuser")
                    .withPassword("testpass");

    /**
     * Overrides Spring Boot's datasource configuration at runtime to point at the
     * Testcontainers PostgreSQL instance instead of the default localhost:5432.
     *
     * <p>This method is called by Spring before the application context is started,
     * so the container URL is available before any beans are initialized.
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
    private SmsMessageRepository smsMessageRepository;

    /**
     * Clears all SMS records before each test to ensure full isolation.
     * Although {@code @DataJpaTest} rolls back transactions, explicit deletion
     * makes test dependencies clearer and prevents ordering issues.
     */
    @BeforeEach
    void setUp() {
        smsMessageRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save and findById
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should save a message and retrieve it by local ID")
    void saveAndFindById() {
        // Arrange – create a new SMS message entity
        SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Hello!", SmsStatus.QUEUED);
        msg.setTwilioSid("SMxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

        // Act – persist via the repository
        SmsMessage saved = smsMessageRepository.save(msg);

        // Assert – the saved entity has a generated ID and all fields are persisted correctly
        assertThat(saved.getId()).isNotNull();
        Optional<SmsMessage> found = smsMessageRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getToNumber()).isEqualTo("+15551234567");
        assertThat(found.get().getFromNumber()).isEqualTo("+15559876543");
        assertThat(found.get().getBody()).isEqualTo("Hello!");
        assertThat(found.get().getStatus()).isEqualTo(SmsStatus.QUEUED);
        assertThat(found.get().getTwilioSid()).isEqualTo("SMxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByTwilioSid
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should find a message by its Twilio SID")
    void findByTwilioSid_found() {
        // Arrange – save a message with a known Twilio SID
        SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Test", SmsStatus.SENT);
        msg.setTwilioSid("SMabc123test");
        smsMessageRepository.save(msg);

        // Act
        Optional<SmsMessage> found = smsMessageRepository.findByTwilioSid("SMabc123test");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getTwilioSid()).isEqualTo("SMabc123test");
        assertThat(found.get().getStatus()).isEqualTo(SmsStatus.SENT);
    }

    @Test
    @DisplayName("should return empty Optional when Twilio SID does not exist")
    void findByTwilioSid_notFound() {
        // Act
        Optional<SmsMessage> found = smsMessageRepository.findByTwilioSid("SMnonexistent");

        // Assert
        assertThat(found).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should find all messages with a specific status")
    void findByStatus_returnsMatchingMessages() {
        // Arrange – save messages with different statuses
        smsMessageRepository.save(new SmsMessage("+15551111111", "+15559876543", "A", SmsStatus.QUEUED));
        smsMessageRepository.save(new SmsMessage("+15552222222", "+15559876543", "B", SmsStatus.FAILED));
        smsMessageRepository.save(new SmsMessage("+15553333333", "+15559876543", "C", SmsStatus.QUEUED));
        smsMessageRepository.save(new SmsMessage("+15554444444", "+15559876543", "D", SmsStatus.DELIVERED));

        // Act – find only QUEUED messages
        List<SmsMessage> queuedMessages = smsMessageRepository.findByStatus(SmsStatus.QUEUED);

        // Assert – only the 2 QUEUED records are returned
        assertThat(queuedMessages).hasSize(2);
        assertThat(queuedMessages)
                .extracting(SmsMessage::getToNumber)
                .containsExactlyInAnyOrder("+15551111111", "+15553333333");
    }

    @Test
    @DisplayName("should return empty list when no messages match the status")
    void findByStatus_noMatches() {
        // Arrange – save only QUEUED messages
        smsMessageRepository.save(new SmsMessage("+15551111111", "+15559876543", "A", SmsStatus.QUEUED));

        // Act – look for DELIVERED (none exist)
        List<SmsMessage> delivered = smsMessageRepository.findByStatus(SmsStatus.DELIVERED);

        // Assert
        assertThat(delivered).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByToNumber
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should find all messages sent to a specific phone number")
    void findByToNumber_returnsAllMessages() {
        // Arrange – save two messages to the same number and one to a different number
        smsMessageRepository.save(new SmsMessage("+15551234567", "+15559876543", "First", SmsStatus.SENT));
        smsMessageRepository.save(new SmsMessage("+15551234567", "+15559876543", "Second", SmsStatus.QUEUED));
        smsMessageRepository.save(new SmsMessage("+15559999999", "+15559876543", "Other", SmsStatus.SENT));

        // Act
        List<SmsMessage> messages = smsMessageRepository.findByToNumber("+15551234567");

        // Assert – only the 2 messages to +15551234567 are returned
        assertThat(messages).hasSize(2);
        assertThat(messages)
                .extracting(SmsMessage::getBody)
                .containsExactlyInAnyOrder("First", "Second");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // existsByTwilioSid
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return true when a message with the Twilio SID exists")
    void existsByTwilioSid_exists() {
        // Arrange
        SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Test", SmsStatus.QUEUED);
        msg.setTwilioSid("SMexists123");
        smsMessageRepository.save(msg);

        // Act & Assert
        assertThat(smsMessageRepository.existsByTwilioSid("SMexists123")).isTrue();
    }

    @Test
    @DisplayName("should return false when no message with the Twilio SID exists")
    void existsByTwilioSid_doesNotExist() {
        // Act & Assert – no records in the database
        assertThat(smsMessageRepository.existsByTwilioSid("SMghost")).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status update
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should update message status correctly")
    void updateMessageStatus() {
        // Arrange – save a QUEUED message
        SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Update me", SmsStatus.QUEUED);
        SmsMessage saved = smsMessageRepository.save(msg);

        // Act – update status to DELIVERED
        saved.setStatus(SmsStatus.DELIVERED);
        SmsMessage updated = smsMessageRepository.save(saved);

        // Assert – the new status is persisted
        Optional<SmsMessage> reloaded = smsMessageRepository.findById(updated.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(SmsStatus.DELIVERED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error fields persistence
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should persist error code and error message for failed SMS")
    void saveErrorFields() {
        // Arrange – create a FAILED message with error info
        SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Test", SmsStatus.FAILED);
        msg.setErrorCode(21211);
        msg.setErrorMessage("The 'To' number is not a valid phone number.");
        SmsMessage saved = smsMessageRepository.save(msg);

        // Act
        Optional<SmsMessage> found = smsMessageRepository.findById(saved.getId());

        // Assert – error fields are persisted correctly
        assertThat(found).isPresent();
        assertThat(found.get().getErrorCode()).isEqualTo(21211);
        assertThat(found.get().getErrorMessage()).contains("not a valid phone number");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // @PrePersist lifecycle callback
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should automatically set createdAt and updatedAt on persist")
    void prePersist_setsTimestamps() {
        // Arrange & Act – save a message (timestamps set by @PrePersist)
        SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Timestamp test", SmsStatus.QUEUED);
        SmsMessage saved = smsMessageRepository.save(msg);

        // Assert – both timestamps are populated after persist
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        // On initial persist, createdAt and updatedAt should be equal
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findAll
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return all saved messages")
    void findAll_returnsAllMessages() {
        // Arrange – save three messages
        smsMessageRepository.save(new SmsMessage("+15551111111", "+15559876543", "A", SmsStatus.QUEUED));
        smsMessageRepository.save(new SmsMessage("+15552222222", "+15559876543", "B", SmsStatus.SENT));
        smsMessageRepository.save(new SmsMessage("+15553333333", "+15559876543", "C", SmsStatus.FAILED));

        // Act
        List<SmsMessage> all = smsMessageRepository.findAll();

        // Assert
        assertThat(all).hasSize(3);
    }
}
