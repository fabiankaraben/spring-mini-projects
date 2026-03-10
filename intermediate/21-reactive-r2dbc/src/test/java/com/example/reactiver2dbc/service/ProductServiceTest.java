package com.example.reactiver2dbc.service;

import com.example.reactiver2dbc.domain.Product;
import com.example.reactiver2dbc.dto.ProductRequest;
import com.example.reactiver2dbc.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p>These tests verify the service's business logic in pure isolation:
 * <ul>
 *   <li>The {@link ProductRepository} is replaced with a Mockito mock, so no real
 *       R2DBC connection or database is needed. Tests run in milliseconds.</li>
 *   <li>No Spring context is loaded — {@link ExtendWith}({@link MockitoExtension}.class)
 *       initialises Mockito annotations only, keeping startup time near zero.</li>
 *   <li>{@link StepVerifier} (from {@code reactor-test}) is used to test reactive
 *       pipelines step-by-step in a synchronous, deterministic way. It subscribes
 *       to a {@link Mono} or {@link Flux}, then asserts each emitted item,
 *       completion, or error signal.</li>
 *   <li>Each test follows the Given / When / Then (Arrange / Act / Assert) pattern.</li>
 * </ul>
 *
 * <p><strong>Why StepVerifier instead of .block()?</strong><br>
 * Reactive types are lazy — nothing executes until subscribed. Calling
 * {@code productService.findAll()} returns a cold {@link Flux} but does NOT trigger
 * any R2DBC calls. {@code StepVerifier.create(...).expectNext(...).verifyComplete()}
 * subscribes and blocks the test thread until the publisher completes, making
 * reactive assertions feel like ordinary JUnit assertions without bypassing the
 * reactive pipeline.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService unit tests")
class ProductServiceTest {

    /**
     * Mockito mock of the repository — no real R2DBC involved.
     * All interactions are simulated via {@code when(...).thenReturn(...)}.
     */
    @Mock
    private ProductRepository productRepository;

    /**
     * The class under test.
     * {@code @InjectMocks} creates a {@link ProductService} instance and injects
     * the {@code @Mock} fields into it via constructor injection.
     */
    @InjectMocks
    private ProductService productService;

    // ── Shared test fixtures ──────────────────────────────────────────────────────

    /** A pre-built domain object returned by the mock repository. */
    private Product sampleProduct;

    /** A DTO representing an incoming HTTP POST/PUT request body. */
    private ProductRequest sampleRequest;

    @BeforeEach
    void setUp() {
        // Build a sample domain entity that the mock repository will return
        sampleProduct = new Product(
                "Wireless Keyboard",
                "Compact TKL with Cherry MX switches",
                new BigDecimal("129.99"),
                "electronics",
                45,
                true
        );
        sampleProduct.setId(1L);

        // Build the corresponding request DTO
        sampleRequest = new ProductRequest(
                "Wireless Keyboard",
                "Compact TKL with Cherry MX switches",
                new BigDecimal("129.99"),
                "electronics",
                45,
                true
        );
    }

