package com.example.circuitbreaker.service;

import com.example.circuitbreaker.client.InventoryClient;
import com.example.circuitbreaker.domain.Product;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service that fetches product data from the upstream inventory API and
 * applies Resilience4j fault-tolerance patterns to protect against failures.
 *
 * <h2>Circuit Breaker Pattern</h2>
 * <p>A circuit breaker sits between the caller and the remote service. It tracks
 * how many calls fail within a sliding window. When the failure rate exceeds a
 * configurable threshold the circuit "opens", and all subsequent calls immediately
 * return the fallback response without hitting the remote service. After a
 * configured wait duration the circuit enters HALF_OPEN state: a limited number
 * of probe calls are allowed through. If they succeed, the circuit closes again;
 * if they fail, it re-opens.
 *
 * <h2>Retry Pattern</h2>
 * <p>Before the circuit breaker records a call as a failure, the {@code @Retry}
 * annotation automatically re-invokes the method on transient errors. This
 * handles brief network hiccups without opening the circuit.
 *
 * <h2>Fallback Methods</h2>
 * <p>Each protected method has a corresponding {@code *Fallback} method that
 * receives the exception and returns a safe, degraded response. The contract is:
 * the fallback method must have the same return type as the protected method plus
 * a {@link Throwable} (or specific exception type) as its last parameter.
 *
 * <h2>Configuration</h2>
 * <p>All thresholds and window sizes are configured in {@code application.yml}
 * under {@code resilience4j.circuitbreaker.instances.inventoryService}.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /**
     * Name constant for the circuit breaker instance.
     * Must match the key under {@code resilience4j.circuitbreaker.instances} in
     * application.yml. Using a constant avoids typos in both annotations and tests.
     */
    public static final String CB_NAME = "inventoryService";

    /**
     * Name constant for the retry instance.
     * Must match the key under {@code resilience4j.retry.instances} in application.yml.
     */
    public static final String RETRY_NAME = "inventoryService";

    /** HTTP client that calls the upstream inventory/product API. */
    private final InventoryClient inventoryClient;

    /**
     * Constructor injection — makes the dependency explicit and allows
     * replacing the real HTTP client with a mock in unit tests.
     *
     * @param inventoryClient the HTTP client for the upstream inventory API
     */
    public ProductService(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    // ── getProductById ────────────────────────────────────────────────────────────

    /**
     * Fetch a single product by ID from the upstream inventory service.
     *
     * <p>Fault-tolerance stack (outermost → innermost):
     * <ol>
     *   <li>{@code @CircuitBreaker} – opens when too many calls fail;
     *       routes to {@link #getProductByIdFallback} when open.</li>
     *   <li>{@code @Retry} – retries up to N times on transient errors before
     *       reporting a failure to the circuit breaker.</li>
     * </ol>
     *
     * <p>Resilience4j applies decorators in the order they are stacked:
     * {@code CircuitBreaker(Retry(method))}. The retry exhausts its attempts first;
     * only after all retries are exhausted does the circuit breaker record one failure.
     *
     * @param id product identifier
     * @return the product, or a fallback product if the circuit is open / call fails
     */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getProductByIdFallback")
    @Retry(name = RETRY_NAME)
    public Product getProductById(Integer id) {
        log.info("Fetching product {} from upstream inventory service", id);
        return inventoryClient.getProductById(id);
    }

    /**
     * Fallback for {@link #getProductById}.
     *
     * <p>Called when the circuit is OPEN or when all retries have been exhausted.
     * Returns a placeholder product so the API can still respond with partial data
     * rather than propagating an error to the client.
     *
     * <p><strong>Fallback naming contract:</strong> the method name is passed as
     * {@code fallbackMethod} in the {@code @CircuitBreaker} annotation. The signature
     * must match the primary method's signature with an extra {@link Throwable} param.
     *
     * @param id        the product ID that was requested
     * @param throwable the exception that caused the fallback to be invoked
     * @return a degraded Product placeholder
     */
    Product getProductByIdFallback(Integer id, Throwable throwable) {
        log.warn("Circuit breaker fallback triggered for getProductById({}): {}",
                id, throwable.getMessage());
        // Return a safe default product so the caller always gets a response
        return new Product(
                id,
                "Product Unavailable",
                "The product catalog is temporarily unavailable. Please try again later.",
                BigDecimal.ZERO,
                false
        );
    }

    // ── getAllProducts ────────────────────────────────────────────────────────────

    /**
     * Fetch all products from the upstream inventory service.
     *
     * <p>Same fault-tolerance stack as {@link #getProductById}. When the circuit is
     * OPEN the fallback returns an empty list, signalling "no data available" to
     * the calling controller.
     *
     * @return list of products, or an empty list when the circuit is open
     */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getAllProductsFallback")
    @Retry(name = RETRY_NAME)
    public List<Product> getAllProducts() {
        log.info("Fetching all products from upstream inventory service");
        return inventoryClient.getAllProducts();
    }

    /**
     * Fallback for {@link #getAllProducts}.
     *
     * <p>Returns an empty list so the API response is still valid JSON ({@code []})
     * rather than an HTTP 500 error. Callers can detect "unavailable" by checking
     * for an empty list or by inspecting the circuit breaker health endpoint.
     *
     * @param throwable the exception that caused the fallback to be invoked
     * @return an empty product list
     */
    List<Product> getAllProductsFallback(Throwable throwable) {
        log.warn("Circuit breaker fallback triggered for getAllProducts(): {}",
                throwable.getMessage());
        return List.of();
    }
}
