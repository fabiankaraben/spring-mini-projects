package com.example.retryratelimiting.client;

import com.example.retryratelimiting.domain.WeatherReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client that calls the external upstream weather API.
 *
 * <p>This class is the integration boundary between our application and the
 * remote weather service. It is intentionally kept simple — it only knows
 * how to make the HTTP call and parse the response. Fault-tolerance logic
 * ({@code @Retry}, {@code @RateLimiter}) is applied in the service layer
 * ({@link com.example.retryratelimiting.service.WeatherService}), following
 * the single-responsibility principle.
 *
 * <p><strong>Design note – why a separate client class?</strong>
 * Keeping the HTTP call in its own component makes it easy to:
 * <ul>
 *   <li>Mock the client in unit tests (replace only the HTTP boundary).</li>
 *   <li>Apply Resilience4j annotations at a clear boundary, not inside
 *       business logic methods.</li>
 *   <li>Swap the HTTP library (RestTemplate → WebClient) without touching
 *       the service or controller layers.</li>
 * </ul>
 *
 * <p>The upstream API contract assumed by this client:
 * <pre>
 *   GET /weather?city={city}  → 200 OK  with a {@link WeatherReport} JSON body
 *                             → 503 Service Unavailable when the service is overloaded
 *                             → 500 Internal Server Error on unexpected failures
 * </pre>
 */
@Component
public class WeatherClient {

    /**
     * Base URL of the upstream weather API.
     * Injected from the {@code weather.base-url} property in application.yml.
     * Overridable via the {@code WEATHER_BASE_URL} environment variable.
     */
    private final String baseUrl;

    /** Spring's synchronous HTTP client, auto-configured as a Bean in AppConfig. */
    private final RestTemplate restTemplate;

    /**
     * Constructor injection makes dependencies explicit and testable.
     *
     * @param baseUrl     base URL of the upstream weather API (from application.yml)
     * @param restTemplate Spring-managed RestTemplate bean
     */
    public WeatherClient(
            @Value("${weather.base-url}") String baseUrl,
            RestTemplate restTemplate) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch the current weather report for the given city from the upstream API.
     *
     * <p>Makes a {@code GET /weather?city={city}} request. Throws a
     * {@link org.springframework.web.client.RestClientException} subclass on
     * HTTP errors (4xx / 5xx) or connectivity problems — the Retry and
     * RateLimiter decorators in {@link com.example.retryratelimiting.service.WeatherService}
     * intercept these.
     *
     * <p>Specifically:
     * <ul>
     *   <li>{@link org.springframework.web.client.HttpServerErrorException} is thrown
     *       on 5xx responses (e.g. 503). This triggers a retry attempt.</li>
     *   <li>{@link org.springframework.web.client.ResourceAccessException} is thrown
     *       on connection timeouts. This also triggers a retry attempt.</li>
     * </ul>
     *
     * @param city the city name to query, e.g. "London"
     * @return the {@link WeatherReport} parsed from the JSON response body
     */
    public WeatherReport getWeatherByCity(String city) {
        // Build the request URL: base URL + path + query parameter
        String url = baseUrl + "/weather?city=" + city;
        // RestTemplate performs HTTP GET and maps the JSON body to WeatherReport
        return restTemplate.getForObject(url, WeatherReport.class);
    }
}