    // ── findAll ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll emits all products from the repository")
    void findAll_emitsAllProducts() {
        // Given: the mock repository returns two products as a Flux
        Product second = new Product("Mouse", "Ergonomic vertical", new BigDecimal("59.99"), "electronics", 120, true);
        second.setId(2L);
        when(productRepository.findAll()).thenReturn(Flux.just(sampleProduct, second));

        // When / Then: StepVerifier subscribes and asserts both items are emitted in order
        StepVerifier.create(productService.findAll())
                .expectNext(sampleProduct)
                .expectNext(second)
                .verifyComplete();

        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAll completes empty when the table is empty")
    void findAll_completesEmpty_whenNoProducts() {
        // Given: the repository returns an empty Flux
        when(productRepository.findAll()).thenReturn(Flux.empty());

        // When / Then: the Flux completes immediately with no items
        StepVerifier.create(productService.findAll())
                .verifyComplete();
    }

    // ── findById ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById emits the product when it exists")
    void findById_emitsProduct_whenFound() {
        // Given: the repository finds the product with id=1
        when(productRepository.findById(1L)).thenReturn(Mono.just(sampleProduct));

        // When / Then
        StepVerifier.create(productService.findById(1L))
                .expectNextMatches(p -> p.getId().equals(1L)
                        && p.getName().equals("Wireless Keyboard"))
                .verifyComplete();

        verify(productRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("findById completes empty when the product does not exist")
    void findById_completesEmpty_whenNotFound() {
        // Given: no product with id=999
        when(productRepository.findById(999L)).thenReturn(Mono.empty());

        // When / Then: the Mono is empty (the controller will map this to HTTP 404)
        StepVerifier.create(productService.findById(999L))
                .verifyComplete();
    }

    // ── findByCategory ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByCategory emits products in the given category")
    void findByCategory_emitsMatchingProducts() {
        // Given: the repository returns our electronics product
        when(productRepository.findByCategory("electronics"))
                .thenReturn(Flux.just(sampleProduct));

        // When / Then
        StepVerifier.create(productService.findByCategory("electronics"))
                .expectNextMatches(p -> "electronics".equals(p.getCategory()))
                .verifyComplete();

        verify(productRepository, times(1)).findByCategory("electronics");
    }

    @Test
    @DisplayName("findByCategory completes empty when no products match")
    void findByCategory_completesEmpty_whenNoMatch() {
        // Given: no products in the "toys" category
        when(productRepository.findByCategory("toys")).thenReturn(Flux.empty());

        StepVerifier.create(productService.findByCategory("toys"))
                .verifyComplete();
    }

    // ── findActive ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findActive emits only active products")
    void findActive_emitsActiveProducts() {
        // Given: our sample product is active
        when(productRepository.findByActive(true)).thenReturn(Flux.just(sampleProduct));

        // When / Then
        StepVerifier.create(productService.findActive())
                .expectNextMatches(Product::isActive)
                .verifyComplete();

        verify(productRepository, times(1)).findByActive(true);
    }

    // ── findByPriceRange ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByPriceRange emits products within the given price range")
    void findByPriceRange_emitsProductsInRange() {
        // Given: our sample product (129.99) falls within [100, 200]
        when(productRepository.findByPriceBetween(new BigDecimal("100"), new BigDecimal("200")))
                .thenReturn(Flux.just(sampleProduct));

        // When / Then
        StepVerifier.create(productService.findByPriceRange(new BigDecimal("100"), new BigDecimal("200")))
                .expectNextMatches(p -> p.getPrice().compareTo(new BigDecimal("100")) >= 0
                        && p.getPrice().compareTo(new BigDecimal("200")) <= 0)
                .verifyComplete();

        verify(productRepository, times(1))
                .findByPriceBetween(new BigDecimal("100"), new BigDecimal("200"));
    }

    // ── searchByName ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("searchByName wraps the keyword with % wildcards and delegates to repository")
    void searchByName_wrapsKeywordAndDelegates() {
        // Given: searching for "keyboard" should produce the pattern "%keyboard%"
        when(productRepository.findByNameLike("%keyboard%"))
                .thenReturn(Flux.just(sampleProduct));

        // When / Then
        StepVerifier.create(productService.searchByName("keyboard"))
                .expectNextMatches(p -> p.getName().toLowerCase().contains("keyboard"))
                .verifyComplete();

        // Verify the service correctly added % wildcards before calling the repository
        verify(productRepository, times(1)).findByNameLike("%keyboard%");
    }

    // ── countByCategory ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("countByCategory emits the count of products in the given category")
    void countByCategory_emitsCount() {
        // Given: 3 electronics products
        when(productRepository.countByCategory("electronics")).thenReturn(Mono.just(3L));

        // When / Then
        StepVerifier.create(productService.countByCategory("electronics"))
                .expectNext(3L)
                .verifyComplete();

        verify(productRepository, times(1)).countByCategory("electronics");
    }

    // ── findLowStock ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findLowStock emits products at or below the given threshold")
    void findLowStock_emitsLowStockProducts() {
        // Given: USB-C Hub has stock_quantity=3, which is <= threshold=5
        Product lowStock = new Product("USB-C Hub", "7-in-1", new BigDecimal("49.99"), "electronics", 3, true);
        lowStock.setId(3L);
        when(productRepository.findLowStock(5)).thenReturn(Flux.just(lowStock));

        // When / Then
        StepVerifier.create(productService.findLowStock(5))
                .expectNextMatches(p -> p.getStockQuantity() <= 5)
                .verifyComplete();

        verify(productRepository, times(1)).findLowStock(5);
    }

    // ── create ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create maps the request DTO to a Product and persists it")
    void create_persistsProductAndEmitsSaved() {
        // Given: the mock repository simulates PostgreSQL assigning a BIGSERIAL id
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L); // simulate PostgreSQL-generated BIGSERIAL
            return Mono.just(p);
        });

        // When / Then: the Mono emits the saved product with a generated id
        StepVerifier.create(productService.create(sampleRequest))
                .expectNextMatches(saved -> {
                    return saved.getId().equals(1L)
                            && "Wireless Keyboard".equals(saved.getName())
                            && "electronics".equals(saved.getCategory())
                            && saved.getPrice().compareTo(new BigDecimal("129.99")) == 0
                            && saved.getStockQuantity() == 45
                            && saved.isActive();
                })
                .verifyComplete();

        verify(productRepository, times(1)).save(any(Product.class));
    }

