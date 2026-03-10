package com.example.retryratelimiting.controller;

import com.example.retryratelimiting.domain.WeatherReport;
import com.example.retryratelimiting.service.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes weather endpoints protected by Retry and RateLimiter.
 *
 * <p>This controller is intentionally thin: it handles only HTTP concerns
 * (query parameters, response status, JSON serialisation). All fault-tolerance
 * logic ({@code @Retry}, {@code @RateLimiter}, fallback) lives in
 * {@link WeatherService}.
 *
 * <p>API routes:
 * <pre>
 *   GET /api/weather?city={city}  – current weather for a city
 * </pre>
 *
 * <p><strong>Fallback behaviour:</strong>
 * When the rate limiter rejects a request or all retry attempts are exhausted,
 * the service layer returns a synthetic report with {@code cached=true}
 * and this controller returns it with HTTP 200. Clients should check the
 * {@code cached} field to detect degraded mode.
 */
@RestController
@RequestMapping("/api")
public class WeatherController {

    private final WeatherService weatherService;

    /**
     * Constructor injection ensures the dependency is explicit and testable.
     *
     * @param weatherService service that applies Resilience4j fault tolerance
     */
    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * Fetch the current weather report for the given city.
     *
     * <p>Returns HTTP 200 in all cases (including fallback). When the rate limiter
     * rejects the call or all retries fail, the response body contains a synthetic
     * report with:
     * <ul>
     *   <li>{@code cached} → {@code true}</li>
     *   <li>{@code description} → "Weather data temporarily unavailable"</li>
     *   <li>{@code temperatureC}, {@code humidity}, {@code windSpeedKmh} → {@code null}</li>
     * </ul>
     *
     * @param city the city name to query (required query parameter)
     * @return 200 OK with the weather report JSON (live or fallback)
     */
    @GetMapping("/weather")
    public ResponseEntity<WeatherReport> getWeather(@RequestParam("city") String city) {
        WeatherReport report = weatherService.getWeatherByCity(city);
        return ResponseEntity.ok(report);
    }
}
