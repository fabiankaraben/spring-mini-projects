package com.example.springcloudgateway.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link RouteInfo} record.
 *
 * <p><b>What is being tested here?</b>
 * Java records automatically generate:
 * <ul>
 *   <li>A canonical constructor.</li>
 *   <li>Accessor methods (e.g. {@code id()}, {@code uri()}).</li>
 *   <li>{@code equals()} and {@code hashCode()} based on all components.</li>
 *   <li>{@code toString()} in the form {@code RouteInfo[id=..., uri=..., ...]}</li>
 * </ul>
 *
 * <p>These tests verify that the record stores and retrieves values correctly,
 * and that the value-based equality semantics work as expected. While the
 * compiler guarantees the generated methods are correct, explicitly testing
 * them serves as living documentation for how the record is intended to be used.
 *
 * <p><b>Testing style:</b>
 * These are pure unit tests — no Spring context, no mock beans, no network.
 * They run instantly and test domain-logic behaviour in isolation.
 */
@DisplayName("RouteInfo record — unit tests")
class RouteInfoTest {

    /** A sample route ID used across test methods. */
    private static final String SAMPLE_ID = "product-service-route";

    /** A sample downstream URI. */
    private static final String SAMPLE_URI = "http://localhost:8081";

    /** Sample predicate descriptions. */
    private static final List<String> SAMPLE_PREDICATES =
            List.of("Path=/api/products/**");

    /** Sample filter descriptions. */
    private static final List<String> SAMPLE_FILTERS =
            List.of("StripPrefix=1", "AddRequestHeader=X-Gateway-Source, spring-cloud-gateway");

    // =========================================================================
    // Constructor and accessor tests
    // =========================================================================

    /**
     * Verifies that the canonical record constructor stores values that can be
     * retrieved via the accessor methods.
     */
    @Test
    @DisplayName("Constructor stores values accessible via accessors")
    void constructorShouldStoreValues() {
        // Act: create a record instance using the canonical constructor.
        RouteInfo routeInfo = new RouteInfo(
                SAMPLE_ID,
                SAMPLE_URI,
                SAMPLE_PREDICATES,
                SAMPLE_FILTERS
        );

        // Assert: each accessor returns the value passed to the constructor.
        assertThat(routeInfo.id()).isEqualTo(SAMPLE_ID);
        assertThat(routeInfo.uri()).isEqualTo(SAMPLE_URI);
        assertThat(routeInfo.predicates()).isEqualTo(SAMPLE_PREDICATES);
        assertThat(routeInfo.filters()).isEqualTo(SAMPLE_FILTERS);
    }

    /**
     * Verifies that an empty predicate list is allowed and correctly stored.
     */
    @Test
    @DisplayName("Empty predicates list is stored and retrieved correctly")
    void emptyPredicatesShouldBeStored() {
        RouteInfo routeInfo = new RouteInfo(SAMPLE_ID, SAMPLE_URI, List.of(), SAMPLE_FILTERS);

        assertThat(routeInfo.predicates()).isEmpty();
    }

    /**
     * Verifies that an empty filter list is allowed and correctly stored.
     */
    @Test
    @DisplayName("Empty filters list is stored and retrieved correctly")
    void emptyFiltersShouldBeStored() {
        RouteInfo routeInfo = new RouteInfo(SAMPLE_ID, SAMPLE_URI, SAMPLE_PREDICATES, List.of());

        assertThat(routeInfo.filters()).isEmpty();
    }

    // =========================================================================
    // Equality tests
    // =========================================================================

    /**
     * Verifies that two {@link RouteInfo} instances with identical values are
     * considered equal (value-based equality, not reference equality).
     */
    @Test
    @DisplayName("Two RouteInfo instances with identical values are equal")
    void equalRecordsShouldBeEqual() {
        // Arrange: two distinct objects with the same field values.
        RouteInfo route1 = new RouteInfo(SAMPLE_ID, SAMPLE_URI, SAMPLE_PREDICATES, SAMPLE_FILTERS);
        RouteInfo route2 = new RouteInfo(SAMPLE_ID, SAMPLE_URI, SAMPLE_PREDICATES, SAMPLE_FILTERS);

        // Assert: records use value-based equality.
        assertThat(route1).isEqualTo(route2);
        assertThat(route1.hashCode()).isEqualTo(route2.hashCode());
    }

    /**
     * Verifies that two {@link RouteInfo} instances with different IDs are
     * NOT equal.
     */
    @Test
    @DisplayName("RouteInfo instances with different IDs are not equal")
    void recordsWithDifferentIdsShouldNotBeEqual() {
        RouteInfo route1 = new RouteInfo("route-a", SAMPLE_URI, SAMPLE_PREDICATES, SAMPLE_FILTERS);
        RouteInfo route2 = new RouteInfo("route-b", SAMPLE_URI, SAMPLE_PREDICATES, SAMPLE_FILTERS);

        assertThat(route1).isNotEqualTo(route2);
    }

    // =========================================================================
    // toString tests
    // =========================================================================

    /**
     * Verifies that {@code toString()} includes the record field values,
     * which is useful for debugging and logging.
     */
    @Test
    @DisplayName("toString() includes the route ID")
    void toStringShouldIncludeRouteId() {
        RouteInfo routeInfo = new RouteInfo(SAMPLE_ID, SAMPLE_URI, SAMPLE_PREDICATES, SAMPLE_FILTERS);

        // Records generate toString() as "RouteInfo[id=..., uri=..., ...]"
        assertThat(routeInfo.toString()).contains(SAMPLE_ID);
        assertThat(routeInfo.toString()).contains(SAMPLE_URI);
    }

    // =========================================================================
    // Null-safety / edge-case tests
    // =========================================================================

    /**
     * Verifies that the record allows null values (it is the caller's
     * responsibility to avoid nulls if that is a business rule — a record does
     * not enforce non-null by default unless compact constructor validation is
     * added).
     */
    @Test
    @DisplayName("RouteInfo can hold null values (no built-in null guard)")
    void nullValuesShouldBeAllowed() {
        // Records do not add null-checks unless a compact constructor adds them.
        RouteInfo routeInfo = new RouteInfo(null, null, null, null);

        assertThat(routeInfo.id()).isNull();
        assertThat(routeInfo.uri()).isNull();
    }

    /**
     * Verifies that a route with multiple predicates stores all of them.
     */
    @Test
    @DisplayName("Multiple predicates are stored and all retrieved")
    void multiplePredicatesShouldAllBeStored() {
        List<String> multiPredicates = List.of(
                "Path=/api/products/**",
                "Method=GET",
                "Header=X-API-Version, 2"
        );

        RouteInfo routeInfo = new RouteInfo(SAMPLE_ID, SAMPLE_URI, multiPredicates, SAMPLE_FILTERS);

        assertThat(routeInfo.predicates()).hasSize(3);
        assertThat(routeInfo.predicates()).containsExactlyElementsOf(multiPredicates);
    }
}
