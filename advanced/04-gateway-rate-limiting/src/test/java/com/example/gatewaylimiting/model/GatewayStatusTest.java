package com.example.gatewaylimiting.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link GatewayStatus} record.
 *
 * <p>Plain JUnit 5 tests — no Spring context loaded. These run extremely fast
 * and verify the domain model's contract: correct field storage, equality
 * semantics, and the auto-generated {@code toString} method.
 */
@DisplayName("GatewayStatus — unit tests")
class GatewayStatusTest {

    /**
     * Verifies that the compact constructor stores all fields and the generated
     * accessor methods return the expected values.
     */
    @Test
    @DisplayName("Constructor stores all fields correctly")
    void constructorShouldStoreAllFields() {
        GatewayStatus status = new GatewayStatus(
                "gateway-rate-limiting",
                "UP",
                3,
                true,
                "IP Address"
        );

        assertThat(status.applicationName()).isEqualTo("gateway-rate-limiting");
        assertThat(status.status()).isEqualTo("UP");
        assertThat(status.totalRoutes()).isEqualTo(3);
        assertThat(status.rateLimitingEnabled()).isTrue();
        assertThat(status.keyResolverStrategy()).isEqualTo("IP Address");
    }

    /**
     * Verifies that two {@link GatewayStatus} instances with identical component
     * values are equal. Java records generate {@code equals()} automatically by
     * comparing all components structurally.
     */
    @Test
    @DisplayName("Two instances with same values are equal")
    void equalityWithSameValues() {
        GatewayStatus s1 = new GatewayStatus("app", "UP", 3, true, "IP Address");
        GatewayStatus s2 = new GatewayStatus("app", "UP", 3, true, "IP Address");

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    /**
     * Verifies that two instances with different status values are not equal.
     */
    @Test
    @DisplayName("Instances with different status values are not equal")
    void inequalityWithDifferentStatus() {
        GatewayStatus up   = new GatewayStatus("app", "UP",   3, true, "IP Address");
        GatewayStatus down = new GatewayStatus("app", "DOWN", 3, true, "IP Address");

        assertThat(up).isNotEqualTo(down);
    }

    /**
     * Verifies that the auto-generated {@code toString()} contains all field
     * values, which is useful for logging and debugging.
     */
    @Test
    @DisplayName("toString contains all field values")
    void toStringShouldContainAllValues() {
        GatewayStatus status = new GatewayStatus(
                "gateway-rate-limiting", "UP", 3, true, "IP Address");

        String str = status.toString();
        assertThat(str).contains("gateway-rate-limiting");
        assertThat(str).contains("UP");
        assertThat(str).contains("3");
        assertThat(str).contains("true");
        assertThat(str).contains("IP Address");
    }

    /**
     * Verifies the expected state of the gateway status when rate limiting is
     * enabled — the {@code rateLimitingEnabled} field must be {@code true}.
     */
    @Test
    @DisplayName("rateLimitingEnabled is true when rate limiting is active")
    void rateLimitingEnabledShouldBeTrueWhenActive() {
        GatewayStatus status = new GatewayStatus("app", "UP", 3, true, "IP Address");

        assertThat(status.rateLimitingEnabled()).isTrue();
    }
}
