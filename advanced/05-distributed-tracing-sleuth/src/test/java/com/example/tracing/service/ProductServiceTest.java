package com.example.tracing.service;

import com.example.tracing.model.Product;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p>These tests use Mockito to inject a fake {@link Tracer} so that the service
 * can be tested in complete isolation — no Spring context, no HTTP server.
 *
 * <p><b>Mocking strategy:</b>
 * The {@code Tracer} and {@code Span} interfaces from Micrometer Tracing are
 * mocked. {@code tracer.currentSpan()} returns a mock {@code Span}, and
 * {@code span.context()} returns a mock {@code TraceContext} that supplies
 * deterministic traceId and spanId values ("test-trace-id", "test-span-id").
 * This allows assertions on the traceId/spanId fields in the returned
 * {@link Product} without requiring a real tracing pipeline.
 *
 * <p><b>What is tested:</b>
 * <ul>
 *   <li>Finding a product that exists returns a non-null result with the correct fields.</li>
 *   <li>The returned product includes traceId and spanId from the current span.</li>
 *   <li>Custom span tags are applied (product.id, product.name, product.category).</li>
 *   <li>Looking up a non-existent product returns null and tags the span accordingly.</li>
 *   <li>Listing all products returns the full catalogue.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService — unit tests")
class ProductServiceTest {

    /** Mocked Micrometer Tracer — no real tracing pipeline needed. */
    @Mock
    private Tracer tracer;

    /** Mocked Span returned by tracer.currentSpan(). */
    @Mock
    private Span currentSpan;

    /** Mocked TraceContext returned by currentSpan.context(). */
    @Mock
    private TraceContext traceContext;

    /** The service under test. */
    private ProductService productService;

    @BeforeEach
    void setUp() {
        // Wire up the mock chain: tracer → span → context → IDs.
        when(tracer.currentSpan()).thenReturn(currentSpan);
        when(currentSpan.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("test-trace-id");
        when(traceContext.spanId()).thenReturn("test-span-id");

        // Allow tag() and event() calls on the mock span without throwing.
        when(currentSpan.tag(anyString(), anyString())).thenReturn(currentSpan);

        productService = new ProductService(tracer, "distributed-tracing-sleuth");
    }

    // =========================================================================
    // findById — found
    // =========================================================================

    /**
     * Looking up an existing product should return a non-null Product.
     */
    @Test
    @DisplayName("findById returns a product when the ID exists")
    void findByIdShouldReturnProductForExistingId() {
        Product product = productService.findById("PROD-001");

        assertThat(product).isNotNull();
        assertThat(product.id()).isEqualTo("PROD-001");
        assertThat(product.name()).isEqualTo("Laptop Pro 15");
        assertThat(product.category()).isEqualTo("Electronics");
        assertThat(product.price()).isEqualTo(1299.99);
    }

    /**
     * The returned product should include the traceId from the current span.
     */
    @Test
    @DisplayName("findById embeds traceId from the current span")
    void findByIdShouldEmbedTraceId() {
        Product product = productService.findById("PROD-001");

        assertThat(product).isNotNull();
        assertThat(product.traceId()).isEqualTo("test-trace-id");
    }

    /**
     * The returned product should include the spanId from the current span.
     */
    @Test
    @DisplayName("findById embeds spanId from the current span")
    void findByIdShouldEmbedSpanId() {
        Product product = productService.findById("PROD-001");

        assertThat(product).isNotNull();
        assertThat(product.spanId()).isEqualTo("test-span-id");
    }

    /**
     * Custom span tags should be applied when a product is found.
     * This verifies the educational tracing enrichment behaviour.
     */
    @Test
    @DisplayName("findById applies span tag product.found=true when product exists")
    void findByIdShouldTagSpanWithFoundTrue() {
        productService.findById("PROD-002");

        // Verify the span was tagged with product.found = true
        verify(currentSpan).tag("product.found", "true");
        verify(currentSpan).tag("product.id", "PROD-002");
    }

    /**
     * The span event for catalogue lookup should be recorded on success.
     */
    @Test
    @DisplayName("findById records catalogue_lookup event on found product")
    void findByIdShouldRecordCatalogueLookupEvent() {
        productService.findById("PROD-001");
        verify(currentSpan).event("product.catalogue_lookup");
    }

    // =========================================================================
    // findById — not found
    // =========================================================================

    /**
     * Looking up a non-existent product returns null.
     */
    @Test
    @DisplayName("findById returns null when product ID does not exist")
    void findByIdShouldReturnNullForUnknownId() {
        Product product = productService.findById("PROD-UNKNOWN");
        assertThat(product).isNull();
    }

    /**
     * When a product is not found, the span is tagged product.found=false.
     */
    @Test
    @DisplayName("findById tags span with product.found=false when product does not exist")
    void findByIdShouldTagSpanWithFoundFalseWhenMissing() {
        productService.findById("PROD-UNKNOWN");
        verify(currentSpan).tag("product.found", "false");
    }

    /**
     * When a product is not found, the product.not_found span event is recorded.
     */
    @Test
    @DisplayName("findById records product.not_found event for unknown products")
    void findByIdShouldRecordNotFoundEvent() {
        productService.findById("PROD-UNKNOWN");
        verify(currentSpan).event("product.not_found");
    }

    // =========================================================================
    // findAll
    // =========================================================================

    /**
     * findAll should return all five products in the catalogue.
     */
    @Test
    @DisplayName("findAll returns all 5 products")
    void findAllShouldReturnAllProducts() {
        Collection<Product> products = productService.findAll();
        assertThat(products).hasSize(5);
    }

    /**
     * All products returned by findAll should have a non-null traceId.
     */
    @Test
    @DisplayName("findAll embeds traceId in every product")
    void findAllShouldEmbedTraceIdInEveryProduct() {
        Collection<Product> products = productService.findAll();
        assertThat(products)
                .isNotEmpty()
                .allMatch(p -> "test-trace-id".equals(p.traceId()));
    }

    /**
     * findAll should include the known product IDs in the result.
     */
    @Test
    @DisplayName("findAll contains all expected product IDs")
    void findAllShouldContainExpectedProductIds() {
        Collection<Product> products = productService.findAll();
        assertThat(products)
                .extracting(Product::id)
                .containsExactlyInAnyOrder(
                        "PROD-001", "PROD-002", "PROD-003", "PROD-004", "PROD-005");
    }
}
