package com.example.retryratelimiting.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link WeatherReport} domain record.
 *
 * <p>These tests verify that the record:
 * <ul>
 *   <li>Stores and exposes all field values correctly.</li>
 *   <li>Treats two records with the same field values as equal (Java record equality).</li>
 *   <li>Produces a meaningful {@code toString()} representation.</li>
 *   <li>Correctly represents the "fallback/cached" state via the {@code cached} flag.</li>
 * </ul>
 *
 * <p>No Spring context is needed — these are pure Java unit tests that run in
 * milliseconds with no external dependencies.
 */
@DisplayName("WeatherReport domain record tests")
class WeatherReportDomainTest {

    // ── Construction and field access ─────────────────────────────────────────────

    @Test
    @DisplayName("Constructor stores all field values and accessors return them correctly")
    void constructor_storesAllFields_accessorsReturnThem() {
        // Given: a fixed timestamp for deterministic comparison
        Instant now = Instant.parse("2024-06-01T12:00:00Z");

        // When: construct a WeatherReport with all fields
        WeatherReport report = new WeatherReport(
                "London",
                "Partly cloudy",
                18.5,
                72,
                14.0,
                false,
                now
        );

        // Then: each accessor returns the value passed to the constructor
        assertThat(report.city()).isEqualTo("London");
        assertThat(report.description()).isEqualTo("Partly cloudy");
        assertThat(report.temperatureC()).isEqualTo(18.5);
        assertThat(report.humidity()).isEqualTo(72);
        assertThat(report.windSpeedKmh()).isEqualTo(14.0);
        assertThat(report.cached()).isFalse();
        assertThat(report.retrievedAt()).isEqualTo(now);
    }

    // ── Equality and hash code (Java record semantics) ────────────────────────────

    @Test
    @DisplayName("Two records with identical field values are equal")
    void equality_twoIdenticalRecords_areEqual() {
        // Given: two records with the same field values
        Instant ts = Instant.parse("2024-01-01T00:00:00Z");
        WeatherReport r1 = new WeatherReport("Tokyo", "Sunny", 25.0, 55, 10.0, false, ts);
        WeatherReport r2 = new WeatherReport("Tokyo", "Sunny", 25.0, 55, 10.0, false, ts);

        // Then: Java record auto-generates equals() based on all components
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("Two records with different city values are not equal")
    void equality_differentCity_notEqual() {
        Instant ts = Instant.parse("2024-01-01T00:00:00Z");
        WeatherReport r1 = new WeatherReport("London", "Rainy", 10.0, 90, 20.0, false, ts);
        WeatherReport r2 = new WeatherReport("Paris", "Rainy", 10.0, 90, 20.0, false, ts);

        assertThat(r1).isNotEqualTo(r2);
    }

    // ── Fallback / cached flag ────────────────────────────────────────────────────

    @Test
    @DisplayName("cached=true correctly signals a fallback / degraded response")
    void cachedFlag_trueSignalsFallback() {
        // Given: a fallback report (as returned by WeatherService.getWeatherByCityFallback)
        WeatherReport fallback = new WeatherReport(
                "Berlin",
                "Weather data temporarily unavailable",
                null,   // temperature unknown in fallback
                null,   // humidity unknown in fallback
                null,   // wind speed unknown in fallback
                true,   // <-- cached=true is the key signal
                Instant.now()
        );

        // Then: the cached flag correctly identifies this as a degraded response
        assertThat(fallback.cached()).isTrue();
        assertThat(fallback.city()).isEqualTo("Berlin");
        // Null fields indicate that no real data was available
        assertThat(fallback.temperatureC()).isNull();
        assertThat(fallback.humidity()).isNull();
        assertThat(fallback.windSpeedKmh()).isNull();
    }

    @Test
    @DisplayName("cached=false correctly signals a live / fresh response")
    void cachedFlag_falseSignalsLiveData() {
        // Given: a live report (as returned by the real upstream API)
        WeatherReport live = new WeatherReport(
                "Sydney", "Clear sky", 22.0, 60, 8.0,
                false, // <-- cached=false signals live data
                Instant.now()
        );

        assertThat(live.cached()).isFalse();
        assertThat(live.temperatureC()).isNotNull();
    }

    // ── toString ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes city and description for diagnostics")
    void toString_includesCityAndDescription() {
        // Given
        WeatherReport report = new WeatherReport(
                "Madrid", "Hot and sunny", 35.0, 30, 5.0,
                false, Instant.parse("2024-07-15T14:00:00Z")
        );

        // When
        String str = report.toString();

        // Then: Java record auto-generates toString with all field names and values
        assertThat(str).contains("Madrid");
        assertThat(str).contains("Hot and sunny");
    }
}
