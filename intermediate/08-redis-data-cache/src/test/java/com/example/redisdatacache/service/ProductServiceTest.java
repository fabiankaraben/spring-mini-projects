package com.example.redisdatacache.service;

import com.example.redisdatacache.domain.Product;
import com.example.redisdatacache.dto.ProductRequest;
import com.example.redisdatacache.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p>These tests exercise the service's business logic in pure isolation:
 * <ul>
 *   <li>The {@link ProductRepository} is replaced with a Mockito mock so no real
 *       data store or sleep delay is involved.</li>
 *   <li>No Spring context is loaded — tests run in milliseconds.</li>
 *   <li>The cache annotations ({@code @Cacheable}, {@code @CachePut},
 *       {@code @CacheEvict}) are <em>not</em> active here because they are
 *       implemented via Spring AOP proxies which require the Spring container.
 *       Integration tests (see {@link com.example.redisdatacache.ProductIntegrationTest})
 *       cover the caching behaviour end-to-end.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService unit tests")
class ProductServiceTest {

    /** Mocked repository — no real data store, no simulated delay. */
    @Mock
    private ProductRepository productRepository;

    /**
     * The class under test.
     * {@code @InjectMocks} asks Mockito to create an instance and inject the
     * {@code @Mock} fields into it via constructor injection.
     */
    @InjectMocks
    private ProductService productService;

    // ── Shared test fixtures ──────────────────────────────────────────────────────

    private Product sampleProduct;
    private ProductRequest sampleRequest;

    @BeforeEach
    void setUp() {
        // A pre-built product returned by the mock repository
        sampleProduct = new Product(
                1L,
                "Test Laptop",
                "A powerful test laptop",
                new BigDecimal("999.99"),
                "electronics"
        );

        // A DTO that would arrive in an HTTP request body
        sampleRequest = new ProductRequest(
                "Test Laptop",
                "A powerful test laptop",
                new BigDecimal("999.99"),
                "electronics"
        );
    }

    // ── findById ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns product when it exists")
    void findById_returnsProduct_whenExists() {
        // Given: the repository returns the sample product for ID 1
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        // When: the service is asked for product 1
        Optional<Product> result = productService.findById(1L);

        // Then: the product is present and the repository was called exactly once
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        assertThat(result.get().getName()).isEqualTo("Test Laptop");
        verify(productRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("findById returns empty Optional when product does not exist")
    void findById_returnsEmpty_whenNotFound() {
        // Given: the repository returns nothing for ID 999
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Product> result = productService.findById(999L);

        // Then: the result is empty and the repository was still consulted
        assertThat(result).isEmpty();
        verify(productRepository, times(1)).findById(999L);
    }

    // ── findAll ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll returns all products from the repository")
    void findAll_returnsAllProducts() {
        // Given: the repository holds two products
        Product second = new Product(2L, "Mouse", "Wireless", new BigDecimal("29.99"), "electronics");
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct, second));

        // When
        List<Product> results = productService.findAll();

        // Then: both products are returned
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Product::getId).containsExactlyInAnyOrder(1L, 2L);
        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAll returns empty list when repository is empty")
    void findAll_returnsEmptyList_whenRepositoryIsEmpty() {
        // Given: the repository has no products
        when(productRepository.findAll()).thenReturn(List.of());

        // When
        List<Product> results = productService.findAll();

        // Then: an empty list is returned (not null)
        assertThat(results).isNotNull().isEmpty();
    }

    // ── create ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create persists a new product and returns it with an assigned ID")
    void create_persistsProductAndReturnsWithId() {
        // Given: the repository assigns ID 10 when saving a new product
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(10L); // simulate auto-increment
            return p;
        });

        // When
        Product created = productService.create(sampleRequest);

        // Then: the saved product reflects the request data and has an ID
        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(10L);
        assertThat(created.getName()).isEqualTo(sampleRequest.getName());
        assertThat(created.getDescription()).isEqualTo(sampleRequest.getDescription());
        assertThat(created.getPrice()).isEqualByComparingTo(sampleRequest.getPrice());
        assertThat(created.getCategory()).isEqualTo(sampleRequest.getCategory());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    // ── update ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update returns updated product when it exists")
    void update_returnsUpdatedProduct_whenExists() {
        // Given: the repository finds the existing product for the update read
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        // The save call returns the mutated product
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // A request with new values
        ProductRequest updateRequest = new ProductRequest(
                "Updated Laptop",
                "Even more powerful",
                new BigDecimal("1199.99"),
                "electronics"
        );

        // When
        Product updated = productService.update(1L, updateRequest);

        // Then: the returned product has the new values
        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("Updated Laptop");
        assertThat(updated.getDescription()).isEqualTo("Even more powerful");
        assertThat(updated.getPrice()).isEqualByComparingTo("1199.99");

        // The repository should have been called once to find and once to save
        verify(productRepository, times(1)).findById(1L);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("update returns null when product does not exist")
    void update_returnsNull_whenProductNotFound() {
        // Given: the repository returns nothing for ID 999
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Product updated = productService.update(999L, sampleRequest);

        // Then: null is returned and save is never called
        assertThat(updated).isNull();
        verify(productRepository, times(1)).findById(999L);
        verify(productRepository, never()).save(any(Product.class));
    }

    // ── deleteById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById returns true when product exists and is deleted")
    void deleteById_returnsTrue_whenDeleted() {
        // Given: the repository successfully deletes the product
        when(productRepository.deleteById(1L)).thenReturn(true);

        // When
        boolean result = productService.deleteById(1L);

        // Then: true is returned and the repository was called
        assertThat(result).isTrue();
        verify(productRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("deleteById returns false when product does not exist")
    void deleteById_returnsFalse_whenNotFound() {
        // Given: the repository reports that no product was deleted
        when(productRepository.deleteById(999L)).thenReturn(false);

        // When
        boolean result = productService.deleteById(999L);

        // Then: false is returned
        assertThat(result).isFalse();
        verify(productRepository, times(1)).deleteById(999L);
    }
}
