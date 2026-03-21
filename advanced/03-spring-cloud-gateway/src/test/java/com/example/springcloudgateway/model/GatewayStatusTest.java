package com.example.springcloudgateway.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link GatewayStatus} record.
 *
 * <p>These tests verify the value-based semantics of the record: correct
 * storage of all fields, equality, and the toString representation. They are
 * pure unit tests with no Spring context required.
 */
@DisplayName("GatewayStatus record — unit tests")
class GatewayStatusTest {

    private static final Instant FIXED_TIME = Instant.parse("2025-01-01T12:00:00Z");

    // =========================================================================
    // Constructor and accessor tests
    // =========================================================================

    /**
     * Verifies that all fields passed to the canonical constructor are
     * retrievable via accessor methods.
     */
    @Test
    @DisplayName("Constructor stores all fields accessible via accessors")
    void constructorShouldStoreAllFields() {
        // Arrange
        List<String> routeIds = List.of("product-service-route", "order-service-route");

        // Act
        GatewayStatus status = new GatewayStatus(
                "spring-cloud-gateway",
                "UP",
                FIXED_TIME,
                2,
                routeIds
        );

        // Assert: each accessor returns the value supplied to the constructor.
        assertThat(status.applicationName()).isEqualTo("spring-cloud-gateway");
        assertThat(status.status()).isEqualTo("UP");
        assertThat(status.timestamp()).isEqualTo(FIXED_TIME);
        assertThat(status.totalRoutes()).isEqualTo(2);
        assertThat(status.routeIds()).isEqualTo(routeIds);
    }

    /**
     * Verifies that {@code totalRoutes} and the size of {@code routeIds} are
     * consistent when the caller constructs the record correctly.
     */
    @Test
    @DisplayName("totalRoutes matches the size of routeIds list")
    void totalRoutesShouldMatchRouteIdsSize() {
        List<String> routeIds = List.of("route-a", "route-b", "route-c");

        GatewayStatus status = new GatewayStatus(
                "spring-cloud-gateway",
                "UP",
                FIXED_TIME,
                routeIds.size(),   // caller passes the correct count
                routeIds
        );

        // The record itself does not enforce consistency — we document the
        // expected invariant through this test.
        assertThat(status.totalRoutes()).isEqualTo(status.routeIds().size());
    }

    /**
     * Verifies that a status with zero routes can be created and that
     * {@code totalRoutes} is 0 and {@code routeIds} is empty.
     */
    @Test
    @DisplayName("GatewayStatus with zero routes has empty routeIds and totalRoutes == 0")
    void zeroRouteStatusShouldBeValid() {
        GatewayStatus status = new GatewayStatus(
                "spring-cloud-gateway",
                "UP",
                FIXED_TIME,
                0,
                List.of()
        );

        assertThat(status.totalRoutes()).isZero();
        assertThat(status.routeIds()).isEmpty();
    }

    // =========================================================================
    // Equality tests
    // =========================================================================

    /**
     * Verifies value-based equality: two instances with identical fields are
     * considered equal, matching record semantics.
     */
    @Test
    @DisplayName("Two GatewayStatus instances with identical values are equal")
    void equalStatusInstancesShouldBeEqual() {
        List<String> routeIds = List.of("route-a");

        GatewayStatus status1 = new GatewayStatus("app", "UP", FIXED_TIME, 1, routeIds);
        GatewayStatus status2 = new GatewayStatus("app", "UP", FIXED_TIME, 1, routeIds);

        assertThat(status1).isEqualTo(status2);
        assertThat(status1.hashCode()).isEqualTo(status2.hashCode());
    }

    /**
     * Verifies that instances with different {@code status} values are not equal.
     */
    @Test
    @DisplayName("GatewayStatus instances with different status strings are not equal")
    void instancesWithDifferentStatusShouldNotBeEqual() {
        List<String> routeIds = List.of("route-a");

        GatewayStatus up   = new GatewayStatus("app", "UP",   FIXED_TIME, 1, routeIds);
        GatewayStatus down = new GatewayStatus("app", "DOWN", FIXED_TIME, 1, routeIds);

        assertThat(up).isNotEqualTo(down);
    }

    // =========================================================================
    // toString tests
    // =========================================================================

    /**
     * Verifies that {@code toString()} includes key field values, making the
     * record easy to debug when logged.
     */
    @Test
    @DisplayName("toString() contains applicationName and status")
    void toStringShouldContainKeyFields() {
        GatewayStatus status = new GatewayStatus(
                "spring-cloud-gateway", "UP", FIXED_TIME, 1, List.of("route-a"));

        String str = status.toString();
        assertThat(str).contains("spring-cloud-gateway");
        assertThat(str).contains("UP");
    }
}
