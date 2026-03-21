package com.example.eurekadiscoveryclient.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link RegistrationStatus} domain record.
 *
 * <p>These tests verify the derived methods {@code isFullyActive()} and
 * {@code hasDiscoveredServices()}. No Spring context is needed.
 */
@DisplayName("RegistrationStatus")
class RegistrationStatusTest {

    // =========================================================================
    // isFullyActive()
    // =========================================================================

    @Nested
    @DisplayName("isFullyActive()")
    class IsFullyActive {

        @Test
        @DisplayName("returns true when both registration and fetch are enabled")
        void shouldReturnTrueWhenBothEnabled() {
            // Arrange: both registration and registry fetch are on
            RegistrationStatus status = new RegistrationStatus("my-svc", 2, true, true);

            // Assert: fully active
            assertThat(status.isFullyActive()).isTrue();
        }

        @Test
        @DisplayName("returns false when registration is disabled")
        void shouldReturnFalseWhenRegistrationDisabled() {
            RegistrationStatus status = new RegistrationStatus("my-svc", 0, false, true);

            assertThat(status.isFullyActive()).isFalse();
        }

        @Test
        @DisplayName("returns false when fetch is disabled")
        void shouldReturnFalseWhenFetchDisabled() {
            RegistrationStatus status = new RegistrationStatus("my-svc", 0, true, false);

            assertThat(status.isFullyActive()).isFalse();
        }

        @Test
        @DisplayName("returns false when both registration and fetch are disabled")
        void shouldReturnFalseWhenBothDisabled() {
            RegistrationStatus status = new RegistrationStatus("my-svc", 0, false, false);

            assertThat(status.isFullyActive()).isFalse();
        }
    }

    // =========================================================================
    // hasDiscoveredServices()
    // =========================================================================

    @Nested
    @DisplayName("hasDiscoveredServices()")
    class HasDiscoveredServices {

        @Test
        @DisplayName("returns true when at least one service is discovered")
        void shouldReturnTrueWhenServicesDiscovered() {
            RegistrationStatus status = new RegistrationStatus("my-svc", 3, true, true);

            assertThat(status.hasDiscoveredServices()).isTrue();
        }

        @Test
        @DisplayName("returns false when no services are discovered")
        void shouldReturnFalseWhenNoServicesDiscovered() {
            RegistrationStatus status = new RegistrationStatus("my-svc", 0, false, false);

            assertThat(status.hasDiscoveredServices()).isFalse();
        }

        @Test
        @DisplayName("returns true even with exactly one discovered service")
        void shouldReturnTrueWithExactlyOneService() {
            RegistrationStatus status = new RegistrationStatus("my-svc", 1, true, true);

            assertThat(status.hasDiscoveredServices()).isTrue();
        }
    }

    // =========================================================================
    // Record fields
    // =========================================================================

    @Nested
    @DisplayName("record fields")
    class RecordFields {

        @Test
        @DisplayName("stores applicationName, registeredServices, registrationEnabled, fetchEnabled correctly")
        void shouldStoreAllFieldsCorrectly() {
            RegistrationStatus status = new RegistrationStatus("product-service", 5, true, false);

            assertThat(status.applicationName()).isEqualTo("product-service");
            assertThat(status.registeredServices()).isEqualTo(5);
            assertThat(status.registrationEnabled()).isTrue();
            assertThat(status.fetchEnabled()).isFalse();
        }
    }
}
