package com.example.circuitbreaker.service;

import com.example.circuitbreaker.client.InventoryClient;
import com.example.circuitbreaker.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p>These tests verify the service's business logic and fallback behaviour in
 * pure isolation — no Spring context, no circuit breaker AOP proxy, no HTTP calls.
 *
 * <p><strong>Testing strategy:</strong>
 * <ul>
 *   <li>{@link InventoryClient} is replaced with a Mockito mock so no real HTTP
 *       requests are made. Tests run in milliseconds without any network dependency.</li>
 *   <li>No Spring context is loaded — {@link ExtendWith}({@link MockitoExtension}.class)
 *       initialises Mockito annotations only, keeping startup time near zero.</li>
 *   <li>Fallback methods are tested directly (package-private visibility) to verify
 *       the degraded response values without needing to trigger the circuit breaker
 *       state machine (which requires a real Resilience4j context).</li>
 * </ul>
 *
 * <p><strong>Why test fallbacks directly?</strong>
 * The Resilience4j annotations ({@code @CircuitBreaker}, {@code @Retry}) are AOP
 * interceptors — they only activate when the bean is invoked through the Spring proxy.
 * In a pure Mockito test, the service is a plain object (no proxy), so the annotations
 * have no effect. Testing fallback methods directly ensures their logic and return
 * values are correct without requiring an integration test setup.
 * The circuit breaker state transitions themselves are tested in the integration tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService unit tests")
class ProductServiceTest {

    /**
     * Mockito mock of the HTTP client.
     * No Spring context, no real HTTP calls — all interactions simulated via
     * {@code when(...).thenReturn(...)}.
     */
    @Mock
    private InventoryClient inventoryClient;

    /**
     * The class under test.
     * {@code @InjectMocks} creates a {@link ProductService} instance and injects
     * the {@code @Mock} field via constructor injection.
     */
    @InjectMocks
    private ProductService productService;

    // ── Shared test fixtures ──────────────────────────────────────────────────────

