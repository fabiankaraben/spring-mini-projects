package com.example.grpc.service;

import com.example.grpc.domain.Product;
import com.example.grpc.domain.ProductStatus;
import com.example.grpc.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p>These tests focus purely on the business logic in the service layer,
 * without starting a Spring context, a gRPC server, or a database.
 *
 * <p>Testing strategy:
 * <ul>
 *   <li>{@link ExtendWith(MockitoExtension.class)} — uses Mockito JUnit 5 extension
 *       to initialize mocks and inject them.</li>
 *   <li>{@link Mock} — creates a mock of {@link ProductRepository} so we can
 *       control its behavior in each test scenario without hitting a real database.</li>
 *   <li>{@link InjectMocks} — creates an instance of {@link ProductService} and injects
 *       the mocked repository into it via constructor injection.</li>
 *   <li>Nested test classes group related scenarios for better readability.</li>
 * </ul>
 *
 * <p>Why unit tests matter here:
 *   The service layer contains non-trivial business rules (status transitions, stock
 *   validation). Unit tests let us verify each rule in isolation, quickly, without
 *   infrastructure overhead. Integration tests then verify the full stack end-to-end.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    /**
     * Mocked repository — all method calls are intercepted by Mockito.
     * We configure return values with {@code when(...).thenReturn(...)} per test.
     */
    @Mock
    private ProductRepository productRepository;

    /**
     * The class under test. Mockito injects the mocked repository via constructor.
     */
    @InjectMocks
    private ProductService productService;

    // =========================================================================
    // Tests for findById()
    // =========================================================================

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("returns the product when it exists")
        void returnsProductWhenFound() {
            // Given: a product exists in the repository with ID 1.
            Product product = buildProduct(1L, "Keyboard", "electronics", 89.99, 100, ProductStatus.ACTIVE);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // When: we look up the product by ID.
            Optional<Product> result = productService.findById(1L);

            // Then: the returned Optional contains the expected product.
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
            assertThat(result.get().getName()).isEqualTo("Keyboard");
        }

        @Test
        @DisplayName("returns empty Optional when product does not exist")
        void returnsEmptyWhenNotFound() {
            // Given: no product exists with ID 99.
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            // When: we look up a non-existent product.
            Optional<Product> result = productService.findById(99L);

            // Then: the result is an empty Optional (no exception).
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Tests for findAll()
    // =========================================================================

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("returns all non-discontinued products when no category filter is given")
        void returnsAllProductsWithoutCategoryFilter() {
            // Given: two active products are returned by the repository.
            List<Product> products = List.of(
                    buildProduct(1L, "Chair", "furniture", 299.00, 10, ProductStatus.ACTIVE),
                    buildProduct(2L, "Desk", "furniture", 499.00, 5, ProductStatus.ACTIVE)
            );
            when(productRepository.findByStatusNotOrderByNameAsc(ProductStatus.DISCONTINUED))
                    .thenReturn(products);

            // When: no category filter is provided (null).
            List<Product> result = productService.findAll(null);

            // Then: the full list is returned.
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Chair");
        }

        @Test
        @DisplayName("returns only products of the given category (excluding discontinued)")
        void returnsFilteredProductsByCategory() {
            // Given: one electronics product is returned when filtering by "electronics".
            List<Product> electronics = List.of(
                    buildProduct(3L, "Monitor", "electronics", 499.99, 20, ProductStatus.ACTIVE)
            );
            when(productRepository.findByCategoryAndStatusNotOrderByNameAsc(
                    eq("electronics"), eq(ProductStatus.DISCONTINUED)))
                    .thenReturn(electronics);

            // When: we request products filtered by the "electronics" category.
            List<Product> result = productService.findAll("electronics");

            // Then: only the electronics product is returned.
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategory()).isEqualTo("electronics");
        }

        @Test
        @DisplayName("returns empty list when no matching products exist")
        void returnsEmptyListWhenNoMatch() {
            // Given: no products are found for the "books" category.
            when(productRepository.findByCategoryAndStatusNotOrderByNameAsc(
                    eq("books"), eq(ProductStatus.DISCONTINUED)))
                    .thenReturn(List.of());

            // When: we query for the "books" category.
            List<Product> result = productService.findAll("books");

            // Then: the result is an empty list (not null).
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("treats blank category string the same as null (no filter)")
        void treatsBlanCategoryAsNoFilter() {
            // Given: the repository returns products when no status filter is applied.
            when(productRepository.findByStatusNotOrderByNameAsc(ProductStatus.DISCONTINUED))
                    .thenReturn(List.of());

            // When: a blank string is passed as the category.
            productService.findAll("   ");

            // Then: the no-filter path is used (category-specific query NOT called).
            verify(productRepository).findByStatusNotOrderByNameAsc(ProductStatus.DISCONTINUED);
            verify(productRepository, never())
                    .findByCategoryAndStatusNotOrderByNameAsc(any(), any());
        }
    }

    // =========================================================================
    // Tests for createProduct()
    // =========================================================================

    @Nested
    @DisplayName("createProduct()")
    class CreateProduct {

        @Test
        @DisplayName("creates product with ACTIVE status when stock > 0")
        void createsActiveProductWhenStockPositive() {
            // Given: the repository saves and returns the product unchanged (with ID assigned).
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product p = invocation.getArgument(0);
                setId(p, 1L);
                return p;
            });

            // When: we create a product with 50 units of stock.
            Product result = productService.createProduct(
                    "Keyboard", "A mechanical keyboard", "electronics", 89.99, 50);

            // Then: the product is created with ACTIVE status.
            assertThat(result.getStatus()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(result.getStockQuantity()).isEqualTo(50);
            assertThat(result.getName()).isEqualTo("Keyboard");
        }

        @Test
        @DisplayName("creates product with OUT_OF_STOCK status when stock = 0")
        void createsOutOfStockProductWhenStockZero() {
            // Given: the repository saves and returns the product.
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product p = invocation.getArgument(0);
                setId(p, 2L);
                return p;
            });

            // When: we create a product with 0 units of stock.
            Product result = productService.createProduct(
                    "Headphones", "ANC headphones", "electronics", 249.99, 0);

            // Then: the product is created with OUT_OF_STOCK status (business rule).
            assertThat(result.getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
            assertThat(result.getStockQuantity()).isEqualTo(0);
        }
    }

    // =========================================================================
    // Tests for updateStock()
    // =========================================================================

    @Nested
    @DisplayName("updateStock()")
    class UpdateStock {

        @Test
        @DisplayName("transitions ACTIVE → OUT_OF_STOCK when new stock = 0")
        void activeToOutOfStockWhenStockBecomesZero() {
            // Given: an active product with 100 units of stock.
            Product product = buildProduct(1L, "Keyboard", "electronics", 89.99, 100, ProductStatus.ACTIVE);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When: the stock is set to 0.
            Optional<Product> result = productService.updateStock(1L, 0);

            // Then: the status automatically changes to OUT_OF_STOCK.
            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
            assertThat(result.get().getStockQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("transitions OUT_OF_STOCK → ACTIVE when stock is replenished")
        void outOfStockToActiveWhenStockReplenished() {
            // Given: an out-of-stock product.
            Product product = buildProduct(1L, "Headphones", "electronics", 249.99, 0, ProductStatus.OUT_OF_STOCK);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When: stock is replenished to 25 units.
            Optional<Product> result = productService.updateStock(1L, 25);

            // Then: the status automatically restores to ACTIVE.
            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(result.get().getStockQuantity()).isEqualTo(25);
        }

        @Test
        @DisplayName("DISCONTINUED product status does NOT change on stock update")
        void discontinuedStatusRemainsUnchanged() {
            // Given: a discontinued product (soft-deleted).
            Product product = buildProduct(1L, "Old TV", "electronics", 999.99, 0, ProductStatus.DISCONTINUED);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When: someone tries to replenish stock on a discontinued product.
            Optional<Product> result = productService.updateStock(1L, 10);

            // Then: the product's status remains DISCONTINUED (business rule — no resurrection).
            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
            assertThat(result.get().getStockQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("returns empty Optional when product does not exist")
        void returnsEmptyWhenProductNotFound() {
            // Given: no product with ID 99.
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            // When: we try to update stock for a non-existent product.
            Optional<Product> result = productService.updateStock(99L, 5);

            // Then: the result is empty (caller must handle NOT_FOUND).
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("throws IllegalArgumentException when new stock is negative")
        void throwsExceptionForNegativeStock() {
            // When + Then: negative stock throws an IllegalArgumentException immediately.
            // The repository should not even be called.
            assertThatThrownBy(() -> productService.updateStock(1L, -5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be negative");

            // Verify the repository was never consulted.
            verify(productRepository, never()).findById(any());
        }
    }

    // =========================================================================
    // Tests for deleteProduct()
    // =========================================================================

    @Nested
    @DisplayName("deleteProduct()")
    class DeleteProduct {

        @Test
        @DisplayName("marks product as DISCONTINUED (soft-delete) and returns true")
        void softDeletesProductAndReturnsTrue() {
            // Given: an active product exists.
            Product product = buildProduct(1L, "Keyboard", "electronics", 89.99, 100, ProductStatus.ACTIVE);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When: we delete the product.
            boolean result = productService.deleteProduct(1L);

            // Then: returns true and the product's status is now DISCONTINUED.
            assertThat(result).isTrue();
            assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
        }

        @Test
        @DisplayName("returns false when product does not exist")
        void returnsFalseWhenProductNotFound() {
            // Given: no product with ID 99.
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            // When: we try to delete a non-existent product.
            boolean result = productService.deleteProduct(99L);

            // Then: returns false without any exception.
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Build a {@link Product} instance for use in test setups.
     * Uses the public constructor; the ID is set via reflection since
     * it is normally assigned by the database (auto-generated).
     */
    private Product buildProduct(Long id, String name, String category,
                                  Double price, Integer stock, ProductStatus status) {
        Product product = new Product(name, "", category, price, stock, status);
        setId(product, id);
        return product;
    }

    /**
     * Set the private {@code id} field on a {@link Product} via reflection.
     *
     * <p>The {@code id} field is managed by JPA/Hibernate and has no public setter.
     * In unit tests that don't use a real database, we use reflection to assign a
     * test ID so that assertions on product IDs work correctly.
     */
    private void setId(Product product, Long id) {
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set product ID via reflection", e);
        }
    }
}
