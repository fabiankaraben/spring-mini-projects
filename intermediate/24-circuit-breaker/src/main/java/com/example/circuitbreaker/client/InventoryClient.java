package com.example.circuitbreaker.client;

import com.example.circuitbreaker.domain.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * HTTP client that calls the external upstream inventory/product API.
 *
 * <p>This class is the integration boundary between our application and
 * the remote service. It is the place where {@code @CircuitBreaker},
 * {@code @Retry}, and {@code @TimeLimiter} are applied in
 * {@link com.example.circuitbreaker.service.ProductService}.
 *
 * <p>Using a plain {@link RestTemplate} (rather than OpenFeign) keeps this
 * project focused on Resilience4j and avoids conflating two separate concepts.
 * In production you could equally wrap a {@code WebClient} or Feign client
 * with the same Resilience4j annotations.
 *
 * <p><strong>Design note – why a separate client class?</strong>
 * Keeping the HTTP call in its own component makes it easy to:
 * <ul>
 *   <li>Mock the client in unit tests (replace only the HTTP boundary).</li>
 *   <li>Apply circuit breaker annotations at the boundary, not inside
 *       business logic methods.</li>
 * </ul>
 */
@Component
public class InventoryClient {

    /**
     * Base URL of the upstream inventory API.
     * Injected from the {@code inventory.base-url} property in application.yml.
     * Overridable via the {@code INVENTORY_BASE_URL} environment variable.
     */
    private final String baseUrl;

    /** Spring's synchronous HTTP client, auto-configured as a Bean. */
    private final RestTemplate restTemplate;

    /**
     * Constructor injection makes dependencies explicit and testable.
     *
     * @param baseUrl     base URL of the upstream inventory API (from application.yml)
     * @param restTemplate Spring-managed RestTemplate bean
     */
    public InventoryClient(
            @Value("${inventory.base-url}") String baseUrl,
            RestTemplate restTemplate) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch a single product by its ID from the upstream inventory service.
     *
     * <p>Makes a {@code GET /products/{id}} request. Throws a
     * {@link org.springframework.web.client.RestClientException} subclass on
     * HTTP errors (4xx / 5xx) or connectivity problems — the circuit breaker
     * in {@link com.example.circuitbreaker.service.ProductService} catches these.
     *
     * @param id the product identifier
     * @return the {@link Product} parsed from the JSON response body
     */
    public Product getProductById(Integer id) {
        // Build the full URL by appending the path to the configured base URL
        String url = baseUrl + "/products/" + id;
        ResponseEntity<Product> response =
                restTemplate.exchange(url, HttpMethod.GET, null, Product.class);
        return response.getBody();
    }

    /**
     * Fetch all products from the upstream inventory service.
     *
     * <p>Makes a {@code GET /products} request. Returns an empty list when the
     * upstream returns an empty JSON array. The circuit breaker intercepts any
     * exception thrown here.
     *
     * @return list of all {@link Product} objects from the upstream service
     */
    public List<Product> getAllProducts() {
        // ParameterizedTypeReference preserves the generic type (List<Product>)
        // at runtime, which is necessary because Java erases generic types.
        String url = baseUrl + "/products";
        ResponseEntity<List<Product>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Product>>() {}
        );
        return response.getBody() != null ? response.getBody() : List.of();
    }
}