    /** A sample Product returned by the mock client. */
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product(
                1,
                "Laptop Pro",
                "High-performance laptop",
                new BigDecimal("1299.99"),
                true
        );
    }

    // ── getProductById – happy path ───────────────────────────────────────────────

    @Test
    @DisplayName("getProductById returns the product from the inventory client when the call succeeds")
    void getProductById_returnsProduct_whenClientSucceeds() {
        // Given: the mock client returns the sample product for id=1
        when(inventoryClient.getProductById(1)).thenReturn(sampleProduct);

        // When: invoke the service method directly (no AOP proxy in unit test)
        Product result = productService.getProductById(1);

        // Then: the service returns the product unchanged
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1);
        assertThat(result.name()).isEqualTo("Laptop Pro");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("1299.99"));
        assertThat(result.available()).isTrue();

        // Verify the client was called exactly once with the correct id
        verify(inventoryClient, times(1)).getProductById(1);
    }

    @Test
    @DisplayName("getProductById propagates exception from the inventory client (no circuit breaker in unit test)")
    void getProductById_propagatesException_whenClientFails() {
        // Given: the mock client simulates a connection failure
        when(inventoryClient.getProductById(99))
                .thenThrow(new ResourceAccessException("Connection refused"));

        // When / Then: without the AOP proxy, the raw exception propagates
        // (the circuit breaker state machine is not active in a plain Mockito test)
        assertThatThrownBy(() -> productService.getProductById(99))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Connection refused");

        verify(inventoryClient, times(1)).getProductById(99);
    }

    // ── getProductById – fallback method ─────────────────────────────────────────

    @Test
    @DisplayName("getProductByIdFallback returns a placeholder product with id preserved")
    void getProductByIdFallback_returnsPlaceholder_withCorrectId() {
        // Given: a simulated exception (what the circuit breaker would pass to the fallback)
        RuntimeException cause = new RuntimeException("upstream timeout");

        // When: invoke the fallback method directly (package-private access in same package)
        Product fallback = productService.getProductByIdFallback(42, cause);

        // Then: the fallback product has the correct sentinel values
        assertThat(fallback).isNotNull();
        // The id is preserved from the original request so the caller knows which product failed
        assertThat(fallback.id()).isEqualTo(42);
        assertThat(fallback.name()).isEqualTo("Product Unavailable");
        assertThat(fallback.available()).isFalse();
        assertThat(fallback.price()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fallback.description()).contains("temporarily unavailable");
    }

    @Test
    @DisplayName("getProductByIdFallback works for any exception type")
    void getProductByIdFallback_worksForAnyExceptionType() {
        // Given: a connection refused exception
        ResourceAccessException ex = new ResourceAccessException("I/O error");

        // When
        Product fallback = productService.getProductByIdFallback(7, ex);

        // Then: fallback still returns a safe default regardless of exception type
        assertThat(fallback.id()).isEqualTo(7);
        assertThat(fallback.available()).isFalse();
    }

    // ── getAllProducts – happy path ────────────────────────────────────────────────

    @Test
    @DisplayName("getAllProducts returns all products from the inventory client when the call succeeds")
    void getAllProducts_returnsAllProducts_whenClientSucceeds() {
        // Given: the mock client returns two products
        Product second = new Product(2, "Monitor", "4K display", new BigDecimal("499.00"), true);
        when(inventoryClient.getAllProducts()).thenReturn(List.of(sampleProduct, second));

        // When
        List<Product> result = productService.getAllProducts();

        // Then: the service returns the list unchanged
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1);
        assertThat(result.get(0).name()).isEqualTo("Laptop Pro");
        assertThat(result.get(1).id()).isEqualTo(2);
        assertThat(result.get(1).name()).isEqualTo("Monitor");

        verify(inventoryClient, times(1)).getAllProducts();
    }

    @Test
    @DisplayName("getAllProducts returns an empty list when the client returns no products")
    void getAllProducts_returnsEmptyList_whenClientReturnsNone() {
        // Given: the mock client returns an empty list
        when(inventoryClient.getAllProducts()).thenReturn(List.of());

        // When
        List<Product> result = productService.getAllProducts();

        // Then: the service returns the empty list unchanged
        assertThat(result).isEmpty();
        verify(inventoryClient, times(1)).getAllProducts();
    }

    // ── getAllProducts – fallback method ──────────────────────────────────────────

    @Test
    @DisplayName("getAllProductsFallback returns an empty list regardless of exception type")
    void getAllProductsFallback_returnsEmptyList() {
        // Given: any exception (simulates exhausted retries or circuit open)
        RuntimeException cause = new RuntimeException("service unavailable");

        // When: invoke the fallback directly
        List<Product> fallback = productService.getAllProductsFallback(cause);

        // Then: an empty list is returned (graceful degradation)
        assertThat(fallback).isNotNull();
        assertThat(fallback).isEmpty();
    }

    @Test
    @DisplayName("getAllProductsFallback returns immutable empty list")
    void getAllProductsFallback_returnsImmutableEmptyList() {
        // Given
        RuntimeException cause = new ResourceAccessException("timeout");

        // When
        List<Product> fallback = productService.getAllProductsFallback(cause);

        // Then: List.of() is unmodifiable — adding to it must throw
        assertThat(fallback).isEmpty();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> fallback.add(sampleProduct)
        );
    }

    // ── Constants ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CB_NAME constant matches the expected circuit breaker instance name")
    void cbName_matchesExpectedInstanceName() {
        // This test guards against accidental renames that would break the
        // @CircuitBreaker(name=CB_NAME) binding to the application.yml config.
        assertThat(ProductService.CB_NAME).isEqualTo("inventoryService");
    }

    @Test
    @DisplayName("RETRY_NAME constant matches the expected retry instance name")
    void retryName_matchesExpectedInstanceName() {
        assertThat(ProductService.RETRY_NAME).isEqualTo("inventoryService");
    }
}
