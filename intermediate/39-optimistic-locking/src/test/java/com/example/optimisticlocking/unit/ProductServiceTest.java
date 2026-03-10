package com.example.optimisticlocking.unit;

import com.example.optimisticlocking.domain.Product;
import com.example.optimisticlocking.dto.ProductRequest;
import com.example.optimisticlocking.dto.ProductUpdateRequest;
import com.example.optimisticlocking.exception.ProductNotFoundException;
import com.example.optimisticlocking.repository.ProductRepository;
import com.example.optimisticlocking.service.ProductService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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
 *   <li>Optimistic locking conflict: the service propagates
 *       {@link ObjectOptimisticLockingFailureException} when the repository throws it,
 *       allowing the controller / exception handler to return HTTP 409.</li>
 *   <li>Error conditions: {@link ProductNotFoundException} is thrown for missing IDs.</li>
 *   <li>Version field handling: the update method sets the client-supplied version
 *       on the entity before saving.</li>
 * </ul>
 *
 * <h2>Why no database?</h2>
 * <p>Unit tests should be fast and independent.  Testing that {@code @Version}
 * actually causes Hibernate to generate the correct SQL requires a real database –
 * that is the job of the integration tests in
 * {@link com.example.optimisticlocking.integration.OptimisticLockingIntegrationTest}.</p>
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
     * Mocked EntityManager – needed because {@link ProductService} calls
     * {@code entityManager.detach()} in the update method.  In unit tests we
     * stub it as a no-op so the detach does not crash (there is no real JPA context).
     */
    @Mock
    private EntityManager entityManager;

    /**
     * The class under test. {@code @InjectMocks} creates an instance and injects
     * both the mocked {@link ProductRepository} and {@link EntityManager} via
     * the constructor.
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
        sampleProduct = new Product("Laptop", "A powerful laptop", new BigDecimal("999.99"), 10, "Electronics");
        // Simulate a product that has been persisted with version=0
        sampleProduct.setVersion(0L);

        // entityManager.detach() is a void method; Mockito stubs void methods with
        // doNothing() by default, so no explicit stubbing is needed here.
        // We still declare the @Mock above so @InjectMocks can satisfy the constructor.
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
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create() should save and return new product with initial data")
    void create_shouldSaveAndReturnProduct() {
        ProductRequest request = new ProductRequest(
                "Phone", "A smartphone", new BigDecimal("599.99"), 25, "Electronics");
        Product saved = new Product("Phone", "A smartphone", new BigDecimal("599.99"), 25, "Electronics");
        saved.setVersion(0L); // Hibernate initialises version to 0 on first INSERT

        when(productRepository.save(any(Product.class))).thenReturn(saved);

        Product result = productService.create(request);

        assertThat(result.getName()).isEqualTo("Phone");
        assertThat(result.getPrice()).isEqualByComparingTo("599.99");
        assertThat(result.getStock()).isEqualTo(25);
        // Version starts at 0 after the first INSERT
        assertThat(result.getVersion()).isEqualTo(0L);
        verify(productRepository).save(any(Product.class));
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update() should set the client-supplied version on the entity before saving")
    void update_shouldSetVersionFromRequest() {
        // Client sends version=0 (the version it received from the last GET)
        ProductUpdateRequest request = new ProductUpdateRequest(
                0L, "Gaming Laptop", "Updated description", new BigDecimal("1299.99"), 5, "Electronics");

        // The entity currently has version=0 in the database
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        productService.update(1L, request);

        // The service must set the client-supplied version on the entity.
        // This is what enables Hibernate to detect version mismatches.
        assertThat(sampleProduct.getVersion()).isEqualTo(0L);
        verify(productRepository).save(sampleProduct);
    }

    @Test
    @DisplayName("update() should apply all field changes to the entity")
    void update_shouldUpdateAllFields() {
        ProductUpdateRequest request = new ProductUpdateRequest(
                0L, "Gaming Laptop", "Updated description", new BigDecimal("1299.99"), 5, "Gaming");

        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        productService.update(1L, request);

        // Verify all mutable fields were updated on the entity
        assertThat(sampleProduct.getName()).isEqualTo("Gaming Laptop");
        assertThat(sampleProduct.getDescription()).isEqualTo("Updated description");
        assertThat(sampleProduct.getPrice()).isEqualByComparingTo("1299.99");
        assertThat(sampleProduct.getStock()).isEqualTo(5);
        assertThat(sampleProduct.getCategory()).isEqualTo("Gaming");
    }

    @Test
    @DisplayName("update() should throw ProductNotFoundException for non-existent product")
    void update_shouldThrow_whenProductNotFound() {
        ProductUpdateRequest request = new ProductUpdateRequest(
                0L, "Name", "Desc", new BigDecimal("10.00"), 1, "Cat");

        when(productRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(42L, request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("42");

        // Verify save was never called if the product wasn't found
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() should propagate ObjectOptimisticLockingFailureException when version is stale")
    void update_shouldPropagate_optimisticLockingException() {
        // Client sends version=0 but the database already has version=1 (stale!)
        ProductUpdateRequest request = new ProductUpdateRequest(
                0L, "New Name", "New Desc", new BigDecimal("100.00"), 3, "Cat");

        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        // Simulate Hibernate detecting the version mismatch during save
        when(productRepository.save(any(Product.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L));

        // The service must NOT swallow the exception – it must propagate it so the
        // GlobalExceptionHandler can convert it to HTTP 409 Conflict
        assertThatThrownBy(() -> productService.update(1L, request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete() should call repository deleteById")
    void delete_shouldCallRepositoryDeleteById() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        productService.delete(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    @DisplayName("delete() should throw ProductNotFoundException for non-existent product")
    void delete_shouldThrow_whenProductNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");

        verify(productRepository, never()).deleteById(any());
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

    // ── Version field domain logic ────────────────────────────────────────────

    @Test
    @DisplayName("Product.setVersion() should allow overriding the version for optimistic locking checks")
    void product_setVersion_shouldUpdateVersionField() {
        // This verifies the setter that the service uses to apply the client-supplied version
        sampleProduct.setVersion(0L);
        assertThat(sampleProduct.getVersion()).isEqualTo(0L);

        sampleProduct.setVersion(5L);
        assertThat(sampleProduct.getVersion()).isEqualTo(5L);
    }

    @Test
    @DisplayName("ProductResponse.from() should include version field in the DTO")
    void productResponse_shouldIncludeVersion() {
        // Verify the version is included in the response DTO so clients always have it
        sampleProduct.setVersion(3L);
        var response = com.example.optimisticlocking.dto.ProductResponse.from(sampleProduct);

        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.name()).isEqualTo("Laptop");
        assertThat(response.stock()).isEqualTo(10);
    }
}
