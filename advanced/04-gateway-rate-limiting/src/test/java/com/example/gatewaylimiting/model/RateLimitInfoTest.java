package com.example.gatewaylimiting.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link RateLimitInfo} record.
 *
 * <p>These tests verify the immutable value object behaviour:
 * correct field storage, equality, and the auto-generated {@code toString}.
 * No Spring context is loaded — these are plain JUnit 5 tests that run fast.
 */
@DisplayName("RateLimitInfo — unit tests")
class RateLimitInfoTest {

    /**
     * Verifies that the compact constructor stores all fields correctly and
     * the accessor methods return the expected values.
     */
    @Test
    @DisplayName("Constructor stores all fields correctly")
    void constructorShouldStoreAllFields() {
        RateLimitInfo info = new RateLimitInfo(
                "product-route",
                "/api/products/**",
                5,
                10,
                "IP Address"
        );

        assertThat(info.routeId()).isEqualTo("product-route");
        assertThat(info.path()).isEqualTo("/api/products/**");
        assertThat(info.replenishRate()).isEqualTo(5);
        assertThat(info.burstCapacity()).isEqualTo(10);
        assertThat(info.keyResolverType()).isEqualTo("IP Address");
    }

    /**
     * Verifies that two {@link RateLimitInfo} instances with identical field
     * values are equal. Java records generate {@code equals()} automatically,
     * comparing all components.
     */
    @Test
    @DisplayName("Two instances with same values are equal")
    void equalityWithSameValues() {
        RateLimitInfo info1 = new RateLimitInfo("order-route", "/api/orders/**", 3, 5, "IP Address");
        RateLimitInfo info2 = new RateLimitInfo("order-route", "/api/orders/**", 3, 5, "IP Address");

        assertThat(info1).isEqualTo(info2);
        assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
    }

    /**
     * Verifies that two instances with different fields are not equal.
     * Changing any single component should make them unequal.
     */
    @Test
    @DisplayName("Instances with different replenishRate are not equal")
    void inequalityWithDifferentReplenishRate() {
        RateLimitInfo info1 = new RateLimitInfo("user-route", "/api/users/**", 10, 20, "IP Address");
        RateLimitInfo info2 = new RateLimitInfo("user-route", "/api/users/**",  5, 20, "IP Address");

        assertThat(info1).isNotEqualTo(info2);
    }

    /**
     * Verifies that the auto-generated {@code toString()} includes all field
     * values, which is useful for debugging and logging.
     */
    @Test
    @DisplayName("toString contains all field values")
    void toStringShouldContainAllValues() {
        RateLimitInfo info = new RateLimitInfo("product-route", "/api/products/**", 5, 10, "IP Address");

        String str = info.toString();
        assertThat(str).contains("product-route");
        assertThat(str).contains("/api/products/**");
        assertThat(str).contains("5");
        assertThat(str).contains("10");
        assertThat(str).contains("IP Address");
    }

    /**
     * Verifies that burstCapacity must be greater than or equal to replenishRate
     * in a correctly configured route. This is not enforced by the record itself
     * (no validation annotation), but this test documents the expected invariant.
     */
    @Test
    @DisplayName("burstCapacity is greater than or equal to replenishRate in valid configs")
    void burstCapacityShouldBeAtLeastReplenishRate() {
        RateLimitInfo productRoute = new RateLimitInfo("product-route", "/api/products/**", 5, 10, "IP Address");
        RateLimitInfo orderRoute   = new RateLimitInfo("order-route",   "/api/orders/**",   3,  5, "IP Address");
        RateLimitInfo userRoute    = new RateLimitInfo("user-route",    "/api/users/**",   10, 20, "IP Address");

        assertThat(productRoute.burstCapacity()).isGreaterThanOrEqualTo(productRoute.replenishRate());
        assertThat(orderRoute.burstCapacity()).isGreaterThanOrEqualTo(orderRoute.replenishRate());
        assertThat(userRoute.burstCapacity()).isGreaterThanOrEqualTo(userRoute.replenishRate());
    }
}
