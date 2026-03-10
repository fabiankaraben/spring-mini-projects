package com.example.softdelete.unit;

import com.example.softdelete.domain.Product;
import com.example.softdelete.dto.ProductRequest;
import com.example.softdelete.exception.ProductNotFoundException;
import com.example.softdelete.repository.ProductRepository;
import com.example.softdelete.service.ProductService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProductService}.
 *
 * <h2>Testing approach</h2>
 * <p>These tests exercise the <em>service layer logic</em> in isolation.  The
 * {@link ProductRepository} is mocked via Mockito so no database or Spring context
 * is started.  This makes the tests extremely fast and focused on the business rules.</p>
 *
 * <h2>What is tested here</h2>
 * <ul>
 *   <li>CRUD operations: create, findAll, findById, update, delete.</li>
 *   <li>Soft-delete transparency: the service delegates to the repository and trusts
 *       that the {@code @SQLRestriction} filter is working (that is an integration
 *       concern verified in the integration tests).</li>
 *   <li>Error conditions: {@link ProductNotFoundException} is thrown for missing IDs.</li>
 *   <li>Restore flow: verifies the correct sequence of calls to
 *       {@code findDeletedById} and {@code restoreById}.</li>
 * </ul>
 *
 * <h2>Why no database?</h2>
 * <p>Unit tests should be fast and independent.  Testing that {@code @SQLDelete} and
 * {@code @SQLRestriction} actually work requires a real database – that is the job
 * of the integration tests in {@link com.example.softdelete.integration.SoftDeleteIntegrationTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService – Unit Tests")
class ProductServiceTest {

    /**
     * Mocked repository – no real database is created; all calls are intercepted
     * by Mockito and return whatever we configure in each test.
     */
    @Mock
    private ProductRepository productRepository;

    /**
     * The class under test. {@code @InjectMocks} creates an instance and injects
     * the mocked {@link ProductRepository} into the constructor.
     */
    @InjectMocks
    private ProductService productService;

    /** A reusable active product instance created before each test. */
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        // Build a sample product using the public constructor.
        // The @PrePersist-managed timestamps are not set here because
        // we're not going through JPA; they are only relevant in integration tests.
        sampleProduct = new Product("Laptop", "A powerful laptop", new BigDecimal("999.99"), "Electronics");
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll() should return list from repository")
    void findAll_shouldReturnAllProducts() {
        // Arrange: repository returns our sample product
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct));

        // Act
        List<Product> result = productService.findAll();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Laptop");
        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAll() should return empty list when no products exist")
    void findAll_shouldReturnEmptyList_whenNoProducts() {
        when(productRepository.findAll()).thenReturn(List.of());

        List<Product> result = productService.findAll();

        assertThat(result).isEmpty();
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById() should return product when found")
    void findById_shouldReturnProduct_whenFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        Product result = productService.findById(1L);

        assertThat(result.getName()).isEqualTo("Laptop");
        verify(productRepository).findById(1L);
    }

    @Test
    @DisplayName("findById() should throw ProductNotFoundException when product does not exist")
    void findById_shouldThrow_whenProductNotFound() {
        // This simulates both: truly absent products AND soft-deleted ones
        // (both are invisible via the @SQLRestriction filter at the DB level)
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create() should save and return new product")
    void create_shouldSaveAndReturnProduct() {
        ProductRequest request = new ProductRequest(
                "Phone", "A smartphone", new BigDecimal("599.99"), "Electronics");
        Product saved = new Product("Phone", "A smartphone", new BigDecimal("599.99"), "Electronics");

        when(productRepository.save(any(Product.class))).thenReturn(saved);

        Product result = productService.create(request);

        assertThat(result.getName()).isEqualTo("Phone");
        assertThat(result.getPrice()).isEqualByComparingTo("599.99");
        verify(productRepository).save(any(Product.class));
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update() should modify and save existing product")
    void update_shouldUpdateAndSaveProduct() {
        ProductRequest request = new ProductRequest(
                "Gaming Laptop", "Updated description", new BigDecimal("1299.99"), "Electronics");

        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        Product result = productService.update(1L, request);

        // Verify the entity was mutated before save
        assertThat(sampleProduct.getName()).isEqualTo("Gaming Laptop");
        assertThat(sampleProduct.getPrice()).isEqualByComparingTo("1299.99");
        verify(productRepository).save(sampleProduct);
    }

    @Test
    @DisplayName("update() should throw ProductNotFoundException for non-existent product")
    void update_shouldThrow_whenProductNotFound() {
        ProductRequest request = new ProductRequest(
                "Name", "Desc", new BigDecimal("10.00"), "Cat");

        when(productRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(42L, request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("42");

        // Verify save was never called if the product wasn't found
        verify(productRepository, never()).save(any());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete() should call repository deleteById (triggering @SQLDelete)")
    void delete_shouldCallRepositoryDeleteById() {
        // The service first checks the product exists, then delegates to deleteById.
        // At the repository level, deleteById triggers the custom @SQLDelete SQL
        // (UPDATE ... SET deleted=true) rather than a physical DELETE.
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        productService.delete(1L);

        // Verify that deleteById was invoked once (which triggers @SQLDelete)
        verify(productRepository).deleteById(1L);
    }

    @Test
    @DisplayName("delete() should throw ProductNotFoundException for non-existent or already-deleted product")
    void delete_shouldThrow_whenProductNotFound() {
        // If the product is soft-deleted, @SQLRestriction makes it invisible,
        // so findById returns empty – same as if it never existed.
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");

        verify(productRepository, never()).deleteById(any());
    }

    // ── restore ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("restore() should call restoreById and return the re-activated product")
    void restore_shouldCallRestoreByIdAndReturnProduct() {
        // The deleted product is found via the native-SQL bypass query
        when(productRepository.findDeletedById(1L)).thenReturn(Optional.of(sampleProduct));
        // After restore, the normal findById should return the now-active product
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        doNothing().when(productRepository).restoreById(1L);

        Product result = productService.restore(1L);

        assertThat(result.getName()).isEqualTo("Laptop");
        verify(productRepository).findDeletedById(1L);
        verify(productRepository).restoreById(1L);
        verify(productRepository).findById(1L);
    }

    @Test
    @DisplayName("restore() should throw ProductNotFoundException when no deleted product with that ID exists")
    void restore_shouldThrow_whenDeletedProductNotFound() {
        when(productRepository.findDeletedById(55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.restore(55L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("55");

        verify(productRepository, never()).restoreById(any());
    }

    // ── searchByName ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("searchByName() should delegate to repository and return results")
    void searchByName_shouldReturnMatchingProducts() {
        when(productRepository.findByNameContainingIgnoreCase("lap"))
                .thenReturn(List.of(sampleProduct));

        List<Product> result = productService.searchByName("lap");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Laptop");
    }

    // ── findByCategory ────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByCategory() should return products in the given category")
    void findByCategory_shouldReturnProductsInCategory() {
        when(productRepository.findByCategory("Electronics"))
                .thenReturn(List.of(sampleProduct));

        List<Product> result = productService.findByCategory("Electronics");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("Electronics");
    }

    // ── findAllDeleted / countDeleted ─────────────────────────────────────────

    @Test
    @DisplayName("findAllDeleted() should return list of soft-deleted products")
    void findAllDeleted_shouldReturnDeletedProducts() {
        Product deletedProduct = new Product("Old Phone", "Discontinued", new BigDecimal("199.99"), "Electronics");
        when(productRepository.findAllDeleted()).thenReturn(List.of(deletedProduct));

        List<Product> result = productService.findAllDeleted();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Old Phone");
    }

    @Test
    @DisplayName("countDeleted() should return count from repository")
    void countDeleted_shouldReturnCount() {
        when(productRepository.countDeleted()).thenReturn(3L);

        long count = productService.countDeleted();

        assertThat(count).isEqualTo(3L);
    }
}