    // ── update ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update emits the updated product when it exists")
    void update_emitsUpdatedProduct_whenFound() {
        // Given: the product is found
        when(productRepository.findById(1L)).thenReturn(Mono.just(sampleProduct));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // New values to apply
        ProductRequest updateRequest = new ProductRequest(
                "Updated Keyboard",
                "New description",
                new BigDecimal("149.99"),
                "accessories",
                20,
                false
        );

        // When / Then
        StepVerifier.create(productService.update(1L, updateRequest))
                .expectNextMatches(updated ->
                        "Updated Keyboard".equals(updated.getName())
                        && "New description".equals(updated.getDescription())
                        && updated.getPrice().compareTo(new BigDecimal("149.99")) == 0
                        && "accessories".equals(updated.getCategory())
                        && updated.getStockQuantity() == 20
                        && !updated.isActive())
                .verifyComplete();

        verify(productRepository, times(1)).findById(1L);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("update completes empty when the product does not exist")
    void update_completesEmpty_whenNotFound() {
        // Given: no product with id=999
        when(productRepository.findById(999L)).thenReturn(Mono.empty());

        // When / Then: the service returns an empty Mono (controller maps to 404)
        StepVerifier.create(productService.update(999L, sampleRequest))
                .verifyComplete();

        verify(productRepository, times(1)).findById(999L);
        // save() must NEVER be called when the product was not found
        verify(productRepository, never()).save(any(Product.class));
    }

    // ── deleteById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById emits true when the product exists and is deleted")
    void deleteById_emitsTrue_whenProductExists() {
        // Given: the product is found and deleteById returns Mono<Void>
        when(productRepository.findById(1L)).thenReturn(Mono.just(sampleProduct));
        when(productRepository.deleteById(1L)).thenReturn(Mono.empty());

        // When / Then: the Mono emits true (product was deleted)
        StepVerifier.create(productService.deleteById(1L))
                .expectNext(true)
                .verifyComplete();

        verify(productRepository, times(1)).findById(1L);
        verify(productRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("deleteById emits false when the product does not exist")
    void deleteById_emitsFalse_whenNotFound() {
        // Given: no product with id=999
        when(productRepository.findById(999L)).thenReturn(Mono.empty());

        // When / Then: the Mono emits false (nothing was deleted)
        StepVerifier.create(productService.deleteById(999L))
                .expectNext(false)
                .verifyComplete();

        verify(productRepository, times(1)).findById(999L);
        // deleteById must NEVER be called when the product was not found
        verify(productRepository, never()).deleteById(anyLong());
    }
}
