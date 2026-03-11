package com.example.twiliosmssender.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link SmsMessage} domain entity.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>The constructor sets fields correctly.</li>
 *   <li>The {@code @PrePersist} lifecycle callback sets timestamps.</li>
 *   <li>The {@code @PreUpdate} lifecycle callback updates the {@code updatedAt} timestamp.</li>
 *   <li>Getters and setters behave as expected.</li>
 * </ul>
 *
 * <p>No Spring context is needed — these are pure Java unit tests using JUnit 5 + AssertJ.
 */
@DisplayName("SmsMessage domain unit tests")
class SmsMessageTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should set all fields via the parameterized constructor")
        void constructor_setsAllFields() {
            // Act – create a new entity using the parameterized constructor
            SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Hello!", SmsStatus.QUEUED);

            // Assert – all fields are set correctly
            assertThat(msg.getToNumber()).isEqualTo("+15551234567");
            assertThat(msg.getFromNumber()).isEqualTo("+15559876543");
            assertThat(msg.getBody()).isEqualTo("Hello!");
            assertThat(msg.getStatus()).isEqualTo(SmsStatus.QUEUED);
        }

        @Test
        @DisplayName("should have null id before persistence")
        void constructor_idIsNullBeforePersistence() {
            // Arrange & Act
            SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Test", SmsStatus.QUEUED);

            // Assert – id is null until JPA assigns it during INSERT
            assertThat(msg.getId()).isNull();
        }

        @Test
        @DisplayName("should have null timestamps before persistence")
        void constructor_timestampsAreNullBeforePersistence() {
            // Arrange & Act
            SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Test", SmsStatus.QUEUED);

            // Assert – timestamps are set by @PrePersist, not by the constructor
            assertThat(msg.getCreatedAt()).isNull();
            assertThat(msg.getUpdatedAt()).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // @PrePersist lifecycle callback
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("@PrePersist lifecycle callback")
    class PrePersistTests {

        @Test
        @DisplayName("should set createdAt and updatedAt when prePersist is called")
        void prePersist_setsTimestamps() throws Exception {
            // Arrange
            SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Test", SmsStatus.QUEUED);

            // Act – invoke the @PrePersist method via reflection (normally called by JPA)
            invokePrePersist(msg);

            // Assert – both timestamps are set and are close to "now"
            assertThat(msg.getCreatedAt()).isNotNull();
            assertThat(msg.getUpdatedAt()).isNotNull();
            // Verify they are very close to the current time (within 2 seconds)
            assertThat(msg.getCreatedAt()).isBetween(
                    Instant.now().minusSeconds(2), Instant.now().plusSeconds(2));
        }

        @Test
        @DisplayName("should set createdAt and updatedAt to the same value on initial persist")
        void prePersist_createdAtEqualsUpdatedAt() throws Exception {
            // Arrange
            SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Test", SmsStatus.QUEUED);

            // Act
            invokePrePersist(msg);

            // Assert – on initial persist, both timestamps should be equal
            assertThat(msg.getCreatedAt()).isEqualTo(msg.getUpdatedAt());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // @PreUpdate lifecycle callback
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("@PreUpdate lifecycle callback")
    class PreUpdateTests {

        @Test
        @DisplayName("should update updatedAt but leave createdAt unchanged on preUpdate")
        void preUpdate_updatesOnlyUpdatedAt() throws Exception {
            // Arrange – simulate a persisted entity with known timestamps
            SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Test", SmsStatus.QUEUED);
            invokePrePersist(msg);
            Instant originalCreatedAt = msg.getCreatedAt();
            Instant originalUpdatedAt = msg.getUpdatedAt();

            // Wait a tiny bit to ensure updatedAt will differ from createdAt
            Thread.sleep(5);

            // Act – invoke @PreUpdate (normally called by JPA on UPDATE)
            invokePreUpdate(msg);

            // Assert – createdAt is unchanged, updatedAt is refreshed
            assertThat(msg.getCreatedAt()).isEqualTo(originalCreatedAt);
            assertThat(msg.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Setters tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test
        @DisplayName("should update status via setStatus")
        void setStatus_updatesStatus() {
            // Arrange
            SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Test", SmsStatus.QUEUED);

            // Act
            msg.setStatus(SmsStatus.DELIVERED);

            // Assert
            assertThat(msg.getStatus()).isEqualTo(SmsStatus.DELIVERED);
        }

        @Test
        @DisplayName("should set twilioSid via setTwilioSid")
        void setTwilioSid_setsValue() {
            // Arrange
            SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Test", SmsStatus.QUEUED);

            // Act
            msg.setTwilioSid("SMxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

            // Assert
            assertThat(msg.getTwilioSid()).isEqualTo("SMxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        }

        @Test
        @DisplayName("should set error fields via setters")
        void setErrorFields_setsValues() {
            // Arrange
            SmsMessage msg = new SmsMessage("+15551234567", "+15559876543", "Test", SmsStatus.FAILED);

            // Act
            msg.setErrorCode(21211);
            msg.setErrorMessage("The 'To' number +15551234567 is not a valid phone number.");

            // Assert
            assertThat(msg.getErrorCode()).isEqualTo(21211);
            assertThat(msg.getErrorMessage()).contains("not a valid phone number");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers – invoke lifecycle callbacks via reflection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Invokes the package-private {@code prePersist()} method via reflection.
     * In production code, JPA calls this automatically before each INSERT.
     */
    private void invokePrePersist(SmsMessage msg) throws Exception {
        var method = SmsMessage.class.getDeclaredMethod("prePersist");
        method.setAccessible(true);
        method.invoke(msg);
    }

    /**
     * Invokes the package-private {@code preUpdate()} method via reflection.
     * In production code, JPA calls this automatically before each UPDATE.
     */
    private void invokePreUpdate(SmsMessage msg) throws Exception {
        var method = SmsMessage.class.getDeclaredMethod("preUpdate");
        method.setAccessible(true);
        method.invoke(msg);
    }
}
