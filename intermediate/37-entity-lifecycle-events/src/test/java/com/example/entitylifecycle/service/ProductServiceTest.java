package com.example.entitylifecycle.service;

import com.example.entitylifecycle.dto.ProductRequest;
import com.example.entitylifecycle.dto.ProductResponse;
import com.example.entitylifecycle.entity.Product;
import com.example.entitylifecycle.exception.ProductNotFoundException;
import com.example.entitylifecycle.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p><b>Testing approach:</b>
 * <ul>
 *   <li>Uses Mockito to mock the {@link ProductRepository} dependency so no
 *       database or Spring context is needed — tests run fast and in isolation.</li>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} integrates Mockito's lifecycle
 *       with JUnit 5, automatically initialising mocks and verifying them after
 *       each test without needing {@code MockitoAnnotations.openMocks(this)}.</li>
 *   <li>Uses AssertJ for fluent, readable assertions.</li>
 *   <li>BDD-style {@code given / when / then} pattern keeps tests easy to follow.</li>
 * </ul>
 *
 * <p><b>What these tests cover:</b>
 * <ul>
 *   <li>Happy-path CRUD operations (findAll, findById, findBySlug, findDiscounted,
 *       create, update, delete).</li>
 *   <li>Error paths — {@link ProductNotFoundException} is thrown when the requested
 *       product does not exist.</li>
 *   <li>The mapping logic from entity to DTO (including lifecycle-event-populated
 *       fields: slug, discountedPrice, createdAt, updatedAt).</li>
 * </ul>
 *
 * <p><b>Note:</b> The actual JPA lifecycle callbacks ({@code @PrePersist},
 * {@code @PostLoad}, etc.) are NOT exercised in these unit tests because there is
 * no JPA context. Those behaviours are verified in the integration tests
 * ({@code ProductRepositoryIntegrationTest}) which use a real database via
 * Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    // Shared test fixtures — rebuilt before each test to guarantee isolation
    private Product sampleProduct;
    private Instant sampleCreatedAt;
    private Instant sampleUpdatedAt;

    /**
     * Sets up a fully populated {@link Product} fixture that simulates what the
     * repository would return after a successful INSERT (i.e. the entity has
     * an ID, a slug, timestamps, and a discountedPrice — all set by lifecycle
     * callbacks in production, injected here via reflection for the unit test).
     */
    @BeforeEach
    void setUp() throws Exception {
        sampleCreatedAt = Instant.parse("2024-01-01T10:00:00Z");
        sampleUpdatedAt = Instant.parse("2024-01-02T12:00:00Z");

        sampleProduct = new Product(
                "Wireless Headphones", "Great sound", new BigDecimal("99.99"), 20);

        // Inject ID via reflection (no public setter — ID is DB-generated)
        var idField = Product.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(sampleProduct, 1L);

        // Inject slug via reflection (set by @PrePersist in production)
        var slugField = Product.class.getDeclaredField("slug");
        slugField.setAccessible(true);
        slugField.set(sampleProduct, "wireless-headphones");

        // Inject discountedPrice via reflection (set by @PrePersist / @PostLoad)
        var discountedPriceField = Product.class.getDeclaredField("discountedPrice");
        discountedPriceField.setAccessible(true);
        discountedPriceField.set(sampleProduct, new BigDecimal("79.99"));

        // Inject timestamps via reflection (set by @PrePersist in production)
        var createdAtField = Product.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(sampleProduct, sampleCreatedAt);

        var updatedAtField = Product.class.getDeclaredField("updatedAt");
        updatedAtField.setAccessible(true);
        updatedAtField.set(sampleProduct, sampleUpdatedAt);
    }

    // =========================================================================
    // findAll
    // =========================================================================

    @Test
    @DisplayName("findAll returns list of ProductResponse DTOs mapped from all entities")
    void findAll_returnsAllProductsAsDtos() {
        // given — repository returns a single product
        given(productRepository.findAll()).willReturn(List.of(sampleProduct));

        // when
        List<ProductResponse> result = productService.findAll();

        // then
        assertThat(result).hasSize(1);
        ProductResponse response = result.get(0);
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Wireless Headphones");
        assertThat(response.slug()).isEqualTo("wireless-headphones");
        assertThat(response.discountPercent()).isEqualTo(20);
        // Lifecycle-event-populated fields must be forwarded from the entity to the DTO
        assertThat(response.discountedPrice()).isEqualByComparingTo("79.99");
        assertThat(response.createdAt()).isEqualTo(sampleCreatedAt);
        assertThat(response.updatedAt()).isEqualTo(sampleUpdatedAt);
    }

    @Test
    @DisplayName("findAll returns empty list when no products exist")
    void findAll_returnsEmptyList_whenNoProducts() {
        // given
        given(productRepository.findAll()).willReturn(List.of());

        // when
        List<ProductResponse> result = productService.findAll();

        // then
        assertThat(result).isEmpty();
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Test
    @DisplayName("findById returns the correct ProductResponse DTO when product exists")
    void findById_returnsDto_whenProductExists() {
        // given
        given(productRepository.findById(1L)).willReturn(Optional.of(sampleProduct));

        // when
        ProductResponse result = productService.findById(1L);

        // then — all lifecycle-event-populated fields must be present in the DTO
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Wireless Headphones");
        assertThat(result.slug()).isEqualTo("wireless-headphones");
        assertThat(result.discountedPrice()).isEqualByComparingTo("79.99");
        assertThat(result.createdAt()).isEqualTo(sampleCreatedAt);
        assertThat(result.updatedAt()).isEqualTo(sampleUpdatedAt);
    }

    @Test
    @DisplayName("findById throws ProductNotFoundException when product does not exist")
    void findById_throwsNotFoundException_whenProductNotFound() {
        // given
        given(productRepository.findById(99L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
    }

    // =========================================================================
    // findBySlug
    // =========================================================================

    @Test
    @DisplayName("findBySlug returns the product DTO when slug exists")
    void findBySlug_returnsDto_whenSlugExists() {
        // given
        given(productRepository.findBySlug("wireless-headphones"))
                .willReturn(Optional.of(sampleProduct));

        // when
        ProductResponse result = productService.findBySlug("wireless-headphones");

        // then
        assertThat(result.slug()).isEqualTo("wireless-headphones");
        assertThat(result.name()).isEqualTo("Wireless Headphones");
    }

    @Test
    @DisplayName("findBySlug throws ProductNotFoundException when slug does not exist")
    void findBySlug_throwsNotFoundException_whenSlugNotFound() {
        // given
        given(productRepository.findBySlug("nonexistent-slug")).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> productService.findBySlug("nonexistent-slug"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // =========================================================================
    // findDiscounted
    // =========================================================================

    @Test
    @DisplayName("findDiscounted returns only products with a discount > 0")
    void findDiscounted_returnsDiscountedProducts() {
        // given
        given(productRepository.findByDiscountPercentGreaterThan(0))
                .willReturn(List.of(sampleProduct));

        // when
        List<ProductResponse> result = productService.findDiscounted();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).discountPercent()).isEqualTo(20);
        assertThat(result.get(0).discountedPrice()).isEqualByComparingTo("79.99");
    }

    // =========================================================================
    // create
    // =========================================================================

    @Test
    @DisplayName("create saves a new Product and returns the response DTO with lifecycle fields")
    void create_savesProductAndReturnsDtoWithLifecycleFields() {
        // given — the request DTO sent by the client
        ProductRequest request = new ProductRequest(
                "Wireless Headphones", "Great sound", new BigDecimal("99.99"), 20);

        // Simulate what the repository returns after INSERT: the entity already
        // has its slug, timestamps, and discountedPrice set by @PrePersist.
        given(productRepository.save(any(Product.class))).willReturn(sampleProduct);

        // when
        ProductResponse result = productService.create(request);

        // then — the service must have called save exactly once
        then(productRepository).should().save(any(Product.class));

        // The returned DTO must carry all lifecycle-event-populated fields
        assertThat(result.slug()).isNotNull().isNotBlank();
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();
        assertThat(result.discountedPrice()).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    // =========================================================================
    // update
    // =========================================================================

    @Test
    @DisplayName("update modifies the product fields and returns the updated DTO")
    void update_modifiesProductAndReturnsUpdatedDto() throws Exception {
        // given — prepare an updated-state product to be returned by save()
        Product updatedProduct = new Product(
                "Updated Headphones", "Even better", new BigDecimal("89.99"), 10);

        var idField = Product.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(updatedProduct, 1L);

        // slug must stay the same (updatable = false)
        var slugField = Product.class.getDeclaredField("slug");
        slugField.setAccessible(true);
        slugField.set(updatedProduct, "wireless-headphones");

        var discountedPriceField = Product.class.getDeclaredField("discountedPrice");
        discountedPriceField.setAccessible(true);
        discountedPriceField.set(updatedProduct, new BigDecimal("80.99"));

        Instant updatedInstant = Instant.parse("2024-06-01T09:00:00Z");
        var createdAtField = Product.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(updatedProduct, sampleCreatedAt); // createdAt must NOT change

        var updatedAtField = Product.class.getDeclaredField("updatedAt");
        updatedAtField.setAccessible(true);
        updatedAtField.set(updatedProduct, updatedInstant); // updatedAt must be refreshed

        given(productRepository.findById(1L)).willReturn(Optional.of(sampleProduct));
        given(productRepository.save(any(Product.class))).willReturn(updatedProduct);

        ProductRequest updateRequest = new ProductRequest(
                "Updated Headphones", "Even better", new BigDecimal("89.99"), 10);

        // when
        ProductResponse result = productService.update(1L, updateRequest);

        // then
        assertThat(result.name()).isEqualTo("Updated Headphones");
        // slug must stay unchanged (updatable = false column)
        assertThat(result.slug()).isEqualTo("wireless-headphones");
        // createdAt is preserved; updatedAt is refreshed
        assertThat(result.createdAt()).isEqualTo(sampleCreatedAt);
        assertThat(result.updatedAt()).isEqualTo(updatedInstant);
        assertThat(result.updatedAt()).isAfter(result.createdAt());
    }

    @Test
    @DisplayName("update throws ProductNotFoundException when product does not exist")
    void update_throwsNotFoundException_whenProductNotFound() {
        // given
        given(productRepository.findById(99L)).willReturn(Optional.empty());
        ProductRequest request = new ProductRequest("Name", "Desc", new BigDecimal("10.00"), 0);

        // when / then
        assertThatThrownBy(() -> productService.update(99L, request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");

        // The repository save must never be called when the entity is not found
        then(productRepository).should(never()).save(any());
    }

    // =========================================================================
    // delete
    // =========================================================================

    @Test
    @DisplayName("delete removes the product when it exists")
    void delete_deletesProduct_whenExists() {
        // given
        given(productRepository.existsById(1L)).willReturn(true);

        // when
        productService.delete(1L);

        // then — deleteById must be called exactly once with the correct ID
        then(productRepository).should().deleteById(1L);
    }

    @Test
    @DisplayName("delete throws ProductNotFoundException when product does not exist")
    void delete_throwsNotFoundException_whenProductNotFound() {
        // given
        given(productRepository.existsById(99L)).willReturn(false);

        // when / then
        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");

        // deleteById must never be called when the entity does not exist
        then(productRepository).should(never()).deleteById(any());
    }
}
