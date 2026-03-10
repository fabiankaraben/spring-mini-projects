package com.example.retryratelimiting.domain;

import java.time.Instant;

/**
 * Immutable domain record representing a weather report returned by the
 * upstream weather API.
 *
 * <p>Using a Java {@code record} instead of a regular class:
 * <ul>
 *   <li>Automatically generates constructor, getters ({@code city()}, etc.),
 *       {@code equals()}, {@code hashCode()}, and {@code toString()}.</li>
 *   <li>Signals immutability — the RateLimiter and Retry fallback responses
 *       are read-only snapshots, making the design intent clear.</li>
 * </ul>
 *
 * @param city        name of the city the report refers to
 * @param description short text description of current conditions (e.g., "Partly cloudy")
 * @param temperatureC current temperature in degrees Celsius
 * @param humidity    relative humidity percentage (0–100)
 * @param windSpeedKmh wind speed in kilometres per hour
 * @param cached      {@code true} when this report is a cached/fallback value,
 *                    not a fresh upstream response
 * @param retrievedAt UTC timestamp at which the report was retrieved (or synthesised)
 */
public record WeatherReport(
        String city,
        String description,
        Double temperatureC,
        Integer humidity,
        Double windSpeedKmh,
        Boolean cached,
        Instant retrievedAt
) {
}
