package com.example.debeziumcdc.service;

import com.example.debeziumcdc.domain.Product;
import com.example.debeziumcdc.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p>Uses Mockito to isolate the service from its {@link ProductRepository} dependency.
 * No Spring context, no database, no Kafka — only plain Java and Mockito.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>{@link ProductService#createProduct} — creates and saves a new product.</li>
 *   <li>{@link ProductService#findById} — delegates to repository, returns Optional.</li>
 *   <li>{@link ProductService#findAll} — returns all products from repository.</li>
 *   <li>{@link ProductService#updateProduct} — applies partial updates to a found product.</li>
 *   <li>{@link ProductService#updateProduct} — throws EntityNotFoundException when not found.</li>
 *   <li>{@link ProductService#deleteProduct} — deletes existing product.</li>
 *   <li>{@link ProductService#deleteProduct} — throws EntityNotFoundException when not found.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService unit tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    /** A reusable product instance (no real JPA lifecycle — ID set manually via reflection). */
    private Product sampleProduct;

    @BeforeEach
    void setUp() throws Exception {
        // Create a product and set its ID via reflection (JPA normally does this)
        sampleProduct = new Product("Laptop", "High-end laptop", new BigDecimal("1299.99"), 10);

        // Use reflection to set the private id field (simulating JPA-assigned ID)
        var idField = Product.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(sampleProduct, 1L);
    }

    // =========================================================================
    // createProduct
    // =========================================================================

    @Test
    @DisplayName("createProduct saves and returns a new product")
    void createProductSavesAndReturnsProduct() {
        // Arrange: repository.save() returns the same product it receives
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        // Act
        Product result = productService.createProduct(
                "Laptop", "High-end laptop", new BigDecimal("1299.99"), 10);

        // Assert: returned product has the expected values
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Laptop");
        assertThat(result.getPrice()).isEqualByComparingTo("1299.99");
        assertThat(result.getStock()).isEqualTo(10);

        // Verify repository.save() was called exactly once
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct calls repository.save with the correct product fields")
    void createProductPassesCorrectFieldsToRepository() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.createProduct("Widget", "A widget", new BigDecimal("9.99"), 5);

        assertThat(result.getName()).isEqualTo("Widget");
        assertThat(result.getDescription()).isEqualTo("A widget");
        assertThat(result.getPrice()).isEqualByComparingTo("9.99");
        assertThat(result.getStock()).isEqualTo(5);
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Test
    @DisplayName("findById returns the product when it exists")
    void findByIdReturnsProductWhenExists() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        Optional<Product> result = productService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("findById returns empty Optional when product does not exist")
    void findByIdReturnsEmptyWhenNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Product> result = productService.findById(99L);

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // findAll
    // =========================================================================

    @Test
    @DisplayName("findAll returns all products from repository")
    void findAllReturnsAllProducts() {
        Product second = new Product("Mouse", "Wireless mouse", new BigDecimal("29.99"), 50);
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct, second));

        List<Product> result = productService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Product::getName)
                .containsExactlyInAnyOrder("Laptop", "Mouse");
    }

    @Test
    @DisplayName("findAll returns empty list when catalogue is empty")
    void findAllReturnsEmptyListWhenCatalogueEmpty() {
        when(productRepository.findAll()).thenReturn(List.of());

        List<Product> result = productService.findAll();

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // updateProduct
    // =========================================================================

    @Test
    @DisplayName("updateProduct applies all provided field updates")
    void updateProductAppliesAllUpdates() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.updateProduct(
                1L, "Updated Laptop", "New description", new BigDecimal("999.00"), 20);

        assertThat(result.getName()).isEqualTo("Updated Laptop");
        assertThat(result.getDescription()).isEqualTo("New description");
        assertThat(result.getPrice()).isEqualByComparingTo("999.00");
        assertThat(result.getStock()).isEqualTo(20);
    }

    @Test
    @DisplayName("updateProduct with null fields leaves those fields unchanged")
    void updateProductWithNullFieldsLeavesFieldsUnchanged() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // Only update name; leave price and stock as null (unchanged)
        Product result = productService.updateProduct(1L, "New Name", null, null, null);

        assertThat(result.getName()).isEqualTo("New Name");
        // Original values preserved because null was passed
        assertThat(result.getPrice()).isEqualByComparingTo("1299.99");
        assertThat(result.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("updateProduct throws EntityNotFoundException when product does not exist")
    void updateProductThrowsWhenNotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productService.updateProduct(999L, "X", null, null, null))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("999");
    }

    // =========================================================================
    // deleteProduct
    // =========================================================================

    @Test
    @DisplayName("deleteProduct calls repository.deleteById when product exists")
    void deleteProductCallsDeleteByIdWhenExists() {
        when(productRepository.existsById(1L)).thenReturn(true);
        doNothing().when(productRepository).deleteById(1L);

        productService.deleteProduct(1L);

        verify(productRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("deleteProduct throws EntityNotFoundException when product does not exist")
    void deleteProductThrowsWhenNotFound() {
        when(productRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> productService.deleteProduct(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("999");

        // deleteById must NOT be called when the product does not exist
        verify(productRepository, never()).deleteById(any());
    }
}
